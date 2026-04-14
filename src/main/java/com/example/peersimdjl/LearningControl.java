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

        private LearningRunState(int runIndex) {
            this.runIndex = runIndex;
        }
    }

    // ── Configuration ────────────────────────────────────────────
    private final int pid;                              // Protocol ID
    private final DHTSessionManager dhtManager;         // Gestionnaire DHT
    private final int activeNodeCount;
    private final String datasetPath;
    
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
    @SuppressWarnings("unused")
    private int roundRobinCursor = 0;
    private final java.util.List<LearningRunState> learningRuns = new java.util.ArrayList<>();
    private boolean learningRunsInitialized = false;

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
        this.maxBatchesPerNode = Math.max(1,
                Configuration.getInt(prefix + ".maxBatchesPerNode", DEFAULT_MAX_BATCHES_PER_NODE));
        this.learningSessionCount = Math.max(1, Configuration.getInt(prefix + ".sessionCount", 1));
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

            if (!run.electionDone) {
                performElection(run);
                run.electionDone = (run.currentSession != null);
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
                transitionSessionToDone(run);
            }
        }

        return false; // Contrôle continue à chaque cycle
    }

    /**
     * Initialise l'ensemble des apprentissages qui doivent démarrer en parallèle.
     */
    private void initializeLearningRuns() {
        learningRuns.clear();
        for (int i = 0; i < learningSessionCount; i++) {
            learningRuns.add(new LearningRunState(i));
        }
        learningRunsInitialized = true;
        System.out.println("[LearningControl] ✓ Initialisation de " + learningRuns.size() + " apprentissages simultanés");
    }

    /**
     * Effectue l'élection du nœud IDE (celui avec le ChordId maximal).
     */
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

        System.out.println("[LearningControl] === CYCLE " + peersim.core.CommonState.getTime() + " : Vérification de l'élection ===");

        // Vérifier que l'IDE est toujours en vie
        boolean ideIsAlive = false;

        for (int i = 0; i < Network.size(); i++) {
            Node node = Network.get(i);
            ChordProtocol protocol = (ChordProtocol) node.getProtocol(pid);
            if (protocol != null && protocol.nodeId == run.currentSession.ideNodeId) {
                if (node.isUp()) {
                    ideIsAlive = true;
                }
                break;
            }
        }

        if (!ideIsAlive) {
            System.err.println("[LearningControl] ⚠ ERREUR : IDE Node " + run.currentSession.ideNodeIdString + " est MORT !");
            run.electionDone = false;
            run.currentSession = null;
            performElection(run);
            return;
        }

        System.out.println("[LearningControl] ✓ IDE Node toujours actif : " + run.currentSession.ideNodeIdString);

        // Sélectionner les nœuds actifs ciblés (IDE + autres nœuds libres)
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
            run.receivedDataset = buildReceivedDataset();
            if (run.receivedDataset == null || run.receivedDataset.length == 0) {
                System.err.println("[LearningControl] ✗ Dataset requis introuvable/illisible. Initialisation session annulée.");
                return;
            }
            run.distributedBatches = splitDataset(run);
            System.out.println("[LearningControl] ✓ Dataset reçu par le node IDE : " + run.receivedDataset.length + " échantillons");
            System.out.println("[LearningControl] ✓ Dataset découpé en " + run.distributedBatches.size() + " batches");
            System.out.println("[LearningControl] ✓ Stratégie d'assignation : " + batchAssignmentStrategy);
            System.out.println("[LearningControl] ✓ Capacité max par node : " + maxBatchesPerNode);
            if (datasetPath != null) {
                System.out.println("[LearningControl] ✓ Dataset chargé depuis : " + datasetPath);
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
        java.util.List<ChordProtocol> allActive = new java.util.ArrayList<>();

        for (int i = 0; i < Network.size(); i++) {
            Node node = Network.get(i);
            if (!node.isUp()) continue;

            ChordProtocol protocol = (ChordProtocol) node.getProtocol(pid);
            if (protocol != null) {
                allActive.add(protocol);
            }
        }

        ChordProtocol ideProtocol = findProtocolByChordId(run.currentSession.ideNodeId);
        java.util.List<ChordProtocol> selected = new java.util.ArrayList<>();

        if (ideProtocol != null) {
            selected.add(ideProtocol);
        }

        for (ChordProtocol protocol : allActive) {
            if (selected.size() >= activeNodeCount) break;
            if (ideProtocol != null && protocol.nodeId == ideProtocol.nodeId) continue;
            selected.add(protocol);
        }

        return selected;
    }

    /**
     * Charge le dataset depuis un fichier CSV fourni par l'utilisateur.
     * Si le fichier est absent, on retombe sur un dataset synthétique de secours.
     */
    private double[][] buildReceivedDataset() {
        if (datasetPath == null || datasetPath.trim().isEmpty()) {
            System.err.println("[LearningControl] ✗ Aucun dataset fourni. Veuillez spécifier control.learning.datasetPath.");
            return null;
        }

        double[][] fromFile = loadDatasetFromCsv(datasetPath.trim());
        if (fromFile == null || fromFile.length == 0) {
            System.err.println("[LearningControl] ✗ Dataset vide ou invalide : " + datasetPath);
            return null;
        }
        return fromFile;
    }

    /**
     * Charge un CSV numérique (lignes séparées par virgule ou point-virgule).
     */
    private double[][] loadDatasetFromCsv(String path) {
        java.util.List<double[]> rows = new java.util.ArrayList<>();

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
                double[] row = new double[parts.length];
                for (int i = 0; i < parts.length; i++) {
                    row[i] = Double.parseDouble(parts[i].trim());
                }
                rows.add(row);
            }
        } catch (Exception e) {
            System.err.println("[LearningControl] ✗ Erreur lecture dataset CSV: " + e.getMessage());
            return null;
        }

        return rows.toArray(new double[0][]);
    }

    /**
     * Découpe le dataset reçu en batches de taille fixe.
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
        int computedBatchSize = Math.max(1, (int) Math.ceil((double) run.receivedDataset.length / targetBatchCount));

        int batchIndex = 0;
        for (int start = 0; start < run.receivedDataset.length; start += computedBatchSize) {
            int end = Math.min(start + computedBatchSize, run.receivedDataset.length);
            double[][] batchData = new double[end - start][];

            for (int i = start; i < end; i++) {
                batchData[i - start] = java.util.Arrays.copyOf(run.receivedDataset[i], run.receivedDataset[i].length);
            }

            DataBatch batch = new DataBatch(run.currentSession.sessionId + "_batch_" + batchIndex, batchData);
            batches.add(batch);
            batchIndex++;
        }

        System.out.println("[LearningControl] ✓ Split dynamique: " + run.receivedDataset.length
                + " lignes, " + effectiveNodeCount + " nodes, batchSize=" + computedBatchSize
                + ", batchCount=" + batches.size());

        return batches;
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
        for (int i = 0; i < Network.size(); i++) {
            Node node = Network.get(i);
            if (!node.isUp()) continue;

            ChordProtocol protocol = (ChordProtocol) node.getProtocol(pid);
            if (protocol != null) {
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
            System.out.println("[LearningControl] === Apprentissage distribué TERMINÉ ===");
            run.completionDone = true;
        } else {
            System.err.println("[LearningControl] ✗ Erreur lors de la transition DONE");
        }
    }
}
