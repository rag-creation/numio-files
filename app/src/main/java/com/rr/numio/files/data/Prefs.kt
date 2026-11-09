package com.rr.numio.files.data

import android.content.Context
import com.rr.numio.files.ui.theme.AppTheme

/** Settings and starred items, kept on the phone only. */
class Prefs(context: Context) {
    private val p = context.getSharedPreferences("files", Context.MODE_PRIVATE)

    var theme: AppTheme
        get() = runCatching { AppTheme.valueOf(p.getString("theme", null) ?: "DARK") }.getOrDefault(AppTheme.DARK)
        set(v) = p.edit().putString("theme", v.name).apply()

    var showHidden: Boolean
        get() = p.getBoolean("hidden", false)
        set(v) = p.edit().putBoolean("hidden", v).apply()

    var sort: SortOrder
        get() = runCatching { SortOrder.valueOf(p.getString("sort", null) ?: "NAME") }.getOrDefault(SortOrder.NAME)
        set(v) = p.edit().putString("sort", v.name).apply()

    var starred: Set<String>
        get() = p.getStringSet("starred", emptySet())?.toSet() ?: emptySet()
        set(v) = p.edit().putStringSet("starred", v).apply()

    /** Recent searches, newest first (kept on the phone only). */
    var searchHistory: List<String>
        get() = p.getString("history", "").orEmpty().split('\n').filter { it.isNotBlank() }
        set(v) = p.edit().putString("history", v.joinToString("\n")).apply()
}
