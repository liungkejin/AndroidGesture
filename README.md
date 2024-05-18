# ViewGestureAttacher

ViewGestureAttacher 是从 PhotoView 项目分离出来的一个脱离ImageView的手势控制器，
ViewGestureAttacher 主要的依赖两个参数，一个是本身view的大小，一个是内容的大小， 
输出为一个Matrix，通过 Matrix 可以获取当前内容区域在view中的位置，以及当前内容区域的大小。

通过 ViewGestureAttacher 实现的两个控件 GestureImageView 和 GestureFrameLayout，

## GestureImageView

和 PhotoView 一样

## GestureFrameLayout

```java
        GestureFrameLayout frameLayout = findViewById(R.id.gesture_frame_layout);
        TextView textView = findViewById(R.id.gesture_control_view);

        frameLayout.addControlView(textView);

        textView.post(() -> {
            int width = textView.getWidth();
            int height = textView.getHeight();
            frameLayout.startControl(width, height);
        });
```