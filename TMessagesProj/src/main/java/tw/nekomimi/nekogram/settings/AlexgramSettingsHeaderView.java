package tw.nekomimi.nekogram.settings;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.view.View;
import android.view.animation.LinearInterpolator;

import org.telegram.ui.ActionBar.Theme;

import java.util.ArrayList;
import java.util.Random;

/**
 * Full-screen animated universe/sky background for A-Settings.
 * Heavily optimized: no object allocation or shader creation in onDraw.
 */
public class AlexgramSettingsHeaderView extends View {

    private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shootPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sunRayPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private int vw, vh;
    private boolean isDark;

    private final ArrayList<Star> stars = new ArrayList<>();
    private final ArrayList<ShootingStar> shootingStars = new ArrayList<>();
    private final ArrayList<Bird> birds = new ArrayList<>();
    private final Random rng = new Random();

    private ValueAnimator ani;
    private long lastNs = 0;
    private boolean ok = false;
    private float shootTimer = 0f;
    private float birdTimer = 0f;

    // Precomputed Shaders for Performance
    private Shader sunGlowShader;
    private Shader moonGlowShader;
    private Shader moonBodyShader;
    private Shader nebula1Shader, nebula2Shader, nebula3Shader;

    // Cache locations
    private float sunX, sunY, sunR;
    private float moonX, moonY, moonR;
    private float n1X, n1Y, n1R;
    private float n2X, n2Y, n2R;
    private float n3X, n3Y, n3R;

    // Paths
    private final Path sunRaysPath = new Path();

    public AlexgramSettingsHeaderView(Context context) {
        super(context);
        // Using hardware layer speeds up rendering significantly
        setLayerType(LAYER_TYPE_HARDWARE, null);
        dotPaint.setStyle(Paint.Style.FILL);
        glowPaint.setStyle(Paint.Style.FILL);
        shootPaint.setStyle(Paint.Style.STROKE);
        shootPaint.setStrokeCap(Paint.Cap.ROUND);
        sunRayPaint.setStyle(Paint.Style.FILL);
        isDark = Theme.getActiveTheme().isDark();
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        if (w == 0 || h == 0) return;
        vw = w; vh = h;
        isDark = Theme.getActiveTheme().isDark();
        
        buildBackground();
        precomputeShaders();
        buildSunRays();

        stars.clear();
        shootingStars.clear();
        birds.clear();
        
        if (isDark) {
            for (int i = 0; i < 140; i++) {
                Star s = new Star();
                s.init(vw, vh, rng);
                stars.add(s);
            }
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

    private void precomputeShaders() {
        // Light Mode: Sun
        sunX = vw * 0.15f; sunY = vh * 0.05f; sunR = vw * 0.25f;
        sunGlowShader = new RadialGradient(sunX, sunY, sunR,
                new int[]{0x40FFD54F, 0x1AFFECB3, 0x00FFF8E1},
                new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP);
        
        sunRayPaint.setShader(new RadialGradient(sunX, sunY, sunR * 1.5f,
                new int[]{0x25FFD54F, 0x05FFECB3, 0x00FFFFFF},
                new float[]{0f, 0.6f, 1f}, Shader.TileMode.CLAMP));

        // Dark Mode: Moon
        moonX = vw * 0.87f; moonY = vh * 0.08f; moonR = vw * 0.035f;
        moonGlowShader = new RadialGradient(moonX, moonY, moonR * 6f,
                new int[]{0x38FFFDE0, 0x18FFFFC8, 0x00FFFFB0},
                new float[]{0f, 0.4f, 1f}, Shader.TileMode.CLAMP);
                
        moonBodyShader = new RadialGradient(moonX - moonR*0.3f, moonY - moonR*0.3f, moonR,
                new int[]{0xFFFFFDE8, 0xFFE8E0C8}, null, Shader.TileMode.CLAMP);

        // Dark Mode: Nebulae
        n1X = vw * 0.12f; n1Y = vh * 0.15f; n1R = vw * 0.45f;
        nebula1Shader = createNebulaShader(n1X, n1Y, n1R, 0x18, 0x50, 0x30, 0xD0);
        
        n2X = vw * 0.78f; n2Y = vh * 0.08f; n2R = vw * 0.30f;
        nebula2Shader = createNebulaShader(n2X, n2Y, n2R, 0x30, 0x18, 0x80, 0xB0);
        
        n3X = vw * 0.45f; n3Y = vh * 0.75f; n3R = vw * 0.50f;
        nebula3Shader = createNebulaShader(n3X, n3Y, n3R, 0x10, 0x35, 0x55, 0x90);
    }
    
    private Shader createNebulaShader(float nx, float ny, float nr, int r, int g, int b, int a) {
        int ao = Math.max(0, Math.min(255, (int) (a * 0.12f)));
        return new RadialGradient(nx, ny, nr,
                new int[]{Color.argb(ao, r, g, b), Color.argb(ao / 3, r, g, b), Color.argb(0, r, g, b)},
                new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP);
    }

    private void buildSunRays() {
        sunRaysPath.reset();
        int rayCount = 12;
        float innerRadius = sunR * 0.2f;
        float outerRadius = sunR * 1.5f;
        for (int i = 0; i < rayCount; i++) {
            float angle1 = (float) (i * Math.PI * 2 / rayCount);
            float angle2 = (float) ((i + 0.3f) * Math.PI * 2 / rayCount);
            sunRaysPath.moveTo(sunX + (float)Math.cos(angle1) * innerRadius, sunY + (float)Math.sin(angle1) * innerRadius);
            sunRaysPath.lineTo(sunX + (float)Math.cos(angle1) * outerRadius, sunY + (float)Math.sin(angle1) * outerRadius);
            sunRaysPath.lineTo(sunX + (float)Math.cos(angle2) * outerRadius, sunY + (float)Math.sin(angle2) * outerRadius);
            sunRaysPath.lineTo(sunX + (float)Math.cos(angle2) * innerRadius, sunY + (float)Math.sin(angle2) * innerRadius);
            sunRaysPath.close();
        }
    }

    private void go() {
        ani = ValueAnimator.ofFloat(0f, 1f);
        ani.setDuration(10000); // 10 sec cycle
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

        // Background gradient O(1)
        c.drawRect(0, 0, vw, vh, bgPaint);

        float time = (System.currentTimeMillis() % 60000) / 60000f; // 60s global loop

        if (isDark) {
            drawDarkTheme(c, dt, time);
        } else {
            drawLightTheme(c, dt, time);
        }
    }

    private void drawDarkTheme(Canvas c, float dt, float time) {
        // 1. Nebulae (Rendered mostly once with hardware transformations)
        drawOptimizedGlow(c, nebula1Shader, n1X, n1Y, n1R, 1f + 0.08f * (float) Math.sin(time * 10f * Math.PI * 2));
        drawOptimizedGlow(c, nebula2Shader, n2X, n2Y, n2R, 1f + 0.08f * (float) Math.sin((time * 10f + 0.33f) * Math.PI * 2));
        drawOptimizedGlow(c, nebula3Shader, n3X, n3Y, n3R, 1f + 0.08f * (float) Math.sin((time * 10f + 0.66f) * Math.PI * 2));

        // 2. Moon Glow (Pulsing)
        float mp = 1f + 0.06f * (float) Math.sin(time * 15f * Math.PI * 2);
        drawOptimizedGlow(c, moonGlowShader, moonX, moonY, moonR * 6f, mp);

        // 3. Moon body
        dotPaint.setShader(moonBodyShader);
        c.drawCircle(moonX, moonY, moonR, dotPaint);
        dotPaint.setShader(null);
        dotPaint.setColor(0x18000000);
        c.drawCircle(moonX + moonR * 0.2f, moonY - moonR * 0.15f, moonR * 0.12f, dotPaint);
        c.drawCircle(moonX - moonR * 0.25f, moonY + moonR * 0.3f, moonR * 0.08f, dotPaint);

        // 4. Twinkling stars (Hardware batched)
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

        // 5. Shooting stars
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
        // 1. Sun Glow
        float sp = 1f + 0.04f * (float) Math.sin(time * 20f * Math.PI * 2);
        drawOptimizedGlow(c, sunGlowShader, sunX, sunY, sunR, sp);

        // 2. Rotating God-Rays
        c.save();
        c.rotate(time * 360f, sunX, sunY); // Full rotation every 60s
        c.drawPath(sunRaysPath, sunRayPaint);
        c.restore();

        // 3. Floating Clouds (Parallax moving left to right)
        float cloudPhase = time * (float) Math.PI * 2;
        float cx1 = (time * 1.5f * vw) % (vw * 1.5f) - vw * 0.2f;
        float cx2 = ((time + 0.3f) * 1.2f * vw) % (vw * 1.5f) - vw * 0.2f;
        float cx3 = ((time + 0.6f) * 0.8f * vw) % (vw * 1.5f) - vw * 0.2f;

        drawCloud(c, cx1, vh * 0.12f, vw * 0.18f, 0x12B0BEC5);
        drawCloud(c, cx2, vh * 0.25f, vw * 0.14f, 0x0EB0BEC5);
        drawCloud(c, cx3, vh * 0.55f, vw * 0.22f, 0x10B0BEC5);

        // 4. Flying Birds
        birdTimer += dt;
        if (birdTimer > 1.5f && rng.nextFloat() < 0.02f) { // Random chance every frame after 1.5s
            birdTimer = 0f;
            Bird b = new Bird();
            b.init(vw, vh, rng);
            birds.add(b);
        }
        
        dotPaint.setColor(0x405C6B7F); // Dark slate for birds
        dotPaint.setShader(null);
        shootPaint.setColor(0x405C6B7F);
        shootPaint.setStrokeWidth(AndroidUtilities.dp(1.5f));
        
        for (int i = birds.size() - 1; i >= 0; i--) {
            Bird b = birds.get(i);
            b.update(dt);
            if (b.dead) { birds.remove(i); continue; }
            
            // Draw V shape bird
            float wingOffset = (float) Math.sin(b.wingPhase) * b.size * 0.8f;
            c.drawLine(b.x, b.y, b.x - b.size, b.y - wingOffset, shootPaint);
            c.drawLine(b.x, b.y, b.x - b.size, b.y + wingOffset, shootPaint);
        }
    }

    private void drawOptimizedGlow(Canvas c, Shader shader, float cx, float cy, float maxR, float scale) {
        c.save();
        c.translate(cx, cy);
        c.scale(scale, scale);
        glowPaint.setShader(shader);
        c.drawCircle(0, 0, maxR, glowPaint);
        c.restore();
    }

    private void drawCloud(Canvas c, float cx, float cy, float r, int color) {
        dotPaint.setColor(color);
        dotPaint.setShader(null);
        c.drawCircle(cx, cy, r, dotPaint);
        c.drawCircle(cx - r * 0.6f, cy + r * 0.15f, r * 0.7f, dotPaint);
        c.drawCircle(cx + r * 0.65f, cy + r * 0.1f, r * 0.65f, dotPaint);
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
    
    // New Day Mode feature: Birds
    private static class Bird {
        float x, y, vx, vy, size, wingPhase, wingSpeed;
        boolean dead = false;
        
        void init(int vw, int vh, Random rng) {
            // Start from right, fly left slowly
            x = vw + 50f;
            y = vh * 0.1f + rng.nextFloat() * vh * 0.3f; // Upper sky
            vx = -(80f + rng.nextFloat() * 60f); // Move left
            vy = -10f + rng.nextFloat() * 20f; // Slight up/down
            size = 8f + rng.nextFloat() * 6f;
            wingPhase = rng.nextFloat() * (float)Math.PI;
            wingSpeed = 8f + rng.nextFloat() * 6f; // Flap frequency
        }
        
        void update(float dt) {
            x += vx * dt;
            y += vy * dt;
            wingPhase += wingSpeed * dt;
            if (x < -100f) dead = true;
        }
    }
}
