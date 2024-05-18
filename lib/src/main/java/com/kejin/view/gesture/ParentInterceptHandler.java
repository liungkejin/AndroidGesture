package com.kejin.view.gesture;

import android.graphics.RectF;
import android.view.ViewParent;

import androidx.annotation.NonNull;

/**
 * 边界拖动处理
 */
public class ParentInterceptHandler implements IParentInterceptHandler {

    private float mHOverDx = 0;
    private float mHOverDy = 0;
    private float mVOverDx = 0;
    private float mVOverDy = 0;

    private int mInterceptThreshold;
    private boolean mAllowHorizontalIntercept = true;
    private boolean mAllowVerticalIntercept = false;

    private boolean mHandledFlag = false;

    public ParentInterceptHandler() {
        this(200);
    }

    public ParentInterceptHandler(int interceptThreshold) {
        mInterceptThreshold = interceptThreshold;
    }

    public void setAllowParentInterceptOnEdge(boolean horizontalAllow, boolean verticalAllow) {
        mAllowHorizontalIntercept = horizontalAllow;
        mAllowVerticalIntercept = verticalAllow;
    }

    public void setAllowParentInterceptThreshold(int px) {
        mInterceptThreshold = px;
    }

    @Override
    public boolean isHandled() {
        return mHandledFlag;
    }

    @Override
    public void onTouchStart(@NonNull ViewParent parent) {
        mVOverDx = 0;
        mVOverDy = 0;
        mHOverDy = 0;
        mHOverDx = 0;
        mHandledFlag = false;
        parent.requestDisallowInterceptTouchEvent(true);
    }

    @Override
    public boolean handleParentIntercept(@NonNull ViewGestureAttacher attacher,
                                         @NonNull ViewParent parent, float dx, float dy) {
        if (mHandledFlag) {
            return true;
        }

        RectF curRect = attacher.getDisplayRect();
        boolean disallowIntercept = false;
        if (mAllowHorizontalIntercept) {
            int left = (int) curRect.left, right = (int) curRect.right;
            boolean leftEdgeTouched = left >= 0;
            boolean rightEdgeTouched = right <= attacher.getViewWidth();
            if (leftEdgeTouched || rightEdgeTouched) {
                mHOverDx += dx;
                mHOverDy += dy;
                // 计算水平方向的角度
                float d = (float) Math.sqrt(mHOverDx * mHOverDx + mHOverDy * mHOverDy);
                if (d > mInterceptThreshold) {
                    float angle = (float) (Math.asin(mHOverDy / d) * 180 / Math.PI);
//                    UILog.i("x angle: " + angle);
                    float absAngle = Math.abs(angle);
                    if (absAngle < 45) {
                        disallowIntercept = true;
                    }
                }
            } else {
                mHOverDx = 0;
            }
        }

        if (!disallowIntercept && mAllowVerticalIntercept) {
            int top = (int) curRect.top, bottom = (int) curRect.bottom;
            boolean topEdgeTouched = top >= 0;
            boolean bottomEdgeTouched = bottom <= attacher.getViewHeight();
            if (topEdgeTouched || bottomEdgeTouched) {
                mVOverDy += dy;
                mVOverDx += dx;
                // 计算垂直方向的角度
                float d = (float) Math.sqrt(mVOverDx * mVOverDx + mVOverDy * mVOverDy);
                if (d > mInterceptThreshold) {
                    float angle = (float) (Math.asin(mVOverDx / d) * 180 / Math.PI);
//                    UILog.i("y angle: " + angle);
                    float absAngle = Math.abs(angle);
                    if (absAngle < 45) {
                        disallowIntercept = true;
                    }
                }
            } else {
                mVOverDy = 0;
                mVOverDx = 0;
            }
        }

        if (disallowIntercept) {
            mHandledFlag = true;
//            UILog.i("requestDisallowInterceptTouchEvent(false)");
            parent.requestDisallowInterceptTouchEvent(false);
        }

        return mHandledFlag;
    }


    @Override
    public void onTouchEnd() {
        mVOverDx = 0;
        mVOverDy = 0;
        mHOverDy = 0;
        mHOverDx = 0;
        mHandledFlag = false;
    }
}
