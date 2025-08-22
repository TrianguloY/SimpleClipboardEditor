package com.trianguloy.clipboardeditor;

import static android.content.pm.PackageManager.PERMISSION_GRANTED;
import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static com.trianguloy.clipboardeditor.Preferences.Pref.CAPITALIZE;
import static com.trianguloy.clipboardeditor.Preferences.Pref.SHOW_KEYBOARD;
import static com.trianguloy.clipboardeditor.Preferences.Pref.STATISTICS;
import static com.trianguloy.clipboardeditor.Preferences.Pref.SYNC_BTN_CI;
import static com.trianguloy.clipboardeditor.Preferences.Pref.SYNC_BTN_IC;
import static com.trianguloy.clipboardeditor.Preferences.Pref.SYNC_EXTERNAL;
import static com.trianguloy.clipboardeditor.Preferences.Pref.SYNC_INPUT;
import static com.trianguloy.clipboardeditor.Preferences.Pref.SYNC_PAUSE;
import static com.trianguloy.clipboardeditor.Preferences.Pref.SYNC_START;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

/**
 * The main activity, a clipboard editor
 */
public class Editor extends Activity {
    private static final String CHANNEL_ID = "text"; // id for the channel for notifications
    private static final int NOTIFICATIONS_REQUEST_CODE = 1;

    // ------------------- data -------------------

    // classes
    private ClipboardManager clipboard; // system clipboard
    private NotificationManager notification; // system notifications
    private Preferences prefs; // preferences wrapper

    // views
    private EditText v_content; // content input
    private EditText v_label; // label input
    private TextView v_extra; // extra text

    private TextView v_statistics; // statistics text

    // internal data
    private boolean noListener = false; // to avoid firing clipboardToInput and inputToClipboard recursively
    private boolean syncOnHasFocus = true; // to run when app starts only once

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
        v_statistics = findViewById(R.id.statistics);

        // descriptions
        for (var viewId : new int[]{R.id.notify, R.id.share, R.id.clear, R.id.configure, R.id.info, R.id.sync_to, R.id.sync_from}) {
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

        // statistics
        v_content.addTextChangedListener(new SimpleTextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                computeStatistics(s);
            }
        });
        computeStatistics(v_content.getText());
        v_statistics.setVisibility(prefs.is(STATISTICS) ? VISIBLE : GONE);

        // enable clipboard to input
        clipboard.addPrimaryClipChangedListener(() -> {
            if (prefs.is(SYNC_EXTERNAL)) {
                clipboardToInput();
            }
        });

        // enable input to clipboard
        var watcher = new SimpleTextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                if (prefs.is(SYNC_INPUT)) {
                    // sync on input
                    inputToClipboard();
                }
            }
        };
        v_content.addTextChangedListener(watcher);
        v_label.addTextChangedListener(watcher);

        // manual buttons
        findViewById(R.id.sync_to).setVisibility(prefs.is(SYNC_BTN_IC) ? VISIBLE : GONE);
        findViewById(R.id.sync_from).setVisibility(prefs.is(SYNC_BTN_CI) ? VISIBLE : GONE);

        // auto-update result
        v_content.addTextChangedListener(new SimpleTextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                var intent = new Intent();
                intent.putExtra(getPackageName(), inputAsPrimaryClip());
                setResult(RESULT_OK, intent);
            }
        });

        // show keyboard if enabled in settings
        if (prefs.is(SHOW_KEYBOARD)) {
            if (v_content.requestFocus()) {
                ((InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE))
                        .showSoftInput(v_content, InputMethodManager.SHOW_IMPLICIT);
            }
        }

        // capitalize input state if enabled in settings
        setCapitalizeState(prefs.is(CAPITALIZE));

        // start intent
        parseIntent(getIntent());
        setIntent(null);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            if (syncOnHasFocus) {
                syncOnHasFocus = false;

                // when windows is focused, update clipboard
                // this is the moment the clipboard is available after the app starts
                if (prefs.is(SYNC_START)) {
                    clipboardToInput();
                }
            }
        } else {
            if (prefs.is(SYNC_PAUSE)) inputToClipboard();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // on new intent (probably from a notification or from a sent text) load it
        parseIntent(intent);
    }

    /**
     * @param intent intent to parse for clipboard data
     */
    private void parseIntent(Intent intent) {
        if (intent == null) return;
        ClipData data = null;

        // set by ourselves
        if (intent.hasExtra(getPackageName()))
            data = intent.getParcelableExtra(getPackageName());
        // sent
        if (data == null && intent.hasExtra(Intent.EXTRA_TEXT))
            data = ClipData.newPlainText(getString(R.string.clip_sent), intent.getStringExtra(Intent.EXTRA_TEXT));

        // set
        if (data != null) {
            clipToInput(data);
            syncOnHasFocus = false;
        }
    }

    /**
     * Update
     */
    private void setCapitalizeState(boolean state) {
        if (state) {
            // set flag
            v_content.setInputType(v_content.getInputType() | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
            v_label.setInputType(v_label.getInputType() | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        } else {
            // remove flag
            v_content.setInputType(v_content.getInputType() & ~InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
            v_label.setInputType(v_label.getInputType() & ~InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        }
    }

    // ------------------- buttons -------------------

    /**
     * Shows a notification with the clipboard content
     */
    public void onNotification(View ignored) {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !notification.areNotificationsEnabled()) {
            // request notifications permission
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, NOTIFICATIONS_REQUEST_CODE);
            return;
        }

        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // setup a notification channel in Oreo+
            var channel = new NotificationChannel(CHANNEL_ID, getString(R.string.channel_name), NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription(getString(R.string.channel_description));
            notification.createNotificationChannel(channel);

            builder = new Notification.Builder(this, CHANNEL_ID);
        } else {
            // no notification channel before Oreo
            builder = new Notification.Builder(this);
        }

        // sets the label as notification title (if any), the content and icon
        if (!v_label.getText().isEmpty()) builder.setContentTitle(v_label.getText());
        builder.setContentText(v_content.getText());
        builder.setSmallIcon(R.drawable.ic_notification);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            // when allowed, set the content as big text (improved display)
            builder.setStyle(new Notification.BigTextStyle()
                    .bigText(v_content.getText()));
        }

        // sets the intent for when you click the notification. It will open the app with the current clipboard content
        var intent = new Intent(this, Editor.class);
        intent.putExtra(getPackageName(), inputAsPrimaryClip());
        builder.setContentIntent(PendingIntent.getActivity(this, getUniqueId(), intent, PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0))); // change the requestCode with an unique id for multiple independent pendingIntents


        // publish the notification
        var notification = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        var id = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? notification.getActiveNotifications().length : getUniqueId(); // ensure unique id
        notification.notify(id,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN ? builder.build() : builder.getNotification()
        );
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == NOTIFICATIONS_REQUEST_CODE) {
            if (grantResults.length >= 1 && grantResults[0] == PERMISSION_GRANTED) {
                onNotification(null);
            }
            Toast.makeText(this, R.string.noPermission, Toast.LENGTH_SHORT).show();
            return;
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    /**
     * Share the clipboard content
     */
    public void onShare(View view) {
        // create a SEND text intent with the clipboard content
        var sendIntent = new Intent();
        sendIntent.setAction(Intent.ACTION_SEND);
        sendIntent.putExtra(Intent.EXTRA_TEXT, v_content.getText().toString());
        sendIntent.setType("text/plain");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            // not sure what it does, but maybe it allows to share images (even if the app can't display them)
            sendIntent.setClipData(inputAsPrimaryClip());
        }

        // start a chooser, use the label as title
        startActivity(Intent.createChooser(sendIntent, v_label.getText()));
    }

    /**
     * Clears the clipboard content
     */
    public void onClear(View view) {
        v_content.setText("");
        v_label.setText("");
    }

    /**
     * Open the configure screen
     */
    public void onConfigure(View view) {
        // setup
        var content = getLayoutInflater().inflate(R.layout.configuration, null);

        for (var preferenceSwitch : List.of(
                new PreferenceSwitch(SHOW_KEYBOARD, R.id.autokeyboard, null),
                new PreferenceSwitch(CAPITALIZE, R.id.capitalize, this::setCapitalizeState),
                new PreferenceSwitch(STATISTICS, R.id.statistics, checked -> {
                    if (checked) computeStatistics(v_content.getEditableText());
                    v_statistics.setVisibility(checked ? VISIBLE : GONE);
                }),
                new PreferenceSwitch(SYNC_START, R.id.sync_start, null),
                new PreferenceSwitch(SYNC_BTN_CI, R.id.sync_btn_ci, checked -> findViewById(R.id.sync_from).setVisibility(checked ? VISIBLE : GONE)),
                new PreferenceSwitch(SYNC_EXTERNAL, R.id.sync_external, null),
                new PreferenceSwitch(SYNC_INPUT, R.id.sync_input, null),
                new PreferenceSwitch(SYNC_BTN_IC, R.id.sync_btn_ic, checked -> findViewById(R.id.sync_to).setVisibility(checked ? VISIBLE : GONE)),
                new PreferenceSwitch(SYNC_PAUSE, R.id.sync_pause, null)
        )) {
            var switchView = content.<Switch>findViewById(preferenceSwitch.id);
            switchView.setChecked(prefs.is(preferenceSwitch.preference));
            switchView.setOnCheckedChangeListener((checkbox, checked) -> {
                prefs.set(preferenceSwitch.preference, checked);
                if (preferenceSwitch.onChange != null) preferenceSwitch.onChange.onChange(checked);
            });
        }

        // show
        new AlertDialog.Builder(this)
                .setIcon(R.mipmap.ic_launcher)
                .setTitle(R.string.descr_configure)
                .setView(content)
                .show();
    }

    record PreferenceSwitch(Preferences.Pref preference, int id, OnPrefChange onChange) {
        interface OnPrefChange {
            void onChange(boolean state);
        }
    }

    /**
     * Show the about screen
     */
    public void onInfo(View view) {
        // before-setup
        var content = getLayoutInflater().inflate(R.layout.about, null);

        // show
        var dialog = new AlertDialog.Builder(this)
                .setIcon(R.mipmap.ic_launcher)
                .setTitle(getString(R.string.descr_info))
                .setView(content)
                .show();

        // after-setup
        for (var id : new int[]{R.id.blog, R.id.github, R.id.playstore}) {
            // setup all three buttons (its tag is the url)
            var button = content.findViewById(id);
            button.setOnClickListener(btn -> {
                // click to open in browser
                var url = btn.getTag().toString();
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                } catch (Exception e) {
                    // on error, like no browser, invoke long click (set the url in the clipboard)
                    btn.performLongClick();
                }
                dialog.dismiss();
            });
            button.setOnLongClickListener(btn -> {
                // long click to set in input
                v_label.setText("TrianguloY");
                v_content.setText(btn.getTag().toString());
                dialog.dismiss();
                return true;
            });
        }
    }

    /**
     * @see this#clipboardToInput
     */
    public void inputFromClipboard(View view) {
        clipboardToInput();
    }

    /**
     * @see this#inputToClipboard()
     */
    public void inputToClipboard(View view) {
        inputToClipboard();
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
        clipToInput(clipboard.getPrimaryClip());

        noListener = false;
    }

    /**
     * Sets the inputs to the values of the clipdata
     */
    private void clipToInput(ClipData clip) {

        // set
        if (clip == null) {
            // no content
            v_extra.setText(String.format("[%s]", getString(R.string.txt_empty)));
            v_label.setText("");
            v_content.setText("");

            Log.d("CLIPBOARD", "--> null");
        } else {
            // content
            var description = clip.getDescription();

            // mimetype
            v_extra.setText(R.string.label_mimetype);
            var empty = true;
            for (var i = 0; i < description.getMimeTypeCount(); i++) {
                if (!empty) v_extra.append(" -");
                empty = false;
                v_extra.append(" " + description.getMimeType(i));
            }
            if (empty) v_extra.append(getString(R.string.txt_empty));

            // item count
            var itemCount = clip.getItemCount();
            if (itemCount > 1) v_extra.append(getString(R.string.txt_itemcount) + itemCount);

            // label
            var label = toStringNonNull(description.getLabel());
            if (!toStringNonNull(v_label.getText()).equals(label)) {
                v_label.setText(label);
                if (v_label.hasFocus()) v_label.setSelection(v_label.getText().length());
            }

            // text
            var content = toStringNonNull(clip.getItemAt(0).coerceToText(this));
            if (!toStringNonNull(v_content.getText()).equals(content)) {
                v_content.setText(content);
                if (v_content.hasFocus()) v_content.setSelection(v_content.getText().length());
            }


            Log.d("CLIPBOARD", "--> [" + label + "] " + content);
        }
    }

    /**
     * Sets the clipboard value to the input ones
     * Ensures it doesn't fire clipboardToInput
     */
    private void inputToClipboard() {
        if (noListener) return;
        noListener = true;

        // set
        var clip = inputAsPrimaryClip();
        clipboard.setPrimaryClip(clip);

        Log.d("CLIPBOARD", "Input --> " + clip);

        noListener = false;
    }

    /**
     * Returns the input as primary clip
     */
    private ClipData inputAsPrimaryClip() {
        return ClipData.newPlainText(v_label.getText().toString(), v_content.getText().toString());
    }

    /** Computes and diplays statistics about the textview content */
    private void computeStatistics(Editable editable) {
        if (prefs.is(STATISTICS)) {
            var string = editable.toString();
            var trimmed = string.trim();
            v_statistics.setText(getString(R.string.statistics,
                    /*lines*/ string.isEmpty() ? 0 : string.split("\\n", -1).length,
                    /*words*/ trimmed.isEmpty() ? 0 : trimmed.split("\\s+").length,
                    /*length*/ editable.length() == string.length() ? Integer.toString(string.length()) : string.length() + "(" + editable.length() + ")"
            ));
        }
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