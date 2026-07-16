package tw.nekomimi.nekogram.ui;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import org.telegram.messenger.AndroidUtilities;

public class IconSplashView extends FrameLayout {

    private final ImageView imageView;
    private Runnable onFinished;
    private boolean isFinished = false;

    public IconSplashView(Context context) {
        super(context);
        setBackgroundColor(0xFF061B3D); // Deep blue background matching Theme.TMessages.Start

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

        // Auto-finish after 800ms
        postDelayed(() -> finish(), 800);
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
