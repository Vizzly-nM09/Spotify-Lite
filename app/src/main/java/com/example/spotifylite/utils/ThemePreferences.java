package com.example.spotifylite.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;

import androidx.appcompat.app.AppCompatDelegate;

public final class ThemePreferences {
    private static final String PREFS_NAME = "spotifylite_theme_prefs";
    private static final String KEY_NIGHT_MODE = "night_mode";

    private ThemePreferences() {
    }

    public static void applySavedTheme(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        int mode = prefs.getInt(KEY_NIGHT_MODE, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        AppCompatDelegate.setDefaultNightMode(mode);
    }

    public static boolean isNightModeActive(Context context) {
        int currentUiMode = context.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;
        return currentUiMode == Configuration.UI_MODE_NIGHT_YES;
    }

    public static void toggleTheme(Context context) {
        int nextMode = isNightModeActive(context)
                ? AppCompatDelegate.MODE_NIGHT_NO
                : AppCompatDelegate.MODE_NIGHT_YES;
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putInt(KEY_NIGHT_MODE, nextMode)
                .apply();
        AppCompatDelegate.setDefaultNightMode(nextMode);
    }
}

