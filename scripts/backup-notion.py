#!/usr/bin/env python3
"""Export everything the Notion integration can see into one zip.

Usage: NOTION_TOKEN=secret_xxx python3 backup-notion.py out/notion-backup-2026-11.zip

The zip holds:
  pages/<title>-<id>.md      readable Markdown (properties + content)
  pages/<title>-<id>.json    raw API data (page object + full block tree)
  databases/<title>-<id>.csv one row per entry, one column per property
  databases/<title>-<id>.json schema + every row as raw API data
  attachments/...            files uploaded into Notion (Notion's links expire)
  index.md, manifest.json    what was exported, counts, and any errors

Only standard-library Python, so it runs anywhere without pip.
The integration only sees pages that are shared with it (page ... > Connections).
"""
import csv
import datetime as dt
import io
import json
import os
import re
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
import zipfile

API = "https://api.notion.com/v1"
VERSION = "2025-09-03"
TOKEN = os.environ.get("NOTION_TOKEN", "").strip()
MAX_ATTACHMENT = 25 * 1024 * 1024
ERRORS = []


def api(method, path, body=None, tries=7):
    data = json.dumps(body).encode() if body is not None else None
    for i in range(tries):
        req = urllib.request.Request(API + path, data=data, method=method, headers={
            "Authorization": f"Bearer {TOKEN}",
            "Notion-Version": VERSION,
            "Content-Type": "application/json",
        })
        try:
            with urllib.request.urlopen(req, timeout=60) as r:
                out = json.load(r)
            time.sleep(0.34)  # Notion allows about 3 requests a second
            return out
        except urllib.error.HTTPError as e:
            if e.code in (409, 429, 500, 502, 503, 504) and i < tries - 1:
                time.sleep(float(e.headers.get("Retry-After") or 2 ** i))
                continue
            raise RuntimeError(f"{method} {path} -> {e.code}: {e.read().decode(errors='replace')[:300]}")
        except (urllib.error.URLError, TimeoutError, ConnectionError) as e:
            if i < tries - 1:
                time.sleep(2 ** i)
                continue
            raise RuntimeError(f"{method} {path} -> {e}")


def paged(method, path, body=None):
    cursor = None
    while True:
        if method == "GET":
            q = {"page_size": 100}
            if cursor:
                q["start_cursor"] = cursor
            r = api("GET", f"{path}?{urllib.parse.urlencode(q)}")
        else:
            b = dict(body or {}, page_size=100)
            if cursor:
                b["start_cursor"] = cursor
            r = api("POST", path, b)
        yield from r.get("results", [])
        if not r.get("has_more"):
            return
        cursor = r.get("next_cursor")


# ---------- text helpers ----------

def plain(arr):
    return "".join(t.get("plain_text", "") for t in arr or [])


def rich(arr):
    out = []
    for t in arr or []:
        s = t.get("plain_text", "")
        if not s:
            continue
        a = t.get("annotations") or {}
        core = s.strip()
        if core:
            if a.get("code"):
                core = f"`{core}`"
            if a.get("bold"):
                core = f"**{core}**"
            if a.get("italic"):
                core = f"*{core}*"
            if a.get("strikethrough"):
                core = f"~~{core}~~"
            if t.get("href"):
                core = f"[{core}]({t['href']})"
            lead = s[: len(s) - len(s.lstrip())]
            trail = s[len(s.rstrip()):]
            s = lead + core + trail
        out.append(s)
    return "".join(out)


def slug(title, oid):
    base = re.sub(r"[^\w\- ]+", "", title or "").strip()[:60].strip() or "Untitled"
    return f"{base}-{oid.replace('-', '')[:8]}"


def title_of(obj):
    if obj.get("object") == "data_source" or obj.get("object") == "database":
        return plain(obj.get("title")) or "Untitled database"
    for p in (obj.get("properties") or {}).values():
        if p.get("type") == "title":
            return plain(p.get("title")) or "Untitled"
    return "Untitled"


def prop_text(p):
    t = p.get("type")
    v = p.get(t)
    if v is None:
        return ""
    if t in ("title", "rich_text"):
        return plain(v)
    if t in ("select", "status"):
        return v.get("name", "")
    if t == "multi_select":
        return ", ".join(o.get("name", "") for o in v)
    if t == "date":
        return v.get("start", "") + (f" -> {v['end']}" if v.get("end") else "")
    if t == "checkbox":
        return "Yes" if v else "No"
    if t == "people":
        return ", ".join(u.get("name") or u.get("id", "") for u in v)
    if t == "relation":
        return ", ".join(r.get("id", "") for r in v)
    if t == "files":
        return ", ".join(f.get("name", "") for f in v)
    if t == "formula":
        inner = v.get(v.get("type"))
        return inner.get("start", "") if isinstance(inner, dict) else ("" if inner is None else str(inner))
    if t == "rollup":
        rt = v.get("type")
        if rt == "array":
            return "; ".join(prop_text(x) for x in v.get("array", []))
        inner = v.get(rt)
        return inner.get("start", "") if isinstance(inner, dict) else str(inner if inner is not None else "")
    if t == "unique_id":
        return f"{v.get('prefix') or ''}{'-' if v.get('prefix') else ''}{v.get('number', '')}"
    if t in ("created_by", "last_edited_by"):
        return v.get("name") or v.get("id", "")
    return str(v) if not isinstance(v, (dict, list)) else json.dumps(v, ensure_ascii=False)


# ---------- blocks ----------

def block_tree(block_id, depth=0):
    kids = []
    try:
        for b in paged("GET", f"/blocks/{block_id}/children"):
            if b.get("has_children") and b["type"] not in ("child_page", "child_database") and depth < 12:
                b["children"] = block_tree(b["id"], depth + 1)
            kids.append(b)
    except RuntimeError as e:
        ERRORS.append(f"blocks {block_id}: {e}")
    return kids


class Files:
    """Downloads files hosted by Notion (their URLs expire after an hour)."""

    def __init__(self, zf):
        self.zf, self.n = zf, 0

    def link(self, obj, block_id):
        kind = obj.get("type")
        url = (obj.get(kind) or {}).get("url", "")
        if kind != "file" or not url:
            return url
        name = urllib.parse.unquote(urllib.parse.urlparse(url).path.rsplit("/", 1)[-1]) or "file"
        path = f"attachments/{block_id.replace('-', '')[:8]}-{re.sub(r'[^\w.\-]+', '_', name)[:80]}"
        try:
            with urllib.request.urlopen(url, timeout=120) as r:
                data = r.read(MAX_ATTACHMENT + 1)
            if len(data) > MAX_ATTACHMENT:
                ERRORS.append(f"attachment over 25 MB skipped: {name}")
                return url
            self.zf.writestr(path, data)
            self.n += 1
            return "../" + path
        except Exception as e:  # noqa: BLE001
            ERRORS.append(f"attachment {name}: {e}")
            return url


def md_blocks(blocks, files, indent=""):
    lines, num = [], 0
    for b in blocks:
        t = b.get("type")
        v = b.get(t) or {}
        num = num + 1 if t == "numbered_list_item" else 0
        kids = b.get("children") or []
        sub = indent + "    "
        is_list = t in ("bulleted_list_item", "numbered_list_item", "to_do")
        if not is_list and lines and lines[-1] != "" and not indent:
            lines.append("")
        text = rich(v.get("rich_text"))
        if t == "paragraph":
            lines.append(indent + text)
        elif t in ("heading_1", "heading_2", "heading_3"):
            lines.append(indent + "#" * int(t[-1]) + " " + text)
        elif t == "bulleted_list_item":
            lines.append(f"{indent}- {text}")
        elif t == "numbered_list_item":
            lines.append(f"{indent}{num}. {text}")
        elif t == "to_do":
            lines.append(f"{indent}- [{'x' if v.get('checked') else ' '}] {text}")
        elif t == "toggle":
            lines.append(f"{indent}- {text}")
        elif t == "quote":
            lines.append(f"{indent}> {text}")
        elif t == "callout":
            icon = (v.get("icon") or {}).get("emoji", "")
            lines.append(f"{indent}> {icon} {text}".rstrip())
        elif t == "code":
            lines += [f"{indent}```{v.get('language', '')}", *[indent + l for l in plain(v.get("rich_text")).split("\n")], f"{indent}```"]
        elif t == "equation":
            lines.append(f"{indent}$$ {v.get('expression', '')} $$")
        elif t == "divider":
            lines.append(indent + "---")
        elif t == "child_page":
            lines.append(f"{indent}[Page: {v.get('title', '')}]")
        elif t == "child_database":
            lines.append(f"{indent}[Database: {v.get('title', '')}]")
        elif t in ("image", "file", "pdf", "video", "audio"):
            url = files.link(v, b["id"])
            cap = plain(v.get("caption")) or v.get("name") or t
            lines.append(f"{indent}{'!' if t == 'image' else ''}[{cap}]({url})")
        elif t in ("bookmark", "embed", "link_preview"):
            url = v.get("url", "")
            lines.append(f"{indent}[{plain(v.get('caption')) or url}]({url})")
        elif t == "link_to_page":
            lines.append(f"{indent}[Link to {v.get(v.get('type'), '')}]")
        elif t == "table":
            rows = [[rich(c) for c in (r.get("table_row") or {}).get("cells", [])] for r in kids]
            if rows:
                w = max(len(r) for r in rows)
                rows = [r + [""] * (w - len(r)) for r in rows]
                lines.append(indent + "| " + " | ".join(rows[0]) + " |")
                lines.append(indent + "|" + " --- |" * w)
                lines += [indent + "| " + " | ".join(r) + " |" for r in rows[1:]]
            kids = []
        elif t in ("column_list", "column", "synced_block", "template"):
            lines += md_blocks(kids, files, indent)
            kids = []
        elif t in ("table_of_contents", "breadcrumb"):
            continue
        else:
            lines.append(f"{indent}<!-- {t} -->")
        if kids:
            lines += md_blocks(kids, files, sub)
        if t not in ("bulleted_list_item", "numbered_list_item", "to_do"):
            lines.append("")
    return lines


# ---------- export ----------

def main():
    if not TOKEN:
        sys.exit("NOTION_TOKEN is not set")
    out_path = sys.argv[1] if len(sys.argv) > 1 else f"notion-backup-{dt.date.today():%Y-%m}.zip"
    os.makedirs(os.path.dirname(out_path) or ".", exist_ok=True)

    found = list(paged("POST", "/search", {"sort": {"direction": "descending", "timestamp": "last_edited_time"}}))
    pages = [o for o in found if o.get("object") == "page" and not o.get("in_trash") and not o.get("archived")]
    sources = [o for o in found if o.get("object") in ("data_source", "database")]
    if not found:
        sys.exit("The integration can't see any pages. Share your top-level pages with it in Notion.")
    print(f"Found {len(pages)} pages and {len(sources)} databases")

    index = ["# Notion backup", "", f"Exported {dt.datetime.now(dt.timezone.utc):%Y-%m-%d %H:%M} UTC", ""]
    with zipfile.ZipFile(out_path, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as zf:
        files = Files(zf)

        index.append(f"## Databases ({len(sources)})\n")
        for s in sources:
            name = slug(title_of(s), s["id"])
            route = f"/data_sources/{s['id']}/query" if s["object"] == "data_source" else f"/databases/{s['id']}/query"
            try:
                rows = list(paged("POST", route, {}))
            except RuntimeError as e:
                ERRORS.append(f"database {title_of(s)}: {e}")
                rows = []
            cols = list((s.get("properties") or {}).keys())
            buf = io.StringIO()
            w = csv.writer(buf)
            w.writerow(["id", "url", *cols])
            for r in rows:
                props = r.get("properties") or {}
                w.writerow([r["id"], r.get("url", ""), *[prop_text(props[c]) if c in props else "" for c in cols]])
            zf.writestr(f"databases/{name}.csv", "﻿" + buf.getvalue())
            zf.writestr(f"databases/{name}.json", json.dumps({"database": s, "rows": rows}, ensure_ascii=False, indent=1))
            index.append(f"- [{title_of(s)}](databases/{urllib.parse.quote(name)}.csv) - {len(rows)} rows")
            print(f"  db  {title_of(s)}: {len(rows)} rows")

        index += ["", f"## Pages ({len(pages)})", ""]
        for n, p in enumerate(pages, 1):
            title, name = title_of(p), slug(title_of(p), p["id"])
            tree = block_tree(p["id"])
            md = [f"# {title}", "", f"Notion: {p.get('url', '')}  ", f"Last edited: {p.get('last_edited_time', '')}", ""]
            props = [(k, prop_text(v)) for k, v in (p.get("properties") or {}).items() if v.get("type") != "title"]
            props = [(k, v) for k, v in props if v]
            if props:
                md += [f"- **{k}:** {v}" for k, v in props] + [""]
            md += md_blocks(tree, files)
            zf.writestr(f"pages/{name}.md", "\n".join(md).rstrip() + "\n")
            zf.writestr(f"pages/{name}.json", json.dumps({"page": p, "blocks": tree}, ensure_ascii=False, indent=1))
            index.append(f"- [{title}](pages/{urllib.parse.quote(name)}.md)")
            if n % 25 == 0:
                print(f"  pages {n}/{len(pages)}")

        if ERRORS:
            index += ["", f"## Problems ({len(ERRORS)})", ""] + [f"- {e}" for e in ERRORS]
        zf.writestr("index.md", "\n".join(index) + "\n")
        zf.writestr("manifest.json", json.dumps({
            "exported_utc": dt.datetime.now(dt.timezone.utc).isoformat(timespec="seconds"),
            "notion_version": VERSION, "pages": len(pages), "databases": len(sources),
            "attachments": files.n, "errors": ERRORS,
        }, indent=1))

    size = os.path.getsize(out_path) / 1e6
    print(f"Wrote {out_path} ({size:.1f} MB): {len(pages)} pages, {len(sources)} databases, "
          f"{files.n} attachments, {len(ERRORS)} problems")
    for e in ERRORS[:20]:
        print("  !", e)


if __name__ == "__main__":
    main()
