package com.example.peersimdjl;

import ai.djl.ndarray.NDArray;
import ai.djl.ndarray.NDManager;
import ai.djl.ndarray.types.Shape;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class DjlParameterCodecTest {

    @Test
    void shouldConvertArrayRoundTrip() {
        try (NDManager manager = NDManager.newBaseManager()) {
            float[] weights = new float[]{0.1f, 0.2f, 0.3f, 0.4f};
            NDArray ndArray = DjlParameterCodec.fromFloatArray(manager, weights, new Shape(2, 2));
            float[] restored = DjlParameterCodec.toFloatArray(ndArray);

            assertEquals(4, restored.length);
            assertArrayEquals(weights, restored, 1e-6f);
        }
    }
}
