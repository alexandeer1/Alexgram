package tw.nekomimi.nekogram.ui;

// [Alexgram: Official Channel Welcome Dialog] - Start
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.LinearInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.browser.Browser;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.LaunchActivity;

import java.util.ArrayList;
import java.util.Random;

public class AlexgramWelcomeAlert extends Dialog {

    private final Activity activity;
    private final boolean isDark;
    private CosmicAuroraView auroraView;
    private GlassCardView cardView;
    private RadiantGradientButton joinButton;
    private ValueAnimator masterAnimator;
    private float globalProgress = 0f;
    private boolean actionExecuted = false;

    private static final String PREF_PREFIX = "alexgram_welcome_channel_shown_";
    private static final String OFFICIAL_CHANNEL_USERNAME = "AlexgramApp";
    private static final String OFFICIAL_CHANNEL_URL = "https://t.me/AlexgramApp";

    public AlexgramWelcomeAlert(Activity activity) {
        super(activity, android.R.style.Theme_Translucent_NoTitleBar_Fullscreen);
        this.activity = activity;
        this.isDark = Theme.isCurrentThemeDark();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Window window = getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
                window.setStatusBarColor(Color.TRANSPARENT);
                window.setNavigationBarColor(Color.TRANSPARENT);
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !isDark) {
                window.getDecorView().setSystemUiVisibility(
                        window.getDecorView().getSystemUiVisibility() | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                );
            }
        }

        // Root container: deep obsidian in dark mode, clean frosted alabaster in day mode
        FrameLayout rootLayout = new FrameLayout(getContext());
        rootLayout.setBackgroundColor(isDark ? 0xF40A0D14 : 0xEAF0F4F9);
        rootLayout.setOnClickListener(v -> {
            markPromptAsShown();
            dismissWithAnimation();
        });
        setContentView(rootLayout);

        // Animated Atmospheric Thunderstorm, Rain & Burning Fire Backdrop View
        auroraView = new CosmicAuroraView(getContext(), isDark);
        rootLayout.addView(auroraView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        // Glassmorphic Card Container (Adapts seamlessly to Dark & Day modes)
        cardView = new GlassCardView(getContext(), isDark);
        cardView.setOnClickListener(v -> {}); // Consume click inside card
        auroraView.setCardView(cardView);

        int cardWidth = Math.min(AndroidUtilities.displaySize.x - AndroidUtilities.dp(36), AndroidUtilities.dp(390));
        FrameLayout.LayoutParams cardParams = new FrameLayout.LayoutParams(
                cardWidth,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
        );
        rootLayout.addView(cardView, cardParams);

        // ScrollView wrapper to adapt dynamically on all display heights
        ScrollView scrollView = new ScrollView(getContext()) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                int maxH = AndroidUtilities.displaySize.y - AndroidUtilities.dp(72);
                super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(Math.max(AndroidUtilities.dp(200), maxH), MeasureSpec.AT_MOST));
            }
        };
        scrollView.setVerticalScrollBarEnabled(false);
        scrollView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        cardView.addView(scrollView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        // Inner vertical content layout
        LinearLayout contentLayout = new LinearLayout(getContext());
        contentLayout.setOrientation(LinearLayout.VERTICAL);
        contentLayout.setGravity(Gravity.CENTER_HORIZONTAL);
        contentLayout.setPadding(AndroidUtilities.dp(22), AndroidUtilities.dp(20), AndroidUtilities.dp(22), AndroidUtilities.dp(20));
        scrollView.addView(contentLayout, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        // Top-right Close Button
        FrameLayout topBar = new FrameLayout(getContext());
        ImageView closeButton = new ImageView(getContext());
        closeButton.setImageResource(R.drawable.baseline_close_24);
        closeButton.setColorFilter(new PorterDuffColorFilter(isDark ? 0x80FFFFFF : 0x8A000000, PorterDuff.Mode.SRC_IN));
        closeButton.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        closeButton.setBackground(Theme.createSelectorDrawable(isDark ? 0x20FFFFFF : 0x18000000, 1));
        closeButton.setOnClickListener(v -> dismissWithAnimation());
        topBar.addView(closeButton, LayoutHelper.createFrame(30, 30, Gravity.RIGHT | Gravity.CENTER_VERTICAL));
        contentLayout.addView(topBar, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 30, 0, 0, 0, 2));

        // Hero Emblem View (Official Alexgram white logo with breathing halo & verified badge)
        AlexgramHeroEmblemView emblemView = new AlexgramHeroEmblemView(getContext(), isDark);
        contentLayout.addView(emblemView, LayoutHelper.createLinear(92, 92, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 14));

        // Official Community Capsule Pill
        FrameLayout badgePill = createOfficialBadgePill();
        contentLayout.addView(badgePill, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 26, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 10));

        // Title: Welcome to Alexgram
        TextView titleView = new TextView(getContext());
        titleView.setText(LocaleController.getString("AlexgramWelcomeTitle", R.string.AlexgramWelcomeTitle));
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 22);
        titleView.setTextColor(isDark ? Color.WHITE : 0xFF0F172A);
        titleView.setTypeface(AndroidUtilities.bold());
        titleView.setGravity(Gravity.CENTER);
        titleView.setShadowLayer(
                isDark ? AndroidUtilities.dp(6) : AndroidUtilities.dp(2),
                0,
                AndroidUtilities.dp(1.5f),
                isDark ? 0x60000000 : 0x14000000
        );
        contentLayout.addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 4));

        // Subtitle: The Next-Generation Messaging Experience
        TextView subtitleView = new TextView(getContext());
        subtitleView.setText(LocaleController.getString("AlexgramWelcomeSubtitle", R.string.AlexgramWelcomeSubtitle));
        subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        subtitleView.setTextColor(isDark ? 0xFF4FA8F6 : 0xFF0284C7);
        subtitleView.setTypeface(AndroidUtilities.bold());
        subtitleView.setGravity(Gravity.CENTER);
        subtitleView.setLetterSpacing(0.02f);
        contentLayout.addView(subtitleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 8));

        // Body Description (Punchy, concise single-sentence tagline)
        TextView descView = new TextView(getContext());
        descView.setText(LocaleController.getString("AlexgramWelcomeDescription", R.string.AlexgramWelcomeDescription));
        descView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13f);
        descView.setTextColor(isDark ? 0xFF94A3B8 : 0xFF475569);
        descView.setGravity(Gravity.CENTER);
        descView.setLineSpacing(AndroidUtilities.dp(3.0f), 1.0f);
        contentLayout.addView(descView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 8, 0, 8, 16));

        // 3 Feature Showcase Cards with authentic vector icons (Adapts to Dark & Day)
        LinearLayout featuresLayout = new LinearLayout(getContext());
        featuresLayout.setOrientation(LinearLayout.VERTICAL);

        featuresLayout.addView(createFeatureRow(
                R.drawable.baseline_flash_on_24, 0xFF38BDF8,
                LocaleController.getString("AlexgramWelcomeFeature1Title", R.string.AlexgramWelcomeFeature1Title),
                LocaleController.getString("AlexgramWelcomeFeature1Desc", R.string.AlexgramWelcomeFeature1Desc)
        ), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 8));

        featuresLayout.addView(createFeatureRow(
                R.drawable.ayu_ghost_solar, 0xFF818CF8,
                LocaleController.getString("AlexgramWelcomeFeature2Title", R.string.AlexgramWelcomeFeature2Title),
                LocaleController.getString("AlexgramWelcomeFeature2Desc", R.string.AlexgramWelcomeFeature2Desc)
        ), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 8));

        featuresLayout.addView(createFeatureRow(
                R.drawable.msg_channel_solar, 0xFF34D399,
                LocaleController.getString("AlexgramWelcomeFeature3Title", R.string.AlexgramWelcomeFeature3Title),
                LocaleController.getString("AlexgramWelcomeFeature3Desc", R.string.AlexgramWelcomeFeature3Desc)
        ), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 16));

        contentLayout.addView(featuresLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        // Primary Action: Join/Open Official Channel CTA Button (Signature Telegram Blue Gradient)
        boolean alreadyJoined = isAlreadyJoined();
        String initialButtonText = alreadyJoined
                ? LocaleController.getString("AlexgramWelcomeOpenButton", R.string.AlexgramWelcomeOpenButton)
                : LocaleController.getString("AlexgramWelcomeJoinButton", R.string.AlexgramWelcomeJoinButton);
        joinButton = new RadiantGradientButton(getContext(), initialButtonText);
        joinButton.setOnClickListener(v -> onJoinClicked());
        contentLayout.addView(joinButton, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 50, Gravity.CENTER_HORIZONTAL, 0, 4, 0, 0));

        // If not cached, resolve username asynchronously to adapt button text if already joined
        if (!alreadyJoined) {
            MessagesController.getInstance(UserConfig.selectedAccount).getUserNameResolver().resolve(OFFICIAL_CHANNEL_USERNAME, (peerId) -> {
                if (peerId != null && peerId < 0) {
                    TLRPC.Chat chat = MessagesController.getInstance(UserConfig.selectedAccount).getChat(-peerId);
                    if (chat != null && !ChatObject.isNotInChat(chat)) {
                        AndroidUtilities.runOnUIThread(() -> {
                            if (joinButton != null && !actionExecuted) {
                                joinButton.updateText(LocaleController.getString("AlexgramWelcomeOpenButton", R.string.AlexgramWelcomeOpenButton));
                            }
                        });
                    }
                }
            });
        }

        // Secondary Action: Explore Alexgram (Continue)
        TextView continueBtn = new TextView(getContext());
        continueBtn.setText(LocaleController.getString("AlexgramWelcomeContinueButton", R.string.AlexgramWelcomeContinueButton));
        continueBtn.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13.5f);
        continueBtn.setTextColor(isDark ? 0xFF94A3B8 : 0xFF64748B);
        continueBtn.setGravity(Gravity.CENTER);
        continueBtn.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(10), AndroidUtilities.dp(16), AndroidUtilities.dp(10));
        continueBtn.setBackground(Theme.createSelectorDrawable(isDark ? 0x18FFFFFF : 0x18000000, 2));
        continueBtn.setOnClickListener(v -> {
            markPromptAsShown();
            dismissWithAnimation();
        });
        contentLayout.addView(continueBtn, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 8, 0, 0));

        // Entrance animation
        cardView.setAlpha(0f);
        cardView.setScaleX(0.90f);
        cardView.setScaleY(0.90f);
        cardView.setTranslationY(AndroidUtilities.dp(20));
        cardView.animate()
                .alpha(1.0f)
                .scaleX(1.0f)
                .scaleY(1.0f)
                .translationY(0)
                .setDuration(400)
                .setInterpolator(new OvershootInterpolator(1.1f))
                .start();

        markPromptAsShown();
        startMasterLoop();
    }

    private FrameLayout createOfficialBadgePill() {
        FrameLayout pill = new FrameLayout(getContext()) {
            private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

            {
                bgPaint.setColor(isDark ? 0x1A38BDF8 : 0x150284C7);
                borderPaint.setStyle(Paint.Style.STROKE);
                borderPaint.setStrokeWidth(AndroidUtilities.dp(0.75f));
                borderPaint.setColor(isDark ? 0x3338BDF8 : 0x350284C7);
            }

            @Override
            protected void onDraw(Canvas canvas) {
                float r = getHeight() / 2f;
                RectF rf = new RectF(1, 1, getWidth() - 1, getHeight() - 1);
                canvas.drawRoundRect(rf, r, r, bgPaint);
                canvas.drawRoundRect(rf, r, r, borderPaint);
                super.onDraw(canvas);
            }
        };
        pill.setWillNotDraw(false);
        pill.setPadding(AndroidUtilities.dp(14), 0, AndroidUtilities.dp(14), 0);

        TextView text = new TextView(getContext());
        text.setText(LocaleController.getString("AlexgramWelcomeBadge", R.string.AlexgramWelcomeBadge));
        text.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 10);
        text.setTextColor(isDark ? 0xFF38BDF8 : 0xFF0284C7);
        text.setTypeface(AndroidUtilities.bold());
        text.setLetterSpacing(0.08f);
        text.setGravity(Gravity.CENTER);

        pill.addView(text, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));
        return pill;
    }

    private View createFeatureRow(int iconResId, int accentColor, String title, String desc) {
        LinearLayout row = new LinearLayout(getContext()) {
            private final Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final Paint border = new Paint(Paint.ANTI_ALIAS_FLAG);
            {
                bg.setColor(isDark ? 0x26171F2E : 0x0A0F172A);
                border.setStyle(Paint.Style.STROKE);
                border.setStrokeWidth(AndroidUtilities.dp(0.75f));
                border.setColor(isDark ? 0x18FFFFFF : 0x12000000);
            }

            @Override
            protected void onDraw(Canvas canvas) {
                float r = AndroidUtilities.dp(14);
                RectF rf = new RectF(1, 1, getWidth() - 1, getHeight() - 1);
                canvas.drawRoundRect(rf, r, r, bg);
                canvas.drawRoundRect(rf, r, r, border);
                super.onDraw(canvas);
            }
        };
        row.setWillNotDraw(false);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(11), AndroidUtilities.dp(12), AndroidUtilities.dp(11));

        // Refined vector icon capsule
        FrameLayout iconFrame = new FrameLayout(getContext()) {
            private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final Paint b = new Paint(Paint.ANTI_ALIAS_FLAG);
            {
                p.setColor((accentColor & 0x00FFFFFF) | (isDark ? 0x20000000 : 0x18000000));
                b.setStyle(Paint.Style.STROKE);
                b.setStrokeWidth(AndroidUtilities.dp(0.75f));
                b.setColor((accentColor & 0x00FFFFFF) | (isDark ? 0x40000000 : 0x30000000));
            }

            @Override
            protected void onDraw(Canvas canvas) {
                float r = AndroidUtilities.dp(11);
                RectF rf = new RectF(1, 1, getWidth() - 1, getHeight() - 1);
                canvas.drawRoundRect(rf, r, r, p);
                canvas.drawRoundRect(rf, r, r, b);
                super.onDraw(canvas);
            }
        };
        iconFrame.setWillNotDraw(false);

        ImageView iconView = new ImageView(getContext());
        iconView.setImageResource(iconResId);
        iconView.setColorFilter(new PorterDuffColorFilter(accentColor, PorterDuff.Mode.SRC_IN));
        iconView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        iconFrame.addView(iconView, LayoutHelper.createFrame(20, 20, Gravity.CENTER));

        row.addView(iconFrame, LayoutHelper.createLinear(40, 40, Gravity.CENTER_VERTICAL, 0, 0, 12, 0));

        // Text container
        LinearLayout textLayout = new LinearLayout(getContext());
        textLayout.setOrientation(LinearLayout.VERTICAL);

        TextView rowTitle = new TextView(getContext());
        rowTitle.setText(title);
        rowTitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13.5f);
        rowTitle.setTextColor(isDark ? Color.WHITE : 0xFF0F172A);
        rowTitle.setTypeface(AndroidUtilities.bold());
        textLayout.addView(rowTitle, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        TextView rowDesc = new TextView(getContext());
        rowDesc.setText(desc);
        rowDesc.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11f);
        rowDesc.setTextColor(isDark ? 0xFF94A3B8 : 0xFF64748B);
        rowDesc.setLineSpacing(AndroidUtilities.dp(1.5f), 1.0f);
        textLayout.addView(rowDesc, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));

        row.addView(textLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL));
        return row;
    }

    private void onJoinClicked() {
        if (actionExecuted) return;
        actionExecuted = true;

        markPromptAsShown();

        // Celebratory burst from join button coordinates
        if (auroraView != null && joinButton != null) {
            int[] loc = new int[2];
            joinButton.getLocationOnScreen(loc);
            float cx = loc[0] + joinButton.getWidth() / 2f;
            float cy = loc[1] + joinButton.getHeight() / 2f;
            auroraView.triggerBurst(cx, cy, 110);
        }

        // Tactile celebration haptic pattern
        playCelebrationHaptic();

        // Update button appearance
        if (joinButton != null) {
            joinButton.updateText("✓ " + LocaleController.getString(R.string.Done));
            joinButton.setEnabled(false);
        }

        int currentAccount = UserConfig.selectedAccount;
        MessagesController messagesController = MessagesController.getInstance(currentAccount);
        TLRPC.User currentUser = UserConfig.getInstance(currentAccount).getCurrentUser();

        // If not already joined, automatically join the official channel on server
        boolean alreadyJoined = isAlreadyJoined();
        if (!alreadyJoined) {
            TLObject cachedObj = messagesController.getUserOrChat(OFFICIAL_CHANNEL_USERNAME);
            if (cachedObj instanceof TLRPC.Chat) {
                messagesController.addUserToChat(((TLRPC.Chat) cachedObj).id, currentUser, 0, null, null, null);
            } else {
                messagesController.getUserNameResolver().resolve(OFFICIAL_CHANNEL_USERNAME, (peerId) -> {
                    if (peerId != null && peerId < 0) {
                        TLRPC.Chat chat = messagesController.getChat(-peerId);
                        if (chat == null || ChatObject.isNotInChat(chat)) {
                            messagesController.addUserToChat(-peerId, currentUser, 0, null, null, null);
                        }
                    }
                });
            }
        }

        // Navigate to the official channel
        AndroidUtilities.runOnUIThread(() -> {
            BaseFragment lastFragment = null;
            if (activity instanceof LaunchActivity) {
                LaunchActivity launchActivity = (LaunchActivity) activity;
                if (launchActivity.getActionBarLayout() != null) {
                    lastFragment = launchActivity.getActionBarLayout().getLastFragment();
                }
            }

            if (lastFragment != null) {
                messagesController.openByUserName(OFFICIAL_CHANNEL_USERNAME, lastFragment, 1);
            } else {
                Browser.openUrl(activity, OFFICIAL_CHANNEL_URL);
            }

            // Smooth exit animation
            dismissWithAnimation();
        }, 950);
    }

    private void dismissWithAnimation() {
        markPromptAsShown();
        if (cardView != null) {
            cardView.animate()
                    .alpha(0f)
                    .scaleX(0.88f)
                    .scaleY(0.88f)
                    .translationY(AndroidUtilities.dp(16))
                    .setDuration(240)
                    .withEndAction(this::dismiss)
                    .start();
        } else {
            dismiss();
        }
    }

    private void markPromptAsShown() {
        int currentAccount = UserConfig.selectedAccount;
        long userId = UserConfig.getInstance(currentAccount).getClientUserId();
        var editor = MessagesController.getGlobalMainSettings().edit();
        if (userId != 0) {
            editor.putBoolean(PREF_PREFIX + userId, true);
        }
        editor.putBoolean(PREF_PREFIX + "installed", true);
        editor.apply();
    }

    private void playCelebrationHaptic() {
        try {
            Vibrator vibrator = (Vibrator) getContext().getSystemService(Context.VIBRATOR_SERVICE);
            if (vibrator != null && vibrator.hasVibrator()) {
                long[] pattern = new long[]{0, 45, 35, 60, 30, 90};
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1));
                } else {
                    vibrator.vibrate(pattern, -1);
                }
            }
        } catch (Throwable ignored) {}
    }

    private void startMasterLoop() {
        masterAnimator = ValueAnimator.ofFloat(0f, 1f);
        masterAnimator.setDuration(3500);
        masterAnimator.setRepeatMode(ValueAnimator.RESTART);
        masterAnimator.setRepeatCount(ValueAnimator.INFINITE);
        masterAnimator.setInterpolator(new LinearInterpolator());
        masterAnimator.addUpdateListener(anim -> {
            globalProgress = (float) anim.getAnimatedValue();
            if (joinButton != null) joinButton.updateProgress(globalProgress);
            if (auroraView != null) auroraView.invalidate();
        });
        masterAnimator.start();
    }

    @Override
    public void dismiss() {
        if (masterAnimator != null) {
            masterAnimator.cancel();
            masterAnimator = null;
        }
        super.dismiss();
    }

    // ─────────────────────────────────────────────────────────────
    // ONE-TIME FIRST-LAUNCH TRIGGER LOGIC
    // ─────────────────────────────────────────────────────────────

    private boolean isAlreadyJoined() {
        int currentAccount = UserConfig.selectedAccount;
        MessagesController mc = MessagesController.getInstance(currentAccount);
        TLObject obj = mc.getUserOrChat(OFFICIAL_CHANNEL_USERNAME);
        if (obj instanceof TLRPC.Chat) {
            return !ChatObject.isNotInChat((TLRPC.Chat) obj);
        }
        return false;
    }

    public static boolean shouldShow() {
        int currentAccount = UserConfig.selectedAccount;
        if (!UserConfig.getInstance(currentAccount).isClientActivated()) {
            return false;
        }
        long userId = UserConfig.getInstance(currentAccount).getClientUserId();
        if (userId == 0) {
            return false;
        }
        if (MessagesController.getGlobalMainSettings().getBoolean(PREF_PREFIX + "installed", false)) {
            return false;
        }
        return !MessagesController.getGlobalMainSettings().getBoolean(PREF_PREFIX + userId, false);
    }

    public static void checkAndShow(Activity activity, long delayMs) {
        if (activity == null || activity.isFinishing()) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && activity.isDestroyed()) return;

        if (!shouldShow()) {
            return;
        }

        AndroidUtilities.runOnUIThread(() -> {
            if (activity == null || activity.isFinishing()) return;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && activity.isDestroyed()) return;
            if (!shouldShow()) return;

            try {
                AlexgramWelcomeAlert alert = new AlexgramWelcomeAlert(activity);
                alert.show();
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }, delayMs);
    }

    public static void show(Activity activity) {
        if (activity == null || activity.isFinishing()) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && activity.isDestroyed()) return;
        try {
            AlexgramWelcomeAlert alert = new AlexgramWelcomeAlert(activity);
            alert.show();
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    // ─────────────────────────────────────────────────────────────
    // REFINED CUSTOM VISUAL COMPONENTS
    // ─────────────────────────────────────────────────────────────

    /**
     * Glassmorphic card frame reflecting storm lightning on the top rim
     * and warm burning fire ambient amber on the bottom rim.
     * Supports both Dark & Day themes seamlessly.
     */
    private static class GlassCardView extends FrameLayout {
        private final boolean isDark;
        private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint innerSpecularPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private float lightningIntensity = 0f;

        public GlassCardView(Context context, boolean isDark) {
            super(context);
            this.isDark = isDark;
            setWillNotDraw(false);

            // Dark: deep acrylic obsidian glass; Day: pure pearl glass
            bgPaint.setColor(isDark ? 0xF5131824 : 0xF7FFFFFF);

            borderPaint.setStyle(Paint.Style.STROKE);
            borderPaint.setStrokeWidth(AndroidUtilities.dp(1.2f));

            innerSpecularPaint.setStyle(Paint.Style.STROKE);
            innerSpecularPaint.setStrokeWidth(AndroidUtilities.dp(0.75f));
            innerSpecularPaint.setColor(isDark ? 0x15FFFFFF : 0x10000000);
        }

        public void setLightningIntensity(float intensity) {
            this.lightningIntensity = intensity;
            updateBorderShader();
            invalidate();
        }

        @Override
        protected void onSizeChanged(int w, int h, int ow, int oh) {
            rect.set(AndroidUtilities.dp(1f), AndroidUtilities.dp(1f),
                    w - AndroidUtilities.dp(1f), h - AndroidUtilities.dp(1f));
            updateBorderShader();
        }

        private void updateBorderShader() {
            int h = getHeight();
            if (h <= 0) return;

            // Directional border shader:
            // Top rim: catches storm lightning flashes (crisp white/ice-blue in dark, sapphire in day)
            // Mid rim: subtle azure glass reflection
            // Bottom rim: catches warm burning fire amber reflection from below!
            int topAlpha = Math.min(255, (int) (0x35 + lightningIntensity * (isDark ? 0x65 : 0x75)));
            int topColor = isDark
                    ? Color.argb(topAlpha, 230, 245, 255)
                    : Color.argb(topAlpha, 37, 99, 235);
            int midColor = isDark ? 0x1438BDF8 : 0x180284C7;
            int botColor = isDark ? 0x42FF6600 : 0x4DFF5500; // Warm amber flame reflection

            borderPaint.setShader(new LinearGradient(
                    0, 0, 0, h,
                    new int[]{topColor, midColor, botColor},
                    new float[]{0f, 0.45f, 1f},
                    Shader.TileMode.CLAMP
            ));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            int w = getWidth();
            int h = getHeight();
            if (w == 0 || h == 0) return;

            float r = AndroidUtilities.dp(24);

            // Card background
            canvas.drawRoundRect(rect, r, r, bgPaint);

            // Subtle inner specular rim
            canvas.drawRoundRect(rect, r, r, innerSpecularPaint);

            // Directional specular border reflecting storm above and fire below
            canvas.drawRoundRect(rect, r, r, borderPaint);

            super.onDraw(canvas);
        }
    }

    /**
     * Hero Emblem displaying the official Alexgram white logo clipped cleanly into a circle,
     * surrounded by a soft breathing ice-blue halo and the official Telegram verified checkmark badge.
     */
    private static class AlexgramHeroEmblemView extends View {
        private final boolean isDark;
        private final Paint haloPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint baseBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint rimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint verifiedBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint verifiedBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint checkPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        private final Path clipPath = new Path();
        private final Path checkPath = new Path();
        private Drawable logoDrawable;

        public AlexgramHeroEmblemView(Context context, boolean isDark) {
            super(context);
            this.isDark = isDark;
            setWillNotDraw(false);

            try {
                logoDrawable = ContextCompat.getDrawable(context, R.drawable.ic_launcher_alexgram_white);
            } catch (Throwable t) {
                try {
                    logoDrawable = ContextCompat.getDrawable(context, R.drawable.ic_launcher_alexgram_colored);
                } catch (Throwable ignored) {}
            }

            haloPaint.setStyle(Paint.Style.STROKE);

            baseBgPaint.setColor(isDark ? 0xFF131824 : 0xFFF1F5F9);
            baseBgPaint.setStyle(Paint.Style.FILL);

            rimPaint.setStyle(Paint.Style.STROKE);
            rimPaint.setStrokeWidth(AndroidUtilities.dp(1.2f));
            rimPaint.setColor(isDark ? 0x28FFFFFF : 0x20000000);

            verifiedBgPaint.setColor(0xFF2AABEE); // Telegram verified blue
            verifiedBgPaint.setStyle(Paint.Style.FILL);

            verifiedBorderPaint.setColor(isDark ? 0xFF131824 : 0xFFFFFFFF);
            verifiedBorderPaint.setStyle(Paint.Style.FILL);

            checkPaint.setColor(Color.WHITE);
            checkPaint.setStyle(Paint.Style.STROKE);
            checkPaint.setStrokeWidth(AndroidUtilities.dp(2));
            checkPaint.setStrokeCap(Paint.Cap.ROUND);
            checkPaint.setStrokeJoin(Paint.Join.ROUND);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            int w = getWidth();
            int h = getHeight();
            if (w == 0 || h == 0) return;

            float cx = w / 2f;
            float cy = h / 2f;
            float baseR = AndroidUtilities.dp(35);

            float time = (System.currentTimeMillis() % 3600) / 3600f;

            // 1. Gentle breathing halo ring
            float pulse = 0.5f + 0.5f * (float) Math.sin(time * Math.PI * 2);
            haloPaint.setColor(isDark ? 0x2238BDF8 : 0x250284C7);
            haloPaint.setStrokeWidth(AndroidUtilities.dp(1.5f));
            canvas.drawCircle(cx, cy, baseR + AndroidUtilities.dp(5) + pulse * AndroidUtilities.dp(3), haloPaint);

            // 2. Base circular backplate
            canvas.drawCircle(cx, cy, baseR, baseBgPaint);

            // 3. Official Alexgram white logo clipped inside circle
            if (logoDrawable != null) {
                canvas.save();
                clipPath.rewind();
                clipPath.addCircle(cx, cy, baseR, Path.Direction.CW);
                canvas.clipPath(clipPath);
                logoDrawable.setBounds((int)(cx - baseR), (int)(cy - baseR), (int)(cx + baseR), (int)(cy + baseR));
                logoDrawable.draw(canvas);
                canvas.restore();
            }

            // 4. Subtle inner specular rim
            canvas.drawCircle(cx, cy, baseR, rimPaint);

            // 5. Official Verified checkmark badge pinned on bottom right
            float vDist = baseR * 0.88f;
            float vx = cx + (float) Math.cos(Math.PI / 4) * vDist;
            float vy = cy + (float) Math.sin(Math.PI / 4) * vDist;
            float vr = AndroidUtilities.dp(10.5f);

            // Separation ring
            canvas.drawCircle(vx, vy, vr + AndroidUtilities.dp(2f), verifiedBorderPaint);

            // Verified blue circle
            canvas.drawCircle(vx, vy, vr, verifiedBgPaint);

            // Crisp checkmark path
            checkPath.rewind();
            checkPath.moveTo(vx - vr * 0.42f, vy);
            checkPath.lineTo(vx - vr * 0.10f, vy + vr * 0.35f);
            checkPath.lineTo(vx + vr * 0.44f, vy - vr * 0.30f);
            canvas.drawPath(checkPath, checkPaint);

            invalidate();
        }
    }

    /**
     * Primary CTA button with official Telegram Blue gradient, continuous animated
     * sweeping shimmer glint, and tactile press animation.
     */
    private static class RadiantGradientButton extends FrameLayout {
        private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint txtPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint shimmerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF btnRect = new RectF();
        private final Path btnPath = new Path();

        private String text;
        private float pressScale = 1.0f;
        private float shimmerProgress = 0f;
        private ValueAnimator pressAnim;

        private static final int[] TELEGRAM_BLUE_GRADIENT = new int[]{
                0xFF2481CC, // Telegram Deep Blue
                0xFF2AABEE  // Telegram Sky/Azure Blue
        };

        public RadiantGradientButton(Context context, String text) {
            super(context);
            this.text = text;
            setWillNotDraw(false);
            setClickable(true);

            txtPaint.setTextAlign(Paint.Align.CENTER);
            txtPaint.setColor(Color.WHITE);
            txtPaint.setTypeface(AndroidUtilities.bold());
            txtPaint.setTextSize(AndroidUtilities.dp(15));
            txtPaint.setShadowLayer(AndroidUtilities.dp(3), 0, AndroidUtilities.dp(1), 0x30000000);

            borderPaint.setStyle(Paint.Style.STROKE);
            borderPaint.setStrokeWidth(AndroidUtilities.dp(1));
            borderPaint.setColor(0x35FFFFFF);
        }

        public void updateProgress(float progress) {
            this.shimmerProgress = progress;
            invalidate();
        }

        public void updateText(String newText) {
            this.text = newText;
            invalidate();
        }

        @Override
        protected void onSizeChanged(int w, int h, int ow, int oh) {
            btnRect.set(AndroidUtilities.dp(1), AndroidUtilities.dp(1),
                    w - AndroidUtilities.dp(1), h - AndroidUtilities.dp(1));
            bgPaint.setShader(new LinearGradient(0, 0, w, 0, TELEGRAM_BLUE_GRADIENT, null, Shader.TileMode.CLAMP));

            btnPath.reset();
            btnPath.addRoundRect(btnRect, h / 2f, h / 2f, Path.Direction.CW);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            int w = getWidth();
            int h = getHeight();
            if (w == 0 || h == 0) return;

            canvas.save();
            canvas.scale(pressScale, pressScale, w / 2f, h / 2f);

            float r = h / 2f;
            canvas.drawRoundRect(btnRect, r, r, bgPaint);
            canvas.drawRoundRect(btnRect, r, r, borderPaint);

            // Sweeping light glint reflection
            canvas.save();
            canvas.clipPath(btnPath);
            float sweepX = -w * 0.4f + (w * 2.0f) * shimmerProgress;
            float sweepW = AndroidUtilities.dp(36);
            shimmerPaint.setShader(new LinearGradient(
                    sweepX - sweepW, 0, sweepX + sweepW, 0,
                    new int[]{0x00FFFFFF, 0x38FFFFFF, 0x00FFFFFF},
                    null, Shader.TileMode.CLAMP
            ));
            canvas.drawRect(0, 0, w, h, shimmerPaint);
            canvas.restore();

            canvas.drawText(text, w / 2f, h / 2f + AndroidUtilities.dp(5f), txtPaint);

            canvas.restore();
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (!isEnabled()) return false;
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    animatePress(0.95f);
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    animatePress(1.0f);
                    break;
            }
            return super.onTouchEvent(event);
        }

        private void animatePress(float to) {
            if (pressAnim != null) pressAnim.cancel();
            pressAnim = ValueAnimator.ofFloat(pressScale, to);
            pressAnim.setDuration(110);
            pressAnim.addUpdateListener(a -> {
                pressScale = (float) a.getAnimatedValue();
                invalidate();
            });
            pressAnim.start();
        }
    }

    /**
     * Atmospheric effects canvas:
     * - Upper screen: Thunderstorm with jagged lightning bolts and delicate angled rain.
     * - Lower screen: Burning fire animation with dynamic flame tongues, waves, and rising embers.
     * - Evaporating raindrops as they approach the fire heat zone.
     * Supports both Dark & Day themes.
     */
    private static class CosmicAuroraView extends View {
        private final boolean isDark;
        private GlassCardView cardView;

        // Rain simulation (Upper & Middle Screen)
        private final ArrayList<RainDrop> rainDrops = new ArrayList<>();
        private final Paint rainPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        // Thunderstorm / Lightning simulation (Upper Screen)
        private final Path lightningPath = new Path();
        private final Path lightningBranchPath = new Path();
        private final Paint lightningCorePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint lightningGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint skyFlashPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        private boolean lightningActive = false;
        private long lightningStartTime = 0;
        private long lastLightningTime = 0;
        private long nextLightningDelay = 3500;
        private float currentLightningIntensity = 0f;

        // Atmospheric Fire Simulation (Bottom Screen)
        private final ArrayList<FlameTongue> flameTongues = new ArrayList<>();
        private final ArrayList<FireEmber> fireEmbers = new ArrayList<>();
        private final Paint fireBaseGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint fireSpirePaint1 = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint fireSpirePaint2 = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint fireSpirePaint3 = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint flameTonguePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path fireSpirePath1 = new Path();
        private final Path fireSpirePath2 = new Path();
        private final Path fireSpirePath3 = new Path();
        private final Path flameTonguePath = new Path();

        // Storm Clouds (Upper Atmosphere: Dual-Layer Cumulonimbus + Scud Mist)
        private final ArrayList<StormCloud> stormCloudsBack = new ArrayList<>();
        private final ArrayList<StormCloud> stormCloudsFront = new ArrayList<>();
        private final ArrayList<ScudMist> scudMists = new ArrayList<>();
        private final Paint stormCloudPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint stormCloudHighlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint stormCloudFlashPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint scudMistPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint stormCeilingPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path stormCeilingPath = new Path();

        // Billowing Smoke Simulation (Rising from flame tips)
        private final ArrayList<SmokePuff> smokePuffs = new ArrayList<>();
        private final Paint smokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        // Evaporating Steam Wisps (Rain meeting fire heat)
        private final ArrayList<SteamWisp> steamWisps = new ArrayList<>();
        private final Paint steamPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        // Celebratory bursts upon CTA button click
        private final ArrayList<Particle> burstParticles = new ArrayList<>();
        private final Paint burstPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        private final Random random = new Random();

        public CosmicAuroraView(Context context, boolean isDark) {
            super(context);
            this.isDark = isDark;
            setWillNotDraw(false);

            // Rain styling: ice-blue in dark mode, sapphire in day mode
            rainPaint.setStyle(Paint.Style.STROKE);
            rainPaint.setStrokeCap(Paint.Cap.ROUND);
            rainPaint.setColor(isDark ? 0x4090C8FF : 0x553B82F6);

            // Lightning styling
            lightningCorePaint.setStyle(Paint.Style.STROKE);
            lightningCorePaint.setStrokeWidth(AndroidUtilities.dp(1.8f));
            lightningCorePaint.setColor(Color.WHITE);
            lightningCorePaint.setStrokeCap(Paint.Cap.ROUND);
            lightningCorePaint.setStrokeJoin(Paint.Join.ROUND);

            lightningGlowPaint.setStyle(Paint.Style.STROKE);
            lightningGlowPaint.setStrokeWidth(AndroidUtilities.dp(5.2f));
            lightningGlowPaint.setColor(isDark ? 0x5570B0FF : 0x600284C7);
            lightningGlowPaint.setStrokeCap(Paint.Cap.ROUND);
            lightningGlowPaint.setStrokeJoin(Paint.Join.ROUND);

            skyFlashPaint.setStyle(Paint.Style.FILL);

            // Fire styling
            fireSpirePaint1.setStyle(Paint.Style.FILL);
            fireSpirePaint2.setStyle(Paint.Style.FILL);
            fireSpirePaint3.setStyle(Paint.Style.FILL);
            flameTonguePaint.setStyle(Paint.Style.FILL);

            // Atmosphere styling
            stormCloudPaint.setStyle(Paint.Style.FILL);
            stormCloudHighlightPaint.setStyle(Paint.Style.FILL);
            stormCloudFlashPaint.setStyle(Paint.Style.FILL);
            scudMistPaint.setStyle(Paint.Style.FILL);
            stormCeilingPaint.setStyle(Paint.Style.FILL);
            smokePaint.setStyle(Paint.Style.FILL);
            steamPaint.setStyle(Paint.Style.FILL);
        }

        public void setCardView(GlassCardView cardView) {
            this.cardView = cardView;
        }

        public void triggerBurst(float cx, float cy, int count) {
            for (int i = 0; i < count; i++) {
                burstParticles.add(new Particle(cx, cy, true));
            }
            invalidate();
        }

        @Override
        protected void onSizeChanged(int w, int h, int ow, int oh) {
            super.onSizeChanged(w, h, ow, oh);
            if (w == 0 || h == 0) return;

            // 1. Initialize Rain drops
            rainDrops.clear();
            for (int i = 0; i < 75; i++) {
                rainDrops.add(new RainDrop(w, h, random, true));
            }

            // 2. Initialize Rising Flame Tongues along bottom
            flameTongues.clear();
            for (int i = 0; i < 50; i++) {
                flameTongues.add(new FlameTongue(w, h, random, true));
            }

            // 3. Initialize Airborne Spark Embers
            fireEmbers.clear();
            for (int i = 0; i < 48; i++) {
                fireEmbers.add(new FireEmber(w, h, random, true));
            }

            // 4. Atmospheric Overcast Ceiling Shader (Anchor along top)
            float ceilingH = AndroidUtilities.dp(140);
            stormCeilingPaint.setShader(new LinearGradient(
                    0, 0, 0, ceilingH,
                    new int[]{
                            isDark ? 0x950B132B : 0x55475569,
                            isDark ? 0x600F172A : 0x3064748B,
                            isDark ? 0x200F172A : 0x1094A3B8,
                            isDark ? 0x000F172A : 0x0094A3B8
                    },
                    new float[]{0f, 0.40f, 0.75f, 1f},
                    Shader.TileMode.CLAMP
            ));

            // Feathered Volumetric Cloud Body Shader (Multi-stop Gaussian falloff with zero-alpha perimeter clamping)
            stormCloudPaint.setShader(new RadialGradient(
                    0, 0, 100,
                    new int[]{
                            isDark ? 0x950E172A : 0x75475569,
                            isDark ? 0x70142036 : 0x5464748B,
                            isDark ? 0x361E2A3E : 0x2A8294A8,
                            isDark ? 0x0A1E2A3E : 0x088294A8,
                            isDark ? 0x001E2A3E : 0x008294A8
                    },
                    new float[]{0f, 0.35f, 0.65f, 0.85f, 1.0f},
                    Shader.TileMode.CLAMP
            ));

            // Subtle Cloud Top Highlight Shader (Ambient atmospheric rim illumination)
            stormCloudHighlightPaint.setShader(new RadialGradient(
                    0, 0, 100,
                    new int[]{
                            isDark ? 0x42475569 : 0x4594A3B8,
                            isDark ? 0x18475569 : 0x1894A3B8,
                            isDark ? 0x00475569 : 0x0094A3B8
                    },
                    new float[]{0f, 0.50f, 1.0f},
                    Shader.TileMode.CLAMP
            ));

            // Soft In-Cloud Sheet Lightning Flash Shader
            stormCloudFlashPaint.setShader(new RadialGradient(
                    0, 0, 100,
                    new int[]{
                            isDark ? 0xD0E0F2FE : 0xB0BAE6FD,
                            isDark ? 0x757DD3FC : 0x5038BDF8,
                            isDark ? 0x2038BDF8 : 0x150284C7,
                            isDark ? 0x0038BDF8 : 0x000284C7
                    },
                    new float[]{0f, 0.32f, 0.70f, 1.0f},
                    Shader.TileMode.CLAMP
            ));

            // Low-Hanging Scud Mist Shred Shader
            scudMistPaint.setShader(new RadialGradient(
                    0, 0, 100,
                    new int[]{
                            isDark ? 0x35334155 : 0x2E64748B,
                            isDark ? 0x12334155 : 0x1064748B,
                            isDark ? 0x00334155 : 0x0064748B
                    },
                    new float[]{0f, 0.55f, 1.0f},
                    Shader.TileMode.CLAMP
            ));

            // Initialize Dual-Layer Cumulonimbus Storm Clouds
            // Layer 1: Distant background storm masses (slower drift, deep atmospheric depth)
            stormCloudsBack.clear();
            for (int i = 0; i < 3; i++) {
                float cx = (w / 3f) * i + random.nextFloat() * (w / 4f);
                float cy = -AndroidUtilities.dp(8) + random.nextFloat() * AndroidUtilities.dp(30);
                float rx = AndroidUtilities.dp(140 + random.nextFloat() * 60);
                float ry = AndroidUtilities.dp(45 + random.nextFloat() * 20);
                float speed = AndroidUtilities.dp(0.06f + random.nextFloat() * 0.05f);
                float baseAlpha = isDark ? (0.28f + random.nextFloat() * 0.10f) : (0.20f + random.nextFloat() * 0.08f);
                stormCloudsBack.add(new StormCloud(cx, cy, rx, ry, speed, baseAlpha, false, random));
            }

            // Layer 2: Foreground rolling cumulonimbus shelf clouds (richer billow detail)
            stormCloudsFront.clear();
            for (int i = 0; i < 4; i++) {
                float cx = (w / 4f) * i + random.nextFloat() * (w / 5f);
                float cy = AndroidUtilities.dp(10 + random.nextFloat() * 40);
                float rx = AndroidUtilities.dp(95 + random.nextFloat() * 45);
                float ry = AndroidUtilities.dp(38 + random.nextFloat() * 20);
                float speed = AndroidUtilities.dp(0.14f + random.nextFloat() * 0.12f);
                float baseAlpha = isDark ? (0.35f + random.nextFloat() * 0.12f) : (0.24f + random.nextFloat() * 0.10f);
                stormCloudsFront.add(new StormCloud(cx, cy, rx, ry, speed, baseAlpha, true, random));
            }

            // Layer 3: Low-hanging wispy scud mist shreds drifting under cloud shelf
            scudMists.clear();
            for (int i = 0; i < 7; i++) {
                scudMists.add(new ScudMist(w, random, true));
            }

            // 5. Initialize Billowing Smoke Puffs
            smokePuffs.clear();
            for (int i = 0; i < 28; i++) {
                smokePuffs.add(new SmokePuff(w, h, random, true));
            }

            // 6. Initialize Evaporation Steam Wisps
            steamWisps.clear();
            for (int i = 0; i < 18; i++) {
                steamWisps.add(new SteamWisp(w, h, random, true));
            }

            // Pre-bake Soft Shaders for Smoke & Steam
            smokePaint.setShader(new RadialGradient(
                    0, 0, 100,
                    new int[]{
                            isDark ? 0x5594A3B8 : 0x40475569,
                            isDark ? 0x2564748B : 0x18334155,
                            0x00000000
                    },
                    new float[]{0f, 0.45f, 1f},
                    Shader.TileMode.CLAMP
            ));

            steamPaint.setShader(new RadialGradient(
                    0, 0, 50,
                    new int[]{
                            isDark ? 0x60E2E8F0 : 0x45CBD5E1,
                            isDark ? 0x2094A3B8 : 0x1594A3B8,
                            0x00000000
                    },
                    new float[]{0f, 0.40f, 1f},
                    Shader.TileMode.CLAMP
            ));

            // 7. Fire Base Underglow (Ambient heat radiation along bottom)
            float fireH = AndroidUtilities.dp(160);
            fireBaseGlowPaint.setShader(new LinearGradient(
                    0, h - fireH, 0, h,
                    new int[]{0x00000000, 0x14EA580C, 0x40F97316, 0x75EA580C},
                    new float[]{0f, 0.38f, 0.72f, 1f},
                    Shader.TileMode.CLAMP
            ));

            // 5. Multi-Layer Fire Spire Wall Gradients (No white cutoff band at bottom!)
            fireSpirePaint1.setShader(new LinearGradient(
                    0, h - AndroidUtilities.dp(115), 0, h,
                    new int[]{0x00991B1B, 0x85B91C1C, 0xD5DC2626, 0xFF9A3412},
                    new float[]{0f, 0.28f, 0.65f, 1f},
                    Shader.TileMode.CLAMP
            ));
            fireSpirePaint2.setShader(new LinearGradient(
                    0, h - AndroidUtilities.dp(78), 0, h,
                    new int[]{0x00C2410C, 0x90EA580C, 0xEFF97316, 0xFFEA580C},
                    new float[]{0f, 0.26f, 0.62f, 1f},
                    Shader.TileMode.CLAMP
            ));
            fireSpirePaint3.setShader(new LinearGradient(
                    0, h - AndroidUtilities.dp(44), 0, h,
                    new int[]{0x00D97706, 0xB0FBBF24, 0xFFFEF08A, 0xFFF59E0B},
                    new float[]{0f, 0.22f, 0.60f, 1f},
                    Shader.TileMode.CLAMP
            ));

            lastLightningTime = System.currentTimeMillis();
            nextLightningDelay = 2500 + random.nextInt(3000);
        }

        private void generateLightning(int w, int h) {
            lightningPath.rewind();
            lightningBranchPath.rewind();

            float startX = w * (0.22f + random.nextFloat() * 0.56f);
            float startY = 0;
            float curX = startX;
            float curY = startY;

            lightningPath.moveTo(curX, curY);

            int segments = 6 + random.nextInt(4);
            float maxBoltY = h * (0.28f + random.nextFloat() * 0.16f);
            float stepY = maxBoltY / segments;

            float branchStartX = 0;
            float branchStartY = 0;
            boolean hasBranch = false;
            int branchSegment = 2 + random.nextInt(Math.max(1, segments - 3));

            for (int i = 1; i <= segments; i++) {
                float targetY = i * stepY;
                float jitterX = (random.nextFloat() - 0.5f) * AndroidUtilities.dp(26);
                curX += jitterX;
                curY = targetY;
                lightningPath.lineTo(curX, curY);

                if (i == branchSegment && !hasBranch) {
                    branchStartX = curX;
                    branchStartY = curY;
                    hasBranch = true;
                }
            }

            // Generate small branch fork
            if (hasBranch) {
                lightningBranchPath.moveTo(branchStartX, branchStartY);
                float bX = branchStartX;
                float bY = branchStartY;
                float branchDir = (random.nextBoolean() ? 1f : -1f);
                int bSegments = 3 + random.nextInt(2);
                for (int j = 1; j <= bSegments; j++) {
                    bX += branchDir * (AndroidUtilities.dp(12) + random.nextFloat() * AndroidUtilities.dp(14));
                    bY += AndroidUtilities.dp(10) + random.nextFloat() * AndroidUtilities.dp(14);
                    lightningBranchPath.lineTo(bX, bY);
                }
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            int w = getWidth();
            int h = getHeight();
            if (w == 0 || h == 0) return;

            long now = System.currentTimeMillis();
            float timeSec = (now % 60000) / 1000f;

            // ─────────────────────────────────────────────────────────
            // 1. UPPER THUNDERSTORM & LIGHTNING
            // ─────────────────────────────────────────────────────────
            // 1a. Atmospheric undulating overcast sky ceiling
            float ceilBaseH = AndroidUtilities.dp(110);
            stormCeilingPath.rewind();
            stormCeilingPath.moveTo(0, 0);
            stormCeilingPath.lineTo(w, 0);
            stormCeilingPath.lineTo(w, ceilBaseH);
            int ceilSteps = 16;
            for (int s = ceilSteps; s >= 0; s--) {
                float px = (w / (float) ceilSteps) * s;
                float wave = (float) Math.sin(px * 0.012f + timeSec * 0.35f) * AndroidUtilities.dp(14)
                        + (float) Math.cos(px * 0.024f - timeSec * 0.25f) * AndroidUtilities.dp(6);
                stormCeilingPath.lineTo(px, ceilBaseH + wave);
            }
            stormCeilingPath.close();
            canvas.drawPath(stormCeilingPath, stormCeilingPaint);

            // 1b. Distant background storm masses (deep atmospheric layer)
            float flashBoost = currentLightningIntensity * (isDark ? 0.45f : 0.35f);
            for (int i = 0; i < stormCloudsBack.size(); i++) {
                StormCloud cloud = stormCloudsBack.get(i);
                cloud.update(w);
                drawStormCloud(canvas, cloud, flashBoost * 0.65f, timeSec);
            }

            // 1c. Foreground rolling cumulonimbus shelf clouds with highlights & lightning illumination
            for (int i = 0; i < stormCloudsFront.size(); i++) {
                StormCloud cloud = stormCloudsFront.get(i);
                cloud.update(w);
                drawStormCloud(canvas, cloud, flashBoost, timeSec);
            }

            // 1d. Low-hanging wispy scud mist shreds drifting under cloud shelf
            for (int i = 0; i < scudMists.size(); i++) {
                ScudMist mist = scudMists.get(i);
                mist.update(w, random, timeSec);
                if (mist.alpha > 0.01f) {
                    drawCloudPuff(canvas, mist.x, mist.y + mist.sway, mist.rx, mist.ry, scudMistPaint, mist.alpha);
                }
            }

            if (!lightningActive && (now - lastLightningTime > nextLightningDelay)) {
                lightningActive = true;
                lightningStartTime = now;
                generateLightning(w, h);
            }

            if (lightningActive) {
                long elapsed = now - lightningStartTime;
                if (elapsed < 240) {
                    // Double-pulse lightning flash curve
                    float intensity;
                    if (elapsed < 40) {
                        intensity = elapsed / 40f;
                    } else if (elapsed < 75) {
                        intensity = 1.0f - (elapsed - 40) / 35f * 0.70f;
                    } else if (elapsed < 115) {
                        intensity = 0.30f + (elapsed - 75) / 40f * 0.55f;
                    } else {
                        intensity = 0.85f * (1.0f - (elapsed - 115) / 125f);
                    }
                    currentLightningIntensity = Math.max(0, Math.min(1, intensity));

                    // Ambient sky illumination
                    skyFlashPaint.setColor(isDark ? 0x28B0E0FF : 0x200284C7);
                    skyFlashPaint.setAlpha((int) (currentLightningIntensity * (isDark ? 55 : 45)));
                    canvas.drawRect(0, 0, w, h * 0.50f, skyFlashPaint);

                    // Lightning bolt outer halo
                    lightningGlowPaint.setAlpha((int) (currentLightningIntensity * (isDark ? 180 : 210)));
                    canvas.drawPath(lightningPath, lightningGlowPaint);
                    canvas.drawPath(lightningBranchPath, lightningGlowPaint);

                    // Lightning bolt inner white core
                    lightningCorePaint.setAlpha((int) (currentLightningIntensity * 255));
                    canvas.drawPath(lightningPath, lightningCorePaint);
                    canvas.drawPath(lightningBranchPath, lightningCorePaint);

                    if (cardView != null) {
                        cardView.setLightningIntensity(currentLightningIntensity);
                    }
                } else {
                    lightningActive = false;
                    lastLightningTime = now;
                    nextLightningDelay = 3500 + random.nextInt(3500);
                    if (currentLightningIntensity > 0) {
                        currentLightningIntensity = 0f;
                        if (cardView != null) cardView.setLightningIntensity(0f);
                    }
                }
            }

            // ─────────────────────────────────────────────────────────
            // 2. GENTLE RAIN DROPLETS (Upper & Middle Screen)
            // ─────────────────────────────────────────────────────────
            for (int i = 0; i < rainDrops.size(); i++) {
                RainDrop drop = rainDrops.get(i);
                drop.update(w, h, random);
                if (drop.alpha > 0.02f) {
                    rainPaint.setStrokeWidth(drop.thickness);
                    rainPaint.setAlpha((int) (drop.alpha * 255));
                    canvas.drawLine(drop.x, drop.y, drop.x + drop.dx, drop.y + drop.dy, rainPaint);
                }
            }

            // ─────────────────────────────────────────────────────────
            // 3. LOWER BURNING FIRE ANIMATION (REALISTIC MULTI-LAYER INFERNO)
            // ─────────────────────────────────────────────────────────
            // 3a. Evaporating steam wisps where rain meets heat
            for (int i = 0; i < steamWisps.size(); i++) {
                SteamWisp wisp = steamWisps.get(i);
                wisp.update(w, h, random, timeSec);
                if (wisp.alpha > 0.01f) {
                    canvas.save();
                    canvas.translate(wisp.x, wisp.y);
                    float s = wisp.currentRadius / 50f;
                    canvas.scale(s, s);
                    steamPaint.setAlpha((int) (wisp.alpha * 255));
                    canvas.drawCircle(0, 0, 50, steamPaint);
                    canvas.restore();
                }
            }

            // 3b. Rising billowing smoke plumes from flame tips
            for (int i = 0; i < smokePuffs.size(); i++) {
                SmokePuff puff = smokePuffs.get(i);
                puff.update(w, h, random, timeSec);
                if (puff.alpha > 0.01f) {
                    canvas.save();
                    canvas.translate(puff.x, puff.y);
                    float s = puff.currentRadius / 100f;
                    canvas.scale(s, s);
                    smokePaint.setAlpha((int) (puff.alpha * 255));
                    canvas.drawCircle(0, 0, 100, smokePaint);
                    canvas.restore();
                }
            }

            // 3c. Base fire underglow along the floor
            canvas.drawRect(0, h - AndroidUtilities.dp(160), w, h, fireBaseGlowPaint);

            // 3d. Rear Roaring Flame Spire Wall (Deep crimson & fiery red)
            drawFlameSheet(canvas, fireSpirePath1, fireSpirePaint1, w, h,
                    AndroidUtilities.dp(105), AndroidUtilities.dp(28),
                    0, 3.8f, 2.3f, timeSec);

            // 3e. Middle Vibrant Flame Spire Wall (Radiant fire orange & amber)
            drawFlameSheet(canvas, fireSpirePath2, fireSpirePaint2, w, h,
                    AndroidUtilities.dp(68), AndroidUtilities.dp(22),
                    AndroidUtilities.dp(11), 4.6f, 3.1f, timeSec);

            // 3f. Dynamic Licking Flame Tongues (Interwoven between layers)
            for (int i = 0; i < flameTongues.size(); i++) {
                FlameTongue tongue = flameTongues.get(i);
                tongue.update(w, h, random, timeSec);
                drawFlameTongue(canvas, tongue);
            }

            // 3g. Front White-Hot Core Spire Wall (Golden yellow & white-hot fuel bed)
            drawFlameSheet(canvas, fireSpirePath3, fireSpirePaint3, w, h,
                    AndroidUtilities.dp(38), AndroidUtilities.dp(16),
                    AndroidUtilities.dp(17), 6.2f, 4.4f, timeSec);

            // 3h. Floating glowing spark embers rising into the air
            for (int i = 0; i < fireEmbers.size(); i++) {
                FireEmber ember = fireEmbers.get(i);
                ember.update(w, h, random, timeSec);
                if (ember.alpha > 0.05f) {
                    flameTonguePaint.setColor(ember.color);
                    float flicker = 0.85f + 0.15f * (float) Math.sin(timeSec * 16.0f + ember.seed);
                    flameTonguePaint.setAlpha((int) (ember.alpha * flicker * 255));
                    canvas.drawCircle(ember.x, ember.y, ember.size, flameTonguePaint);
                }
            }

            // ─────────────────────────────────────────────────────────
            // 4. CELEBRATION BURST PARTICLES
            // ─────────────────────────────────────────────────────────
            for (int i = burstParticles.size() - 1; i >= 0; i--) {
                Particle p = burstParticles.get(i);
                p.update();
                if (p.alpha <= 0 || p.y > h + 100) {
                    burstParticles.remove(i);
                } else {
                    p.draw(canvas, burstPaint);
                }
            }
        }

        private void drawFlameSheet(Canvas canvas, Path path, Paint paint,
                                    int w, int h, float baseH, float tongueW,
                                    float phaseOffset, float speed1, float speed2, float timeSec) {
            path.rewind();
            path.moveTo(0, h);
            path.lineTo(0, h - baseH * 0.35f);

            int numTongues = (int) Math.ceil((float) w / tongueW) + 2;
            for (int k = 0; k < numTongues; k++) {
                float xL = (k - 0.5f) * tongueW + phaseOffset;
                float xC = k * tongueW + phaseOffset;
                float xR = (k + 0.5f) * tongueW + phaseOffset;

                // Multi-harmonic fluctuating peak height
                float f1 = (float) Math.sin(timeSec * speed1 + k * 1.73f);
                float f2 = (float) Math.sin(timeSec * speed2 - k * 2.41f);
                float f3 = (float) Math.sin(timeSec * (speed1 * 1.6f) + k * 3.89f);
                float hFactor = 0.52f + 0.28f * (f1 * 0.5f + 0.5f) + 0.18f * (f2 * 0.5f + 0.5f) + 0.12f * (f3 * 0.5f + 0.5f);
                float peakH = baseH * hFactor;

                // Dynamic tip sway
                float sway = (float) Math.sin(timeSec * 5.8f + k * 1.25f) * AndroidUtilities.dp(4.2f);
                float tipX = xC + sway;
                float tipY = h - peakH;

                // Connected trough between adjacent flame spires
                float troughH = peakH * 0.28f;
                float troughY = h - troughH;

                // Ascending curve to sharp licking flame peak
                path.cubicTo(
                        xL + tongueW * 0.22f, troughY,
                        tipX - tongueW * 0.18f + sway * 0.25f, tipY + peakH * 0.32f,
                        tipX, tipY
                );
                // Descending curve down to next trough
                path.cubicTo(
                        tipX + tongueW * 0.18f + sway * 0.25f, tipY + peakH * 0.32f,
                        xR - tongueW * 0.22f, troughY,
                        xR, troughY
                );
            }

            path.lineTo(w, h);
            path.close();
            canvas.drawPath(path, paint);
        }

        private void drawFlameTongue(Canvas canvas, FlameTongue tongue) {
            if (tongue.currentHeight <= 0 || tongue.currentWidth <= 0 || tongue.alpha <= 0.01f) return;

            float tipX = tongue.x + tongue.sway;
            float tipY = tongue.y - tongue.currentHeight;
            float halfW = tongue.currentWidth * 0.5f;

            // Asymmetric dynamic curvature: bowing with wind & convection
            float bowX = tongue.sway * 0.6f;

            flameTonguePath.rewind();
            flameTonguePath.moveTo(tipX, tipY);
            // Right side curves down to base
            flameTonguePath.cubicTo(
                    tongue.x + halfW * 1.30f + bowX, tongue.y - tongue.currentHeight * 0.55f,
                    tongue.x + halfW * 0.80f, tongue.y - tongue.currentHeight * 0.12f,
                    tongue.x, tongue.y
            );
            // Left side curves down to base
            flameTonguePath.cubicTo(
                    tongue.x - halfW * 0.80f, tongue.y - tongue.currentHeight * 0.12f,
                    tongue.x - halfW * 1.30f + bowX, tongue.y - tongue.currentHeight * 0.55f,
                    tipX, tipY
            );
            flameTonguePath.close();

            flameTonguePaint.setColor(tongue.outerColor);
            flameTonguePaint.setAlpha((int) (tongue.alpha * 230));
            canvas.drawPath(flameTonguePath, flameTonguePaint);

            // Inner glowing core (visible in prime life stage)
            if (tongue.currentHeight > AndroidUtilities.dp(14) && tongue.life < 0.70f) {
                float coreH = tongue.currentHeight * 0.52f;
                float coreHalfW = halfW * 0.48f;
                float coreTipX = tongue.x + tongue.sway * 0.50f;
                float coreTipY = tongue.y - coreH;
                float coreBowX = bowX * 0.50f;

                flameTonguePath.rewind();
                flameTonguePath.moveTo(coreTipX, coreTipY);
                flameTonguePath.cubicTo(
                        tongue.x + coreHalfW * 1.25f + coreBowX, tongue.y - coreH * 0.50f,
                        tongue.x + coreHalfW * 0.80f, tongue.y - coreH * 0.10f,
                        tongue.x, tongue.y
                );
                flameTonguePath.cubicTo(
                        tongue.x - coreHalfW * 0.80f, tongue.y - coreH * 0.10f,
                        tongue.x - coreHalfW * 1.25f + coreBowX, tongue.y - coreH * 0.50f,
                        coreTipX, coreTipY
                );
                flameTonguePath.close();

                flameTonguePaint.setColor(tongue.innerColor);
                flameTonguePaint.setAlpha((int) (tongue.alpha * 250));
                canvas.drawPath(flameTonguePath, flameTonguePaint);
            }
        }

        private void drawCloudPuff(Canvas canvas, float cx, float cy, float rx, float ry, Paint paint, float alphaRatio) {
            if (alphaRatio <= 0.005f || rx <= 0 || ry <= 0) return;
            canvas.save();
            canvas.translate(cx, cy);
            canvas.scale(rx / 100f, ry / 100f);
            paint.setAlpha((int) (alphaRatio * 255));
            // Drawing at radius 115 guarantees that outer boundary is 100% transparent via Shader.TileMode.CLAMP
            // This completely eliminates any visible circular edge or perimeter line!
            canvas.drawCircle(0, 0, 115, paint);
            canvas.restore();
        }

        private void drawStormCloud(Canvas canvas, StormCloud cloud, float flashBoost, float timeSec) {
            float alpha = Math.min(1.0f, cloud.baseAlpha);
            if (alpha <= 0.005f) return;

            // Subtle organic breathing animation
            float breathe = (float) Math.sin(timeSec * 0.75f + cloud.seed) * AndroidUtilities.dp(1.8f);

            // 1. Draw all anatomical base & body cloud billow lobes
            for (int k = 0; k < cloud.lobeDx.length; k++) {
                float px = cloud.x + cloud.lobeDx[k];
                float py = cloud.y + cloud.lobeDy[k] + breathe * (k % 2 == 0 ? 0.5f : -0.5f);
                float prx = cloud.lobeRx[k];
                float pry = cloud.lobeRy[k];
                float pAlpha = alpha * cloud.lobeAlpha[k];

                drawCloudPuff(canvas, px, py, prx, pry, stormCloudPaint, pAlpha);

                // Subtle ambient top highlight for upper billows
                if (cloud.lobeIsHighlight[k]) {
                    drawCloudPuff(canvas, px, py - pry * 0.15f, prx * 0.85f, pry * 0.75f, stormCloudHighlightPaint, pAlpha * 0.40f);
                }
            }

            // 2. Realistic in-cloud lightning sheet illumination from within the cloud
            if (flashBoost > 0.02f) {
                float flashAlpha = Math.min(1.0f, flashBoost * 1.25f);
                // Center dense lightning flash core
                drawCloudPuff(canvas, cloud.x, cloud.y, cloud.rx * 0.95f, cloud.ry * 0.95f, stormCloudFlashPaint, flashAlpha);
                // Flanking vapor illumination
                if (cloud.lobeDx.length > 2) {
                    drawCloudPuff(canvas, cloud.x + cloud.lobeDx[1], cloud.y + cloud.lobeDy[1], cloud.lobeRx[1] * 0.85f, cloud.lobeRy[1] * 0.85f, stormCloudFlashPaint, flashAlpha * 0.75f);
                    drawCloudPuff(canvas, cloud.x + cloud.lobeDx[2], cloud.y + cloud.lobeDy[2], cloud.lobeRx[2] * 0.85f, cloud.lobeRy[2] * 0.85f, stormCloudFlashPaint, flashAlpha * 0.75f);
                }
            }
        }
    }

    private static class RainDrop {
        float x, y;
        float vx, vy;
        float dx, dy;
        float length;
        float thickness;
        float baseAlpha;
        float alpha;

        RainDrop(int w, int h, Random random, boolean randomizeY) {
            reset(w, h, random, randomizeY);
        }

        void reset(int w, int h, Random random, boolean randomizeY) {
            x = -AndroidUtilities.dp(20) + random.nextFloat() * (w + AndroidUtilities.dp(40));
            y = randomizeY ? (random.nextFloat() * (h * 0.72f)) : -AndroidUtilities.dp(25 + random.nextInt(30));
            length = AndroidUtilities.dp(12 + random.nextFloat() * 12);
            thickness = AndroidUtilities.dp(0.9f + random.nextFloat() * 0.6f);
            baseAlpha = 0.18f + random.nextFloat() * 0.32f;
            alpha = baseAlpha;

            float speed = AndroidUtilities.dp(15 + random.nextFloat() * 9);
            float angleRad = (float) Math.toRadians(11.0); // Slight wind angle
            vx = (float) Math.sin(angleRad) * speed;
            vy = (float) Math.cos(angleRad) * speed;
            dx = (float) Math.sin(angleRad) * length;
            dy = (float) Math.cos(angleRad) * length;
        }

        void update(int w, int h, Random random) {
            x += vx;
            y += vy;

            // Evaporate near the burning fire at the bottom
            float fireThreshold = h - AndroidUtilities.dp(160);
            if (y > fireThreshold) {
                float evapRatio = Math.max(0, 1.0f - (y - fireThreshold) / AndroidUtilities.dp(100));
                alpha = baseAlpha * evapRatio;
            } else {
                alpha = baseAlpha;
            }

            if (y > h - AndroidUtilities.dp(60) || x > w + AndroidUtilities.dp(30)) {
                reset(w, h, random, false);
            }
        }
    }

    private static class FlameTongue {
        float x, y;
        float vy;
        float baseWidth;
        float baseHeight;
        float currentWidth;
        float currentHeight;
        float sway;
        float life;
        float lifeSpeed;
        float seed;
        int outerColor;
        int innerColor;
        float alpha;

        FlameTongue(int w, int h, Random random, boolean randomizeLife) {
            reset(w, h, random, randomizeLife);
        }

        void reset(int w, int h, Random random, boolean randomizeLife) {
            x = random.nextFloat() * w;
            y = h - AndroidUtilities.dp(random.nextFloat() * 16);
            baseWidth = AndroidUtilities.dp(8 + random.nextFloat() * 10);
            baseHeight = AndroidUtilities.dp(28 + random.nextFloat() * 32);
            currentWidth = baseWidth;
            currentHeight = baseHeight;
            life = randomizeLife ? random.nextFloat() : 0f;
            lifeSpeed = 0.018f + random.nextFloat() * 0.024f;
            vy = -(AndroidUtilities.dp(1.2f + random.nextFloat() * 1.8f));
            seed = random.nextFloat() * 100f;
            sway = 0f;
            updateColorsAndAlpha();
        }

        void update(int w, int h, Random random, float timeSec) {
            life += lifeSpeed;
            if (life >= 1.0f) {
                reset(w, h, random, false);
                return;
            }

            y += vy;
            // Turbulent sideways sway increases as flame tongue licks upward
            sway = (float) Math.sin(timeSec * 7.5f + seed) * AndroidUtilities.dp(3.5f + life * 5.5f);
            x += (float) Math.sin(timeSec * 3.8f + seed * 0.5f) * AndroidUtilities.dp(0.8f);

            // Shape dynamics: flame stretches and tapers as it rises
            float taper = 1.0f - life * 0.65f;
            currentWidth = Math.max(AndroidUtilities.dp(2.0f), baseWidth * taper);
            currentHeight = Math.max(AndroidUtilities.dp(6.0f), baseHeight * (1.0f - life * 0.40f));

            updateColorsAndAlpha();
        }

        private void updateColorsAndAlpha() {
            // Smooth appearance at birth, sustained brilliance, fading smoothly into smoky embers
            if (life < 0.15f) {
                alpha = life / 0.15f;
            } else if (life < 0.55f) {
                alpha = 0.95f;
            } else {
                alpha = Math.max(0f, (1.0f - life) / 0.45f);
            }

            // Temperature color spectrum for authentic fire layers
            if (life < 0.25f) {
                // Intense early fire: orange envelope with white-hot yellow core
                outerColor = 0xFFF97316; // Vibrant flame orange
                innerColor = 0xFFFFFBEB; // Pure white-hot core
            } else if (life < 0.58f) {
                // Roaring fire body: rich fiery amber with golden core
                outerColor = 0xFFEA580C; // Deep orange
                innerColor = 0xFFFDE047; // Bright lemon-gold core
            } else if (life < 0.85f) {
                // Rising lick: fiery crimson with warm orange core
                outerColor = 0xFFDC2626; // Crimson red
                innerColor = 0xFFF97316; // Fiery orange core
            } else {
                // Dying tip: dark smoky embers
                outerColor = 0x88450A0A; // Burnt smoky maroon
                innerColor = 0x44260505;
            }
        }
    }

    private static class FireEmber {
        float x, y;
        float vx, vy;
        float size;
        float alpha;
        float alphaSpeed;
        int color;
        float seed;

        private static final int[] EMBER_COLORS = new int[]{
                0xFFFFFFFF, // White-hot spark
                0xFFFEF08A, // Bright lemon spark
                0xFFFDE047, // Gold spark
                0xFFF97316, // Orange spark
                0xFFEA580C  // Amber spark
        };

        FireEmber(int w, int h, Random random, boolean randomizeY) {
            reset(w, h, random, randomizeY);
        }

        void reset(int w, int h, Random random, boolean randomizeY) {
            x = random.nextFloat() * w;
            y = randomizeY ? (h - AndroidUtilities.dp(random.nextFloat() * 220)) : (h - AndroidUtilities.dp(random.nextFloat() * 30));
            size = AndroidUtilities.dp(0.75f + random.nextFloat() * 1.35f);
            vy = -(AndroidUtilities.dp(2.0f + random.nextFloat() * 3.8f));
            alpha = 0.5f + random.nextFloat() * 0.5f;
            alphaSpeed = 0.005f + random.nextFloat() * 0.010f;
            color = EMBER_COLORS[random.nextInt(EMBER_COLORS.length)];
            seed = random.nextFloat() * 50f;
        }

        void update(int w, int h, Random random, float timeSec) {
            y += vy;
            vx = (float) Math.sin(timeSec * 5.2f + seed) * AndroidUtilities.dp(1.4f);
            x += vx;
            alpha -= alphaSpeed;

            if (alpha <= 0 || y < h - AndroidUtilities.dp(260)) {
                reset(w, h, random, false);
            }
        }
    }

    private static class StormCloud {
        float x, y;
        float rx, ry;
        float speed;
        float baseAlpha;
        boolean isForeground;
        float seed;
        float[] lobeDx;
        float[] lobeDy;
        float[] lobeRx;
        float[] lobeRy;
        float[] lobeAlpha;
        boolean[] lobeIsHighlight;

        StormCloud(float x, float y, float rx, float ry, float speed, float baseAlpha, boolean isForeground, Random random) {
            this.x = x;
            this.y = y;
            this.rx = rx;
            this.ry = ry;
            this.speed = speed;
            this.baseAlpha = baseAlpha;
            this.isForeground = isForeground;
            this.seed = random.nextFloat() * 100f;

            int count = isForeground ? 11 : 8;
            lobeDx = new float[count];
            lobeDy = new float[count];
            lobeRx = new float[count];
            lobeRy = new float[count];
            lobeAlpha = new float[count];
            lobeIsHighlight = new boolean[count];

            // 0: Main central dense base condensation deck
            lobeDx[0] = 0;
            lobeDy[0] = 0;
            lobeRx[0] = rx * 1.0f;
            lobeRy[0] = ry * 0.95f;
            lobeAlpha[0] = 1.0f;
            lobeIsHighlight[0] = false;

            // 1 & 2: Flanking horizontal condensation spread
            lobeDx[1] = -rx * 0.52f;
            lobeDy[1] = ry * 0.08f;
            lobeRx[1] = rx * 0.72f;
            lobeRy[1] = ry * 0.78f;
            lobeAlpha[1] = 0.88f;
            lobeIsHighlight[1] = false;

            lobeDx[2] = rx * 0.54f;
            lobeDy[2] = ry * 0.06f;
            lobeRx[2] = rx * 0.76f;
            lobeRy[2] = ry * 0.80f;
            lobeAlpha[2] = 0.85f;
            lobeIsHighlight[2] = false;

            // 3, 4, 5: Upper billowing convective towers (cumulonimbus crowns)
            lobeDx[3] = -rx * 0.22f;
            lobeDy[3] = -ry * 0.38f;
            lobeRx[3] = rx * 0.62f;
            lobeRy[3] = ry * 0.70f;
            lobeAlpha[3] = 0.80f;
            lobeIsHighlight[3] = isForeground;

            lobeDx[4] = rx * 0.18f;
            lobeDy[4] = -ry * 0.45f;
            lobeRx[4] = rx * 0.68f;
            lobeRy[4] = ry * 0.74f;
            lobeAlpha[4] = 0.82f;
            lobeIsHighlight[4] = isForeground;

            lobeDx[5] = 0f;
            lobeDy[5] = -ry * 0.28f;
            lobeRx[5] = rx * 0.78f;
            lobeRy[5] = ry * 0.72f;
            lobeAlpha[5] = 0.90f;
            lobeIsHighlight[5] = false;

            // 6 & 7: Secondary perimeter billows
            lobeDx[6] = -rx * 0.78f;
            lobeDy[6] = ry * 0.12f;
            lobeRx[6] = rx * 0.48f;
            lobeRy[6] = ry * 0.55f;
            lobeAlpha[6] = 0.60f;
            lobeIsHighlight[6] = false;

            lobeDx[7] = rx * 0.82f;
            lobeDy[7] = ry * 0.10f;
            lobeRx[7] = rx * 0.50f;
            lobeRy[7] = ry * 0.56f;
            lobeAlpha[7] = 0.58f;
            lobeIsHighlight[7] = false;

            if (isForeground) {
                // 8, 9, 10: Fine vapor fringes that dissolve naturally into the sky
                lobeDx[8] = -rx * 0.40f;
                lobeDy[8] = -ry * 0.55f;
                lobeRx[8] = rx * 0.38f;
                lobeRy[8] = ry * 0.45f;
                lobeAlpha[8] = 0.50f;
                lobeIsHighlight[8] = true;

                lobeDx[9] = rx * 0.38f;
                lobeDy[9] = -ry * 0.58f;
                lobeRx[9] = rx * 0.42f;
                lobeRy[9] = ry * 0.46f;
                lobeAlpha[9] = 0.52f;
                lobeIsHighlight[9] = true;

                lobeDx[10] = 0f;
                lobeDy[10] = ry * 0.22f;
                lobeRx[10] = rx * 0.70f;
                lobeRy[10] = ry * 0.40f;
                lobeAlpha[10] = 0.70f;
                lobeIsHighlight[10] = false;
            }
        }

        void update(int w) {
            x += speed;
            if (x - rx * 1.8f > w) {
                x = -rx * 1.8f;
            }
        }
    }

    private static class ScudMist {
        float x, y;
        float rx, ry;
        float speed;
        float alpha;
        float sway;
        float seed;

        ScudMist(int w, Random random, boolean initial) {
            reset(w, random, initial);
        }

        void reset(int w, Random random, boolean initial) {
            x = initial ? (random.nextFloat() * w) : -AndroidUtilities.dp(80);
            y = AndroidUtilities.dp(60 + random.nextFloat() * 70);
            rx = AndroidUtilities.dp(45 + random.nextFloat() * 45);
            ry = AndroidUtilities.dp(10 + random.nextFloat() * 12);
            speed = AndroidUtilities.dp(0.18f + random.nextFloat() * 0.14f);
            alpha = 0.12f + random.nextFloat() * 0.08f;
            seed = random.nextFloat() * 100f;
            sway = 0f;
        }

        void update(int w, Random random, float timeSec) {
            x += speed;
            sway = (float) Math.sin(timeSec * 0.9f + seed) * AndroidUtilities.dp(2.2f);
            if (x - rx * 1.5f > w) {
                reset(w, random, false);
            }
        }
    }

    private static class SmokePuff {
        float x, y;
        float vx, vy;
        float startRadius;
        float maxRadius;
        float currentRadius;
        float life;
        float lifeSpeed;
        float maxAlpha;
        float alpha;
        float seed;

        SmokePuff(int w, int h, Random random, boolean randomizeLife) {
            reset(w, h, random, randomizeLife);
        }

        void reset(int w, int h, Random random, boolean randomizeLife) {
            x = random.nextFloat() * w;
            y = h - AndroidUtilities.dp(85 + random.nextFloat() * 45);
            startRadius = AndroidUtilities.dp(14 + random.nextFloat() * 12);
            maxRadius = AndroidUtilities.dp(40 + random.nextFloat() * 30);
            currentRadius = startRadius;
            life = randomizeLife ? random.nextFloat() : 0f;
            lifeSpeed = 0.007f + random.nextFloat() * 0.009f;
            vy = -(AndroidUtilities.dp(0.9f + random.nextFloat() * 1.3f));
            maxAlpha = 0.16f + random.nextFloat() * 0.12f;
            seed = random.nextFloat() * 100f;
            updateAlphaAndRadius();
        }

        void update(int w, int h, Random random, float timeSec) {
            life += lifeSpeed;
            if (life >= 1.0f || y < h - AndroidUtilities.dp(300)) {
                reset(w, h, random, false);
                return;
            }

            y += vy;
            vx = (float) Math.sin(timeSec * 2.2f + seed) * AndroidUtilities.dp(1.2f);
            x += vx;

            updateAlphaAndRadius();
        }

        private void updateAlphaAndRadius() {
            currentRadius = startRadius + (maxRadius - startRadius) * (float) Math.sqrt(life);

            if (life < 0.20f) {
                alpha = (life / 0.20f) * maxAlpha;
            } else if (life < 0.55f) {
                alpha = maxAlpha;
            } else {
                alpha = Math.max(0f, (1.0f - life) / 0.45f * maxAlpha);
            }
        }
    }

    private static class SteamWisp {
        float x, y;
        float vx, vy;
        float startRadius, maxRadius, currentRadius;
        float life, lifeSpeed;
        float maxAlpha, alpha;
        float seed;

        SteamWisp(int w, int h, Random random, boolean randomizeLife) {
            reset(w, h, random, randomizeLife);
        }

        void reset(int w, int h, Random random, boolean randomizeLife) {
            x = random.nextFloat() * w;
            y = h - AndroidUtilities.dp(120 + random.nextFloat() * 45);
            startRadius = AndroidUtilities.dp(4 + random.nextFloat() * 4);
            maxRadius = AndroidUtilities.dp(14 + random.nextFloat() * 12);
            currentRadius = startRadius;
            life = randomizeLife ? random.nextFloat() : 0f;
            lifeSpeed = 0.022f + random.nextFloat() * 0.028f;
            vy = -(AndroidUtilities.dp(1.2f + random.nextFloat() * 1.6f));
            maxAlpha = 0.22f + random.nextFloat() * 0.15f;
            seed = random.nextFloat() * 100f;
            updateAlphaAndRadius();
        }

        void update(int w, int h, Random random, float timeSec) {
            life += lifeSpeed;
            if (life >= 1.0f) {
                reset(w, h, random, false);
                return;
            }

            y += vy;
            vx = (float) Math.sin(timeSec * 4.5f + seed) * AndroidUtilities.dp(1.0f);
            x += vx;

            updateAlphaAndRadius();
        }

        private void updateAlphaAndRadius() {
            currentRadius = startRadius + (maxRadius - startRadius) * life;
            if (life < 0.20f) {
                alpha = (life / 0.20f) * maxAlpha;
            } else {
                alpha = Math.max(0f, (1.0f - life) / 0.80f * maxAlpha);
            }
        }
    }

    private static class Particle {
        float x, y;
        float vx, vy;
        float size;
        int color;
        float alpha;
        float alphaSpeed;
        boolean isBurst;
        float rotation;
        float rotSpeed;

        // Celebratory burst palette
        private static final int[] BURST_PALETTE = new int[]{
                0xFF2AABEE, // Telegram Azure
                0xFF38BDF8, // Sky Blue
                0xFF34D399, // Mint Emerald
                0xFFFDE047, // Soft Gold
                0xFFFFFFFF  // Pure White
        };

        Particle(float x, float y, boolean burst) {
            this.x = x;
            this.y = y;
            this.isBurst = burst;
            Random r = new Random();
            rotation = r.nextFloat() * 360f;
            rotSpeed = (r.nextFloat() - 0.5f) * 6f;
            color = BURST_PALETTE[r.nextInt(BURST_PALETTE.length)];

            float angle = (float) (r.nextFloat() * Math.PI * 2);
            float speed = AndroidUtilities.dp(3.5f) + r.nextFloat() * AndroidUtilities.dp(12);
            vx = (float) Math.cos(angle) * speed;
            vy = (float) Math.sin(angle) * speed - AndroidUtilities.dp(2);
            size = AndroidUtilities.dp(3.0f) + r.nextFloat() * AndroidUtilities.dp(4.0f);
            alpha = 1.0f;
            alphaSpeed = 0.016f + r.nextFloat() * 0.02f;
        }

        void update() {
            x += vx;
            y += vy;
            rotation += rotSpeed;
            vy += AndroidUtilities.dp(0.30f); // Gravity
            vx *= 0.96f; // Air friction
            alpha -= alphaSpeed;
        }

        void draw(Canvas canvas, Paint paint) {
            if (alpha <= 0) return;
            paint.setColor(color);
            paint.setAlpha((int) (Math.max(0, Math.min(1, alpha)) * 255));

            canvas.save();
            canvas.translate(x, y);
            canvas.rotate(rotation);
            RectF rf = new RectF(-size / 2f, -size / 3f, size / 2f, size / 3f);
            canvas.drawRoundRect(rf, AndroidUtilities.dp(1.5f), AndroidUtilities.dp(1.5f), paint);
            canvas.restore();
        }
    }
}
// [Alexgram: Official Channel Welcome Dialog] - End
