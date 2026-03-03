package tw.nekomimi.nekogram.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.view.animation.OvershootInterpolator;

import java.util.ArrayList;
import java.util.Random;

/**
 * Premium animated splash screen for Alexgram.
 *
 * Animation sequence:
 * 1. Background gradient fades in
 * 2. The "A" letter draws itself with a stroke animation + scale overshoot
 * 3. A paper plane flies in from the upper-right along a Bézier curve with trailing particles
 * 4. Glow pulse radiates from the completed logo
 * 5. Everything fades out to reveal the app
 */
public class AlexgramSplashView extends View {

    // ── Timing constants (ms) ──
    private static final long TOTAL_DURATION    = 2200;
    private static final long BG_FADE_START     = 0;
    private static final long BG_FADE_END       = 200;
    private static final long A_DRAW_START      = 150;
    private static final long A_DRAW_END        = 850;
    private static final long PLANE_START       = 500;
    private static final long PLANE_END         = 1200;
    private static final long GLOW_START        = 1100;
    private static final long GLOW_END          = 1800;
    private static final long FADE_OUT_START    = 1700;
    private static final long FADE_OUT_END      = TOTAL_DURATION;

    // ── Colors ──
    private static final int COLOR_BG_TOP      = 0xFF5BC8F5;
    private static final int COLOR_BG_MID      = 0xFF2AABEE;
    private static final int COLOR_BG_BOTTOM   = 0xFF1565C0;
    private static final int COLOR_A_LIGHT     = 0xFF80D8FF;
    private static final int COLOR_A_MAIN      = 0xFF2196F3;
    private static final int COLOR_A_DARK      = 0xFF0D47A1;
    private static final int COLOR_PLANE_LIGHT = 0xFFE0F7FA;
    private static final int COLOR_PLANE_BODY  = 0xFF29B6F6;
    private static final int COLOR_PARTICLE    = 0xFFB3E5FC;

    // ── Paints ──
    private final Paint bgPaint       = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint aFillPaint    = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint aStrokePaint  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint planePaint    = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint     = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint particlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shadowPaint   = new Paint(Paint.ANTI_ALIAS_FLAG);

    // ── Pre-allocated interpolators (avoid per-frame GC) ──
    private final OvershootInterpolator overshootInterpolator = new OvershootInterpolator(2.0f);
    private final DecelerateInterpolator decelerateInterpolator = new DecelerateInterpolator(2.0f);

    // ── Pre-allocated arrays (avoid per-frame GC) ──
    private final float[] planePos = new float[2];
    private final float[] planeTan = new float[2];

    // ── Pre-allocated reusable path for stroke animation ──
    private final Path partialStrokePath = new Path();

    // ── Paths ──
    private Path aPath;
    private Path planePath;
    private Path planeFlightPath;
    private PathMeasure aPathMeasure;
    private PathMeasure flightPathMeasure;

    // ── Pre-computed shaders ──
    private LinearGradient aGradient;
    private LinearGradient planeGradient;

    // ── Particles ──
    private final ArrayList<Particle> particles = new ArrayList<>();
    private final Random random = new Random();

    // ── Animation state ──
    private float animProgress = 0f;
    private ValueAnimator mainAnimator;
    private boolean isFinished = false;
    private long lastFrameTimeNanos = 0;

    // ── Cached dimensions ──
    private int viewWidth, viewHeight;
    private float centerX, centerY;
    private float logoSize;
    private boolean pathsInitialized = false;

    // ── Callback ──
    private Runnable onFinishedCallback;

    public AlexgramSplashView(Context context) {
        super(context);
        init();
    }

    public void setOnFinishedCallback(Runnable callback) {
        this.onFinishedCallback = callback;
    }

    private void init() {
        setLayerType(LAYER_TYPE_HARDWARE, null);

        aStrokePaint.setStyle(Paint.Style.STROKE);
        aStrokePaint.setStrokeCap(Paint.Cap.ROUND);
        aStrokePaint.setStrokeJoin(Paint.Join.ROUND);
        aStrokePaint.setStrokeWidth(4f);
        aStrokePaint.setColor(COLOR_A_LIGHT);

        aFillPaint.setStyle(Paint.Style.FILL);

        planePaint.setStyle(Paint.Style.FILL);

        shadowPaint.setStyle(Paint.Style.FILL);
        shadowPaint.setColor(0x30000000);

        particlePaint.setStyle(Paint.Style.FILL);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w == 0 || h == 0) return;

        viewWidth = w;
        viewHeight = h;
        centerX = w / 2f;
        centerY = h / 2f;
        logoSize = Math.min(w, h) * 0.28f;

        initPaths();
        initShaders();

        if (mainAnimator == null) {
            startAnimation();
        }
    }

    private void initShaders() {
        // Background gradient
        LinearGradient bgGradient = new LinearGradient(
                0, 0, 0, viewHeight,
                new int[]{COLOR_BG_TOP, COLOR_BG_MID, COLOR_BG_BOTTOM},
                new float[]{0f, 0.45f, 1f},
                Shader.TileMode.CLAMP
        );
        bgPaint.setShader(bgGradient);

        // "A" fill gradient (pre-computed, constant)
        aGradient = new LinearGradient(
                centerX - logoSize * 0.5f, centerY - logoSize,
                centerX + logoSize * 0.5f, centerY + logoSize,
                new int[]{COLOR_A_LIGHT, COLOR_A_MAIN, COLOR_A_DARK},
                new float[]{0f, 0.4f, 1f},
                Shader.TileMode.CLAMP
        );

        // Plane body gradient (pre-computed, constant — coordinates are relative
        // to the plane's local space after canvas translate/rotate/scale)
        planeGradient = new LinearGradient(
                -logoSize * 0.2f, -logoSize * 0.3f,
                logoSize * 0.4f, logoSize * 0.2f,
                new int[]{COLOR_PLANE_LIGHT, COLOR_PLANE_BODY, Color.WHITE},
                new float[]{0f, 0.5f, 1f},
                Shader.TileMode.CLAMP
        );
    }

    private void initPaths() {
        // ── Build the stylized "A" letter path ──
        float s = logoSize;
        float cx = centerX;
        float cy = centerY;

        aPath = new Path();

        // Outer shape of the "A" — a stylized geometric A with sharp edges
        float topX = cx - s * 0.05f;
        float topY = cy - s * 1.0f;
        float leftBottomX = cx - s * 0.65f;
        float leftBottomY = cy + s * 0.75f;
        float rightBottomX = cx + s * 0.15f;
        float rightBottomY = cy + s * 0.75f;
        float rightMidX = cx + s * 0.05f;
        float rightMidY = cy + s * 0.15f;
        float leftMidX = cx - s * 0.35f;
        float leftMidY = cy + s * 0.15f;
        float innerTopX = cx - s * 0.05f;
        float innerTopY = cy - s * 0.45f;

        // Build the A shape
        aPath.moveTo(topX, topY);
        aPath.lineTo(cx + s * 0.12f, topY + s * 0.12f);
        aPath.lineTo(rightBottomX + s * 0.1f, rightBottomY);
        aPath.lineTo(rightMidX + s * 0.08f, rightMidY + s * 0.35f);
        aPath.lineTo(leftMidX + s * 0.1f, leftMidY + s * 0.35f);
        aPath.lineTo(leftBottomX, leftBottomY);
        aPath.close();

        // Cut out the inner triangle of the A
        Path innerCut = new Path();
        innerCut.moveTo(innerTopX, innerTopY);
        innerCut.lineTo(cx - s * 0.22f, cy + s * 0.1f);
        innerCut.lineTo(cx + s * 0.02f, cy + s * 0.1f);
        innerCut.close();

        aPath.op(innerCut, Path.Op.DIFFERENCE);

        // ── Build the paper plane path (in local coordinates, centered at origin) ──
        planePath = new Path();
        float planeS = s * 0.55f;

        float tipX = planeS * 1.0f;
        float tipY = -planeS * 0.6f;
        float lwX = -planeS * 0.3f;
        float lwY = planeS * 0.15f;
        float bfX = planeS * 0.1f;
        float bfY = planeS * 0.4f;
        float rwbX = planeS * 0.55f;
        float rwbY = planeS * 0.1f;
        float bcX = planeS * 0.15f;
        float bcY = -planeS * 0.05f;

        planePath.moveTo(tipX, tipY);
        planePath.lineTo(lwX, lwY);
        planePath.lineTo(bcX, bcY);
        planePath.lineTo(bfX, bfY);
        planePath.lineTo(rwbX, rwbY);
        planePath.close();

        // ── Build the flight path (Bézier curve from off-screen to final position) ──
        planeFlightPath = new Path();
        float planeRestX = cx + s * 0.25f;
        float planeRestY = cy - s * 0.15f;
        float startX = viewWidth + s;
        float startY = -s;

        planeFlightPath.moveTo(startX, startY);
        planeFlightPath.cubicTo(
                viewWidth * 0.7f, viewHeight * 0.15f,
                centerX + s * 1.5f, centerY - s * 0.8f,
                planeRestX, planeRestY
        );

        flightPathMeasure = new PathMeasure(planeFlightPath, false);

        // For the A stroke animation, use the outline path
        aPathMeasure = new PathMeasure(aPath, true);

        // ── Pre-allocate particle pool ──
        particles.clear();
        for (int i = 0; i < 35; i++) {
            particles.add(new Particle());
        }

        pathsInitialized = true;
    }

    private void startAnimation() {
        mainAnimator = ValueAnimator.ofFloat(0f, 1f);
        mainAnimator.setDuration(TOTAL_DURATION);
        mainAnimator.setInterpolator(new LinearInterpolator());
        mainAnimator.addUpdateListener(animation -> {
            animProgress = (float) animation.getAnimatedValue();
            invalidate();
        });
        mainAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                isFinished = true;
                if (onFinishedCallback != null) {
                    post(onFinishedCallback);
                }
            }
        });
        mainAnimator.start();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (!pathsInitialized || viewWidth == 0) return;

        long currentMs = (long) (animProgress * TOTAL_DURATION);

        // Compute real delta time for particle physics
        long nowNanos = System.nanoTime();
        float dt;
        if (lastFrameTimeNanos == 0) {
            dt = 0.016f; // 16ms default for first frame
        } else {
            dt = (nowNanos - lastFrameTimeNanos) / 1_000_000_000f;
            dt = Math.min(dt, 0.05f); // cap at 50ms to avoid physics explosions
        }
        lastFrameTimeNanos = nowNanos;

        // ── 1. Background ──
        drawBackground(canvas, currentMs);

        // ── 2. "A" letter ──
        drawALetter(canvas, currentMs);

        // ── 3. Paper plane ──
        drawPaperPlane(canvas, currentMs, dt);

        // ── 4. Particles ──
        drawParticles(canvas, currentMs, dt);

        // ── 5. Glow pulse ──
        drawGlow(canvas, currentMs);
    }

    // ═══════════════════════════════════════════════════════════
    //  PHASE 1 — Background gradient
    // ═══════════════════════════════════════════════════════════

    private void drawBackground(Canvas canvas, long ms) {
        float alpha = clampProgress(ms, BG_FADE_START, BG_FADE_END);
        // Keep background until fade out
        if (ms > FADE_OUT_START) {
            float fadeOut = 1f - clampProgress(ms, FADE_OUT_START, FADE_OUT_END);
            alpha = Math.min(alpha, fadeOut);
        }

        if (alpha <= 0.001f) return;

        // Use saveLayerAlpha for correct shader opacity
        int layerAlpha = (int) (alpha * 255);
        canvas.saveLayerAlpha(0, 0, viewWidth, viewHeight, layerAlpha);
        canvas.drawRect(0, 0, viewWidth, viewHeight, bgPaint);
        canvas.restore();
    }

    // ═══════════════════════════════════════════════════════════
    //  PHASE 2 — "A" letter drawing
    // ═══════════════════════════════════════════════════════════

    private void drawALetter(Canvas canvas, long ms) {
        if (ms < A_DRAW_START) return;

        float rawProgress = clampProgress(ms, A_DRAW_START, A_DRAW_END);

        // Overshoot for scale
        float scaleProgress = overshootInterpolator.getInterpolation(Math.min(rawProgress * 1.2f, 1f));
        float scale = 0.3f + 0.7f * scaleProgress;

        // Alpha
        float alpha = Math.min(rawProgress * 3f, 1f);

        // Fade out
        if (ms > FADE_OUT_START) {
            float fadeOut = 1f - clampProgress(ms, FADE_OUT_START, FADE_OUT_END);
            alpha *= fadeOut;
        }

        if (alpha <= 0.001f) return;

        canvas.save();
        canvas.translate(centerX, centerY);
        canvas.scale(scale, scale);
        canvas.translate(-centerX, -centerY);

        // Shadow beneath
        canvas.save();
        canvas.translate(logoSize * 0.03f, logoSize * 0.06f);
        shadowPaint.setAlpha((int) (alpha * 60));
        canvas.drawPath(aPath, shadowPaint);
        canvas.restore();

        // Fill with pre-computed gradient
        aFillPaint.setShader(aGradient);
        aFillPaint.setAlpha((int) (alpha * 255));
        canvas.drawPath(aPath, aFillPaint);

        // Stroke outline (edge glow) — draws progressively, fades as fill completes
        if (rawProgress < 1f) {
            float strokeAlpha = (1f - rawProgress) * alpha;
            aStrokePaint.setAlpha((int) (strokeAlpha * 255));
            aStrokePaint.setStrokeWidth(3f + (1f - rawProgress) * 4f);

            partialStrokePath.reset();
            float pathLength = aPathMeasure.getLength();
            aPathMeasure.getSegment(0, pathLength * rawProgress, partialStrokePath, true);
            canvas.drawPath(partialStrokePath, aStrokePaint);
        }

        canvas.restore();
    }

    // ═══════════════════════════════════════════════════════════
    //  PHASE 3 — Paper plane flight
    // ═══════════════════════════════════════════════════════════

    private void drawPaperPlane(Canvas canvas, long ms, float dt) {
        if (ms < PLANE_START) return;

        float rawProgress = clampProgress(ms, PLANE_START, PLANE_END);

        // Use decelerate for natural flight deceleration
        float flightProgress = decelerateInterpolator.getInterpolation(rawProgress);

        // Get position along the flight path (reuse pre-allocated arrays)
        float pathLen = flightPathMeasure.getLength();
        flightPathMeasure.getPosTan(pathLen * flightProgress, planePos, planeTan);

        float planeX = planePos[0];
        float planeY = planePos[1];

        // Rotation from tangent
        float angle = (float) Math.toDegrees(Math.atan2(planeTan[1], planeTan[0]));

        // Scale: plane starts bigger and shrinks to final size
        float planeScale = 1.0f + (1f - flightProgress) * 0.6f;

        // Alpha
        float alpha = Math.min(rawProgress * 4f, 1f);
        if (ms > FADE_OUT_START) {
            float fadeOut = 1f - clampProgress(ms, FADE_OUT_START, FADE_OUT_END);
            alpha *= fadeOut;
        }

        if (alpha <= 0.001f) return;

        // Spawn particles at plane position
        if (rawProgress < 0.9f && rawProgress > 0.05f) {
            for (int i = 0; i < particles.size(); i++) {
                Particle p = particles.get(i);
                if (!p.alive && random.nextFloat() < 0.15f) {
                    p.spawn(planeX, planeY, angle);
                }
            }
        }

        canvas.save();
        canvas.translate(planeX, planeY);
        canvas.rotate(angle);
        canvas.scale(planeScale, planeScale);

        // Shadow
        canvas.save();
        canvas.translate(2, 4);
        shadowPaint.setAlpha((int) (alpha * 50));
        canvas.drawPath(planePath, shadowPaint);
        canvas.restore();

        // Plane body with pre-computed gradient
        planePaint.setShader(planeGradient);
        planePaint.setAlpha((int) (alpha * 255));
        canvas.drawPath(planePath, planePaint);

        canvas.restore();
    }

    // ═══════════════════════════════════════════════════════════
    //  Particles trailing behind plane
    // ═══════════════════════════════════════════════════════════

    private void drawParticles(Canvas canvas, long ms, float dt) {
        if (ms < PLANE_START) return;

        float globalAlpha = 1f;
        if (ms > FADE_OUT_START) {
            globalAlpha = 1f - clampProgress(ms, FADE_OUT_START, FADE_OUT_END);
        }
        if (globalAlpha <= 0.001f) return;

        for (int i = 0; i < particles.size(); i++) {
            Particle p = particles.get(i);
            if (!p.alive) continue;

            p.update(dt);

            float pAlpha = p.alpha * globalAlpha;
            if (pAlpha <= 0.001f) continue;

            // Outer particle glow
            particlePaint.setColor(COLOR_PARTICLE);
            particlePaint.setAlpha((int) (pAlpha * 200));
            canvas.drawCircle(p.x, p.y, p.radius, particlePaint);

            // Inner bright core
            particlePaint.setColor(Color.WHITE);
            particlePaint.setAlpha((int) (pAlpha * 120));
            canvas.drawCircle(p.x, p.y, p.radius * 0.4f, particlePaint);
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  PHASE 4 — Glow pulse
    // ═══════════════════════════════════════════════════════════

    private void drawGlow(Canvas canvas, long ms) {
        if (ms < GLOW_START || ms > GLOW_END) return;

        float progress = clampProgress(ms, GLOW_START, GLOW_END);

        // Pulse: expand and fade
        float radius = logoSize * (0.8f + progress * 2.5f);
        float alpha = (1f - progress) * 0.5f;

        // Fade out overlap
        if (ms > FADE_OUT_START) {
            float fadeOut = 1f - clampProgress(ms, FADE_OUT_START, FADE_OUT_END);
            alpha *= fadeOut;
        }

        if (alpha <= 0.001f || radius <= 0) return;

        RadialGradient glowGrad = new RadialGradient(
                centerX, centerY,
                radius,
                new int[]{
                        Color.argb((int) (alpha * 255), 79, 195, 247),
                        Color.argb((int) (alpha * 150), 79, 195, 247),
                        Color.argb(0, 79, 195, 247)
                },
                new float[]{0f, 0.4f, 1f},
                Shader.TileMode.CLAMP
        );
        glowPaint.setShader(glowGrad);
        canvas.drawCircle(centerX, centerY, radius, glowPaint);
    }

    // ═══════════════════════════════════════════════════════════
    //  Utility
    // ═══════════════════════════════════════════════════════════

    private static float clampProgress(long currentMs, long startMs, long endMs) {
        if (currentMs <= startMs) return 0f;
        if (currentMs >= endMs) return 1f;
        return (float) (currentMs - startMs) / (float) (endMs - startMs);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (mainAnimator != null) {
            mainAnimator.cancel();
            mainAnimator = null;
        }
    }

    /**
     * Call to immediately finish and remove the splash.
     */
    public void finishSplash() {
        if (mainAnimator != null) {
            mainAnimator.cancel();
        }
        isFinished = true;
        animate().alpha(0f).setDuration(200).withEndAction(() -> {
            ViewGroup parent = (ViewGroup) getParent();
            if (parent != null) {
                parent.removeView(AlexgramSplashView.this);
            }
        }).start();
    }

    // ═══════════════════════════════════════════════════════════
    //  Particle class
    // ═══════════════════════════════════════════════════════════

    private class Particle {
        float x, y;
        float vx, vy;
        float radius;
        float alpha;
        float life;
        float maxLife;
        boolean alive = false;

        void spawn(float px, float py, float angleDeg) {
            alive = true;
            x = px;
            y = py;

            // Emit backwards from the plane direction with some spread
            float radians = (float) Math.toRadians(angleDeg + 180);
            float speed = 80f + random.nextFloat() * 120f;
            float spread = (random.nextFloat() - 0.5f) * 1.2f;
            vx = (float) (Math.cos(radians + spread) * speed);
            vy = (float) (Math.sin(radians + spread) * speed);

            radius = 2f + random.nextFloat() * 5f;
            maxLife = 0.5f + random.nextFloat() * 0.8f;
            life = 0f;
            alpha = 0.6f + random.nextFloat() * 0.4f;
        }

        void update(float dt) {
            if (!alive) return;

            life += dt;
            if (life >= maxLife) {
                alive = false;
                return;
            }

            x += vx * dt;
            y += vy * dt;

            // Slow down
            vx *= 0.96f;
            vy *= 0.96f;

            // Fade out
            float lifeRatio = life / maxLife;
            alpha = (1f - lifeRatio) * 0.8f;
            radius *= 0.995f;
        }
    }
}
