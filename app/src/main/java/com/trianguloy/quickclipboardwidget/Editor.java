package com.trianguloy.quickclipboardwidget;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.os.Bundle;
import android.text.Editable;
import android.util.Log;
import android.view.Window;
import android.widget.EditText;
import android.widget.TextView;

public class Editor extends Activity {

    private ClipboardManager clipboard;

    private EditText v_input;
    private EditText v_label;
    private TextView v_extra;

    private boolean noListener = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // activity content
        this.requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_editor);

        // views
        v_input = findViewById(R.id.editor);
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


    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) clipboardToInput();
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
            CharSequence label = description.getLabel().toString();
            if (!v_label.getText().toString().equals(label.toString())) v_label.setText(label);

            // text
            String content = primaryClip.getItemAt(0).coerceToText(this).toString();
            if (!v_input.getText().toString().equals(content)) v_input.setText(content);


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

    // ------------------- clipboard -------------------


}