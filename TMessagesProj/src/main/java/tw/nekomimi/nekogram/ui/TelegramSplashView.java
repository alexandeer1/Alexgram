package tw.nekomimi.nekogram.ui;

import android.app.Activity;
import android.content.Context;
import android.graphics.drawable.Animatable2;
import android.graphics.drawable.AnimatedVectorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;

public class TelegramSplashView extends FrameLayout {

    private final ImageView imageView;
    private Runnable onFinished;
    private boolean isFinished = false;
    private final Runnable fallbackRunnable = this::finish;

    public TelegramSplashView(Context context) {
        super(context);
        
        boolean isDark = Theme.isCurrentThemeDark();
        setBackgroundColor(isDark ? 0xFF1F2732 : 0xFFFFFFFF);

        if (context instanceof Activity) {
            Activity activity = (Activity) context;
            AndroidUtilities.setLightStatusBar(activity, !isDark);
            AndroidUtilities.setLightNavigationBar(activity, !isDark);
        }

        imageView = new ImageView(context);
        Drawable drawable = context.getDrawable(R.drawable.tg_splash_320);
        imageView.setImageDrawable(drawable);

        // Center the 320dp x 320dp vector drawable in the screen
        int size = AndroidUtilities.dp(320);
        LayoutParams params = new LayoutParams(size, size, Gravity.CENTER);
        addView(imageView, params);

        setOnClickListener(v -> finish());

        if (drawable instanceof AnimatedVectorDrawable) {
            AnimatedVectorDrawable avd = (AnimatedVectorDrawable) drawable;
            
            // On API 23+, we can register an animation callback
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                avd.registerAnimationCallback(new Animatable2.AnimationCallback() {
                    @Override
                    public void onAnimationEnd(Drawable drawable) {
                        post(() -> finish());
                    }
                });
            }
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        Drawable drawable = imageView.getDrawable();
        if (drawable instanceof AnimatedVectorDrawable) {
            ((AnimatedVectorDrawable) drawable).start();
        }
        // Timer fallback to ensure the splash screen doesn't get stuck (e.g. 1000ms)
        postDelayed(fallbackRunnable, 1000);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        removeCallbacks(fallbackRunnable);
    }

    public void setOnFinishedCallback(Runnable callback) {
        this.onFinished = callback;
    }

    private void finish() {
        if (isFinished) return;
        isFinished = true;
        if (onFinished != null) {
            onFinished.run();
        }
    }
}

