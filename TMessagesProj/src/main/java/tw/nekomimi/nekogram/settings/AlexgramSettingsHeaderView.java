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
    private final Paint ringPnt = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ambPnt = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint aFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint planeRt = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint planeLt = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPnt = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPnt = new Paint(Paint.ANTI_ALIAS_FLAG);

    private static final int BG_TOP = 0xFF050B14;
    private static final int BG_MID = 0xFF0A1830;
    private static final int BG_BOT = 0xFF03070E;

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

    private final ArrayList<AmbientDot> ambients = new ArrayList<>();
    private final Random rng = new Random();
    
    private ValueAnimator ani;
    private long lastNs = 0;
    private boolean ok = false;
    
    public AlexgramSettingsHeaderView(Context context) {
        super(context);
        setLayerType(LAYER_TYPE_HARDWARE, null);

        aFill.setStyle(Paint.Style.FILL);
        planeRt.setStyle(Paint.Style.FILL);
        planeLt.setStyle(Paint.Style.FILL);
        ambPnt.setStyle(Paint.Style.FILL);
        ringPnt.setStyle(Paint.Style.STROKE);
        
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
        // Move logo up in the header
        cy = h * 0.40f; 
        ls = Math.min(w, h) * 0.15f; 

        build();

        ambients.clear();
        for (int i = 0; i < 40; i++) {
            AmbientDot d = new AmbientDot();
            d.init(vw, vh, rng);
            ambients.add(d);
        }

        ok = true;
        if (ani == null) go();
    }

    private void build() {
        float s = ls;
        float ax = cx;
        float ay = cy;
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
            bgPaint.setShader(new LinearGradient(0, 0, vw * 0.5f, vh,
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
                    -ps*0.5f, 0, ps, ps,
                    new int[]{PLANE_MID, PLANE_DK}, null, Shader.TileMode.CLAMP));
        }
    }

    private void go() {
        ani = ValueAnimator.ofFloat(0f, 1f);
        ani.setDuration(2000);
        ani.setRepeatCount(ValueAnimator.INFINITE);
        ani.setInterpolator(new LinearInterpolator());
        ani.addUpdateListener(a -> { invalidate(); });
        ani.start();
    }

    @Override
    protected void onDraw(Canvas c) {
        if (!ok) return;
        long ns = System.nanoTime();
        float dt = lastNs == 0 ? 0.016f : Math.min((ns - lastNs) / 1e9f, 0.05f);
        lastNs = ns;

        // Background
        c.drawRect(0, 0, vw, vh, bgPaint);

        // Ambient particles
        for (int i = 0; i < ambients.size(); i++) {
            AmbientDot d = ambients.get(i);
            d.update(dt);
            float da = d.alpha;
            
            ambPnt.setColor(NEON_CYAN);
            ambPnt.setAlpha((int) (da * 60));
            c.drawCircle(d.x, d.y, d.r * 2f, ambPnt);
            
            ambPnt.setColor(Color.WHITE);
            ambPnt.setAlpha((int) (da * 180));
            c.drawCircle(d.x, d.y, d.r, ambPnt);
        }

        // Glowing backdrop behind logo
        float pulse = 1f + 0.05f * (float) Math.sin((System.currentTimeMillis() % 2000) / 2000f * Math.PI * 2);
        float r = ls * 2.2f * pulse;
        if (r > 0) {
            RadialGradient g = new RadialGradient(cx, cy, r,
                    new int[]{
                            Color.argb(120, 79, 195, 247),
                            Color.argb(0, 13, 71, 161)
                    },
                    null, Shader.TileMode.CLAMP);
            glowPnt.setShader(g);
            c.drawCircle(cx, cy, r, glowPnt);
        }

        // Ring around logo
        ringPnt.setColor(NEON_CYAN);
        ringPnt.setAlpha((int)(150 * pulse));
        ringPnt.setStrokeWidth(ls * 0.05f);
        c.drawCircle(cx, cy, ls * 1.5f, ringPnt);
        ringPnt.setColor(Color.WHITE);
        ringPnt.setAlpha(100);
        ringPnt.setStrokeWidth(ls * 0.02f);
        c.drawCircle(cx, cy, ls * 1.5f, ringPnt);

        // A Fill
        aFill.setAlpha(255);
        c.drawPath(aBody, aFill);

        // Plane
        c.save();
        c.translate(finalPlaneX, finalPlaneY);
        c.rotate(finalPlaneAngle);
        planeLt.setAlpha(255);
        c.drawPath(planeLeftWing, planeLt);
        planeRt.setAlpha(255);
        c.drawPath(planeRightWing, planeRt);
        c.restore();

        // Text "A-Settings"
        textPnt.setTextSize(ls * 1.1f);
        // add vertical spacing
        c.drawText("A-Settings", cx, cy + ls * 2.5f, textPnt);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (ani != null) { ani.cancel(); ani = null; }
    }

    private static class AmbientDot {
        float x, y, r, alpha, speed, angle;
        int vw, vh;
        void init(int vw, int vh, Random rng) {
            this.vw = vw;
            this.vh = vh;
            x = rng.nextFloat() * vw;
            y = rng.nextFloat() * (vh * 0.8f);
            r = 1.0f + rng.nextFloat() * 2.0f;
            alpha = 0.2f + rng.nextFloat() * 0.8f;
            speed = 10f + rng.nextFloat() * 20f;
            angle = rng.nextFloat() * 360f;
        }
        void update(float dt) {
            x += Math.cos(Math.toRadians(angle)) * speed * dt;
            y += Math.sin(Math.toRadians(angle)) * speed * dt;
            if (x < 0) x += vw;
            if (x > vw) x -= vw;
            if (y < 0) y += vh;
            if (y > vh) y -= vh;
        }
    }
}
