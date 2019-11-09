package com.otaliastudios.cameraview.filter;

import android.opengl.GLES11Ext;
import android.opengl.GLES20;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import com.otaliastudios.cameraview.size.Size;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * A {@link MultiFilter} is a special {@link Filter} that can group one or more filters together.
 * When this happens, filters are applied in sequence:
 * - the first filter reads from input frames
 * - the second filters reads the output of the first
 * And so on, until the last filter which will read from the previous and write to the real output.
 *
 * New filters can be added at any time through {@link #addFilter(Filter)}, but currently they
 * can not be removed because we can not easily ensure that they would be correctly released.
 *
 * The {@link MultiFilter} does also implement {@link OneParameterFilter} and
 * {@link TwoParameterFilter}, dispatching all the parameter calls to child filters,
 * assuming they support it.
 *
 * There are some important technical caveats when using {@link MultiFilter}:
 * - each child filter requires the allocation of a GL framebuffer. Using a large number of filters
 *   will likely cause memory issues (e.g. https://stackoverflow.com/q/6354208/4288782).
 * - some of the children need to write into {@link GLES20#GL_TEXTURE_2D} instead of
 *   {@link GLES11Ext#GL_TEXTURE_EXTERNAL_OES}! To achieve this, we replace samplerExternalOES
 *   with sampler2D in your fragment shader code. This might cause issues for some shaders.
 */
@SuppressWarnings("unused")
public class MultiFilter implements Filter, OneParameterFilter, TwoParameterFilter {

    @VisibleForTesting
    final List<Filter> filters = new ArrayList<>();
    private final Object lock = new Object();
    private Size size = null;
    private float parameter1 = 0F;
    private float parameter2 = 0F;

    /**
     * Creates a new group with the given filters.
     * @param filters children
     */
    @SuppressWarnings("WeakerAccess")
    public MultiFilter(@NonNull Filter... filters) {
        this(Arrays.asList(filters));
    }

    /**
     * Creates a new group with the given filters.
     * @param filters children
     */
    @SuppressWarnings("WeakerAccess")
    public MultiFilter(@NonNull Collection<Filter> filters) {
        for (Filter filter : filters) {
            addFilter(filter);
        }
    }

    /**
     * Adds a new filter. It will be used in the next frame.
     * If the filter is a {@link MultiFilter}, we'll use its children instead.
     *
     * @param filter a new filter
     */
    @SuppressWarnings("WeakerAccess")
    public void addFilter(@NonNull Filter filter) {
        if (filter instanceof MultiFilter) {
            MultiFilter multiFilter = (MultiFilter) filter;
            for (Filter multiChild : multiFilter.filters) {
                addFilter(multiChild);
            }
            return;
        }
        synchronized (lock) {
            if (!filters.contains(filter)) {
                filters.add(filter);
            }
        }
    }

    private void maybeSetSize(@NonNull Filter filter) {
        //noinspection ConstantConditions
        if (size != null && !size.equals(filter.getSize())) {
            filter.setSize(size.getWidth(), size.getHeight());
        }
    }

    @NonNull
    @Override
    public String getVertexShader() {
        // Whatever, we won't be using this.
        return new NoFilter().getVertexShader();
    }

    @NonNull
    @Override
    public String getFragmentShader() {
        // Whatever, we won't be using this.
        return new NoFilter().getFragmentShader();
    }

    @Override
    public void onCreate(int programHandle) {
        // We'll create children during the draw() op, since some of them
        // might have been added after this onCreate() is called.
    }

    @Override
    public void onDestroy() {
        synchronized (lock) {
            for (Filter filter : filters) {
                filter.onDestroy();
            }
        }
    }

    @Override
    public void setSize(int width, int height) {
        size = new Size(width, height);
        synchronized (lock) {
            for (Filter filter : filters) {
                maybeSetSize(filter);
            }
        }
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
        synchronized (lock) {
            int currentTextureId = textureId;
            for (int i = 0; i < filters.size(); i++) {
                boolean isLast = i == filters.size() - 1;
                Filter filter = filters.get(i);
                maybeSetSize(filter);

                // Perform the actual drawing.
                // The first filter should apply all the transformations. Then,
                // since they are applied, we should use a no-op matrix.
                currentTextureId = filter.drawFrameBuffer(
                        currentTextureId, timestampUs, transformMatrix);
            }
            return currentTextureId;
        }
    }

    @NonNull
    @Override
    public Filter copy() {
        synchronized (lock) {
            MultiFilter copy = new MultiFilter();
            for (Filter filter : filters) {
                copy.addFilter(filter.copy());
            }
            return copy;
        }
    }

    @Override
    public void setParameter1(float parameter1) {
        this.parameter1 = parameter1;
        synchronized (lock) {
            for (Filter filter : filters) {
                if (filter instanceof OneParameterFilter) {
                    ((OneParameterFilter) filter).setParameter1(parameter1);
                }
            }
        }
    }

    @Override
    public void setParameter2(float parameter2) {
        this.parameter2 = parameter2;
        synchronized (lock) {
            for (Filter filter : filters) {
                if (filter instanceof TwoParameterFilter) {
                    ((TwoParameterFilter) filter).setParameter2(parameter2);
                }
            }
        }
    }

    @Override
    public float getParameter1() {
        return parameter1;
    }

    @Override
    public float getParameter2() {
        return parameter2;
    }
}
