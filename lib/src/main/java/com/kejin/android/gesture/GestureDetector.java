package com.kejin.android.gesture;

import android.content.Context;
import android.graphics.PointF;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.Scroller;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * 支持双指缩放，双指旋转，单/双指移动，Fling
 * 支持点击，双击，长按
 * 支持slide检测
 * 支持单指缩放，旋转
 */
public class GestureDetector {
    private final Context context;
    private final Handler handler;

    private final GestureListener listener;

    private final PointF lastPoint = new PointF();
    private final PointF downPoint = new PointF();
    private long downTouchPts = -1; // 按下的时间
    private boolean allPointValidClick = false;
    private float clickRangeThreshold;
    private float pendingClickX = 0, pendingClickY = 0;
    private Runnable pendingClick = null;
    private boolean doubleClickEnable = false;

    private final PointF dragFirstPointer = new PointF();
    private final PointF dragSecondPointer = new PointF();
    private float sumDragX = 0, sumDragY = 0;
    private float dragThreshold;
    private boolean startDragFlag = false;
    private VelocityTracker velocityTracker = null;
    private FlingRunnable flingRunnable = null;

    private float scaleThreshold;
    private boolean startScaleFlag = false;
    private final PointF scaleFirstPointer = new PointF();
    private final PointF scaleSecondPointer = new PointF();

    private float rotateThreshold;
    private boolean startRotateFlag = false;
    private final PointF rotateFirstPointer = new PointF();
    private final PointF rotateSecondPointer = new PointF();

    /**
     * slide动作检测，和 drag 还有单指 单指旋转缩放时冲突的
     */
    private boolean slideDetectEnable = false;
    private int slideThreshold;

    private TouchPointer curTouchPointer = null;
    private boolean isTouchMoving = false;

    private View gestureView = null;

    public GestureDetector(@NonNull Context context, @NonNull GestureListener listener) {
        this.context = context;
        this.handler = new Handler(Looper.getMainLooper());
        this.listener = listener;
        ViewConfiguration vc = ViewConfiguration.get(context);
        this.dragThreshold = vc.getScaledTouchSlop();

        this.clickRangeThreshold = dp2px(20);
        this.slideThreshold = dp2px(50);
        this.scaleThreshold = 0.1f;
        this.rotateThreshold = 3;
    }

    public void setDoubleClickEnable(boolean enable) {
        doubleClickEnable = enable;
    }

    /**
     * 如果手指触摸区域超出一定范围就不认为是一次正常的点击
     */
    public void setClickRangeThreshold(int threshold) {
        clickRangeThreshold = threshold;
    }

    public void setDragThreshold(int threshold) {
        dragThreshold = threshold;
    }

    public void setScaleThreshold(float threshold) {
        scaleThreshold = threshold;
    }

    public void setRotateThreshold(float threshold) {
        rotateThreshold = threshold;
    }

    /**
     * 单手拖动和检测slide 是冲突的
     */
    public void setSlideDetectEnable(boolean enable) {
        slideDetectEnable = enable;
    }

    public void setSlideThreshold(int threshold) {
        slideThreshold = threshold;
    }

    /**
     * 单指缩放，旋转，需要给定一个旋转的中心点
     */
    private boolean singlePointerScaleRotateEnable = false;
    private float singlePointerScaleRotateCenterX = 0;
    private float singlePointerScaleRotateCenterY = 0;

    public void disableSinglePointerScaleRotate() {
        singlePointerScaleRotateEnable = false;
    }

    /**
     * 开启单指操作模式，设置一个锚点
     * @param cx 锚点x
     * @param cy 锚点y
     */
    public void enableSinglePointerScaleRotate(float cx, float cy) {
        singlePointerScaleRotateEnable = true;
        this.singlePointerScaleRotateCenterX = cx;
        this.singlePointerScaleRotateCenterY = cy;
    }

    public boolean isDragging() {
        return singlePointerDragStartFlag || startDragFlag;
    }

    public boolean isScaling() {
        return startScaleFlag;
    }

    public boolean isRotating() {
        return startRotateFlag;
    }

    public void onTouchEvent(@Nullable View view, @NonNull MotionEvent event) {
        gestureView = view;

        if (velocityTracker == null) {
            velocityTracker = VelocityTracker.obtain();
        }
        velocityTracker.addMovement(event);

        listener.onTouchEventBefore(event);
//        ILOG.utilsInfo("GestureDetector event: " + event);
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            listener.onTouchBeg(event);
        }
        switch (action) {
            case MotionEvent.ACTION_POINTER_DOWN:
                if (flingRunnable != null) {
                    flingRunnable.cancelFling();
                    flingRunnable = null;
                }
                if (event.getPointerCount() > 1 && curTouchPointer != TouchPointer.MULTI_POINTER) {
                    TouchPointer lastTouchMode = curTouchPointer;
                    // 切换为多指操作
                    curTouchPointer = TouchPointer.MULTI_POINTER;
                    if (lastTouchMode == TouchPointer.SINGLE_POINTER && isTouchMoving) {
                        // 单指模式, 立即结束
                        callSinglePointerMove(MotionEvent.ACTION_UP, lastPoint.x, lastPoint.y);
                    }

                    isTouchMoving = false;
                    startDragFlag = false;
                    startScaleFlag = false;
                }
                break;

            case MotionEvent.ACTION_DOWN:
                velocityTracker.clear();
                sumDragX = 0;
                sumDragY = 0;
                if (flingRunnable != null) {
                    flingRunnable.cancelFling();
                    flingRunnable = null;
                }
                if (event.getPointerCount() == 1) {
                    float x = event.getX(), y = event.getY();
                    curTouchPointer = TouchPointer.SINGLE_POINTER;
                    lastPoint.set(x, y);
                    downPoint.set(x, y);
                    downTouchPts = System.currentTimeMillis();
                    allPointValidClick = true;

                    isTouchMoving = false;
                    if (!singlePointerScaleRotateEnable) {
                        onSingleTouchMove(x, y);
                    }
                }
                break;

            case MotionEvent.ACTION_MOVE:
                if (curTouchPointer == TouchPointer.SINGLE_POINTER) {
                    if (singlePointerScaleRotateEnable) {
                        onSinglePointerRotateScale(event);
                        break;
                    }
                    float x = event.getX(), y = event.getY();
                    onSingleTouchMove(x, y);

                    lastPoint.set(x, y);
                    allPointValidClick = allPointValidClick &&
                            PointF.length(x - downPoint.x, y - downPoint.y) < clickRangeThreshold;
                } else if (curTouchPointer == TouchPointer.MULTI_POINTER) {
                    onMultiTouchMode(event);
                }
                break;

            case MotionEvent.ACTION_POINTER_UP:
                if (curTouchPointer == TouchPointer.MULTI_POINTER) {
                    isTouchMoving = false; // 重新计算
                }
                break;

            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP:
                if (curTouchPointer == TouchPointer.SINGLE_POINTER) {
                    float x = event.getX(), y = event.getY();
                    if (allPointValidClick && !startDragFlag &&
                            !startRotateFlag && !startScaleFlag && !singlePointerDragStartFlag) {
                        long times = System.currentTimeMillis() - downTouchPts;
                        if (times < 200) {
                            if (doubleClickEnable) {
                                if (pendingClick != null) {
                                    handler.removeCallbacks(pendingClick);
                                    pendingClick = null;
                                    listener.onDoubleClick(x, y);
                                } else {
                                    pendingClickX = x;
                                    pendingClickY = y;
                                    pendingClick = () -> {
                                        if (pendingClick != null) {
                                            listener.onClick(pendingClickX, pendingClickY);
                                        }
                                        pendingClick = null;
                                    };
                                    handler.postDelayed(pendingClick, 300);
                                }
                            } else {
                                listener.onClick(x, y);
                            }
                        } else if (times >= 500) {
                            listener.onLongClick(x, y);
                        }
                    }

                    if (!singlePointerScaleRotateEnable) {
                        if (!isTouchMoving) {
                            callSinglePointerMove(MotionEvent.ACTION_DOWN, x, y);
                        }
                        callSinglePointerMove(MotionEvent.ACTION_UP, x, y);
                    }
                }

                if (startDragFlag || singlePointerDragStartFlag) {
                    onDragEnd(singlePointerDragStartFlag);
                }
                boolean singlePointer = curTouchPointer != TouchPointer.MULTI_POINTER;
                if (startScaleFlag) {
                    listener.onScaleEnd(lastScaleCenterX, lastScaleCenterY, singlePointer);
                }
                if (startRotateFlag) {
                    listener.onRotateEnd(singlePointer);
                }

                isTouchMoving = false;
                startDragFlag = false;
                startScaleFlag = false;
                startRotateFlag = false;
                singlePointerDragStartFlag = false;
                sumDragX = 0;
                sumDragY = 0;
                lastPoint.set(0, 0);
                curTouchPointer = null;
                velocityTracker.clear();
                break;
        }

        listener.onTouchEventAfter(event);
    }

    private void onDragging(float x, float y, float dx, float dy, boolean singlePointerDrag) {
        sumDragX += dx;
        sumDragY += dy;
        listener.onDrag(x, y, dx, dy, sumDragX, sumDragY, singlePointerDrag);
    }

    private void onDragEnd(boolean singlePointerDrag) {
        int vX = 0, vY = 0;
        if (velocityTracker != null) {
            velocityTracker.computeCurrentVelocity(1000);

            vX = (int) velocityTracker.getXVelocity();
            vY = (int) velocityTracker.getYVelocity();
        }

        if (flingRunnable != null) {
            flingRunnable.cancelFling();
            flingRunnable = null;
        }

        if (listener.onDragEnd(sumDragX, sumDragY, vX, vY, singlePointerDrag)) {
            return;
        }

        if (vX == 0 && vY == 0) {
            return;
        }

        flingRunnable = new FlingRunnable(context, singlePointerDrag);
        flingRunnable.startFling(vX, vY);
    }

    private void onSingleTouchMove(float x, float y) {
//        if (!cachedPoints.isEmpty()) {
//            for (PointF point : cachedPoints) {
//                if (!isTouchMoving) {
//                    callSinglePointerMove(MotionEvent.ACTION_DOWN, point.x, point.y);
//                    isTouchMoving = true;
//                } else {
//                    callSinglePointerMove(MotionEvent.ACTION_MOVE, point.x, point.y);
//                }
//            }
//            cachedPoints.clear();
//        }

        if (!isTouchMoving) {
            callSinglePointerMove(MotionEvent.ACTION_DOWN, x, y);
            isTouchMoving = true;
        } else {
            callSinglePointerMove(MotionEvent.ACTION_MOVE, x, y);
        }
    }

    private final PointF singleLastDragPoint = new PointF();
    private boolean singlePointerDragStartFlag = false;
    private long singlePointerDownTouchPts = 0;

    private void callSinglePointerMove(int action, float x, float y) {
        if (!slideDetectEnable) {
            float dx, dy;
            switch (action) {
                case MotionEvent.ACTION_DOWN:
                    singleLastDragPoint.set(x, y);
                    singlePointerDragStartFlag = false;
                    break;

                case MotionEvent.ACTION_MOVE:
                    dx = x - singleLastDragPoint.x;
                    dy = y - singleLastDragPoint.y;
                    if (singlePointerDragStartFlag) {
                        onDragging(x, y, dx, dy, true);
                        singleLastDragPoint.set(x, y);
                    }
                    if (!singlePointerDragStartFlag && Math.sqrt(dx * dx + dy * dy) > dragThreshold) {
                        singlePointerDragStartFlag = true;
                        singleLastDragPoint.set(x, y);
                        listener.onDragStart(x, y, x, y, true);
                    }
                    break;

                case MotionEvent.ACTION_UP:
                    dx = x - singleLastDragPoint.x;
                    dy = y - singleLastDragPoint.y;
                    if (singlePointerDragStartFlag) {
                        onDragging(x, y, dx, dy, true);
                        singleLastDragPoint.set(x, y);
                    }
                    break;
            }
        } else {
            switch (action) {
                case MotionEvent.ACTION_DOWN:
                    singleLastDragPoint.set(x, y);
                    singlePointerDownTouchPts = System.currentTimeMillis();
                    break;

                case MotionEvent.ACTION_UP:
                    float dx = x - singleLastDragPoint.x;
                    float dy = y - singleLastDragPoint.y;
                    float dis = (float) Math.sqrt(dx * dx + dy * dy);
                    long spendTime = System.currentTimeMillis() - singlePointerDownTouchPts;
                    if (spendTime < 500 && dis > slideThreshold) {
                        float degree = (float) (Math.asin(dy/dis) * 180/Math.PI);
                        if (Math.abs(degree) < 30) {
                            if (dx < 0) {
                                // slide left
                                listener.onSlide(true, false, false, false);
                            } else {
                                // slide right
                                listener.onSlide(false, false, true, false);
                            }
                        } else if (Math.abs(degree) > 60) {
                            if (dy < 0) {
                                // slide up
                                listener.onSlide(false, true, false, false);
                            } else {
                                // slide down
                                listener.onSlide(false, false, false, true);
                            }
                        }
                    }
                    break;
            }
        }
    }

    private float lastScaleCenterX = 0, lastScaleCenterY = 0;
    private void onSinglePointerRotateScale(@NonNull MotionEvent event) {
        float x0 = event.getX(), y0 = event.getY();
        float x1 = singlePointerScaleRotateCenterX;
        float y1 = singlePointerScaleRotateCenterY;

        if (isTouchMoving) {
            ///////////// scale
            float curDx = x0 - x1;
            float curDy = y0 - y1;
            float distance = (float) Math.sqrt(curDx * curDx + curDy * curDy);
            float lastDx = scaleFirstPointer.x - scaleSecondPointer.x;
            float lastDy = scaleFirstPointer.y - scaleSecondPointer.y;
            float lastDistance = (float) Math.sqrt(lastDx * lastDx + lastDy * lastDy);

            float scale = distance / lastDistance;

            if (startScaleFlag) {
                //避免图片每次缩放尺寸过大
                if (scale > 1.05f) {
                    scale = 1.05f;
                }
                lastScaleCenterX = (x0 + x1) / 2f;
                lastScaleCenterY = (y0 + y1) / 2f;
                listener.onScale(lastScaleCenterX, lastScaleCenterY, scale, true);
            } else if (Math.abs(1 - distance / lastDistance) > scaleThreshold) {
                startScaleFlag = true;
                listener.onScaleStart(true);
            }

            //////////// rotate
            float degrees = calculateDegrees(x1 - x0, y1 - y0,
                    rotateSecondPointer.x - rotateFirstPointer.x,
                    rotateSecondPointer.y - rotateFirstPointer.y);
            if (startRotateFlag) {
                listener.onRotate((x0 + x1) / 2f, (y0 + y1) / 2f, degrees, true);
            } else if (Math.abs(degrees) > rotateThreshold) {
                startRotateFlag = true;
                listener.onRotateStart(true);
            }

        } else {
            scaleFirstPointer.set(x0, y0);
            scaleSecondPointer.set(x1, y1);
            startScaleFlag = false;
            rotateFirstPointer.set(x0, y0);
            rotateSecondPointer.set(x1, y1);
            startRotateFlag = false;
        }

        isTouchMoving = true;

        if (startScaleFlag) {
            scaleFirstPointer.set(x0, y0);
            scaleSecondPointer.set(x1, y1);
        }

        if (startRotateFlag) {
            rotateFirstPointer.set(x0, y0);
            rotateSecondPointer.set(x1, y1);
        }
    }

    private void onMultiTouchMode(@NonNull MotionEvent event) {
        if (event.getPointerCount() < 2) {
            isTouchMoving = false;
            return;
        }

        float x0 = event.getX(0), y0 = event.getY(0);
        float x1 = event.getX(1), y1 = event.getY(1);

        if (isTouchMoving) {
            float dx0 = x0 - dragFirstPointer.x;
            float dy0 = y0 - dragFirstPointer.y;
            float dx1 = x1 - dragSecondPointer.x;
            float dy1 = y1 - dragSecondPointer.y;

            if (startDragFlag) {
                float dx = (dx0 + dx1) / 2;
                float dy = (dy0 + dy1) / 2;

                onDragging((x0+x1)/2, (y0+y1)/2, dx, dy, false);
            } else if (PointF.length(dx0, dy0) > dragThreshold && PointF.length(dx1, dy1) > dragThreshold) {
                startDragFlag = true;
                listener.onDragStart(x0, y0, x1, y1, false);
            }

            ///////////// scale

            float curDx = x0 - x1;
            float curDy = y0 - y1;
            float distance = (float) Math.sqrt(curDx * curDx + curDy * curDy);
            float lastDx = scaleFirstPointer.x - scaleSecondPointer.x;
            float lastDy = scaleFirstPointer.y - scaleSecondPointer.y;
            float lastDistance = (float) Math.sqrt(lastDx * lastDx + lastDy * lastDy);

            float scale = distance / lastDistance;
            if (startScaleFlag) {
                lastScaleCenterX = (x0 + x1) / 2f;
                lastScaleCenterY = (y0 + y1) / 2f;
                listener.onScale(lastScaleCenterX, lastScaleCenterY, scale, false);
            } else if (Math.abs(1 - distance / lastDistance) > scaleThreshold) {
                startScaleFlag = true;
                listener.onScaleStart(false);
            }

            //////////// rotate

            float degrees = calculateDegrees(x1 - x0, y1 - y0,
                    rotateSecondPointer.x - rotateFirstPointer.x, rotateSecondPointer.y - rotateFirstPointer.y);
            if (startRotateFlag) {
                listener.onRotate((x0 + x1) / 2f, (y0 + y1) / 2f, degrees, false);
            } else if (Math.abs(degrees) > rotateThreshold) {
                startRotateFlag = true;
                listener.onRotateStart(false);
            }

        } else {
            dragFirstPointer.set(x0, y0);
            dragSecondPointer.set(x1, y1);
            startDragFlag = false;
            scaleFirstPointer.set(x0, y0);
            scaleSecondPointer.set(x1, y1);
            startScaleFlag = false;
            rotateFirstPointer.set(x0, y0);
            rotateSecondPointer.set(x1, y1);
            startRotateFlag = false;
        }

        isTouchMoving = true;
        if (startDragFlag) {
            dragFirstPointer.set(x0, y0);
            dragSecondPointer.set(x1, y1);
        }

        if (startScaleFlag) {
            scaleFirstPointer.set(x0, y0);
            scaleSecondPointer.set(x1, y1);
        }

        if (startRotateFlag) {
            rotateFirstPointer.set(x0, y0);
            rotateSecondPointer.set(x1, y1);
        }
    }

    public float calculateDegrees(float v1x, float v1y, float v2x, float v2y) {
        try {
            float lastDegrees = (float) Math.atan2(v2y, v2x);
            float currentDegrees = (float) Math.atan2(v1y, v1x);
            return (float) Math.toDegrees(currentDegrees - lastDegrees);
        } catch (Throwable e) {
            e.printStackTrace();
        }
        return 0;
    }

    private int dp2px(int dp) {
        float density = context.getResources().getConfiguration().densityDpi / 160.0f;
        density = Math.max(1, density);
        return (int) (dp * (density < 0.1f ? 3 : density) + 0.5f);
    }

    private class FlingRunnable implements Runnable {
        private final Scroller scroller;
        private int currentX, currentY;
        private final boolean singlePointer;

        public FlingRunnable(@NonNull Context context, boolean singlePointer) {
            scroller = new Scroller(context); // OverScroller 的效果有点奇怪，滑动的方向有一点偏差
            this.singlePointer = singlePointer;
        }

        public void cancelFling() {
            boolean isRunning = !scroller.isFinished();
            scroller.forceFinished(true);
            handler.removeCallbacks(this);
            if (isRunning) {
                listener.onFlingEnd(singlePointer);
            }
        }

        public boolean isFinished() {
            return scroller.isFinished();
        }

        public void startFling(int velocityX, int velocityY) {
            currentX = 0;
            currentY = 0;

            int unit = 100000;
            scroller.fling(0, 0, velocityX, velocityY, -unit, unit, -unit, unit);
            handler.post(this);
        }

        @Override
        public void run() {
            if (scroller.computeScrollOffset()) {
                int newX = scroller.getCurrX();
                int newY = scroller.getCurrY();

                float dx = newX - currentX;
                float dy = newY - currentY;

//                ILOG.utilsInfo("fling: dx: " + dx + ", dy: " + dy);
                if (dx != 0 || dy != 0) {
                    if (listener.onFling(dx, dy, singlePointer)) {
                        scroller.forceFinished(true);
                        return;
                    }
                }

                currentX = newX;
                currentY = newY;
                // Post On animation
                if (gestureView != null) {
                    gestureView.postOnAnimation(this);
                } else {
                    // 这种方式的动画，不够丝滑
                    handler.postDelayed(this, 16);
                }
            } else {
                listener.onFlingEnd(singlePointer);
            }
        }
    }

    enum TouchPointer {
        SINGLE_POINTER,
        MULTI_POINTER
    }
}
