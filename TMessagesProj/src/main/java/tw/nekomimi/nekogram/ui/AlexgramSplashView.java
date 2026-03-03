package tw.nekomimi.nekogram.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.view.animation.OvershootInterpolator;

import org.telegram.messenger.R;

import java.util.ArrayList;
import java.util.Random;

/**
 * Premium animated splash screen for Alexgram.
 * Uses the actual app icon bitmap with cinematic animations:
 *
 * Phase 1: Deep blue gradient background appears instantly
 * Phase 2: Rotating light rays radiate from center
 * Phase 3: Icon drops in with 3D-style spring bounce + rotation
 * Phase 4: Sparkle particles burst outward from icon
 * Phase 5: Shimmering light sweep passes across icon
 * Phase 6: Soft pulsing glow halo behind icon
 * Phase 7: Everything fades out smoothly
 */
public class AlexgramSplashView extends View {

    // ── Timing (ms) ──
    private static final long TOTAL_DURATION   = 2600;
    private static final long RAYS_START       = 0;
    private static final long RAYS_END         = 2200;
    private static final long ICON_START       = 100;
    private static final long ICON_SETTLE      = 700;
    private static final long SPARKLE_START    = 500;
    private static final long SPARKLE_END      = 1600;
    private static final long SHIMMER_START    = 700;
    private static final long SHIMMER_END      = 1400;
    private static final long GLOW_START       = 600;
    private static final long GLOW_END         = 2000;
    private static final long FADE_OUT_START   = 2000;
    private static final long FADE_OUT_END     = TOTAL_DURATION;

    // ── Color palette ──
    private static final int BG_COLOR_TOP     = 0xFF0A1628;
    private static final int BG_COLOR_MID     = 0xFF0F2847;
    private static final int BG_COLOR_BOTTOM  = 0xFF061224;
    private static final int RAY_COLOR        = 0xFF1E88E5;
    private static final int GLOW_COLOR_INNER = 0xFF4FC3F7;
    private static final int GLOW_COLOR_OUTER = 0xFF0D47A1;
    private static final int SHIMMER_COLOR    = 0xCCFFFFFF;

    // ── Paints ──
    private final Paint bgPaint       = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint iconPaint     = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint rayPaint      = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint     = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shimmerPaint  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint particlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shadowPaint   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ringPaint     = new Paint(Paint.ANTI_ALIAS_FLAG);

    // ── Interpolators (cached) ──
    private final OvershootInterpolator overshootInterp = new OvershootInterpolator(3.0f);
    private final DecelerateInterpolator decelInterp    = new DecelerateInterpolator(2.5f);

    // ── Icon bitmap ──
    private Bitmap iconBitmap;
    private int iconDrawSize;

    // ── Particles ──
    private final ArrayList<SparkleParticle> sparkles = new ArrayList<>();
    private final Random rng = new Random();

    // ── Light rays ──
    private static final int NUM_RAYS = 12;
    private final Path rayPath = new Path();

    // ── Animation ──
    private float animProgress = 0f;
    private ValueAnimator mainAnimator;
    private long lastFrameNanos = 0;

    // ── Dimensions ──
    private int vw, vh;
    private float cx, cy;
    private boolean initialized = false;

    // ── Callback ──
    private Runnable onFinishedCallback;

    public AlexgramSplashView(Context context) {
        super(context);
        init(context);
    }

    public void setOnFinishedCallback(Runnable cb) {
        this.onFinishedCallback = cb;
    }

    private void init(Context context) {
        setLayerType(LAYER_TYPE_HARDWARE, null);

        // Load the actual Alexgram icon at high res
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inPreferredConfig = Bitmap.Config.ARGB_8888;
        iconBitmap = BitmapFactory.decodeResource(context.getResources(),
                R.drawable.ic_launcher_alexgram_blue, opts);

        rayPaint.setStyle(Paint.Style.FILL);
        shadowPaint.setStyle(Paint.Style.FILL);
        shadowPaint.setColor(0x40000000);
        particlePaint.setStyle(Paint.Style.FILL);
        shimmerPaint.setStyle(Paint.Style.FILL);

        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeWidth(2f);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w == 0 || h == 0) return;

        vw = w;
        vh = h;
        cx = w / 2f;
        cy = h / 2f;

        iconDrawSize = (int) (Math.min(w, h) * 0.30f);

        // Background gradient
        bgPaint.setShader(new LinearGradient(0, 0, 0, vh,
                new int[]{BG_COLOR_TOP, BG_COLOR_MID, BG_COLOR_BOTTOM},
                new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));

        // Pre-scale icon bitmap once
        if (iconBitmap != null && iconDrawSize > 0) {
            iconBitmap = Bitmap.createScaledBitmap(iconBitmap, iconDrawSize, iconDrawSize, true);
        }

        // Init sparkle particles
        sparkles.clear();
        for (int i = 0; i < 50; i++) {
            sparkles.add(new SparkleParticle());
        }

        initRayPath();

        initialized = true;

        if (mainAnimator == null) {
            startAnimation();
        }
    }

    private void initRayPath() {
        // A single ray "wedge" shape — will be rotated for each ray
        float innerR = iconDrawSize * 0.3f;
        float outerR = Math.max(vw, vh) * 0.8f;
        float halfAngle = (float) Math.toRadians(6);

        rayPath.reset();
        rayPath.moveTo(innerR * (float) Math.cos(-halfAngle), innerR * (float) Math.sin(-halfAngle));
        rayPath.lineTo(outerR * (float) Math.cos(-halfAngle), outerR * (float) Math.sin(-halfAngle));
        rayPath.lineTo(outerR * (float) Math.cos(halfAngle), outerR * (float) Math.sin(halfAngle));
        rayPath.lineTo(innerR * (float) Math.cos(halfAngle), innerR * (float) Math.sin(halfAngle));
        rayPath.close();
    }

    private void startAnimation() {
        mainAnimator = ValueAnimator.ofFloat(0f, 1f);
        mainAnimator.setDuration(TOTAL_DURATION);
        mainAnimator.setInterpolator(new LinearInterpolator());
        mainAnimator.addUpdateListener(a -> {
            animProgress = (float) a.getAnimatedValue();
            invalidate();
        });
        mainAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (onFinishedCallback != null) {
                    post(onFinishedCallback);
                }
            }
        });
        mainAnimator.start();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (!initialized) return;

        long ms = (long) (animProgress * TOTAL_DURATION);

        // Real delta time
        long nowNanos = System.nanoTime();
        float dt;
        if (lastFrameNanos == 0) {
            dt = 0.016f;
        } else {
            dt = Math.min((nowNanos - lastFrameNanos) / 1_000_000_000f, 0.05f);
        }
        lastFrameNanos = nowNanos;

        float masterAlpha = 1f;
        if (ms > FADE_OUT_START) {
            masterAlpha = 1f - clamp01(ms, FADE_OUT_START, FADE_OUT_END);
        }

        // 1. Background
        canvas.saveLayerAlpha(0, 0, vw, vh, (int) (masterAlpha * 255));
        canvas.drawRect(0, 0, vw, vh, bgPaint);
        canvas.restore();

        // 2. Light rays
        drawRays(canvas, ms, masterAlpha);

        // 3. Glow halo behind icon
        drawGlow(canvas, ms, masterAlpha);

        // 4. Expanding rings
        drawRings(canvas, ms, masterAlpha);

        // 5. Icon
        drawIcon(canvas, ms, masterAlpha);

        // 6. Shimmer sweep
        drawShimmer(canvas, ms, masterAlpha);

        // 7. Sparkle particles
        drawSparkles(canvas, ms, masterAlpha, dt);
    }

    // ═══════════════════════════════════════════════════════════
    //  Light rays rotating behind the icon
    // ═══════════════════════════════════════════════════════════

    private void drawRays(Canvas canvas, long ms, float masterAlpha) {
        if (ms < RAYS_START) return;

        float progress = clamp01(ms, RAYS_START, RAYS_END);
        float fadeIn = Math.min(progress * 4f, 1f);
        float fadeOut = ms > FADE_OUT_START ? (1f - clamp01(ms, FADE_OUT_START, FADE_OUT_END)) : 1f;
        float alpha = fadeIn * fadeOut * masterAlpha * 0.12f;

        if (alpha <= 0.001f) return;

        // Slow rotation
        float rotation = progress * 90f;

        canvas.save();
        canvas.translate(cx, cy);
        canvas.rotate(rotation);

        rayPaint.setColor(RAY_COLOR);
        rayPaint.setAlpha((int) (alpha * 255));

        float angleStep = 360f / NUM_RAYS;
        for (int i = 0; i < NUM_RAYS; i++) {
            canvas.drawPath(rayPath, rayPaint);
            canvas.rotate(angleStep);
        }

        canvas.restore();
    }

    // ═══════════════════════════════════════════════════════════
    //  Pulsing glow halo
    // ═══════════════════════════════════════════════════════════

    private void drawGlow(Canvas canvas, long ms, float masterAlpha) {
        if (ms < GLOW_START) return;

        float progress = clamp01(ms, GLOW_START, GLOW_END);
        float fadeIn = decelInterp.getInterpolation(Math.min(progress * 2f, 1f));

        // Gentle pulse
        float pulse = 1f + 0.08f * (float) Math.sin(progress * Math.PI * 4);
        float radius = iconDrawSize * 0.9f * fadeIn * pulse;
        float alpha = fadeIn * masterAlpha * 0.7f;

        if (alpha <= 0.001f || radius <= 1f) return;

        RadialGradient glow = new RadialGradient(cx, cy, radius,
                new int[]{
                        Color.argb((int) (alpha * 180), 79, 195, 247),
                        Color.argb((int) (alpha * 80), 30, 136, 229),
                        Color.argb(0, 13, 71, 161)
                },
                new float[]{0f, 0.5f, 1f},
                Shader.TileMode.CLAMP);
        glowPaint.setShader(glow);
        canvas.drawCircle(cx, cy, radius, glowPaint);
    }

    // ═══════════════════════════════════════════════════════════
    //  Expanding rings
    // ═══════════════════════════════════════════════════════════

    private void drawRings(Canvas canvas, long ms, float masterAlpha) {
        // Draw 3 expanding rings at staggered times
        for (int i = 0; i < 3; i++) {
            long ringStart = ICON_SETTLE + i * 200L;
            long ringEnd = ringStart + 800L;
            if (ms < ringStart || ms > ringEnd) continue;

            float p = clamp01(ms, ringStart, ringEnd);
            float radius = iconDrawSize * 0.35f + p * iconDrawSize * 1.2f;
            float al = (1f - p) * masterAlpha * 0.35f;

            if (al <= 0.001f) continue;

            ringPaint.setColor(GLOW_COLOR_INNER);
            ringPaint.setAlpha((int) (al * 255));
            ringPaint.setStrokeWidth(3f - p * 2f);
            canvas.drawCircle(cx, cy, radius, ringPaint);
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  Icon with spring-bounce entrance
    // ═══════════════════════════════════════════════════════════

    private void drawIcon(Canvas canvas, long ms, float masterAlpha) {
        if (ms < ICON_START || iconBitmap == null) return;

        float progress = clamp01(ms, ICON_START, ICON_SETTLE);

        // Spring bounce scale
        float scaleP = overshootInterp.getInterpolation(progress);
        float scale = scaleP;

        // Subtle initial rotation that settles to 0
        float rotation = (1f - progress) * -15f;

        // Alpha (quick fade in)
        float alpha = Math.min(progress * 5f, 1f) * masterAlpha;

        if (alpha <= 0.001f || scale <= 0.001f) return;

        float halfSize = iconDrawSize / 2f;

        canvas.save();
        canvas.translate(cx, cy);
        canvas.rotate(rotation);
        canvas.scale(scale, scale);

        // Drop shadow
        shadowPaint.setAlpha((int) (alpha * 80));
        canvas.drawRoundRect(
                -halfSize + 4, -halfSize + 8,
                halfSize + 4, halfSize + 8,
                iconDrawSize * 0.18f, iconDrawSize * 0.18f,
                shadowPaint);

        // Draw icon
        iconPaint.setAlpha((int) (alpha * 255));
        canvas.drawBitmap(iconBitmap, -halfSize, -halfSize, iconPaint);

        canvas.restore();
    }

    // ═══════════════════════════════════════════════════════════
    //  Shimmer light sweep across icon
    // ═══════════════════════════════════════════════════════════

    private void drawShimmer(Canvas canvas, long ms, float masterAlpha) {
        if (ms < SHIMMER_START || ms > SHIMMER_END) return;

        float progress = clamp01(ms, SHIMMER_START, SHIMMER_END);

        // Shimmer band sweeps left-to-right across the icon
        float bandWidth = iconDrawSize * 0.5f;
        float halfIcon = iconDrawSize / 2f;
        float leftEdge = cx - halfIcon - bandWidth;
        float rightEdge = cx + halfIcon + bandWidth;
        float shimmerX = leftEdge + progress * (rightEdge - leftEdge);

        float alpha = masterAlpha * 0.6f;
        // Peak in the middle, fade at edges
        float peakFade = 1f - Math.abs(progress - 0.5f) * 2f;
        alpha *= peakFade;

        if (alpha <= 0.001f) return;

        // Clip to icon area
        canvas.save();
        float iconScale = overshootInterp.getInterpolation(
                clamp01(ms, ICON_START, ICON_SETTLE));
        if (iconScale <= 0.01f) {
            canvas.restore();
            return;
        }

        canvas.translate(cx, cy);
        canvas.scale(iconScale, iconScale);

        // Draw a shimmer gradient band
        LinearGradient shimmerGrad = new LinearGradient(
                shimmerX - cx - bandWidth / 2, 0,
                shimmerX - cx + bandWidth / 2, 0,
                new int[]{
                        Color.argb(0, 255, 255, 255),
                        Color.argb((int) (alpha * 200), 255, 255, 255),
                        Color.argb((int) (alpha * 255), 255, 255, 255),
                        Color.argb((int) (alpha * 200), 255, 255, 255),
                        Color.argb(0, 255, 255, 255)
                },
                new float[]{0f, 0.3f, 0.5f, 0.7f, 1f},
                Shader.TileMode.CLAMP);
        shimmerPaint.setShader(shimmerGrad);

        RectF iconRect = new RectF(-halfIcon, -halfIcon, halfIcon, halfIcon);
        float cornerR = iconDrawSize * 0.18f;
        canvas.drawRoundRect(iconRect, cornerR, cornerR, shimmerPaint);

        canvas.restore();
    }

    // ═══════════════════════════════════════════════════════════
    //  Sparkle particles
    // ═══════════════════════════════════════════════════════════

    private void drawSparkles(Canvas canvas, long ms, float masterAlpha, float dt) {
        if (ms < SPARKLE_START) return;

        float spawnProgress = clamp01(ms, SPARKLE_START, SPARKLE_END);

        // Spawn particles
        if (spawnProgress < 1f) {
            for (int i = 0; i < sparkles.size(); i++) {
                SparkleParticle sp = sparkles.get(i);
                if (!sp.alive && rng.nextFloat() < 0.12f) {
                    sp.spawn(cx, cy, iconDrawSize * 0.4f);
                }
            }
        }

        // Draw particles
        for (int i = 0; i < sparkles.size(); i++) {
            SparkleParticle sp = sparkles.get(i);
            if (!sp.alive) continue;

            sp.update(dt);
            float a = sp.alpha * masterAlpha;
            if (a <= 0.001f) continue;

            // 4-point star shape for premium sparkle
            drawStarSparkle(canvas, sp.x, sp.y, sp.radius, a, sp.rotation);
        }
    }

    private void drawStarSparkle(Canvas canvas, float x, float y, float r, float alpha, float rotation) {
        canvas.save();
        canvas.translate(x, y);
        canvas.rotate(rotation);

        // Outer glow
        particlePaint.setColor(GLOW_COLOR_INNER);
        particlePaint.setAlpha((int) (alpha * 80));
        canvas.drawCircle(0, 0, r * 2f, particlePaint);

        // Cross lines (star shape)
        particlePaint.setColor(Color.WHITE);
        particlePaint.setAlpha((int) (alpha * 255));
        // Vertical line
        canvas.drawRect(-r * 0.15f, -r, r * 0.15f, r, particlePaint);
        // Horizontal line
        canvas.drawRect(-r, -r * 0.15f, r, r * 0.15f, particlePaint);

        // Center bright dot
        particlePaint.setAlpha((int) (alpha * 255));
        canvas.drawCircle(0, 0, r * 0.3f, particlePaint);

        canvas.restore();
    }

    // ═══════════════════════════════════════════════════════════
    //  Utility
    // ═══════════════════════════════════════════════════════════

    private static float clamp01(long ms, long start, long end) {
        if (ms <= start) return 0f;
        if (ms >= end) return 1f;
        return (float) (ms - start) / (float) (end - start);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (mainAnimator != null) {
            mainAnimator.cancel();
            mainAnimator = null;
        }
        if (iconBitmap != null && !iconBitmap.isRecycled()) {
            iconBitmap.recycle();
            iconBitmap = null;
        }
    }

    public void finishSplash() {
        if (mainAnimator != null) mainAnimator.cancel();
        animate().alpha(0f).setDuration(200).withEndAction(() -> {
            ViewGroup parent = (ViewGroup) getParent();
            if (parent != null) parent.removeView(AlexgramSplashView.this);
        }).start();
    }

    // ═══════════════════════════════════════════════════════════
    //  Sparkle Particle
    // ═══════════════════════════════════════════════════════════

    private class SparkleParticle {
        float x, y, vx, vy;
        float radius, alpha, rotation, rotSpeed;
        float life, maxLife;
        boolean alive = false;

        void spawn(float originX, float originY, float spawnRadius) {
            alive = true;

            // Spawn in a ring around the icon
            float angle = rng.nextFloat() * 360f;
            float rad = (float) Math.toRadians(angle);
            float dist = spawnRadius + rng.nextFloat() * spawnRadius * 0.8f;
            x = originX + (float) Math.cos(rad) * dist;
            y = originY + (float) Math.sin(rad) * dist;

            // Move outward from center
            float speed = 40f + rng.nextFloat() * 100f;
            vx = (float) Math.cos(rad) * speed;
            vy = (float) Math.sin(rad) * speed;

            radius = 3f + rng.nextFloat() * 8f;
            maxLife = 0.6f + rng.nextFloat() * 1.0f;
            life = 0f;
            alpha = 0.7f + rng.nextFloat() * 0.3f;
            rotation = rng.nextFloat() * 360f;
            rotSpeed = (rng.nextFloat() - 0.5f) * 200f;
        }

        void update(float dt) {
            if (!alive) return;
            life += dt;
            if (life >= maxLife) { alive = false; return; }

            x += vx * dt;
            y += vy * dt;
            vx *= 0.97f;
            vy *= 0.97f;
            rotation += rotSpeed * dt;

            float lifeRatio = life / maxLife;
            // Fade in quickly, fade out slowly
            if (lifeRatio < 0.15f) {
                alpha = (lifeRatio / 0.15f) * 0.9f;
            } else {
                alpha = (1f - (lifeRatio - 0.15f) / 0.85f) * 0.9f;
            }
            radius *= 0.998f;
        }
    }
}
