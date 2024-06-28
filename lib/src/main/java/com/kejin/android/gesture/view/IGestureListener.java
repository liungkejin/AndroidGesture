package com.kejin.android.gesture.view;

import android.view.ViewParent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public interface IGestureListener {
    default void onTouchStart(@NonNull ViewGestureAttacher attacher) {}

    /**
     * @param dx delta x
     * @param dy delta y
     * @param sumDx 从touch down 开始总的距离
     * @param sumDy 从touch down 开始总的距离, 即所有 dy 的总和
     * @return true表示自己处理drag事件
     */
    default boolean onDrag(@NonNull ViewGestureAttacher attacher,
                           @Nullable ViewParent parent,
                           float dx, float dy, float sumDx, float sumDy) {
        return false;
    }

    /**
     * 拖动结束的回调
     * @param sumDx 从touch down 开始总的距离
     * @param sumDy 从touch down 开始总的距离, 即所有 dy 的总和
     */
    default boolean onDragEnd(@NonNull ViewGestureAttacher attacher, float sumDx, float sumDy) {
        return false;
    }

    /**
     * 向下拖拽超出边界
     * @param dy 垂直距离
     * @param percent 和整个view的比值 0 - 1
     */
    default void onDragOverDown(@NonNull ViewGestureAttacher attacher, float dy, float percent) {}

    /**
     * 向下拖拽超出边界结束
     * @return 是否做处理，如果true表示已做了处理，不会去修正边界
     */
    default boolean onDragOverDownEnd(@NonNull ViewGestureAttacher attacher, float dy, float percent) {
        return false;
    }

    /**
     * 缩放的回调
     * @param factor 缩放因子
     * @param fx 缩放的中心点x
     * @param fy 缩放的中心点y
     */
    default boolean onScale(@NonNull ViewGestureAttacher attacher,
                            float factor, float fx, float fy) {
        return false;
    }

    /**
     * 点击事件回调
     * @param x 点击的坐标 基于view的坐标
     * @param y 点击的坐标 基于view的坐标
     * @param insideImage 是否在图片区域内
     */
    default void onClick(@NonNull ViewGestureAttacher attacher,
                         float x, float y, boolean insideImage) {}

    /**
     * 双击事件回调
     * @param x 点击的坐标 基于view的坐标
     * @param y 点击的坐标 基于view的坐标
     * @return true 表示自己处理双击事件，false 表示不处理，默认双击为放大到center crop
     */
    default boolean onDoubleClick(@NonNull ViewGestureAttacher attacher, float x, float y) {
        return false;
    }

    /**
     * 在每次 TOUCH_UP / TOUCH_CANCEL 时回调
     */
    default boolean onDetectEnd(@NonNull ViewGestureAttacher attacher) {
        return false;
    }
}
