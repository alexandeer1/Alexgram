package xyz.nextalone.nagram;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.graphics.BlurMaskFilter;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;

import java.util.ArrayList;
import java.util.Random;

public class StarFallView extends View {

    private final ArrayList<Star> stars = new ArrayList<>();
    private final Random random = new Random();
    private Paint starPaint;
    private Paint glowPaint;
    private Bitmap starBitmap;
    private long lastTime;

    private static class Star {
        float x, y;
        float speed;
        float scale;
        float alpha;
        float rotation;
        float rotationSpeed;
        int color;
        float lifeTime; 
        float maxLife;

        void reset(int w, int h, Random random) {
            x = random.nextFloat() * w;
            y = -random.nextFloat() * h * 0.5f - AndroidUtilities.dp(50); // Start way above
            speed = AndroidUtilities.dp(1) + random.nextFloat() * AndroidUtilities.dp(3);
            scale = 0.5f + random.nextFloat() * 1.0f;
            alpha = 0f; 
            rotation = random.nextFloat() * 360;
            rotationSpeed = (random.nextFloat() - 0.5f) * 100;
            lifeTime = 0;
            maxLife = 200 + random.nextInt(200); // Frames

            // "God Level" Colors
            int type = random.nextInt(10);
            if (type < 4) color = 0xFFFFFFFF; // White (40%)
            else if (type < 6) color = 0xFFFFD700; // Gold (20%)
            else if (type < 8) color = 0xFF00BFFF; // Deep Sky Blue (20%)
            else color = 0xFFFF69B4; // Hot Pink (20%)
        }
    }

    public StarFallView(Context context) {
        super(context);
        init();
    }

    private void init() {
        // Hardware acceleration is good for bitmaps, but shadowLayer needs software.
        // We will simulate glow with a separate paint/circle instead of shadowLayer for performance.
        setLayerType(LAYER_TYPE_HARDWARE, null);

        starPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        glowPaint.setStyle(Paint.Style.FILL);
        glowPaint.setMaskFilter(new BlurMaskFilter(AndroidUtilities.dp(8), BlurMaskFilter.Blur.NORMAL));

        Drawable drawable = null;
        try {
            drawable = getContext().getResources().getDrawable(R.drawable.photo_star_fill);
        } catch (Exception e) {
            // ignore
        }
        
        if (drawable == null) {
             try {
                drawable = getContext().getResources().getDrawable(R.drawable.baseline_star_24);
             } catch (Exception e) {}
        }

        if (drawable != null) {
            int size = AndroidUtilities.dp(24);
            starBitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(starBitmap);
            drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
            drawable.setColorFilter(new PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN));
            drawable.draw(canvas);
        }

        // Initialize stars
        for (int i = 0; i < 60; i++) {
            Star s = new Star();
            // Initially place them randomly on screen so it's not empty start
            s.reset(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y, random); // Approximate
            s.y = random.nextFloat() * AndroidUtilities.displaySize.y; 
            s.alpha = random.nextFloat();
            stars.add(s);
        }
        
        lastTime = System.currentTimeMillis();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        // Re-scatter if size changes dramatically? No, just keep falling.
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        
        if (starBitmap == null) return;

        long now = System.currentTimeMillis();
        float dt = (now - lastTime) / 16.0f; 
        if (dt > 4) dt = 4;
        lastTime = now;

        int w = getWidth();
        int h = getHeight();
        int halfW = starBitmap.getWidth() / 2;
        int halfH = starBitmap.getHeight() / 2;

        for (Star star : stars) {
            // Logic
            star.y += star.speed * dt;
            star.rotation += star.rotationSpeed * dt;
            star.lifeTime += dt;

            // Fade in/out
            if (star.lifeTime < 20) {
                 star.alpha += 0.05f * dt;
                 if (star.alpha > 1f) star.alpha = 1f;
            } else if (star.lifeTime > star.maxLife - 20) {
                 star.alpha -= 0.05f * dt;
            } else {
                 // Twinkle
                 if (random.nextFloat() < 0.05f) {
                     star.alpha = 0.5f + random.nextFloat() * 0.5f;
                 }
            }

            if (star.alpha <= 0) star.alpha = 0;
            if (star.y > h + 50) {
                star.reset(w, h, random);
            }

            // Draw Glow (simulated by a circle behind)
            glowPaint.setColor(star.color);
            glowPaint.setAlpha((int) (star.alpha * 100)); // lighter alpha for glow
            float glowRadius = (halfW * star.scale) * 1.5f;
            canvas.drawCircle(star.x, star.y, glowRadius, glowPaint);

            // Draw Star
            starPaint.setColorFilter(new PorterDuffColorFilter(star.color, PorterDuff.Mode.SRC_IN));
            starPaint.setAlpha((int) (star.alpha * 255));

            canvas.save();
            canvas.translate(star.x, star.y);
            canvas.rotate(star.rotation);
            canvas.scale(star.scale, star.scale);
            canvas.drawBitmap(starBitmap, -halfW, -halfH, starPaint);
            canvas.restore();
        }

        invalidate();
    }
}
