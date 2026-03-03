package tw.nekomimi.nekogram.settings;

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.media.MediaPlayer;
import android.view.Surface;
import android.view.TextureView;
import android.widget.FrameLayout;

import org.telegram.messenger.FileLog;
import org.telegram.ui.Components.LayoutHelper;

import java.io.File;

import tw.nekomimi.nekogram.NekoConfig;

public class VideoBackgroundView extends FrameLayout implements TextureView.SurfaceTextureListener {

    private TextureView textureView;
    private MediaPlayer mediaPlayer;
    private Surface surface;
    private String currentVideoPath;

    public VideoBackgroundView(Context context) {
        super(context);
        
        currentVideoPath = NekoConfig.videoHeaderPath.String();
        
        textureView = new TextureView(context);
        textureView.setSurfaceTextureListener(this);
        addView(textureView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
    }

    private void initMediaPlayer(Surface surface) {
        if (currentVideoPath == null || currentVideoPath.isEmpty()) {
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
        int viewWidth = getWidth();
        int viewHeight = getHeight();

        if (viewWidth == 0 || viewHeight == 0 || videoWidth == 0 || videoHeight == 0) {
            return;
        }

        Matrix matrix = new Matrix();

        float scaleX = (float) viewWidth / videoWidth;
        float scaleY = (float) viewHeight / videoHeight;
        float maxScale = Math.max(scaleX, scaleY);

        // Center crop
        float scaledVideoWidth = videoWidth * maxScale;
        float scaledVideoHeight = videoHeight * maxScale;

        float translateX = (viewWidth - scaledVideoWidth) / 2f;
        float translateY = (viewHeight - scaledVideoHeight) / 2f;

        matrix.setScale(maxScale, maxScale);
        matrix.postTranslate(translateX, translateY);

        // Required because TextureView scaling uses the view's dimensions 
        // as 1.0f scale by default
        matrix.postScale(1f / viewWidth, 1f / viewHeight, 0, 0);
        matrix.preScale(viewWidth, viewHeight);

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
        surface = new Surface(surfaceTexture);
        initMediaPlayer(surface);
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int width, int height) {
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
        if (mediaPlayer != null && !mediaPlayer.isPlaying()) {
            mediaPlayer.start();
        }
    }

    public void pause() {
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (textureView.isAvailable() && mediaPlayer == null) {
            surface = new Surface(textureView.getSurfaceTexture());
            initMediaPlayer(surface);
        } else {
            play();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        pause();
    }
}
