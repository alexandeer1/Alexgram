package tw.nekomimi.nekogram.settings;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.view.View;
import android.view.animation.LinearInterpolator;

import java.util.ArrayList;
import java.util.Random;

public class AlexgramSettingsHeaderView extends View {

    private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint starPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint moonPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint moonGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shootPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ringPnt = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint aFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint planeRt = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint planeLt = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPnt = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPnt = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint nebulaPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    // Deep space gradient colors
    private static final int BG_TOP = 0xFF020810;
    private static final int BG_MID = 0xFF0B1528;
    private static final int BG_BOT = 0xFF000308;

    // Logo colors
    private static final int NEON_CYAN = 0xFF00E5FF;
    private static final int A_FILL_LT = 0xFF4FC3F7;
    private static final int A_FILL_DK = 0xFF0D47A1;
    private static final int PLANE_LT = 0xFFE0F7FA;
    private static final int PLANE_MID = 0xFF4FC3F7;
    private static final int PLANE_DK = 0xFF0288D1;

    private int vw, vh;
    private float cx, cy, ls;

    private Path aBody, planeRightWing, planeLeftWing;
    private float finalPlaneX, finalPlaneY, finalPlaneAngle;

    // Stars
    private final ArrayList<Star> stars = new ArrayList<>();
    // Shooting stars
    private final ArrayList<ShootingStar> shootingStars = new ArrayList<>();
    private final Random rng = new Random();

    private ValueAnimator ani;
    private long lastNs = 0;
    private boolean ok = false;
    private float shootTimer = 0f;

    public AlexgramSettingsHeaderView(Context context) {
        super(context);
        setLayerType(LAYER_TYPE_HARDWARE, null);

        aFill.setStyle(Paint.Style.FILL);
        planeRt.setStyle(Paint.Style.FILL);
        planeLt.setStyle(Paint.Style.FILL);
        starPaint.setStyle(Paint.Style.FILL);
        moonPaint.setStyle(Paint.Style.FILL);
        moonGlowPaint.setStyle(Paint.Style.FILL);
        shootPaint.setStyle(Paint.Style.STROKE);
        shootPaint.setStrokeCap(Paint.Cap.ROUND);
        ringPnt.setStyle(Paint.Style.STROKE);
        nebulaPaint.setStyle(Paint.Style.FILL);

        textPnt.setColor(Color.WHITE);
        textPnt.setFakeBoldText(true);
        textPnt.setTextAlign(Paint.Align.CENTER);
        textPnt.setAntiAlias(true);
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        if (w == 0 || h == 0) return;
        vw = w; vh = h;
        cx = w / 2f;
        cy = h * 0.40f;
        ls = Math.min(w, h) * 0.15f;

        buildLogo();

        // Create starfield
        stars.clear();
        for (int i = 0; i < 120; i++) {
            Star s = new Star();
            s.init(vw, vh, rng);
            stars.add(s);
        }

        ok = true;
        if (ani == null) go();
    }

    private void buildLogo() {
        float s = ls;
        float ax = cx, ay = cy;
        float hw = s * 0.14f;

        float topX = ax, topY = ay - s * 0.7f;
        float blX = ax - s * 0.5f, blY = ay + s * 0.7f;
        float brX = ax + s * 0.45f, brY = ay + s * 0.7f;
        float midY = ay + s * 0.2f;

        aBody = new Path();
        aBody.moveTo(topX, topY - hw);
        aBody.lineTo(topX + hw, topY + hw * 0.5f);
        aBody.lineTo(brX + hw, brY);
        aBody.lineTo(brX - hw * 1.5f, brY);
        aBody.lineTo(ax + s * 0.1f, midY + hw);
        aBody.lineTo(ax - s * 0.15f, midY + hw);
        aBody.lineTo(blX + hw * 1.2f, blY);
        aBody.lineTo(blX - hw * 0.8f, blY);
        aBody.close();

        Path hole = new Path();
        hole.moveTo(topX, topY + hw * 2.5f);
        hole.lineTo(ax - s * 0.22f, midY - hw);
        hole.lineTo(ax + s * 0.18f, midY - hw);
        hole.close();
        aBody.op(hole, Path.Op.DIFFERENCE);

        planeRightWing = new Path();
        planeLeftWing = new Path();

        float ps = s * 0.75f;
        float pTipX = ps * 0.9f, pTipY = -ps * 0.1f;
        float pTailX = -ps * 0.6f, pTailY = ps * 0.3f;
        float pTopWingX = -ps * 0.3f, pTopWingY = -ps * 0.45f;
        float pBotWingX = 0f, pBotWingY = ps * 0.5f;
        float pCenterFoldX = -ps * 0.3f, pCenterFoldY = 0f;

        planeRightWing.moveTo(pTipX, pTipY);
        planeRightWing.lineTo(pTopWingX, pTopWingY);
        planeRightWing.lineTo(pCenterFoldX, pCenterFoldY);
        planeRightWing.lineTo(pTailX, pTailY);
        planeRightWing.close();

        planeLeftWing.moveTo(pTipX, pTipY);
        planeLeftWing.lineTo(pCenterFoldX, pCenterFoldY);
        planeLeftWing.lineTo(pBotWingX, pBotWingY);
        planeLeftWing.close();

        finalPlaneX = ax + s * 0.15f;
        finalPlaneY = ay + s * 0.05f;
        finalPlaneAngle = -15f;

        if (vw > 0 && vh > 0) {
            bgPaint.setShader(new LinearGradient(0, 0, vw * 0.3f, vh,
                    new int[]{BG_TOP, BG_MID, BG_BOT},
                    new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));

            aFill.setShader(new LinearGradient(
                    ax - s, ay - s, ax + s, ay + s,
                    new int[]{A_FILL_LT, A_FILL_DK},
                    null, Shader.TileMode.CLAMP));

            planeRt.setShader(new LinearGradient(
                    -ps, -ps, ps, ps,
                    new int[]{PLANE_LT, PLANE_MID}, null, Shader.TileMode.CLAMP));

            planeLt.setShader(new LinearGradient(
                    -ps * 0.5f, 0, ps, ps,
                    new int[]{PLANE_MID, PLANE_DK}, null, Shader.TileMode.CLAMP));
        }
    }

    private void go() {
        ani = ValueAnimator.ofFloat(0f, 1f);
        ani.setDuration(2000);
        ani.setRepeatCount(ValueAnimator.INFINITE);
        ani.setInterpolator(new LinearInterpolator());
        ani.addUpdateListener(a -> invalidate());
        ani.start();
    }

    @Override
    protected void onDraw(Canvas c) {
        if (!ok) return;
        long ns = System.nanoTime();
        float dt = lastNs == 0 ? 0.016f : Math.min((ns - lastNs) / 1e9f, 0.05f);
        lastNs = ns;

        // 1. Deep space background
        c.drawRect(0, 0, vw, vh, bgPaint);

        // 2. Nebula clouds (subtle purple/blue patches)
        float time = (System.currentTimeMillis() % 10000) / 10000f;
        drawNebula(c, vw * 0.15f, vh * 0.25f, vw * 0.35f, 0x15, 0x60, 0x30, 0xC0, time);
        drawNebula(c, vw * 0.75f, vh * 0.15f, vw * 0.25f, 0x30, 0x20, 0x80, 0xA0, time + 0.3f);
        drawNebula(c, vw * 0.5f, vh * 0.7f, vw * 0.4f, 0x10, 0x40, 0x60, 0x80, time + 0.6f);

        // 3. Moon (top-right corner)
        float moonX = vw * 0.85f;
        float moonY = vh * 0.12f;
        float moonR = vw * 0.04f;

        // Moon glow (outer)
        float moonPulse = 1f + 0.08f * (float) Math.sin(time * Math.PI * 2);
        RadialGradient moonGlow = new RadialGradient(moonX, moonY, moonR * 5f * moonPulse,
                new int[]{
                        Color.argb(50, 255, 255, 220),
                        Color.argb(20, 255, 255, 200),
                        Color.argb(0, 255, 255, 180)
                },
                new float[]{0f, 0.4f, 1f}, Shader.TileMode.CLAMP);
        moonGlowPaint.setShader(moonGlow);
        c.drawCircle(moonX, moonY, moonR * 5f * moonPulse, moonGlowPaint);

        // Moon body
        moonPaint.setColor(0xFFF5F0E0);
        moonPaint.setShader(new RadialGradient(moonX - moonR * 0.3f, moonY - moonR * 0.3f, moonR,
                new int[]{0xFFFFFDE8, 0xFFE8E0C8},
                null, Shader.TileMode.CLAMP));
        c.drawCircle(moonX, moonY, moonR, moonPaint);

        // Moon craters (subtle)
        moonPaint.setShader(null);
        moonPaint.setColor(0x20000000);
        c.drawCircle(moonX + moonR * 0.2f, moonY - moonR * 0.15f, moonR * 0.15f, moonPaint);
        c.drawCircle(moonX - moonR * 0.25f, moonY + moonR * 0.3f, moonR * 0.1f, moonPaint);
        c.drawCircle(moonX + moonR * 0.35f, moonY + moonR * 0.25f, moonR * 0.08f, moonPaint);

        // 4. Stars twinkling
        for (int i = 0; i < stars.size(); i++) {
            Star s = stars.get(i);
            s.update(dt);

            starPaint.setColor(s.color);
            starPaint.setAlpha((int) (s.alpha * 255));
            c.drawCircle(s.x, s.y, s.r, starPaint);

            // Bright stars get a cross-flare
            if (s.r > 1.8f && s.alpha > 0.7f) {
                starPaint.setAlpha((int) (s.alpha * 80));
                c.drawLine(s.x - s.r * 3f, s.y, s.x + s.r * 3f, s.y, starPaint);
                c.drawLine(s.x, s.y - s.r * 3f, s.x, s.y + s.r * 3f, starPaint);
            }
        }

        // 5. Shooting stars
        shootTimer += dt;
        if (shootTimer > 1.5f + rng.nextFloat() * 2f) {
            shootTimer = 0f;
            ShootingStar ss = new ShootingStar();
            ss.init(vw, vh, rng);
            shootingStars.add(ss);
        }

        for (int i = shootingStars.size() - 1; i >= 0; i--) {
            ShootingStar ss = shootingStars.get(i);
            ss.update(dt);
            if (ss.dead) {
                shootingStars.remove(i);
                continue;
            }
            shootPaint.setColor(Color.WHITE);
            shootPaint.setAlpha((int) (ss.alpha * 255));
            shootPaint.setStrokeWidth(ss.width);
            c.drawLine(ss.x, ss.y, ss.tailX, ss.tailY, shootPaint);

            // Tiny glow at head
            starPaint.setColor(0x40FFFFFF);
            c.drawCircle(ss.x, ss.y, ss.width * 2f, starPaint);
        }

        // 6. Glowing backdrop behind logo
        float pulse = 1f + 0.05f * (float) Math.sin(time * Math.PI * 2);
        float r = ls * 2.2f * pulse;
        if (r > 0) {
            RadialGradient g = new RadialGradient(cx, cy, r,
                    new int[]{
                            Color.argb(100, 79, 195, 247),
                            Color.argb(30, 13, 71, 161),
                            Color.argb(0, 0, 0, 0)
                    },
                    new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP);
            glowPnt.setShader(g);
            c.drawCircle(cx, cy, r, glowPnt);
        }

        // 7. Ring around logo
        ringPnt.setColor(NEON_CYAN);
        ringPnt.setAlpha((int) (120 * pulse));
        ringPnt.setStrokeWidth(ls * 0.04f);
        c.drawCircle(cx, cy, ls * 1.5f, ringPnt);
        ringPnt.setColor(Color.WHITE);
        ringPnt.setAlpha(60);
        ringPnt.setStrokeWidth(ls * 0.015f);
        c.drawCircle(cx, cy, ls * 1.5f, ringPnt);

        // 8. A Logo Fill
        aFill.setAlpha(255);
        c.drawPath(aBody, aFill);

        // 9. Paper Plane
        c.save();
        c.translate(finalPlaneX, finalPlaneY);
        c.rotate(finalPlaneAngle);
        planeLt.setAlpha(255);
        c.drawPath(planeLeftWing, planeLt);
        planeRt.setAlpha(255);
        c.drawPath(planeRightWing, planeRt);
        c.restore();

        // 10. Text "A-Settings"
        textPnt.setTextSize(ls * 1.1f);
        c.drawText("A-Settings", cx, cy + ls * 2.5f, textPnt);
    }

    private void drawNebula(Canvas c, float nx, float ny, float nr, int r, int g, int b, int a, float phase) {
        float breathe = 1f + 0.1f * (float) Math.sin(phase * Math.PI * 2);
        float finalR = nr * breathe;
        if (finalR <= 0) return;
        int alphaOuter = Math.max(0, Math.min(255, (int) (a * 0.15f)));
        RadialGradient grad = new RadialGradient(nx, ny, finalR,
                new int[]{
                        Color.argb(alphaOuter, r, g, b),
                        Color.argb(alphaOuter / 2, r, g, b),
                        Color.argb(0, r, g, b)
                },
                new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP);
        nebulaPaint.setShader(grad);
        c.drawCircle(nx, ny, finalR, nebulaPaint);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (ani != null) { ani.cancel(); ani = null; }
    }

    // Twinkling star
    private static class Star {
        float x, y, r, alpha, twinkleSpeed, twinklePhase;
        int color;

        void init(int vw, int vh, Random rng) {
            x = rng.nextFloat() * vw;
            y = rng.nextFloat() * vh;
            r = 0.5f + rng.nextFloat() * 2.0f;
            alpha = 0.3f + rng.nextFloat() * 0.7f;
            twinkleSpeed = 0.5f + rng.nextFloat() * 2.5f;
            twinklePhase = rng.nextFloat() * (float) Math.PI * 2;

            // Mix of white, pale blue, and warm yellow stars
            float colorRng = rng.nextFloat();
            if (colorRng < 0.5f) {
                color = Color.WHITE;
            } else if (colorRng < 0.75f) {
                color = 0xFFCCDDFF; // pale blue
            } else if (colorRng < 0.9f) {
                color = 0xFFFFEECC; // warm yellow
            } else {
                color = 0xFFFFCCDD; // faint pink
            }
        }

        void update(float dt) {
            twinklePhase += twinkleSpeed * dt;
            alpha = 0.3f + 0.7f * (0.5f + 0.5f * (float) Math.sin(twinklePhase));
        }
    }

    // Falling/shooting star
    private static class ShootingStar {
        float x, y, tailX, tailY;
        float vx, vy;
        float alpha, life, maxLife, width;
        boolean dead;

        void init(int vw, int vh, Random rng) {
            // Start from upper portion, move diagonally down
            x = rng.nextFloat() * vw;
            y = rng.nextFloat() * vh * 0.3f;
            float angle = 30f + rng.nextFloat() * 30f; // 30-60 degrees
            float speed = 300f + rng.nextFloat() * 400f;
            vx = (float) Math.cos(Math.toRadians(angle)) * speed;
            vy = (float) Math.sin(Math.toRadians(angle)) * speed;
            alpha = 1f;
            life = 0;
            maxLife = 0.4f + rng.nextFloat() * 0.5f;
            width = 1f + rng.nextFloat() * 1.5f;
            dead = false;
            tailX = x;
            tailY = y;
        }

        void update(float dt) {
            life += dt;
            if (life >= maxLife) {
                dead = true;
                return;
            }
            tailX = x;
            tailY = y;
            x += vx * dt;
            y += vy * dt;
            float progress = life / maxLife;
            // Fade in for first 20%, then fade out
            if (progress < 0.2f) {
                alpha = progress / 0.2f;
            } else {
                alpha = 1f - ((progress - 0.2f) / 0.8f);
            }
            alpha = Math.max(0, Math.min(1, alpha));
        }
    }
}
