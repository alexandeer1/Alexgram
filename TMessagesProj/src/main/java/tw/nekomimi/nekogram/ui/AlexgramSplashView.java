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

import java.util.ArrayList;
import java.util.Random;

/**
 * Ultra-Premium, Seamless Alexgram Splash Screen.
 * Fast, fluid, and leaves no square icon edges — the animated parts BECOME the final logo.
 *
 * Sequence:
 * 1. Deep blue gradient + drifting ambient stars
 * 2. Neon beam traces the "A" very fast
 * 3. The "A" fills with a metallic gradient
 * 4. Paper plane swoops in from bottom-left to center-right
 * 5. Shockwave flash on impact
 * 6. The A and the Plane just stay on screen, perfectly forming the final logo
 * 7. Fast fade out
 */
public class AlexgramSplashView extends View {

    // ═══════════════ TIMING (ms) — Sped up significantly ═══════════════════
    private static final long TOTAL = 2200;

    private static final long AMBIENT_START = 0;
    private static final long AMBIENT_END   = 1800;

    // A letter tracing
    private static final long A_STROKE_S = 0;
    private static final long A_STROKE_E = 600;

    // A letter fill
    private static final long A_FILL_S = 400;
    private static final long A_FILL_E = 700;

    // Plane flight
    private static final long PLANE_S = 300;
    private static final long PLANE_E = 800;

    // Shockwave flash at merge
    private static final long FLASH_S = 750;
    private static final long FLASH_PEAK = 850;
    private static final long FLASH_E = 1100;

    // Post-impact glow & sparks
    private static final long GLOW_S = 800;
    private static final long GLOW_E = 1600;

    // Fade entire view out to app
    private static final long FADE_S = 1800;
    private static final long FADE_E = 2200;

    // ═══════════════ COLORS ═══════════════════
    private static final int BG_TOP = 0xFF061B3D;
    private static final int BG_MID = 0xFF0D3B6E;
    private static final int BG_BOT = 0xFF041430;

    private static final int NEON_CYAN = 0xFF00E5FF;
    private static final int A_FILL_LT = 0xFF4FC3F7;
    private static final int A_FILL_DK = 0xFF0D47A1;

    // The plane has two parts: left wing (darker) and right wing (lighter)
    private static final int PLANE_LT = 0xFFE0F7FA;
    private static final int PLANE_MID = 0xFF4FC3F7;
    private static final int PLANE_DK = 0xFF0288D1;

    private static final int GLOW_IN = 0xFF4FC3F7;

    // ═══════════════ PAINTS ═══════════════════
    private final Paint bgPaint    = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint aStroke    = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint aGlow      = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint aFill      = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint aShadow    = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint planeRt    = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint planeLt    = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint planeShad  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint flashPnt   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPnt    = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sparkPnt   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ringPnt    = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ambPnt     = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint trailPnt   = new Paint(Paint.ANTI_ALIAS_FLAG);

    // ═══════════════ CACHED ═══════════════════
    private final DecelerateInterpolator decI = new DecelerateInterpolator(2.0f);
    private final Path partP = new Path();
    private final float[] pPos = new float[2];
    private final float[] pTan = new float[2];
    private final float[] beamP = new float[2];

    // ═══════════════ PATHS ═══════════════════
    private Path aOutline, aBody;
    private Path planeRightWing, planeLeftWing;
    private Path flightPath;
    private PathMeasure aOutM, flightM;

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

    // Coordinates for the final resting place of the plane
    private float finalPlaneX, finalPlaneY, finalPlaneAngle;

    public AlexgramSplashView(Context c) {
        super(c);
        setLayerType(LAYER_TYPE_HARDWARE, null);

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
        aShadow.setColor(0x60000000); // dark drop shadow
        
        planeRt.setStyle(Paint.Style.FILL);
        planeLt.setStyle(Paint.Style.FILL);
        planeShad.setStyle(Paint.Style.FILL);
        planeShad.setColor(0x50000000);
        
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
        ls = Math.min(w, h) * 0.28f; // slightly larger logo base scale

        build();

        sparks.clear();
        for (int i = 0; i < 70; i++) sparks.add(new Spark());

        ambients.clear();
        for (int i = 0; i < 40; i++) {
            AmbientDot d = new AmbientDot();
            d.init(vw, vh, rng);
            ambients.add(d);
        }
        trailDots.clear();

        ok = true;
        if (ani == null) go();
    }

    // ══════════════════════════════════════════
    //  BUILD PATHS TO MATCH ACTUAL ICON EXACTLY
    // ══════════════════════════════════════════
    private void build() {
        float s = ls;

        // ── 1. The "A" BODY (Seamless, matching icon shape) ──
        // Center of the A
        float ax = cx;
        float ay = cy;

        // Let's build the A perfectly centered
        // It's a sharp A with a thick left leg and a crossbar
        aOutline = new Path();
        float hw = s * 0.14f; // stroke width

        // Apex
        float topX = ax, topY = ay - s * 0.7f;
        // Bottom left
        float blX = ax - s * 0.5f, blY = ay + s * 0.7f;
        // Bottom right
        float brX = ax + s * 0.45f, brY = ay + s * 0.7f;
        // Crossbar
        float midY = ay + s * 0.2f;

        aOutline.moveTo(blX, blY);
        aOutline.lineTo(topX, topY);
        aOutline.lineTo(brX, brY);
        aOutline.moveTo(ax - s * 0.25f, midY);
        aOutline.lineTo(ax + s * 0.22f, midY);
        aOutM = new PathMeasure(aOutline, false);

        // Filled A shape (with proper leg thickness)
        aBody = new Path();
        aBody.moveTo(topX, topY - hw); // sharp tip
        aBody.lineTo(topX + hw, topY + hw * 0.5f); // right side of apex
        aBody.lineTo(brX + hw, brY); // bottom right outer
        aBody.lineTo(brX - hw * 1.5f, brY); // bottom right inner
        
        // Go up to inner crossbar
        aBody.lineTo(ax + s * 0.1f, midY + hw);
        aBody.lineTo(ax - s * 0.15f, midY + hw);
        
        // Down to inside left leg
        aBody.lineTo(blX + hw * 1.2f, blY);
        aBody.lineTo(blX - hw * 0.8f, blY); // bottom left outer
        aBody.close();

        // Cutout the top triangle hole
        Path hole = new Path();
        hole.moveTo(topX, topY + hw * 2.5f); // inner apex
        hole.lineTo(ax - s * 0.22f, midY - hw); // left inner
        hole.lineTo(ax + s * 0.18f, midY - hw); // right inner
        hole.close();
        aBody.op(hole, Path.Op.DIFFERENCE);

        // ── 2. PLANE SHAPES (Seamless, matching icon) ──
        // We split the plane into left wing (dark) and right wing (light)
        // so it has the natural 3D paper plane look of the Alexgram logo.
        planeRightWing = new Path();
        planeLeftWing = new Path();
        
        float ps = s * 0.75f; // plane scale
        
        // Plane is mostly pointing to the right, slightly angled up
        // Local coordinates:
        float pTipX = ps * 0.9f, pTipY = -ps * 0.1f;
        float pTailX = -ps * 0.6f, pTailY = ps * 0.3f;
        float pTopWingX = -ps * 0.3f, pTopWingY = -ps * 0.45f;
        float pBotWingX = 0f, pBotWingY = ps * 0.5f;
        float pCenterFoldX = -ps * 0.3f, pCenterFoldY = 0f;

        // Right (upper/main) wing - lighter blue
        planeRightWing.moveTo(pTipX, pTipY);
        planeRightWing.lineTo(pTopWingX, pTopWingY);
        planeRightWing.lineTo(pCenterFoldX, pCenterFoldY);
        planeRightWing.lineTo(pTailX, pTailY);
        planeRightWing.close();

        // Left (lower/under) wing - darker blue
        planeLeftWing.moveTo(pTipX, pTipY);
        planeLeftWing.lineTo(pCenterFoldX, pCenterFoldY);
        planeLeftWing.lineTo(pBotWingX, pBotWingY);
        planeLeftWing.close();

        // Final plane position needs to overlay perfectly onto the "A"
        finalPlaneX = ax + s * 0.15f;
        finalPlaneY = ay + s * 0.05f;
        finalPlaneAngle = -15f; // slight upward tilt for the actual icon look

        // ── FLIGHT PATH ──
        flightPath = new Path();
        float startX = -s * 2.0f;
        float startY = vh + s;

        flightPath.moveTo(startX, startY);
        flightPath.cubicTo(
                vw * 0.2f, vh * 0.6f,       // CP1: sweep up left
                cx - s * 1.0f, cy + s * 0.5f, // CP2: approach horizontally
                finalPlaneX, finalPlaneY      // target
        );
        flightM = new PathMeasure(flightPath, false);

        // ── SHADERS ──
        bgPaint.setShader(new LinearGradient(0, 0, vw * 0.5f, vh,
                new int[]{BG_TOP, BG_MID, BG_BOT},
                new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));

        aFill.setShader(new LinearGradient(
                ax - s, ay - s, ax + s, ay + s,
                new int[]{A_FILL_LT, A_FILL_DK},
                null, Shader.TileMode.CLAMP));

        // The plane wings get exact solid gradients to match the icon
        planeRt.setShader(new LinearGradient(
                -ps, -ps, ps, ps,
                new int[]{PLANE_LT, PLANE_MID}, null, Shader.TileMode.CLAMP));
                
        planeLt.setShader(new LinearGradient(
                -ps*0.5f, 0, ps, ps,
                new int[]{PLANE_MID, PLANE_DK}, null, Shader.TileMode.CLAMP));
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
        if (m <= 0.001f) return;

        // 1. Background
        c.saveLayerAlpha(0, 0, vw, vh, (int) (m * 255));
        c.drawRect(0, 0, vw, vh, bgPaint);

        // 2. Ambient particles (drift slowly in bg)
        drawAmbient(c, ms, m, dt);

        // 3. "A" tracing beam
        drawAStroke(c, ms, m);

        // 4. "A" fill (stays on screen once fully drawn)
        drawAFill(c, ms, m);

        // 5. Plane flight (flies into place, then stays there)
        drawPlane(c, ms, m, dt);

        // 6. Flash and shockwave (only at merge impact)
        drawFlashAndShockwave(c, ms, m);

        // 7. Sparkle burst
        drawSparks(c, ms, m, dt);

        c.restore(); // end global alpha layer
    }

    // ══════════════════════════════════════════
    //  AMBIENT
    // ══════════════════════════════════════════
    private void drawAmbient(Canvas c, long ms, float m, float dt) {
        if (ms > AMBIENT_END) return;
        float a = m;
        if (ms > AMBIENT_END - 400) a *= cl(AMBIENT_END, ms, AMBIENT_END);

        for (int i = 0; i < ambients.size(); i++) {
            AmbientDot d = ambients.get(i);
            d.update(dt);
            float da = d.alpha * a;
            if (da < 0.01f) continue;
            
            ambPnt.setColor(NEON_CYAN);
            ambPnt.setAlpha((int) (da * 60));
            c.drawCircle(d.x, d.y, d.r * 2f, ambPnt);
            
            ambPnt.setColor(Color.WHITE);
            ambPnt.setAlpha((int) (da * 180));
            c.drawCircle(d.x, d.y, d.r, ambPnt);
        }
    }

    // ══════════════════════════════════════════
    //  "A" STROKE TRACING
    // ══════════════════════════════════════════
    private void drawAStroke(Canvas c, long ms, float alpha) {
        if (ms < A_STROKE_S || ms > A_STROKE_E + 200) return; // fades out after fill
        
        float p = cl(ms, A_STROKE_S, A_STROKE_E);
        
        // fade stroke out as solid body fades in
        float fadeOut = 1f;
        if (ms > A_FILL_S) fadeOut = 1f - cl(ms, A_FILL_S, A_FILL_E + 200);
        alpha *= fadeOut;
        if (alpha < 0.001f) return;

        float sw = 3f + ls * 0.03f;
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

            // Outer wide glow
            aGlow.setStrokeWidth(sw * 6f);
            aGlow.setAlpha((int) (alpha * 40));
            c.drawPath(partP, aGlow);

            // Core beam
            aStroke.setStrokeWidth(sw);
            aStroke.setAlpha((int) (alpha * 255));
            c.drawPath(partP, aStroke);

            // Leading bright tip
            if (seg < cLen && p < 1f) {
                pm.getPosTan(seg, beamP, null);
                sparkPnt.setColor(NEON_CYAN);
                sparkPnt.setAlpha((int) (alpha * 150));
                c.drawCircle(beamP[0], beamP[1], sw * 4f, sparkPnt);
                sparkPnt.setColor(Color.WHITE);
                sparkPnt.setAlpha((int) (alpha * 255));
                c.drawCircle(beamP[0], beamP[1], sw * 1.5f, sparkPnt);
            }
            drawn += cLen;
        } while (pm.nextContour());
        c.restore();
    }

    // ══════════════════════════════════════════
    //  "A" SOLID FILL (persists)
    // ══════════════════════════════════════════
    private void drawAFill(Canvas c, long ms, float m) {
        if (ms < A_FILL_S) return;
        float p = cl(ms, A_FILL_S, A_FILL_E);
        float a = p * m;
        
        // Slight pop-in scale
        float scale = 0.9f + 0.1f * decI.getInterpolation(p);

        c.save();
        c.translate(cx, cy);
        c.scale(scale, scale);
        c.translate(-cx, -cy);

        // Dark back shadow for depth against plane
        c.save();
        c.translate(0, ls * 0.06f);
        aShadow.setAlpha((int) (a * 150));
        c.drawPath(aBody, aShadow);
        c.restore();

        aFill.setAlpha((int) (a * 255));
        c.drawPath(aBody, aFill);
        
        c.restore();
    }

    // ══════════════════════════════════════════
    //  PLANE (flies in and parks in final logo spot)
    // ══════════════════════════════════════════
    private void drawPlane(Canvas c, long ms, float m, float dt) {
        if (ms < PLANE_S) return;
        
        float flightProg = cl(ms, PLANE_S, PLANE_E);
        float flight = decI.getInterpolation(flightProg);

        float px, py, angle, scale;
        
        if (flightProg < 1f) {
            // In flight
            float pathLen = flightM.getLength();
            flightM.getPosTan(pathLen * flight, pPos, pTan);
            px = pPos[0]; 
            py = pPos[1];
            
            // Raw tangent angle
            float pathAngle = (float) Math.toDegrees(Math.atan2(pTan[1], pTan[0]));
            
            // Smoothly lerp angle into final parking angle
            angle = pathAngle + (finalPlaneAngle - pathAngle) * flight;
            
            // Starts big, shrinks down perfectly into logo size
            scale = 1.6f - 0.6f * flight;
        } else {
            // Parked (is exactly the logo)
            px = finalPlaneX;
            py = finalPlaneY;
            angle = finalPlaneAngle;
            scale = 1.0f;
        }

        // Draw trail behind plane during flight only
        if (flightProg > 0.05f && flightProg < 0.98f) {
            trailDots.add(new float[]{px, py, 1f});
            for (int i = 0; i < sparks.size(); i++) {
                Spark sp = sparks.get(i);
                if (!sp.alive && rng.nextFloat() < 0.25f)
                    sp.spawnTrail(px, py, angle);
            }
        }
        
        for (int i = trailDots.size() - 1; i >= 0; i--) {
            float[] td = trailDots.get(i);
            td[2] *= 0.92f;
            if (td[2] < 0.02f) { trailDots.remove(i); continue; }
            float ta = td[2] * m;
            trailPnt.setColor(NEON_CYAN);
            trailPnt.setAlpha((int) (ta * 70));
            c.drawCircle(td[0], td[1], ls * 0.15f, trailPnt);
            trailPnt.setColor(Color.WHITE);
            trailPnt.setAlpha((int) (ta * 150));
            c.drawCircle(td[0], td[1], ls * 0.04f, trailPnt);
        }

        float a = Math.min(flightProg * 5f, 1f) * m;

        // Draw plane at correct position
        c.save();
        c.translate(px, py);
        c.rotate(angle);
        c.scale(scale, scale);

        // Big drop shadow under the plane makes it pop over the A
        c.save();
        c.translate(0, ls * 0.1f);
        planeShad.setAlpha((int) (a * 150));
        c.drawPath(planeRightWing, planeShad);
        c.drawPath(planeLeftWing, planeShad);
        c.restore();

        // Left wing (dark mode)
        planeLt.setAlpha((int) (a * 255));
        c.drawPath(planeLeftWing, planeLt);

        // Right wing (light mode)
        planeRt.setAlpha((int) (a * 255));
        c.drawPath(planeRightWing, planeRt);

        // Extra white glare on the tip while flying
        opacityIf(c, planeRt, Color.WHITE, (int) ( (1f - flightProg) * a * 150 ));
        c.drawPath(planeRightWing, planeRt);

        c.restore();
    }

    private void opacityIf(Canvas c, Paint p, int color, int alpha) {
        if (alpha <= 0) return;
        int oldC = p.getColor();
        Shader oldS = p.getShader();
        p.setShader(null);
        p.setColor(color);
        p.setAlpha(alpha);
        p.setStyle(Paint.Style.FILL);
    }

    // ══════════════════════════════════════════
    //  FLASH + SHOCKWAVE (Impact!)
    // ══════════════════════════════════════════
    private void drawFlashAndShockwave(Canvas c, long ms, float m) {
        if (ms < FLASH_S || ms > GLOW_E) return;

        float hitP = cl(ms, FLASH_S, FLASH_E);
        if (hitP > 0 && hitP < 1f) {
            float expand = decI.getInterpolation(hitP);
            float a = ms < FLASH_PEAK ? cl(ms, FLASH_S, FLASH_PEAK) : 1f - cl(ms, FLASH_PEAK, FLASH_E);
            
            // Center ring shockwave
            ringPnt.setColor(Color.WHITE);
            ringPnt.setAlpha((int) (a * m * 200));
            ringPnt.setStrokeWidth(5f * (1f - expand));
            c.drawCircle(finalPlaneX, finalPlaneY, ls * 0.5f + expand * ls * 3f, ringPnt);

            // Bright center blast
            float r = ls * (0.8f + expand * 2.5f);
            RadialGradient fg = new RadialGradient(finalPlaneX, finalPlaneY, Math.max(r, 1f),
                    new int[]{
                            Color.argb((int) (a * m * 255), 255, 255, 255),
                            Color.argb((int) (a * m * 150), 0, 229, 255),
                            Color.argb(0, 13, 71, 161)
                    },
                    new float[]{0f, 0.4f, 1f}, Shader.TileMode.CLAMP);
            flashPnt.setShader(fg);
            c.drawCircle(finalPlaneX, finalPlaneY, r, flashPnt);
        }

        // Ambient sustained glow post-impact
        if (ms > GLOW_S) {
            float gP = cl(ms, GLOW_S, GLOW_E);
            float a = (1f - gP) * m * 0.4f; // fades out slowly
            if (a > 0.01f) {
                float pulse = 1f + 0.05f * (float) Math.sin(gP * Math.PI * 6);
                float r = ls * 2.0f * pulse;
                RadialGradient g = new RadialGradient(cx, cy, r,
                        new int[]{
                                Color.argb((int) (a * 255), 79, 195, 247),
                                Color.argb(0, 13, 71, 161)
                        },
                        null, Shader.TileMode.CLAMP);
                glowPnt.setShader(g);
                c.drawCircle(cx, cy, r, glowPnt);
            }
        }
    }

    // ══════════════════════════════════════════
    //  SPARKLES
    // ══════════════════════════════════════════
    private void drawSparks(Canvas c, long ms, float m, float dt) {
        if (ms >= FLASH_S && ms < FLASH_S + 200) {
            for (int i = 0; i < sparks.size(); i++) {
                Spark sp = sparks.get(i);
                if (!sp.alive && rng.nextFloat() < 0.4f)
                    sp.spawnBurst(finalPlaneX, finalPlaneY, ls * 0.5f);
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

            sparkPnt.setColor(Color.WHITE);
            sparkPnt.setAlpha((int) (a * 255));
            c.drawRect(-r * 0.15f, -r, r * 0.15f, r, sparkPnt);
            c.drawRect(-r, -r * 0.15f, r, r * 0.15f, sparkPnt);

            sparkPnt.setColor(GLOW_IN);
            sparkPnt.setAlpha((int) (a * 100));
            c.drawCircle(0, 0, r * 1.8f, sparkPnt);

            c.restore();
        }
    }

    private static float cl(long ms, long s, long e) {
        if (ms <= s) return 0f;
        if (ms >= e) return 1f;
        return (float) (ms - s) / (float) (e - s);
    }

    @Override protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (ani != null) { ani.cancel(); ani = null; }
    }

    public void finishSplash() {
        if (ani != null) ani.cancel();
        animate().alpha(0f).setDuration(150).withEndAction(() -> {
            ViewGroup par = (ViewGroup) getParent();
            if (par != null) par.removeView(AlexgramSplashView.this);
        }).start();
    }

    private class Spark {
        float x, y, vx, vy, rad, alpha, rot, rs;
        float life, ml;
        boolean alive = false;

        void spawnTrail(float px, float py, float ang) {
            alive = true; x = px; y = py;
            float rd = (float) Math.toRadians(ang + 180);
            float sp = 60f + rng.nextFloat() * 80f;
            float sd = (rng.nextFloat() - 0.5f) * 1.5f;
            vx = (float) (Math.cos(rd + sd) * sp);
            vy = (float) (Math.sin(rd + sd) * sp);
            rad = 2.5f + rng.nextFloat() * 4.5f;
            ml = 0.25f + rng.nextFloat() * 0.35f;
            life = 0f; alpha = 1f;
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
            float sp = 120f + rng.nextFloat() * 200f;
            vx = (float) Math.cos(rd) * sp;
            vy = (float) Math.sin(rd) * sp;
            rad = 3.5f + rng.nextFloat() * 10f;
            ml = 0.4f + rng.nextFloat() * 0.6f;
            life = 0f; alpha = 1f;
            rot = rng.nextFloat() * 360f;
            rs = (rng.nextFloat() - 0.5f) * 200f;
        }

        void update(float dt) {
            if (!alive) return;
            life += dt;
            if (life >= ml) { alive = false; return; }
            x += vx * dt; y += vy * dt;
            vx *= 0.95f; vy *= 0.95f;
            rot += rs * dt;
            float r = life / ml;
            alpha = 1f - r;
            rad *= 0.99f;
        }
    }

    private static class AmbientDot {
        float x, y, r, alpha, speed, angle;
        void init(int vw, int vh, Random rng) {
            x = rng.nextFloat() * vw;
            y = rng.nextFloat() * vh;
            r = 1.5f + rng.nextFloat() * 3.0f;
            alpha = 0.2f + rng.nextFloat() * 0.8f;
            speed = 15f + rng.nextFloat() * 30f;
            angle = rng.nextFloat() * 360f;
        }
        void update(float dt) {
            x += Math.cos(Math.toRadians(angle)) * speed * dt;
            y += Math.sin(Math.toRadians(angle)) * speed * dt;
            angle += (Math.random() - 0.5) * 50 * dt;
        }
    }
}
