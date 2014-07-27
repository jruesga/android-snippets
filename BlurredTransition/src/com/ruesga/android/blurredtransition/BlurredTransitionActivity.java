/*
 * Copyright (C) 2014 Jorge Ruesga
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

package com.ruesga.android.blurredtransition;

import android.app.Activity;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;

public class BlurredTransitionActivity extends Activity {

    private Button mButton;
    private BlurredTransitionImageView mImageView;

    private int mCurrentImage = 0;

    private static final int[] DRAWABLES_RES_IDS = {
            R.drawable.horseshoe_bend,
            R.drawable.lone_pine_sunset,
            R.drawable.moving_rock2,
            R.drawable.sierra_heavens
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        mButton = (Button) findViewById(R.id.button);
        mImageView = (BlurredTransitionImageView) findViewById(R.id.image);
        mButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                mCurrentImage++;
                if (mCurrentImage >= DRAWABLES_RES_IDS.length) {
                    mCurrentImage = 0;
                }
                mImageView.setImageBitmap(decodeSampledBitmapFromResource(getResources(),
                        DRAWABLES_RES_IDS[mCurrentImage], mImageView.getWidth(),
                        mImageView.getHeight()));
            }
        });
    }

    private static Bitmap decodeSampledBitmapFromResource(Resources res, int resId,
            int reqWidth, int reqHeight) {
        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeResource(res, resId, options);
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;
        if (height > reqHeight || width > reqWidth) {
            final int halfHeight = height / 2;
            final int halfWidth = width / 2;
            while ((halfHeight / inSampleSize) > reqHeight &&
                    (halfWidth / inSampleSize) > reqWidth) {
                inSampleSize *= 2;
            }
        }
        options.inSampleSize = inSampleSize;
        options.inJustDecodeBounds = false;
        return BitmapFactory.decodeResource(res, resId, options);
    }
}
