package com.kejin.android.gesture.view;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Matrix;
import android.graphics.Matrix.ScaleToFit;
import android.graphics.PointF;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewParent;
import android.widget.ImageView.ScaleType;

import androidx.annotation.NonNull;
import androidx.interpolator.view.animation.FastOutSlowInInterpolator;

import com.kejin.android.gesture.GestureDetector;
import com.kejin.android.gesture.GestureListener;

public class ViewGestureAttacher implements
        View.OnTouchListener, View.OnLayoutChangeListener, GestureListener {

    private final static float DEFAULT_MIN_SCALE = 1.0f;
    private final static int DEFAULT_ANIM_DURATION = 200;

    private final View mImageView;
    private final int mImageWidth, mImageHeight;

    // Gesture Detectors
    private final GestureDetector mGestureDetector;

    // These are set so we don't keep allocating them on the heap
    private final Matrix mBaseMatrix = new Matrix();
    private final Matrix mDrawMatrix = new Matrix();
    private final Matrix mSuppMatrix = new Matrix();
    private final RectF mDisplayRect = new RectF();
    private final float[] mMatrixValues = new float[9];

    private float mMinScale = DEFAULT_MIN_SCALE;
    private float mDoubleScale = 2.0f;
    private float mMaxScale;

    private ScaleType mScaleType = ScaleType.FIT_CENTER;

    private int mCurPointerCount = 0;

    private IGestureListener mGestureListener = null;
    private IMatrixListener mMatrixListener = null;
    private IParentInterceptHandler mInterceptHandler;
    private OverDragDownHandler mOverDragHandler;

    private ValueAnimator mTransAnimator = null;
    private ValueAnimator mScaleAnimator = null;

    public ViewGestureAttacher(@NonNull View imageView, int imageWdth, int imageHeight) {
        this.mImageView = imageView;
        this.mImageWidth = imageWdth;
        this.mImageHeight = imageHeight;
        int threshold = dp2px(10);
        this.mInterceptHandler = new ParentInterceptHandler(threshold);
        this.mOverDragHandler = new OverDragDownHandler(threshold);

        imageView.setOnTouchListener(this);
        imageView.addOnLayoutChangeListener(this);

        int vw = getViewWidth();
        int vh = getViewHeight();
        float minSize = Math.min(vw, vh);
        if (minSize < 1) {
            minSize = 1080;
        }
        this.mMaxScale = Math.max(Math.max(imageWdth, imageHeight) / minSize, 5.0f);

        Context context = imageView.getContext();
        this.mGestureDetector = new GestureDetector(context, this);
        this.mGestureDetector.setDoubleClickEnable(true);
    }

    public void release() {
        mImageView.setOnClickListener(null);
        mImageView.removeOnLayoutChangeListener(this);
    }

    public void setDoubleClickEnable(boolean enable) {
        mGestureDetector.setDoubleClickEnable(enable);
    }

    public void setGestureListener(IGestureListener listener) {
        mGestureListener = listener;
    }

    public void setMatrixListener(IMatrixListener listener) {
        mMatrixListener = listener;
    }

    public void setParentInterceptHandler(IParentInterceptHandler handler) {
        mInterceptHandler = handler;
    }

    public void setOverDragHandler(OverDragDownHandler handler) {
        mOverDragHandler = handler;
    }

    public boolean isScaling() {
        return mGestureDetector.isScaling();
    }

    @Override
    public void onLayoutChange(View v,
                               int left, int top, int right, int bottom,
                               int oldLeft, int oldTop, int oldRight, int oldBottom) {
        // Update our base matrix, as the bounds have changed
        if (left != oldLeft || top != oldTop || right != oldRight || bottom != oldBottom) {
            updateBaseMatrix();
        }
    }

    @Override
    public boolean onTouch(@NonNull View v, @NonNull MotionEvent ev) {
        mCurPointerCount = ev.getPointerCount();
        mGestureDetector.onTouchEvent(v, ev);
        return true;
    }

    @Override
    public void onTouchBeg(@NonNull MotionEvent e) {
        ViewParent parent = mImageView.getParent();
        if (parent != null) {
            if (mInterceptHandler != null) {
                mInterceptHandler.onTouchStart(parent);
            } else {
                parent.requestDisallowInterceptTouchEvent(true);
            }
        }
        if (mGestureListener != null) {
            mGestureListener.onTouchStart(this);
        }
    }

    @Override
    public void onClick(float x, float y) {
        if (mGestureListener != null) {
            RectF rect = getDisplayRect();
            boolean insideImage = rect.contains(x, y);
            mGestureListener.onClick(this, x, y, insideImage);
        }
    }

    @Override
    public void onDoubleClick(float x, float y) {
        if (mGestureListener != null &&
                mGestureListener.onDoubleClick(this, x, y)) {
            return;
        }
        float scale = getScale();
        if (scale > mMinScale) {
            scaleTo(mMinScale, x, y, true, true);
        } else {
            scaleTo(mDoubleScale, x, y, true, true);
        }
    }

    @Override
    public void onDrag(float x, float y, float dx, float dy,
                       float sumDx, float sumDy, boolean singlePointer) {
        if (hasRunningAnimation()) {
            return; // 有动画执行时禁止拖动
        }

        ViewParent parent = mImageView.getParent();
        if (mGestureListener != null &&
                mGestureListener.onDrag(this, parent, dx, dy, sumDx, sumDy)) {
            return;
        }

        if (parent != null && mOverDragHandler != null && !mOverDragHandler.isHandling()) {
            if (mInterceptHandler != null && !mInterceptHandler.skipHandle(this) &&
                    mInterceptHandler.handleParentIntercept(this, parent, dx, dy)) {
                return;
            }
        }

        if (mOverDragHandler != null &&
                mOverDragHandler.handDrag(this, dx, dy, sumDx, sumDy)) {
            if (mGestureListener != null) {
                mGestureListener.onDragOverDown(this,
                        mOverDragHandler.getOverDragDistance(),
                        mOverDragHandler.getOverDragPercent());
            }
            return;
        }

        mSuppMatrix.postTranslate(dx, dy);

        if (!fixBoundary()) {
            notifyMatrixChanged();
        }
    }

    @Override
    public boolean onDragEnd(float sumDx, float sumDy,
                             int velocityX, int velocityY, boolean singlePointer) {
        if (mOverDragHandler != null && mOverDragHandler.isHandling()) {
            return true;
        }
        if (mGestureListener != null && mGestureListener.onDragEnd(this, sumDx, sumDy)) {
            return true;
        }

        return hasRunningAnimation();
    }

    @Override
    public boolean onFling(float dx, float dy, boolean singlePointer) {
        mSuppMatrix.postTranslate(dx, dy);

        if (!fixBoundary()) {
            notifyMatrixChanged();
        }
        return false;
    }

    @Override
    public void onScale(float focusX, float focusY, float scaleFactor, boolean singlePointer) {
        if (mOverDragHandler != null && mOverDragHandler.isHandling()) {
            return;
        }

        if (hasRunningAnimation()) {
            return; // 有动画执行时禁止scale
        }

        if (mGestureListener != null &&
                mGestureListener.onScale(this, scaleFactor, focusX, focusY)) {
            return;
        }
        float curScale = getScale();
        float deltaScale = 0;
        if (curScale > mMaxScale && scaleFactor > 1) {
            deltaScale = (curScale - mMaxScale) / mMaxScale;
        } else if (curScale < mMinScale && scaleFactor < 1) {
            deltaScale = mMinScale - curScale / mMinScale;
        }
        if (deltaScale > 0) {
            // 实现弹簧效果
            float factor = (float) Math.min(Math.exp(-deltaScale * 2), 1);
            scaleFactor = 1 - (1 - scaleFactor) * factor;
        }

        mSuppMatrix.postScale(scaleFactor, scaleFactor, focusX, focusY);
        if (!fixBoundary()) {
            notifyMatrixChanged();
        }
    }

    @Override
    public void onScaleEnd(float cx, float cy, boolean singlePointer) {
        if (mOverDragHandler != null && mOverDragHandler.isHandling()) {
            return;
        }
        fixScaleAnimated(cx, cy, true);
    }

    @Override
    public void onTouchEnd(@NonNull MotionEvent e) {
        if (mOverDragHandler != null && mOverDragHandler.isHandling()) {
            boolean handle = false;
            if (mGestureListener != null) {
                handle = mGestureListener.onDragOverDownEnd(this,
                        mOverDragHandler.getOverDragDistance(),
                        mOverDragHandler.getOverDragPercent());
            }
            mOverDragHandler.exitHandle();
            if (handle) {
                return;
            }
        }

        if (mInterceptHandler != null) {
            mInterceptHandler.onTouchEnd();
        }

        if (mGestureListener != null &&
                mGestureListener.onDetectEnd(this)) {
            return;
        }

        fixBoundaryAnimated();
        if (mScaleAnimator == null || !mScaleAnimator.isRunning()) {
            fixScaleAnimated(false);
        }
    }

    ////////////////////////////////

    public void update() {
        // Update the base matrix using the current drawable
        updateBaseMatrix();
    }

    /**
     * 设置目标显示区域
     *
     * @param rect    目标区域，如果和当前的比例不一致，以宽表标准
     * @param fixBound 是否修正边界
     * @param animate 是否动画
     */
    public void setDisplayRectTo(@NonNull RectF rect, boolean fixBound, boolean animate) {
        float cx = rect.centerX();
        float cy = rect.centerY();

        RectF curRect = getDisplayRect();
        if (curRect.width() < 1) {
            return;
        }
        float scaleFactor = rect.width() / curRect.width();
        float dstScale = getScale() * scaleFactor;
        setDisplayRectTo(cx, cy, dstScale, fixBound, animate);
    }

    /**
     * 设置目标显示区域
     *
     * @param dstCx    目标中心点x
     * @param dstCy    目标中心点y
     * @param dstScale 目标缩放比例
     * @param fixBound 是否修正边界
     * @param animate  是否动画
     */
    public void setDisplayRectTo(float dstCx, float dstCy, float dstScale,
                                 boolean fixBound, boolean animate) {
        translateTo(dstCx, dstCy, fixBound, animate);
        scaleTo(dstScale, fixBound, animate);
    }

    private boolean hasRunningAnimation() {
        if (mTransAnimator != null && mTransAnimator.isRunning()) {
            return true;
        }
        return mScaleAnimator != null && mScaleAnimator.isRunning();
    }

    /**
     * 移动displayRect的中心点到指定位置
     * @param dstX 目标x
     * @param dstY 目标y
     * @param fixBound 是否修正边界
     * @param animate 是否动画
     */
    public void translateTo(float dstX, float dstY,
                            boolean fixBound, boolean animate) {
        RectF rect = getDisplayRect();
        float curX = rect.centerX();
        float curY = rect.centerY();
        float dx = dstX - curX;
        float dy = dstY - curY;
        if (dx == 0 && dy == 0) {
            return;
        }
        postTranslate(dx, dy, fixBound, animate);
    }

    /**
     * 移动displayRect的中心点到指定位置
     * @param dx delta x x方向的中心点移动距离
     * @param dy delta y y方向的中心点移动距离
     * @param fixBound 是否修正边界
     * @param animate 是否动画
     */
    public void postTranslate(float dx, float dy,
                              boolean fixBound, boolean animate) {
        if (!animate) {
            postTranslate(dx, dy, fixBound);
            return;
        }

        if (mTransAnimator != null) {
            mTransAnimator.cancel();
            mTransAnimator = null;
        }
        mTransAnimator = ValueAnimator.ofFloat(0, 1);
        mTransAnimator.setInterpolator(new FastOutSlowInInterpolator());
        mTransAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            float lastp = 0;
            @Override
            public void onAnimationUpdate(@NonNull ValueAnimator animation) {
                float p = (float) animation.getAnimatedValue();
                float ddx = dx * (p - lastp);
                float ddy = dy * (p - lastp);
                lastp = p;
                postTranslate(ddx, ddy, fixBound);
            }
        });
        mTransAnimator.setDuration(DEFAULT_ANIM_DURATION);
        mTransAnimator.start();
    }

    void postTranslate(float dx, float dy, boolean fixBound) {
        mSuppMatrix.postTranslate(dx, dy);
        if (fixBound) {
            if (!fixBoundary()) {
                notifyMatrixChanged();
            }
        } else {
            notifyMatrixChanged();
        }
    }

    void postTranslateScale(float dx, float dy, float scaleFactor, boolean fixBound) {
        mSuppMatrix.postTranslate(dx, dy);
        postScale(scaleFactor, fixBound);
    }

    /**
     * 以中心点缩放
     *
     * @param dstScale 目标缩放比例
     * @param fixBound 是否修正边界
     * @param animate  是否动画
     */
    public void scaleTo(float dstScale, boolean fixBound, boolean animate) {
        scaleTo(dstScale, Float.MAX_VALUE, Float.MAX_VALUE, fixBound, animate);
    }

    /**
     * 以指定中心点缩放
     *
     * @param dstScale 目标缩放比例
     * @param focalX   中心点x
     * @param focalY   中心点y
     * @param fixBound 是否修正边界
     * @param animate  是否动画
     */
    public void scaleTo(float dstScale,
                        float focalX, float focalY,
                        boolean fixBound, boolean animate) {
        float curScale = getScale();
//        UILog.i("scale from: " + curScale + " to " + dstScale + ", animate: " + animate);
        if (!animate) {
            float scaleFactor = dstScale / curScale;
            postScale(scaleFactor, focalX, focalY, fixBound);
            return;
        }

        if (mScaleAnimator != null) {
            mScaleAnimator.cancel();
            mScaleAnimator = null;
        }
        mScaleAnimator = ValueAnimator.ofFloat(curScale, dstScale);
        mScaleAnimator.setInterpolator(new FastOutSlowInInterpolator());
        mScaleAnimator.addUpdateListener(animation -> {
            float scale = (float) animation.getAnimatedValue();
            float scaleFactor = scale / getScale();
            postScale(scaleFactor, focalX, focalY, fixBound);
        });
        mScaleAnimator.setDuration(DEFAULT_ANIM_DURATION);
        mScaleAnimator.start();
    }

    void postScale(float scaleFactor, boolean fixBound) {
        postScale(scaleFactor, Float.MAX_VALUE, Float.MAX_VALUE, fixBound);
    }

    void postScale(float scaleFactor, float focalX, float focalY, boolean fixBound) {
        boolean centerScale = focalX == Float.MAX_VALUE || focalY == Float.MAX_VALUE;
        if (centerScale) {
            RectF rect = getDisplayRect();
            mSuppMatrix.postScale(scaleFactor, scaleFactor, rect.centerX(), rect.centerY());
        } else {
            mSuppMatrix.postScale(scaleFactor, scaleFactor, focalX, focalY);
        }
        if (!fixBound || !fixBoundary()) {
            notifyMatrixChanged();
        }
    }

    public boolean fixBoundaryAnimated() {
        PointF deltaXY = getBoundaryDeltaXY();
        if (deltaXY.x == 0 && deltaXY.y == 0) {
            return false;
        }
        postTranslate(deltaXY.x, deltaXY.y, false, true);
        return true;
    }

    public boolean fixBoundary() {
        PointF deltaXY = getBoundaryDeltaXY();
        if (deltaXY.x == 0 && deltaXY.y == 0) {
            return false;
        }
        mSuppMatrix.postTranslate(deltaXY.x, deltaXY.y);
        notifyMatrixChanged();
        return true;
    }

    public boolean fixScaleAnimated(boolean fixBound) {
        return fixScale(Float.MAX_VALUE, Float.MAX_VALUE, fixBound, true);
    }

    public boolean fixScaleAnimated(float fx, float fy, boolean fixBound) {
        return fixScale(fx, fy, fixBound, true);
    }

    public boolean fixScale(boolean fixBound, boolean animate) {
        return fixScale(Float.MAX_VALUE, Float.MAX_VALUE, fixBound, animate);
    }

    public boolean fixScale(float fx, float fy, boolean fixBound, boolean animate) {
        float curScale = getScale();
        if (curScale >= mMinScale && curScale <= mMaxScale) {
            return false;
        }

        float dstScale;
        if (curScale > mMaxScale) {
            dstScale = mMaxScale;
        } else {
            dstScale = Math.max(curScale, mMinScale);
        }
        scaleTo(dstScale, fx, fy, fixBound, animate);
        return true;
    }

    @NonNull
    public RectF getDisplayRect() {
        return getDisplayRect(getDrawMatrix());
    }

    /**
     * Helper method that maps the supplied Matrix to the current Drawable
     *
     * @param matrix - Matrix to map Drawable against
     * @return RectF - Displayed Rectangle
     */
    @NonNull
    private RectF getDisplayRect(@NonNull Matrix matrix) {
        mDisplayRect.set(0, 0, mImageWidth, mImageHeight);
        matrix.mapRect(mDisplayRect);
        return mDisplayRect;
    }

    public float getMinimumScale() {
        return mMinScale;
    }

    public float getMaximumScale() {
        return mMaxScale;
    }

    public float getScale() {
        return (float) Math.sqrt(
                (float) Math.pow(getValue(mSuppMatrix, Matrix.MSCALE_X), 2) +
                        (float) Math.pow(getValue(mSuppMatrix, Matrix.MSKEW_Y), 2));
    }

    public int getCurPointerCount() {
        return mCurPointerCount;
    }

    public ScaleType getScaleType() {
        return mScaleType;
    }

    public void setMinimumScale(float minimumScale) {
        mMinScale = Math.min(Math.max(1, minimumScale), mMaxScale);
    }

    public void setMaximumScale(float maximumScale) {
        mMaxScale = Math.max(mMinScale, maximumScale);
    }

    public void setScaleType(ScaleType scaleType) {
        if (scaleType == null) {
            return;
        }
        if (scaleType == ScaleType.MATRIX) {
            throw new IllegalArgumentException("Matrix scale type is not supported");
        }
        if (scaleType != mScaleType) {
            mScaleType = scaleType;
            update();
        }
    }

    /**
     * Get the display matrix
     *
     * @param matrix target matrix to copy to
     */
    public void getDisplayMatrix(@NonNull Matrix matrix) {
        matrix.set(getDrawMatrix());
    }

    /**
     * Get the current support matrix
     */
    public void getSuppMatrix(@NonNull Matrix matrix) {
        matrix.set(mSuppMatrix);
    }

    @NonNull
    private Matrix getDrawMatrix() {
        mDrawMatrix.set(mBaseMatrix);
        mDrawMatrix.postConcat(mSuppMatrix);
        return mDrawMatrix;
    }

    public Matrix getImageMatrix() {
        return mDrawMatrix;
    }

    /**
     * Helper method that 'unpacks' a Matrix and returns the required value
     *
     * @param matrix     Matrix to unpack
     * @param whichValue Which value from Matrix.M* to return
     * @return returned value
     */
    private float getValue(@NonNull Matrix matrix, int whichValue) {
        matrix.getValues(mMatrixValues);
        return mMatrixValues[whichValue];
    }

    /**
     * Resets the Matrix back to FIT_CENTER, and then displays its contents
     */
    private void resetMatrix() {
        mSuppMatrix.reset();
        notifyMatrixChanged();
    }

    /**
     * Helper method that simply checks the Matrix, and then displays the result
     */
    private void notifyMatrixChanged() {
        if (mMatrixListener != null) {
            mMatrixListener.onMatrixChanged(getDrawMatrix());
        }
    }

    /**
     * Calculate Matrix for FIT_CENTER
     */
    private void updateBaseMatrix() {
        float drawableWidth = mImageWidth;
        float drawableHeight = mImageHeight;
        final float viewWidth = getViewWidth();
        final float viewHeight = getViewHeight();
        mBaseMatrix.reset();
        if (viewWidth < 1 || viewHeight < 1) {
            return;
        }
        final float widthScale = viewWidth / drawableWidth;
        final float heightScale = viewHeight / drawableHeight;

        if (widthScale > heightScale) {
            mDoubleScale = widthScale / heightScale;
        } else {
            mDoubleScale = heightScale / widthScale;
        }

        mDoubleScale = Math.min(mDoubleScale, mMaxScale);
        if (mDoubleScale / mMinScale < 1.2f) {
            mDoubleScale = 2.0f;
        }

        if (mScaleType == ScaleType.CENTER) {
            mBaseMatrix.postTranslate((viewWidth - drawableWidth) / 2F,
                    (viewHeight - drawableHeight) / 2F);

        } else if (mScaleType == ScaleType.CENTER_CROP) {
            float scale = Math.max(widthScale, heightScale);
            mBaseMatrix.postScale(scale, scale);
            mBaseMatrix.postTranslate((viewWidth - drawableWidth * scale) / 2F,
                    (viewHeight - drawableHeight * scale) / 2F);

        } else if (mScaleType == ScaleType.CENTER_INSIDE) {
            float scale = Math.min(1.0f, Math.min(widthScale, heightScale));
            mBaseMatrix.postScale(scale, scale);
            mBaseMatrix.postTranslate((viewWidth - drawableWidth * scale) / 2F,
                    (viewHeight - drawableHeight * scale) / 2F);
        } else {
            RectF mTempSrc = new RectF(0, 0, drawableWidth, drawableHeight);
            RectF mTempDst = new RectF(0, 0, viewWidth, viewHeight);
            switch (mScaleType) {
                case FIT_CENTER:
                    mBaseMatrix.setRectToRect(mTempSrc, mTempDst, ScaleToFit.CENTER);
                    break;
                case FIT_START:
                    mBaseMatrix.setRectToRect(mTempSrc, mTempDst, ScaleToFit.START);
                    break;
                case FIT_END:
                    mBaseMatrix.setRectToRect(mTempSrc, mTempDst, ScaleToFit.END);
                    break;
                case FIT_XY:
                    mBaseMatrix.setRectToRect(mTempSrc, mTempDst, ScaleToFit.FILL);
                    break;
                default:
                    break;
            }
        }
        resetMatrix();
    }

    @NonNull
    private PointF getBoundaryDeltaXY() {
        final RectF rect = getDisplayRect(getDrawMatrix());

        float deltaX = 0, deltaY = 0;
        final int viewHeight = getViewHeight();
        final float height = rect.height(), width = rect.width();
        if (height <= viewHeight) {
            switch (mScaleType) {
                case FIT_START:
                    deltaY = -rect.top;
                    break;
                case FIT_END:
                    deltaY = viewHeight - height - rect.top;
                    break;
                default:
                    deltaY = (viewHeight - height) / 2 - rect.top;
                    break;
            }
        } else if (rect.top > 0) {
            deltaY = -rect.top;
        } else if (rect.bottom < viewHeight) {
            deltaY = viewHeight - rect.bottom;
        }
        final int viewWidth = getViewWidth();
        if (width <= viewWidth) {
            switch (mScaleType) {
                case FIT_START:
                    deltaX = -rect.left;
                    break;
                case FIT_END:
                    deltaX = viewWidth - width - rect.left;
                    break;
                default:
                    deltaX = (viewWidth - width) / 2 - rect.left;
                    break;
            }
        } else if (rect.left > 0) {
            deltaX = -rect.left;
        } else if (rect.right < viewWidth) {
            deltaX = viewWidth - rect.right;
        }
        return new PointF(deltaX, deltaY);
    }

    public int getImageWidth() {
        return mImageWidth;
    }

    public int getImageHeight() {
        return mImageHeight;
    }

    public boolean isSameImageSize(int width, int height) {
        return mImageWidth == width && mImageHeight == height;
    }

    public int getViewWidth() {
        return mImageView.getWidth() - mImageView.getPaddingLeft() - mImageView.getPaddingRight();
    }

    public int getViewHeight() {
        return mImageView.getHeight() - mImageView.getPaddingTop() - mImageView.getPaddingBottom();
    }

    public int dp2px(float dp) {
        float density = mImageView.getResources().getConfiguration().densityDpi / 160.0f;
        return (int) (dp * (density < 0.1f ? 3 : density) + 0.5f);
    }
}
