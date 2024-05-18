package com.kejin.view.gesture;

import androidx.annotation.NonNull;

public class OverDragDownHandler {
    private boolean mOverDragFlag = false;

    private float mStartSumDy = 0;

    private final int mThreshold;

    private float mOverDragPercent = 0;
    private float mOverDragDistance = 0;

    public OverDragDownHandler(int threshold) {
        mThreshold = threshold;
    }

    public boolean isHandling() {
        return mOverDragFlag;
    }

    public boolean handDrag(@NonNull ViewGestureAttacher attacher,
                            float dx, float dy, float sumDx, float sumDy) {
        int viewHeight = attacher.getViewHeight();
        if (mOverDragFlag) {
            if (viewHeight < 1) {
                return true;
            }
            mOverDragDistance = sumDy - mStartSumDy;
            float ddy = Math.abs(mOverDragDistance);
            float p = Math.min(ddy * 2 / viewHeight, 1);
            mOverDragPercent = p;

            float percent = (float) (1-Math.exp(-ddy/2000));
            if (percent > 0.9f) {
                percent = 0.9f;
            }
            float dstScale = (1 - percent) * attacher.getMinimumScale();
            float scaleFactor = dstScale/attacher.getScale();
            attacher.postTranslateScale((1-p)*dx, (1-p)*dy, scaleFactor, false);
            return true;
        }

        float curScale = attacher.getScale();
        if (curScale <= attacher.getMinimumScale()*1.001f && attacher.getCurPointerCount() == 1) {
            float d = (float) Math.sqrt(sumDx*sumDx+sumDy*sumDy);
            if (d > mThreshold) {
                float angle = (float) (Math.asin(sumDx / d) * 180 / Math.PI);
//                UILog.i("over drag angle: " + angle);
                if (Math.abs(angle) < 45 && sumDy > 0) {
                    // 向下滑
                    mOverDragFlag = true;
                    mStartSumDy = sumDy;
                }
            }
        }
        return mOverDragFlag;
    }

    public float getOverDragDistance() {
        return mOverDragDistance;
    }

    public float getOverDragPercent() {
        return mOverDragPercent;
    }

    public void exitHandle() {
        mOverDragFlag = false;
        mOverDragPercent = 0;
        mOverDragDistance = 0;
    }
}
