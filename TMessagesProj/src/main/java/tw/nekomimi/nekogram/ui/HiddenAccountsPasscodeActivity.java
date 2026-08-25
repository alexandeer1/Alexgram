// [Alexgram: Hidden Accounts] - Start
package tw.nekomimi.nekogram.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.os.SystemClock;
import android.text.method.PasswordTransformationMethod;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.FingerprintController;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.CodeFieldContainer;
import org.telegram.ui.CodeNumberField;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.CustomPhoneKeyboardView;
import org.telegram.ui.Components.LayoutHelper;

import java.util.ArrayList;
import java.util.Random;
import java.util.concurrent.Executor;

import tw.nekomimi.nekogram.helpers.HiddenAccountsController;
import tw.nekomimi.nekogram.settings.HiddenAccountsSettingsActivity;

public class HiddenAccountsPasscodeActivity extends BaseFragment {

    public static final int MODE_SETUP_PIN      = 0;
    public static final int MODE_UNLOCK_SETTINGS = 1;
    public static final int MODE_CHANGE_PIN     = 2;

    private final int mode;
    private int setupStep;
    private int changeStep;
    private String firstPin;

    private TextView titleView;
    private TextView subtitleView;
    private TextView errorView;
    private VaultContainerView containerView;
    private CodeFieldContainer codeFieldContainer;
    private CustomPhoneKeyboardView keyboardView;
    private AmberBackgroundView backgroundView;
    private ImageView fingerprintImage;
    private FrameLayout rootView;

    private boolean stableIsDark;

    // Accent colour palette: amber/gold
    private static final int DARK_ACCENT  = 0xFFFFAB00;
    private static final int LIGHT_ACCENT = 0xFFFF8F00;

    public HiddenAccountsPasscodeActivity(int mode) {
        this.mode = mode;
    }

    private int accentColor() {
        return stableIsDark ? DARK_ACCENT : LIGHT_ACCENT;
    }

    @Override
    public boolean onFragmentCreate() {
        stableIsDark = Theme.isCurrentThemeDark();
        return super.onFragmentCreate();
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(false);
        actionBar.setCastShadows(false);
        actionBar.setTitle("HIDDEN ACCOUNTS");
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) finishFragment();
            }
        });

        boolean isDark = stableIsDark;
        int accent = accentColor();
        int bgColor = isDark ? 0xFF0A0500 : 0xFFFFFBF0;
        int textColor = isDark ? Color.WHITE : 0xFF212121;
        int subTextColor = isDark ? Color.argb(200, 255, 255, 255) : Color.argb(200, 80, 60, 0);

        actionBar.setBackgroundColor(bgColor);
        actionBar.setItemsColor(isDark ? Color.WHITE : 0xFF1A1A1A, false);
        actionBar.setTitleColor(isDark ? Color.WHITE : 0xFF1A1A1A);
        actionBar.setItemsBackgroundColor(isDark ? 0x0FFFFFFF : 0x0F000000, false);

        FrameLayout root = new FrameLayout(context);
        rootView = root;
        root.setBackgroundColor(bgColor);

        backgroundView = new AmberBackgroundView(context);
        root.addView(backgroundView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        LinearLayout mainContent = new LinearLayout(context);
        mainContent.setOrientation(LinearLayout.VERTICAL);
        root.addView(mainContent, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        ScrollView scrollWrapper = new ScrollView(context);
        scrollWrapper.setFillViewport(true);
        mainContent.addView(scrollWrapper, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 0, 1.0f));

        FrameLayout topLayout = new FrameLayout(context);
        scrollWrapper.addView(topLayout, LayoutHelper.createScroll(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.NO_GRAVITY));

        containerView = new VaultContainerView(context);
        topLayout.addView(containerView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 28, 20, 28, 20));

        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER_HORIZONTAL);
        containerView.addView(content, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP, 0, 20, 0, 20));

        titleView = new TextView(context);
        titleView.setTextColor(accent);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 26);
        titleView.setTypeface(AndroidUtilities.bold());
        titleView.setGravity(Gravity.CENTER_HORIZONTAL);
        titleView.setLetterSpacing(0.08f);
        content.addView(titleView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL));

        subtitleView = new TextView(context);
        subtitleView.setTextColor(subTextColor);
        subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11);
        subtitleView.setGravity(Gravity.CENTER_HORIZONTAL);
        subtitleView.setPadding(AndroidUtilities.dp(32), AndroidUtilities.dp(8), AndroidUtilities.dp(32), 0);
        subtitleView.setAllCaps(true);
        subtitleView.setLetterSpacing(0.12f);
        content.addView(subtitleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL));

        codeFieldContainer = new CodeFieldContainer(context) {
            @Override
            protected void processNextPressed() {
                handleCodeEntered();
            }
        };
        codeFieldContainer.setNumbersCount(4, CodeFieldContainer.TYPE_PASSCODE);
        for (int i = 0; i < codeFieldContainer.codeField.length; i++) {
            final CodeNumberField f = codeFieldContainer.codeField[i];
            f.setShowSoftInputOnFocusCompat(false);
            f.setTransformationMethod(PasswordTransformationMethod.getInstance());
            f.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 32);
            f.setTextColor(textColor);
            f.setCursorColor(accent);
            f.setCursorWidth(AndroidUtilities.dp(2));
            f.setBackground(new android.graphics.drawable.Drawable() {
                private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
                @Override public void draw(@NonNull Canvas canvas) {
                    boolean focused = f.isFocused();
                    p.setColor(accent);
                    p.setStrokeWidth(AndroidUtilities.dp(focused ? 3 : 1.5f));
                    p.setAlpha(focused ? 255 : 110);
                    float y = canvas.getHeight() - AndroidUtilities.dp(2);
                    canvas.drawLine(0, y, canvas.getWidth(), y, p);
                    if (focused) {
                        p.setAlpha(35);
                        canvas.drawRect(0, 0, canvas.getWidth(), canvas.getHeight(), p);
                    }
                }
                @Override public void setAlpha(int a) {}
                @Override public void setColorFilter(android.graphics.ColorFilter cf) {}
                @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
            });
            f.setPadding(0, 0, 0, AndroidUtilities.dp(8));
            f.setOnFocusChangeListener((v, hasFocus) -> {
                keyboardView.setEditText(f);
                keyboardView.setDispatchBackWhenEmpty(true);
                if (hasFocus) v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                v.invalidate();
            });
        }
        content.addView(codeFieldContainer, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 40, 0, 0));

        errorView = new TextView(context);
        errorView.setTextColor(0xFFFF6D00);
        errorView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        errorView.setGravity(Gravity.CENTER_HORIZONTAL);
        errorView.setVisibility(View.INVISIBLE);
        errorView.setPadding(0, AndroidUtilities.dp(20), 0, 0);
        errorView.setAllCaps(true);
        content.addView(errorView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL));

        if (android.os.Build.VERSION.SDK_INT >= 23 && mode == MODE_UNLOCK_SETTINGS) {
            fingerprintImage = new ImageView(context);
            fingerprintImage.setImageResource(R.drawable.fingerprint);
            fingerprintImage.setScaleType(ImageView.ScaleType.CENTER);
            fingerprintImage.setColorFilter(new android.graphics.PorterDuffColorFilter(accent, android.graphics.PorterDuff.Mode.SRC_IN));
            fingerprintImage.setBackground(Theme.createSimpleSelectorCircleDrawable(AndroidUtilities.dp(64), Color.TRANSPARENT, Color.argb(isDark ? 40 : 20, 255, 171, 0)));
            fingerprintImage.setOnClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                checkFingerprint();
            });
            fingerprintImage.setContentDescription(LocaleController.getString(R.string.AccDescrFingerprint));
            content.addView(fingerprintImage, LayoutHelper.createLinear(64, 64, Gravity.CENTER_HORIZONTAL, 0, 30, 0, 0));
        }

        keyboardView = new CustomPhoneKeyboardView(context);
        keyboardView.setBackgroundColor(0);
        mainContent.addView(keyboardView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, CustomPhoneKeyboardView.KEYBOARD_HEIGHT_DP, Gravity.BOTTOM, 8, 0, 8, 16));

        root.setOnClickListener(v -> focusFirstEmptyField());

        updateTexts();
        focusFirstEmptyField();
        runEntranceAnimation();

        fragmentView = root;
        return fragmentView;
    }

    @Override
    public void onResume() {
        super.onResume();
        AndroidUtilities.requestAltFocusable(getParentActivity(), classGuid);
        AndroidUtilities.hideKeyboard(fragmentView);
        if (mode == MODE_UNLOCK_SETTINGS) checkFingerprint();
    }

    @Override
    public void onPause() {
        super.onPause();
        AndroidUtilities.removeAltFocusable(getParentActivity(), classGuid);
    }

    // ── Code logic ────────────────────────────────────────────────────────────

    private void handleCodeEntered() {
        String code = getCode();
        if (code.length() != 4) { showError("PIN must be 4 digits"); return; }
        clearError();
        HiddenAccountsController ctrl = HiddenAccountsController.getInstance();

        if (mode == MODE_SETUP_PIN) {
            if (setupStep == 0) {
                firstPin = code; setupStep = 1;
                clearCode(); updateTexts(); return;
            }
            if (!code.equals(firstPin)) {
                showError("PINs do not match");
                setupStep = 0; firstPin = null;
                clearCode(); updateTexts(); return;
            }
            ctrl.setPin(code);
            BulletinFactory.of(this).createSimpleBulletin(R.raw.done, "Hidden Accounts activated").show();
            presentFragment(new HiddenAccountsSettingsActivity(), true);
            return;
        }

        if (mode == MODE_CHANGE_PIN) {
            if (changeStep == 0) {
                if (!ctrl.checkPin(code)) { showError("Incorrect PIN"); clearCode(); return; }
                changeStep = 1; clearCode(); updateTexts(); return;
            }
            if (changeStep == 1) {
                firstPin = code; changeStep = 2; clearCode(); updateTexts(); return;
            }
            if (!code.equals(firstPin)) {
                showError("PINs do not match");
                changeStep = 1; firstPin = null;
                clearCode(); updateTexts(); return;
            }
            ctrl.setPin(code);
            BulletinFactory.of(this).createSimpleBulletin(R.raw.done, "PIN changed").show();
            finishFragment();
            return;
        }

        // MODE_UNLOCK_SETTINGS
        if (!ctrl.checkPin(code)) { showError("Incorrect PIN"); clearCode(); return; }
        ctrl.unlock();
        if (fragmentView != null) fragmentView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        presentFragment(new HiddenAccountsSettingsActivity(), true);
    }

    private void checkFingerprint() {
        if (android.os.Build.VERSION.SDK_INT < 23) return;
        Activity activity = getParentActivity();
        if (!(activity instanceof FragmentActivity)) return;
        try {
            if (BiometricManager.from(getContext()).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS
                    && FingerprintController.isKeyReady()
                    && !FingerprintController.checkDeviceFingerprintsChanged()) {
                Executor executor = ContextCompat.getMainExecutor(getContext());
                BiometricPrompt prompt = new BiometricPrompt((FragmentActivity) activity, executor, new BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                        AndroidUtilities.runOnUIThread(() -> {
                            HiddenAccountsController.getInstance().unlock();
                            presentFragment(new HiddenAccountsSettingsActivity(), true);
                        });
                    }
                    @Override
                    public void onAuthenticationError(int code, @NonNull CharSequence msg) { focusFirstEmptyField(); }
                    @Override
                    public void onAuthenticationFailed() { showError("Biometric not recognized"); }
                });
                BiometricPrompt.PromptInfo info = new BiometricPrompt.PromptInfo.Builder()
                        .setTitle("Hidden Accounts")
                        .setNegativeButtonText(LocaleController.getString(R.string.UsePIN))
                        .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                        .build();
                prompt.authenticate(info);
            }
        } catch (Exception e) { FileLog.e(e); }
    }

    // ── Text animation ────────────────────────────────────────────────────────

    private void updateTexts() {
        String title, sub;
        if (mode == MODE_SETUP_PIN) {
            if (setupStep == 0) { title = "INIT VAULT"; sub = "Set a 4-digit PIN to protect accounts."; }
            else                 { title = "CONFIRM PIN"; sub = "Re-enter the PIN to verify."; }
        } else if (mode == MODE_CHANGE_PIN) {
            if (changeStep == 0)      { title = "VERIFY CURRENT"; sub = "Enter your existing PIN first."; }
            else if (changeStep == 1) { title = "SET NEW PIN"; sub = "Choose your new 4-digit PIN."; }
            else                      { title = "CONFIRM NEW";   sub = "Re-enter the new PIN."; }
        } else {
            title = "VAULT ACCESS"; sub = "Enter PIN to manage hidden accounts.";
        }
        runDecryptAnim(titleView, title);
        runDecryptAnim(subtitleView, sub);
    }

    private void runDecryptAnim(TextView view, String target) {
        if (target == null || view == null) return;
        ValueAnimator anim = ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(700);
        Random rnd = new Random();
        anim.addUpdateListener(a -> {
            float p = (float) a.getAnimatedValue();
            int reveal = (int) (target.length() * p);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < target.length(); i++) {
                if (i < reveal) sb.append(target.charAt(i));
                else sb.append((char) ('!' + rnd.nextInt(90)));
            }
            view.setText(sb.toString());
        });
        anim.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator animation) { view.setText(target); }
        });
        anim.start();
    }

    private void runEntranceAnimation() {
        containerView.setAlpha(0); containerView.setScaleX(1.15f); containerView.setScaleY(1.15f);
        keyboardView.setAlpha(0); keyboardView.setTranslationY(AndroidUtilities.dp(120));
        AndroidUtilities.runOnUIThread(() -> {
            containerView.animate().alpha(1).scaleX(1).scaleY(1).setDuration(750).setInterpolator(new OvershootInterpolator(0.7f)).start();
            keyboardView.animate().alpha(1).translationY(0).setDuration(900).setStartDelay(250).setInterpolator(AndroidUtilities.overshootInterpolator).start();
        }, 80);
    }

    // ── Field helpers ─────────────────────────────────────────────────────────

    private void focusFirstEmptyField() {
        if (codeFieldContainer == null || codeFieldContainer.codeField == null) return;
        for (int i = 0; i < codeFieldContainer.codeField.length; i++) {
            if (codeFieldContainer.codeField[i].length() == 0) {
                codeFieldContainer.codeField[i].requestFocus(); return;
            }
        }
        codeFieldContainer.codeField[codeFieldContainer.codeField.length - 1].requestFocus();
    }

    private String getCode() {
        return codeFieldContainer != null ? codeFieldContainer.getCode() : "";
    }

    private void clearCode() {
        if (codeFieldContainer == null) return;
        for (CodeNumberField f : codeFieldContainer.codeField) f.setText("");
        focusFirstEmptyField();
    }

    private void showError(String msg) {
        if (errorView == null) return;
        errorView.setText(msg); errorView.setVisibility(View.VISIBLE);
        if (codeFieldContainer != null) {
            AndroidUtilities.shakeViewSpring(codeFieldContainer, 4f);
            ValueAnimator ca = ValueAnimator.ofArgb(accentColor(), 0xFFFF6D00, accentColor());
            ca.setDuration(450);
            ca.addUpdateListener(a -> {
                int c = (int) a.getAnimatedValue();
                for (CodeNumberField f : codeFieldContainer.codeField) f.setTextColor(c);
            });
            ca.start();
        }
        if (fragmentView != null) fragmentView.performHapticFeedback(HapticFeedbackConstants.CONFIRM);
    }

    private void clearError() {
        if (errorView != null) { errorView.setText(""); errorView.setVisibility(View.INVISIBLE); }
    }

    // ── Custom views ──────────────────────────────────────────────────────────

    /** Animated plexus-particle amber background */
    private class AmberBackgroundView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final ArrayList<Dot> dots = new ArrayList<>();
        private final Random rnd = new Random();
        private long lastTime;

        AmberBackgroundView(Context ctx) {
            super(ctx);
            for (int i = 0; i < 35; i++) dots.add(new Dot());
        }

        @Override
        protected void onDraw(Canvas canvas) {
            long now = SystemClock.elapsedRealtime();
            float dt = lastTime == 0 ? 0.016f : (now - lastTime) / 1000f;
            lastTime = now;
            boolean isDark = stableIsDark;
            int c1 = isDark ? 0xFF0A0500 : 0xFFFFFBF0;
            int c2 = isDark ? 0xFF1A0A00 : 0xFFFFECB3;
            int ac = accentColor();

            paint.setShader(new LinearGradient(0, 0, 0, getHeight(), c1, c2, Shader.TileMode.CLAMP));
            canvas.drawRect(0, 0, getWidth(), getHeight(), paint);
            paint.setShader(null);
            paint.setStrokeWidth(AndroidUtilities.dp(0.5f));
            for (int i = 0; i < dots.size(); i++) {
                Dot d1 = dots.get(i); d1.update(dt, getWidth(), getHeight());
                for (int j = i + 1; j < dots.size(); j++) {
                    Dot d2 = dots.get(j);
                    float dist = (float) Math.hypot(d1.x - d2.x, d1.y - d2.y);
                    if (dist < AndroidUtilities.dp(110)) {
                        paint.setColor(ac);
                        paint.setAlpha((int) ((1f - dist / AndroidUtilities.dp(110)) * (isDark ? 55 : 35)));
                        canvas.drawLine(d1.x, d1.y, d2.x, d2.y, paint);
                    }
                }
            }
            for (Dot d : dots) d.draw(canvas, ac, isDark);
            // Amber scanline
            paint.setColor(ac); paint.setAlpha(isDark ? 30 : 20);
            paint.setStrokeWidth(AndroidUtilities.dp(1));
            float scan = (now % 3500) / 3500f * getHeight();
            canvas.drawLine(0, scan, getWidth(), scan, paint);
            invalidate();
        }

        private class Dot {
            float x, y, vx, vy, r, a;
            void reset(int w, int h) {
                x = rnd.nextFloat() * w; y = rnd.nextFloat() * h;
                vx = (rnd.nextFloat() - 0.5f) * 35; vy = (rnd.nextFloat() - 0.5f) * 35;
                r = 1 + rnd.nextFloat() * 2; a = 0.2f + rnd.nextFloat() * 0.5f;
            }
            void update(float dt, int w, int h) {
                if (x == 0) reset(w, h);
                x += vx * dt; y += vy * dt;
                if (x < 0) x = w; if (x > w) x = 0;
                if (y < 0) y = h; if (y > h) y = 0;
            }
            void draw(Canvas c, int color, boolean dark) {
                paint.setColor(color); paint.setAlpha((int) (a * (dark ? 240 : 160)));
                c.drawCircle(x, y, AndroidUtilities.dp(r), paint);
            }
        }
    }

    /** Vault-style container with amber corner brackets */
    private class VaultContainerView extends FrameLayout {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float scanY; private boolean down = true;

        VaultContainerView(Context ctx) {
            super(ctx); setWillNotDraw(false);
            setBackgroundColor(Color.argb(stableIsDark ? 35 : 20, 255, 171, 0));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float w = getWidth(), h = getHeight();
            int ac = accentColor();
            // Border
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(AndroidUtilities.dp(0.8f));
            p.setColor(ac); p.setAlpha(stableIsDark ? 60 : 45);
            canvas.drawRect(AndroidUtilities.dp(2), AndroidUtilities.dp(2), w - AndroidUtilities.dp(2), h - AndroidUtilities.dp(2), p);
            // Corner brackets
            p.setAlpha(255); p.setStrokeWidth(AndroidUtilities.dp(2.5f));
            float len = AndroidUtilities.dp(18);
            canvas.drawLine(0, 0, len, 0, p); canvas.drawLine(0, 0, 0, len, p);
            canvas.drawLine(w, 0, w - len, 0, p); canvas.drawLine(w, 0, w, len, p);
            canvas.drawLine(0, h, len, h, p); canvas.drawLine(0, h, 0, h - len, p);
            canvas.drawLine(w, h, w - len, h, p); canvas.drawLine(w, h, w, h - len, p);
            // Scanner
            p.setStrokeWidth(AndroidUtilities.dp(1.5f)); p.setAlpha(stableIsDark ? 90 : 70);
            canvas.drawLine(AndroidUtilities.dp(4), scanY, w - AndroidUtilities.dp(4), scanY, p);
            if (down) { scanY += AndroidUtilities.dp(1.8f); if (scanY > h) down = false; }
            else       { scanY -= AndroidUtilities.dp(1.8f); if (scanY < 0)  down = true;  }
            // Decorative text
            p.setStyle(Paint.Style.FILL); p.setTextSize(AndroidUtilities.dp(7));
            p.setAlpha(stableIsDark ? 110 : 160);
            canvas.drawText("VAULT_STATUS: SEALED", AndroidUtilities.dp(8), AndroidUtilities.dp(14), p);
            canvas.drawText("AUTH: PIN_4D", w - AndroidUtilities.dp(72), h - AndroidUtilities.dp(8), p);
            invalidate();
        }
    }
}
// [Alexgram: Hidden Accounts] - End