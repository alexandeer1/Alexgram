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
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
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
 * Premium Alexgram splash screen animation.
 *
 * Sequence:
 * 1. Blue gradient background fades in
 * 2. The "A" letter draws itself with a glowing beam tracing its outline
 * 3. A paper plane flies in from the top-right along a curved path with sparkle trail
 * 4. The plane merges with the "A" — a bright flash occurs
 * 5. The flash reveals the actual Alexgram icon (bitmap)
 * 6. Icon pulses with a glow halo, sparkles radiate outward
 * 7. Smooth fade out to the app
 */
public class AlexgramSplashView extends View {

    // ═══════════════════════════════════════════════════════
    //  TIMING (ms)
    // ═══════════════════════════════════════════════════════
    private static final long TOTAL           = 3200;
    private static final long BG_END          = 200;
    // Phase: A letter stroke-draw
    private static final long A_STROKE_START  = 100;
    private static final long A_STROKE_END    = 1000;
    // Phase: A letter fill
    private static final long A_FILL_START    = 700;
    private static final long A_FILL_END      = 1100;
    // Phase: Paper plane flight
    private static final long PLANE_START     = 900;
    private static final long PLANE_END       = 1600;
    // Phase: Flash & merge -> reveal icon
    private static final long FLASH_START     = 1500;
    private static final long FLASH_PEAK      = 1650;
    private static final long FLASH_END       = 1900;
    // Phase: Icon reveal (actual bitmap)
    private static final long ICON_START      = 1600;
    private static final long ICON_END        = 2000;
    // Phase: Glow halo + sparkles
    private static final long GLOW_START      = 1700;
    private static final long GLOW_END        = 2700;
    // Phase: Fade out
    private static final long FADE_START      = 2600;
    private static final long FADE_END        = TOTAL;

    // ═══════════════════════════════════════════════════════
    //  COLORS
    // ═══════════════════════════════════════════════════════
    private static final int BG_TOP       = 0xFF0E4C92;
    private static final int BG_MID       = 0xFF1976D2;
    private static final int BG_BOTTOM    = 0xFF0D47A1;
    private static final int A_STROKE_CLR = 0xFF80D8FF;
    private static final int A_FILL_LIGHT = 0xFF4FC3F7;
    private static final int A_FILL_DARK  = 0xFF1565C0;
    private static final int PLANE_CLR    = 0xFFE0F7FA;
    private static final int FLASH_CLR    = 0xFFFFFFFF;
    private static final int GLOW_CLR     = 0xFF4FC3F7;
    private static final int PARTICLE_CLR = 0xFFB3E5FC;

    // ═══════════════════════════════════════════════════════
    //  PAINTS
    // ═══════════════════════════════════════════════════════
    private final Paint bgPaint      = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint aStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint aFillPaint   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint aGlowPaint   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint planePaint   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint flashPaint   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint    = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint iconPaint    = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint particlePnt  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint trailPaint   = new Paint(Paint.ANTI_ALIAS_FLAG);

    // ═══════════════════════════════════════════════════════
    //  CACHED OBJECTS
    // ═══════════════════════════════════════════════════════
    private final OvershootInterpolator overshoot = new OvershootInterpolator(2.5f);
    private final DecelerateInterpolator decel    = new DecelerateInterpolator(2.0f);
    private final Path partialPath = new Path();
    private final float[] planePos = new float[2];
    private final float[] planeTan = new float[2];
    private final float[] glowBeamPos = new float[2];

    // ═══════════════════════════════════════════════════════
    //  PATHS & MEASURES
    // ═══════════════════════════════════════════════════════
    private Path aOutlinePath;
    private Path aFillPath;
    private Path planeShape;
    private Path planeFlightPath;
    private PathMeasure aOutlineMeasure;
    private PathMeasure flightMeasure;

    // ═══════════════════════════════════════════════════════
    //  ICON BITMAP
    // ═══════════════════════════════════════════════════════
    private Bitmap iconBitmap;
    private int iconSize;

    // ═══════════════════════════════════════════════════════
    //  PARTICLES
    // ═══════════════════════════════════════════════════════
    private final ArrayList<Spark> sparks = new ArrayList<>();
    private final Random rng = new Random();

    // ═══════════════════════════════════════════════════════
    //  STATE
    // ═══════════════════════════════════════════════════════
    private float animP = 0f;
    private ValueAnimator anim;
    private long lastNanos = 0;
    private int vw, vh;
    private float cx, cy, logoS;
    private boolean ready = false;
    private Runnable onDone;

    public AlexgramSplashView(Context ctx) {
        super(ctx);
        setLayerType(LAYER_TYPE_HARDWARE, null);

        // Load icon bitmap
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inPreferredConfig = Bitmap.Config.ARGB_8888;
        iconBitmap = BitmapFactory.decodeResource(ctx.getResources(),
                R.drawable.ic_launcher_alexgram_blue, o);

        // Paint setup
        aStrokePaint.setStyle(Paint.Style.STROKE);
        aStrokePaint.setStrokeCap(Paint.Cap.ROUND);
        aStrokePaint.setStrokeJoin(Paint.Join.ROUND);
        aStrokePaint.setColor(A_STROKE_CLR);

        aGlowPaint.setStyle(Paint.Style.STROKE);
        aGlowPaint.setStrokeCap(Paint.Cap.ROUND);
        aGlowPaint.setStrokeJoin(Paint.Join.ROUND);
        aGlowPaint.setColor(A_STROKE_CLR);
        aGlowPaint.setMaskFilter(new android.graphics.BlurMaskFilter(
                12f, android.graphics.BlurMaskFilter.Blur.NORMAL));

        aFillPaint.setStyle(Paint.Style.FILL);
        planePaint.setStyle(Paint.Style.FILL);
        flashPaint.setStyle(Paint.Style.FILL);
        particlePnt.setStyle(Paint.Style.FILL);

        trailPaint.setStyle(Paint.Style.FILL);
        trailPaint.setColor(PLANE_CLR);
    }

    public void setOnFinishedCallback(Runnable cb) { this.onDone = cb; }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        if (w == 0 || h == 0) return;
        vw = w; vh = h;
        cx = w / 2f; cy = h / 2f;
        logoS = Math.min(w, h) * 0.22f;
        iconSize = (int)(Math.min(w, h) * 0.32f);

        // Scale icon bitmap
        if (iconBitmap != null && iconSize > 0) {
            iconBitmap = Bitmap.createScaledBitmap(iconBitmap, iconSize, iconSize, true);
        }

        buildPaths();
        buildShaders();

        sparks.clear();
        for (int i = 0; i < 60; i++) sparks.add(new Spark());

        ready = true;
        if (anim == null) startAnim();
    }

    // ═══════════════════════════════════════════════════════
    //  BUILD THE "A" PATH
    // ═══════════════════════════════════════════════════════
    private void buildPaths() {
        float s = logoS;

        // ── Stylized "A" outline as a single continuous stroke path ──
        // This traces the Alexgram "A" shape: two legs and a crossbar
        aOutlinePath = new Path();

        // Start from bottom-left leg, go up to apex, then down right leg
        float blX = cx - s * 0.58f, blY = cy + s * 0.80f;  // bottom-left
        float apX = cx,              apY = cy - s * 0.95f;  // apex (top)
        float brX = cx + s * 0.52f,  brY = cy + s * 0.80f;  // bottom-right

        // Crossbar endpoints
        float clX = cx - s * 0.30f, clY = cy + s * 0.12f;
        float crX = cx + s * 0.28f, crY = cy + s * 0.12f;

        // Trace: bottom-left → apex → bottom-right (the V of the A)
        aOutlinePath.moveTo(blX, blY);
        aOutlinePath.lineTo(apX, apY);
        aOutlinePath.lineTo(brX, brY);

        // Then crossbar: jump to crossbar left → crossbar right
        aOutlinePath.moveTo(clX, clY);
        aOutlinePath.lineTo(crX, crY);

        aOutlineMeasure = new PathMeasure(aOutlinePath, false);

        // ── Filled "A" path (solid shape for the fill phase) ──
        aFillPath = new Path();
        // Outer triangle
        aFillPath.moveTo(apX - s * 0.08f, apY);
        aFillPath.lineTo(apX + s * 0.08f, apY + s * 0.05f);
        aFillPath.lineTo(brX + s * 0.08f, brY);
        aFillPath.lineTo(brX - s * 0.08f, brY);
        aFillPath.lineTo(crX, crY + s * 0.12f);
        aFillPath.lineTo(clX, clY + s * 0.12f);
        aFillPath.lineTo(blX + s * 0.08f, blY);
        aFillPath.lineTo(blX - s * 0.08f, blY);
        aFillPath.close();

        // Cut inner triangle (the hole in the A)
        Path hole = new Path();
        hole.moveTo(apX, apY + s * 0.40f);
        hole.lineTo(cx - s * 0.18f, clY - s * 0.03f);
        hole.lineTo(cx + s * 0.16f, crY - s * 0.03f);
        hole.close();
        aFillPath.op(hole, Path.Op.DIFFERENCE);

        // ── Paper plane shape (in local coordinates) ──
        planeShape = new Path();
        float ps = s * 0.40f;
        // Sleek paper plane pointing right
        planeShape.moveTo(ps * 1.0f, 0);                    // tip
        planeShape.lineTo(-ps * 0.5f, -ps * 0.35f);         // top-left wing
        planeShape.lineTo(-ps * 0.15f, 0);                   // body crease
        planeShape.lineTo(-ps * 0.5f, ps * 0.35f);           // bottom-left wing
        planeShape.close();

        // Body fold line detail
        Path fold = new Path();
        fold.moveTo(ps * 1.0f, 0);
        fold.lineTo(-ps * 0.15f, 0);
        planeShape.addPath(fold);

        // ── Flight path: starts off-screen top-right, curves to merge position ──
        planeFlightPath = new Path();
        float endX = cx + s * 0.15f;
        float endY = cy - s * 0.20f;
        float startX = vw + s * 1.5f;
        float startY = -s * 1.5f;

        planeFlightPath.moveTo(startX, startY);
        // Sweeping curve
        planeFlightPath.cubicTo(
                vw * 0.65f, vh * 0.10f,     // control point 1
                cx + s * 2.0f, cy - s * 1.2f, // control point 2
                endX, endY                    // end at merge point
        );

        flightMeasure = new PathMeasure(planeFlightPath, false);
    }

    private void buildShaders() {
        bgPaint.setShader(new LinearGradient(0, 0, 0, vh,
                new int[]{BG_TOP, BG_MID, BG_BOTTOM},
                new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));

        aFillPaint.setShader(new LinearGradient(
                cx - logoS, cy - logoS, cx + logoS, cy + logoS,
                new int[]{A_FILL_LIGHT, A_FILL_DARK},
                null, Shader.TileMode.CLAMP));
    }

    // ═══════════════════════════════════════════════════════
    //  ANIMATION DRIVER
    // ═══════════════════════════════════════════════════════
    private void startAnim() {
        anim = ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(TOTAL);
        anim.setInterpolator(new LinearInterpolator());
        anim.addUpdateListener(a -> { animP = (float) a.getAnimatedValue(); invalidate(); });
        anim.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator a) {
                if (onDone != null) post(onDone);
            }
        });
        anim.start();
    }

    // ═══════════════════════════════════════════════════════
    //  DRAW
    // ═══════════════════════════════════════════════════════
    @Override
    protected void onDraw(Canvas c) {
        if (!ready) return;
        long ms = (long)(animP * TOTAL);

        long now = System.nanoTime();
        float dt = lastNanos == 0 ? 0.016f : Math.min((now - lastNanos) / 1e9f, 0.05f);
        lastNanos = now;

        float master = ms > FADE_START ? 1f - cl(ms, FADE_START, FADE_END) : 1f;

        // 1. Background
        c.saveLayerAlpha(0, 0, vw, vh, (int)(Math.min(cl(ms, 0, BG_END), master) * 255));
        c.drawRect(0, 0, vw, vh, bgPaint);
        c.restore();

        // 2–3. "A" letter (stroke then fill) — hidden after flash reveals icon
        float hideA = cl(ms, FLASH_PEAK, FLASH_END);
        if (hideA < 1f) {
            drawAStroke(c, ms, master * (1f - hideA));
            drawAFill(c, ms, master * (1f - hideA));
        }

        // 4. Paper plane — hidden after flash
        if (hideA < 1f) {
            drawPlane(c, ms, master * (1f - hideA), dt);
        }

        // 5. Flash burst
        drawFlash(c, ms, master);

        // 6. Actual icon (revealed after flash)
        drawIcon(c, ms, master);

        // 7. Glow + sparkles
        drawGlow(c, ms, master);
        drawSparkles(c, ms, master, dt);
    }

    // ═══════════════════════════════════════════════════════
    //  2. "A" STROKE TRACE — glowing beam traces the outline
    // ═══════════════════════════════════════════════════════
    private void drawAStroke(Canvas c, long ms, float alpha) {
        if (ms < A_STROKE_START || alpha <= 0.001f) return;

        float p = cl(ms, A_STROKE_START, A_STROKE_END);
        float strokeW = 5f + logoS * 0.04f;

        // Trace through all contours
        PathMeasure pm = new PathMeasure(aOutlinePath, false);
        float totalLen = 0;
        // Measure total length across all contours
        do { totalLen += pm.getLength(); } while (pm.nextContour());

        // Reset and draw
        pm = new PathMeasure(aOutlinePath, false);
        float drawn = 0;
        float targetLen = totalLen * p;

        c.save();

        do {
            float contourLen = pm.getLength();
            float segEnd = Math.min(targetLen - drawn, contourLen);
            if (segEnd <= 0) { drawn += contourLen; continue; }

            partialPath.reset();
            pm.getSegment(0, segEnd, partialPath, true);

            // Glow layer (wider, blurred)
            aGlowPaint.setStrokeWidth(strokeW * 3f);
            aGlowPaint.setAlpha((int)(alpha * 60));
            c.drawPath(partialPath, aGlowPaint);

            // Main stroke
            aStrokePaint.setStrokeWidth(strokeW);
            aStrokePaint.setAlpha((int)(alpha * 255));
            c.drawPath(partialPath, aStrokePaint);

            // Bright dot at the tip of the beam
            if (segEnd < contourLen && p < 1f) {
                pm.getPosTan(segEnd, glowBeamPos, null);
                particlePnt.setColor(Color.WHITE);
                particlePnt.setAlpha((int)(alpha * 255));
                c.drawCircle(glowBeamPos[0], glowBeamPos[1], strokeW * 1.5f, particlePnt);

                // Outer glow on tip
                particlePnt.setColor(A_STROKE_CLR);
                particlePnt.setAlpha((int)(alpha * 120));
                c.drawCircle(glowBeamPos[0], glowBeamPos[1], strokeW * 4f, particlePnt);
            }

            drawn += contourLen;
        } while (pm.nextContour());

        c.restore();
    }

    // ═══════════════════════════════════════════════════════
    //  3. "A" FILL — solid A fills in after stroke completes
    // ═══════════════════════════════════════════════════════
    private void drawAFill(Canvas c, long ms, float alpha) {
        if (ms < A_FILL_START || alpha <= 0.001f) return;

        float p = cl(ms, A_FILL_START, A_FILL_END);
        float a = p * alpha;

        aFillPaint.setAlpha((int)(a * 255));
        c.drawPath(aFillPath, aFillPaint);
    }

    // ═══════════════════════════════════════════════════════
    //  4. PAPER PLANE FLIGHT with sparkle trail
    // ═══════════════════════════════════════════════════════
    private void drawPlane(Canvas c, long ms, float alpha, float dt) {
        if (ms < PLANE_START || alpha <= 0.001f) return;

        float p = cl(ms, PLANE_START, PLANE_END);
        float flight = decel.getInterpolation(p);

        float pathLen = flightMeasure.getLength();
        flightMeasure.getPosTan(pathLen * flight, planePos, planeTan);

        float px = planePos[0], py = planePos[1];
        float angle = (float) Math.toDegrees(Math.atan2(planeTan[1], planeTan[0]));
        float scale = 1.2f + (1f - flight) * 0.8f;

        float a = Math.min(p * 6f, 1f) * alpha;

        // Spawn trail particles
        if (p > 0.03f && p < 0.92f) {
            for (int i = 0; i < sparks.size(); i++) {
                Spark sp = sparks.get(i);
                if (!sp.alive && rng.nextFloat() < 0.18f) {
                    sp.spawnTrail(px, py, angle);
                }
            }
        }

        c.save();
        c.translate(px, py);
        c.rotate(angle);
        c.scale(scale, scale);

        // Plane glow
        planePaint.setColor(A_STROKE_CLR);
        planePaint.setAlpha((int)(a * 40));
        planePaint.setShader(null);
        c.save();
        c.scale(1.8f, 1.8f);
        c.drawPath(planeShape, planePaint);
        c.restore();

        // Solid plane
        planePaint.setShader(new LinearGradient(-logoS * 0.2f, -logoS * 0.15f,
                logoS * 0.2f, logoS * 0.15f,
                new int[]{PLANE_CLR, 0xFFB3E5FC, Color.WHITE},
                null, Shader.TileMode.CLAMP));
        planePaint.setAlpha((int)(a * 255));
        c.drawPath(planeShape, planePaint);

        c.restore();
    }

    // ═══════════════════════════════════════════════════════
    //  5. FLASH — bright white burst when plane merges with A
    // ═══════════════════════════════════════════════════════
    private void drawFlash(Canvas c, long ms, float master) {
        if (ms < FLASH_START || ms > FLASH_END) return;

        float p;
        float a;
        if (ms < FLASH_PEAK) {
            p = cl(ms, FLASH_START, FLASH_PEAK);
            a = p;
        } else {
            p = cl(ms, FLASH_PEAK, FLASH_END);
            a = 1f - p;
        }
        a *= master;

        float radius = logoS * (0.5f + p * 3.5f);

        RadialGradient flGrad = new RadialGradient(cx, cy, Math.max(radius, 1f),
                new int[]{
                        Color.argb((int)(a * 255), 255, 255, 255),
                        Color.argb((int)(a * 180), 79, 195, 247),
                        Color.argb(0, 79, 195, 247)
                },
                new float[]{0f, 0.3f, 1f},
                Shader.TileMode.CLAMP);
        flashPaint.setShader(flGrad);
        c.drawCircle(cx, cy, radius, flashPaint);
    }

    // ═══════════════════════════════════════════════════════
    //  6. ACTUAL ICON — revealed after flash
    // ═══════════════════════════════════════════════════════
    private void drawIcon(Canvas c, long ms, float master) {
        if (ms < ICON_START || iconBitmap == null) return;

        float p = cl(ms, ICON_START, ICON_END);
        float a = decel.getInterpolation(p) * master;
        float scale = overshoot.getInterpolation(Math.min(p * 1.1f, 1f));

        if (a <= 0.001f || scale <= 0.001f) return;

        float half = iconSize / 2f;

        c.save();
        c.translate(cx, cy);
        c.scale(scale, scale);

        iconPaint.setAlpha((int)(a * 255));
        c.drawBitmap(iconBitmap, -half, -half, iconPaint);

        c.restore();
    }

    // ═══════════════════════════════════════════════════════
    //  7. GLOW HALO — soft pulsing glow behind the icon
    // ═══════════════════════════════════════════════════════
    private void drawGlow(Canvas c, long ms, float master) {
        if (ms < GLOW_START) return;

        float p = cl(ms, GLOW_START, GLOW_END);
        float fadeIn = Math.min(p * 3f, 1f);
        float pulse = 1f + 0.06f * (float) Math.sin(p * Math.PI * 5);
        float r = iconSize * 0.7f * fadeIn * pulse;
        float a = fadeIn * master * 0.5f;

        if (a <= 0.001f || r <= 1f) return;

        RadialGradient g = new RadialGradient(cx, cy, r,
                new int[]{
                        Color.argb((int)(a * 150), 79, 195, 247),
                        Color.argb((int)(a * 60), 30, 136, 229),
                        Color.argb(0, 13, 71, 161)
                },
                new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP);
        glowPaint.setShader(g);
        c.drawCircle(cx, cy, r, glowPaint);
    }

    // ═══════════════════════════════════════════════════════
    //  8. SPARKLE PARTICLES
    // ═══════════════════════════════════════════════════════
    private void drawSparkles(Canvas c, long ms, float master, float dt) {
        // Burst sparkles at flash time
        if (ms >= FLASH_START && ms < FLASH_END + 200) {
            for (int i = 0; i < sparks.size(); i++) {
                Spark sp = sparks.get(i);
                if (!sp.alive && rng.nextFloat() < 0.25f) {
                    sp.spawnBurst(cx, cy, iconSize * 0.3f);
                }
            }
        }

        for (int i = 0; i < sparks.size(); i++) {
            Spark sp = sparks.get(i);
            if (!sp.alive) continue;
            sp.update(dt);

            float a = sp.alpha * master;
            if (a <= 0.001f) continue;

            c.save();
            c.translate(sp.x, sp.y);
            c.rotate(sp.rotation);

            // Star cross
            particlePnt.setColor(Color.WHITE);
            particlePnt.setAlpha((int)(a * 255));
            float r = sp.radius;
            c.drawRect(-r * 0.12f, -r, r * 0.12f, r, particlePnt);
            c.drawRect(-r, -r * 0.12f, r, r * 0.12f, particlePnt);

            // Center glow
            particlePnt.setColor(GLOW_CLR);
            particlePnt.setAlpha((int)(a * 100));
            c.drawCircle(0, 0, r * 1.5f, particlePnt);

            c.restore();
        }
    }

    // ═══════════════════════════════════════════════════════
    //  UTILITY
    // ═══════════════════════════════════════════════════════
    private static float cl(long ms, long s, long e) {
        if (ms <= s) return 0f;
        if (ms >= e) return 1f;
        return (float)(ms - s) / (float)(e - s);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (anim != null) { anim.cancel(); anim = null; }
        if (iconBitmap != null && !iconBitmap.isRecycled()) {
            iconBitmap.recycle();
            iconBitmap = null;
        }
    }

    public void finishSplash() {
        if (anim != null) anim.cancel();
        animate().alpha(0f).setDuration(200).withEndAction(() -> {
            ViewGroup par = (ViewGroup) getParent();
            if (par != null) par.removeView(AlexgramSplashView.this);
        }).start();
    }

    // ═══════════════════════════════════════════════════════
    //  SPARK PARTICLE
    // ═══════════════════════════════════════════════════════
    private class Spark {
        float x, y, vx, vy, radius, alpha, rotation, rotSpd;
        float life, maxLife;
        boolean alive = false;

        void spawnTrail(float px, float py, float angleDeg) {
            alive = true;
            x = px; y = py;
            float rad = (float) Math.toRadians(angleDeg + 180);
            float spd = 60f + rng.nextFloat() * 100f;
            float spread = (rng.nextFloat() - 0.5f) * 1.0f;
            vx = (float)(Math.cos(rad + spread) * spd);
            vy = (float)(Math.sin(rad + spread) * spd);
            radius = 2f + rng.nextFloat() * 4f;
            maxLife = 0.3f + rng.nextFloat() * 0.5f;
            life = 0f;
            alpha = 0.8f;
            rotation = rng.nextFloat() * 360f;
            rotSpd = (rng.nextFloat() - 0.5f) * 300f;
        }

        void spawnBurst(float ox, float oy, float dist) {
            alive = true;
            float ang = rng.nextFloat() * 360f;
            float rd = (float) Math.toRadians(ang);
            float d = dist + rng.nextFloat() * dist;
            x = ox + (float) Math.cos(rd) * d;
            y = oy + (float) Math.sin(rd) * d;
            float spd = 60f + rng.nextFloat() * 140f;
            vx = (float) Math.cos(rd) * spd;
            vy = (float) Math.sin(rd) * spd;
            radius = 3f + rng.nextFloat() * 7f;
            maxLife = 0.5f + rng.nextFloat() * 0.8f;
            life = 0f;
            alpha = 0.9f;
            rotation = rng.nextFloat() * 360f;
            rotSpd = (rng.nextFloat() - 0.5f) * 200f;
        }

        void update(float dt) {
            if (!alive) return;
            life += dt;
            if (life >= maxLife) { alive = false; return; }
            x += vx * dt; y += vy * dt;
            vx *= 0.96f; vy *= 0.96f;
            rotation += rotSpd * dt;
            float ratio = life / maxLife;
            alpha = ratio < 0.1f ? (ratio / 0.1f) * 0.9f : (1f - (ratio - 0.1f) / 0.9f) * 0.9f;
            radius *= 0.998f;
        }
    }
}
