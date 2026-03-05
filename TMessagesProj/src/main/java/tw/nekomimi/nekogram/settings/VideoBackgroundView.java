package tw.nekomimi.nekogram.settings;

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.media.MediaPlayer;
import android.view.Surface;
import android.view.TextureView;
import android.widget.FrameLayout;

import android.widget.ImageView;

import org.telegram.messenger.FileLog;
import org.telegram.ui.Components.LayoutHelper;

import java.io.File;

import tw.nekomimi.nekogram.NekoConfig;

public class VideoBackgroundView extends FrameLayout implements TextureView.SurfaceTextureListener {

    private TextureView textureView;
    private ImageView imageView;
    private MediaPlayer mediaPlayer;
    private Surface surface;
    private String currentVideoPath;
    private boolean isImage;

    public VideoBackgroundView(Context context) {
        super(context);
        
        currentVideoPath = NekoConfig.videoHeaderPath.String();
        
        isImage = isImageFile(currentVideoPath);

        if (isImage) {
            imageView = new ImageView(context);
            imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
            try {
                if (currentVideoPath != null) {
                    imageView.setImageURI(android.net.Uri.fromFile(new File(currentVideoPath)));
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
            addView(imageView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        } else {
            textureView = new TextureView(context);
            textureView.setSurfaceTextureListener(this);
            addView(textureView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        }
    }

    private boolean isImageFile(String path) {
        if (path == null) return false;
        String lower = path.toLowerCase();
        return lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") || lower.endsWith(".webp") || lower.endsWith(".bmp") || lower.endsWith(".gif");
    }

    private void initMediaPlayer(Surface surface) {
        if (isImage || currentVideoPath == null || currentVideoPath.isEmpty()) {
            return;
        }

        File file = new File(currentVideoPath);
        if (!file.exists()) {
            return; // Video file not found
        }

        try {
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setDataSource(currentVideoPath);
            mediaPlayer.setSurface(surface);
            mediaPlayer.setLooping(true);
            mediaPlayer.setVolume(0f, 0f); // Silent background video
            
            mediaPlayer.setOnVideoSizeChangedListener((mp, width, height) -> {
                updateTextureViewSize(width, height);
            });
            
            mediaPlayer.setOnPreparedListener(mp -> {
                mp.start();
            });
            
            mediaPlayer.prepareAsync();
        } catch (Exception e) {
            FileLog.e("Error initializing VideoBackgroundView", e);
            releaseMediaPlayer();
        }
    }

    private void updateTextureViewSize(int videoWidth, int videoHeight) {
        if (isImage) return;
        int viewWidth = getWidth();
        int viewHeight = getHeight();

        if (viewWidth == 0 || viewHeight == 0 || videoWidth == 0 || videoHeight == 0) {
            return;
        }

        float videoAspect = (float) videoWidth / videoHeight;
        float viewAspect = (float) viewWidth / viewHeight;

        Matrix matrix = new Matrix();

        float scaleX = 1f;
        float scaleY = 1f;

        if (videoAspect > viewAspect) {
            // Video is wider than the view.
            // To maintain aspect ratio and fill height, we scale X (width) up? No.
            // If we stretch to View (W, H), the video is squished horizontally (or rather, the stored aspect is too small).
            // Actually, wait. TextureView stretches the content (videoWidth, videoHeight) into (viewWidth, viewHeight).
            
            // If videoAspect > viewAspect (e.g. 16:9 video in 1:1 view).
            // Width is relatively larger in video.
            // Stretched into 1:1, it looks tall and thin? No, wide video into square looks tall?
            // Wide video (say 100x50, aspect 2) -> Square view (50x50, aspect 1).
            // Horizontal pixels get squashed by factor 2.
            // We need to Expand X by factor 2 to restore aspect.
            // But if we expand X, we crop sides. That's CENTER_CROP.
            // ScaleX = videoAspect / viewAspect.
            scaleX = videoAspect / viewAspect;
        } else {
            // Video is taller than view (e.g. 9:16 video in 1:1 view).
            // Tall video (say 50x100, aspect 0.5) -> Square view (50x50, aspect 1).
            // Vertical pixels get squashed by factor 2.
            // We need to Expand Y by factor 2.
            // ScaleY = viewAspect / videoAspect.
            scaleY = viewAspect / videoAspect;
        }

        matrix.setScale(scaleX, scaleY, viewWidth / 2f, viewHeight / 2f);
        textureView.setTransform(matrix);
    }

    private void releaseMediaPlayer() {
        if (mediaPlayer != null) {
            try {
                mediaPlayer.stop();
            } catch (Exception ignore) {}
            try {
                mediaPlayer.release();
            } catch (Exception ignore) {}
            mediaPlayer = null;
        }
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int width, int height) {
        if (isImage) return;
        surface = new Surface(surfaceTexture);
        initMediaPlayer(surface);
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int width, int height) {
        if (isImage) return;
        if (mediaPlayer != null) {
            updateTextureViewSize(mediaPlayer.getVideoWidth(), mediaPlayer.getVideoHeight());
        }
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
        releaseMediaPlayer();
        if (surface != null) {
            surface.release();
            surface = null;
        }
        return true;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {
        // Not needed
    }

    public void play() {
        if (isImage) return;
        if (mediaPlayer != null && !mediaPlayer.isPlaying()) {
            mediaPlayer.start();
        }
    }

    public void pause() {
        if (isImage) return;
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (isImage) return;
        if (textureView != null && textureView.isAvailable() && mediaPlayer == null) {
            surface = new Surface(textureView.getSurfaceTexture());
            initMediaPlayer(surface);
        } else {
            play();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (isImage) return;
        pause();
    }
}
