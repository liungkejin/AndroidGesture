package com.kejin.android.gesture.view;

import android.view.ViewParent;

import androidx.annotation.NonNull;

/**
 * 边界拖动处理
 */
public interface IParentInterceptHandler {
    boolean isHandled();

    void onTouchStart(@NonNull ViewParent parent);

    default boolean skipHandle(@NonNull ViewGestureAttacher attacher) {
        return attacher.isScaling()
                || attacher.getCurPointerCount() > 1
                || attacher.getScale() > attacher.getMinimumScale();
    }

    boolean handleParentIntercept(@NonNull ViewGestureAttacher attacher,
                                  @NonNull ViewParent parent, float dx, float dy);

    void onTouchEnd();
}
