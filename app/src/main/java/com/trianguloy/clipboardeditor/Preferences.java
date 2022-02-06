package com.trianguloy.clipboardeditor;

import android.content.SharedPreferences;

/**
 * Simple Preferences wrapper
 */
public class Preferences {
    private final SharedPreferences prefs; // the prefs

    /**
     * @param prefs preferences to wrap
     */
    public Preferences(SharedPreferences prefs) {
        this.prefs = prefs;
    }

    // ------------------- autoshowkeyboard -------------------
    private final String SHOWKEYBOARD_KEY = "showKeyboard";
    private final boolean SHOWKEYBOARD_DEFAULT = true;

    /**
     * @return if SHOWKEYBOARD preference is set
     */
    public boolean isShowKeyboard() {
        return prefs.getBoolean(SHOWKEYBOARD_KEY, SHOWKEYBOARD_DEFAULT);
    }


    /**
     * @param showKeyboard new SHOWKEYBOARD preference to set
     */
    public void setShowKeyboard(boolean showKeyboard) {
        prefs.edit().putBoolean(SHOWKEYBOARD_KEY, showKeyboard).apply();
    }

    // ------------------- capitalize sentences -------------------

    private final String CAPITALIZE_KEY = "capitalize";
    private final boolean CAPITALIZE_DEFAULT = false;

    /**
     * @return if CAPITALIZE preference is set
     */
    public boolean isCapitalize() {
        return prefs.getBoolean(CAPITALIZE_KEY, CAPITALIZE_DEFAULT);
    }


    /**
     * @param capitalize new CAPITALIZE preference to set
     */
    public void setCapitalize(boolean capitalize) {
        prefs.edit().putBoolean(CAPITALIZE_KEY, capitalize).apply();
    }
}
