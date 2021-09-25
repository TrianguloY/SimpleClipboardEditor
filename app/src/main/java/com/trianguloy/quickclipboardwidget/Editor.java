package com.trianguloy.quickclipboardwidget;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.os.Bundle;
import android.text.Editable;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.widget.EditText;
import android.widget.TextView;

public class Editor extends Activity {

    // ------------------- data -------------------

    // classes
    private ClipboardManager clipboard;

    // views
    private EditText v_input;
    private EditText v_label;
    private TextView v_extra;

    // internal data
    private boolean noListener = false;

    // ------------------- init -------------------

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // activity content
        this.requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_editor);

        // views
        v_input = findViewById(R.id.content);
        v_label = findViewById(R.id.label);
        v_extra = findViewById(R.id.description);

        // clipboard
        clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        clipboard.addPrimaryClipChangedListener(this::clipboardToInput);

        // inputs
        SimpleTextWatcher watcher = new SimpleTextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                inputToClipboard();
            }
        };
        v_input.addTextChangedListener(watcher);
        v_label.addTextChangedListener(watcher);

        v_input.requestFocus();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            clipboardToInput();
        }
    }

    // ------------------- buttons -------------------


    public void onClear(View view) {
        noListener = true;
        v_input.setText("");
        v_label.setText("");
        noListener = false;
        inputToClipboard();
    }


    public void onInfo(View view) {
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.app_name))
                .setMessage(R.string.about)
                .show();
    }


    // ------------------- transfer -------------------

    private void clipboardToInput() {
        if (noListener) return;
        noListener = true;

        // get
        ClipData primaryClip = clipboard.getPrimaryClip();

        // set
        if (primaryClip == null) {
            v_extra.setText("");
            v_label.setText("");
            v_input.setText("");

            Log.d("CLIPBOARD", "--> null");
        } else {
            ClipDescription description = primaryClip.getDescription();

            // mimetype
            v_extra.setText("Mimetype: ");
            boolean empty = true;
            for (int i = 0; i < description.getMimeTypeCount(); i++) {
                if (!empty) v_extra.append(" - ");
                empty = false;
                v_extra.append(description.getMimeType(i));
            }
            if (empty) v_extra.append("[none]");

            // item count
            int itemCount = primaryClip.getItemCount();
            if (itemCount > 1) v_extra.append("\nItem count = " + itemCount);

            // label
            String label = toStringNonNull(description.getLabel());
            if (!toStringNonNull(v_label.getText()).equals(label)) {
                v_label.setText(label);
                if (v_label.hasFocus()) v_label.setSelection(v_label.getText().length());
            }

            // text
            String content = toStringNonNull(primaryClip.getItemAt(0).coerceToText(this));
            if (!toStringNonNull(v_input.getText()).equals(content)) {
                v_input.setText(content);
                if (v_input.hasFocus()) v_input.setSelection(v_input.getText().length());
            }


            Log.d("CLIPBOARD", "--> [" + label + "] " + content);
        }


        noListener = false;
    }

    private void inputToClipboard() {
        if (noListener) return;
        noListener = true;

        // get
        CharSequence content = v_input.getText();
        CharSequence label = v_label.getText();

        // set
        clipboard.setPrimaryClip(ClipData.newPlainText(label, content));

        Log.d("CLIPBOARD", "<-- [" + label + "] " + content);

        noListener = false;
    }

    // ------------------- utils -------------------

    static private String toStringNonNull(Object object) {
        if (object == null) return "";
        else return object.toString();
    }

}