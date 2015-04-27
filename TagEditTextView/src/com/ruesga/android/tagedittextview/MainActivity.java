package com.ruesga.android.tagedittextview;

import android.app.Activity;
import android.os.Bundle;

import com.ruesga.android.tagedittextview.TagEditTextView.Tag;

public class MainActivity extends Activity {

    TagEditTextView mChips;
    TagEditTextView mReadOnlyChips;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Tag[] tags = new Tag[2];
        tags[0] = new Tag();
        tags[0].mTag = "@jruesga";
        tags[1] = new Tag();
        tags[1].mTag = "#ios";

        mChips = (TagEditTextView) findViewById(R.id.chips);
        mChips.setReadOnlyMode(false);
        mChips.addTagEventListener(new TagEditTextView.OnTagEventListener() {
            @Override
            public void onTagCreate(Tag tag) {
                mReadOnlyChips.setTags(mChips.getTags());
            }

            @Override
            public void onTagRemove(Tag tag) {
                mReadOnlyChips.setTags(mChips.getTags());
            }
        });

        mReadOnlyChips = (TagEditTextView) findViewById(R.id.readonly_chips);
        mReadOnlyChips.setReadOnlyMode(true);


        mChips.setTags(tags);
    }
}
