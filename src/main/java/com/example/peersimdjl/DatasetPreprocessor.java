package com.example.peersimdjl;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Prétraitement léger pour dataset CSV importé.
 */
public final class DatasetPreprocessor {

    public static final class Result {
        public final double[][] data;
        public final boolean headerSkipped;
        public final int rowCount;
        public final int columnCount;
        public final int categoricalColumns;
        public final boolean normalized;

        private Result(double[][] data, boolean headerSkipped, int categoricalColumns, boolean normalized) {
            this.data = data;
            this.headerSkipped = headerSkipped;
            this.rowCount = data == null ? 0 : data.length;
            this.columnCount = rowCount == 0 ? 0 : data[0].length;
            this.categoricalColumns = categoricalColumns;
            this.normalized = normalized;
        }
    }

    private DatasetPreprocessor() {
    }

    public static Result preprocess(List<String[]> rawRows, boolean enabled) {
        if (rawRows == null || rawRows.isEmpty()) {
            return new Result(new double[0][], false, 0, false);
        }

        if (!enabled) {
            return new Result(parseNumericStrict(rawRows), false, 0, false);
        }

        int columnCount = maxColumns(rawRows);
        List<String[]> alignedRows = alignRows(rawRows, columnCount);

        boolean headerSkipped = false;
        if (!alignedRows.isEmpty() && looksLikeHeader(alignedRows.get(0))) {
            alignedRows.remove(0);
            headerSkipped = true;
        }

        if (alignedRows.isEmpty()) {
            return new Result(new double[0][], headerSkipped, 0, false);
        }

        Map<Integer, Map<String, Integer>> categoricalMaps = new LinkedHashMap<>();
        for (int column = 0; column < columnCount; column++) {
            if (isCategoricalColumn(alignedRows, column)) {
                categoricalMaps.put(column, new LinkedHashMap<>());
            }
        }

        double[][] encoded = new double[alignedRows.size()][columnCount];
        for (int rowIndex = 0; rowIndex < alignedRows.size(); rowIndex++) {
            String[] row = alignedRows.get(rowIndex);
            for (int column = 0; column < columnCount; column++) {
                String token = safeToken(row, column);
                if (categoricalMaps.containsKey(column)) {
                    Map<String, Integer> mapping = categoricalMaps.get(column);
                    Integer existing = mapping.get(token);
                    if (existing == null) {
                        existing = mapping.size();
                        mapping.put(token, existing);
                    }
                    encoded[rowIndex][column] = existing;
                } else {
                    encoded[rowIndex][column] = parseNumber(token);
                }
            }
        }

        normalizeMinMax(encoded);
        return new Result(encoded, headerSkipped, categoricalMaps.size(), true);
    }

    private static double[][] parseNumericStrict(List<String[]> rows) {
        int columnCount = maxColumns(rows);
        List<String[]> alignedRows = alignRows(rows, columnCount);
        double[][] parsed = new double[alignedRows.size()][columnCount];
        for (int rowIndex = 0; rowIndex < alignedRows.size(); rowIndex++) {
            for (int column = 0; column < columnCount; column++) {
                parsed[rowIndex][column] = parseNumber(safeToken(alignedRows.get(rowIndex), column));
            }
        }
        return parsed;
    }

    private static int maxColumns(List<String[]> rows) {
        int max = 0;
        for (String[] row : rows) {
            if (row != null) {
                max = Math.max(max, row.length);
            }
        }
        return max;
    }

    private static List<String[]> alignRows(List<String[]> rows, int columnCount) {
        List<String[]> aligned = new ArrayList<>();
        for (String[] row : rows) {
            if (row == null) {
                continue;
            }
            String[] target = new String[columnCount];
            for (int i = 0; i < columnCount; i++) {
                target[i] = i < row.length ? sanitize(row[i]) : "0";
            }
            aligned.add(target);
        }
        return aligned;
    }

    private static String sanitize(String value) {
        if (value == null) {
            return "0";
        }
        String token = value.trim();
        if (token.isEmpty() || "?".equals(token) || "NA".equalsIgnoreCase(token) || "null".equalsIgnoreCase(token)) {
            return "0";
        }
        return token;
    }

    private static boolean looksLikeHeader(String[] firstRow) {
        if (firstRow == null || firstRow.length == 0) {
            return false;
        }
        int nonNumericCount = 0;
        for (String token : firstRow) {
            if (!isNumeric(token)) {
                nonNumericCount++;
            }
        }
        return nonNumericCount >= Math.max(1, firstRow.length / 2);
    }

    private static boolean isCategoricalColumn(List<String[]> rows, int column) {
        for (String[] row : rows) {
            if (!isNumeric(safeToken(row, column))) {
                return true;
            }
        }
        return false;
    }

    private static String safeToken(String[] row, int column) {
        if (row == null || column < 0 || column >= row.length) {
            return "0";
        }
        return sanitize(row[column]);
    }

    private static boolean isNumeric(String token) {
        try {
            Double.parseDouble(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static double parseNumber(String token) {
        try {
            return Double.parseDouble(token);
        } catch (Exception e) {
            return 0.0d;
        }
    }

    private static void normalizeMinMax(double[][] values) {
        if (values == null || values.length == 0 || values[0].length == 0) {
            return;
        }

        int rowCount = values.length;
        int columnCount = values[0].length;
        for (int column = 0; column < columnCount; column++) {
            double min = values[0][column];
            double max = values[0][column];
            for (int row = 1; row < rowCount; row++) {
                min = Math.min(min, values[row][column]);
                max = Math.max(max, values[row][column]);
            }

            double range = max - min;
            if (range == 0.0d) {
                for (int row = 0; row < rowCount; row++) {
                    values[row][column] = 0.0d;
                }
            } else {
                for (int row = 0; row < rowCount; row++) {
                    values[row][column] = (values[row][column] - min) / range;
                }
            }
        }
    }
}
