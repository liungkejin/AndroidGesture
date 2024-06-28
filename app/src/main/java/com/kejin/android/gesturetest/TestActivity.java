package com.kejin.android.gesturetest;

import android.os.Bundle;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.kejin.android.gesture.view.GestureFrameLayout;
import com.kejin.android.gesture.view.GestureImageView;


public class TestActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.layout_activity_test);

        GestureImageView gestureImageView = findViewById(R.id.gesture_image_view);

        GestureFrameLayout frameLayout = findViewById(R.id.gesture_frame_layout);
        TextView textView = findViewById(R.id.gesture_control_view);

        frameLayout.addControlView(textView);

        textView.post(() -> {
            int width = textView.getWidth();
            int height = textView.getHeight();
            frameLayout.startControl(width, height);
        });
    }
}
