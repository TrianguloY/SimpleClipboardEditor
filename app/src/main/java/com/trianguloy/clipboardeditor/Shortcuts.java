package com.trianguloy.clipboardeditor;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.Toast;

/**
 * Activity that will clear the clipboard when launched, then exit
 */
public class Shortcuts extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // get
        var clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);

        // clear
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // the easy way, just call 'clear'
            clipboard.clearPrimaryClip();
        } else {
            // the not-so-easy way, manually set as empty
            clipboard.setPrimaryClip(ClipData.newPlainText("", ""));
        }

        Toast.makeText(this, R.string.toast_cleared, Toast.LENGTH_SHORT).show();
        finish();
    }
}
