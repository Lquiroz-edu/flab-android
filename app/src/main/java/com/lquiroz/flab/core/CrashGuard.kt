package com.lquiroz.flab.core

import android.content.Context

object CrashGuard {
    private const val WINDOW_MS = 10 * 60 * 1000L

    fun install(context: Context) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { record(context, error) }
            previous?.uncaughtException(thread, error)
        }
    }

    private fun record(context: Context, error: Throwable) {
        val prefs = context.getSharedPreferences("flab", Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val first = prefs.getLong("crash_window", 0L)
        val count = if (now - first <= WINDOW_MS) prefs.getInt("crash_count", 0) + 1 else 1
        val editor = prefs.edit()
            .putLong("crash_window", if (count == 1) now else first)
            .putInt("crash_count", count)
            .putString("last_error", "${error.javaClass.simpleName}: ${error.message.orEmpty().take(120)}")
        if (count >= 3) editor.putBoolean("global_enabled", false)
        editor.apply()
    }
}
