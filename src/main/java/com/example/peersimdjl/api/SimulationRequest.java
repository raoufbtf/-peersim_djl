package com.example.peersimdjl.api;

import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

public class SimulationRequest {

    @NotNull
    @Pattern(regexp = "MLP|CNN")
    private String modelType;

    @Size(min = 1)
    private String[] datasetPaths;

    @Min(1)
    private int networkSize;

    @Size(min = 1)
    private int[] sessionRequirements;

    @Min(1)
    private int federatedEpochs;

    @DecimalMin(value = "0.0001", inclusive = true)
    private double learningRate;

    @NotNull
    @Pattern(regexp = "ROUND_ROBIN|RANDOM")
    private String batchStrategy;

    @Min(1)
    private int maxBatchesPerNode;

    private boolean preprocessOnUpload;

    @Min(1)
    private int simulationCycles;

    // Getters and Setters
    public String getModelType() {
        return modelType;
    }

    public void setModelType(String modelType) {
        this.modelType = modelType;
    }

    public String[] getDatasetPaths() {
        return datasetPaths;
    }

    public void setDatasetPaths(String[] datasetPaths) {
        this.datasetPaths = datasetPaths;
    }

    public int getNetworkSize() {
        return networkSize;
    }

    public void setNetworkSize(int networkSize) {
        this.networkSize = networkSize;
    }

    public int[] getSessionRequirements() {
        return sessionRequirements;
    }

    public void setSessionRequirements(int[] sessionRequirements) {
        this.sessionRequirements = sessionRequirements;
    }

    public int getFederatedEpochs() {
        return federatedEpochs;
    }

    public void setFederatedEpochs(int federatedEpochs) {
        this.federatedEpochs = federatedEpochs;
    }

    public double getLearningRate() {
        return learningRate;
    }

    public void setLearningRate(double learningRate) {
        this.learningRate = learningRate;
    }

    public String getBatchStrategy() {
        return batchStrategy;
    }

    public void setBatchStrategy(String batchStrategy) {
        this.batchStrategy = batchStrategy;
    }

    public int getMaxBatchesPerNode() {
        return maxBatchesPerNode;
    }

    public void setMaxBatchesPerNode(int maxBatchesPerNode) {
        this.maxBatchesPerNode = maxBatchesPerNode;
    }

    public boolean isPreprocessOnUpload() {
        return preprocessOnUpload;
    }

    public void setPreprocessOnUpload(boolean preprocessOnUpload) {
        this.preprocessOnUpload = preprocessOnUpload;
    }

    public int getSimulationCycles() {
        return simulationCycles;
    }

    public void setSimulationCycles(int simulationCycles) {
        this.simulationCycles = simulationCycles;
    }
}
