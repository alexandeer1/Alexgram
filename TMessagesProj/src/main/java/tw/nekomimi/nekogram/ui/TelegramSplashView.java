package tw.nekomimi.nekogram.ui;

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

public class TelegramSplashView extends FrameLayout {

    private final ImageView imageView;
    private Runnable onFinished;
    private boolean isFinished = false;

    public TelegramSplashView(Context context) {
        super(context);
        setBackgroundColor(0xFF061B3D); // Deep blue background matching Theme.TMessages.Start

        imageView = new ImageView(context);
        Drawable drawable = context.getDrawable(R.drawable.tg_splash_320);
        imageView.setImageDrawable(drawable);

        // Center the 320dp x 320dp vector drawable in the screen
        int size = AndroidUtilities.dp(320);
        LayoutParams params = new LayoutParams(size, size, Gravity.CENTER);
        addView(imageView, params);

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
            avd.start();
        }

        // Timer fallback to ensure the splash screen doesn't get stuck (e.g. 1000ms)
        postDelayed(() -> finish(), 1000);
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
