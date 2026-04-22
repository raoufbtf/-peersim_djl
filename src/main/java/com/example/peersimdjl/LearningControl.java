package com.example.peersimdjl;

import peersim.config.Configuration;
import peersim.core.Control;
import peersim.core.Network;
import peersim.core.Node;
import java.util.*;

/**
 * Gère l'élection du nœud IDE et l'initialisation des sessions
 * d'apprentissage distribué dans le réseau Chord P2P.
 * 
 * Responsabilités :
 * - Élection du nœud IDE (ChordId le plus élevé)
 * - Création d'une LearningSession avec métadonnées
 * - Stockage et diffusion de la session via le DHT Chord
 * - Vérification de la consistance de l'élection
 * - Transition d'état de la session (INIT → RUNNING → DONE)
 */
public class LearningControl implements Control {

    /**
     * Stratégie de distribution des batches de données entre les nœuds.
     */
    private enum BatchAssignmentStrategy {
        ROUND_ROBIN,
        RANDOM
    }

    /**
     * État complet d'une session d'apprentissage indépendante.
     */
    private static final class LearningRunState {
        private final int runIndex;
        private final SessionRequest request;
        private ActiveSession activeSession = null;
        private LearningSession currentSession = null;
        private boolean electionDone = false;
        private boolean sessionInitialized = false;
        private boolean batchesDistributed = false;
        private boolean runningTransitionDone = false;
        private boolean completionDone = false;
        private double[][] receivedDataset = null;
        private java.util.List<DataBatch> distributedBatches = new java.util.ArrayList<>();
        private java.util.List<ChordProtocol> activeParticipants = new java.util.ArrayList<>();
        private final java.util.Map<String, Integer> nodeLoadById = new java.util.LinkedHashMap<>();
        private final java.util.Random batchRandom = new java.util.Random();
        private int roundRobinCursor = 0;
        private final java.util.Map<String, float[]> localWeightsByNodeId = new java.util.LinkedHashMap<>();
        private final java.util.Map<String, Integer> datasetSizeByNodeId = new java.util.LinkedHashMap<>();
        private final java.util.Map<String, LocalModelManager> modelsByNodeId = new java.util.LinkedHashMap<>();
        private float[] previousGlobalModel = null;
        private float[] currentGlobalModel = null;
        private int federatedEpoch = 0;
        private boolean convergenceReached = false;
        private float learningRate = 0.05f;
        private AccuracyTracker accuracyTracker = new AccuracyTracker();

        private LearningRunState(int runIndex, int requestedLearners, String datasetPath) {
            this.runIndex = runIndex;
            this.request = new SessionRequest(runIndex + 1, requestedLearners, datasetPath);
        }
    }

    // ── Configuration ────────────────────────────────────────────
    private final int pid;                              // Protocol ID
    private final DHTSessionManager dhtManager;         // Gestionnaire DHT
    private final int activeNodeCount;
    private final String datasetPath;
    private final java.util.List<String> configuredDatasetPaths;
    
    // ── État de contrôle ────────────────────────────────────────
    @SuppressWarnings("unused")
    private LearningSession currentSession = null;
    @SuppressWarnings("unused")
    private boolean electionDone = false;
    @SuppressWarnings("unused")
    private boolean sessionInitialized = false;
    @SuppressWarnings("unused")
    private boolean batchesDistributed = false;
    @SuppressWarnings("unused")
    private boolean runningTransitionDone = false;
    @SuppressWarnings("unused")
    private boolean completionDone = false;
    private final int learningSessionCount;
    @SuppressWarnings("unused")
    private int completedLearningSessions = 0;

    private static final int DEFAULT_MAX_BATCHES_PER_NODE = 2;

    @SuppressWarnings("unused")
    private double[][] receivedDataset = null;
    @SuppressWarnings("unused")
    private java.util.List<DataBatch> distributedBatches = new java.util.ArrayList<>();
    @SuppressWarnings("unused")
    private java.util.List<ChordProtocol> activeParticipants = new java.util.ArrayList<>();
    @SuppressWarnings("unused")
    private final java.util.Map<String, Integer> nodeLoadById = new java.util.LinkedHashMap<>();
    @SuppressWarnings("unused")
    private final java.util.Random batchRandom = new java.util.Random();
    private final BatchAssignmentStrategy batchAssignmentStrategy;
    private final int maxBatchesPerNode;
    private final int federatedEpochLimit;
    private final float initialLearningRate;
    private final int configuredNumParams;
    private final String configuredModelType;
    private final boolean preprocessOnUpload;
    @SuppressWarnings("unused")
    private int roundRobinCursor = 0;
    private final java.util.List<LearningRunState> learningRuns = new java.util.ArrayList<>();
    private boolean learningRunsInitialized = false;
    private final SessionQueueManager sessionQueueManager = SessionQueueManager.getInstance();
    private final java.util.List<Integer> sessionNodeRequirements;

    /**
     * Construit le contrôleur d'apprentissage depuis la configuration PeerSim.
     *
     * @param prefix Préfixe de configuration (`control.learning.*`).
     */
    public LearningControl(String prefix) {
        this.pid = Configuration.getPid(prefix + ".pid");
        this.dhtManager = new DHTSessionManager(pid);
        this.activeNodeCount = Math.max(1, Configuration.getInt(prefix + ".activeNodeCount", 4));
        this.datasetPath = Configuration.getString(prefix + ".datasetPath", null);
        java.util.List<String> parsedDatasetPaths = parseDatasetPaths(
            Configuration.getString(prefix + ".datasetPaths", ""));
        if (parsedDatasetPaths.isEmpty() && this.datasetPath != null && !this.datasetPath.trim().isEmpty()) {
            parsedDatasetPaths.add(this.datasetPath.trim());
        }
        this.configuredDatasetPaths = parsedDatasetPaths;
        this.maxBatchesPerNode = Math.max(1,
                Configuration.getInt(prefix + ".maxBatchesPerNode", DEFAULT_MAX_BATCHES_PER_NODE));
        this.federatedEpochLimit = Math.max(1, Configuration.getInt(prefix + ".federatedEpochs", 3));
        this.initialLearningRate = (float) Math.max(0.0001d,
            Configuration.getDouble(prefix + ".learningRate", 0.05d));
        this.configuredNumParams = Math.max(1, Configuration.getInt(prefix + ".numParams", 4));
        this.configuredModelType = parseModelType(Configuration.getString(prefix + ".modelType", "MLP"));
        this.preprocessOnUpload = Configuration.getBoolean(prefix + ".preprocessOnUpload", true);
        this.learningSessionCount = Math.max(1, Configuration.getInt(prefix + ".sessionCount", 1));
        this.sessionNodeRequirements = parseNodeRequirements(Configuration.getString(prefix + ".sessionRequirements", ""));
        String strategyName = Configuration.getString(prefix + ".batchStrategy", "ROUND_ROBIN");
        BatchAssignmentStrategy parsedStrategy;
        try {
            parsedStrategy = BatchAssignmentStrategy.valueOf(strategyName.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (Exception e) {
            parsedStrategy = BatchAssignmentStrategy.ROUND_ROBIN;
        }
        this.batchAssignmentStrategy = parsedStrategy;
    }

    /**
     * Pilote la séquence d'apprentissage distribuée en fonction du cycle.
     *
     * @return `false` pour conserver ce contrôle actif à chaque cycle.
     */
    @Override
    public boolean execute() {
        if (!learningRunsInitialized) {
            initializeLearningRuns();
        }

        for (LearningRunState run : learningRuns) {
            if (run.completionDone) {
                continue;
            }

            if (run.activeSession == null) {
                run.activeSession = sessionQueueManager.tryStartSession(run.request);
                if (run.activeSession == null) {
                    System.out.println("[LearningControl] ⏳ Session " + run.request.sessionId
                            + " en attente: besoin=" + run.request.requiredLearners
                            + ", nœuds libres=" + countFreeNodesForRun(run));
                    continue;
                }

                run.currentSession = run.activeSession.getLearningSession();
                run.electionDone = true;
                continue;
            }

            if (run.electionDone && !run.sessionInitialized) {
                verifyAndInitializeSession(run);
                continue;
            }

            if (run.sessionInitialized && !run.batchesDistributed) {
                assignBatchToNodes(run);
                continue;
            }

            if (run.sessionInitialized && run.batchesDistributed && !run.runningTransitionDone) {
                transitionSessionToRunning(run);
                continue;
            }

            if (run.runningTransitionDone && !run.completionDone) {
                if (run.federatedEpoch >= federatedEpochLimit || run.convergenceReached) {
                    transitionSessionToDone(run);
                } else {
                    executeFederatedEpoch(run);
                }
            }
        }

        return false; // Contrôle continue à chaque cycle
    }

    /**
     * Initialise l'ensemble des apprentissages qui doivent démarrer en parallèle.
     */
    private void initializeLearningRuns() {
        learningRuns.clear();
        java.util.List<Integer> requirements = new java.util.ArrayList<>(sessionNodeRequirements);
        if (requirements.isEmpty()) {
            for (int i = 0; i < learningSessionCount; i++) {
                requirements.add(activeNodeCount);
            }
        }

        for (int i = 0; i < requirements.size(); i++) {
            String runDatasetPath = resolveDatasetPathForRun(i);
            LearningRunState run = new LearningRunState(i, requirements.get(i), runDatasetPath);
            run.learningRate = initialLearningRate;
            learningRuns.add(run);
            System.out.println("[LearningControl] ✓ Session " + (i + 1) + " dataset: " + runDatasetPath);
        }
        learningRunsInitialized = true;
        System.out.println("[LearningControl] ✓ Initialisation de " + learningRuns.size() + " apprentissages simultanés");
    }

    private String resolveDatasetPathForRun(int runIndex) {
        if (configuredDatasetPaths.isEmpty()) {
            return datasetPath;
        }
        if (runIndex < configuredDatasetPaths.size()) {
            return configuredDatasetPaths.get(runIndex);
        }
        return configuredDatasetPaths.get(configuredDatasetPaths.size() - 1);
    }

    private java.util.List<String> parseDatasetPaths(String rawValue) {
        java.util.List<String> paths = new java.util.ArrayList<>();
        if (rawValue == null || rawValue.trim().isEmpty()) {
            return paths;
        }

        String[] parts = rawValue.split(",");
        for (String part : parts) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                paths.add(trimmed);
            }
        }
        return paths;
    }

    /**
     * Parse une liste de besoins en nœuds séparés par des virgules.
     */
    private java.util.List<Integer> parseNodeRequirements(String rawValue) {
        java.util.List<Integer> counts = new java.util.ArrayList<>();
        if (rawValue == null || rawValue.trim().isEmpty()) {
            return counts;
        }

        String[] parts = rawValue.split(",");
        for (String part : parts) {
            try {
                int parsed = Integer.parseInt(part.trim());
                if (parsed > 0) {
                    counts.add(parsed);
                }
            } catch (Exception ignored) {
                // Valeur ignorée si elle n'est pas numérique.
            }
        }

        return counts;
    }

    private String parseModelType(String rawValue) {
        if (rawValue == null) {
            return "MLP";
        }
        String normalized = rawValue.trim().toUpperCase(java.util.Locale.ROOT);
        if ("CNN".equals(normalized)) {
            return "CNN";
        }
        return "MLP";
    }

    /**
     * Compte les nœuds encore disponibles pour un apprentissage.
     */
    private int countFreeNodesForRun(LearningRunState run) {
        return NodeStateManager.getInstance().getAvailableLearners().size();
    }

    /**
     * Effectue l'élection du nœud IDE (celui avec le ChordId maximal).
     */
    @SuppressWarnings("unused")
    private void performElection(LearningRunState run) {
        System.out.println("[LearningControl] === CYCLE " + peersim.core.CommonState.getTime() + " : Début de l'élection ===");

        // Trouver le nœud avec le ChordId le plus élevé
        int maxChordId = -1;
        String maxNodeIdString = null;
        Node ideNode = null;

        for (int i = 0; i < Network.size(); i++) {
            Node node = Network.get(i);
            if (!node.isUp()) continue;

            ChordProtocol protocol = (ChordProtocol) node.getProtocol(pid);
            if (protocol != null) {
                if (protocol.nodeId > maxChordId) {
                    maxChordId = protocol.nodeId;
                    maxNodeIdString = protocol.nodeIdString;
                    ideNode = node;
                }
            }
        }

        if (ideNode == null) {
            System.err.println("[LearningControl] ERREUR : Aucun nœud disponible pour l'élection !");
            return;
        }

        // Créer la session avec le nœud IDE élu
        int totalNodes = activeNodeCount;
        long currentTime = System.currentTimeMillis();
        int currentCycle = (int) peersim.core.CommonState.getTime();

        run.currentSession = new LearningSession(
            "session_" + (run.runIndex + 1) + "_" + UUID.randomUUID().toString().substring(0, 8),
                maxChordId,
                maxNodeIdString,
                totalNodes,
                currentTime,
                currentCycle
        );

        System.out.println("[LearningControl] ✓ IDE Node élu : " + maxNodeIdString + " (ChordId: " + maxChordId + ")");
        System.out.println("[LearningControl] ✓ Session créée : " + run.currentSession.sessionId);
        System.out.println("[LearningControl] ✓ Total participants ciblés : " + totalNodes);
    }

    /**
     * Vérifie que l'IDE élu est toujours actif et initialise la session
     * avec la liste des nœuds actifs.
     */
    private void verifyAndInitializeSession(LearningRunState run) {
        if (run.currentSession == null) {
            System.err.println("[LearningControl] ERREUR : Aucune session créée pour la vérification !");
            return;
        }

        System.out.println("[LearningControl] === Session " + run.currentSession.sessionId + " : initialisation ===");
        System.out.println("[LearningControl] ✓ IDE Node réservé : " + run.currentSession.ideNodeIdString);

        // Sélectionner les nœuds actifs de cette session uniquement
        run.activeParticipants = selectActiveParticipants(run);

        // Initialiser la liste des nœuds actifs dans la session
        for (ChordProtocol protocol : run.activeParticipants) {
            run.currentSession.addActiveNode(protocol.nodeId, protocol.nodeIdString);
        }

        System.out.println("[LearningControl] ✓ Nœuds actifs ciblés : " + run.currentSession.activeNodeIdStrings.size());
        System.out.println("[LearningControl]   Active nodes: " + run.currentSession.activeNodeIdStrings);

        // STOCKER LA SESSION DANS LE DHT via le gestionnaire
        boolean stored = dhtManager.storeSessionInDHT(run.currentSession);

        if (stored) {
            System.out.println("[LearningControl] ✓ Session stockée dans le DHT");
            run.receivedDataset = buildReceivedDataset(run.request.csvDataset);
            if (run.receivedDataset == null || run.receivedDataset.length == 0) {
                System.err.println("[LearningControl] ✗ Dataset requis introuvable/illisible. Initialisation session annulée.");
                return;
            }
            run.distributedBatches = splitDataset(run);
            System.out.println("[LearningControl] ✓ Dataset reçu par le node IDE : " + run.receivedDataset.length + " échantillons");
            System.out.println("[LearningControl] ✓ Dataset découpé en " + run.distributedBatches.size() + " batches");
            System.out.println("[LearningControl] ✓ Stratégie d'assignation : " + batchAssignmentStrategy);
            System.out.println("[LearningControl] ✓ Capacité max par node : " + maxBatchesPerNode);
            if (run.request.csvDataset != null) {
                System.out.println("[LearningControl] ✓ Dataset chargé depuis : " + run.request.csvDataset);
            }
            run.sessionInitialized = true;
        } else {
            System.err.println("[LearningControl] ✗ Erreur lors du stockage en DHT");
        }
    }

    /**
     * Sélectionne exactement `activeNodeCount` nœuds actifs, en incluant d'abord l'IDE.
     */
    private java.util.List<ChordProtocol> selectActiveParticipants(LearningRunState run) {
        java.util.List<ChordProtocol> selected = new java.util.ArrayList<>();

        ChordProtocol ideProtocol = findProtocolByChordId(run.currentSession.ideNodeId);
        if (ideProtocol != null) {
            selected.add(ideProtocol);
        }

        for (int learnerChordId : run.activeSession.getLearnerChordIds()) {
            ChordProtocol learnerProtocol = findProtocolByChordId(learnerChordId);
            if (learnerProtocol != null) {
                selected.add(learnerProtocol);
            }
        }

        return selected;
    }

    /**
     * Charge le dataset depuis un fichier CSV fourni par l'utilisateur.
     * Si le fichier est absent, on retombe sur un dataset synthétique de secours.
     */
    private double[][] buildReceivedDataset(String datasetPathForRun) {
        if (datasetPathForRun == null || datasetPathForRun.trim().isEmpty()) {
            System.err.println("[LearningControl] ✗ Aucun dataset fourni. Veuillez spécifier control.learning.datasetPath.");
            return null;
        }

        double[][] fromFile = loadDatasetFromCsv(datasetPathForRun.trim());
        if (fromFile == null || fromFile.length == 0) {
            System.err.println("[LearningControl] ✗ Dataset vide ou invalide : " + datasetPathForRun);
            return null;
        }
        return fromFile;
    }

    /**
     * Charge un CSV numérique (lignes séparées par virgule ou point-virgule).
     */
    private double[][] loadDatasetFromCsv(String path) {
        java.util.List<String[]> rawRows = new java.util.ArrayList<>();

        java.io.File file = new java.io.File(path);
        if (!file.exists()) {
            file = new java.io.File("c:\\Users\\Admin\\projects\\peersim-djl 3\\" + path);
        }

        if (!file.exists()) {
            System.err.println("[LearningControl] ✗ Fichier dataset introuvable : " + path);
            return null;
        }

        try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }

                String[] parts = line.split("[;,]");
                for (int i = 0; i < parts.length; i++) {
                    parts[i] = parts[i].trim();
                }
                rawRows.add(parts);
            }
        } catch (Exception e) {
            System.err.println("[LearningControl] ✗ Erreur lecture dataset CSV: " + e.getMessage());
            return null;
        }

        if (rawRows.isEmpty()) {
            return new double[0][];
        }

        DatasetPreprocessor.Result result = DatasetPreprocessor.preprocess(rawRows, preprocessOnUpload);
        if (preprocessOnUpload) {
            System.out.println("[LearningControl] ✓ Prétraitement dataset activé: header=" + result.headerSkipped
                    + ", lignes=" + result.rowCount + ", colonnes=" + result.columnCount
                    + ", colonnesCatégorielles=" + result.categoricalColumns
                    + ", normalisation=" + result.normalized);
        }
        return result.data;
    }

    /**
     * Découpe le dataset reçu en batches stratifiés pour garder une distribution
     * de labels équilibrée dans chaque batch.
     *
     * @return la liste des batches créés
     */
    private java.util.List<DataBatch> splitDataset(LearningRunState run) {
        java.util.List<DataBatch> batches = new java.util.ArrayList<>();

        if (run.receivedDataset == null || run.receivedDataset.length == 0) {
            System.err.println("[LearningControl] ✗ Dataset vide, impossible de split");
            return batches;
        }

        int effectiveNodeCount = (run.activeParticipants == null || run.activeParticipants.isEmpty())
                ? Math.max(1, activeNodeCount)
                : Math.max(1, run.activeParticipants.size());
        int targetBatchCount = Math.max(1, effectiveNodeCount);
        int effectiveBatchCount = Math.min(targetBatchCount, run.receivedDataset.length);
        java.util.List<java.util.List<double[]>> batchRows = new java.util.ArrayList<>();
        for (int i = 0; i < effectiveBatchCount; i++) {
            batchRows.add(new java.util.ArrayList<>());
        }

        java.util.Map<Integer, java.util.List<double[]>> rowsByLabel = new java.util.LinkedHashMap<>();
        for (double[] row : run.receivedDataset) {
            int label = extractLabelBucket(row);
            rowsByLabel.computeIfAbsent(label, ignored -> new java.util.ArrayList<>()).add(row);
        }

        java.util.Random shuffleRandom = new java.util.Random(20260419L + run.runIndex);
        for (java.util.List<double[]> labelRows : rowsByLabel.values()) {
            java.util.Collections.shuffle(labelRows, shuffleRandom);
            int batchCursor = 0;
            for (double[] row : labelRows) {
                batchRows.get(batchCursor % effectiveBatchCount).add(java.util.Arrays.copyOf(row, row.length));
                batchCursor++;
            }
        }

        for (int batchIndex = 0; batchIndex < batchRows.size(); batchIndex++) {
            java.util.List<double[]> rows = batchRows.get(batchIndex);
            double[][] batchData = new double[rows.size()][];
            for (int i = 0; i < rows.size(); i++) {
                batchData[i] = rows.get(i);
            }
            DataBatch batch = new DataBatch(run.currentSession.sessionId + "_batch_" + batchIndex, batchData);
            batches.add(batch);
        }

        java.util.Map<Integer, java.util.Map<Integer, Integer>> batchLabelCounts = new java.util.LinkedHashMap<>();
        for (int batchIndex = 0; batchIndex < batchRows.size(); batchIndex++) {
            java.util.Map<Integer, Integer> labelCounts = new java.util.LinkedHashMap<>();
            for (double[] row : batchRows.get(batchIndex)) {
                int label = extractLabelBucket(row);
                labelCounts.put(label, labelCounts.getOrDefault(label, 0) + 1);
            }
            batchLabelCounts.put(batchIndex, labelCounts);
        }

        System.out.println("[LearningControl] ✓ Split dynamique: " + run.receivedDataset.length
                + " lignes, " + effectiveNodeCount + " nodes, targetBatchCount=" + targetBatchCount
                + ", batchCount=" + batches.size());
        System.out.println("[LearningControl] ✓ Répartition stratifiée des labels par batch: " + batchLabelCounts);

        return batches;
    }

    private int extractLabelBucket(double[] row) {
        if (row == null || row.length == 0) {
            return 0;
        }

        double labelValue = row[row.length - 1];
        if (Double.isNaN(labelValue) || Double.isInfinite(labelValue)) {
            return 0;
        }

        return (int) Math.round(labelValue);
    }

    /**
     * Assigne chaque batch à un node libre et le stocke dans le DHT.
     * La répartition se fait en round-robin sur les nœuds actifs.
     */
    private void assignBatchToNodes(LearningRunState run) {
        if (run.currentSession == null || run.distributedBatches.isEmpty()) {
            System.err.println("[LearningControl] ✗ Aucun batch à distribuer");
            return;
        }

        java.util.List<ChordProtocol> availableProtocols = new java.util.ArrayList<>();

        // L'IDE participe aussi au traitement (rôle IDE + learner).
        ChordProtocol ideProtocol = findProtocolByChordId(run.currentSession.ideNodeId);
        if (ideProtocol != null) {
            availableProtocols.add(ideProtocol);
        }

        for (int learnerChordId : run.activeSession.getLearnerChordIds()) {
            ChordProtocol protocol = findProtocolByChordId(learnerChordId);
            if (protocol != null && !availableProtocols.contains(protocol)) {
                availableProtocols.add(protocol);
            }
        }

        if (availableProtocols.isEmpty()) {
            System.err.println("[LearningControl] ✗ Aucun node disponible pour recevoir les batches");
            return;
        }

        run.nodeLoadById.clear();
        for (ChordProtocol protocol : availableProtocols) {
            run.nodeLoadById.put(protocol.nodeIdString, 0);
        }

        System.out.println("[LearningControl] === Distribution des batches ===");

        for (int i = 0; i < run.distributedBatches.size(); i++) {
            DataBatch batch = run.distributedBatches.get(i);
            ChordProtocol target = selectTargetNode(run, availableProtocols);

            if (target == null) {
                System.err.println("[LearningControl] ✗ Aucun node libre disponible pour " + batch.batchId);
                continue;
            }

            batch.markProcessing(target.nodeId, target.nodeIdString);
            System.out.println("[LearningControl] >>> Node " + target.nodeIdString + " traite batch " + batch.batchId);

            batch.assignToNode(target.nodeId, target.nodeIdString);
            incrementLoad(run, target.nodeIdString);
            boolean stored = dhtManager.storeBatchInDHT(batch);

            if (stored) {
                System.out.println("[LearningControl] ✓ " + batch.batchId + " assigné à " + target.nodeIdString +
                        " (ChordId=" + target.nodeId + ", charge=" + run.nodeLoadById.get(target.nodeIdString) + "/" +
                        maxBatchesPerNode + ")");
            } else {
                System.err.println("[LearningControl] ✗ Impossible de stocker " + batch.batchId + " dans le DHT");
            }
        }

        System.out.println("[LearningControl] ✓ Distribution terminée : " + run.distributedBatches.size() + " batches");
        run.batchesDistributed = true;
    }

    /**
     * Sélectionne un nœud libre selon la stratégie configurée.
     * Les nœuds sont considérés libres tant qu'ils restent sous la capacité max.
     */
    private ChordProtocol selectTargetNode(LearningRunState run, java.util.List<ChordProtocol> availableProtocols) {
        java.util.List<ChordProtocol> freeNodes = new java.util.ArrayList<>();
        for (ChordProtocol protocol : availableProtocols) {
            int load = run.nodeLoadById.getOrDefault(protocol.nodeIdString, 0);
            if (load < maxBatchesPerNode) {
                freeNodes.add(protocol);
            }
        }

        java.util.List<ChordProtocol> candidates = freeNodes.isEmpty() ? availableProtocols : freeNodes;

        if (candidates.isEmpty()) {
            return null;
        }

        if (batchAssignmentStrategy == BatchAssignmentStrategy.RANDOM) {
            return candidates.get(run.batchRandom.nextInt(candidates.size()));
        }

        int size = candidates.size();
        for (int offset = 0; offset < size; offset++) {
            ChordProtocol candidate = candidates.get((run.roundRobinCursor + offset) % size);
            if (run.nodeLoadById.getOrDefault(candidate.nodeIdString, 0) < maxBatchesPerNode) {
                run.roundRobinCursor = (run.roundRobinCursor + offset + 1) % size;
                return candidate;
            }
        }

        ChordProtocol candidate = candidates.get(run.roundRobinCursor % size);
        run.roundRobinCursor = (run.roundRobinCursor + 1) % size;
        return candidate;
    }

    /**
     * Incrémente la charge locale d'un nœud pour éviter la surcharge pendant la distribution.
     */
    private void incrementLoad(LearningRunState run, String nodeIdString) {
        run.nodeLoadById.put(nodeIdString, run.nodeLoadById.getOrDefault(nodeIdString, 0) + 1);
    }

    /**
     * Retourne le protocole Chord correspondant à un ChordId.
     */
    private ChordProtocol findProtocolByChordId(int chordId) {
        for (int i = 0; i < Network.size(); i++) {
            Node node = Network.get(i);
            ChordProtocol protocol = (ChordProtocol) node.getProtocol(pid);
            if (protocol != null && protocol.nodeId == chordId && node.isUp()) {
                return protocol;
            }
        }
        return null;
    }

    /**
     * Exécute une epoch FL complète sans thread, en 7 phases ordonnées.
     */
    private void executeFederatedEpoch(LearningRunState run) {
        int epoch = run.federatedEpoch;
        java.util.List<String> nodeIds = collectParticipantNodeIds(run);
        int expectedContributors = nodeIds.size();
        int numParams = inferNumParams(run);

        System.out.println("[EPOCH " + epoch + "] ===== Federated Epoch START =====");

        for (ChordProtocol protocol : run.activeParticipants) {
            String nodeId = protocol.nodeIdString;
            float[] prevWeights = getOrInitLocalWeights(run, nodeId, numParams);
            int datasetSize = run.datasetSizeByNodeId.getOrDefault(nodeId, inferDatasetSizeForNode(run, nodeId));
            run.datasetSizeByNodeId.put(nodeId, datasetSize);

            float[] newWeights = simulateLocalTraining(run, nodeId, prevWeights, datasetSize, epoch);

            GradientPublisher publisher = new GradientPublisher(protocol, expectedContributors);
            publisher.publishGradients(prevWeights, newWeights, epoch, nodeId, datasetSize);

            run.localWeightsByNodeId.put(nodeId, newWeights);
        }

        for (ChordProtocol protocol : run.activeParticipants) {
            new DepotAggregator(protocol).checkAndAggregate(epoch);
        }

        float[] globalModel = null;
        for (ChordProtocol protocol : run.activeParticipants) {
            float[] collected = new GlobalModelCollector(protocol).collectGlobalModel(epoch, numParams, run.currentGlobalModel);
            if (collected != null) {
                globalModel = collected;
                break;
            }
        }

        if (globalModel == null) {
            System.out.println("[EPOCH " + epoch + "] Modèle global incomplet, attente prochain cycle.");
            return;
        }

        run.previousGlobalModel = run.currentGlobalModel;
        run.currentGlobalModel = java.util.Arrays.copyOf(globalModel, globalModel.length);

        int totalDatasetSize = sumDatasetSize(run);
        run.accuracyTracker.evaluateGlobal(globalModel, totalDatasetSize, epoch);

        for (ChordProtocol protocol : run.activeParticipants) {
            ConvergenceVoter voter = new ConvergenceVoter(protocol);
            ConvergenceVoter.Vote vote;
            if (run.previousGlobalModel == null) {
                vote = ConvergenceVoter.Vote.CONTINUE;
            } else {
                vote = voter.computeVote(globalModel, run.previousGlobalModel);
            }
            voter.publishVote(vote, epoch, protocol.nodeIdString);
        }

        VoteCollector.Decision decision = new VoteCollector(run.activeParticipants.get(0))
                .collectAndDecide(epoch, nodeIds);

        if (decision == VoteCollector.Decision.RESET_LR) {
            run.learningRate = Math.max(0.0001f, run.learningRate * 0.5f);
            System.out.println("[EPOCH " + epoch + "] divergence détectée -> learningRate=" + run.learningRate);
        } else if (decision == VoteCollector.Decision.STOP_CONVERGED) {
            run.convergenceReached = true;
            System.out.println("[EPOCH " + epoch + "] quorum convergent atteint.");
            compareGlobalModelOnLocalBatches(run, epoch);
        }

        for (String nodeId : nodeIds) {
            run.localWeightsByNodeId.put(nodeId, java.util.Arrays.copyOf(globalModel, globalModel.length));
        }

        run.accuracyTracker.printEpochSummary(
            run.currentSession != null ? run.currentSession.sessionId : null,
            run.request != null ? run.request.csvDataset : null,
            epoch);
        System.out.println("[EPOCH " + epoch + "] ===== Federated Epoch END =====");
        run.federatedEpoch++;
    }

    private void compareGlobalModelOnLocalBatches(LearningRunState run, int epoch) {
        if (run.currentGlobalModel == null || run.currentGlobalModel.length == 0) {
            System.out.println("[EPOCH " + epoch + "] Comparaison globale ignorée: modèle global vide");
            return;
        }

        System.out.println("[EPOCH " + epoch + "] ===== Compare global vs local on batches =====");
        for (DataBatch batch : run.distributedBatches) {
            if (batch == null || batch.data == null || batch.data.length == 0) {
                continue;
            }

            String nodeId = batch.assignedNodeIdString != null ? batch.assignedNodeIdString : batch.processingNodeIdString;
            if (nodeId == null) {
                nodeId = "unknown";
            }

            LocalModelManager model = run.modelsByNodeId.get(nodeId);
            if (model == null) {
                System.out.println("[EPOCH " + epoch + "][COMPARE] batch=" + batch.batchId + " node=" + nodeId
                        + " local model unavailable");
                continue;
            }

            float[] localWeights = model.getModelWeights();
            if (localWeights == null) {
                localWeights = new float[0];
            }

            try {
                model.setModelWeights(run.currentGlobalModel);
                float globalAccuracy = model.evaluateAccuracy(batch.data);

                model.setModelWeights(localWeights);
                float localAccuracy = model.evaluateAccuracy(batch.data);

                float delta = globalAccuracy - localAccuracy;
                System.out.printf("[EPOCH %d][COMPARE] batch=%s node=%s rows=%d localAcc=%.4f globalAcc=%.4f delta=%.4f%n",
                        epoch, batch.batchId, nodeId, batch.rowCount(), localAccuracy, globalAccuracy, delta);
            } finally {
                model.setModelWeights(localWeights);
            }
        }
        System.out.println("[EPOCH " + epoch + "] ===== End compare global vs local =====");
    }

    private java.util.List<String> collectParticipantNodeIds(LearningRunState run) {
        java.util.List<String> ids = new java.util.ArrayList<>();
        for (ChordProtocol protocol : run.activeParticipants) {
            if (protocol != null && protocol.nodeIdString != null) {
                ids.add(protocol.nodeIdString);
            }
        }
        return ids;
    }

    private int inferNumParams(LearningRunState run) {
        for (LocalModelManager model : run.modelsByNodeId.values()) {
            if (model == null) {
                continue;
            }
            float[] weights = model.getModelWeights();
            if (weights != null && weights.length > 0) {
                return weights.length;
            }
        }

        if (run.receivedDataset != null && run.receivedDataset.length > 0 && run.receivedDataset[0] != null) {
            int featureCount = Math.max(1, run.receivedDataset[0].length);
            return Math.max(configuredNumParams, featureCount);
        }
        return configuredNumParams;
    }

    private float[] getOrInitLocalWeights(LearningRunState run, String nodeId, int numParams) {
        float[] existing = run.localWeightsByNodeId.get(nodeId);
        if (existing != null && existing.length == numParams) {
            return java.util.Arrays.copyOf(existing, existing.length);
        }

        float[] base = run.currentGlobalModel;
        if (base != null && base.length == numParams) {
            return java.util.Arrays.copyOf(base, base.length);
        }

        float[] initialized = createInitialGlobalWeights(numParams, run.runIndex);
        run.currentGlobalModel = java.util.Arrays.copyOf(initialized, initialized.length);
        return initialized;
    }

    private float[] createInitialGlobalWeights(int numParams, int runIndex) {
        float[] weights = new float[Math.max(1, numParams)];
        long seed = 20260418L + runIndex;
        java.util.Random random = new java.util.Random(seed);
        for (int i = 0; i < weights.length; i++) {
            weights[i] = (random.nextFloat() - 0.5f) * 0.1f;
        }
        return weights;
    }

    private float[] simulateLocalTraining(LearningRunState run, String nodeId, float[] prevWeights, int datasetSize, int epoch) {
        // Obtenir les données d'entraînement locales du nœud
        double[][] localData = extractLocalTrainingData(run, nodeId);
        if (localData == null || localData.length == 0) {
            System.out.println("[LearningControl] ⚠ Pas de données pour " + nodeId + ", poids non changés");
            return prevWeights != null ? java.util.Arrays.copyOf(prevWeights, prevWeights.length) : new float[0];
        }

        // Récupérer ou créer le modèle local du nœud
        LocalModelManager model = run.modelsByNodeId.get(nodeId);
        if (model == null) {
            // Initialiser un nouveau modèle avec une dimension d'entrée cohérente dataset
            int inputSize = 1;
            if (localData[0] != null) {
                inputSize = Math.max(1, localData[0].length - 1);
            }
            int outputSize = 1; // Classification binaire
            int[] hiddenLayers = {128, 64};
            model = new LocalModelManager(nodeId, inputSize, outputSize, hiddenLayers, run.learningRate, configuredModelType);
            run.modelsByNodeId.put(nodeId, model);
            System.out.println("[LearningControl] ✓ Modèle " + configuredModelType + " créé pour " + nodeId +
                             ": " + inputSize + "-128-64-1");
        }
        
        // Restaurer les poids précédents du modèle global
        if (prevWeights != null && prevWeights.length > 0) {
            model.setModelWeights(prevWeights);
        }
        
        // Entraîner le modèle local sur 1-3 epochs
        int localEpochs = Math.min(3, Math.max(1, datasetSize / 50));
        float loss = model.trainLocalModel(localData, localEpochs);
        System.out.println("[LearningControl] [" + nodeId + "] Epoch " + epoch + 
                         " Training Loss: " + String.format("%.6f", loss) + 
                         " on " + localData.length + " samples");
        
        // Évaluer la précision locale
        float accuracy = model.evaluateLocal(localData);
        run.accuracyTracker.trackLocalAccuracy(nodeId, accuracy, epoch);
        
        // Retourner les nouveaux poids du modèle
        return model.getModelWeights();
    }
    
    /**
     * Extrait les données d'entraînement locales pour un nœud.
     */
    private double[][] extractLocalTrainingData(LearningRunState run, String nodeId) {
        java.util.List<double[]> localRows = new java.util.ArrayList<>();
        
        for (DataBatch batch : run.distributedBatches) {
            if (batch == null || batch.data == null) {
                continue;
            }
            if (!nodeId.equals(batch.assignedNodeIdString)) {
                continue;
            }
            for (double[] row : batch.data) {
                if (row != null) {
                    localRows.add(row);
                }
            }
        }
        
        if (localRows.isEmpty()) {
            return null;
        }
        
        double[][] result = new double[localRows.size()][];
        for (int i = 0; i < localRows.size(); i++) {
            result[i] = localRows.get(i);
        }
        return result;
    }

    private int inferDatasetSizeForNode(LearningRunState run, String nodeId) {
        int size = 0;
        for (DataBatch batch : run.distributedBatches) {
            if (batch != null && nodeId.equals(batch.assignedNodeIdString) && batch.data != null) {
                size += batch.data.length;
            }
        }
        return Math.max(1, size);
    }

    private int sumDatasetSize(LearningRunState run) {
        int total = 0;
        for (String nodeId : run.datasetSizeByNodeId.keySet()) {
            total += Math.max(0, run.datasetSizeByNodeId.getOrDefault(nodeId, 0));
        }
        return Math.max(1, total);
    }

    /**
     * Transite la session de l'état INIT vers RUNNING.
     */
    private void transitionSessionToRunning(LearningRunState run) {
        System.out.println("[LearningControl] === Transition INIT → RUNNING ===");

        // Récupérer la session depuis le DHT
        LearningSession session = dhtManager.retrieveSessionFromDHT(run.currentSession.sessionId);
        
        if (session == null) {
            System.err.println("[LearningControl] ⚠ ERREUR : Impossible de récupérer la session !");
            return;
        }

        // Transition d'état
        session.transitionToRunning();
        session.updateTimestamp((int) peersim.core.CommonState.getTime());

        // Re-stocker dans le DHT
        boolean updated = dhtManager.storeSessionInDHT(session);
        
        if (updated) {
            System.out.println("[LearningControl] ✓ Session est maintenant en état RUNNING");
            System.out.println("[LearningControl]   " + session);
            run.runningTransitionDone = true;
        } else {
            System.err.println("[LearningControl] ✗ Erreur lors de la transition RUNNING");
        }
    }

    /**
     * Transite la session de l'état RUNNING vers DONE.
     */
    private void transitionSessionToDone(LearningRunState run) {
        System.out.println("[LearningControl] === Transition RUNNING → DONE ===");

        // Récupérer la session depuis le DHT
        LearningSession session = dhtManager.retrieveSessionFromDHT(run.currentSession.sessionId);
        
        if (session == null) {
            System.err.println("[LearningControl] ⚠ ERREUR : Impossible de récupérer la session !");
            return;
        }

        // Transition d'état
        session.transitionToDone();
        session.updateTimestamp((int) peersim.core.CommonState.getTime());

        // Re-stocker dans le DHT
        boolean updated = dhtManager.storeSessionInDHT(session);
        
        if (updated) {
            System.out.println("[LearningControl] ✓ Session est maintenant DONE");
            System.out.println("[LearningControl]   " + session);

            int deletedCount = 0;
            for (DataBatch batch : run.distributedBatches) {
                if (batch == null || batch.batchId == null) {
                    continue;
                }
                boolean deleted = dhtManager.deleteBatchFromDHTAndDisk(batch.batchId);
                if (deleted) {
                    deletedCount++;
                }
            }

            System.out.println("[LearningControl] ✓ Nettoyage batchs physiques terminé : " + deletedCount + "/" + run.distributedBatches.size());
            sessionQueueManager.onSessionComplete(run.request.sessionId);
            System.out.println("[LearningControl] === Apprentissage distribué TERMINÉ ===");
            run.completionDone = true;
        } else {
            System.err.println("[LearningControl] ✗ Erreur lors de la transition DONE");
        }
    }
}
