package org.telegram.ui.Components.CameraFilters;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;

import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageLoader;
import org.telegram.ui.Components.CameraFilters.effects.*;

import java.io.File;
import java.io.FileOutputStream;

public class CameraFilterProcessor {

    private static final ICameraFilterEffect[] EFFECTS = new ICameraFilterEffect[CameraFilterType.COUNT];

    static {
        register(new BeautyFilterEffect());
        register(new GoldenHourFilterEffect());
        register(new Vintage90sFilterEffect());
        register(new CyberpunkFilterEffect());
        register(new BwNoirFilterEffect());
        register(new PastelAnimeFilterEffect());
        register(new VhsGlitchFilterEffect());
        register(new FisheyeFilterEffect());
        register(new ThermalFilterEffect());
        register(new SepiaFilterEffect());
        register(new NightVisionFilterEffect());
        register(new ComicPopArtFilterEffect());
        register(new RoseBlushFilterEffect());
        register(new ColdIceFilterEffect());
        register(new CinemaTealFilterEffect());
        register(new Portra400FilterEffect());
        register(new FujiVelviaFilterEffect());
        register(new VaporwaveFilterEffect());
        register(new DreamyBloomFilterEffect());
        register(new SinCityRedFilterEffect());
        register(new EmeraldForestFilterEffect());
        register(new SunsetPeachFilterEffect());
        register(new PrismRefractionFilterEffect());
        register(new MochaWarmFilterEffect());
        register(new DuotoneNeonFilterEffect());
        register(new HolographicFilterEffect());
        register(new AcidPopFilterEffect());
        register(new BladeRunnerFilterEffect());
        register(new WesAndersonFilterEffect());
        register(new KodakGoldFilterEffect());
        register(new IlfordHp5FilterEffect());
        register(new SoftAuraFilterEffect());
        register(new PeachGlowFilterEffect());
        register(new HoneyWarmthFilterEffect());
        register(new LavenderHazeFilterEffect());
        register(new Super8FilterEffect());
        register(new Polaroid600FilterEffect());
        register(new RetroDiscoFilterEffect());
        register(new DarkAcademiaFilterEffect());
        register(new RetroScanlinesFilterEffect());
        register(new VortexWarpFilterEffect());
        register(new KaleidoscopeFilterEffect());
        register(new InfraredAeroFilterEffect());
        register(new NeonCyanMagentaFilterEffect());
        register(new GoldDustFilterEffect());
        register(new AnamorphicFlareFilterEffect());
        register(new EdgeNeonGlowFilterEffect());
        register(new RadioactiveGlowFilterEffect());
        register(new SolarEclipseFilterEffect());
        register(new PixelArt8bitFilterEffect());
        register(new DoubleExposureFilterEffect());
        register(new MagmaVolcanoFilterEffect());
        register(new DeepAbyssOceanFilterEffect());
        register(new GlitchDatamoshFilterEffect());
        register(new StarlightSparkleFilterEffect());
        register(new ChronoSpeedBlurFilterEffect());
        register(new MidnightPurpleFilterEffect());
        register(new RippleWaterFilterEffect());
        register(new BulgeWarpFilterEffect());
        register(new GameBoyFilterEffect());
        register(new MatrixRainFilterEffect());
        register(new NeonWireframeFilterEffect());
        register(new QuadMirrorFilterEffect());
        register(new TunnelZoomFilterEffect());
        register(new HoloBeamFilterEffect());
        register(new HeatwaveMirageFilterEffect());
        register(new InkSketchFilterEffect());
        register(new SilentCinemaFilterEffect());
        register(new CrystalSphereFilterEffect());
    }

    private static void register(ICameraFilterEffect effect) {
        int type = effect.getFilterType();
        if (type >= 0 && type < EFFECTS.length) {
            EFFECTS[type] = effect;
        }
    }

    public static boolean applyFilterToFile(File file, int filterType, float intensity, int orientation) {
        if (file == null || !file.exists() || filterType == CameraFilterType.ORIGINAL || intensity <= 0f) {
            return false;
        }

        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inPreferredConfig = Bitmap.Config.ARGB_8888;
            Bitmap src = BitmapFactory.decodeFile(file.getAbsolutePath(), options);
            if (src == null) return false;

            if (orientation != 0 && orientation != -1) {
                Matrix matrix = new Matrix();
                matrix.postRotate(orientation);
                Bitmap rotated = Bitmap.createBitmap(src, 0, 0, src.getWidth(), src.getHeight(), matrix, true);
                if (rotated != src) {
                    src.recycle();
                    src = rotated;
                }
            }

            Bitmap filtered = applyFilterToBitmap(src, filterType, intensity);
            if (filtered == null) {
                src.recycle();
                return false;
            }

            FileOutputStream fos = new FileOutputStream(file);
            filtered.compress(Bitmap.CompressFormat.JPEG, 95, fos);
            fos.flush();
            fos.getFD().sync();
            fos.close();

            ImageLoader.getInstance().clearMemory();

            if (filtered != src) {
                filtered.recycle();
            }
            src.recycle();
            return true;
        } catch (Throwable e) {
            FileLog.e(e);
            return false;
        }
    }

    public static boolean applyFilterToFile(File file, int filterType, float intensity) {
        int orientation = 0;
        try {
            ExifInterface exif = new ExifInterface(file.getAbsolutePath());
            int exifOrientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            if (exifOrientation == ExifInterface.ORIENTATION_ROTATE_90) {
                orientation = 90;
            } else if (exifOrientation == ExifInterface.ORIENTATION_ROTATE_180) {
                orientation = 180;
            } else if (exifOrientation == ExifInterface.ORIENTATION_ROTATE_270) {
                orientation = 270;
            }
        } catch (Exception ignore) {}
        return applyFilterToFile(file, filterType, intensity, orientation);
    }

    public static Bitmap applyFilterToBitmap(Bitmap src, int filterType, float intensity) {
        if (src == null || filterType <= CameraFilterType.ORIGINAL || filterType >= EFFECTS.length || intensity <= 0f) {
            return src;
        }

        ICameraFilterEffect effect = EFFECTS[filterType];
        if (effect == null) {
            return src;
        }

        int width = src.getWidth();
        int height = src.getHeight();
        int[] pixels = new int[width * height];
        src.getPixels(pixels, 0, width, 0, 0, width, height);

        float clampedIntensity = Math.max(0f, Math.min(1f, intensity));
        int[] outPixels = new int[pixels.length];

        effect.process(pixels, outPixels, width, height, clampedIntensity);

        return Bitmap.createBitmap(outPixels, width, height, Bitmap.Config.ARGB_8888);
    }
}
