package tw.nekomimi.nekogram.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.SizeNotifierFrameLayout;

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
    private float blurIntensity = 0.5f; // 0.0 to 1.0
    private boolean isDraggingSlider = false;
    private final float sliderWidth = AndroidUtilities.dp(4);
    private final float sliderHeight = AndroidUtilities.dp(200);
    private final float sliderMargin = AndroidUtilities.dp(16);
    private final float sliderKnobRadius = AndroidUtilities.dp(8);
    
    private final Paint blurPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint dimPaint = new Paint();
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sliderBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sliderProgressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sliderKnobPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    
    private Bitmap blurredBitmap;
    private Canvas blurCanvas;
    private final float DOWN_SCALE = 8f;
    private View targetViewToBlur;
    
    private ChatActivity chatActivity;

    public PrivacyBlurOverlayView(Context context, ChatActivity chatActivity, View targetViewToBlur) {
        super(context);
        this.chatActivity = chatActivity;
        this.targetViewToBlur = targetViewToBlur;
        init();
    }

    public PrivacyBlurOverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        topY = AndroidUtilities.dp(150);
        bottomY = AndroidUtilities.dp(400);

        dimPaint.setColor(0x40000000); // Semi-transparent black over blur
        
        linePaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        linePaint.setAlpha(200);
        linePaint.setStrokeWidth(lineThickness);
        linePaint.setShadowLayer(AndroidUtilities.dp(2), 0, AndroidUtilities.dp(1), 0x40000000);
        
        sliderBgPaint.setColor(Theme.getColor(Theme.key_divider));
        sliderBgPaint.setStrokeWidth(sliderWidth);
        sliderBgPaint.setStrokeCap(Paint.Cap.ROUND);
        
        sliderProgressPaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        sliderProgressPaint.setStrokeWidth(sliderWidth);
        sliderProgressPaint.setStrokeCap(Paint.Cap.ROUND);
        
        sliderKnobPaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        sliderKnobPaint.setShadowLayer(AndroidUtilities.dp(2), 0, AndroidUtilities.dp(1), 0x40000000);
        
        setWillNotDraw(false);
        setVisibility(GONE);
    }
    
    public void toggleVisibility() {
        isVisible = !isVisible;
        if (isVisible) {
            setVisibility(VISIBLE);
            updateBlur();
        } else {
            setVisibility(GONE);
            if (blurredBitmap != null) {
                blurredBitmap.recycle();
                blurredBitmap = null;
            }
        }
    }

    private void updateBlur() {
        if (targetViewToBlur == null || targetViewToBlur.getWidth() == 0 || targetViewToBlur.getHeight() == 0) return;
        
        int w = targetViewToBlur.getWidth();
        int h = targetViewToBlur.getHeight();
        int bw = (int) (w / DOWN_SCALE);
        int bh = (int) (h / DOWN_SCALE);
        
        if (bw == 0 || bh == 0) return;

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
        
        targetViewToBlur.draw(blurCanvas);
        
        blurCanvas.restore();
        
        int radius = Math.max(2, (int) (maxBlurRadius() * blurIntensity));
        if (radius > 1) {
            Utilities.stackBlurBitmap(blurredBitmap, radius);
        }
        invalidate();
    }
    
    private int maxBlurRadius() {
        return Math.max(7, Math.max(targetViewToBlur.getHeight(), targetViewToBlur.getWidth()) / 120);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        if (bottomY == AndroidUtilities.dp(400) && getMeasuredHeight() > 0) {
            // First measure initialization
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
                if (isDraggingSlider) {
                    isDraggingSlider = false;
                    updateBlur();
                }
                break;
        }

        // Intercept touches outside the clear area to prevent interacting with blurred messages
        return y < topY || y > bottomY;
    }

    private void updateSliderFromTouch(float y, float sliderYStart, float sliderHeight) {
        float progress = 1f - Math.max(0, Math.min(1f, (y - sliderYStart) / sliderHeight));
        if (progress != blurIntensity) {
            blurIntensity = Math.max(0.01f, progress);
            updateBlur();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (!isVisible) return;
        
        // Ensure blurred bitmap is updated if size changed
        if (blurredBitmap == null) {
            updateBlur();
        }
        
        // Draw top blurred area
        if (topY > 0) {
            canvas.save();
            canvas.clipRect(0, 0, getMeasuredWidth(), topY);
            drawBlurredBackground(canvas);
            canvas.restore();
            canvas.drawLine(0, topY, getMeasuredWidth(), topY, linePaint);
        }

        // Draw bottom blurred area
        if (bottomY < getMeasuredHeight()) {
            canvas.save();
            canvas.clipRect(0, bottomY, getMeasuredWidth(), getMeasuredHeight());
            drawBlurredBackground(canvas);
            canvas.restore();
            canvas.drawLine(0, bottomY, getMeasuredWidth(), bottomY, linePaint);
        }

        // Draw intensity slider on right edge
        float sliderX = getMeasuredWidth() - sliderMargin - sliderWidth / 2f;
        float sliderTop = (getMeasuredHeight() - sliderHeight) / 2f;
        float sliderBottom = sliderTop + sliderHeight;
        
        canvas.drawLine(sliderX, sliderTop, sliderX, sliderBottom, sliderBgPaint);
        
        float progressY = sliderBottom - (sliderHeight * blurIntensity);
        canvas.drawLine(sliderX, progressY, sliderX, sliderBottom, sliderProgressPaint);
        
        canvas.drawCircle(sliderX, progressY, sliderKnobRadius, sliderKnobPaint);
    }
    
    private void drawBlurredBackground(Canvas canvas) {
        if (blurredBitmap != null) {
            canvas.save();
            canvas.scale(DOWN_SCALE, DOWN_SCALE);
            canvas.drawBitmap(blurredBitmap, 0, 0, blurPaint);
            canvas.restore();
            canvas.drawRect(canvas.getClipBounds(), dimPaint);
        } else {
            canvas.drawRect(canvas.getClipBounds(), dimPaint);
        }
    }
    
    public void invalidateBlur() {
        if (isVisible && !isDraggingTop && !isDraggingBottom && !isDraggingSlider) {
            updateBlur();
        }
    }
}
