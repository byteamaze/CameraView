package com.otaliastudios.cameraview.filter;

import android.opengl.GLES11Ext;
import android.opengl.GLES20;

import androidx.annotation.NonNull;

import com.otaliastudios.cameraview.internal.GlUtils;

/**
 * convert samplerExternalOES to sampler2D
 */
public class OESInputOutputFilter extends BaseFilter {
    @SuppressWarnings("WeakerAccess")
    protected final static String DEFAULT_VERTEX_MVP_MATRIX_NAME = "uMVPMatrix";

    @SuppressWarnings("WeakerAccess")
    protected final static String DEFAULT_VERTEX_TRANSFORM_MATRIX_NAME = "uTexMatrix";

    @SuppressWarnings("WeakerAccess")
    protected String vertexModelViewProjectionMatrixName = DEFAULT_VERTEX_MVP_MATRIX_NAME;
    @SuppressWarnings("WeakerAccess")
    protected String vertexTransformMatrixName = DEFAULT_VERTEX_TRANSFORM_MATRIX_NAME;

    private int vertexModelViewProjectionMatrixLocation = -1;
    private int vertexTransformMatrixLocation = -1;

    private String mVertexShader = "uniform mat4 " + vertexModelViewProjectionMatrixName + ";\n"
            + "uniform mat4 " + vertexTransformMatrixName + ";\n"
            + "attribute vec4 " + vertexPositionName + ";\n"
            + "attribute vec4 " + vertexTextureCoordinateName + ";\n"
            + "varying vec2 " + fragmentTextureCoordinateName + ";\n"
            + "void main() {\n"
            + "    gl_Position = " + vertexModelViewProjectionMatrixName + " * " + vertexPositionName + ";\n"
            + "    " + fragmentTextureCoordinateName + " = (" + vertexTransformMatrixName + " * " + vertexTextureCoordinateName + ").xy;\n"
            + "}\n";

    private String mFragmentShader = "#extension GL_OES_EGL_image_external : require\n"
            + "precision mediump float;\n"
            + "varying vec2 " + fragmentTextureCoordinateName + ";\n"
            + "uniform samplerExternalOES sTexture;\n"
            + "void main() {\n"
            + "  gl_FragColor = texture2D(sTexture, " + fragmentTextureCoordinateName + ");\n"
            + "}\n";


    @Override
    public void onCreate(int programHandle) {
        super.onCreate(programHandle);
        vertexModelViewProjectionMatrixLocation = GLES20.glGetUniformLocation(programHandle,
                vertexModelViewProjectionMatrixName);
        GlUtils.checkLocation(vertexModelViewProjectionMatrixLocation,
                vertexModelViewProjectionMatrixName);
        vertexTransformMatrixLocation = GLES20.glGetUniformLocation(programHandle,
                vertexTransformMatrixName);
        GlUtils.checkLocation(vertexTransformMatrixLocation, vertexTransformMatrixName);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        vertexModelViewProjectionMatrixLocation = -1;
        vertexTransformMatrixLocation = -1;
    }

    @Override
    public void onPreDraw(int textureId, long timestampUs, float[] transformMatrix) {
        // Copy the model / view / projection matrix over.
        GLES20.glUniformMatrix4fv(vertexModelViewProjectionMatrixLocation, 1,
                false, GlUtils.IDENTITY_MATRIX, 0);
        GlUtils.checkError("glUniformMatrix4fv");

        // Copy the texture transformation matrix over.
        GLES20.glUniformMatrix4fv(vertexTransformMatrixLocation, 1,
                false, transformMatrix, 0);
        GlUtils.checkError("glUniformMatrix4fv");
        super.onPreDraw(textureId, timestampUs, transformMatrix);
    }

    @NonNull
    @Override
    public String getFragmentShader() {
        return mFragmentShader;
    }

    @NonNull
    @Override
    public String getVertexShader() {
        return mVertexShader;
    }

    @Override
    protected int getTextureType() {
        return GLES11Ext.GL_TEXTURE_EXTERNAL_OES;
    }
}
