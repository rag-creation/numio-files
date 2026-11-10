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

    /** Folders where the person chose grid ("g:path") or list ("l:path"). */
    var viewChoices: Set<String>
        get() = p.getStringSet("views", emptySet())?.toSet() ?: emptySet()
        set(v) = p.edit().putStringSet("views", v).apply()

    /** Start in Home or Browse. */
    var openOnBrowse: Boolean
        get() = p.getBoolean("openBrowse", false)
        set(v) = p.edit().putBoolean("openBrowse", v).apply()

    /** Ask before moving things to Trash. */
    var askBeforeTrash: Boolean
        get() = p.getBoolean("askTrash", true)
        set(v) = p.edit().putBoolean("askTrash", v).apply()

    /** How many days Trash keeps things: 7, 30 or 90. */
    var trashDays: Int
        get() = p.getInt("trashDays", 30)
        set(v) = p.edit().putInt("trashDays", v).apply()

    /** Fingerprint / screen lock for the whole app. */
    var lockApp: Boolean
        get() = p.getBoolean("lockApp", false)
        set(v) = p.edit().putBoolean("lockApp", v).apply()

    /** Fingerprint / screen lock before showing hidden files. */
    var lockHidden: Boolean
        get() = p.getBoolean("lockHidden", false)
        set(v) = p.edit().putBoolean("lockHidden", v).apply()
}
