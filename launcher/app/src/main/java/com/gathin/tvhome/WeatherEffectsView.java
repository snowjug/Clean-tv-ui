package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.Choreographer;
import android.view.View;

import androidx.annotation.Nullable;

import java.util.Random;

/**
 * High-performance, lightweight micro-animations overlay for weather conditions (Rain, Drizzle,
 * Snow, and Cold Wind Wisps).
 *
 * Runs at 60 FPS on Android TV with zero memory allocations inside the draw loop. When Home is
 * paused or no effect is active, animation callbacks are immediately stopped, consuming 0% CPU.
 */
public final class WeatherEffectsView extends View implements Choreographer.FrameCallback {

    public static final int EFFECT_NONE = 0;
    public static final int EFFECT_RAIN = 1;
    public static final int EFFECT_DRIZZLE = 2;
    public static final int EFFECT_SNOW = 3;
    public static final int EFFECT_WIND = 4;
    public static final int EFFECT_THUNDERSTORM = 5;

    private static final int MAX_PARTICLES = 36;
    private static final int MAX_WIND_WISPS = 5;

    private int currentEffect = EFFECT_NONE;
    private boolean isRunning = false;
    private final Random rnd = new Random();

    // Reusable pre-allocated particle properties (0 allocations during onDraw)
    private final float[] pX = new float[MAX_PARTICLES];
    private final float[] pY = new float[MAX_PARTICLES];
    private final float[] pSpeed = new float[MAX_PARTICLES];
    private final float[] pSize = new float[MAX_PARTICLES];
    private final float[] pPhase = new float[MAX_PARTICLES];

    // Wind wisps
    private final float[] wX = new float[MAX_WIND_WISPS];
    private final float[] wY = new float[MAX_WIND_WISPS];
    private final float[] wLen = new float[MAX_WIND_WISPS];
    private final float[] wSpeed = new float[MAX_WIND_WISPS];
    private final Path windPath = new Path();

    // Lightning flash timer for thunderstorm
    private long lastFlashTime = 0;
    private float flashAlpha = 0f;

    private final Paint rainPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint snowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint windPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint flashPaint = new Paint();

    private long lastFrameTimeNanos = 0;

    public WeatherEffectsView(Context context) {
        this(context, null);
    }

    public WeatherEffectsView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setFocusable(false);
        setClickable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        setWillNotDraw(false);

        rainPaint.setStyle(Paint.Style.STROKE);
        rainPaint.setStrokeCap(Paint.Cap.ROUND);

        snowPaint.setStyle(Paint.Style.FILL);

        windPaint.setStyle(Paint.Style.STROKE);
        windPaint.setStrokeCap(Paint.Cap.ROUND);

        flashPaint.setStyle(Paint.Style.FILL);
        flashPaint.setColor(0x22FFFFFF);

        initParticles();
    }

    private void initParticles() {
        for (int i = 0; i < MAX_PARTICLES; i++) {
            resetParticle(i, true);
        }
        for (int i = 0; i < MAX_WIND_WISPS; i++) {
            resetWindWisp(i, true);
        }
    }

    private void resetParticle(int i, boolean randomY) {
        int w = getWidth() > 0 ? getWidth() : 1920;
        int h = getHeight() > 0 ? getHeight() : 1080;
        pX[i] = rnd.nextFloat() * (w + 200) - 100;
        pY[i] = randomY ? (rnd.nextFloat() * h) : (-20 - rnd.nextFloat() * 80);
        pPhase[i] = rnd.nextFloat() * 6.283f;

        if (currentEffect == EFFECT_RAIN || currentEffect == EFFECT_THUNDERSTORM) {
            pSpeed[i] = 16f + rnd.nextFloat() * 12f;
            pSize[i] = 22f + rnd.nextFloat() * 14f;
        } else if (currentEffect == EFFECT_DRIZZLE) {
            pSpeed[i] = 7f + rnd.nextFloat() * 5f;
            pSize[i] = 10f + rnd.nextFloat() * 8f;
        } else if (currentEffect == EFFECT_SNOW) {
            pSpeed[i] = 1.2f + rnd.nextFloat() * 2.2f;
            pSize[i] = 2.5f + rnd.nextFloat() * 3.5f; // radius in dp
        }
    }

    private void resetWindWisp(int i, boolean randomX) {
        int w = getWidth() > 0 ? getWidth() : 1920;
        int h = getHeight() > 0 ? getHeight() : 1080;
        wX[i] = randomX ? (rnd.nextFloat() * w) : (-150 - rnd.nextFloat() * 200);
        wY[i] = rnd.nextFloat() * (h * 0.75f) + 50;
        wLen[i] = 120f + rnd.nextFloat() * 160f;
        wSpeed[i] = 8f + rnd.nextFloat() * 10f;
    }

    public void setEffect(int effect) {
        if (currentEffect == effect) return;
        currentEffect = effect;
        if (currentEffect == EFFECT_NONE) {
            stop();
            setVisibility(GONE);
        } else {
            setVisibility(VISIBLE);
            initParticles();
            start();
        }
        invalidate();
    }

    public int getEffect() {
        return currentEffect;
    }

    public void start() {
        if (isRunning || currentEffect == EFFECT_NONE) return;
        isRunning = true;
        lastFrameTimeNanos = 0;
        Choreographer.getInstance().postFrameCallback(this);
    }

    public void stop() {
        if (!isRunning) return;
        isRunning = false;
        Choreographer.getInstance().removeFrameCallback(this);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (currentEffect != EFFECT_NONE) start();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        stop();
    }

    @Override
    public void doFrame(long frameTimeNanos) {
        if (!isRunning) return;

        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) {
            Choreographer.getInstance().postFrameCallback(this);
            return;
        }

        // Update rain / drizzle / snow particles
        if (currentEffect == EFFECT_RAIN || currentEffect == EFFECT_DRIZZLE || currentEffect == EFFECT_THUNDERSTORM) {
            float windDrift = (currentEffect == EFFECT_DRIZZLE ? 1.5f : 3.5f);
            for (int i = 0; i < MAX_PARTICLES; i++) {
                pY[i] += pSpeed[i];
                pX[i] += windDrift;
                if (pY[i] > h + 30 || pX[i] > w + 50) {
                    resetParticle(i, false);
                }
            }
        } else if (currentEffect == EFFECT_SNOW) {
            for (int i = 0; i < MAX_PARTICLES; i++) {
                pY[i] += pSpeed[i];
                pPhase[i] += 0.035f;
                pX[i] += (float) Math.sin(pPhase[i]) * 0.8f;
                if (pY[i] > h + 20) {
                    resetParticle(i, false);
                }
            }
        }

        // Update wind wisps
        if (currentEffect == EFFECT_WIND || currentEffect == EFFECT_RAIN || currentEffect == EFFECT_DRIZZLE) {
            for (int i = 0; i < MAX_WIND_WISPS; i++) {
                wX[i] += wSpeed[i];
                if (wX[i] > w + wLen[i] + 50) {
                    resetWindWisp(i, false);
                }
            }
        }

        // Thunderstorm lightning logic
        if (currentEffect == EFFECT_THUNDERSTORM) {
            long now = SystemClock.uptimeMillis();
            if (flashAlpha > 0f) {
                flashAlpha = Math.max(0f, flashAlpha - 0.04f);
            } else if (now - lastFlashTime > 6000 && rnd.nextInt(180) == 1) {
                lastFlashTime = now;
                flashAlpha = 0.18f;
            }
        }

        invalidate();
        Choreographer.getInstance().postFrameCallback(this);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (currentEffect == EFFECT_NONE) return;

        int w = getWidth();
        int h = getHeight();

        // 1. Rain / Drizzle / Thunderstorm
        if (currentEffect == EFFECT_RAIN || currentEffect == EFFECT_DRIZZLE || currentEffect == EFFECT_THUNDERSTORM) {
            boolean isDrizzle = (currentEffect == EFFECT_DRIZZLE);
            rainPaint.setColor(isDrizzle ? 0x30A8D5F2 : 0x4DA0D8EF);
            rainPaint.setStrokeWidth(isDrizzle ? 1.5f : 2.2f);
            float windDrift = isDrizzle ? 1.5f : 3.5f;

            for (int i = 0; i < MAX_PARTICLES; i++) {
                float x1 = pX[i];
                float y1 = pY[i];
                float x2 = x1 + windDrift * (pSize[i] / pSpeed[i]);
                float y2 = y1 + pSize[i];
                canvas.drawLine(x1, y1, x2, y2, rainPaint);
            }

            if (currentEffect == EFFECT_THUNDERSTORM && flashAlpha > 0f) {
                flashPaint.setAlpha((int) (flashAlpha * 255));
                canvas.drawRect(0, 0, w, h, flashPaint);
            }
        }

        // 2. Snow / Flurries
        if (currentEffect == EFFECT_SNOW) {
            snowPaint.setColor(0x80FFFFFF);
            for (int i = 0; i < MAX_PARTICLES; i++) {
                canvas.drawCircle(pX[i], pY[i], pSize[i], snowPaint);
            }
        }

        // 3. Cold Wind Wisps
        if (currentEffect == EFFECT_WIND || currentEffect == EFFECT_RAIN || currentEffect == EFFECT_DRIZZLE) {
            windPaint.setColor(0x22FFFFFF);
            windPaint.setStrokeWidth(1.8f);
            for (int i = 0; i < MAX_WIND_WISPS; i++) {
                windPath.reset();
                float x = wX[i];
                float y = wY[i];
                float len = wLen[i];
                windPath.moveTo(x, y);
                windPath.quadTo(x + len * 0.5f, y + 14f, x + len, y);
                canvas.drawPath(windPath, windPaint);
            }
        }
    }
}
