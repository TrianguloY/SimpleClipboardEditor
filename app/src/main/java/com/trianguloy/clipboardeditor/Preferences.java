package com.trianguloy.clipboardeditor;

import android.content.SharedPreferences;

public class Preferences {
    private final SharedPreferences prefs;

    public Preferences(SharedPreferences prefs) {
        this.prefs = prefs;
    }

    // ------------------- autoshowkeyboard -------------------
    private final String SHOWKEYBOARD_KEY = "showKeyboard";
    private final boolean SHOWKEYBOARD_DEFAULT = true;

    public boolean isShowKeyboard() {
        return prefs.getBoolean(SHOWKEYBOARD_KEY, SHOWKEYBOARD_DEFAULT);
    }

    public void setShowKeyboard(boolean showKeyboard) {
        prefs.edit().putBoolean(SHOWKEYBOARD_KEY, showKeyboard).apply();
    }
}
