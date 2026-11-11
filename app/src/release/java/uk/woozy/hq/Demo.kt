package uk.woozy.hq

import android.content.Context
import android.content.Intent

/** Release builds: no demo mode. The debug copy fills sample data for CI screenshots. */
object Demo {
    fun apply(c: Context, i: Intent?) = Unit
}
