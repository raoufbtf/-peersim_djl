package com.example.peersimdjl;

import peersim.core.Network;
import peersim.core.Node;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Gestionnaire centralisé pour les opérations DHT sur les LearningSession.
 * 
 * Fonctionnalités :
 * - Stockage et récupération avec clés uniques "session_<id>"
 * - Gestion des erreurs et retries
 * - Logging détaillé pour debugging
 * - Support des transitions d'état
 */
public class DHTSessionManager {

    private static final String SESSION_KEY_PREFIX = "session_";
    private static final String BATCH_KEY_PREFIX = "batch_";
    private static final int STALE_CYCLE_THRESHOLD = 2;  // Sessions > 2 cycles d'ancienneté = stale
    private static final boolean DEBUG = ChordProtocol.DEBUGChord;

    private final int protocolId;
    private LearningSession currentSession = null;

    public DHTSessionManager(int protocolId) {
        this.protocolId = protocolId;
    }

    // ════════════════════════════════════════════════════════════════════════
    //  PUT : STOCKER DANS LE DHT
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Stocke la session dans le DHT Chord avec distribution/réplication.
     * 
     * @param session La session à stocker
     * @return true si succès, false sinon
     */
    public boolean storeSessionInDHT(LearningSession session) {
        if (session == null) {
            System.err.println("[DHTSessionManager] ✗ Session null, impossible de stocker");
            return false;
        }

        try {
            String sessionKey = SESSION_KEY_PREFIX + session.sessionId;
            
            // Trouver le nœud IDE qui sera responsable du stockage
            Node ideNode = findNodeById(session.ideNodeId);
            if (ideNode == null) {
                System.err.println("[DHTSessionManager] ✗ Nœud IDE (ChordId=" + 
                    session.ideNodeId + ") introuvable dans le réseau");
                return false;
            }

            ChordProtocol protocol = (ChordProtocol) ideNode.getProtocol(protocolId);
            if (protocol == null) {
                System.err.println("[DHTSessionManager] ✗ Protocole Chord indisponible sur nœud IDE");
                return false;
            }

            // Incrémenter la version (pour versioning)
            session.version++;
            session.updateTimestamp((int) peersim.core.CommonState.getTime());

            // PUT dans le DHT (routing + réplication)
            protocol.put(sessionKey, session);

            if (DEBUG) {
                System.out.println("[DHTSessionManager] ✓ Session stockée avec succès");
                System.out.println("  Clé: " + sessionKey);
                System.out.println("  Nœud IDE: " + protocol.nodeIdString + " (ChordId=" + session.ideNodeId + ")");
                System.out.println("  Version: " + session.version);
                System.out.println("  État: " + session.state);
            }

            this.currentSession = session;
            return true;

        } catch (Exception e) {
            System.err.println("[DHTSessionManager] ✗ Erreur lors du PUT: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Stocke un batch de données dans le DHT avec la clé {@code batch_<id>}.
     *
     * @param batch Le batch à stocker
     * @return true si succès, false sinon
     */
    public boolean storeBatchInDHT(DataBatch batch) {
        if (batch == null) {
            System.err.println("[DHTSessionManager] ✗ Batch null, impossible de stocker");
            return false;
        }

        if (batch.data == null || batch.data.length == 0) {
            System.err.println("[DHTSessionManager] ✗ Batch vide, impossible de stocker");
            return false;
        }

        try {
            String batchKey = BATCH_KEY_PREFIX + batch.batchId;

            Node targetNode = null;
            if (batch.assignedNodeId >= 0) {
                targetNode = findNodeById(batch.assignedNodeId);
            }
            if (targetNode == null) {
                targetNode = findAnyAliveNode();
            }

            if (targetNode == null) {
                System.err.println("[DHTSessionManager] ✗ Aucun nœud disponible pour stocker le batch");
                return false;
            }

            ChordProtocol protocol = (ChordProtocol) targetNode.getProtocol(protocolId);
            if (protocol == null) {
                System.err.println("[DHTSessionManager] ✗ Protocole Chord indisponible");
                return false;
            }

            String nodeIdString = (batch.assignedNodeIdString != null && !batch.assignedNodeIdString.trim().isEmpty())
                    ? batch.assignedNodeIdString.trim()
                    : protocol.nodeIdString;

            Path nodeStorageDirectory = resolveNodeStorageDirectory(nodeIdString);
            String safeBatchId = batch.batchId.replaceAll("[^a-zA-Z0-9._-]", "_");
            Path batchCsvPath = nodeStorageDirectory.resolve("batch_" + safeBatchId + ".csv");

            saveBatchToCsv(batch, batchCsvPath);

            // Le DHT stocke uniquement le chemin du fichier physique.
            protocol.put(batchKey, batchCsvPath.toString());
            batch.markStored();

            if (DEBUG) {
                System.out.println("[DHTSessionManager] ✓ Batch stocké physiquement avec succès");
                System.out.println("  Clé: " + batchKey);
                System.out.println("  Chemin CSV: " + batchCsvPath.toAbsolutePath());
                System.out.println("  Batch: " + batch.batchId + " (" + batch.rowCount() + " lignes)");
            }

            return true;

        } catch (Exception e) {
            System.err.println("[DHTSessionManager] ✗ Erreur lors du PUT batch: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Diffuse la session à TOUS les nœuds localement (sans routing Chord).
     * Utile pour les métadonnées critiques qui doivent être connues de tous.
     * 
     * @param session La session à diffuser
     * @return nombre de nœuds ayant reçu la session
     */
    public int broadcastSessionToAllNodes(LearningSession session) {
        if (session == null) return 0;

        int count = 0;
        String sessionKey = SESSION_KEY_PREFIX + session.sessionId;

        for (int i = 0; i < Network.size(); i++) {
            try {
                Node node = Network.get(i);
                ChordProtocol protocol = (ChordProtocol) node.getProtocol(protocolId);
                
                if (protocol != null && node.isUp()) {
                    protocol.putLocal(sessionKey, session);
                    count++;
                }
            } catch (Exception e) {
                System.err.println("[DHTSessionManager] ⚠ Erreur broadcast sur nœud " + i);
            }
        }

        if (DEBUG) {
            System.out.println("[DHTSessionManager] ✓ Session broadcastée à " + count + " nœuds");
        }
        return count;
    }

    // ════════════════════════════════════════════════════════════════════════
    //  GET : RÉCUPÉRER DU DHT
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Récupère la session du DHT via routing Chord.
     * Peut être appelée depuis n'importe quel nœud du réseau.
     * 
     * @param sessionId L'ID de la session à récupérer
     * @return La session ou null si non trouvée
     */
    public LearningSession retrieveSessionFromDHT(String sessionId) {
        if (sessionId == null || sessionId.isEmpty()) {
            System.err.println("[DHTSessionManager] ✗ SessionId null ou vide");
            return null;
        }

        try {
            if (Network.size() == 0) {
                System.err.println("[DHTSessionManager] ✗ Réseau vide");
                return null;
            }

            String sessionKey = SESSION_KEY_PREFIX + sessionId;
            
            // Récupérer depuis un nœud quelconque du réseau
            // Le DHT routera automatiquement vers le nœud responsable
            Node anyNode = Network.get(0);
            ChordProtocol protocol = (ChordProtocol) anyNode.getProtocol(protocolId);

            if (protocol == null) {
                System.err.println("[DHTSessionManager] ✗ Protocole Chord indisponible");
                return null;
            }

            // GET : le DHT route automatiquement et retourne l'objet
            Object retrieved = protocol.get(sessionKey);

            if (retrieved instanceof LearningSession) {
                LearningSession session = (LearningSession) retrieved;

                // Vérifier la fraîcheur
                int currentCycle = (int) peersim.core.CommonState.getTime();
                if (session.isStale(currentCycle, STALE_CYCLE_THRESHOLD)) {
                    System.out.println("[DHTSessionManager] ⚠ Session récupérée mais obsolète");
                    System.out.println("  Âge: " + (currentCycle - session.lastUpdatedAtCycle) + " cycles");
                }

                if (DEBUG) {
                    System.out.println("[DHTSessionManager] ✓ Session récupérée du DHT");
                    System.out.println("  Clé: " + sessionKey);
                    System.out.println("  IDE Node: " + session.ideNodeIdString);
                    System.out.println("  État: " + session.state);
                    System.out.println("  Version: " + session.version);
                    System.out.println("  Participants actifs: " + session.activeNodeIdStrings.size() + "/" + 
                        session.totalParticipants);
                }

                this.currentSession = session;
                return session;

            } else if (retrieved == null) {
                System.err.println("[DHTSessionManager] ✗ Session '" + sessionId + "' non trouvée dans le DHT");
                return null;
            } else {
                System.err.println("[DHTSessionManager] ✗ Type incorrect pour clé '" + sessionKey + 
                    "': " + retrieved.getClass().getSimpleName());
                return null;
            }

        } catch (Exception e) {
            System.err.println("[DHTSessionManager] ✗ Erreur lors du GET: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Récupère la session stockée localement (sans routing).
     * Utilisé comme cache rapide.
     * 
     * @param sessionId L'ID de la session
     * @return La session (depuis cache local) ou null
     */
    public LearningSession retrieveSessionLocally(String sessionId) {
        if (sessionId == null) return null;

        try {
            String sessionKey = SESSION_KEY_PREFIX + sessionId;
            Node anyNode = Network.get(0);
            ChordProtocol protocol = (ChordProtocol) anyNode.getProtocol(protocolId);

            if (protocol == null) return null;

            Object local = protocol.getLocal(sessionKey);
            
            if (local instanceof LearningSession) {
                if (DEBUG) {
                    System.out.println("[DHTSessionManager] ✓ Session trouvée localement (cache)");
                }
                return (LearningSession) local;
            }
            return null;

        } catch (Exception e) {
            System.err.println("[DHTSessionManager] ✗ Erreur lors du GET local: " + e.getMessage());
            return null;
        }
    }

    /**
     * Récupère un batch depuis le DHT via routing Chord.
     *
     * @param batchId L'ID du batch
     * @return Le batch ou null si absent
     */
    public DataBatch retrieveBatchFromDHT(String batchId) {
        if (batchId == null || batchId.isEmpty()) {
            System.err.println("[DHTSessionManager] ✗ BatchId null ou vide");
            return null;
        }

        try {
            if (Network.size() == 0) {
                System.err.println("[DHTSessionManager] ✗ Réseau vide");
                return null;
            }

            String batchKey = BATCH_KEY_PREFIX + batchId;
            Node anyNode = Network.get(0);
            ChordProtocol protocol = (ChordProtocol) anyNode.getProtocol(protocolId);

            if (protocol == null) {
                System.err.println("[DHTSessionManager] ✗ Protocole Chord indisponible");
                return null;
            }

            Object retrieved = protocol.get(batchKey);
            if (retrieved instanceof DataBatch) {
                DataBatch batch = (DataBatch) retrieved;
                if (DEBUG) {
                    System.out.println("[DHTSessionManager] ✓ Batch récupéré du DHT");
                    System.out.println("  Clé: " + batchKey);
                    System.out.println("  Batch: " + batch);
                }
                return batch;
            }

            if (retrieved instanceof String) {
                Path batchCsvPath = Paths.get((String) retrieved);
                DataBatch batch = loadBatchFromCsv(batchId, batchCsvPath);
                if (batch != null) {
                    if (DEBUG) {
                        System.out.println("[DHTSessionManager] ✓ Batch récupéré depuis stockage physique");
                        System.out.println("  Clé: " + batchKey);
                        System.out.println("  Chemin CSV: " + batchCsvPath.toAbsolutePath());
                        System.out.println("  Lignes: " + batch.rowCount());
                    }
                    return batch;
                }
                System.err.println("[DHTSessionManager] ✗ Impossible de charger le CSV du batch: " + batchCsvPath);
                return null;
            }

            if (retrieved == null) {
                System.err.println("[DHTSessionManager] ✗ Batch '" + batchId + "' non trouvé dans le DHT");
            } else {
                System.err.println("[DHTSessionManager] ✗ Type incorrect pour batch '" + batchKey + "': " + retrieved.getClass().getSimpleName());
            }
            return null;

        } catch (Exception e) {
            System.err.println("[DHTSessionManager] ✗ Erreur lors du GET batch: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Supprime un batch stocké physiquement (CSV) et retire sa référence du DHT.
     *
     * @param batchId L'ID du batch à supprimer
     * @return true si la suppression est effectuée (ou déjà absente), false en cas d'erreur
     */
    public boolean deleteBatchFromDHTAndDisk(String batchId) {
        if (batchId == null || batchId.isEmpty()) {
            System.err.println("[DHTSessionManager] ✗ BatchId null ou vide pour suppression");
            return false;
        }

        try {
            if (Network.size() == 0) {
                System.err.println("[DHTSessionManager] ✗ Réseau vide (suppression batch impossible)");
                return false;
            }

            String batchKey = BATCH_KEY_PREFIX + batchId;
            Node anyNode = Network.get(0);
            ChordProtocol protocol = (ChordProtocol) anyNode.getProtocol(protocolId);

            if (protocol == null) {
                System.err.println("[DHTSessionManager] ✗ Protocole Chord indisponible");
                return false;
            }

            Object retrieved = protocol.get(batchKey);
            boolean diskDeleted = true;

            if (retrieved instanceof String) {
                Path csvPath = Paths.get((String) retrieved);
                if (Files.exists(csvPath)) {
                    Files.delete(csvPath);
                    if (DEBUG) {
                        System.out.println("[DHTSessionManager] ✓ CSV batch supprimé: " + csvPath.toAbsolutePath());
                    }
                }
            } else if (retrieved instanceof DataBatch) {
                // Compatibilité ancien format mémoire: rien à supprimer sur disque.
                diskDeleted = true;
            } else if (retrieved == null) {
                if (DEBUG) {
                    System.out.println("[DHTSessionManager] ⚠ Batch déjà absent du DHT: " + batchKey);
                }
                return true;
            }

            protocol.remove(batchKey);

            if (DEBUG) {
                System.out.println("[DHTSessionManager] ✓ Référence DHT supprimée: " + batchKey);
            }

            return diskDeleted;

        } catch (Exception e) {
            System.err.println("[DHTSessionManager] ✗ Erreur suppression batch: " + e.getMessage());
            return false;
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    //  TRANSITIONS D'ÉTAT
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Transite la session de INIT vers RUNNING.
     * 
     * @param sessionId L'ID de la session
     * @return true si succès, false sinon
     */
    public boolean transitionToRunning(String sessionId) {
        LearningSession session = retrieveSessionFromDHT(sessionId);
        if (session == null) {
            System.err.println("[DHTSessionManager] ✗ Session non trouvée pour transition INIT→RUNNING");
            return false;
        }

        if (session.state != LearningSession.SessionState.INIT) {
            System.err.println("[DHTSessionManager] ⚠ Session n'est pas en état INIT (état actuel: " + 
                session.state + ")");
            return false;
        }

        session.transitionToRunning();
        return storeSessionInDHT(session);
    }

    /**
     * Transite la session de RUNNING vers DONE.
     * 
     * @param sessionId L'ID de la session
     * @return true si succès, false sinon
     */
    public boolean transitionToDone(String sessionId) {
        LearningSession session = retrieveSessionFromDHT(sessionId);
        if (session == null) {
            System.err.println("[DHTSessionManager] ✗ Session non trouvée pour transition RUNNING→DONE");
            return false;
        }

        if (session.state != LearningSession.SessionState.RUNNING) {
            System.err.println("[DHTSessionManager] ⚠ Session n'est pas en état RUNNING (état actuel: " + 
                session.state + ")");
            return false;
        }

        session.transitionToDone();
        return storeSessionInDHT(session);
    }

    // ════════════════════════════════════════════════════════════════════════
    //  GESTION DES PARTICIPANTS
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Ajoute un nœud participant à la liste active.
     * 
     * @param sessionId L'ID de la session
     * @param chordId Le ChordId du nœud
     * @param nodeIdString L'identifiant permanent du nœud
     * @return true si succès
     */
    public boolean addParticipant(String sessionId, int chordId, String nodeIdString) {
        LearningSession session = retrieveSessionFromDHT(sessionId);
        if (session == null) return false;

        session.addActiveNode(chordId, nodeIdString);
        return storeSessionInDHT(session);
    }

    /**
     * Retire un nœud participant de la liste active.
     * 
     * @param sessionId L'ID de la session
     * @param chordId Le ChordId du nœud
     * @return true si succès
     */
    public boolean removeParticipant(String sessionId, int chordId) {
        LearningSession session = retrieveSessionFromDHT(sessionId);
        if (session == null) return false;

        session.removeActiveNode(chordId);
        return storeSessionInDHT(session);
    }

    // ════════════════════════════════════════════════════════════════════════
    //  UTILITAIRES
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Récupère le nœud du réseau ayant un ChordId spécifique.
     */
    private Node findNodeById(int chordId) {
        for (int i = 0; i < Network.size(); i++) {
            Node node = Network.get(i);
            ChordProtocol protocol = (ChordProtocol) node.getProtocol(protocolId);
            if (protocol != null && protocol.nodeId == chordId) {
                return node;
            }
        }
        return null;
    }

    /**
     * Retourne le premier nœud vivant du réseau.
     */
    private Node findAnyAliveNode() {
        for (int i = 0; i < Network.size(); i++) {
            Node node = Network.get(i);
            if (node != null && node.isUp()) {
                return node;
            }
        }
        return null;
    }

    /**
     * Retourne la session actuellement en cache.
     */
    public LearningSession getCurrentSession() {
        return currentSession;
    }

    /**
     * Efface le cache local.
     */
    public void clearCache() {
        currentSession = null;
    }

    /**
     * Retourne le répertoire racine du stockage physique des batchs.
     */
    private Path resolveBatchStorageRoot() {
        return Paths.get("src", "main", "resources", "stockage");
    }

    /**
     * Retourne/crée le répertoire physique d'un nœud: /resources/stockage/node<idString>
     */
    private Path resolveNodeStorageDirectory(String nodeIdString) throws IOException {
        String sanitizedNodeId = (nodeIdString == null || nodeIdString.trim().isEmpty())
                ? "unknown"
                : nodeIdString.trim().replaceAll("[^a-zA-Z0-9._-]", "_");
        Path nodeDirectory = resolveBatchStorageRoot().resolve("node" + sanitizedNodeId);
        Files.createDirectories(nodeDirectory);
        return nodeDirectory;
    }

    /**
     * Sérialise les données d'un batch dans un fichier CSV.
     */
    private void saveBatchToCsv(DataBatch batch, Path csvPath) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(csvPath)) {
            for (double[] row : batch.data) {
                if (row == null || row.length == 0) {
                    writer.newLine();
                    continue;
                }

                for (int i = 0; i < row.length; i++) {
                    if (i > 0) {
                        writer.write(",");
                    }
                    writer.write(Double.toString(row[i]));
                }
                writer.newLine();
            }
        }
    }

    /**
     * Recharge un batch depuis un fichier CSV stocké physiquement.
     */
    private DataBatch loadBatchFromCsv(String batchId, Path csvPath) {
        if (csvPath == null || !Files.exists(csvPath)) {
            return null;
        }

        java.util.List<double[]> rows = new java.util.ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(csvPath)) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }

                String[] values = line.split("[;,]");
                double[] parsedRow = new double[values.length];
                for (int i = 0; i < values.length; i++) {
                    parsedRow[i] = Double.parseDouble(values[i].trim());
                }
                rows.add(parsedRow);
            }
        } catch (Exception e) {
            System.err.println("[DHTSessionManager] ✗ Erreur lecture CSV batch: " + e.getMessage());
            return null;
        }

        DataBatch batch = new DataBatch(batchId, rows.toArray(new double[0][]));
        String nodeFolderName = csvPath.getParent() != null ? csvPath.getParent().getFileName().toString() : null;
        if (nodeFolderName != null && nodeFolderName.startsWith("node") && nodeFolderName.length() > 4) {
            batch.assignToNode(-1, nodeFolderName.substring(4));
        }
        batch.markStored();
        return batch;
    }
}
