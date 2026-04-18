package com.example.peersimdjl;

import java.util.ArrayList;
import java.util.List;

/**
 * Base commune pour les modèles locaux.
 * Fournit les utilitaires de parsing et les champs partagés.
 */
public abstract class AbstractLocalModel {

    protected final double learningRate;
    protected int inputDim = -1;
    protected double lastLoss = 0.0;
    protected List<double[]> lastBatch = new ArrayList<>();

    protected AbstractLocalModel(double learningRate) {
        this.learningRate = learningRate > 0.0 ? learningRate : 0.01;
    }

    protected void ensureInputDim(List<double[]> batch) {
        if (inputDim > 0 || batch == null) {
            return;
        }
        for (double[] row : batch) {
            if (row != null && row.length > 1) {
                inputDim = Math.max(1, row.length - 1);
                return;
            }
        }
        inputDim = 1;
    }

    protected void rememberBatch(List<double[]> batch) {
        lastBatch = batch == null ? new ArrayList<>() : new ArrayList<>(batch);
    }

    protected double[] extractFeatures(double[] row) {
        double[] features = new double[Math.max(1, inputDim)];
        for (int i = 0; i < features.length; i++) {
            features[i] = i < row.length ? row[i] : 0.0;
        }
        return features;
    }

    protected double extractTarget(double[] row) {
        if (row == null || row.length == 0) {
            return 0.0;
        }
        int index = Math.min(Math.max(0, inputDim), row.length - 1);
        return row[index];
    }

    protected double clip(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    protected double sigmoid(double value) {
        return 1.0 / (1.0 + Math.exp(-value));
    }

    public abstract void train(List<double[]> batch);

    public abstract double[] getParameters();

    public abstract void setParameters(double[] params);

    public abstract double computeLoss();

    public abstract String getModelType();
}