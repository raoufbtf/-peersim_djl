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
    
    private final String nodeId;
    private final int inputSize;
    private final int outputSize;
    private final int[] hiddenLayers; // Ex: {128, 64}
    private final float learningRate;
    private final String modelType;
    
    // Non-serializable: recréé après désérialisation
    private transient FederatedLocalModel model;
    private transient int trainingIterations = 0;
    
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
        
        // Pour simplifier: utilise les mêmes données comme labels (autoencoder-like)
        // Ou on peut créer des labels binaires simples
        double[][] labels = generateSimpleLabels(trainingData);
        
        float finalLoss = 0f;
        for (int epoch = 0; epoch < numEpochs; epoch++) {
            float batchLoss = model.trainBatch(trainingData, labels);
            finalLoss = batchLoss;
            trainingIterations++;
            if (DEBUG && epoch % Math.max(1, numEpochs/3) == 0) {
                System.out.println(TAG + " [" + nodeId + "] Epoch " + epoch + "/" + numEpochs + 
                                   " Loss: " + String.format("%.6f", finalLoss));
            }
        }
        
        return finalLoss;
    }
    
    /**
     * Crée des labels simples à partir des données.
     * Pour test: classifie si moyenne > 0.5 (binaire).
     */
    private double[][] generateSimpleLabels(double[][] data) {
        double[][] labels = new double[data.length][outputSize];
        for (int i = 0; i < data.length; i++) {
            double mean = 0;
            for (int j = 0; j < data[i].length; j++) {
                mean += data[i][j];
            }
            mean /= data[i].length;
            
            // Classification binaire: outputSize=1
            labels[i][0] = mean > 0.5 ? 1.0 : 0.0;
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
        if (testData == null || testData.length == 0 || model == null) {
            return 0f;
        }
        double[][] labels = generateSimpleLabels(testData);
        float acc = model.evaluate(testData, labels);
        if (DEBUG) System.out.println(TAG + " [" + nodeId + "] Local Accuracy: " + 
                                      String.format("%.4f", acc));
        return acc;
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
