package com.trianguloy.clipboardeditor;

import android.text.Editable;
import android.text.TextWatcher;

/**
 * {@link TextWatcher} is an interface, with 3 methods, and you usually don't need all of them. This class overrides them all so you don't need to
 */
public class SimpleTextWatcher implements TextWatcher {
    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {
    }

    @Override
    public void afterTextChanged(Editable s) {
    }
}
