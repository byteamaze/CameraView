package com.otaliastudios.cameraview.filter;

import android.opengl.GLES20;

import androidx.annotation.NonNull;

/**
 * draw texture on default FBO
 */
public class GlDisplayFilter extends BaseFilter {

    @NonNull
    @Override
    public String getFragmentShader() {
        return createDefaultFragmentShader();
    }

    @Override
    public void drawFrame(int textureId, long timestampUs, float[] transformMatrix) {
        int programHandle = maybeCreateProgramHandle();
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        GLES20.glUseProgram(programHandle);
        drawTexture(textureId, timestampUs, transformMatrix);
    }

    @Override
    public int drawFrameBuffer(int textureId, long timestampUs, float[] transformMatrix) {
        throw new RuntimeException("never call drawFrameBuffer() in this filter");
    }
}
