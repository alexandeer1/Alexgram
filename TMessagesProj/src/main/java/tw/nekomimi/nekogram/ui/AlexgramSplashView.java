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
 * Premium Alexgram splash screen.
 *
 * 1. Deep blue gradient + ambient floating particles
 * 2. "A" letter traces itself with a bright neon beam + glow halo
 * 3. "A" fills in with metallic gradient + shadow
 * 4. Paper plane swoops in from bottom-left curving to upper-right (matching icon)
 *    with a luminous trail and sparkles
 * 5. Merge: plane reaches A → shockwave rings + bright flash
 * 6. Flash reveals actual Alexgram icon with spring-bounce
 * 7. Icon breathes with a pulsing glow + floating sparkles
 * 8. Cinematic fade out
 */
public class AlexgramSplashView extends View {

    // ═══════════════ TIMING (ms) ═══════════════════
    private static final long TOTAL = 3400;

    // Background + ambient
    private static final long BG_END = 150;
    private static final long AMBIENT_START = 0;
    private static final long AMBIENT_END = 2800;

    // A letter stroke
    private static final long A_STROKE_S = 80;
    private static final long A_STROKE_E = 1050;

    // A letter fill
    private static final long A_FILL_S = 750;
    private static final long A_FILL_E = 1150;

    // Plane flight
    private static final long PLANE_S = 950;
    private static final long PLANE_E = 1700;

    // Flash + shockwave
    private static final long FLASH_S = 1580;
    private static final long FLASH_PEAK = 1700;
    private static final long FLASH_E = 1950;

    // Icon reveal
    private static final long ICON_S = 1650;
    private static final long ICON_E = 2050;

    // Glow + post-sparkles
    private static final long GLOW_S = 1750;
    private static final long GLOW_E = 2900;

    // Fade out
    private static final long FADE_S = 2800;
    private static final long FADE_E = TOTAL;

    // ═══════════════ COLORS ═══════════════════
    private static final int BG_TOP = 0xFF061B3D;
    private static final int BG_MID = 0xFF0D3B6E;
    private static final int BG_BOT = 0xFF041430;

    private static final int NEON_CYAN = 0xFF00E5FF;
    private static final int NEON_BLUE = 0xFF2979FF;
    private static final int A_FILL_LT = 0xFF4FC3F7;
    private static final int A_FILL_DK = 0xFF0D47A1;

    private static final int PLANE_W = 0xFFE0F7FA;
    private static final int PLANE_B = 0xFF29B6F6;

    private static final int GLOW_IN = 0xFF4FC3F7;
    private static final int SPARK_C = 0xFFB3E5FC;

    // ═══════════════ PAINTS ═══════════════════
    private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint aStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint aGlow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint aFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint aShadow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint planePnt = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint flashPnt = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPnt = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint iconPnt = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint sparkPnt = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ringPnt = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ambPnt = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint trailPnt = new Paint(Paint.ANTI_ALIAS_FLAG);

    // ═══════════════ CACHED ═══════════════════
    private final OvershootInterpolator overI = new OvershootInterpolator(2.8f);
    private final DecelerateInterpolator decI = new DecelerateInterpolator(2.5f);
    private final Path partP = new Path();
    private final float[] pPos = new float[2];
    private final float[] pTan = new float[2];
    private final float[] beamP = new float[2];

    // ═══════════════ PATHS ═══════════════════
    private Path aOutline, aBody, planeShape, flightPath;
    private PathMeasure aOutM, flightM;

    // ═══════════════ BITMAP ═══════════════════
    private Bitmap icon;
    private int icoSz;

    // ═══════════════ PARTICLES ═══════════════════
    private final ArrayList<Spark> sparks = new ArrayList<>();
    private final ArrayList<AmbientDot> ambients = new ArrayList<>();
    private final ArrayList<float[]> trailDots = new ArrayList<>();
    private final Random rng = new Random();

    // ═══════════════ STATE ═══════════════════
    private float prog = 0f;
    private ValueAnimator ani;
    private long lastNs = 0;
    private int vw, vh;
    private float cx, cy, ls;
    private boolean ok = false;
    private Runnable onDone;

    public AlexgramSplashView(Context c) {
        super(c);
        setLayerType(LAYER_TYPE_HARDWARE, null);

        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inPreferredConfig = Bitmap.Config.ARGB_8888;
        icon = BitmapFactory.decodeResource(c.getResources(),
                R.drawable.ic_launcher_alexgram_blue, o);

        aStroke.setStyle(Paint.Style.STROKE);
        aStroke.setStrokeCap(Paint.Cap.ROUND);
        aStroke.setStrokeJoin(Paint.Join.ROUND);
        aStroke.setColor(NEON_CYAN);

        aGlow.setStyle(Paint.Style.STROKE);
        aGlow.setStrokeCap(Paint.Cap.ROUND);
        aGlow.setStrokeJoin(Paint.Join.ROUND);
        aGlow.setColor(NEON_CYAN);
        try {
            aGlow.setMaskFilter(new android.graphics.BlurMaskFilter(
                    16f, android.graphics.BlurMaskFilter.Blur.NORMAL));
        } catch (Exception ignored) {}

        aFill.setStyle(Paint.Style.FILL);
        aShadow.setStyle(Paint.Style.FILL);
        aShadow.setColor(0x50000000);
        planePnt.setStyle(Paint.Style.FILL);
        flashPnt.setStyle(Paint.Style.FILL);
        sparkPnt.setStyle(Paint.Style.FILL);
        ambPnt.setStyle(Paint.Style.FILL);
        trailPnt.setStyle(Paint.Style.FILL);

        ringPnt.setStyle(Paint.Style.STROKE);
        ringPnt.setColor(NEON_CYAN);
    }

    public void setOnFinishedCallback(Runnable cb) { onDone = cb; }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        if (w == 0 || h == 0) return;
        vw = w; vh = h;
        cx = w / 2f; cy = h / 2f;
        ls = Math.min(w, h) * 0.22f;
        icoSz = (int) (Math.min(w, h) * 0.32f);

        if (icon != null && icoSz > 0)
            icon = Bitmap.createScaledBitmap(icon, icoSz, icoSz, true);

        build();

        sparks.clear();
        for (int i = 0; i < 70; i++) sparks.add(new Spark());

        ambients.clear();
        for (int i = 0; i < 30; i++) {
            AmbientDot d = new AmbientDot();
            d.init(vw, vh, rng);
            ambients.add(d);
        }

        trailDots.clear();

        ok = true;
        if (ani == null) go();
    }

    // ══════════════════════════════════════════
    //  BUILD PATHS
    // ══════════════════════════════════════════
    private void build() {
        float s = ls;

        // ── "A" OUTLINE (continuous stroke for beam tracing) ──
        aOutline = new Path();
        float blX = cx - s * 0.58f, blY = cy + s * 0.82f;
        float apX = cx, apY = cy - s * 0.98f;
        float brX = cx + s * 0.52f, brY = cy + s * 0.82f;
        float clX = cx - s * 0.30f, clY = cy + s * 0.10f;
        float crX = cx + s * 0.28f, crY = cy + s * 0.10f;

        // Left leg up to apex
        aOutline.moveTo(blX, blY);
        aOutline.lineTo(apX, apY);
        // Apex down right leg
        aOutline.lineTo(brX, brY);
        // Crossbar
        aOutline.moveTo(clX, clY);
        aOutline.lineTo(crX, crY);

        aOutM = new PathMeasure(aOutline, false);

        // ── "A" SOLID BODY (filled shape) ──
        aBody = new Path();
        float hw = s * 0.10f; // half-width of the letter strokes
        // Outer shape
        aBody.moveTo(apX, apY - hw * 0.3f);
        aBody.lineTo(apX + hw * 1.2f, apY + hw * 0.5f);
        aBody.lineTo(brX + hw, brY + hw * 0.3f);
        aBody.lineTo(brX - hw * 0.3f, brY + hw * 0.3f);
        aBody.lineTo(crX + hw * 0.5f, crY + hw * 1.5f);
        aBody.lineTo(clX - hw * 0.5f, clY + hw * 1.5f);
        aBody.lineTo(blX + hw * 0.3f, blY + hw * 0.3f);
        aBody.lineTo(blX - hw, blY + hw * 0.3f);
        aBody.close();

        // Inner cut (A hole)
        Path hole = new Path();
        hole.moveTo(apX, apY + s * 0.42f);
        hole.lineTo(cx - s * 0.17f, clY - s * 0.02f);
        hole.lineTo(cx + s * 0.15f, crY - s * 0.02f);
        hole.close();
        aBody.op(hole, Path.Op.DIFFERENCE);

        // ── PLANE SHAPE (pointing upper-right, matching icon) ──
        planeShape = new Path();
        float ps = s * 0.38f;
        // Tip pointing upper-right
        planeShape.moveTo(ps * 0.9f, -ps * 0.35f);    // tip
        planeShape.lineTo(-ps * 0.4f, -ps * 0.45f);    // top-left wing
        planeShape.lineTo(-ps * 0.1f, -ps * 0.05f);    // crease
        planeShape.lineTo(-ps * 0.4f, ps * 0.25f);     // bottom-left wing
        planeShape.lineTo(ps * 0.4f, ps * 0.0f);       // tail
        planeShape.close();

        // ── FLIGHT PATH: bottom-left → curving up to upper-right (matching icon direction) ──
        flightPath = new Path();
        float endX = cx + s * 0.12f;
        float endY = cy - s * 0.25f;
        float startX = -s * 2.0f;
        float startY = vh + s * 1.0f;

        flightPath.moveTo(startX, startY);
        flightPath.cubicTo(
                vw * 0.15f, vh * 0.55f,       // CP1: curves up through lower-left
                cx - s * 0.5f, cy - s * 1.5f,  // CP2: sweeps over the top
                endX, endY                      // lands at merge point
        );
        flightM = new PathMeasure(flightPath, false);

        // Shaders
        bgPaint.setShader(new LinearGradient(0, 0, vw * 0.3f, vh,
                new int[]{BG_TOP, BG_MID, BG_BOT},
                new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));

        aFill.setShader(new LinearGradient(
                cx - ls, cy - ls, cx + ls, cy + ls,
                new int[]{A_FILL_LT, A_FILL_DK, 0xFF0A3566},
                new float[]{0f, 0.6f, 1f}, Shader.TileMode.CLAMP));
    }

    // ══════════════════════════════════════════
    //  ANIM DRIVER
    // ══════════════════════════════════════════
    private void go() {
        ani = ValueAnimator.ofFloat(0f, 1f);
        ani.setDuration(TOTAL);
        ani.setInterpolator(new LinearInterpolator());
        ani.addUpdateListener(a -> { prog = (float) a.getAnimatedValue(); invalidate(); });
        ani.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator a) {
                if (onDone != null) post(onDone);
            }
        });
        ani.start();
    }

    // ══════════════════════════════════════════
    //  MAIN DRAW
    // ══════════════════════════════════════════
    @Override
    protected void onDraw(Canvas c) {
        if (!ok) return;
        long ms = (long) (prog * TOTAL);
        long ns = System.nanoTime();
        float dt = lastNs == 0 ? 0.016f : Math.min((ns - lastNs) / 1e9f, 0.05f);
        lastNs = ns;

        float m = ms > FADE_S ? 1f - cl(ms, FADE_S, FADE_E) : 1f;

        // 1. Background
        c.saveLayerAlpha(0, 0, vw, vh, (int) (Math.min(cl(ms, 0, BG_END), m) * 255));
        c.drawRect(0, 0, vw, vh, bgPaint);
        c.restore();

        // 2. Ambient floating particles
        drawAmbient(c, ms, m, dt);

        // Before flash: draw A and plane
        float hideP = cl(ms, FLASH_PEAK, FLASH_E);
        if (hideP < 1f) {
            drawAStroke(c, ms, m * (1f - hideP));
            drawAFill(c, ms, m * (1f - hideP));
            drawPlane(c, ms, m * (1f - hideP), dt);
        }

        // Flash + shockwave
        drawFlash(c, ms, m);
        drawShockwaves(c, ms, m);

        // Icon
        drawIcon(c, ms, m);

        // Glow
        drawGlow(c, ms, m);

        // Sparkles
        drawSparks(c, ms, m, dt);
    }

    // ══════════════════════════════════════════
    //  AMBIENT FLOATING DOTS (background life)
    // ══════════════════════════════════════════
    private void drawAmbient(Canvas c, long ms, float m, float dt) {
        if (ms < AMBIENT_START || ms > AMBIENT_END) return;
        float a = Math.min(cl(ms, AMBIENT_START, AMBIENT_START + 500), 1f) * m;
        if (ms > AMBIENT_END - 500) a *= cl(AMBIENT_END, ms, AMBIENT_END);

        for (int i = 0; i < ambients.size(); i++) {
            AmbientDot d = ambients.get(i);
            d.update(dt);
            float da = d.alpha * a;
            if (da < 0.01f) continue;
            ambPnt.setColor(NEON_CYAN);
            ambPnt.setAlpha((int) (da * 80));
            c.drawCircle(d.x, d.y, d.r * 2.5f, ambPnt);
            ambPnt.setColor(Color.WHITE);
            ambPnt.setAlpha((int) (da * 160));
            c.drawCircle(d.x, d.y, d.r, ambPnt);
        }
    }

    // ══════════════════════════════════════════
    //  "A" STROKE — neon beam traces the letter
    // ══════════════════════════════════════════
    private void drawAStroke(Canvas c, long ms, float alpha) {
        if (ms < A_STROKE_S || alpha < 0.001f) return;
        float p = cl(ms, A_STROKE_S, A_STROKE_E);
        float sw = 4f + ls * 0.05f;

        // Measure total path length
        PathMeasure pm = new PathMeasure(aOutline, false);
        float total = 0;
        do { total += pm.getLength(); } while (pm.nextContour());

        pm = new PathMeasure(aOutline, false);
        float drawn = 0, target = total * p;

        c.save();
        do {
            float cLen = pm.getLength();
            float seg = Math.min(target - drawn, cLen);
            if (seg <= 0) { drawn += cLen; continue; }

            partP.reset();
            pm.getSegment(0, seg, partP, true);

            // Wide outer glow
            aGlow.setStrokeWidth(sw * 5f);
            aGlow.setAlpha((int) (alpha * 35));
            c.drawPath(partP, aGlow);

            // Mid glow
            aGlow.setStrokeWidth(sw * 2.5f);
            aGlow.setAlpha((int) (alpha * 70));
            c.drawPath(partP, aGlow);

            // Core neon stroke
            aStroke.setStrokeWidth(sw);
            aStroke.setAlpha((int) (alpha * 255));
            c.drawPath(partP, aStroke);

            // Inner white core for extra brightness
            aStroke.setColor(Color.WHITE);
            aStroke.setStrokeWidth(sw * 0.4f);
            aStroke.setAlpha((int) (alpha * 180));
            c.drawPath(partP, aStroke);
            aStroke.setColor(NEON_CYAN);

            // Bright beam tip
            if (seg < cLen && p < 0.98f) {
                pm.getPosTan(seg, beamP, null);
                // Outer glow
                sparkPnt.setColor(NEON_CYAN);
                sparkPnt.setAlpha((int) (alpha * 100));
                c.drawCircle(beamP[0], beamP[1], sw * 6f, sparkPnt);
                // Inner bright
                sparkPnt.setColor(Color.WHITE);
                sparkPnt.setAlpha((int) (alpha * 255));
                c.drawCircle(beamP[0], beamP[1], sw * 2f, sparkPnt);

                // Spawn tiny particles at beam tip
                for (int i = 0; i < sparks.size(); i++) {
                    Spark sp = sparks.get(i);
                    if (!sp.alive && rng.nextFloat() < 0.08f) {
                        sp.spawnTiny(beamP[0], beamP[1]);
                    }
                }
            }

            drawn += cLen;
        } while (pm.nextContour());
        c.restore();
    }

    // ══════════════════════════════════════════
    //  "A" FILL — metallic gradient fill
    // ══════════════════════════════════════════
    private void drawAFill(Canvas c, long ms, float alpha) {
        if (ms < A_FILL_S || alpha < 0.001f) return;
        float p = cl(ms, A_FILL_S, A_FILL_E);
        float a = p * alpha;

        // Drop shadow
        c.save();
        c.translate(ls * 0.02f, ls * 0.05f);
        aShadow.setAlpha((int) (a * 100));
        c.drawPath(aBody, aShadow);
        c.restore();

        // Filled A
        aFill.setAlpha((int) (a * 255));
        c.drawPath(aBody, aFill);
    }

    // ══════════════════════════════════════════
    //  PLANE — flies from bottom-left to upper-right
    // ══════════════════════════════════════════
    private void drawPlane(Canvas c, long ms, float alpha, float dt) {
        if (ms < PLANE_S || alpha < 0.001f) return;
        float p = cl(ms, PLANE_S, PLANE_E);
        float flight = decI.getInterpolation(p);

        float pathLen = flightM.getLength();
        flightM.getPosTan(pathLen * flight, pPos, pTan);
        float px = pPos[0], py = pPos[1];
        float angle = (float) Math.toDegrees(Math.atan2(pTan[1], pTan[0]));
        float scale = 1.0f + (1f - flight) * 1.0f;
        float a = Math.min(p * 5f, 1f) * alpha;

        // ── Trail dots (glowing path behind plane) ──
        if (p > 0.02f && p < 0.95f) {
            trailDots.add(new float[]{px, py, 1f}); // x, y, alpha
        }
        // Draw trail
        for (int i = trailDots.size() - 1; i >= 0; i--) {
            float[] td = trailDots.get(i);
            td[2] *= 0.94f; // fade
            if (td[2] < 0.02f) { trailDots.remove(i); continue; }
            float ta = td[2] * alpha;
            trailPnt.setColor(NEON_CYAN);
            trailPnt.setAlpha((int) (ta * 50));
            c.drawCircle(td[0], td[1], ls * 0.12f, trailPnt);
            trailPnt.setColor(Color.WHITE);
            trailPnt.setAlpha((int) (ta * 120));
            c.drawCircle(td[0], td[1], ls * 0.03f, trailPnt);
        }

        // ── Spawn sparkle particles along trail ──
        if (p > 0.05f && p < 0.90f) {
            for (int i = 0; i < sparks.size(); i++) {
                Spark sp = sparks.get(i);
                if (!sp.alive && rng.nextFloat() < 0.20f)
                    sp.spawnTrail(px, py, angle);
            }
        }

        c.save();
        c.translate(px, py);
        c.rotate(angle);
        c.scale(scale, scale);

        // Outer glow
        planePnt.setShader(null);
        planePnt.setColor(NEON_CYAN);
        planePnt.setAlpha((int) (a * 30));
        c.save();
        c.scale(2.2f, 2.2f);
        c.drawPath(planeShape, planePnt);
        c.restore();

        // Mid glow
        planePnt.setAlpha((int) (a * 60));
        c.save();
        c.scale(1.5f, 1.5f);
        c.drawPath(planeShape, planePnt);
        c.restore();

        // Plane body
        planePnt.setShader(new LinearGradient(-ls * 0.2f, -ls * 0.15f,
                ls * 0.3f, ls * 0.1f,
                new int[]{PLANE_W, PLANE_B, Color.WHITE},
                null, Shader.TileMode.CLAMP));
        planePnt.setAlpha((int) (a * 255));
        c.drawPath(planeShape, planePnt);

        c.restore();
    }

    // ══════════════════════════════════════════
    //  FLASH — cinematic white burst
    // ══════════════════════════════════════════
    private void drawFlash(Canvas c, long ms, float m) {
        if (ms < FLASH_S || ms > FLASH_E) return;
        float a;
        float expand;
        if (ms < FLASH_PEAK) {
            float p = cl(ms, FLASH_S, FLASH_PEAK);
            a = p * p; // ease-in
            expand = p;
        } else {
            float p = cl(ms, FLASH_PEAK, FLASH_E);
            a = (1f - p) * (1f - p); // ease-out
            expand = 1f + p * 0.5f;
        }
        a *= m;
        float r = ls * (0.5f + expand * 4f);

        RadialGradient fg = new RadialGradient(cx, cy, Math.max(r, 1f),
                new int[]{
                        Color.argb((int) (a * 255), 255, 255, 255),
                        Color.argb((int) (a * 200), 0, 229, 255),
                        Color.argb((int) (a * 100), 41, 121, 255),
                        Color.argb(0, 13, 71, 161)
                },
                new float[]{0f, 0.2f, 0.5f, 1f}, Shader.TileMode.CLAMP);
        flashPnt.setShader(fg);
        c.drawCircle(cx, cy, r, flashPnt);
    }

    // ══════════════════════════════════════════
    //  SHOCKWAVE RINGS
    // ══════════════════════════════════════════
    private void drawShockwaves(Canvas c, long ms, float m) {
        for (int i = 0; i < 3; i++) {
            long rs = FLASH_S + i * 120L;
            long re = rs + 600L;
            if (ms < rs || ms > re) continue;
            float p = cl(ms, rs, re);
            float r = ls * 0.3f + p * ls * 3f;
            float a = (1f - p) * (1f - p) * m * 0.6f;
            if (a < 0.005f) continue;
            ringPnt.setAlpha((int) (a * 255));
            ringPnt.setStrokeWidth(4f - p * 3f);
            c.drawCircle(cx, cy, r, ringPnt);
        }
    }

    // ══════════════════════════════════════════
    //  ICON REVEAL
    // ══════════════════════════════════════════
    private void drawIcon(Canvas c, long ms, float m) {
        if (ms < ICON_S || icon == null) return;
        float p = cl(ms, ICON_S, ICON_E);
        float a = decI.getInterpolation(p) * m;
        float sc = overI.getInterpolation(Math.min(p * 1.05f, 1f));
        if (a < 0.001f || sc < 0.001f) return;

        float half = icoSz / 2f;
        c.save();
        c.translate(cx, cy);
        c.scale(sc, sc);

        // Icon shadow
        aShadow.setAlpha((int) (a * 60));
        c.drawRoundRect(-half + 3, -half + 6, half + 3, half + 6,
                icoSz * 0.15f, icoSz * 0.15f, aShadow);

        iconPnt.setAlpha((int) (a * 255));
        c.drawBitmap(icon, -half, -half, iconPnt);
        c.restore();
    }

    // ══════════════════════════════════════════
    //  GLOW HALO — breaths behind icon
    // ══════════════════════════════════════════
    private void drawGlow(Canvas c, long ms, float m) {
        if (ms < GLOW_S) return;
        float p = cl(ms, GLOW_S, GLOW_E);
        float fi = Math.min(p * 3f, 1f);
        float pulse = 1f + 0.07f * (float) Math.sin(p * Math.PI * 6);
        float r = icoSz * 0.75f * fi * pulse;
        float a = fi * m * 0.55f;
        if (a < 0.001f || r < 1f) return;

        RadialGradient g = new RadialGradient(cx, cy, r,
                new int[]{
                        Color.argb((int) (a * 140), 79, 195, 247),
                        Color.argb((int) (a * 50), 41, 121, 255),
                        Color.argb(0, 13, 71, 161)
                },
                new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP);
        glowPnt.setShader(g);
        c.drawCircle(cx, cy, r, glowPnt);
    }

    // ══════════════════════════════════════════
    //  SPARKLES
    // ══════════════════════════════════════════
    private void drawSparks(Canvas c, long ms, float m, float dt) {
        // Burst at flash
        if (ms >= FLASH_S && ms < FLASH_E + 300) {
            for (int i = 0; i < sparks.size(); i++) {
                Spark sp = sparks.get(i);
                if (!sp.alive && rng.nextFloat() < 0.30f)
                    sp.spawnBurst(cx, cy, icoSz * 0.3f);
            }
        }

        for (int i = 0; i < sparks.size(); i++) {
            Spark sp = sparks.get(i);
            if (!sp.alive) continue;
            sp.update(dt);
            float a = sp.alpha * m;
            if (a < 0.005f) continue;

            c.save();
            c.translate(sp.x, sp.y);
            c.rotate(sp.rot);
            float r = sp.rad;

            // 4-point star cross
            sparkPnt.setColor(Color.WHITE);
            sparkPnt.setAlpha((int) (a * 255));
            c.drawRect(-r * 0.1f, -r, r * 0.1f, r, sparkPnt);
            c.drawRect(-r, -r * 0.1f, r, r * 0.1f, sparkPnt);

            // Outer glow
            sparkPnt.setColor(GLOW_IN);
            sparkPnt.setAlpha((int) (a * 70));
            c.drawCircle(0, 0, r * 2f, sparkPnt);

            c.restore();
        }
    }

    // ══════════════════════════════════════════
    //  UTILITY
    // ══════════════════════════════════════════
    private static float cl(long ms, long s, long e) {
        if (ms <= s) return 0f;
        if (ms >= e) return 1f;
        return (float) (ms - s) / (float) (e - s);
    }

    @Override protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (ani != null) { ani.cancel(); ani = null; }
        if (icon != null && !icon.isRecycled()) { icon.recycle(); icon = null; }
    }

    public void finishSplash() {
        if (ani != null) ani.cancel();
        animate().alpha(0f).setDuration(200).withEndAction(() -> {
            ViewGroup par = (ViewGroup) getParent();
            if (par != null) par.removeView(AlexgramSplashView.this);
        }).start();
    }

    // ══════════════════════════════════════════
    //  SPARK PARTICLE
    // ══════════════════════════════════════════
    private class Spark {
        float x, y, vx, vy, rad, alpha, rot, rs;
        float life, ml;
        boolean alive = false;

        void spawnTrail(float px, float py, float ang) {
            alive = true; x = px; y = py;
            float rd = (float) Math.toRadians(ang + 180);
            float sp = 50f + rng.nextFloat() * 90f;
            float sd = (rng.nextFloat() - 0.5f) * 1.2f;
            vx = (float) (Math.cos(rd + sd) * sp);
            vy = (float) (Math.sin(rd + sd) * sp);
            rad = 2f + rng.nextFloat() * 5f;
            ml = 0.3f + rng.nextFloat() * 0.5f;
            life = 0f; alpha = 0.9f;
            rot = rng.nextFloat() * 360f;
            rs = (rng.nextFloat() - 0.5f) * 300f;
        }

        void spawnBurst(float ox, float oy, float dist) {
            alive = true;
            float a = rng.nextFloat() * 360f;
            float rd = (float) Math.toRadians(a);
            float d = dist + rng.nextFloat() * dist * 1.5f;
            x = ox + (float) Math.cos(rd) * d;
            y = oy + (float) Math.sin(rd) * d;
            float sp = 80f + rng.nextFloat() * 160f;
            vx = (float) Math.cos(rd) * sp;
            vy = (float) Math.sin(rd) * sp;
            rad = 3f + rng.nextFloat() * 8f;
            ml = 0.5f + rng.nextFloat() * 0.9f;
            life = 0f; alpha = 1f;
            rot = rng.nextFloat() * 360f;
            rs = (rng.nextFloat() - 0.5f) * 250f;
        }

        void spawnTiny(float px, float py) {
            alive = true; x = px; y = py;
            float a = rng.nextFloat() * 360f;
            float rd = (float) Math.toRadians(a);
            float sp = 30f + rng.nextFloat() * 60f;
            vx = (float) Math.cos(rd) * sp;
            vy = (float) Math.sin(rd) * sp;
            rad = 1f + rng.nextFloat() * 3f;
            ml = 0.2f + rng.nextFloat() * 0.3f;
            life = 0f; alpha = 0.7f;
            rot = rng.nextFloat() * 360f;
            rs = (rng.nextFloat() - 0.5f) * 400f;
        }

        void update(float dt) {
            if (!alive) return;
            life += dt;
            if (life >= ml) { alive = false; return; }
            x += vx * dt; y += vy * dt;
            vx *= 0.96f; vy *= 0.96f;
            rot += rs * dt;
            float r = life / ml;
            alpha = r < 0.1f ? (r / 0.1f) : (1f - (r - 0.1f) / 0.9f);
            rad *= 0.997f;
        }
    }

    // ══════════════════════════════════════════
    //  AMBIENT DOT (floating background particle)
    // ══════════════════════════════════════════
    private static class AmbientDot {
        float x, y, r, alpha, speed, angle;

        void init(int vw, int vh, Random rng) {
            x = rng.nextFloat() * vw;
            y = rng.nextFloat() * vh;
            r = 1f + rng.nextFloat() * 2.5f;
            alpha = 0.2f + rng.nextFloat() * 0.5f;
            speed = 8f + rng.nextFloat() * 20f;
            angle = rng.nextFloat() * 360f;
        }

        void update(float dt) {
            float rad = (float) Math.toRadians(angle);
            x += Math.cos(rad) * speed * dt;
            y += Math.sin(rad) * speed * dt;
            angle += (Math.random() - 0.5) * 30 * dt;
            // Gentle pulse
            alpha += (float) (Math.sin(System.nanoTime() / 500_000_000.0) * 0.01);
            alpha = Math.max(0.1f, Math.min(0.7f, alpha));
        }
    }
}
