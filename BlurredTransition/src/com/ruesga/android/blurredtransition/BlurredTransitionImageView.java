package com.ruesga.android.blurredtransition;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.support.v8.renderscript.Allocation;
import android.support.v8.renderscript.Element;
import android.support.v8.renderscript.RSRuntimeException;
import android.support.v8.renderscript.RenderScript;
import android.support.v8.renderscript.RenderScript.ContextType;
import android.support.v8.renderscript.ScriptIntrinsicBlur;
import android.util.AttributeSet;
import android.util.Log;
import android.view.animation.Animation;
import android.view.animation.LinearInterpolator;
import android.view.animation.Transformation;
import android.view.animation.Animation.AnimationListener;
import android.widget.ImageView;

public class BlurredTransitionImageView extends ImageView {
    
    private static final String TAG = "BlurredTransitionImageView";

    private static final int DEFAULT_BLUR_MAX_RADIUS = 25;
    private static final int DEFAULT_TRANSITION_DURATION = 800;

    private class BlurredTransitionAnimation extends Animation {
        private final RenderScript mRs;
        private final ScriptIntrinsicBlur mScript;
        private int mLastBlurRadius;
        private int mMaxRadius;

        private final Bitmap mSrc;
        private final Bitmap mDst;

        public BlurredTransitionAnimation(RenderScript rs, Bitmap src, Bitmap dst) {
            super();
            mRs = rs;
            mScript = ScriptIntrinsicBlur.create(mRs, Element.U8_4(mRs));
            mLastBlurRadius = 1;
            mSrc = src;
            mDst = dst;
            mMaxRadius = DEFAULT_TRANSITION_DURATION;

            setInterpolator(new LinearInterpolator());
            setFillAfter(true);
            setDuration(DEFAULT_TRANSITION_DURATION);
            setRepeatMode(0);
        }

        public void setMaxRadius(int maxRadius) {
            mMaxRadius = maxRadius;
        }

        @Override
        protected void applyTransformation(float interpolatedTime, Transformation t) {
            Bitmap bitmap = interpolatedTime >= 0.4f ? mDst : mSrc;
            float delta = 0.5f - Math.abs(interpolatedTime - 0.5f);
            int radius = (int) ((mMaxRadius * delta) / 0.5f);
            if (radius == 0) {
                setInternalBitmap(bitmap);
                return;
            }
            if (radius == mLastBlurRadius) {
                return;
            }

            Bitmap out = bitmap.copy(bitmap.getConfig(), true);
            final Allocation input = Allocation.createFromBitmap(mRs, out,
                    Allocation.MipmapControl.MIPMAP_NONE, Allocation.USAGE_SCRIPT);
            final Allocation output = Allocation.createTyped(mRs, input.getType());
            mScript.setRadius(radius);
            mScript.setInput(input);
            mScript.forEach(output);
            output.copyTo(out);
            setInternalBitmap(out);

            mLastBlurRadius = radius;
        }
    }

    private RenderScript mRs;
    private BlurredTransitionAnimation mBlurAnim;

    private int mMaxRadius;
    private int mTransitionDuration;

    public BlurredTransitionImageView(Context context) {
        this(context, null, 0);
    }

    public BlurredTransitionImageView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public BlurredTransitionImageView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        try {
            mRs = RenderScript.create(context, ContextType.NORMAL);
        } catch (RSRuntimeException ex) {
            Log.w(TAG, "RenderScript is not supported", ex);
        }

        TypedArray a = context.obtainStyledAttributes(attrs,
                R.styleable.BlurredTransitionImageView);
        setMaxRadius(a.getInt(R.styleable.BlurredTransitionImageView_maxRadius,
                DEFAULT_BLUR_MAX_RADIUS));
        mTransitionDuration = a.getInt(R.styleable.BlurredTransitionImageView_duration,
                DEFAULT_TRANSITION_DURATION);
        a.recycle();
    }

    public int getMaxRadius() {
        return mMaxRadius;
    }

    public void setMaxRadius(int maxRadius) {
        mMaxRadius = maxRadius;
        if (mMaxRadius < 0) mMaxRadius = 0;
        else if (mMaxRadius > 25) mMaxRadius = 25;
    }

    public int getTransitionDuration() {
        return mTransitionDuration;
    }

    public void setTransitionDuration(int transitionDuration) {
        mTransitionDuration = transitionDuration;
    }

    @Override
    public void setImageResource(int resId) {
        performBlurAnimation(BitmapFactory.decodeResource(getResources(), resId));
    }

    @Override
    public void setImageDrawable(Drawable drawable) {
        if (drawable instanceof BitmapDrawable) {
            performBlurAnimation(((BitmapDrawable) drawable).getBitmap());
        }
        super.setImageDrawable(drawable);
    }

    @Override
    public void setImageBitmap(Bitmap bm) {
        performBlurAnimation(bm);
    }

    private void setInternalBitmap(Bitmap bm) {
        super.setImageDrawable(new BitmapDrawable(getContext().getResources(), bm));
    }

    private synchronized void performBlurAnimation(Bitmap dst) {
        if (mRs == null) {
            Log.w(TAG, "RenderScript is not supported");
            setInternalBitmap(dst);
            return;
        }

        if (getDrawable() != null) {
            if (mBlurAnim != null) {
                mBlurAnim.cancel();
                mBlurAnim = null;
            }
            final Bitmap src = ((BitmapDrawable) getDrawable()).getBitmap();
            mBlurAnim = new BlurredTransitionAnimation(mRs, src, dst);
            mBlurAnim.setDuration(mTransitionDuration);
            mBlurAnim.setMaxRadius(mMaxRadius);
            mBlurAnim.setAnimationListener(new AnimationListener() {
                @Override
                public void onAnimationStart(Animation animation) {}
                @Override
                public void onAnimationRepeat(Animation animation) {}
    
                @Override
                public void onAnimationEnd(Animation animation) {
                    mBlurAnim = null;
                    src.recycle();
                }
            });
            startAnimation(mBlurAnim);
        } else {
            setInternalBitmap(dst);
        }
    }
}
