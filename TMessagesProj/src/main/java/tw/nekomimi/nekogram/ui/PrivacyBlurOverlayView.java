package tw.nekomimi.nekogram.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ChatActivity;

public class PrivacyBlurOverlayView extends View {

    private boolean isVisible = false;
    private float topY;
    private float bottomY;
    private final float lineThickness = AndroidUtilities.dp(2);
    private final float TOUCH_SLOP = AndroidUtilities.dp(24);

    // Drag state
    private boolean isDraggingTop = false;
    private boolean isDraggingBottom = false;

    // Slider state
    private float blurIntensity = 0.5f;
    private boolean isDraggingSlider = false;
    private final float sliderWidth = AndroidUtilities.dp(4);
    private final float sliderHeight = AndroidUtilities.dp(200);
    private final float sliderMargin = AndroidUtilities.dp(16);
    private final float sliderKnobRadius = AndroidUtilities.dp(10);

    private final Paint dimPaint = new Paint();
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arrowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sliderBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sliderProgressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sliderKnobPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint blurBitmapPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint handlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private Bitmap blurredBitmap;
    private Canvas blurCanvas;
    private final float DOWN_SCALE = 8f;
    private View targetViewToBlur;

    private ChatActivity chatActivity;
    private final Path arrowPath = new Path();
    private final RectF handleRect = new RectF();

    public PrivacyBlurOverlayView(Context context, ChatActivity chatActivity, View targetViewToBlur) {
        super(context);
        this.chatActivity = chatActivity;
        this.targetViewToBlur = targetViewToBlur;
        init();
    }

    private void init() {
        topY = AndroidUtilities.dp(150);
        bottomY = AndroidUtilities.dp(400);

        dimPaint.setColor(0x55000000);

        linePaint.setColor(Color.WHITE);
        linePaint.setAlpha(230);
        linePaint.setStrokeWidth(lineThickness);
        linePaint.setStyle(Paint.Style.STROKE);

        arrowPaint.setColor(0xFF333333);
        arrowPaint.setAlpha(200);
        arrowPaint.setStrokeWidth(AndroidUtilities.dp(2));
        arrowPaint.setStyle(Paint.Style.STROKE);
        arrowPaint.setStrokeCap(Paint.Cap.ROUND);
        arrowPaint.setStrokeJoin(Paint.Join.ROUND);

        handlePaint.setColor(Color.WHITE);
        handlePaint.setAlpha(200);

        sliderBgPaint.setColor(0x44FFFFFF);
        sliderBgPaint.setStrokeWidth(sliderWidth);
        sliderBgPaint.setStrokeCap(Paint.Cap.ROUND);

        sliderProgressPaint.setColor(Color.WHITE);
        sliderProgressPaint.setStrokeWidth(sliderWidth);
        sliderProgressPaint.setStrokeCap(Paint.Cap.ROUND);

        sliderKnobPaint.setColor(Color.WHITE);

        setWillNotDraw(false);
        setVisibility(GONE);
    }

    public void toggleVisibility() {
        isVisible = !isVisible;
        if (isVisible) {
            setVisibility(VISIBLE);
            postInvalidateOnAnimation();
        } else {
            setVisibility(GONE);
            if (blurredBitmap != null) {
                blurredBitmap.recycle();
                blurredBitmap = null;
            }
        }
    }

    private void captureAndBlur() {
        if (targetViewToBlur == null || targetViewToBlur.getWidth() == 0 || targetViewToBlur.getHeight() == 0) return;

        int w = targetViewToBlur.getWidth();
        int h = targetViewToBlur.getHeight();
        int bw = (int) (w / DOWN_SCALE);
        int bh = (int) (h / DOWN_SCALE);

        if (bw <= 0 || bh <= 0) return;

        if (blurredBitmap == null || blurredBitmap.getWidth() != bw || blurredBitmap.getHeight() != bh) {
            if (blurredBitmap != null) {
                blurredBitmap.recycle();
            }
            try {
                blurredBitmap = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888);
                blurCanvas = new Canvas(blurredBitmap);
            } catch (Exception e) {
                return;
            }
        }

        blurredBitmap.eraseColor(Color.TRANSPARENT);
        blurCanvas.save();
        blurCanvas.scale(1f / DOWN_SCALE, 1f / DOWN_SCALE);
        // Temporarily hide this overlay to avoid capturing it in the blur
        setAlpha(0f);
        targetViewToBlur.draw(blurCanvas);
        setAlpha(1f);
        blurCanvas.restore();

        int radius = Math.max(2, (int) (maxBlurRadius() * blurIntensity));
        if (radius > 1) {
            Utilities.stackBlurBitmap(blurredBitmap, radius);
        }
    }

    private int maxBlurRadius() {
        if (targetViewToBlur == null) return 10;
        return Math.max(10, Math.max(targetViewToBlur.getHeight(), targetViewToBlur.getWidth()) / 80);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        if (bottomY == AndroidUtilities.dp(400) && getMeasuredHeight() > 0) {
            topY = getMeasuredHeight() / 3f;
            bottomY = getMeasuredHeight() * 2f / 3f;
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!isVisible) return false;

        float x = event.getX();
        float y = event.getY();
        int action = event.getActionMasked();

        float sliderX = getMeasuredWidth() - sliderMargin - sliderWidth / 2f;
        float sliderYStart = (getMeasuredHeight() - sliderHeight) / 2f;
        float sliderYEnd = sliderYStart + sliderHeight;

        switch (action) {
            case MotionEvent.ACTION_DOWN:
                if (Math.abs(x - sliderX) < TOUCH_SLOP * 2 && y >= sliderYStart - TOUCH_SLOP && y <= sliderYEnd + TOUCH_SLOP) {
                    isDraggingSlider = true;
                    updateSliderFromTouch(y, sliderYStart, sliderHeight);
                    return true;
                } else if (Math.abs(y - topY) <= TOUCH_SLOP) {
                    isDraggingTop = true;
                    return true;
                } else if (Math.abs(y - bottomY) <= TOUCH_SLOP) {
                    isDraggingBottom = true;
                    return true;
                }
                break;
            case MotionEvent.ACTION_MOVE:
                if (isDraggingSlider) {
                    updateSliderFromTouch(y, sliderYStart, sliderHeight);
                    return true;
                } else if (isDraggingTop) {
                    topY = Math.max(0, Math.min(y, bottomY - AndroidUtilities.dp(50)));
                    invalidate();
                    return true;
                } else if (isDraggingBottom) {
                    bottomY = Math.max(topY + AndroidUtilities.dp(50), Math.min(y, getMeasuredHeight()));
                    invalidate();
                    return true;
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                isDraggingTop = false;
                isDraggingBottom = false;
                isDraggingSlider = false;
                break;
        }

        // Intercept touches in blurred areas
        return y < topY || y > bottomY;
    }

    private void updateSliderFromTouch(float y, float sliderYStart, float sliderHeight) {
        float progress = 1f - Math.max(0, Math.min(1f, (y - sliderYStart) / sliderHeight));
        if (progress != blurIntensity) {
            blurIntensity = Math.max(0.05f, progress);
            invalidate();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (!isVisible) return;

        // Always re-capture and blur on every frame for real-time sync with scrolling
        captureAndBlur();

        int width = getMeasuredWidth();
        int height = getMeasuredHeight();

        // Draw top blurred area
        if (topY > 0) {
            canvas.save();
            canvas.clipRect(0, 0, width, topY);
            drawBlurredBackground(canvas);
            canvas.restore();
        }

        // Draw bottom blurred area
        if (bottomY < height) {
            canvas.save();
            canvas.clipRect(0, bottomY, width, height);
            drawBlurredBackground(canvas);
            canvas.restore();
        }

        // Draw top line with handle and arrow
        drawLineWithHandle(canvas, topY, true, width);

        // Draw bottom line with handle and arrow
        drawLineWithHandle(canvas, bottomY, false, width);

        // Draw intensity slider on right edge
        drawSlider(canvas, width, height);

        // Keep redrawing while visible for real-time blur
        if (isVisible) {
            postInvalidateOnAnimation();
        }
    }

    private void drawLineWithHandle(Canvas canvas, float lineY, boolean isTopLine, int viewWidth) {
        // Draw the horizontal line
        canvas.drawLine(0, lineY, viewWidth, lineY, linePaint);

        // Draw center handle pill
        float handleW = AndroidUtilities.dp(40);
        float handleH = AndroidUtilities.dp(16);
        float cx = viewWidth / 2f;
        handleRect.set(cx - handleW / 2f, lineY - handleH / 2f, cx + handleW / 2f, lineY + handleH / 2f);
        canvas.drawRoundRect(handleRect, AndroidUtilities.dp(8), AndroidUtilities.dp(8), handlePaint);

        // Draw arrow inside the handle
        float arrowSize = AndroidUtilities.dp(4);
        arrowPath.reset();
        if (isTopLine) {
            // Up arrow (▲) — indicates drag upward to extend blur area
            float ay = lineY - arrowSize / 2f;
            arrowPath.moveTo(cx - arrowSize, ay + arrowSize);
            arrowPath.lineTo(cx, ay);
            arrowPath.lineTo(cx + arrowSize, ay + arrowSize);
        } else {
            // Down arrow (▼) — indicates drag downward to extend blur area
            float ay = lineY - arrowSize / 2f;
            arrowPath.moveTo(cx - arrowSize, ay);
            arrowPath.lineTo(cx, ay + arrowSize);
            arrowPath.lineTo(cx + arrowSize, ay);
        }
        canvas.drawPath(arrowPath, arrowPaint);
    }

    private void drawSlider(Canvas canvas, int viewWidth, int viewHeight) {
        float sliderX = viewWidth - sliderMargin - sliderWidth / 2f;
        float sliderTop = (viewHeight - sliderHeight) / 2f;
        float sliderBottom = sliderTop + sliderHeight;

        // Slider background track
        canvas.drawLine(sliderX, sliderTop, sliderX, sliderBottom, sliderBgPaint);

        // Slider progress
        float progressY = sliderBottom - (sliderHeight * blurIntensity);
        canvas.drawLine(sliderX, progressY, sliderX, sliderBottom, sliderProgressPaint);

        // Slider knob
        canvas.drawCircle(sliderX, progressY, sliderKnobRadius, sliderKnobPaint);
    }

    private void drawBlurredBackground(Canvas canvas) {
        if (blurredBitmap != null) {
            canvas.save();
            canvas.scale(DOWN_SCALE, DOWN_SCALE);
            canvas.drawBitmap(blurredBitmap, 0, 0, blurBitmapPaint);
            canvas.restore();
            canvas.drawRect(canvas.getClipBounds(), dimPaint);
        } else {
            canvas.drawRect(canvas.getClipBounds(), dimPaint);
        }
    }

    public void invalidateBlur() {
        if (isVisible) {
            invalidate();
        }
    }
}
