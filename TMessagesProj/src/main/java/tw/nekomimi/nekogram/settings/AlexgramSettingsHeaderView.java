package tw.nekomimi.nekogram.settings;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.view.View;
import android.view.animation.LinearInterpolator;

import org.telegram.ui.ActionBar.Theme;

import java.util.ArrayList;
import java.util.Random;

/**
 * Full-screen animated universe/sky background for A-Settings.
 * Dark mode: deep space with stars, nebulae, moon, shooting stars.
 * Light mode: soft gradient sky with drifting clouds and sun glow.
 */
public class AlexgramSettingsHeaderView extends View {

    private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shootPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private int vw, vh;
    private boolean isDark;

    private final ArrayList<Star> stars = new ArrayList<>();
    private final ArrayList<ShootingStar> shootingStars = new ArrayList<>();
    private final Random rng = new Random();

    private ValueAnimator ani;
    private long lastNs = 0;
    private boolean ok = false;
    private float shootTimer = 0f;

    public AlexgramSettingsHeaderView(Context context) {
        super(context);
        setLayerType(LAYER_TYPE_HARDWARE, null);
        dotPaint.setStyle(Paint.Style.FILL);
        glowPaint.setStyle(Paint.Style.FILL);
        shootPaint.setStyle(Paint.Style.STROKE);
        shootPaint.setStrokeCap(Paint.Cap.ROUND);
        isDark = Theme.getActiveTheme().isDark();
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        if (w == 0 || h == 0) return;
        vw = w; vh = h;
        isDark = Theme.getActiveTheme().isDark();
        buildBackground();

        stars.clear();
        int count = isDark ? 140 : 0;
        for (int i = 0; i < count; i++) {
            Star s = new Star();
            s.init(vw, vh, rng);
            stars.add(s);
        }

        ok = true;
        if (ani == null) go();
    }

    private void buildBackground() {
        if (isDark) {
            bgPaint.setShader(new LinearGradient(0, 0, vw * 0.3f, vh,
                    new int[]{0xFF020810, 0xFF0B1528, 0xFF000308},
                    new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
        } else {
            bgPaint.setShader(new LinearGradient(0, 0, vw * 0.5f, vh,
                    new int[]{0xFFE8F0FE, 0xFFC9DDFF, 0xFFE3ECFF},
                    new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
        }
    }

    private void go() {
        ani = ValueAnimator.ofFloat(0f, 1f);
        ani.setDuration(3000);
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

        // Background gradient
        c.drawRect(0, 0, vw, vh, bgPaint);

        float time = (System.currentTimeMillis() % 12000) / 12000f;

        if (isDark) {
            drawDarkTheme(c, dt, time);
        } else {
            drawLightTheme(c, dt, time);
        }
    }

    private void drawDarkTheme(Canvas c, float dt, float time) {
        // Nebula clouds
        drawNebula(c, vw * 0.12f, vh * 0.15f, vw * 0.45f, 0x18, 0x50, 0x30, 0xD0, time);
        drawNebula(c, vw * 0.78f, vh * 0.08f, vw * 0.30f, 0x30, 0x18, 0x80, 0xB0, time + 0.33f);
        drawNebula(c, vw * 0.45f, vh * 0.75f, vw * 0.50f, 0x10, 0x35, 0x55, 0x90, time + 0.66f);

        // Moon (top-right)
        float moonX = vw * 0.87f, moonY = vh * 0.06f;
        float moonR = vw * 0.035f;
        float mp = 1f + 0.06f * (float) Math.sin(time * Math.PI * 2);

        glowPaint.setShader(new RadialGradient(moonX, moonY, moonR * 6f * mp,
                new int[]{0x38FFFDE0, 0x18FFFFC8, 0x00FFFFB0},
                new float[]{0f, 0.4f, 1f}, Shader.TileMode.CLAMP));
        c.drawCircle(moonX, moonY, moonR * 6f * mp, glowPaint);

        dotPaint.setShader(new RadialGradient(moonX - moonR * 0.3f, moonY - moonR * 0.3f, moonR,
                new int[]{0xFFFFFDE8, 0xFFE8E0C8}, null, Shader.TileMode.CLAMP));
        c.drawCircle(moonX, moonY, moonR, dotPaint);
        dotPaint.setShader(null);
        dotPaint.setColor(0x18000000);
        c.drawCircle(moonX + moonR * 0.2f, moonY - moonR * 0.15f, moonR * 0.12f, dotPaint);
        c.drawCircle(moonX - moonR * 0.25f, moonY + moonR * 0.3f, moonR * 0.08f, dotPaint);

        // Twinkling stars
        for (int i = 0; i < stars.size(); i++) {
            Star s = stars.get(i);
            s.update(dt);
            dotPaint.setColor(s.color);
            dotPaint.setAlpha((int) (s.alpha * 255));
            c.drawCircle(s.x, s.y, s.r, dotPaint);
            if (s.r > 1.6f && s.alpha > 0.7f) {
                dotPaint.setAlpha((int) (s.alpha * 50));
                c.drawLine(s.x - s.r * 2.5f, s.y, s.x + s.r * 2.5f, s.y, dotPaint);
                c.drawLine(s.x, s.y - s.r * 2.5f, s.x, s.y + s.r * 2.5f, dotPaint);
            }
        }

        // Shooting stars
        shootTimer += dt;
        if (shootTimer > 2f + rng.nextFloat() * 3f) {
            shootTimer = 0f;
            ShootingStar ss = new ShootingStar();
            ss.init(vw, vh, rng);
            shootingStars.add(ss);
        }
        for (int i = shootingStars.size() - 1; i >= 0; i--) {
            ShootingStar ss = shootingStars.get(i);
            ss.update(dt);
            if (ss.dead) { shootingStars.remove(i); continue; }
            shootPaint.setColor(Color.WHITE);
            shootPaint.setAlpha((int) (ss.alpha * 200));
            shootPaint.setStrokeWidth(ss.width);
            c.drawLine(ss.x, ss.y, ss.tailX, ss.tailY, shootPaint);
        }
    }

    private void drawLightTheme(Canvas c, float dt, float time) {
        // Soft sun glow (top-left)
        float sunX = vw * 0.15f, sunY = vh * 0.05f;
        float sp = 1f + 0.04f * (float) Math.sin(time * Math.PI * 2);
        float sunR = vw * 0.25f * sp;
        glowPaint.setShader(new RadialGradient(sunX, sunY, sunR,
                new int[]{0x30FFD54F, 0x15FFECB3, 0x00FFF8E1},
                new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
        c.drawCircle(sunX, sunY, sunR, glowPaint);

        // Drifting faint cloud shapes
        float cloudPhase = time * (float) Math.PI * 2;
        drawCloud(c, vw * 0.3f + (float) Math.sin(cloudPhase) * vw * 0.02f, vh * 0.12f, vw * 0.18f, 0x12B0BEC5);
        drawCloud(c, vw * 0.7f + (float) Math.cos(cloudPhase * 0.7f) * vw * 0.015f, vh * 0.25f, vw * 0.14f, 0x0EB0BEC5);
        drawCloud(c, vw * 0.5f + (float) Math.sin(cloudPhase * 1.3f) * vw * 0.025f, vh * 0.55f, vw * 0.22f, 0x10B0BEC5);
    }

    private void drawCloud(Canvas c, float cx, float cy, float r, int color) {
        dotPaint.setColor(color);
        dotPaint.setShader(null);
        c.drawCircle(cx, cy, r, dotPaint);
        c.drawCircle(cx - r * 0.6f, cy + r * 0.15f, r * 0.7f, dotPaint);
        c.drawCircle(cx + r * 0.65f, cy + r * 0.1f, r * 0.65f, dotPaint);
    }

    private void drawNebula(Canvas c, float nx, float ny, float nr, int r, int g, int b, int a, float phase) {
        float breathe = 1f + 0.08f * (float) Math.sin(phase * Math.PI * 2);
        float fr = nr * breathe;
        if (fr <= 0) return;
        int ao = Math.max(0, Math.min(255, (int) (a * 0.12f)));
        glowPaint.setShader(new RadialGradient(nx, ny, fr,
                new int[]{Color.argb(ao, r, g, b), Color.argb(ao / 3, r, g, b), Color.argb(0, r, g, b)},
                new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
        c.drawCircle(nx, ny, fr, glowPaint);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (ani != null) { ani.cancel(); ani = null; }
    }

    private static class Star {
        float x, y, r, alpha, twinkleSpeed, twinklePhase;
        int color;
        void init(int vw, int vh, Random rng) {
            x = rng.nextFloat() * vw;
            y = rng.nextFloat() * vh;
            r = 0.4f + rng.nextFloat() * 1.8f;
            alpha = 0.2f + rng.nextFloat() * 0.8f;
            twinkleSpeed = 0.4f + rng.nextFloat() * 2.5f;
            twinklePhase = rng.nextFloat() * (float) Math.PI * 2;
            float cr = rng.nextFloat();
            if (cr < 0.55f) color = Color.WHITE;
            else if (cr < 0.75f) color = 0xFFCCDDFF;
            else if (cr < 0.9f) color = 0xFFFFEECC;
            else color = 0xFFFFCCDD;
        }
        void update(float dt) {
            twinklePhase += twinkleSpeed * dt;
            alpha = 0.2f + 0.8f * (0.5f + 0.5f * (float) Math.sin(twinklePhase));
        }
    }

    private static class ShootingStar {
        float x, y, tailX, tailY, vx, vy, alpha, life, maxLife, width;
        boolean dead;
        void init(int vw, int vh, Random rng) {
            x = rng.nextFloat() * vw;
            y = rng.nextFloat() * vh * 0.25f;
            float angle = 25f + rng.nextFloat() * 35f;
            float speed = 350f + rng.nextFloat() * 350f;
            vx = (float) Math.cos(Math.toRadians(angle)) * speed;
            vy = (float) Math.sin(Math.toRadians(angle)) * speed;
            alpha = 1f; life = 0; maxLife = 0.35f + rng.nextFloat() * 0.4f;
            width = 1f + rng.nextFloat() * 1.2f; dead = false; tailX = x; tailY = y;
        }
        void update(float dt) {
            life += dt;
            if (life >= maxLife) { dead = true; return; }
            tailX = x; tailY = y;
            x += vx * dt; y += vy * dt;
            float p = life / maxLife;
            alpha = p < 0.15f ? p / 0.15f : 1f - ((p - 0.15f) / 0.85f);
            alpha = Math.max(0, Math.min(1, alpha));
        }
    }
}
