package com.example.peersimdjl;

public interface FederatedLocalModel extends AutoCloseable {

    float trainBatch(double[][] features, double[][] labels);

    float evaluate(double[][] features, double[][] labels);

    double[][] predict(double[][] features);

    float[] getWeights();

    void setWeights(float[] weights);

    @Override
    void close();
}
