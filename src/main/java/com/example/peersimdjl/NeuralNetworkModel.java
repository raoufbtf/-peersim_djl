package com.example.peersimdjl;

import ai.djl.Device;
import ai.djl.Model;
import ai.djl.ndarray.NDArray;
import ai.djl.ndarray.NDList;
import ai.djl.ndarray.NDManager;
import ai.djl.ndarray.types.DataType;
import ai.djl.ndarray.types.Shape;
import ai.djl.nn.Activation;
import ai.djl.nn.SequentialBlock;
import ai.djl.nn.core.Linear;
import ai.djl.nn.Parameter;
import ai.djl.training.DefaultTrainingConfig;
import ai.djl.training.GradientCollector;
import ai.djl.training.Trainer;
import ai.djl.training.initializer.XavierInitializer;
import ai.djl.training.loss.L2Loss;
import ai.djl.training.loss.Loss;
import ai.djl.training.optimizer.Optimizer;
import ai.djl.training.tracker.Tracker;
import ai.djl.util.Pair;

import java.util.ArrayList;
import java.util.List;

/**
 * Petit MLP DJL réellement exécutable.
 *
 * Architecture stable pour rester compatible avec l'agrégation fédérée:
 * inputDim -> Dense(1) -> ReLU -> Dense(1) -> Sigmoid
 *
 * Le nombre total de paramètres est volontairement maintenu à:
 * parameterCount = inputDim + 3
 */
public class NeuralNetworkModel implements FederatedLocalModel {

    private static final String TAG = "[NeuralNetworkModel]";

    private final int parameterCount;
    private final int inputDim;
    private final int hiddenSize = 1;
    private final int outputSize = 1;
    private final double learningRate;

    private Model model;
    private Trainer trainer;
    private NDManager manager;

    public NeuralNetworkModel(double learningRate) {
        this(4, 1, new int[]{1}, (float) learningRate);
    }

    public NeuralNetworkModel(double learningRate, int inputDim) {
        this(Math.max(4, inputDim), 1, new int[]{1}, (float) learningRate);
    }

    public NeuralNetworkModel(int inputSize, int outputSize, int[] hiddenLayerSizes, float learningRate) {
        this.parameterCount = Math.max(4, inputSize);
        this.inputDim = Math.max(1, this.parameterCount - 3);
        this.learningRate = learningRate > 0f ? learningRate : 0.01f;
        initializeModel();
        System.out.println(TAG + " ✓ MLP DJL initialisé: " + inputDim + " -> 1 -> 1 (params=" + parameterCount + ")");
    }

    private void initializeModel() {
        closeSilently();

        manager = NDManager.newBaseManager();
        model = Model.newInstance("djl-mlp");

        SequentialBlock block = new SequentialBlock();
        block.add(Linear.builder().setUnits(hiddenSize).build());
        block.add(Activation.reluBlock());
        block.add(Linear.builder().setUnits(outputSize).build());
        block.add(Activation.sigmoidBlock());
        model.setBlock(block);

        Loss loss = new L2Loss();
        Optimizer optimizer = Optimizer.sgd()
                .setLearningRateTracker(Tracker.fixed((float) learningRate))
                .build();

        DefaultTrainingConfig config = new DefaultTrainingConfig(loss)
                .optOptimizer(optimizer)
            .optInitializer(new XavierInitializer(), Parameter.Type.WEIGHT)
                .optDevices(new Device[]{Device.cpu()});

        trainer = model.newTrainer(config);
        trainer.initialize(new Shape(1, inputDim));
    }

    public float trainBatch(double[][] features, double[][] labels) {
        if (features == null || labels == null || features.length == 0) {
            return 0f;
        }

        int samples = Math.min(features.length, labels.length);
        if (samples == 0) {
            return 0f;
        }

        double[][] x = new double[samples][inputDim];
        double[][] y = new double[samples][1];
        for (int i = 0; i < samples; i++) {
            x[i] = normalizeInput(features[i]);
            y[i][0] = labels[i] != null && labels[i].length > 0 ? labels[i][0] : 0.0;
        }

        return (float) trainInternal(x, y);
    }

    public float evaluate(double[][] features, double[][] labels) {
        if (features == null || labels == null || features.length == 0) {
            return 0f;
        }

        double[][] predictions = predict(features);
        int samples = Math.min(predictions.length, labels.length);
        int correct = 0;

        for (int i = 0; i < samples; i++) {
            int predicted = predictions[i][0] >= 0.5 ? 1 : 0;
            int actual = labels[i] != null && labels[i].length > 0 && labels[i][0] >= 0.5 ? 1 : 0;
            if (predicted == actual) {
                correct++;
            }
        }

        return (float) correct / Math.max(1, samples);
    }

    public double[][] predict(double[][] features) {
        if (features == null || features.length == 0) {
            return new double[0][1];
        }

        ensureTrainer();
        double[][] normalized = new double[features.length][inputDim];
        for (int i = 0; i < features.length; i++) {
            normalized[i] = normalizeInput(features[i]);
        }

        try (NDManager inferenceManager = manager.newSubManager()) {
            NDArray input = inferenceManager.create(normalized).toType(DataType.FLOAT32, false);
            NDList output = trainer.evaluate(new NDList(input));
            NDArray predictions = output.get(0);
            float[] flat = predictions.toFloatArray();
            double[][] result = new double[normalized.length][1];
            for (int i = 0; i < normalized.length; i++) {
                result[i][0] = i < flat.length ? flat[i] : 0.0;
            }
            return result;
        }
    }

    public void train(List<double[]> batch) {
        if (batch == null || batch.isEmpty()) {
            return;
        }

        double[][] features = new double[batch.size()][inputDim];
        double[][] labels = new double[batch.size()][1];
        for (int i = 0; i < batch.size(); i++) {
            double[] row = batch.get(i);
            features[i] = normalizeInput(row);
            labels[i][0] = row != null && row.length > 0 ? clampTarget(row[row.length - 1]) : 0.0;
        }

        trainInternal(features, labels);
    }

    public float[] getWeights() {
        double[] params = getParameters();
        float[] weights = new float[params.length];
        for (int i = 0; i < params.length; i++) {
            weights[i] = (float) params[i];
        }
        return weights;
    }

    public void setWeights(float[] weights) {
        if (weights == null || weights.length == 0) {
            return;
        }

        ensureTrainer();
        List<Parameter> parameters = getModelParameters();
        int offset = 0;

        try (NDManager paramManager = manager.newSubManager()) {
            for (Parameter parameter : parameters) {
                NDArray current = parameter.getArray();
                if (current == null) {
                    continue;
                }

                int length = (int) current.size();
                float[] slice = new float[length];
                for (int i = 0; i < length && offset < weights.length; i++) {
                    slice[i] = weights[offset++];
                }
                current.set(slice);
            }
        }
    }

    public double[] getParameters() {
        List<Parameter> parameters = getModelParameters();
        List<Double> flattened = new ArrayList<>();
        for (Parameter parameter : parameters) {
            NDArray array = parameter.getArray();
            if (array == null) {
                continue;
            }
            for (float value : array.toFloatArray()) {
                flattened.add((double) value);
            }
        }

        double[] result = new double[flattened.size()];
        for (int i = 0; i < flattened.size(); i++) {
            result[i] = flattened.get(i);
        }
        return result;
    }

    public void setParameters(double[] params) {
        if (params == null || params.length == 0) {
            return;
        }

        float[] converted = new float[params.length];
        for (int i = 0; i < params.length; i++) {
            converted[i] = (float) params[i];
        }
        setWeights(converted);
    }

    public double computeLoss() {
        return 0.0;
    }

    public String getModelType() {
        return "DJL_MLP";
    }

    @Override
    public void close() {
        closeSilently();
    }

    @Override
    public String toString() {
        return "NeuralNetworkModel{DJL-MLP,inputDim=" + inputDim + ",params=" + parameterCount + "}";
    }

    private double trainInternal(double[][] features, double[][] labels) {
        ensureTrainer();
        if (features == null || labels == null || features.length == 0) {
            return 0.0;
        }

        try (NDManager batchManager = manager.newSubManager()) {
            NDArray input = batchManager.create(features).toType(DataType.FLOAT32, false);
            NDArray target = batchManager.create(labels).toType(DataType.FLOAT32, false);

            try (GradientCollector collector = trainer.newGradientCollector()) {
                NDList prediction = trainer.forward(new NDList(input));
                NDArray lossValue = trainer.getLoss().evaluate(new NDList(target), prediction).mean();
                collector.backward(lossValue);
                trainer.step();
                float[] lossArray = lossValue.toFloatArray();
                return lossArray.length == 0 ? 0.0 : lossArray[0];
            }
        }
    }

    private void ensureTrainer() {
        if (trainer == null || model == null || manager == null) {
            initializeModel();
        }
    }

    private double[] normalizeInput(double[] row) {
        double[] input = new double[inputDim];
        if (row == null) {
            return input;
        }
        for (int i = 0; i < inputDim; i++) {
            input[i] = i < row.length ? row[i] : 0.0;
        }
        return input;
    }

    private double clampTarget(double target) {
        return target >= 0.5 ? 1.0 : 0.0;
    }

    private List<Parameter> getModelParameters() {
        List<Parameter> parameters = new ArrayList<>();
        for (Pair<String, Parameter> entry : model.getBlock().getParameters()) {
            parameters.add(entry.getValue());
        }
        return parameters;
    }

    private void closeSilently() {
        try {
            if (trainer != null) {
                trainer.close();
            }
        } catch (Exception ignored) {
        }

        try {
            if (model != null) {
                model.close();
            }
        } catch (Exception ignored) {
        }

        try {
            if (manager != null) {
                manager.close();
            }
        } catch (Exception ignored) {
        }

        trainer = null;
        model = null;
        manager = null;
    }
}
