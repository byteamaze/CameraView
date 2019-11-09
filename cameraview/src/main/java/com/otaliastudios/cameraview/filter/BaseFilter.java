package com.otaliastudios.cameraview.filter;

import android.opengl.GLES20;
import android.opengl.GLES30;

import androidx.annotation.NonNull;

import com.otaliastudios.cameraview.CameraLogger;
import com.otaliastudios.cameraview.internal.GlUtils;
import com.otaliastudios.cameraview.size.Size;

import java.nio.FloatBuffer;

/**
 * A base implementation of {@link Filter} that just leaves the fragment shader to subclasses.
 * See {@link NoFilter} for a non-abstract implementation.
 *
 * This class offers a default vertex shader implementation which in most cases is not required
 * to be changed. Most effects can be rendered by simply changing the fragment shader, thus
 * by overriding {@link #getFragmentShader()}.
 *
 * All {@link BaseFilter}s should have a no-arguments public constructor.
 * This class will try to automatically implement {@link #copy()} thanks to this.
 * If your filter implements public parameters, please implement {@link OneParameterFilter}
 * and {@link TwoParameterFilter} to handle them and have them passed automatically to copies.
 *
 * NOTE - This class expects variable to have a certain name:
 * - {@link #vertexPositionName}
 * - {@link #vertexTransformMatrixName}
 * - {@link #vertexModelViewProjectionMatrixName}
 * - {@link #vertexTextureCoordinateName}
 * - {@link #fragmentTextureCoordinateName}
 * You can either change these variables, for example in your constructor, or change your
 * vertex and fragment shader code to use them.
 *
 * NOTE - the {@link android.graphics.SurfaceTexture} restrictions apply:
 * We only support the {@link android.opengl.GLES11Ext#GL_TEXTURE_EXTERNAL_OES} texture target
 * and it must be specified in the fragment shader as a samplerExternalOES texture.
 * You also have to explicitly require the extension: see
 * {@link #createDefaultFragmentShader(String)}.
 */
public abstract class BaseFilter implements Filter {

    private final static String TAG = BaseFilter.class.getSimpleName();
    private final static CameraLogger LOG = CameraLogger.create(TAG);

    @SuppressWarnings("WeakerAccess")
    protected final static String DEFAULT_VERTEX_POSITION_NAME = "aPosition";

    @SuppressWarnings("WeakerAccess")
    protected final static String DEFAULT_VERTEX_TEXTURE_COORDINATE_NAME = "aTextureCoord";

    protected final static String DEFAULT_FRAGMENT_TEXTURE_COORDINATE_NAME = "vTextureCoord";

    @NonNull
    private static String createDefaultVertexShader(
            @NonNull String vertexPositionName,
            @NonNull String vertexTextureCoordinateName,
            @NonNull String fragmentTextureCoordinateName) {
        return "attribute vec4 " + vertexPositionName + ";\n"
                + "attribute vec4 " + vertexTextureCoordinateName + ";\n"
                + "varying vec2 " + fragmentTextureCoordinateName + ";\n"
                + "void main() {\n"
                + "    gl_Position = " + vertexPositionName + ";\n"
                + "    " + fragmentTextureCoordinateName + " = " + vertexTextureCoordinateName + ".xy;\n"
                + "}\n";
    }

    @NonNull
    private static String createDefaultFragmentShader(
            @NonNull String fragmentTextureCoordinateName) {
        return "#extension GL_OES_EGL_image_external : require\n"
                + "precision mediump float;\n"
                + "varying vec2 " + fragmentTextureCoordinateName + ";\n"
                + "uniform sampler2D sTexture;\n"
                + "void main() {\n"
                + "  gl_FragColor = texture2D(sTexture, " + fragmentTextureCoordinateName + ");\n"
                + "}\n";
    }

    // When the model/view/projection matrix is identity, this will exactly cover the viewport.
    private FloatBuffer vertexPosition = GlUtils.floatBuffer(new float[]{
            -1.0f, -1.0f, // 0 bottom left
            1.0f, -1.0f, // 1 bottom right
            -1.0f, 1.0f, // 2 top left
            1.0f, 1.0f, // 3 top right
    });

    private FloatBuffer textureCoordinates = GlUtils.floatBuffer(new float[]{
            0.0f, 0.0f, // 0 bottom left
            1.0f, 0.0f, // 1 bottom right
            0.0f, 1.0f, // 2 top left
            1.0f, 1.0f  // 3 top right
    });

    private int vertexPositionLocation = -1;
    private int vertexTextureCoordinateLocation = -1;
    private int programHandle = -1;
    private Size size;

    private int[] mFrameBuffers;
    private int[] mFrameBufferTextures;

    @SuppressWarnings("WeakerAccess")
    protected String vertexPositionName = DEFAULT_VERTEX_POSITION_NAME;
    @SuppressWarnings("WeakerAccess")
    protected String vertexTextureCoordinateName = DEFAULT_VERTEX_TEXTURE_COORDINATE_NAME;
    @SuppressWarnings({"unused", "WeakerAccess"})
    protected String fragmentTextureCoordinateName = DEFAULT_FRAGMENT_TEXTURE_COORDINATE_NAME;

    @SuppressWarnings("WeakerAccess")
    @NonNull
    protected String createDefaultVertexShader() {
        return createDefaultVertexShader(vertexPositionName,
                vertexTextureCoordinateName,
                fragmentTextureCoordinateName);
    }

    @SuppressWarnings("WeakerAccess")
    @NonNull
    protected String createDefaultFragmentShader() {
        return createDefaultFragmentShader(fragmentTextureCoordinateName);
    }

    @Override
    public void onCreate(int programHandle) {
        this.programHandle = programHandle;
        vertexPositionLocation = GLES20.glGetAttribLocation(programHandle, vertexPositionName);
        GlUtils.checkLocation(vertexPositionLocation, vertexPositionName);
        vertexTextureCoordinateLocation = GLES20.glGetAttribLocation(programHandle, vertexTextureCoordinateName);
        GlUtils.checkLocation(vertexTextureCoordinateLocation, vertexTextureCoordinateName);
    }

    @Override
    public void onDestroy() {
        if (programHandle != -1) {
            GLES20.glDeleteProgram(programHandle);
            programHandle = -1;
        }
        destroyFrameBuffer();
        vertexPositionLocation = -1;
        vertexTextureCoordinateLocation = -1;
    }

    private void destroyFrameBuffer() {
        //noinspection ConstantConditions
        if (mFrameBuffers != null) {
            GLES20.glDeleteFramebuffers(1, mFrameBuffers, 0);
            mFrameBuffers = null;
        }
        if (mFrameBufferTextures != null) {
            GLES20.glDeleteTextures(1, mFrameBufferTextures, 0);
            mFrameBufferTextures = null;
        }
    }

    @NonNull
    @Override
    public String getVertexShader() {
        return createDefaultVertexShader();
    }

    @Override
    public void setSize(int width, int height) {
        if (size != null && size.getWidth() == width && size.getHeight() == height) return;
        size = new Size(width, height);
    }

    @Override
    public Size getSize() {
        return size;
    }

    @Override
    public void drawFrame(int textureId, long timestampUs, float[] transformMatrix) {
    }

    @Override
    public int drawFrameBuffer(int textureId, long timestampUs, float[] transformMatrix) {
        maybeCreateProgramHandle();
        maybeCreateFramebuffer(getSize().getWidth(), getSize().getHeight());
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mFrameBuffers[0]);
        GLES20.glUseProgram(programHandle);
        drawTexture(textureId, timestampUs, transformMatrix);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        return mFrameBufferTextures[0];
    }

    @SuppressWarnings("WeakerAccess")
    protected void drawTexture(int textureId, long timestampUs, float[] transformMatrix) {
        if (programHandle == -1) {
            LOG.w("Filter.draw() called after destroying the filter. " +
                    "This can happen rarely because of threading.");
        } else {
            onPreDraw(textureId, timestampUs, transformMatrix);
            onDraw(timestampUs);
            onPostDraw(timestampUs);
        }
    }

    @SuppressWarnings("WeakerAccess")
    protected int maybeCreateProgramHandle() {
        if (programHandle == -1)
            onCreate(GlUtils.createProgram(getVertexShader(), getFragmentShader()));
        return programHandle;
    }

    @SuppressWarnings("WeakerAccess")
    protected void maybeCreateFramebuffer(int width, int height) {
        if (mFrameBuffers == null) {
            mFrameBuffers = new int[1];
            mFrameBufferTextures = new int[1];
            GLES20.glGenFramebuffers(1, mFrameBuffers, 0);
            GLES20.glGenTextures(1, mFrameBufferTextures, 0);

            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mFrameBufferTextures[0]);
            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA,
                    width, height, 0, GLES20.GL_RGBA,
                    GLES20.GL_UNSIGNED_BYTE, null);
            GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
                    GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
                    GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
                    GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
                    GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mFrameBuffers[0]);
            GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0,
                    GLES20.GL_TEXTURE_2D, mFrameBufferTextures[0], 0);

            int status = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER);
            if (status != GLES20.GL_FRAMEBUFFER_COMPLETE) {
                throw new RuntimeException("Invalid framebuffer generation. Error:" + status);
            }

            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        }
    }

    public void onPreDraw(int textureId, long timestampUs, float[] transformMatrix) {
        // Enable the "aPosition" vertex attribute.
        // Connect vertexBuffer to "aPosition".
        GLES20.glEnableVertexAttribArray(vertexPositionLocation);
        GlUtils.checkError("glEnableVertexAttribArray: " + vertexPositionLocation);
        GLES20.glVertexAttribPointer(vertexPositionLocation, 2, GLES20.GL_FLOAT,
                false, 8, vertexPosition);
        GlUtils.checkError("glVertexAttribPointer");

        // Enable the "aTextureCoord" vertex attribute.
        // Connect texBuffer to "aTextureCoord".
        GLES20.glEnableVertexAttribArray(vertexTextureCoordinateLocation);
        GlUtils.checkError("glEnableVertexAttribArray");
        GLES20.glVertexAttribPointer(vertexTextureCoordinateLocation, 2, GLES20.GL_FLOAT,
                false, 8, textureCoordinates);
        GlUtils.checkError("glVertexAttribPointer");

        // bind texture
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0);
        GLES30.glBindTexture(getTextureType(), textureId);
    }

    @SuppressWarnings("WeakerAccess")
    public void onDraw(long timestampUs) {
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        GlUtils.checkError("glDrawArrays");
    }

    @SuppressWarnings("WeakerAccess")
    public void onPostDraw(long timestampUs) {
        GLES20.glDisableVertexAttribArray(vertexPositionLocation);
        GLES20.glDisableVertexAttribArray(vertexTextureCoordinateLocation);
        GLES30.glBindTexture(getTextureType(), 0);
        GLES30.glUseProgram(0);
    }

    @NonNull
    @Override
    public final BaseFilter copy() {
        BaseFilter copy = onCopy();
        if (size != null) {
            copy.setSize(size.getWidth(), size.getHeight());
        }
        if (this instanceof OneParameterFilter) {
            ((OneParameterFilter) copy).setParameter1(((OneParameterFilter) this).getParameter1());
        }
        if (this instanceof TwoParameterFilter) {
            ((TwoParameterFilter) copy).setParameter2(((TwoParameterFilter) this).getParameter2());
        }
        return copy;
    }

    protected BaseFilter onCopy() {
        try {
            return getClass().newInstance();
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Filters should have a public no-arguments constructor.", e);
        } catch (InstantiationException e) {
            throw new RuntimeException("Filters should have a public no-arguments constructor.", e);
        }
    }

    /**
     * texture type, default: GL_TEXTURE_2D
     * only OESInputOutputFilter is a special type
     *
     * @return
     */
    protected int getTextureType() {
        return GLES20.GL_TEXTURE_2D;
    }
}
