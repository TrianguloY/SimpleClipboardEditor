package com.trianguloy.clipboardeditor;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;

/**
 * This activity receives the PROCESS_TEXT intent and calls the main activity with it.
 * Separated to allow having a different label, and also because this feature is for Android 6.0+ only
 */
@TargetApi(Build.VERSION_CODES.M)
public class Process extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // get the text
        ClipData clipData = ClipData.newPlainText(
                getString(R.string.clip_selection),
                getIntent().getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)
        );

        // process the text
        Intent intent = new Intent(this, Editor.class);
        intent.putExtra(getPackageName(), clipData);
        startActivityForResult(intent, 0);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        // get the result
        ClipData clipData = data == null ? null : data.getParcelableExtra(getPackageName());
        CharSequence result = clipData == null ? "" : clipData.getItemAt(0).coerceToText(this);

        // return it (we ignore the readonly attribute, it's unnecessary)
        Intent intent = new Intent();
        intent.putExtra(Intent.EXTRA_PROCESS_TEXT, result);
        setResult(RESULT_OK, intent);
        finish();
    }
}
