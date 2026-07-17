package tw.nekomimi.nekogram.ui;

import android.app.Activity;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;

public class IconSplashView extends FrameLayout {

    private final ImageView imageView;
    private Runnable onFinished;
    private boolean isFinished = false;
    private final Runnable fallbackRunnable = this::finish;

    public IconSplashView(Context context) {
        super(context);
        
        boolean isDark = Theme.isCurrentThemeDark();
        setBackgroundColor(isDark ? 0xFF1F2732 : 0xFFFFFFFF);

        if (context instanceof Activity) {
            Activity activity = (Activity) context;
            AndroidUtilities.setLightStatusBar(activity, !isDark);
            AndroidUtilities.setLightNavigationBar(activity, !isDark);
        }

        imageView = new ImageView(context);
        try {
            Drawable drawable = context.getPackageManager().getApplicationIcon(context.getPackageName());
            imageView.setImageDrawable(drawable);
        } catch (Exception e) {
            // Fallback
        }

        // Center the 120dp x 120dp icon in the screen
        int size = AndroidUtilities.dp(120);
        LayoutParams params = new LayoutParams(size, size, Gravity.CENTER);
        addView(imageView, params);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        // Auto-finish after 800ms
        postDelayed(fallbackRunnable, 800);
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

