package com.example.peersimdjl;

import java.io.Serializable;

/**
 * Encapsule le modèle neuronal d'un nœud local.
 * Serializable pour stockage dans ChordProtocol.
 * 
 * Responsabilités:
 * - Créer et gérer une instance NeuralNetworkModel
 * - Traîner sur les batches locaux
 * - Partager/recevoir des poids fédérés
 * - Évaluer la précision locale
 */
public class LocalModelManager implements Serializable {
    
    private static final long serialVersionUID = 1L;
    private static final String TAG = "[LocalModelManager]";
    private static final boolean DEBUG = true;
    private static final double TRAIN_SPLIT_RATIO = 0.8;
    
    private final String nodeId;
    private final int inputSize;
    private final int outputSize;
    private final int[] hiddenLayers; // Ex: {128, 64}
    private final float learningRate;
    private final String modelType;
    
    // Non-serializable: recréé après désérialisation
    private transient FederatedLocalModel model;
    private transient int trainingIterations = 0;
    private transient float lastValidationAccuracy = Float.NaN;
    
    public LocalModelManager(String nodeId, int inputSize, int outputSize,
                             int[] hiddenLayers, float learningRate, String modelType) {
        this.nodeId = nodeId;
        this.inputSize = inputSize;
        this.outputSize = outputSize;
        this.hiddenLayers = hiddenLayers != null ? hiddenLayers : new int[]{128, 64};
        this.learningRate = learningRate;
        this.modelType = modelType != null ? modelType.trim().toUpperCase(java.util.Locale.ROOT) : "MLP";
        
        initializeModel();
    }
    
    /**
     * Initialise ou réinitialise le modèle neuronal.
     */
    private void initializeModel() {
        try {
            if (model != null) {
                model.close();
            }
            if ("CNN".equals(modelType)) {
                model = new CnnModel(inputSize, learningRate);
            } else {
                model = new NeuralNetworkModel(inputSize, outputSize, hiddenLayers, learningRate);
            }
            if (DEBUG) System.out.println(TAG + " [" + nodeId + "] ✓ Modèle " + modelType + " initialisé");
        } catch (Exception e) {
            System.err.println(TAG + " [" + nodeId + "] ✗ Erreur lors de l'initialisation");
            e.printStackTrace();
        }
    }
    
    /**
     * Entraîne le modèle local sur les données du nœud.
     * 
     * @param trainingData Données de training (2D)
     * @param numEpochs Nombre d'epochs locaux
     * @return Perte finale moyenne
     */
    public float trainLocalModel(double[][] trainingData, int numEpochs) {
        if (trainingData == null || trainingData.length == 0 || model == null) {
            return Float.NaN;
        }

        double[][] shuffled = java.util.Arrays.copyOf(trainingData, trainingData.length);
        java.util.Collections.shuffle(java.util.Arrays.asList(shuffled),
                new java.util.Random((long) nodeId.hashCode() + trainingIterations));

        int splitIndex = Math.max(1, (int) Math.floor(shuffled.length * TRAIN_SPLIT_RATIO));
        if (splitIndex >= shuffled.length && shuffled.length > 1) {
            splitIndex = shuffled.length - 1;
        }

        double[][] trainRows = java.util.Arrays.copyOfRange(shuffled, 0, splitIndex);
        double[][] validationRows = splitIndex < shuffled.length
                ? java.util.Arrays.copyOfRange(shuffled, splitIndex, shuffled.length)
                : trainRows;

        double[][] trainFeatures = extractFeatures(trainRows);
        double[][] trainLabels = extractLabels(trainRows);
        double[][] validationFeatures = extractFeatures(validationRows);
        double[][] validationLabels = extractLabels(validationRows);
        
        float finalLoss = 0f;
        for (int epoch = 0; epoch < numEpochs; epoch++) {
            float batchLoss = model.trainBatch(trainFeatures, trainLabels);
            finalLoss = batchLoss;
            trainingIterations++;
            if (DEBUG && epoch % Math.max(1, numEpochs/3) == 0) {
                System.out.println(TAG + " [" + nodeId + "] Epoch " + epoch + "/" + numEpochs + 
                                   " Loss: " + String.format("%.6f", finalLoss));
            }
        }

        lastValidationAccuracy = model.evaluate(validationFeatures, validationLabels);
        if (DEBUG) {
            System.out.println(TAG + " [" + nodeId + "] Validation Accuracy: "
                    + String.format("%.4f", lastValidationAccuracy)
                    + " (train=" + trainRows.length + ", val=" + validationRows.length + ")");
        }
        
        return finalLoss;
    }

    private double[][] extractFeatures(double[][] rows) {
        if (rows == null || rows.length == 0) {
            return new double[0][0];
        }

        int featureCount = 1;
        for (double[] row : rows) {
            if (row != null && row.length > 1) {
                featureCount = Math.max(1, row.length - 1);
                break;
            }
            if (row != null && row.length == 1) {
                featureCount = 1;
            }
        }

        double[][] features = new double[rows.length][featureCount];
        for (int i = 0; i < rows.length; i++) {
            double[] row = rows[i];
            if (row == null || row.length == 0) {
                continue;
            }

            int maxFeatureIndexExclusive = row.length > 1 ? row.length - 1 : row.length;
            for (int j = 0; j < featureCount; j++) {
                features[i][j] = j < maxFeatureIndexExclusive ? row[j] : 0.0;
            }
        }
        return features;
    }

    private double[][] extractLabels(double[][] rows) {
        if (rows == null || rows.length == 0) {
            return new double[0][1];
        }

        double[][] labels = new double[rows.length][1];
        for (int i = 0; i < rows.length; i++) {
            double[] row = rows[i];
            if (row == null || row.length == 0) {
                labels[i][0] = 0.0;
                continue;
            }
            double rawLabel = row[row.length - 1];
            labels[i][0] = rawLabel >= 0.5 ? 1.0 : 0.0;
        }
        return labels;
    }
    
    /**
     * Obtient les poids du modèle (pour agrégation fédérée).
     */
    public float[] getModelWeights() {
        if (model == null) {
            return new float[0];
        }
        return model.getWeights();
    }
    
    /**
     * Restaure les poids du modèle (après agrégation fédérée).
     */
    public void setModelWeights(float[] weights) {
        if (model == null) {
            initializeModel();
        }
        model.setWeights(weights);
        if (DEBUG) System.out.println(TAG + " [" + nodeId + "] ✓ Poids restaurés");
    }
    
    /**
     * Évalue la précision locale.
     */
    public float evaluateLocal(double[][] testData) {
        float acc = evaluateAccuracy(testData);
        if (DEBUG) System.out.println(TAG + " [" + nodeId + "] Local Accuracy: " + 
                                      String.format("%.4f", acc));
        return acc;
    }

    /**
     * Évalue la précision sur un jeu de données sans afficher de log.
     */
    public float evaluateAccuracy(double[][] testData) {
        if (testData == null || testData.length == 0 || model == null) {
            return 0f;
        }
        double[][] features = extractFeatures(testData);
        double[][] labels = extractLabels(testData);
        return model.evaluate(features, labels);
    }
    
    /**
     * Prédiction sur de nouvelles données.
     */
    public double[][] predict(double[][] data) {
        if (model == null) {
            return new double[0][outputSize];
        }
        return model.predict(data);
    }
    
    /**
     * Ferme les ressources du modèle.
     */
    public void close() {
        if (model != null) {
            model.close();
            model = null;
        }
    }
    
    /**
     * Gère la désérialisation: recréer le modèle après lecture.
     */
    private void readObject(java.io.ObjectInputStream in) throws java.io.IOException, ClassNotFoundException {
        in.defaultReadObject();
        trainingIterations = 0;
        lastValidationAccuracy = Float.NaN;
        initializeModel();
        if (DEBUG) System.out.println(TAG + " [" + nodeId + "] ✓ Désérialisé et modèle recréé");
    }
    
    @Override
    public String toString() {
        return "LocalModelManager{" + nodeId + 
             ", modelType=" + modelType +
             ", shape=" + inputSize + "-" + hiddenLayers[0] + "-" + outputSize + 
               ", iterations=" + trainingIterations + "}";
    }
}
