package com.otaliastudios.cameraview.internal.egl;


import android.opengl.GLES11Ext;
import android.opengl.GLES20;

import androidx.annotation.NonNull;

import com.otaliastudios.cameraview.CameraLogger;
import com.otaliastudios.cameraview.filter.Filter;
import com.otaliastudios.cameraview.filter.GlDisplayFilter;
import com.otaliastudios.cameraview.filter.NoFilter;
import com.otaliastudios.cameraview.filter.OESInputOutputFilter;
import com.otaliastudios.cameraview.internal.GlUtils;


public class EglViewport {

    private final static CameraLogger LOG = CameraLogger.create(EglViewport.class.getSimpleName());

    private Filter mFilter;
    private Filter mOESFilter;
    private Filter mDisplayFilter;
    private Filter mPendingFilter;

    public EglViewport() {
        this(new NoFilter());
    }

    public EglViewport(@NonNull Filter filter) {
        mOESFilter = new OESInputOutputFilter();
        mDisplayFilter = new GlDisplayFilter();
        mFilter = filter;
    }

    public void release() {
        mOESFilter.onDestroy();
        mFilter.onDestroy();
        mDisplayFilter.onDestroy();
    }

    // Camera input texture type: GLES11Ext.GL_TEXTURE_EXTERNAL_OES
    public int createTexture() {
        int[] textures = new int[1];
        GLES20.glGenTextures(1, textures, 0);
        GlUtils.checkError("glGenTextures");

        int texId = textures[0];
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texId);
        GlUtils.checkError("glBindTexture " + texId);

        GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER,
                GLES20.GL_NEAREST);
        GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER,
                GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S,
                GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T,
                GLES20.GL_CLAMP_TO_EDGE);
        GlUtils.checkError("glTexParameter");

        return texId;
    }

    public void setFilter(@NonNull Filter filter) {
        // TODO see if this is needed. If setFilter is always called from the correct GL thread,
        // we don't need to wait for a new draw call (which might not even happen).
        mPendingFilter = filter;
    }

    public int drawFrame(int textureId, long timestampUs, float[] textureMatrix) {
        if (mPendingFilter != null) {
            release();
            mFilter = mPendingFilter;
            mPendingFilter = null;
        }

        GlUtils.checkError("draw start");

        // Draw.
        maybeSetSize(mFilter);
        int currentTextureId = mOESFilter.drawFrameBuffer(
                textureId, timestampUs, textureMatrix);
        currentTextureId = mFilter.drawFrameBuffer(
                currentTextureId, timestampUs, textureMatrix);
        mDisplayFilter.drawFrame(currentTextureId, timestampUs, textureMatrix);
        return currentTextureId;
    }

    private void maybeSetSize(@NonNull Filter filter) {
        if (mOESFilter.getSize() == null || !mOESFilter.getSize().equals(filter.getSize()))
            mOESFilter.setSize(filter.getSize().getWidth(), filter.getSize().getHeight());
        if (mDisplayFilter.getSize() == null || !mDisplayFilter.getSize().equals(filter.getSize()))
            mDisplayFilter.setSize(filter.getSize().getWidth(), filter.getSize().getHeight());
    }
}
