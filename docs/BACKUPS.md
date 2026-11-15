# Monthly failsafe backups

On the 1st of every month at 03:00 UTC, GitHub Actions copies three things into Google Drive:

```
Woozy-Failsafe-Backups/
  2026-11/
    hq-2026-11.bundle            full git history, every branch (HQ)
    hq-2026-11-source.zip        plain copy of the current code (HQ)
    notion-backup-2026-11.zip    every Notion page and database HQ's integration can see
    hq-fit-2026-11.bundle        full git history, every branch (Woozy Fit)
    hq-fit-2026-11-source.zip    plain copy of the current code (Woozy Fit)
```

The last 12 months are kept; older month folders go to Drive's bin, which empties itself after 30 days.
Each repo runs `.github/workflows/monthly-backup.yml`; the Notion step only exists in HQ (`scripts/backup-notion.py`).
If anything fails, the run goes red, GitHub emails you, and the files are kept as a 7-day download on the run page.

## Setup (once)

1. **Install rclone on the PC**: in PowerShell run `winget install Rclone.Rclone`, then open a new PowerShell window. On a Mac: `brew install rclone`.
2. **Connect Google Drive**: run
   `rclone config create gdrive drive scope=drive.file`
   A browser opens; sign in with the Google account whose Drive you want and click Allow.
   `drive.file` means rclone can only see the files it creates, so this key can never touch anything else in your Drive.
3. **Copy the config**: run `rclone config show gdrive` and copy everything it prints, from `[gdrive]` to the closing `}` of the token line.
4. **Add the secrets** on github.com, in each repo: Settings > Secrets and variables > Actions > New repository secret.
   - `Woozypictures/hq`: `RCLONE_CONFIG` = what you copied; `NOTION_TOKEN` = the Notion integration secret HQ uses (`ntn_...` or `secret_...`).
   - `Woozypictures/hq-fit`: `RCLONE_CONFIG` = the same text.
5. **Give the integration your whole workspace**: in Notion, on each top-level page, open ... > Connections and add HQ's integration. It only exports pages it's connected to (child pages are included automatically).
6. **Test**: in each repo open Actions > monthly-backup > Run workflow. After a few minutes the run should be green and `Woozy-Failsafe-Backups/<this month>/` should appear in Drive.

## Restoring

- **Code**: `git clone hq-2026-11.bundle hq` gives back the full repo with history; then `git remote set-url origin <new GitHub URL>` and `git push --all`.
- **Quick look at code**: unzip the `-source.zip`.
- **Notion**: unzip `notion-backup-*.zip` and open `index.md`. Pages are Markdown (Notion's Import > Markdown takes them), databases are CSV (Notion's Import > CSV makes a database again). The `.json` files hold the raw data if anything needs rebuilding exactly.

## If a run fails

- `RCLONE_CONFIG secret is not set` / `NOTION_TOKEN secret is not set`: step 4 above.
- `invalid_grant` or `token expired` from rclone: Google revoked the login (it happens if you remove rclone from your Google account's third-party access). Redo steps 2 to 4.
- `The integration can't see any pages`: step 5.
- The latest summary is always on the `backup-log` branch (`status.txt`).
