package com.example.peersimdjl;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatasetPreprocessorTest {

    @Test
    void shouldSkipHeaderEncodeCategoricalAndNormalize() {
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{"age", "job", "income"});
        rows.add(new String[]{"25", "admin", "50000"});
        rows.add(new String[]{"50", "tech", "100000"});

        DatasetPreprocessor.Result result = DatasetPreprocessor.preprocess(rows, true);

        assertTrue(result.headerSkipped);
        assertEquals(2, result.rowCount);
        assertEquals(3, result.columnCount);
        assertEquals(1, result.categoricalColumns);

        for (double[] row : result.data) {
            for (double value : row) {
                assertTrue(value >= 0.0d && value <= 1.0d);
            }
        }
    }

    @Test
    void shouldKeepNumericParsingWhenPreprocessDisabled() {
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{"1", "2", "3"});
        rows.add(new String[]{"4", "5", "6"});

        DatasetPreprocessor.Result result = DatasetPreprocessor.preprocess(rows, false);

        assertTrue(!result.headerSkipped);
        assertEquals(2, result.rowCount);
        assertEquals(3, result.columnCount);
        assertEquals(0, result.categoricalColumns);
        assertTrue(!result.normalized);
        assertEquals(1.0d, result.data[0][0], 1e-9);
        assertEquals(6.0d, result.data[1][2], 1e-9);
    }
}
