package com.kejin.android.gesture.view;

import android.graphics.Matrix;

import androidx.annotation.NonNull;

/**
 * Interface definition for a callback to be invoked when the internal Matrix has changed for
 * this View.
 */
public interface IMatrixListener {

    /**
     * Callback for when the Matrix displaying the Drawable has changed. This could be because
     * the View's bounds have changed, or the user has zoomed.
     */
    void onMatrixChanged(@NonNull Matrix matrix);
}
