/*
 * Copyright (C) 2015 Jorge Ruesga
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ruesga.android.tagedittextview;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.text.Editable;
import android.text.InputType;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.method.KeyListener;
import android.text.style.ImageSpan;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A {@link View} to edit hash tags(#) and user tags(@).
 */
// TODO Add support for RTL
public class TagEditTextView extends LinearLayout {

    private class TagEditText extends EditText {
        public TagEditText(Context context) {
            super(context);
        }

        @Override
        protected void onSelectionChanged(int selStart, int selEnd) {
            super.onSelectionChanged(selStart, selEnd);
            int minSelPos = mTagList.size();
            if (selStart < minSelPos) {
                setSelection(minSelPos);
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (mReadOnly) {
                return false;
            }

            int action = event.getAction();
            switch (action) {
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_DOWN:
                    int x = (int) event.getX();
                    int y = (int) event.getY();

                    // Compute where the user has touched (is in a remove tag area?)
                    int w = getWidth();
                    int x1 = 0, y1 = 0;
                    for (Tag tag : mTagList) {
                        x1 += tag.w;
                        if (x1 > w) {
                            x1 = tag.w;
                            y1 = tag.h;
                        }
                        if ((x > (x1 - tag.w) && x < x1) && (y > y1 && y < (y1 + tag.h))) {
                            if (x >= (x1 - mChipRemoveAreaWidth)) {
                                // User click in a remove tag area
                                if (action == MotionEvent.ACTION_UP) {
                                    onTagRemoveClick(tag);
                                }
                                return true;
                            }
                        }
                    }
                    break;
            }
            return super.onTouchEvent(event);
        }
    }

    public interface OnTagEventListener {
        void onTagCreate(Tag tag);
        void onTagRemove(Tag tag);
    }

    public static class Tag {
        public CharSequence mTag;
        public int mColor;

        private int w;
        private int h;

        private Tag swallowCopy() {
            Tag tag = new Tag();
            tag.mTag = mTag;
            tag.mColor = mColor;
            return tag;
        }
    }

    public enum TAG_MODE {
        HASH,
        USER
    }

    private final TextWatcher mEditListener = new TextWatcher() {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
            if (mLockEdit) {
                return;
            }
            if (count == 0 && start == (mTagList.size() - 1)) {
                mLockEdit = true;
                try {
                    Editable e = mTagEdit.getEditableText();
                    ImageSpan[] spans = e.getSpans(start, start + 1, ImageSpan.class);
                    for (ImageSpan span : spans) {
                        e.removeSpan(span);
                    }
                    Tag tag = mTagList.get(start);
                    mTagList.remove(start);

                    notifyTagRemoved(tag);

                } finally {
                    mLockEdit = false;
                }
            }
        }

        @Override
        public void afterTextChanged(Editable s) {
            // Prevent any pending message to be called
            mHandler.removeMessages(MESSAGE_CREATE_CHIP);

            // If we are removing skip chip creation code
            if (mLockEdit) {
                return;
            }

            // Check if we need to create a new chip
            String text = s.toString();
            int textLength = text.length();
            boolean isCreateChip = false;
            if (textLength > 0) {
                String lastChar = text.substring(textLength - 1);
                isCreateChip = NON_UNICODE_CHAR_PATTERN.matcher(lastChar).matches();
            }
            if (isCreateChip) {
                createChip(s);
            } else if (mTriggerTagCreationThreshold > 0) {
                int start = mTagList.size();
                String tagText = s.subSequence(start, textLength).toString().trim();
                if (tagText.length() >= CREATE_CHIP_LENGTH_THRESHOLD) {
                    mHandler.removeMessages(MESSAGE_CREATE_CHIP);
                    mHandler.sendMessageDelayed(
                            mHandler.obtainMessage(MESSAGE_CREATE_CHIP),
                            mTriggerTagCreationThreshold);
                }
            }
        }
    };

    private Handler.Callback mTagMessenger = new Handler.Callback() {
        @Override
        public boolean handleMessage(Message msg) {
            switch (msg.what){
                case MESSAGE_CREATE_CHIP:
                    Editable s = mTagEdit.getEditableText();
                    s.insert(s.length(), CHIP_REPLACEMENT_CHAR);
                    break;
            }
            return false;
        }
    };

    private static final Pattern HASH_TAG_PATTERN = Pattern.compile(
            "(?<=^|(?<=[^a-zA-Z0-9-_\\\\.]))#([\\p{L}]+[\\p{L}0-9_]+)");
    private static final Pattern USER_TAG_PATTERN = Pattern.compile(
            "(?<=^|(?<=[^a-zA-Z0-9-_\\\\.]))@([\\p{L}]+[\\p{L}0-9_]+)");
    private static final Pattern NON_UNICODE_CHAR_PATTERN = Pattern.compile("[^\\p{L}0-9_#@]");

    private static final char[] VALID_TAGS = new char[]{'#', '@'};

    private static final String CHIP_SEPARATOR_CHAR = " ";
    private static final String CHIP_REPLACEMENT_CHAR = ".";

    private static final int MESSAGE_CREATE_CHIP = 0;

    private static final long CREATE_CHIP_LENGTH_THRESHOLD = 3L;
    private static final long CREATE_CHIP_DEFUALT_DELAYED_TIMEOUT = 1500L;

    private static float ONE_PIXEL = 0f;
    private static final Typeface CHIP_TYPEFACE = Typeface.create("Helvetica", Typeface.BOLD);
    private static final String CHIP_REMOVE_TEXT = " | x ";
    private Paint mChipBgPaint;
    private Paint mChipFgPaint;
    private int mChipRemoveAreaWidth;

    private TagEditText mTagEdit;
    private List<Tag> mTagList = new ArrayList<>();

    private long mTriggerTagCreationThreshold;
    private boolean mReadOnly;
    private KeyListener mEditModeKeyListener;
    private Drawable mEditModeBackground;

    private TAG_MODE mDefaultTagMode;

    private Handler mHandler;
    private final List<OnTagEventListener> mTagEventCallBacks = new ArrayList<>();

    private boolean mLockEdit;

    public TagEditTextView(Context ctx) {
        super(ctx, null, 0);
        init();
    }

    public TagEditTextView(Context ctx, AttributeSet attrs) {
        super(ctx, attrs, 0);
        init();
    }

    public TagEditTextView(Context ctx, AttributeSet attrs, int defStyleAttr) {
        super(ctx, attrs, defStyleAttr);
        init();
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public TagEditTextView(Context ctx, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(ctx, attrs, defStyleAttr, defStyleRes);
        init();
    }

    private void init() {
        mHandler = new Handler(mTagMessenger);
        mTriggerTagCreationThreshold = CREATE_CHIP_DEFUALT_DELAYED_TIMEOUT;

        // Create the internal EditText that holds the tag logic
        mTagEdit = new TagEditText(getContext());
        mTagEdit.setLayoutParams(new LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, 0));
        mTagEdit.setInputType(InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        mTagEdit.addTextChangedListener(mEditListener);
        mTagEdit.setTextIsSelectable(false);
        mTagEdit.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        mTagEdit.setOnFocusChangeListener(new OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                // Remove any pending message
                mHandler.removeMessages(MESSAGE_CREATE_CHIP);
            }
        });
        mTagEdit.setCustomSelectionActionModeCallback(new ActionMode.Callback() {
            @Override
            public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                return false;
            }

            @Override
            public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
                return false;
            }

            @Override
            public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                return false;
            }

            @Override
            public void onDestroyActionMode(ActionMode mode) {

            }
        });
        addView(mTagEdit);

        // Configure the window mode for landscape orientation, to disallow hide the
        // EditText control, and show characters instead of chips
        int orientation = getContext().getResources().getConfiguration().orientation;
        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            Window window = ((Activity)getContext()).getWindow();
            if (window != null) {
                window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);
                mTagEdit.setImeOptions(EditorInfo.IME_FLAG_NO_EXTRACT_UI);
            }
        }

        // Save the keyListener for later restore
        mEditModeKeyListener = mTagEdit.getKeyListener();
        mEditModeBackground = mTagEdit.getBackground();

        // Initialize resources for chips
        mChipBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        mChipFgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mChipFgPaint.setTextSize(mTagEdit.getTextSize());
        if (CHIP_TYPEFACE != null) {
            mChipFgPaint.setTypeface(CHIP_TYPEFACE);
        }
        mChipFgPaint.setColor(Color.WHITE);
        mChipFgPaint.setTextAlign(Paint.Align.LEFT);

        // Calculate the width area used to remove the tag in the chip
        mChipRemoveAreaWidth = (int) (mChipFgPaint.measureText(CHIP_REMOVE_TEXT) + 0.5f);

        if (ONE_PIXEL <= 0) {
            Resources res = getResources();
            ONE_PIXEL = TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP, 1, res.getDisplayMetrics());
        }

        // By default we enabled the edition of tags
        mReadOnly = false;
        mDefaultTagMode = TAG_MODE.HASH;
    }

    private void onTagRemoveClick(final Tag tag) {
        Editable s = mTagEdit.getEditableText();
        int position = mTagList.indexOf(tag);
        mLockEdit = true;
        mTagList.remove(position);
        ImageSpan[] spans = s.getSpans(position, position + 1, ImageSpan.class);
        for (ImageSpan span : spans) {
            s.removeSpan(span);
        }
        s.delete(position, position + 1);
        mLockEdit = false;

        mHandler.post(new Runnable() {
            @Override
            public void run() {
                notifyTagRemoved(tag);
            }
        });
    }

    public boolean getReadOnlyMode() {
        return mReadOnly;
    }

    public void setReadOnlyMode(boolean readOnly) {
        mReadOnly = readOnly;
        mTagEdit.setCursorVisible(!mReadOnly);
        mTagEdit.setFocusable(!mReadOnly);
        mTagEdit.setFocusableInTouchMode(!mReadOnly);
        mTagEdit.setKeyListener(mReadOnly ? null : mEditModeKeyListener);
        mTagEdit.setBackground(mReadOnly ? null : mEditModeBackground);
    }

    public TAG_MODE getDefaultTagMode() {
        return mDefaultTagMode;
    }

    public void setDefaultTagMode(TAG_MODE defaultTagMode) {
        this.mDefaultTagMode = defaultTagMode;
    }

    public long getTriggerTagCreationThreshold() {
        return mTriggerTagCreationThreshold;
    }

    public void setTriggerTagCreationThreshold(long triggerTagCreationThreshold) {
        this.mTriggerTagCreationThreshold = triggerTagCreationThreshold;
    }

    public void addTagEventListener(OnTagEventListener callback) {
        mTagEventCallBacks.add(callback);
    }

    public void removeTagEventListener(OnTagEventListener callback) {
        mTagEventCallBacks.remove(callback);
    }


    public Tag[] getTags() {
        Tag[] tags = new Tag[mTagList.size()];
        int count = mTagList.size();
        for (int i = 0; i < count; i++) {
            tags[i] = mTagList.get(i).swallowCopy();
        }
        return tags;
    }

    public void setTags(Tag[] tags) {
        // Delete any existent data
        mTagEdit.getEditableText().clearSpans();
        int count = mTagList.size() - 1;
        for (int i = count; i >= 0; i--) {
            onTagRemoveClick(mTagList.get(i));
        }
        mTagEdit.setText("");

        // Filter invalid tags
        for (Tag tag : tags) {
            Matcher hashTagMatcher = HASH_TAG_PATTERN.matcher(tag.mTag);
            Matcher userTagMatcher = USER_TAG_PATTERN.matcher(tag.mTag);
            if (hashTagMatcher.matches() || userTagMatcher.matches()) {
                mTagList.add(tag);
            }
        }

        // Build the spans
        SpannableStringBuilder builder;
        if (tags.length > 0) {
            final String text = String.format("%" + tags.length + "s", CHIP_SEPARATOR_CHAR)
                    .replaceAll(CHIP_SEPARATOR_CHAR, CHIP_REPLACEMENT_CHAR);
            builder = new SpannableStringBuilder(text);
        } else {
            builder = new SpannableStringBuilder("");
        }

        int pos = 0;
        for (final Tag tag : mTagList) {
            Bitmap b = createTagChip(tag);
            tag.w = b.getWidth();
            tag.h = b.getHeight();
            ImageSpan span = new ImageSpan(getContext(), b, ImageSpan.ALIGN_BOTTOM);
            builder.setSpan(span, pos, pos + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            pos++;

            notifyTagCreated(tag);
        }
        mTagEdit.setText(builder);
        mTagEdit.setSelection(mTagEdit.getText().length());
    }

    private void createChip(Editable s) {
        String text = s.toString();
        int start = mTagList.size();
        int end = text.length();
        String tagText = s.subSequence(start, end).toString().trim();
        tagText = NON_UNICODE_CHAR_PATTERN.matcher(tagText).replaceAll("");
        if (tagText.isEmpty()) {
            // User is still writing
            return;
        }
        if (Arrays.binarySearch(VALID_TAGS, tagText.charAt(0)) < 0) {
            char tag = mDefaultTagMode == TAG_MODE.HASH ? VALID_TAGS[0] : VALID_TAGS[1];
            tagText = tag + tagText;
        }

        // Replace the new tag
        mLockEdit = true;
        s.replace(start, end, CHIP_REPLACEMENT_CHAR);
        mLockEdit = false;

        // Create the tag and its spannable
        final Tag tag = new Tag();
        tag.mTag = NON_UNICODE_CHAR_PATTERN.matcher(tagText).replaceAll("");
        Bitmap b = createTagChip(tag);
        ImageSpan span = new ImageSpan(getContext(), b);
        s.setSpan(span, start, start + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        tag.w = b.getWidth();
        tag.h = b.getHeight();
        mTagList.add(tag);


        notifyTagCreated(tag);
    }

    private Bitmap createTagChip(Tag tag) {
        // Create the tag string (prepend/append spaces to better ux). Create a clickable
        // area for deleting the tag in non-readonly mode
        String tagText = String.format(" %s " + (mReadOnly ? "" : CHIP_REMOVE_TEXT), tag.mTag);

        // Create a new color for the tag if necessary
        if (tag.mColor == 0) {
            tag.mColor = newRandomColor();
        }
        mChipBgPaint.setColor(tag.mColor);

        // Measure the chip rect
        int padding = (int) ONE_PIXEL * 2;
        int w = (int) (mChipFgPaint.measureText(tagText) + 0.5f) + (padding * 2);
        float baseline = (int) (-mChipFgPaint.ascent() + 0.5f + (padding / 2));
        int h = (int) (baseline + mChipFgPaint.descent() + 0.5f) + (padding * 2);

        // Create the bitmap
        Bitmap bitmap = Bitmap.createBitmap(w + padding, h + padding, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        // Draw the bitmap
        canvas.drawRoundRect(new RectF(0, (padding / 2), w, h), 6, 6, mChipBgPaint);
        canvas.drawText(tagText, (padding / 2), baseline, mChipFgPaint);
        return bitmap;
    }

    public static int newRandomColor() {
        int random = (int) (Math.floor(Math.random() * 0xff0f0f0f) + 0xff000000);
        int color = Color.argb(0xff, Color.red(random), Color.green(random), Color.blue(random));

        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        hsv[2] *= 0.8f; // value component
        color = Color.HSVToColor(hsv);
        return color;
    }

    private void notifyTagCreated(Tag tag) {
        Tag copy = tag.swallowCopy();
        for (OnTagEventListener cb : mTagEventCallBacks) {
            cb.onTagCreate(copy);
        }
    }

    private void notifyTagRemoved(Tag tag) {
        Tag copy = tag.swallowCopy();
        for (OnTagEventListener cb : mTagEventCallBacks) {
            cb.onTagRemove(copy);
        }
    }
}
