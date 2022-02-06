package com.trianguloy.clipboardeditor;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
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

/**
 * The main activity, a clipboard editor
 */
public class Editor extends Activity {
    private static final String CHANNEL_ID = "text"; // id for the channel for notifications

    // ------------------- data -------------------

    // classes
    private ClipboardManager clipboard; // system clipboard
    private NotificationManager notification; // system notifications
    private Preferences prefs; // preferences wrapper

    // views
    private EditText v_content; // content input
    private EditText v_label; // label input
    private TextView v_extra; // extra text

    // internal data
    private boolean noListener = false; // to avoid firing clipboardToInput and inputToClipboard recursively

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

        // show keyboard if enabled in settings
        if (prefs.isShowKeyboard()) {
            if (v_content.requestFocus()) {
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.showSoftInput(v_content, InputMethodManager.SHOW_IMPLICIT);
            }
        }


        // start intent
        parseIntent(getIntent());
        setIntent(null);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        // when windows is focused, update clipboard
        if (hasFocus) {
            clipboardToInput();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // on new intent (probably from a notification) load it
        parseIntent(intent);
    }

    /**
     * @param intent intent to parse for clipboard data
     */
    private void parseIntent(Intent intent) {
        if (intent == null) return;
        ClipData data = intent.getParcelableExtra(getPackageName());
        if (data == null) return;
        clipboard.setPrimaryClip(data);
    }

    // ------------------- buttons -------------------

    /**
     * Shows a notification with the clipboard content
     */
    public void onNotification(View view) {
        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // setup a notification channel in Oreo+
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, getString(R.string.channel_name), NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription(getString(R.string.channel_description));
            notification.createNotificationChannel(channel);

            builder = new Notification.Builder(this, CHANNEL_ID);
        } else {
            // no notification channel before Oreo
            builder = new Notification.Builder(this);
        }

        // sets the label as notification title (if any), the content and icon
        if (v_label.getText().length() > 0) builder.setContentTitle(v_label.getText());
        builder.setContentText(v_content.getText());
        builder.setSmallIcon(R.drawable.ic_notification);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            // when allowed, set the content as big text (improved display)
            builder.setStyle(new Notification.BigTextStyle()
                    .bigText(v_content.getText()));
        }

        // sets the intent for when you click the notification. It will open the app with the current clipboard content
        Intent intent = new Intent(this, Editor.class);
        intent.putExtra(getPackageName(), clipboard.getPrimaryClip());
        builder.setContentIntent(PendingIntent.getActivity(this, getUniqueId(), intent, PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0))); // change the requestCode with an unique id for multiple independent pendingIntents


        // publish the notification
        NotificationManager notification = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        int id = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? notification.getActiveNotifications().length : getUniqueId(); // ensure unique id
        notification.notify(id,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN ? builder.build() : builder.getNotification()
        );
    }

    /**
     * Share the clipboard content
     */
    public void onShare(View view) {
        // create a SEND text intent with the clipboard content
        Intent sendIntent = new Intent();
        sendIntent.setAction(Intent.ACTION_SEND);
        sendIntent.putExtra(Intent.EXTRA_TEXT, v_content.getText().toString());
        sendIntent.setType("text/plain");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            // not sure what it does, but maybe it allows to share images (even if the app can't display them)
            sendIntent.setClipData(clipboard.getPrimaryClip());
        }

        // start a chooser, use the label as title
        startActivity(Intent.createChooser(sendIntent, v_label.getText()));
    }

    /**
     * Clears the clipboard content
     */
    public void onClear(View view) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // the easy way, just call 'clear'
            clipboard.clearPrimaryClip();
        } else {
            // the not-so-easy way, manually set as empty
            noListener = true;
            v_content.setText("");
            v_label.setText("");
            noListener = false;
            inputToClipboard();
        }
    }

    /**
     * Open the configure screen
     */
    public void onConfigure(View view) {
        // setup
        View content = getLayoutInflater().inflate(R.layout.configuration, null);
        CheckBox autokeyboard = content.findViewById(R.id.autokeyboard);
        autokeyboard.setChecked(prefs.isShowKeyboard());
        autokeyboard.setOnCheckedChangeListener((checkbox, checked) -> prefs.setShowKeyboard(checked));

        // show
        new AlertDialog.Builder(this)
                .setIcon(R.mipmap.ic_launcher)
                .setTitle(R.string.descr_configure)
                .setView(content)
                .show();
    }

    /**
     * Show the about screen
     */
    public void onInfo(View view) {
        // before-setup
        View content = getLayoutInflater().inflate(R.layout.about, null);

        // show
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setIcon(R.mipmap.ic_launcher)
                .setTitle(getString(R.string.descr_info))
                .setView(content)
                .show();

        // after-setup
        for (int id : new int[]{R.id.blog, R.id.github, R.id.playstore}) {
            // setup all three buttons (its tag is the url)
            View button = content.findViewById(id);
            button.setOnClickListener(btn -> {
                // click to open in browser
                String url = btn.getTag().toString();
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                } catch (Exception e) {
                    // on error, like no browser, invoke long click (set the url in the clipboard)
                    btn.performLongClick();
                }
                dialog.dismiss();
            });
            button.setOnLongClickListener(btn -> {
                // long click to set in clipboard
                clipboard.setPrimaryClip(ClipData.newPlainText("TrianguloY", btn.getTag().toString()));
                dialog.dismiss();
                return true;
            });
        }
    }


    // ------------------- transfer -------------------

    /**
     * Sets the input values to the clipboard ones
     * Ensures it doesn't fire inputToClipboard
     */
    private void clipboardToInput() {
        if (noListener) return;
        noListener = true;

        // get
        ClipData primaryClip = clipboard.getPrimaryClip();

        // set
        if (primaryClip == null) {
            // no content
            v_extra.setText(String.format("[%s]", getString(R.string.txt_empty)));
            v_label.setText("");
            v_content.setText("");

            Log.d("CLIPBOARD", "--> null");
        } else {
            // content
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

    /**
     * Sets the clipboard value to the input ones
     * Ensures it doesn't fire clipboardToInput
     */
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

    /**
     * return object?.toString() ?: "";
     *
     * @param object any object, including null
     * @return non null string
     */
    static private String toStringNonNull(Object object) {
        return object == null ? "" : object.toString();
    }

    /**
     * @return a 'unique' id (should be unique unless called at the same millisecond or after a very VERY long time)
     */
    private static int getUniqueId() {
        return Long.valueOf(System.currentTimeMillis()).intValue();
    }

}