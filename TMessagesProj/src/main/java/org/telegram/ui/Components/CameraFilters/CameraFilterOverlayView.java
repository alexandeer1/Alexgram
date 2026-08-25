package org.telegram.ui.Components.CameraFilters;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;

public class CameraFilterOverlayView extends FrameLayout {

    public interface FilterOverlayDelegate {
        void onFilterChanged(int filterType, float intensity);
        void onCarouselVisibilityChanged(boolean visible);
        void onShutterClick();
        boolean onShutterLongClick();
        void onShutterRelease();
    }

    private final CameraFilterCarouselView carouselView;
    private final FrameLayout badgeContainer;
    private final TextView badgeTitleTextView;
    private final TextView badgeSubtitleTextView;
    private final ImageView filterToggleButton;
    private final LinearLayout sliderContainer;
    private final SeekBar intensitySeekBar;
    private final TextView intensityTextView;

    private FilterOverlayDelegate delegate;
    private boolean carouselVisible = false;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable hideBadgeRunnable;

    private int currentFilterType = CameraFilterType.ORIGINAL;
    private float currentIntensity = 1.0f;

    public CameraFilterOverlayView(Context context) {
        super(context);
        setClipChildren(false);

        // 1. Filter Badge Container (top center)
        badgeContainer = new FrameLayout(context);
        GradientDrawable badgeBg = new GradientDrawable();
        badgeBg.setShape(GradientDrawable.RECTANGLE);
        badgeBg.setCornerRadius(AndroidUtilities.dp(20));
        badgeBg.setColor(0xB3000000);
        badgeBg.setStroke(AndroidUtilities.dp(1f), 0x33FFFFFF);
        badgeContainer.setBackground(badgeBg);
        badgeContainer.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(7), AndroidUtilities.dp(20), AndroidUtilities.dp(7));
        badgeContainer.setAlpha(0f);
        badgeContainer.setVisibility(View.GONE);

        LinearLayout badgeTextLayout = new LinearLayout(context);
        badgeTextLayout.setOrientation(LinearLayout.VERTICAL);
        badgeTextLayout.setGravity(Gravity.CENTER_HORIZONTAL);

        badgeTitleTextView = new TextView(context);
        badgeTitleTextView.setTextColor(0xFFFFFFFF);
        badgeTitleTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        badgeTitleTextView.setTypeface(AndroidUtilities.bold());
        badgeTitleTextView.setGravity(Gravity.CENTER_HORIZONTAL);
        badgeTextLayout.addView(badgeTitleTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

        badgeSubtitleTextView = new TextView(context);
        badgeSubtitleTextView.setTextColor(0xCCFFFFFF);
        badgeSubtitleTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11);
        badgeSubtitleTextView.setGravity(Gravity.CENTER_HORIZONTAL);
        badgeTextLayout.addView(badgeSubtitleTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

        badgeContainer.addView(badgeTextLayout, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));
        addView(badgeContainer, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 0, 0, 0, 168));

        // 2. Intensity Slider Container (above the carousel)
        sliderContainer = new LinearLayout(context);
        sliderContainer.setOrientation(LinearLayout.HORIZONTAL);
        sliderContainer.setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable sliderBg = new GradientDrawable();
        sliderBg.setShape(GradientDrawable.RECTANGLE);
        sliderBg.setCornerRadius(AndroidUtilities.dp(16));
        sliderBg.setColor(0xB3000000);
        sliderBg.setStroke(AndroidUtilities.dp(1f), 0x33FFFFFF);
        sliderContainer.setBackground(sliderBg);
        sliderContainer.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(6), AndroidUtilities.dp(16), AndroidUtilities.dp(6));
        sliderContainer.setAlpha(0f);
        sliderContainer.setVisibility(View.GONE);

        intensityTextView = new TextView(context);
        intensityTextView.setTextColor(0xFFFFFFFF);
        intensityTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        intensityTextView.setTypeface(AndroidUtilities.bold());
        intensityTextView.setText("100%");
        sliderContainer.addView(intensityTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 0, 10, 0));

        intensitySeekBar = new SeekBar(context);
        intensitySeekBar.setMax(100);
        intensitySeekBar.setProgress(100);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            intensitySeekBar.setProgressTintList(android.content.res.ColorStateList.valueOf(0xFFFFFFFF));
            intensitySeekBar.setThumbTintList(android.content.res.ColorStateList.valueOf(0xFFFFFFFF));
        }
        intensitySeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    currentIntensity = progress / 100f;
                    intensityTextView.setText(progress + "%");
                    if (delegate != null) {
                        delegate.onFilterChanged(currentFilterType, currentIntensity);
                    }
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        sliderContainer.addView(intensitySeekBar, LayoutHelper.createLinear(150, LayoutHelper.WRAP_CONTENT));

        addView(sliderContainer, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 0, 0, 0, 128));

        // 3. Snapchat Lens Carousel View
        carouselView = new CameraFilterCarouselView(context);
        carouselView.setAlpha(0f);
        carouselView.setVisibility(View.GONE);
        carouselView.setDelegate(new CameraFilterCarouselView.Delegate() {
            @Override
            public void onFilterSelected(CameraFilterModel filter, boolean byUser) {
                currentFilterType = filter.id;
                currentIntensity = filter.intensity;
                intensitySeekBar.setProgress(Math.round(currentIntensity * 100));
                intensityTextView.setText(Math.round(currentIntensity * 100) + "%");

                showFilterBadge(filter.getTitle(), filter.getSubtitle());

                if (!filter.isOriginal() && carouselVisible) {
                    sliderContainer.setVisibility(View.VISIBLE);
                    sliderContainer.animate().alpha(1f).setDuration(180).start();
                } else {
                    sliderContainer.animate().alpha(0f).setDuration(180).withEndAction(() -> sliderContainer.setVisibility(View.GONE)).start();
                }

                if (delegate != null) {
                    delegate.onFilterChanged(currentFilterType, currentIntensity);
                }
            }

            @Override
            public void onShutterClick() {
                if (delegate != null) {
                    delegate.onShutterClick();
                }
            }

            @Override
            public boolean onShutterLongClick() {
                if (delegate != null) {
                    return delegate.onShutterLongClick();
                }
                return false;
            }

            @Override
            public void onShutterRelease() {
                if (delegate != null) {
                    delegate.onShutterRelease();
                }
            }
        });
        addView(carouselView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 110, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 0, 0, 0, 8));

        // 4. Snapchat Filter Toggle Button (Floating Sparkle/Lens Button)
        filterToggleButton = new ImageView(context) {
            private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final Paint starPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final Path starPath = new Path();

            {
                bgPaint.setColor(0xA6000000);
                borderPaint.setStyle(Paint.Style.STROKE);
                borderPaint.setStrokeWidth(AndroidUtilities.dp(1.2f));
                borderPaint.setColor(0x40FFFFFF);
                starPaint.setColor(0xFFFFFFFF);
                starPaint.setStyle(Paint.Style.FILL);
            }

            @Override
            protected void onDraw(Canvas canvas) {
                int w = getWidth();
                int h = getHeight();
                float r = w / 2f;
                canvas.drawCircle(r, r, r - AndroidUtilities.dp(1), bgPaint);
                canvas.drawCircle(r, r, r - AndroidUtilities.dp(1), borderPaint);

                if (carouselVisible) {
                    // Draw close / X icon
                    starPaint.setStrokeWidth(AndroidUtilities.dp(2.5f));
                    starPaint.setStyle(Paint.Style.STROKE);
                    float pad = w * 0.32f;
                    canvas.drawLine(pad, pad, w - pad, h - pad, starPaint);
                    canvas.drawLine(w - pad, pad, pad, h - pad, starPaint);
                    starPaint.setStyle(Paint.Style.FILL);
                } else {
                    // Draw sparkle/lens star icon
                    float cx = w / 2f;
                    float cy = h / 2f;
                    float sr = w * 0.27f;

                    starPath.reset();
                    starPath.moveTo(cx, cy - sr);
                    starPath.quadTo(cx, cy, cx + sr, cy);
                    starPath.quadTo(cx, cy, cx, cy + sr);
                    starPath.quadTo(cx, cy, cx - sr, cy);
                    starPath.quadTo(cx, cy, cx, cy - sr);
                    starPath.close();
                    canvas.drawPath(starPath, starPaint);

                    float miniCx = cx + sr * 0.78f;
                    float miniCy = cy - sr * 0.62f;
                    float miniR = sr * 0.44f;
                    starPath.reset();
                    starPath.moveTo(miniCx, miniCy - miniR);
                    starPath.quadTo(miniCx, miniCy, miniCx + miniR, miniCy);
                    starPath.quadTo(miniCx, miniCy, miniCx, miniCy + miniR);
                    starPath.quadTo(miniCx, miniCy, miniCx - miniR, miniCy);
                    starPath.quadTo(miniCx, miniCy, miniCx, miniCy - miniR);
                    starPath.close();
                    canvas.drawPath(starPath, starPaint);
                }
            }
        };

        filterToggleButton.setOnClickListener(v -> toggleCarousel(!carouselVisible, true));
        addView(filterToggleButton, LayoutHelper.createFrame(44, 44, Gravity.NO_GRAVITY));
    }

    private int bottomOffset = 0;

    public void setBottomOffset(int offset) {
        if (bottomOffset != offset) {
            bottomOffset = offset;
            if (carouselView != null && carouselView.getLayoutParams() instanceof FrameLayout.LayoutParams) {
                ((FrameLayout.LayoutParams) carouselView.getLayoutParams()).bottomMargin = AndroidUtilities.dp(8) + bottomOffset;
            }
            if (sliderContainer != null && sliderContainer.getLayoutParams() instanceof FrameLayout.LayoutParams) {
                ((FrameLayout.LayoutParams) sliderContainer.getLayoutParams()).bottomMargin = AndroidUtilities.dp(128) + bottomOffset;
            }
            if (badgeContainer != null && badgeContainer.getLayoutParams() instanceof FrameLayout.LayoutParams) {
                ((FrameLayout.LayoutParams) badgeContainer.getLayoutParams()).bottomMargin = AndroidUtilities.dp(168) + bottomOffset;
            }
            requestLayout();
        }
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);

        int width = right - left;
        int height = bottom - top;
        if (width <= 0 || height <= 0) return;

        int btnW = filterToggleButton.getMeasuredWidth();
        int btnH = filterToggleButton.getMeasuredHeight();

        int effectCenterX;
        int effectCenterY;

        if (carouselVisible) {
            // When carousel is open, place close button beside carousel on bottom-left
            effectCenterX = AndroidUtilities.dp(36);
            effectCenterY = height - bottomOffset - AndroidUtilities.dp(110 / 2 + 8);
        } else {
            // When carousel is closed, position directly UPPER of the Flashlight button!
            if (width == AndroidUtilities.dp(126)) {
                // Landscape camera panel
                int cy = height / 2;
                int flashCenterY = cy / 2 - AndroidUtilities.dp(17);
                int flashCenterX = AndroidUtilities.dp(126 / 2);
                effectCenterX = flashCenterX + AndroidUtilities.dp(58);
                effectCenterY = flashCenterY;
            } else {
                // Standard Portrait: Flashlight is at cx3 = (width / 2) / 2 - 17dp, cy3 = height - bottomOffset - 126 + 50dp
                int cx = width / 2;
                int flashCenterX = cx / 2 - AndroidUtilities.dp(17);
                int flashCenterY = height - bottomOffset - AndroidUtilities.dp(126) + AndroidUtilities.dp(50);
                effectCenterX = flashCenterX;
                effectCenterY = flashCenterY - AndroidUtilities.dp(58); // Directly UPPER of flashlight!
            }
        }

        filterToggleButton.layout(
            effectCenterX - btnW / 2,
            effectCenterY - btnH / 2,
            effectCenterX + btnW / 2,
            effectCenterY + btnH / 2
        );
    }

    public void setDelegate(FilterOverlayDelegate delegate) {
        this.delegate = delegate;
    }

    public void toggleCarousel(boolean show, boolean animated) {
        carouselVisible = show;

        if (delegate != null) {
            delegate.onCarouselVisibilityChanged(show);
        }

        requestLayout();
        filterToggleButton.invalidate();

        if (animated) {
            if (show) {
                carouselView.setVisibility(View.VISIBLE);
                carouselView.setTranslationY(AndroidUtilities.dp(30));
                carouselView.animate().alpha(1f).translationY(0f).setDuration(240).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
                filterToggleButton.animate().scaleX(1.05f).scaleY(1.05f).setDuration(200).start();

                if (currentFilterType != CameraFilterType.ORIGINAL) {
                    sliderContainer.setVisibility(View.VISIBLE);
                    sliderContainer.animate().alpha(1f).setDuration(200).start();
                }
            } else {
                carouselView.animate().alpha(0f).translationY(AndroidUtilities.dp(30)).setDuration(200).setInterpolator(CubicBezierInterpolator.DEFAULT).withEndAction(() -> carouselView.setVisibility(View.GONE)).start();
                filterToggleButton.animate().scaleX(1.0f).scaleY(1.0f).setDuration(200).start();
                sliderContainer.animate().alpha(0f).setDuration(180).withEndAction(() -> sliderContainer.setVisibility(View.GONE)).start();
                badgeContainer.animate().alpha(0f).translationY(AndroidUtilities.dp(6)).setDuration(160).withEndAction(() -> badgeContainer.setVisibility(View.GONE)).start();
            }
        } else {
            carouselView.setVisibility(show ? View.VISIBLE : View.GONE);
            carouselView.setAlpha(show ? 1f : 0f);
            carouselView.setTranslationY(0f);
            sliderContainer.setVisibility(show && currentFilterType != CameraFilterType.ORIGINAL ? View.VISIBLE : View.GONE);
            sliderContainer.setAlpha(show && currentFilterType != CameraFilterType.ORIGINAL ? 1f : 0f);
            if (!show) {
                badgeContainer.setVisibility(View.GONE);
                badgeContainer.setAlpha(0f);
            }
        }
    }

    public boolean isCarouselVisible() {
        return carouselVisible;
    }

    public int getCurrentFilterType() {
        return currentFilterType;
    }

    public float getCurrentIntensity() {
        return currentIntensity;
    }

    public void reset() {
        currentFilterType = CameraFilterType.ORIGINAL;
        currentIntensity = 1.0f;
        carouselView.setSelectedFilter(CameraFilterType.ORIGINAL, false);
        toggleCarousel(false, false);
        if (delegate != null) {
            delegate.onFilterChanged(currentFilterType, currentIntensity);
        }
    }

    private void showFilterBadge(String title, String subtitle) {
        badgeTitleTextView.setText(title);
        badgeSubtitleTextView.setText(subtitle);

        badgeContainer.setVisibility(View.VISIBLE);
        badgeContainer.animate().cancel();
        badgeContainer.setTranslationY(AndroidUtilities.dp(8));
        badgeContainer.animate().alpha(1f).translationY(0f).setDuration(200).setInterpolator(CubicBezierInterpolator.DEFAULT).start();

        if (hideBadgeRunnable != null) {
            handler.removeCallbacks(hideBadgeRunnable);
        }
        hideBadgeRunnable = () -> badgeContainer.animate().alpha(0f).translationY(AndroidUtilities.dp(6)).setDuration(220).withEndAction(() -> badgeContainer.setVisibility(View.GONE)).start();
        handler.postDelayed(hideBadgeRunnable, 1800);
    }
}
