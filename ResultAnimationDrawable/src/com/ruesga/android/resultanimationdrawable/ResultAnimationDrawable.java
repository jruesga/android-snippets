package com.ruesga.android.resultanimationdrawable;

import android.animation.ObjectAnimator;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.Drawable;
import android.view.animation.AccelerateInterpolator;

public class ResultAnimationDrawable extends Drawable implements Animatable {

    private static final int DEFAULT_ANIMATION_DURATION = 350;

    private static final float SCALE = 24.0f;
    private static final float STROKE_WIDTH = 2.5f;

    private static final RectF[] SUCCESS_PATH = new RectF[]{
            new RectF(4.27f, 12.81f, 9.86f, 18.1f),
            new RectF(9.86f, 18.1f, 20.14f, 6.15f)
    };
    private static final RectF[] ERROR_PATH = new RectF[]{
            new RectF(5.90f, 5.85f, 18.20f, 18.05f),
            new RectF(18.20f, 5.85f, 5.90f, 18.05f)
    };

    private static class InterpolationPath {
        public final Paint mPaint;
        public final RectF[] mOriginalPath;
        public final RectF[] mPath;

        private float[] mDistances;
        private float mDistance;

        private Path mDrawingPath = new Path();

        private InterpolationPath(Paint paint, RectF[] path) {
            mPaint = paint;
            mOriginalPath = path;
            mPath = new RectF[path.length];
            int count = path.length;
            for (int i = 0; i < count; i++) {
                mPath[i] = new RectF();
            }
            compute(1.0f);
            setInterpolation(0.0f);
        }

        void compute(float scale) {
            int count = mPath.length;
            mDistances = new float[count];
            float distance = 0f;
            for (int i = 0; i < count; i++) {
                RectF r = mPath[i];
                RectF r1 = mOriginalPath[i];
                r.left = r1.left * scale;
                r.top = r1.top * scale;
                r.right = r1.right * scale;
                r.bottom = r1.bottom * scale;

                double x1 = mPath[i].left;
                double x2 = mPath[i].width();
                double y1 = mPath[i].top;
                double y2 = mPath[i].height();
                mDistances[i] = (float) Math.sqrt(Math.pow(x2 - x1, 2d) + Math.pow(y2 - y1, 2d));
                distance += mDistances[i];
            }
            mDistance = distance;

            mPaint.setStrokeWidth((int) (scale * STROKE_WIDTH));
        }

        public void setInterpolation(float delta) {
            RectF lastRect = new RectF();
            mDrawingPath.reset();

            float currentDelta = delta;
            if (currentDelta > 1f) {
                currentDelta = 1f;
            } else if (currentDelta < 0f) {
                currentDelta = 0f;
            }

            float deltaDistance = mDistance * currentDelta;

            int count = mPath.length;
            float currentDistance = 0f;
            for (int i = 0; i < count; i++) {
                RectF r = mPath[i];
                if (deltaDistance < currentDistance) {
                    // not need to redraw the rest
                    break;
                }

                // calculate delta position
                float pathDistance = mDistances[i];
                float nextPathDistance = currentDistance + pathDistance;
                float d = deltaDistance <= nextPathDistance
                        ? nextPathDistance - deltaDistance : 0;
                float w = (d * r.width()) / pathDistance;
                float h = (d * r.height()) / pathDistance;

                // insert path
                if (lastRect.right != r.left || lastRect.bottom != r.top) {
                    mDrawingPath.moveTo(r.left, r.top);
                }
                mDrawingPath.lineTo(r.right - w, r.bottom - h);
                lastRect = r;

                currentDistance += pathDistance;
            }
        }

        void draw(Canvas canvas) {
           canvas.drawPath(mDrawingPath, mPaint);
        }
    }

    public enum STATE {
        SUCCESS,
        ERROR
    }

    private final Paint mSuccessPaint;
    private final Paint mErrorPaint;
    private final InterpolationPath mSuccessInterpolation;
    private final InterpolationPath mErrorInterpolation;
    private InterpolationPath mCurrentInterpolation;

    private STATE mCurrentState;

    private float mInterpolation;

    private final ObjectAnimator mAnimator;

    public ResultAnimationDrawable() {
        mInterpolation = 0.0f;

        mAnimator = ObjectAnimator.ofFloat(this, "interpolation", 0.0f, 1.0f);
        mAnimator.setDuration(DEFAULT_ANIMATION_DURATION);
        mAnimator.setInterpolator(new AccelerateInterpolator());

        mSuccessPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mSuccessPaint.setStrokeCap(Paint.Cap.BUTT);
        mSuccessPaint.setColor(Color.WHITE);
        mSuccessPaint.setStyle(Paint.Style.STROKE);
        mErrorPaint = new Paint(mSuccessPaint);

        mSuccessInterpolation = new InterpolationPath(mSuccessPaint, SUCCESS_PATH);
        mErrorInterpolation = new InterpolationPath(mErrorPaint, ERROR_PATH);
        setState(STATE.SUCCESS);
    }

    @Override
    protected void onBoundsChange(Rect bounds) {
        super.onBoundsChange(bounds);

        float scale = bounds.width() / SCALE;
        mCurrentInterpolation.compute(scale);
        mCurrentInterpolation.setInterpolation(mInterpolation);
    }

    public void setColors(int success, int error) {
        mSuccessPaint.setColor(success);
        mErrorPaint.setColor(error);
        invalidateSelf();
    }

    public void setState(STATE state) {
        if (mCurrentState == state) {
            return;
        }

        // reset the current state
        reset();

        mCurrentInterpolation = state == STATE.SUCCESS
                ? mSuccessInterpolation : mErrorInterpolation;
        mCurrentState = state;

        float scale = getBounds().width() / SCALE;
        mCurrentInterpolation.compute(scale);
        mCurrentInterpolation.setInterpolation(mInterpolation);
        invalidateSelf();
    }

    public float getInterpotation() {
        return mInterpolation;
    }

    public void setInterpolation(float delta) {
        mInterpolation = delta;
        mCurrentInterpolation.setInterpolation(mInterpolation);
        invalidateSelf();
    }

    public void setDuration(int duration) {
        reset();
        mAnimator.setDuration(duration);
    }

    @Override
    public void draw(Canvas canvas) {
        mCurrentInterpolation.draw(canvas);
    }

    @Override
    public void setAlpha(int alpha) {
        mSuccessPaint.setAlpha(alpha);
        mErrorPaint.setAlpha(alpha);
        invalidateSelf();
    }

    @Override
    public void setColorFilter(ColorFilter cf) {
        mSuccessPaint.setColorFilter(cf);
        mErrorPaint.setColorFilter(cf);
        invalidateSelf();
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }

    @Override
    public void start() {
        if (!mAnimator.isRunning()) {
            mAnimator.start();
        }
    }

    @Override
    public void stop() {
        if (mAnimator.isRunning()) {
            mAnimator.cancel();
        }
    }

    @Override
    public boolean isRunning() {
        return mAnimator.isRunning();
    }

    public void reset() {
        stop();
        mInterpolation = 0.0f;
    }
}
