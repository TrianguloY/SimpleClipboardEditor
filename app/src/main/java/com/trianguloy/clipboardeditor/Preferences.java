package com.trianguloy.clipboardeditor;

import static com.trianguloy.clipboardeditor.Preferences.Pref.SYNC_BTN_IC;
import static com.trianguloy.clipboardeditor.Preferences.Pref.SYNC_EXTERNAL;
import static com.trianguloy.clipboardeditor.Preferences.Pref.SYNC_PAUSE;
import static com.trianguloy.clipboardeditor.Preferences.Pref.SYNC_START;

import android.content.SharedPreferences;

/** Simple Preferences wrapper */
public class Preferences {
    private final SharedPreferences prefs; // the prefs

    /** @param prefs preferences to wrap */
    public Preferences(SharedPreferences prefs) {
        this.prefs = prefs;

        // migrations
        if (prefs.contains("sync")) {
            if (!prefs.getBoolean("sync", true)) {
                // sync = false -> toggle appropriate
                set(SYNC_START, false);
                set(SYNC_BTN_IC, true);
                set(SYNC_EXTERNAL, false);
                set(SYNC_BTN_IC, true);
                set(SYNC_PAUSE, false);
            }
            prefs.edit().remove("sync").apply();
        }
    }

    public enum Pref {
        SHOW_KEYBOARD("showKeyboard", true),
        CAPITALIZE("capitalize", false),
        STATISTICS("statistics", true),
        SYNC_START("syncStart", true),
        SYNC_BTN_CI("syncBtnCi", false),
        SYNC_EXTERNAL("syncExternal", true),
        SYNC_INPUT("syncInput", false),
        SYNC_BTN_IC("syncBtnIC", false),
        SYNC_PAUSE("syncPause", true),
        ;

        private final String key;
        private final boolean defaultValue;

        Pref(String key, boolean defaultValue) {
            this.key = key;
            this.defaultValue = defaultValue;
        }
    }

    public boolean is(Pref pref) {
        return prefs.getBoolean(pref.key, pref.defaultValue);
    }

    public void set(Pref pref, boolean value) {
        prefs.edit().putBoolean(pref.key, value).apply();
    }

}
