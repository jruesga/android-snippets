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

package com.ruesga.android.refreshanimationdrawable;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.TimeAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.widget.ImageView;

public class RefreshAnimationView extends ImageView {

    public class RefreshAnimationDrawable extends Drawable {

        private static final int ROTATE_ANIMATION_DURATION = 2200;
        private static final float SWEEP_MAX_DELTA = .25f;
        private static final int ARROW_OUT_ANIMATION_DURATION = 550;
        private static final int ARROW_IN_ANIMATION_DURATION = 350;
        private static final int TO_ORIGINAL_ANIMATION_DURATION = 350;
        private static final int TO_MAX_SWEEP_ANIMATION_DURATION = 200;

        private static final float SCALE = 24.0f;
        private static final float STROKE_WIDTH = 2.8f;

        private static final float DEFAULT_START_ANGLE = 0f;
        private static final float DEFAULT_SWEEP_ANGLE = 315f;
        private static final float MAX_SWEEP_ANGLE = 285f;
        private static final float MIN_SWEEP_ANGLE = 15f;
        private static final float TOTAL_SWEEP_ANGLE = MAX_SWEEP_ANGLE - MIN_SWEEP_ANGLE;

        private final Paint mPaint;
        private final Paint mArrowPaint;
        private final Path mArrowPath = new Path();
        private RectF mIntrinsicBounds = null;

        private AnimatorSet mFromStop;
        private AnimatorSet mToStop;

        private float mStartAngle = 0f;
        private float mExtraStartAngle = DEFAULT_START_ANGLE;
        private float mSweepAngle = DEFAULT_SWEEP_ANGLE;
        private float mLastStartAngle = 0f;
        private float mLastExtraStartAngle = 0f;
        private float mLastSweepAngle = 0f;
        private float mCurrentArrowDelta = 1f;

        private RectF mArrowRect;
        private float mArrowPointX;
        private float mArrowPointY;
        private float mStrokeWidth;

        private boolean mRunning;
        private boolean mIndeterminate;
        private boolean mRotating;

        private int mColorActivated;
        private int mColorDisabled;

        public RefreshAnimationDrawable() {
            TypedArray ta = getContext().obtainStyledAttributes(
                    new int[]{android.R.attr.colorControlActivated,
                            android.R.attr.colorControlNormal});
            mColorActivated = ta.getColor(0, Color.WHITE);
            mColorDisabled = ta.getColor(1, Color.WHITE);
            ta.recycle();

            mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            mPaint.setStrokeCap(Paint.Cap.BUTT);
            mPaint.setColor(mColorActivated);
            mPaint.setStyle(Paint.Style.STROKE);

            mArrowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            mArrowPaint.setColor(mColorActivated);
            mArrowPaint.setStyle(Paint.Style.FILL_AND_STROKE);

            createAnimators();
        }

        private void createAnimators() {
            // Indeterminate
            TimeAnimator indeterminateAnimator = new TimeAnimator();
            indeterminateAnimator.setDuration(ROTATE_ANIMATION_DURATION);
            indeterminateAnimator.setRepeatMode(ValueAnimator.RESTART);
            indeterminateAnimator.setRepeatCount(ValueAnimator.INFINITE);
            indeterminateAnimator.setTimeListener(new TimeAnimator.TimeListener() {
                @Override
                public void onTimeUpdate(TimeAnimator animation,
                        long totalTime, long deltaTime) {
                    updateArcAngle(totalTime);
                }
            });

            // From arrow
            ValueAnimator fromArrowAnimator = ValueAnimator.ofFloat(1f, 0f);
            fromArrowAnimator.setDuration(ARROW_OUT_ANIMATION_DURATION);
            fromArrowAnimator.setInterpolator(new AccelerateInterpolator());
            fromArrowAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    mCurrentArrowDelta = (float) animation.getAnimatedValue();
                    createArrowPath(mCurrentArrowDelta);
                    invalidateSelf();
                }
            });

            // From arrow
            ValueAnimator toArrowAnimator = ValueAnimator.ofFloat(0f, 1f);
            toArrowAnimator.setDuration(ARROW_IN_ANIMATION_DURATION);
            toArrowAnimator.setInterpolator(new AccelerateInterpolator());
            toArrowAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    mCurrentArrowDelta = (float) animation.getAnimatedValue();
                    createArrowPath(mCurrentArrowDelta);
                    invalidateSelf();
                }
            });

            // To original position
            ValueAnimator toOriginalAnimator = ValueAnimator.ofFloat(0f, 1f);
            toOriginalAnimator.setDuration(TO_ORIGINAL_ANIMATION_DURATION);
            toOriginalAnimator.setInterpolator(new AccelerateInterpolator());
            toOriginalAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    float delta = (float) animation.getAnimatedValue();
                    mStartAngle = (mLastStartAngle - (mLastStartAngle + 360 * delta)) % 360;
                    mSweepAngle = mLastSweepAngle +
                            ((DEFAULT_SWEEP_ANGLE - mLastSweepAngle) * delta);
                    invalidateSelf();
                }
            });

            // To max sweep position
            ValueAnimator toMaxSweepAnimator = ValueAnimator.ofFloat(0f, 1f);
            toMaxSweepAnimator.setDuration(TO_MAX_SWEEP_ANIMATION_DURATION);
            toMaxSweepAnimator.setInterpolator(new AccelerateInterpolator());
            toMaxSweepAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    float delta = (float) animation.getAnimatedValue();
                    float deltaAngle = ((DEFAULT_SWEEP_ANGLE - MAX_SWEEP_ANGLE) * delta);
                    mStartAngle = deltaAngle;
                    mSweepAngle = DEFAULT_SWEEP_ANGLE - deltaAngle;

                    if (!mRotating) {
                        mLastSweepAngle = mSweepAngle;
                        mLastStartAngle = mStartAngle;
                        mExtraStartAngle = mStartAngle;
                        mLastExtraStartAngle = mStartAngle;
                    }
                    invalidateSelf();
                }
            });

            // Animators
            mFromStop = new AnimatorSet();
            mFromStop.playSequentially(fromArrowAnimator, toMaxSweepAnimator, indeterminateAnimator);
            mFromStop.addListener(new Animator.AnimatorListener() {
                @Override
                public void onAnimationStart(Animator animation) {
                    mExtraStartAngle = 0;
                    mRunning = true;
                    mIndeterminate = true;
                    mRotating = false;
                }

                @Override
                public void onAnimationEnd(Animator animation) {
                    mRotating = false;
                }

                @Override
                public void onAnimationCancel(Animator animation) {

                }

                @Override
                public void onAnimationRepeat(Animator animation) {

                }
            });
            mToStop = new AnimatorSet();
            mToStop.playSequentially(toOriginalAnimator, toArrowAnimator);
            mToStop.addListener(new Animator.AnimatorListener() {
                @Override
                public void onAnimationStart(Animator animation) {
                    mRunning = true;
                    mRotating = false;
                }

                @Override
                public void onAnimationEnd(Animator animation) {
                    mRunning = false;
                }

                @Override
                public void onAnimationCancel(Animator animation) {

                }

                @Override
                public void onAnimationRepeat(Animator animation) {

                }
            });
        }

        private void updateArcAngle(long totalTime) {
            mRotating = true;
            if (totalTime > 0) {
                long deltaTime = totalTime % ROTATE_ANIMATION_DURATION;
                float delta = deltaTime / (float) ROTATE_ANIMATION_DURATION;

                int state = 0;
                if (delta > 0.3f && delta < 0.55f) {
                    // close sweep angle
                    float sweepAngleDelta =
                            (SWEEP_MAX_DELTA - (0.55f - delta)) / SWEEP_MAX_DELTA;
                    mExtraStartAngle = (mLastExtraStartAngle +
                            (TOTAL_SWEEP_ANGLE * sweepAngleDelta)) % 360;
                    mSweepAngle = MAX_SWEEP_ANGLE - (TOTAL_SWEEP_ANGLE * sweepAngleDelta);

                    state = -1;
                } else if (delta > 0.75f) {
                    // open sweep angle
                    float sweepAngleDelta = (SWEEP_MAX_DELTA - (1f - delta)) / SWEEP_MAX_DELTA;
                    mSweepAngle = MIN_SWEEP_ANGLE + (TOTAL_SWEEP_ANGLE * sweepAngleDelta);
                    state = 1;
                }

                float rotation = ((totalTime % ROTATE_ANIMATION_DURATION) * 360);
                mStartAngle = ((rotation / ROTATE_ANIMATION_DURATION) + mExtraStartAngle) % 360;

                mLastStartAngle = mStartAngle;
                mLastSweepAngle = mSweepAngle;
                if (state != -1) {
                    mLastExtraStartAngle = mExtraStartAngle;
                }
                invalidateSelf();
            }
        }

        @Override
        protected void onBoundsChange(Rect bounds) {
            super.onBoundsChange(bounds);

            float scale = bounds.width() / SCALE;
            mStrokeWidth = scale * STROKE_WIDTH;
            mIntrinsicBounds = new RectF(getBounds());
            float diagonal = Math.min(mIntrinsicBounds.width(), mIntrinsicBounds.height());
            mIntrinsicBounds.inset(
                    (mStrokeWidth / 2) + (mIntrinsicBounds.width() - diagonal) / 2,
                    (mStrokeWidth / 2) + (mIntrinsicBounds.height() - diagonal) / 2);

            // Create the arrow triangle
            final float offset = 0.2f * getResources().getDisplayMetrics().density;
            float radius = Math.min(mIntrinsicBounds.width(), mIntrinsicBounds.height()) / 2;
            mArrowPointX = (float)(radius * Math.cos(DEFAULT_SWEEP_ANGLE * Math.PI / 180f))
                    + getBounds().centerX() + offset;
            mArrowPointY = (float) (radius * Math.sin(DEFAULT_SWEEP_ANGLE * Math.PI / 180f))
                    + getBounds().centerY() + offset;

            mArrowRect = new RectF(mArrowPointX - mStrokeWidth, mArrowPointY - mStrokeWidth,
                    mArrowPointX + mStrokeWidth, mArrowPointY + mStrokeWidth);
            createArrowPath(mCurrentArrowDelta);

            mPaint.setStrokeWidth(mStrokeWidth);
        }

        private void createArrowPath(float delta) {
            float maxDistance = mStrokeWidth - (mStrokeWidth / 2f);
            float deltaDistance = mStrokeWidth + (maxDistance * delta);
            float inverseDeltaDistance = (mStrokeWidth / 2f) - (maxDistance * delta);
            float height = mStrokeWidth * 2f * delta;

            mArrowPath.reset();
            mArrowPath.moveTo(mArrowPointX - deltaDistance, mArrowPointY);
            mArrowPath.lineTo(mArrowPointX + deltaDistance, mArrowPointY);
            mArrowPath.lineTo(mArrowPointX + inverseDeltaDistance, mArrowPointY + height);
            mArrowPath.lineTo(mArrowPointX - inverseDeltaDistance, mArrowPointY + height);
        }

        @Override
        public void draw(Canvas canvas) {
            canvas.drawArc(mIntrinsicBounds, mStartAngle, mSweepAngle, false, mPaint);

            // Draw the arrow if necessary
            if (mCurrentArrowDelta > 0f) {
                canvas.save();
                canvas.translate(mArrowRect.centerX(), mArrowRect.centerY());
                canvas.rotate(-45);
                canvas.translate(-1 * mArrowRect.centerX(), -1 * mArrowRect.centerY());
                canvas.drawPath(mArrowPath, mArrowPaint);
                canvas.restore();
            }
        }

        @Override
        public void setAlpha(int alpha) {
            mPaint.setAlpha(alpha);
            mArrowPaint.setAlpha(alpha);
            invalidateSelf();
        }

        @Override
        public void setColorFilter(ColorFilter cf) {
            mPaint.setColorFilter(cf);
            mArrowPaint.setColorFilter(cf);
            invalidateSelf();
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }

        public void setEnabled(boolean enabled) {
            mPaint.setColor(enabled || mRunning ? mColorActivated : mColorDisabled);
            mArrowPaint.setColor(enabled || mRunning ? mColorActivated : mColorDisabled);
        }

        public void start() {
            if (mToStop.isStarted() || mToStop.isRunning()) {
                mToStop.end();
            }
            if (!mFromStop.isStarted() && !mFromStop.isRunning()) {
                mFromStop.start();
            }
        }

        public void stop() {
            if (mFromStop.isStarted() || mFromStop.isRunning()) {
                mFromStop.end();
            }
            if (!mToStop.isStarted() && !mToStop.isRunning()) {
                mToStop.start();
            }
        }

        public void reset() {
            if (mToStop.isStarted() || mToStop.isRunning()) {
                mToStop.end();
            }
            if (mFromStop.isStarted() || mFromStop.isRunning()) {
                mFromStop.end();
            }
            mStartAngle = DEFAULT_START_ANGLE;
            mSweepAngle = DEFAULT_SWEEP_ANGLE;
            mExtraStartAngle = 0;
            invalidateSelf();
        }
    }

    private RefreshAnimationDrawable mDrawable;

    public RefreshAnimationView(Context context) {
        this(context, null, 0, 0);
    }

    public RefreshAnimationView(Context context, AttributeSet attrs) {
        this(context, attrs, 0, 0);
    }

    public RefreshAnimationView(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public RefreshAnimationView(Context context, AttributeSet attrs,
                                int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init();
    }

    private void init() {
        mDrawable = new RefreshAnimationDrawable();
        super.setImageDrawable(mDrawable);
    }

    @Override
    protected void onVisibilityChanged(View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        if (mDrawable.mRunning) {
            if (visibility == GONE || visibility == INVISIBLE) {
                mDrawable.mFromStop.pause();
                mDrawable.mToStop.pause();
            } else {
                if (mDrawable.mIndeterminate) {
                    mDrawable.mFromStop.resume();
                } else {
                    mDrawable.mToStop.resume();
                }
            }
        }
    }

    public void startProgress() {
        mDrawable.start();
    }

    public void stopProgress() {
        mDrawable.stop();
    }

    public void reset() {
        mDrawable.reset();
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        mDrawable.setEnabled(enabled);
    }

    public void setColor(int color) {
        mDrawable.mColorActivated = color;
        mDrawable.mPaint.setColor(color);
        mDrawable.mArrowPaint.setColor(color);
    }

    @Override
    public final void setImageDrawable(Drawable drawable) {
        // Ignore
    }

    @Override
    public final void setImageResource(int resId) {
        // Ignore
    }

    @Override
    public final void setImageURI(Uri uri) {
        // Ignore
    }

    @Override
    public final void setImageBitmap(Bitmap bm) {
        // Ignore
    }

}
