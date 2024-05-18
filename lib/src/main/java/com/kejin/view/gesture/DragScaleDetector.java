package com.kejin.view.gesture;

import android.content.Context;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.VelocityTracker;

import androidx.annotation.NonNull;

/**
 * 识别 Drag 和 Scale 手势
 */
public class DragScaleDetector implements ScaleGestureDetector.OnScaleGestureListener {

    private static final int INVALID_POINTER_ID = -1;

    private int mActivePointerId = INVALID_POINTER_ID;
    private int mActivePointerIndex = 0;
    private final ScaleGestureDetector mDetector;

    private VelocityTracker mVelocityTracker;
    private boolean mIsDragging;
    private float mLastTouchX;
    private float mLastTouchY;
    private float mSumDragX = 0;
    private float mSumDragY = 0;
    private final float mTouchSlop;
    private final float mMinimumVelocity;
    private final Listener mListener;

    DragScaleDetector(@NonNull Context context,
                      int minVelocity, int touchSlop, @NonNull Listener listener) {
//        final ViewConfiguration configuration = ViewConfiguration.get(context);
//        mMinimumVelocity = configuration.getScaledMinimumFlingVelocity();
//        mTouchSlop = configuration.getScaledTouchSlop();
        mMinimumVelocity = minVelocity;
        mTouchSlop = touchSlop;

        mListener = listener;
        mDetector = new ScaleGestureDetector(context, this);
    }

    private float getActiveX(MotionEvent ev) {
        try {
            return ev.getX(mActivePointerIndex);
        } catch (Exception e) {
            return ev.getX();
        }
    }

    private float getActiveY(MotionEvent ev) {
        try {
            return ev.getY(mActivePointerIndex);
        } catch (Exception e) {
            return ev.getY();
        }
    }

    public boolean isScaling() {
        return mDetector.isInProgress();
    }

    public boolean isDragging() {
        return mIsDragging;
    }

    public boolean onTouchEvent(MotionEvent ev) {
        try {
            mDetector.onTouchEvent(ev);
            return processTouchEvent(ev);
        } catch (IllegalArgumentException e) {
            // Fix for support lib bug, happening when onDestroy is called
            return true;
        }
    }

    private boolean processTouchEvent(@NonNull MotionEvent ev) {
        final int action = ev.getAction();
        switch (action & MotionEvent.ACTION_MASK) {
            case MotionEvent.ACTION_DOWN:
                mActivePointerId = ev.getPointerId(0);

                mVelocityTracker = VelocityTracker.obtain();
                if (null != mVelocityTracker) {
                    mVelocityTracker.addMovement(ev);
                }

                mLastTouchX = getActiveX(ev);
                mLastTouchY = getActiveY(ev);
                mSumDragX = 0;
                mSumDragY = 0;
                mIsDragging = false;
                break;
            case MotionEvent.ACTION_MOVE:
                final float x = getActiveX(ev);
                final float y = getActiveY(ev);

                if (!mIsDragging) {
                    // Use Pythagoras to see if drag length is larger than
                    // touch slop
                    final float dx = x - mLastTouchX, dy = y - mLastTouchY;
                    mIsDragging = Math.sqrt((dx * dx) + (dy * dy)) >= mTouchSlop;
                    if (mIsDragging) {
                        mLastTouchX = x;
                        mLastTouchY = y;
                        break;
                    }
                }

                if (mIsDragging && !isScaling()) {
                    if (mLastTouchX == Float.MAX_VALUE || mLastTouchY == Float.MAX_VALUE) {
                        mLastTouchX = x;
                        mLastTouchY = y;
                        break;
                    }
                    final float dx = x - mLastTouchX, dy = y - mLastTouchY;
                    mSumDragX += dx;
                    mSumDragY += dy;
                    mListener.onDrag(dx, dy, mSumDragX, mSumDragY);
                    mLastTouchX = x;
                    mLastTouchY = y;

                    if (null != mVelocityTracker) {
                        mVelocityTracker.addMovement(ev);
                    }
                }
                break;
            case MotionEvent.ACTION_CANCEL:
                mActivePointerId = INVALID_POINTER_ID;
                if (mIsDragging) {
                    mListener.onDragEnd(mSumDragX, mSumDragY);
                }
                // Recycle Velocity Tracker
                if (null != mVelocityTracker) {
                    mVelocityTracker.recycle();
                    mVelocityTracker = null;
                }
                break;
            case MotionEvent.ACTION_UP:
                mActivePointerId = INVALID_POINTER_ID;
                if (mIsDragging) {
                    mListener.onDragEnd(mSumDragX, mSumDragY);
                    if (null != mVelocityTracker) {
                        mLastTouchX = getActiveX(ev);
                        mLastTouchY = getActiveY(ev);

                        // Compute velocity within the last 1000ms
                        mVelocityTracker.addMovement(ev);
                        mVelocityTracker.computeCurrentVelocity(1000);

                        float vX = mVelocityTracker.getXVelocity();
                        float vY = mVelocityTracker.getYVelocity();

                        // If the velocity is greater than minVelocity, call
                        // listener
                        if (Math.max(Math.abs(vX), Math.abs(vY)) >= mMinimumVelocity) {
                            mListener.onFling(mLastTouchX, mLastTouchY, -vX, -vY);
                        }
                    }
                }

                // Recycle Velocity Tracker
                if (null != mVelocityTracker) {
                    mVelocityTracker.recycle();
                    mVelocityTracker = null;
                }
                break;
            case MotionEvent.ACTION_POINTER_UP:
                final int pointerIndex = getPointerIndex(ev.getAction());
                final int pointerId = ev.getPointerId(pointerIndex);
                if (pointerId == mActivePointerId) {
                    // This was our active pointer going up. Choose a new
                    // active pointer and adjust accordingly.
                    final int newPointerIndex = pointerIndex == 0 ? 1 : 0;
                    mActivePointerId = ev.getPointerId(newPointerIndex);
                    mLastTouchX = ev.getX(newPointerIndex);
                    mLastTouchY = ev.getY(newPointerIndex);
                }
                break;
        }

        mActivePointerIndex = ev.findPointerIndex(
                mActivePointerId != INVALID_POINTER_ID ? mActivePointerId : 0);
        return true;
    }

    private float lastFocusX, lastFocusY = 0;

    @Override
    public boolean onScaleBegin(@NonNull ScaleGestureDetector detector) {
        lastFocusX = detector.getFocusX();
        lastFocusY = detector.getFocusY();
        return true;
    }

    @Override
    public boolean onScale(@NonNull ScaleGestureDetector detector) {
        float scaleFactor = detector.getScaleFactor();

        if (Float.isNaN(scaleFactor) || Float.isInfinite(scaleFactor))
            return false;

        if (scaleFactor >= 0) {
            float fx = detector.getFocusX();
            float fy = detector.getFocusY();
            float dx = fx - lastFocusX;
            float dy = fy - lastFocusY;
            if (!mIsDragging) {
                mIsDragging = true;
            }
            mSumDragX += dx;
            mSumDragY += dy;
            mLastTouchX = Float.MAX_VALUE;
            mLastTouchY = Float.MAX_VALUE;
            mListener.onDrag(dx, dy, mSumDragX, mSumDragY);

            mListener.onScale(scaleFactor, fx, fy);
            lastFocusX = fx;
            lastFocusY = fy;
        }
        return true;
    }

    @Override
    public void onScaleEnd(@NonNull ScaleGestureDetector detector) {
        mListener.onScaleEnd(lastFocusX, lastFocusY);
    }

    static int getPointerIndex(int action) {
        return (action & MotionEvent.ACTION_POINTER_INDEX_MASK) >> MotionEvent.ACTION_POINTER_INDEX_SHIFT;
    }

    public interface Listener {
        void onDrag(float dx, float dy, float sumDx, float sumDy);

        default void onDragEnd(float sumDx, float sumDy) {}

        void onFling(float startX, float startY, float velocityX, float velocityY);

        void onScale(float scaleFactor, float focusX, float focusY);

        default void onScaleEnd(float focusX, float focusY) {}
    }
}
