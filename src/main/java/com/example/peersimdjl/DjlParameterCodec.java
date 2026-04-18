package com.example.peersimdjl;

import ai.djl.ndarray.NDArray;
import ai.djl.ndarray.NDManager;
import ai.djl.ndarray.types.Shape;

/**
 * Conversion utilitaire DJL : NDArray ↔ float[].
 */
public final class DjlParameterCodec {

    private DjlParameterCodec() {
    }

    public static float[] toFloatArray(NDArray array) {
        if (array == null) {
            return new float[0];
        }
        return array.toFloatArray();
    }

    public static NDArray fromFloatArray(NDManager manager, float[] values) {
        if (manager == null) {
            throw new IllegalArgumentException("NDManager requis");
        }
        float[] safeValues = values == null ? new float[0] : values;
        return manager.create(safeValues);
    }

    public static NDArray fromFloatArray(NDManager manager, float[] values, Shape shape) {
        NDArray base = fromFloatArray(manager, values);
        if (shape == null) {
            return base;
        }
        return base.reshape(shape);
    }
}
