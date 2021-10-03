package com.trianguloy.clipboardeditor;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.inputmethod.InputMethodManager;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

public class Editor extends Activity {
    private static final String CHANNEL_ID = "text";

    // ------------------- data -------------------

    // classes
    private ClipboardManager clipboard;
    private Preferences prefs;

    // views
    private EditText v_content;
    private EditText v_label;
    private TextView v_extra;

    // internal data
    private boolean noListener = false;
    private NotificationManager notification;

    // ------------------- init -------------------

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // activity content
        this.requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_editor);

        // views
        v_content = findViewById(R.id.content);
        v_label = findViewById(R.id.label);
        v_extra = findViewById(R.id.description);

        // descriptions
        for (int viewId : new int[]{R.id.notify, R.id.share, R.id.clear, R.id.configure, R.id.info}) {
            findViewById(viewId).setOnLongClickListener(view -> {
                Toast.makeText(Editor.this, view.getContentDescription().toString(), Toast.LENGTH_SHORT).show();
                return true;
            });
        }

        // preferences
        prefs = new Preferences(getPreferences(MODE_PRIVATE));

        // clipboard
        clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        notification = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        clipboard.addPrimaryClipChangedListener(this::clipboardToInput);

        // inputs
        SimpleTextWatcher watcher = new SimpleTextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                inputToClipboard();
            }
        };
        v_content.addTextChangedListener(watcher);
        v_label.addTextChangedListener(watcher);

        if (prefs.isShowKeyboard()) {
            // show keyboard
            if (v_content.requestFocus()) {
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.showSoftInput(v_content, InputMethodManager.SHOW_IMPLICIT);
            }
        }


        // intent
        parseIntent(getIntent());
        setIntent(null);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            clipboardToInput();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        parseIntent(intent);
    }

    private void parseIntent(Intent intent) {
        if (intent == null) return;
        ClipData data = intent.getParcelableExtra(getPackageName());
        if (data == null) return;
        clipboard.setPrimaryClip(data);
    }

    // ------------------- buttons -------------------

    public void onNotification(View view) {
        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, getString(R.string.channel_name), NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription(getString(R.string.channel_description));
            notification.createNotificationChannel(channel);

            builder = new Notification.Builder(this, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(this);
        }

        if (v_label.getText().length() > 0) builder.setContentTitle(v_label.getText());
        builder.setContentText(v_content.getText());
        builder.setSmallIcon(R.drawable.ic_notification);

        Intent intent = new Intent(this, Editor.class);
        intent.putExtra(getPackageName(), clipboard.getPrimaryClip());
        builder.setContentIntent(PendingIntent.getActivity(this, getUniqueId(), intent, PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0)));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            builder.setStyle(new Notification.BigTextStyle()
                    .bigText(v_content.getText()));
        }


        NotificationManager notification = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        int id;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            id = notification.getActiveNotifications().length;
        } else {
            id = getUniqueId();
        }
        notification.notify(id,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN ? builder.build() : builder.getNotification()
        );
    }

    public void onShare(View view) {
        Intent sendIntent = new Intent();
        sendIntent.setAction(Intent.ACTION_SEND);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            sendIntent.setClipData(clipboard.getPrimaryClip());
        }
        sendIntent.putExtra(Intent.EXTRA_TEXT, v_content.getText());
        sendIntent.setType("text/plain");

        Intent shareIntent = Intent.createChooser(sendIntent, v_label.getText());
        startActivity(shareIntent);
    }


    public void onClear(View view) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            clipboard.clearPrimaryClip();
        } else {
            noListener = true;
            v_content.setText("");
            v_label.setText("");
            noListener = false;
            inputToClipboard();
        }
    }

    public void onConfigure(View view) {
        View content = getLayoutInflater().inflate(R.layout.configuration, null);
        CheckBox autokeyboard = content.findViewById(R.id.autokeyboard);
        autokeyboard.setChecked(prefs.isShowKeyboard());
        autokeyboard.setOnCheckedChangeListener((checkbox, checked) -> prefs.setShowKeyboard(checked));
        new AlertDialog.Builder(this)
                .setIcon(R.mipmap.ic_launcher)
                .setTitle(R.string.app_name)
                .setView(content)
                .show();
    }


    public void onInfo(View view) {
        new AlertDialog.Builder(this)
                .setIcon(R.mipmap.ic_launcher)
                .setTitle(getString(R.string.app_name))
                .setMessage(R.string.about)
                .setNeutralButton(R.string.blog, (dialog, button) -> {
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://triangularapps.blogspot.com/")));
                    } catch (ActivityNotFoundException e) {
                        Toast.makeText(Editor.this, R.string.toast_noBrowser, Toast.LENGTH_SHORT).show();
                    }
                })
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
            v_extra.setText(String.format("[%s]", getString(R.string.txt_empty)));
            v_label.setText("");
            v_content.setText("");

            Log.d("CLIPBOARD", "--> null");
        } else {
            ClipDescription description = primaryClip.getDescription();

            // mimetype
            v_extra.setText(R.string.label_mimetype);
            boolean empty = true;
            for (int i = 0; i < description.getMimeTypeCount(); i++) {
                if (!empty) v_extra.append(" -");
                empty = false;
                v_extra.append(" " + description.getMimeType(i));
            }
            if (empty) v_extra.append(getString(R.string.txt_empty));

            // item count
            int itemCount = primaryClip.getItemCount();
            if (itemCount > 1) v_extra.append(getString(R.string.txt_itemcount) + itemCount);

            // label
            String label = toStringNonNull(description.getLabel());
            if (!toStringNonNull(v_label.getText()).equals(label)) {
                v_label.setText(label);
                if (v_label.hasFocus()) v_label.setSelection(v_label.getText().length());
            }

            // text
            String content = toStringNonNull(primaryClip.getItemAt(0).coerceToText(this));
            if (!toStringNonNull(v_content.getText()).equals(content)) {
                v_content.setText(content);
                if (v_content.hasFocus()) v_content.setSelection(v_content.getText().length());
            }


            Log.d("CLIPBOARD", "--> [" + label + "] " + content);
        }


        noListener = false;
    }

    private void inputToClipboard() {
        if (noListener) return;
        noListener = true;

        // get
        CharSequence content = v_content.getText();
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

    private static int getUniqueId() {
        return Long.valueOf(System.currentTimeMillis()).intValue();
    }
}