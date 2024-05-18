package com.kejin.view.gesture;

import android.content.Context;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;

public class GestureFrameLayout extends FrameLayout {
    private final ArrayList<View> controlViews = new ArrayList<>();
    private ViewGestureAttacher gestureAttacher;
    private IGestureListener gestureListener;
    private boolean gestureEnable = true;

    public GestureFrameLayout(@NonNull Context context) {
        super(context);
    }

    public GestureFrameLayout(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public GestureFrameLayout(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public GestureFrameLayout(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public void setGestureListener(IGestureListener listener) {
        gestureListener = listener;
        if (gestureAttacher != null) {
            gestureAttacher.setGestureListener(listener);
        }
    }

    public void setGestureEnable(boolean enable) {
        gestureEnable = enable;
    }

    @Nullable
    public ViewGestureAttacher getGestureAttacher() {
        return gestureAttacher;
    }

    public void addControlView(@NonNull View view) {
        if (!controlViews.contains(view)) {
            controlViews.add(view);
        }
    }

    public void removeControlView(@NonNull View view) {
        controlViews.remove(view);
    }

    @Nullable
    public ViewGestureAttacher startControl(int width, int height) {
        for (View view : controlViews) {
            LayoutParams params =
                    new LayoutParams(width, height, Gravity.LEFT|Gravity.TOP);
            if (indexOfChild(view) < 0) {
                addView(view, params);
            } else {
                view.setLayoutParams(params);
            }
        }

        if (gestureAttacher != null &&
                gestureAttacher.isSameImageSize(width, height)) {
            return gestureAttacher;
        }

        if (gestureAttacher != null) {
            gestureAttacher.release();
            gestureAttacher = null;
        }
        if (width < 1 || height < 1) {
            return null;
        }
        ViewGestureAttacher attacher = new ViewGestureAttacher(this, width, height);
        attacher.setScaleType(ImageView.ScaleType.FIT_CENTER);
        attacher.setMatrixListener(matrix -> {
            RectF displayRect = attacher.getDisplayRect();
            updateControlViewRect(width, height, displayRect);
        });
        attacher.setGestureListener(gestureListener);
        attacher.update();
        gestureAttacher = attacher;
        return gestureAttacher;
    }

    private void updateControlViewRect(float vw, float vh, @NonNull RectF rect) {
        if (!gestureEnable) {
            return;
        }

        float scaleW = rect.width() / vw;
        float scaleH = rect.height() / vh;

        float dx = rect.centerX() - vw / 2;
        float dy = rect.centerY() - vh / 2;

        for (View view : controlViews) {
            view.setTranslationX(dx);
            view.setTranslationY(dy);
            view.setScaleX(scaleW);
            view.setScaleY(scaleH);
        }
    }
}
