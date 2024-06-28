package com.kejin.android.gesture.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageView;

/**
 * 替换 PhotoView
 */
public class GestureImageView extends AppCompatImageView {

    private boolean gestureEnable = true;
    private ViewGestureAttacher gestureAttacher = null;
    private ScaleType gestureScaleType = ScaleType.FIT_CENTER;
    private IGestureListener gestureListener = null;

    public GestureImageView(Context context) {
        this(context, null);
    }

    public GestureImageView(Context context, AttributeSet attr) {
        this(context, attr, 0);
    }

    public GestureImageView(Context context, AttributeSet attr, int defStyle) {
        super(context, attr, defStyle);
    }

    @Override
    public void setScaleType(ScaleType scaleType) {
        if (gestureEnable) {
            gestureScaleType = scaleType;
            if (gestureAttacher != null) {
                gestureAttacher.setScaleType(scaleType);
            }
        } else {
            super.setScaleType(scaleType);
        }
    }

    public void setGestureEnable(boolean enable) {
        gestureEnable = enable;
        if (!gestureEnable) {
            if (gestureAttacher != null) {
                gestureAttacher.release();
                gestureAttacher = null;
            }
            super.setScaleType(gestureScaleType);
        }
    }

    public void setGestureListener(@NonNull IGestureListener listener) {
        gestureListener = listener;
        if (gestureAttacher != null) {
            gestureAttacher.setGestureListener(listener);
        }
    }

    @Nullable
    public ViewGestureAttacher getGestureAttacher() {
        return gestureAttacher;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        try {
            super.onDraw(canvas);
        } catch (Throwable e) {
            // ignore
        }

        if (!gestureEnable) {
            return;
        }

        Drawable drawable = getDrawable();
        if (drawable == null) {
            return;
        }
        int width = drawable.getIntrinsicWidth();
        int height = drawable.getIntrinsicHeight();
        if (width < 1 || height < 1) {
            return;
        }

        if (gestureAttacher == null ||
                !gestureAttacher.isSameImageSize(width, height)) {
            if (gestureAttacher != null) {
                gestureAttacher.release();
            }
            super.setScaleType(ScaleType.MATRIX);
            gestureAttacher = new ViewGestureAttacher(this, width, height);
            gestureAttacher.setMatrixListener(this::setImageMatrix);
            gestureAttacher.setGestureListener(gestureListener);
            gestureAttacher.setScaleType(gestureScaleType);
            gestureAttacher.update();
        }
    }
}
