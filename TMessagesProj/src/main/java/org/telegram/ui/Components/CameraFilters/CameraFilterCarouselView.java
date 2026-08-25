package org.telegram.ui.Components.CameraFilters;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.util.SparseArray;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.Scroller;

import androidx.core.content.ContextCompat;

import org.telegram.messenger.AndroidUtilities;

import java.util.List;

public class CameraFilterCarouselView extends View {

    public interface Delegate {
        void onFilterSelected(CameraFilterModel filter, boolean byUser);
        void onShutterClick();
        boolean onShutterLongClick();
        void onShutterRelease();
    }

    private final List<CameraFilterModel> filters;
    private Delegate delegate;

    private final Paint circlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint highlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint recordingRingPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF itemRect = new RectF();
    private final RectF highlightRect = new RectF();

    private final SparseArray<Drawable> iconCache = new SparseArray<>();

    private final Scroller scroller;
    private VelocityTracker velocityTracker;
    private final DecelerateInterpolator interpolator = new DecelerateInterpolator();

    private float scrollX = 0f;
    private int selectedIndex = 0;
    private int lastHapticIndex = 0;

    private float lastTouchX = 0f;
    private float touchStartX_val = 0f;
    private float touchStartY_val = 0f;
    private boolean isDragging = false;
    private boolean isRecording = false;
    private boolean isCenterPressed = false;
    private ValueAnimator snapAnimator;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable longPressRunnable;

    private static final int ITEM_SIZE_DP = 64;
    private static final int ITEM_SPACING_DP = 18;
    private int itemSizePx;
    private int itemSpacingPx;
    private int itemTotalWidthPx;

    public CameraFilterCarouselView(Context context) {
        super(context);
        filters = CameraFilterRegistry.getAllFilters();
        scroller = new Scroller(context, interpolator);

        itemSizePx = AndroidUtilities.dp(ITEM_SIZE_DP);
        itemSpacingPx = AndroidUtilities.dp(ITEM_SPACING_DP);
        itemTotalWidthPx = itemSizePx + itemSpacingPx;

        shadowPaint.setColor(0x44000000);
        shadowPaint.setStyle(Paint.Style.FILL);

        highlightPaint.setColor(0x28FFFFFF);
        highlightPaint.setStyle(Paint.Style.FILL);

        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeWidth(AndroidUtilities.dp(4.0f));
        ringPaint.setColor(0xFFFFFFFF);

        recordingRingPaint.setStyle(Paint.Style.STROKE);
        recordingRingPaint.setStrokeWidth(AndroidUtilities.dp(5.0f));
        recordingRingPaint.setColor(0xFFFF2A55);

        textPaint.setColor(0xFFFFFFFF);
        textPaint.setTextSize(AndroidUtilities.dp(11));
        textPaint.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setShadowLayer(AndroidUtilities.dp(3), 0, AndroidUtilities.dp(1.5f), 0xCC000000);
    }

    private Drawable getCachedDrawable(int resId) {
        if (resId <= 0) return null;
        Drawable d = iconCache.get(resId);
        if (d == null) {
            try {
                d = ContextCompat.getDrawable(getContext(), resId);
                if (d != null) {
                    d = d.mutate();
                    iconCache.put(resId, d);
                }
            } catch (Throwable ignore) {}
        }
        return d;
    }

    public void setDelegate(Delegate delegate) {
        this.delegate = delegate;
    }

    public void setSelectedFilter(int filterId, boolean animated) {
        for (int i = 0; i < filters.size(); i++) {
            if (filters.get(i).id == filterId) {
                setSelectedIndex(i, animated, false);
                break;
            }
        }
    }

    public CameraFilterModel getSelectedFilter() {
        if (selectedIndex >= 0 && selectedIndex < filters.size()) {
            return filters.get(selectedIndex);
        }
        return filters.get(0);
    }

    public void setSelectedIndex(int index, boolean animated, boolean byUser) {
        if (index < 0) index = 0;
        if (index >= filters.size()) index = filters.size() - 1;

        selectedIndex = index;
        float targetScrollX = index * itemTotalWidthPx;

        if (animated) {
            if (snapAnimator != null) {
                snapAnimator.cancel();
            }
            snapAnimator = ValueAnimator.ofFloat(scrollX, targetScrollX);
            snapAnimator.setDuration(240);
            snapAnimator.setInterpolator(interpolator);
            snapAnimator.addUpdateListener(animation -> {
                scrollX = (float) animation.getAnimatedValue();
                invalidate();
            });
            snapAnimator.start();
        } else {
            scrollX = targetScrollX;
            invalidate();
        }

        if (delegate != null) {
            delegate.onFilterSelected(filters.get(selectedIndex), byUser);
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int height = AndroidUtilities.dp(110);
        int width = MeasureSpec.getSize(widthMeasureSpec);
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int width = getWidth();
        int height = getHeight();
        if (width == 0 || height == 0) return;

        float centerX = width / 2f;
        float centerY = height / 2f - AndroidUtilities.dp(8);

        float currentScroll = scrollX;

        for (int i = 0; i < filters.size(); i++) {
            CameraFilterModel filter = filters.get(i);
            float itemCenterX = centerX + (i * itemTotalWidthPx) - currentScroll;

            if (itemCenterX < -itemSizePx || itemCenterX > width + itemSizePx) {
                continue;
            }

            float distFromCenter = Math.abs(itemCenterX - centerX);
            float progressToCenter = Math.max(0f, 1f - (distFromCenter / (itemTotalWidthPx * 1.3f)));

            float baseScale = 0.72f + 0.28f * progressToCenter;
            if (i == selectedIndex && isCenterPressed) {
                baseScale *= 0.92f;
            }
            float radius = (itemSizePx / 2f) * baseScale;

            itemRect.set(
                itemCenterX - radius,
                centerY - radius,
                itemCenterX + radius,
                centerY + radius
            );

            // 1. Draw smooth drop shadow
            canvas.drawCircle(itemCenterX, centerY + AndroidUtilities.dp(2), radius, shadowPaint);

            // 2. Draw circular lens body gradient
            if (filter.isOriginal()) {
                circlePaint.setShader(null);
                circlePaint.setColor(0x881A1A1A);
                circlePaint.setStyle(Paint.Style.FILL);
                canvas.drawCircle(itemCenterX, centerY, radius, circlePaint);

                circlePaint.setStyle(Paint.Style.STROKE);
                circlePaint.setStrokeWidth(AndroidUtilities.dp(2.0f));
                circlePaint.setColor(0xAAFFFFFF);
                canvas.drawCircle(itemCenterX, centerY, radius - AndroidUtilities.dp(1), circlePaint);
                circlePaint.setStyle(Paint.Style.FILL);
            } else {
                LinearGradient gradient = new LinearGradient(
                    itemRect.left, itemRect.top,
                    itemRect.right, itemRect.bottom,
                    filter.primaryColor, filter.secondaryColor,
                    Shader.TileMode.CLAMP
                );
                circlePaint.setShader(gradient);
                circlePaint.setStyle(Paint.Style.FILL);
                canvas.drawCircle(itemCenterX, centerY, radius, circlePaint);

                // Subtle inner border ring
                circlePaint.setShader(null);
                circlePaint.setStyle(Paint.Style.STROKE);
                circlePaint.setStrokeWidth(AndroidUtilities.dp(1.2f));
                circlePaint.setColor(0x40FFFFFF);
                canvas.drawCircle(itemCenterX, centerY, radius - AndroidUtilities.dp(0.6f), circlePaint);
                circlePaint.setStyle(Paint.Style.FILL);
            }

            // 3. Draw 3D glossy specular top highlight arc
            highlightRect.set(
                itemCenterX - radius + AndroidUtilities.dp(3),
                centerY - radius + AndroidUtilities.dp(3),
                itemCenterX + radius - AndroidUtilities.dp(3),
                centerY + radius * 0.3f
            );
            canvas.drawOval(highlightRect, highlightPaint);

            // 4. Draw Custom XML Vector Drawable
            Drawable iconDrawable = getCachedDrawable(filter.iconResId);
            if (iconDrawable != null) {
                int iconSize = Math.round(radius * 0.96f);
                int left = Math.round(itemCenterX - iconSize / 2f);
                int top = Math.round(centerY - iconSize / 2f);
                iconDrawable.setBounds(left, top, left + iconSize, top + iconSize);
                iconDrawable.setAlpha((int) (210 + 45 * progressToCenter));
                iconDrawable.draw(canvas);
            }

            // 5. Draw Snapchat outer selection ring
            if (progressToCenter > 0.05f) {
                float ringRadius = radius + AndroidUtilities.dp(5.5f) * progressToCenter;
                if (i == selectedIndex && isRecording) {
                    canvas.drawCircle(itemCenterX, centerY, ringRadius, recordingRingPaint);
                } else {
                    ringPaint.setAlpha((int) (255 * progressToCenter));
                    canvas.drawCircle(itemCenterX, centerY, ringRadius, ringPaint);
                }
            }

            // 6. Draw filter title under lens
            float textAlpha = Math.max(0f, (progressToCenter - 0.25f) / 0.75f);
            if (textAlpha > 0.01f) {
                textPaint.setAlpha((int) (255 * textAlpha));
                canvas.drawText(
                    filter.getTitle(),
                    itemCenterX,
                    centerY + radius + AndroidUtilities.dp(16),
                    textPaint
                );
            }
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (velocityTracker == null) {
            velocityTracker = VelocityTracker.obtain();
        }
        velocityTracker.addMovement(event);

        int action = event.getActionMasked();
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                if (snapAnimator != null) snapAnimator.cancel();
                if (!scroller.isFinished()) scroller.abortAnimation();
                lastTouchX = event.getX();
                touchStartX_val = event.getX();
                touchStartY_val = event.getY();
                isDragging = false;
                isRecording = false;

                float centerX = getWidth() / 2f;
                float centerY = getHeight() / 2f - AndroidUtilities.dp(8);
                float distFromCenter = (float) Math.hypot(event.getX() - centerX, event.getY() - centerY);
                if (distFromCenter <= AndroidUtilities.dp(44)) {
                    isCenterPressed = true;
                    invalidate();

                    if (longPressRunnable != null) {
                        handler.removeCallbacks(longPressRunnable);
                    }
                    longPressRunnable = () -> {
                        if (isCenterPressed && !isDragging && delegate != null) {
                            if (delegate.onShutterLongClick()) {
                                isRecording = true;
                                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                                invalidate();
                            }
                        }
                    };
                    handler.postDelayed(longPressRunnable, 450);
                }
                return true;

            case MotionEvent.ACTION_MOVE:
                float dx = event.getX() - lastTouchX;
                lastTouchX = event.getX();

                if (!isDragging && Math.abs(event.getX() - touchStartX_val) > AndroidUtilities.dp(8)) {
                    isDragging = true;
                    isCenterPressed = false;
                    if (longPressRunnable != null) {
                        handler.removeCallbacks(longPressRunnable);
                        longPressRunnable = null;
                    }
                    if (getParent() != null) {
                        getParent().requestDisallowInterceptTouchEvent(true);
                    }
                }

                if (isDragging) {
                    scrollX -= dx;
                    float minScroll = 0f;
                    float maxScroll = (filters.size() - 1) * itemTotalWidthPx;
                    scrollX = Math.max(-itemTotalWidthPx * 0.4f, Math.min(maxScroll + itemTotalWidthPx * 0.4f, scrollX));

                    int nearestIndex = Math.round(scrollX / itemTotalWidthPx);
                    nearestIndex = Math.max(0, Math.min(filters.size() - 1, nearestIndex));
                    if (nearestIndex != lastHapticIndex) {
                        lastHapticIndex = nearestIndex;
                        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                    }

                    if (nearestIndex != selectedIndex && nearestIndex >= 0 && nearestIndex < filters.size()) {
                        selectedIndex = nearestIndex;
                        if (delegate != null) {
                            delegate.onFilterSelected(filters.get(selectedIndex), true);
                        }
                    }

                    invalidate();
                }
                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (longPressRunnable != null) {
                    handler.removeCallbacks(longPressRunnable);
                    longPressRunnable = null;
                }

                if (isRecording) {
                    isRecording = false;
                    if (delegate != null) {
                        delegate.onShutterRelease();
                    }
                } else if (!isDragging && action == MotionEvent.ACTION_UP) {
                    float clickX = event.getX();
                    float cX = getWidth() / 2f;
                    float relativeX = clickX - cX + scrollX;
                    int clickedIndex = Math.round(relativeX / itemTotalWidthPx);
                    clickedIndex = Math.max(0, Math.min(filters.size() - 1, clickedIndex));

                    if (clickedIndex != selectedIndex) {
                        // Tapped any filter item -> immediately select, snap to center, and apply filter
                        setSelectedIndex(clickedIndex, true, true);
                        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                    } else {
                        // Tapped the active center filter -> Shutter click to capture photo
                        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                        if (delegate != null) {
                            delegate.onShutterClick();
                        }
                    }
                } else if (isDragging) {
                    velocityTracker.computeCurrentVelocity(1000);
                    float velocityX = velocityTracker.getXVelocity();
                    float targetScroll = scrollX - (velocityX * 0.18f);
                    int targetIndex = Math.round(targetScroll / itemTotalWidthPx);
                    targetIndex = Math.max(0, Math.min(filters.size() - 1, targetIndex));
                    setSelectedIndex(targetIndex, true, true);
                }

                isCenterPressed = false;
                if (velocityTracker != null) {
                    velocityTracker.recycle();
                    velocityTracker = null;
                }
                isDragging = false;
                invalidate();
                return true;
        }
        return super.onTouchEvent(event);
    }
}
