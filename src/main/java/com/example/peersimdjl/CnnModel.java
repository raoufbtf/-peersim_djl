package com.example.peersimdjl;

import ai.djl.Device;
import ai.djl.Model;
import ai.djl.ndarray.NDArray;
import ai.djl.ndarray.NDList;
import ai.djl.ndarray.NDManager;
import ai.djl.ndarray.types.DataType;
import ai.djl.ndarray.types.Shape;
import ai.djl.nn.Activation;
import ai.djl.nn.Blocks;
import ai.djl.nn.Parameter;
import ai.djl.nn.SequentialBlock;
import ai.djl.nn.convolutional.Conv2d;
import ai.djl.nn.core.Linear;
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

public class CnnModel implements FederatedLocalModel {

    private static final String TAG = "[CnnModel]";

    private final int inputDim;
    private final int inputHeight;
    private final int inputWidth;
    private final double learningRate;

    private Model model;
    private Trainer trainer;
    private NDManager manager;

    public CnnModel(int inputSize, float learningRate) {
        this.inputDim = Math.max(1, inputSize);
        this.inputHeight = 4;
        this.inputWidth = Math.max(1, (int) Math.ceil((double) inputDim / inputHeight));
        this.learningRate = learningRate > 0f ? learningRate : 0.01f;
        initializeModel();
        System.out.println(TAG + " ✓ CNN DJL initialisé: 1x" + inputHeight + "x" + inputWidth);
    }

    private void initializeModel() {
        closeSilently();

        manager = NDManager.newBaseManager();
        model = Model.newInstance("djl-cnn");

        SequentialBlock block = new SequentialBlock();
        block.add(Conv2d.builder().setFilters(4).setKernelShape(new Shape(3, 3)).optPadding(new Shape(1, 1)).build());
        block.add(Activation.reluBlock());
        block.add(Blocks.batchFlattenBlock());
        block.add(Linear.builder().setUnits(16).build());
        block.add(Activation.reluBlock());
        block.add(Linear.builder().setUnits(1).build());
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
        trainer.initialize(new Shape(1, 1, inputHeight, inputWidth));
    }

    @Override
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

    @Override
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

    @Override
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
            NDArray input = toCnnInput(normalized, inferenceManager);
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

    @Override
    public float[] getWeights() {
        List<Float> flattened = new ArrayList<>();
        for (Parameter parameter : getModelParameters()) {
            NDArray array = parameter.getArray();
            if (array == null) {
                continue;
            }
            for (float value : array.toFloatArray()) {
                flattened.add(value);
            }
        }

        float[] result = new float[flattened.size()];
        for (int i = 0; i < flattened.size(); i++) {
            result[i] = flattened.get(i);
        }
        return result;
    }

    @Override
    public void setWeights(float[] weights) {
        if (weights == null || weights.length == 0) {
            return;
        }

        ensureTrainer();
        int offset = 0;
        for (Parameter parameter : getModelParameters()) {
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

            if (offset >= weights.length) {
                break;
            }
        }
    }

    @Override
    public void close() {
        closeSilently();
    }

    private double trainInternal(double[][] features, double[][] labels) {
        ensureTrainer();
        if (features == null || labels == null || features.length == 0) {
            return 0.0;
        }

        try (NDManager batchManager = manager.newSubManager()) {
            NDArray input = toCnnInput(features, batchManager);
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

    private NDArray toCnnInput(double[][] features, NDManager localManager) {
        int samples = features.length;
        int matrixSize = inputHeight * inputWidth;
        float[] flattened = new float[samples * matrixSize];

        for (int i = 0; i < samples; i++) {
            double[] row = normalizeInput(features[i]);
            for (int j = 0; j < matrixSize; j++) {
                int index = i * matrixSize + j;
                flattened[index] = j < row.length ? (float) row[j] : 0f;
            }
        }

        return localManager.create(flattened, new Shape(samples, 1, inputHeight, inputWidth));
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
