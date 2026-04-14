package com.example.peersimdjl;

import peersim.core.*;
import peersim.cdsim.*;

public class ChordProtocol implements Protocol, CDProtocol {

    public static final int CHORD_PROTOCOL_ID = 0;

    public static boolean DEBUGChord= true 
    ; // true pour activer les logs détaillés;

    // ── Compteur statique pour générer des IDs uniques et permanents ────────
    private static int nextUniqueStringId = 0;  // N100, N101, N102, etc.

    // ── état du nœud ────────────────────────────────────────────────────────
    Node   selfNode;
    Node[] finger;
    int    m;
    int    nodeId;                    // ID interne Chord (utilisé pour calculs)
    String nodeIdString;              // ID PERMANENT et IMMUABLE (jamais réassigné, même si crash)
    Node   successor;
    Node   predecessor;
    public Node[] successorList;

    private static final int SUCCESSOR_LIST_SIZE = 3;

    private static int simCycle = 0;

    // ── DHT STORAGE ─────────────────────────────────────────────────────────
    private java.util.Map<String, Object> localStorage = new java.util.HashMap<>();

    // ── RÉPLICATION ──────────────────────────────────────────────────────────
    private static final int REPLICATION_FACTOR = 3;  // Nombre de copies par clé
    private java.util.Map<String, java.util.Set<Node>> keyReplicas = new java.util.HashMap<>();  // Clé -> Set des nœuds qui la détiennent

    // ── Méthode statique pour générer des IDs uniques et permanents ────────
    public static synchronized String generateUniqueStringId() {
        return "N" + (nextUniqueStringId++);
    }

    // ── constructeurs ────────────────────────────────────────────────────────
    public ChordProtocol() {}

    public ChordProtocol(String prefix) {
        successorList = new Node[SUCCESSOR_LIST_SIZE];
    }

    // ════════════════════════════════════════════════════════════════════════
    //  UTILITAIRES : vivacité et présence réseau
    // ════════════════════════════════════════════════════════════════════════

    /** Retourne true si le nœud est présent dans le réseau PeerSim et possède un protocole Chord. */
    public boolean isAlive(Node n) {
        if (n == null) return false;
        if (!networkContains(n)) return false;
        ChordProtocol cp = (ChordProtocol) n.getProtocol(CHORD_PROTOCOL_ID);
        return cp != null;
    }

    public boolean networkContains(Node n) {
        for (int i = 0; i < Network.size(); i++) {
            if (Network.get(i) == n) return true;
        }
        return false;
    }

    // ════════════════════════════════════════════════════════════════════════
    //  NETTOYAGE DES RÉFÉRENCES MORTES
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Supprime de toutes les structures locales (successor, predecessor,
     * successorList, finger[]) toute référence vers un nœud mort.
     * Doit être appelée AVANT stabilize() / fixFingers().
     */
    public void cleanupDeadReferences() {
        // successor
        if (successor != null && !isAlive(successor)) {
            if (DEBUGChord) System.out.println("[CLEANUP] Node " + nodeIdString + " (PeerSim=" + selfNode.getIndex()
                    + ") : successor mort supprimé (ChordId=" + chordIdOf(successor) + ")");
            successor = null;
        }

        // predecessor
        if (predecessor != null && !isAlive(predecessor)) {
            if (DEBUGChord) System.out.println("[CLEANUP] Node " + nodeIdString + " (PeerSim=" + selfNode.getIndex()
                    + ") : predecessor mort supprimé (ChordId=" + chordIdOf(predecessor) + ")");
            predecessor = null;
        }

        // successorList
        if (successorList != null) {
            for (int i = 0; i < successorList.length; i++) {
                if (successorList[i] != null && !isAlive(successorList[i])) {
                    if (DEBUGChord) System.out.println("[CLEANUP] Node " + nodeIdString + " (PeerSim=" + selfNode.getIndex()
                            + ") : successorList[" + i + "] mort supprimé (ChordId="
                            + chordIdOf(successorList[i]) + ")");
                    successorList[i] = null;
                }
            }
        }

        // finger table
        if (finger != null) {
            for (int i = 0; i < finger.length; i++) {
                if (finger[i] != null && !isAlive(finger[i])) {
                    if (DEBUGChord) System.out.println("[CLEANUP] Node " + nodeIdString + " (PeerSim=" + selfNode.getIndex()
                            + ") : finger[" + i + "] mort supprimé (ChordId="
                            + chordIdOf(finger[i]) + ")");
                    finger[i] = null;
                }
            }
        }
    }

    /** Retourne le chordId d'un nœud (même mort – pour le log). */
    private int chordIdOf(Node n) {
        if (n == null) return -1;
        try {
            ChordProtocol cp = (ChordProtocol) n.getProtocol(CHORD_PROTOCOL_ID);
            return (cp != null) ? cp.nodeId : -1;
        } catch (Exception e) { return -1; }
    }

    // ════════════════════════════════════════════════════════════════════════
    //  CYCLE CDProtocol
    // ════════════════════════════════════════════════════════════════════════

    @Override
    public void nextCycle(Node node, int protocolID) {
        simCycle++;
        cleanupDeadReferences();   // 1. purger les références mortes en premier
        stabilize();               // 2. stabiliser
        fixFingers();              // 3. corriger la finger table
        checkPredecessor();
        checkAndRepairReplication(); // 4. vérifier et réparer la réplication
    }    // ════════════════════════════════════════════════════════════════════════
    //  CLONE
    // ════════════════════════════════════════════════════════════════════════

    @Override
    public Object clone() {
        ChordProtocol clone = null;
        try {
            clone = (ChordProtocol) super.clone();
            clone.successor     = null;
            clone.predecessor   = null;
            clone.selfNode      = null;
            clone.successorList = new Node[SUCCESSOR_LIST_SIZE];
        } catch (CloneNotSupportedException e) {
            e.printStackTrace();
        }
        return clone;
    }

    // ════════════════════════════════════════════════════════════════════════
    //  LOOKUP : findSuccessor
    // ════════════════════════════════════════════════════════════════════════

    /** Recherche le successeur de {@code id} depuis {@code currentNode}. */
    public Node findSuccessor(Node currentNode, int id) {
        if (currentNode == null) return null;

        ChordProtocol currentChord = (ChordProtocol) currentNode.getProtocol(CHORD_PROTOCOL_ID);
        if (currentChord == null) return null;

        if (id == currentChord.nodeId) return currentNode;

        Node startNode  = currentNode;
        ChordProtocol startChord = currentChord;

        while (true) {
            if (currentChord.successor == null) return startNode;

            Node successorNode = currentChord.successor;

            // ── vérification vivacité ──
            if (!isAlive(successorNode)) {
                // essayer la successorList
                Node backup = findFirstAliveSuccessor(currentChord);
                if (backup != null) {
                    currentChord.successor = backup;
                    successorNode = backup;
                } else {
                    return startNode;
                }
            }

            ChordProtocol successorChord = (ChordProtocol) successorNode.getProtocol(CHORD_PROTOCOL_ID);
            if (successorChord == null) return startNode;

            int currentId   = currentChord.nodeId;
            int successorId = successorChord.nodeId;

            if (isBetween(id, currentId, successorId)) {
                return successorNode;
            }

            Node next = currentChord.closestPrecedingNode(id);
            if (next == null || next == currentNode) {
                return successorNode;
            }

            currentNode  = next;
            currentChord = (ChordProtocol) currentNode.getProtocol(CHORD_PROTOCOL_ID);

            if (currentNode == startNode) {
                // tour complet sans match → retourner le min chordId vivant
                return findMinAliveNode(startNode, startChord);
            }
        }
    }

    /** Parcourt la successorList et retourne le premier nœud vivant. */
    private Node findFirstAliveSuccessor(ChordProtocol c) {
        if (c.successorList == null) return null;
        for (Node n : c.successorList) {
            if (isAlive(n)) return n;
        }
        return null;
    }

    /** Retourne le nœud de plus petit chordId encore vivant dans l'anneau. */
    private Node findMinAliveNode(Node startNode, ChordProtocol startChord) {
        Node minNode = startNode;
        ChordProtocol minChord = startChord;
        Node scanNode = startChord.successor;
        int  steps    = 0;
        while (scanNode != null && scanNode != startNode && steps < Network.size()) {
            if (isAlive(scanNode)) {
                ChordProtocol scanChord = (ChordProtocol) scanNode.getProtocol(CHORD_PROTOCOL_ID);
                if (scanChord != null && scanChord.nodeId < minChord.nodeId) {
                    minChord = scanChord;
                    minNode  = scanNode;
                }
                scanNode = scanChord == null ? null : scanChord.successor;
            } else {
                break;
            }
            steps++;
        }
        return isAlive(minNode) ? minNode : startNode;
    }

    /** Surcharge pratique : cherche depuis selfNode. */
    public Node findSuccessor(int id) {
        if (selfNode == null) return null;
        return findSuccessor(selfNode, id);
    }

    // ════════════════════════════════════════════════════════════════════════
    //  closestPrecedingNode
    // ════════════════════════════════════════════════════════════════════════

    public Node closestPrecedingNode(int id) {
        if (finger == null || finger.length == 0 || successor == null) return successor;

        for (int i = m - 1; i >= 0; i--) {
            if (i >= finger.length) continue;
            Node fn = finger[i];
            if (fn == null || !isAlive(fn)) continue;
            ChordProtocol fnChord = (ChordProtocol) fn.getProtocol(CHORD_PROTOCOL_ID);
            if (fnChord == null) continue;
            if (inIntervalOpen(fnChord.nodeId, this.nodeId, id)) return fn;
        }
        return isAlive(successor) ? successor : selfNode;
    }

    // ════════════════════════════════════════════════════════════════════════
    //  HELPERS intervalle
    // ════════════════════════════════════════════════════════════════════════

    /** (start, end] avec wrap-around. */
    private boolean isBetween(int id, int start, int end) {
        if (start < end) return id > start && id <= end;
        else             return id > start || id <= end;
    }

    /** (start, end) ouvert avec wrap-around. */
    private static boolean inIntervalOpen(int id, int start, int end) {
        if (start < end)      return id > start && id < end;
        else if (start > end) return id > start || id < end;
        else                  return false;
    }

    /** (start, end] utilisé en statique dans InitControl. */
    public static boolean inInterval(int id, int start, int end) {
        if (start < end)      return id > start && id <= end;
        else if (start > end) return id > start || id <= end;
        else                  return true; // full ring
    }

    // ════════════════════════════════════════════════════════════════════════
    //  STABILIZATION
    // ════════════════════════════════════════════════════════════════════════

    public void stabilize() {
        // ── récupérer un successeur vivant ──────────────────────────────────
        if (!isAlive(successor)) {
            Node backup = findFirstAliveSuccessor(this);
            if (backup != null) {
                successor = backup;
                if (DEBUGChord) System.out.println("[STABILIZE] Node " + nodeIdString + " (PeerSim=" + selfNode.getIndex()
                        + ") bascule sur backup successor ChordId="
                        + chordIdOf(backup));
            } else {
                // seul survivant
                successor  = selfNode;
                predecessor = selfNode;
                return;
            }
        }

        updateSuccessorList();

        ChordProtocol succChord = (ChordProtocol) successor.getProtocol(CHORD_PROTOCOL_ID);
        if (succChord == null) return;

        Node x = succChord.predecessor;
        if (x != null && isAlive(x)) {
            ChordProtocol xChord = (ChordProtocol) x.getProtocol(CHORD_PROTOCOL_ID);
            if (xChord != null && isBetween(xChord.nodeId, this.nodeId, succChord.nodeId)) {
                successor        = x;
                successorList[0] = successor;
            }
        }

        if (isAlive(successor)) {
            succChord = (ChordProtocol) successor.getProtocol(CHORD_PROTOCOL_ID);
            if (succChord != null) succChord.notifyPredecessor(selfNode);
        }
    }

    public void notifyPredecessor(Node n) {
        if (n == null) return;
        if (predecessor == null || !isAlive(predecessor)) {
            predecessor = n;
            return;
        }
        ChordProtocol predChord = (ChordProtocol) predecessor.getProtocol(CHORD_PROTOCOL_ID);
        ChordProtocol nChord    = (ChordProtocol) n.getProtocol(CHORD_PROTOCOL_ID);
        if (predChord == null || nChord == null) return;
        if (isBetween(nChord.nodeId, predChord.nodeId, this.nodeId)) {
            predecessor = n;
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    //  FINGER TABLE
    // ════════════════════════════════════════════════════════════════════════

    private int nextFinger = 0;

    public void fixFingers() {
        if (finger == null || finger.length == 0 || m <= 0) return;
        nextFinger = (nextFinger + 1) % m;
        int start = (nodeId + (1 << nextFinger)) % (1 << m);

        // ── si l'entrée actuelle est morte, recalculer ─────────────────────
        if (finger[nextFinger] != null && !isAlive(finger[nextFinger])) {
            if (DEBUGChord) System.out.println("[FIXFINGER] Node " + nodeId
                    + " : finger[" + nextFinger + "] mort, recalcul…");
            finger[nextFinger] = null;
        }

        Node succ = findSuccessor(start);
        // Toujours stocker un nœud vivant (jamais null, jamais mort)
        if (succ != null && isAlive(succ)) {
            finger[nextFinger] = succ;
        } else if (isAlive(successor)) {
            finger[nextFinger] = successor;
        } else {
            finger[nextFinger] = selfNode;
        }
    }

    public void rebuildFingerTable() {
        if (finger == null || m <= 0 || selfNode == null) return;
        // S'assurer que le tableau a la bonne taille
        if (finger.length != m) finger = new Node[m];
        int ringSize = 1 << m;
        for (int i = 0; i < m; i++) {
            int start = (nodeId + (1 << i)) % ringSize;
            Node succ = findSuccessor(start);
            finger[i] = (succ != null && isAlive(succ))
                        ? succ
                        : (isAlive(successor) ? successor : selfNode);
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    //  PREDECESSOR CHECK
    // ════════════════════════════════════════════════════════════════════════

    public void checkPredecessor() {
        if (predecessor != null && !isAlive(predecessor)) {
            if (DEBUGChord) System.out.println("[CHECKPRED] Node " + nodeId
                    + " : predecessor mort (chordId=" + chordIdOf(predecessor) + ") → null");
            predecessor = null;
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    //  JOIN / LEAVE
    // ════════════════════════════════════════════════════════════════════════

    public void join(Node n0) {
        if (n0 == null) {
            predecessor  = null;
            successor    = selfNode;
            successorList[0] = selfNode;
            return;
        }
        ChordProtocol n0Chord = (ChordProtocol) n0.getProtocol(CHORD_PROTOCOL_ID);
        successor = n0Chord.findSuccessor(nodeId);
        if (successorList == null) successorList = new Node[SUCCESSOR_LIST_SIZE];
        successorList[0] = successor;
        updateSuccessorList();

        if (successor != null && isAlive(successor)) {
            ChordProtocol succChord = (ChordProtocol) successor.getProtocol(CHORD_PROTOCOL_ID);
            if (succChord != null) {
                succChord.predecessor = selfNode;
                // 2. Migrer les données qui m'appartiennent maintenant
                migrateDataFromSuccessor(succChord);
            }
        }

        if (DEBUGChord) {
            System.out.println("Node " + nodeIdString + " joined the network and migrated data");
        }
    }

    /**
     * Migre les données du successeur qui m'appartiennent maintenant
     */
    private void migrateDataFromSuccessor(ChordProtocol successorChord) {
        synchronized (successorChord.localStorage) {
            java.util.Map<String, Object> keysToMigrate = new java.util.HashMap<>();

            // Identifier les clés qui doivent être migrées (celles entre predecessor et moi)
            for (java.util.Map.Entry<String, Object> entry : successorChord.localStorage.entrySet()) {
                String logicalKey = entry.getKey();
                int keyId;
                try {
                    keyId = Integer.parseInt(hashKey(logicalKey));
                } catch (NumberFormatException ex) {
                    continue;
                }

                // Une clé doit être migrée si elle appartient maintenant à ce nœud
                if (isKeyMine(keyId)) {
                    keysToMigrate.put(logicalKey, entry.getValue());
                }
            }

            // Migrer les clés identifiées
            for (java.util.Map.Entry<String, Object> entry : keysToMigrate.entrySet()) {
                String logicalKey = entry.getKey();
                Object value = entry.getValue();

                // Stocker localement
                putLocal(logicalKey, value);
                // Supprimer du successeur
                successorChord.removeLocal(logicalKey);

                if (DEBUGChord) {
                    System.out.println("DATA MIGRATION: Key " + logicalKey + " migrated from Node " +
                        successorChord.nodeIdString + " to Node " + nodeIdString);
                }
            }
        }
    }

    /**
     * Vérifie si une clé appartient à ce nœud (entre predecessor et nodeId)
     */
    private boolean isKeyMine(int keyId) {
        if (predecessor == null) {
            // Je suis le premier nœud, toutes les clés m'appartiennent sauf celles du dernier nœud
            return keyId >= nodeId || keyId < getLastNodeId();
        }

        ChordProtocol predChord = (ChordProtocol) predecessor.getProtocol(CHORD_PROTOCOL_ID);
        if (predChord == null) return false;

        // La clé m'appartient si elle est dans (predecessor.nodeId, nodeId]
        return isBetween(keyId, predChord.nodeId, nodeId);
    }

    /**
     * Obtient l'ID du dernier nœud dans l'anneau (pour le premier nœud)
     */
    private int getLastNodeId() {
        int maxId = 0;
        for (int i = 0; i < Network.size(); i++) {
            ChordProtocol cp = (ChordProtocol) Network.get(i).getProtocol(CHORD_PROTOCOL_ID);
            if (cp != null && cp.nodeId > maxId) {
                maxId = cp.nodeId;
            }
        }
        return maxId;
    }

    public void leave() {
        // 1. Migrer les données vers le successeur avant de partir
        if (successor != null && isAlive(successor)) {
            ChordProtocol succChord = (ChordProtocol) successor.getProtocol(CHORD_PROTOCOL_ID);
            if (succChord != null) {
                migrateDataToSuccessor(succChord);
            }
        }

        // 2. Quitter l'anneau comme avant
        if (predecessor != null && successor != null) {
            ChordProtocol predChord = (ChordProtocol) predecessor.getProtocol(CHORD_PROTOCOL_ID);
            ChordProtocol succChord = (ChordProtocol) successor.getProtocol(CHORD_PROTOCOL_ID);
            if (predChord != null) predChord.successor   = successor;
            if (succChord != null) succChord.predecessor = predecessor;
        }
        successor   = null;
        predecessor = null;

        if (DEBUGChord) {
            System.out.println("Node " + nodeIdString + " left the network and migrated data");
        }
    }

    /**
     * Migre toutes les données locales vers le successeur
     */
    private void migrateDataToSuccessor(ChordProtocol successorChord) {
        synchronized (localStorage) {
            for (java.util.Map.Entry<String, Object> entry : localStorage.entrySet()) {
                String logicalKey = entry.getKey();
                Object value = entry.getValue();

                successorChord.putLocal(logicalKey, value);

                if (DEBUGChord) {
                    System.out.println("DATA MIGRATION: Key " + logicalKey + " migrated to successor Node " +
                        successorChord.nodeIdString + " from leaving Node " + nodeIdString);
                }
            }
            // Vider le stockage local
            localStorage.clear();
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    //  TRAITEMENT PAR NODE
    // ════════════════════════════════════════════════════════════════════════

    public void traitementparnode() {
        if (!DEBUGChord) return;
        for (int i = 1; i <= 10; i++) {
            System.out.println("Node " + nodeIdString + " traitementparnode: " + i);
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    //  DHT - DISTRIBUTED HASH TABLE
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Hash une clé dans l'espace [0, 2^m[
     */
    private String hashKey(String key) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
            byte[] hashBytes = md.digest(key.getBytes());
            // Convertir en entier positif et modulo 2^m
            int hashInt = Math.abs(new java.math.BigInteger(1, hashBytes).intValue()) % (1 << m);
            return String.valueOf(hashInt);
        } catch (java.security.NoSuchAlgorithmException e) {
            // Fallback simple si MD5 n'est pas disponible
            return String.valueOf(Math.abs(key.hashCode()) % (1 << m));
        }
    }

    /**
     * Stockage local d'une paire clé-valeur
     */
    public void putLocal(String key, Object value) {
        synchronized (localStorage) {
            localStorage.put(key, value);
        }
    }

    /**
     * Récupération locale d'une valeur
     */
    public Object getLocal(String key) {
        synchronized (localStorage) {
            return localStorage.get(key);
        }
    }

    /**
     * Suppression locale d'une clé
     */
    public Object removeLocal(String key) {
        synchronized (localStorage) {
            return localStorage.remove(key);
        }
    }

    /**
     * Vérifie si une clé existe localement
     */
    public boolean containsLocal(String key) {
        synchronized (localStorage) {
            return localStorage.containsKey(key);
        }
    }

    /**
     * PUT distribué : stocke une valeur dans la DHT
     */
    public void put(String key, Object value) {
        String hashedKey = hashKey(key);
        int keyId = Integer.parseInt(hashedKey);
        Node responsibleNode = findSuccessor(keyId);

        if (responsibleNode == selfNode) {
            // Stockage local
            putLocal(key, value);
            if (DEBUGChord) {
                System.out.println("DHT PUT: Node " + nodeIdString + " stored key=" + key + " (hash=" + hashedKey + ") locally");
            }
        } else {
            // Envoi au nœud responsable (simulation simple pour PeerSim)
            ChordProtocol responsibleChord = (ChordProtocol) responsibleNode.getProtocol(CHORD_PROTOCOL_ID);
            responsibleChord.putLocal(key, value);
            if (DEBUGChord) {
                System.out.println("DHT PUT: Node " + nodeIdString + " forwarded key=" + key + " (hash=" + hashedKey + ") to Node " + responsibleChord.nodeIdString);
            }
        }
    }

    /**
     * GET distribué : récupère une valeur de la DHT
     */
    public Object get(String key) {
        String hashedKey = hashKey(key);
        int keyId = Integer.parseInt(hashedKey);
        Node responsibleNode = findSuccessor(keyId);

        if (responsibleNode == selfNode) {
            // Récupération locale
            Object value = getLocal(key);
            if (DEBUGChord) {
                System.out.println("DHT GET: Node " + nodeIdString + " retrieved key=" + key + " (hash=" + hashedKey + ") locally: " + value);
            }
            return value;
        } else {
            // Demande au nœud responsable (simulation simple pour PeerSim)
            ChordProtocol responsibleChord = (ChordProtocol) responsibleNode.getProtocol(CHORD_PROTOCOL_ID);
            Object value = responsibleChord.getLocal(key);
            if (DEBUGChord) {
                System.out.println("DHT GET: Node " + nodeIdString + " retrieved key=" + key + " (hash=" + hashedKey + ") from Node " + responsibleChord.nodeIdString + ": " + value);
            }
            return value;
        }
    }

    /**
     * REMOVE distribué : supprime une valeur de la DHT
     */
    public Object remove(String key) {
        String hashedKey = hashKey(key);
        int keyId = Integer.parseInt(hashedKey);
        Node responsibleNode = findSuccessor(keyId);

        if (responsibleNode == selfNode) {
            // Suppression locale
            Object value = removeLocal(key);
            if (DEBUGChord) {
                System.out.println("DHT REMOVE: Node " + nodeIdString + " removed key=" + key + " (hash=" + hashedKey + ") locally");
            }
            return value;
        } else {
            // Suppression sur le nœud responsable
            ChordProtocol responsibleChord = (ChordProtocol) responsibleNode.getProtocol(CHORD_PROTOCOL_ID);
            Object value = responsibleChord.removeLocal(key);
            if (DEBUGChord) {
                System.out.println("DHT REMOVE: Node " + nodeIdString + " removed key=" + key + " (hash=" + hashedKey + ") from Node " + responsibleChord.nodeIdString);
            }
            return value;
        }
    }

    /**
     * CONTAINS distribué : vérifie si une clé existe dans la DHT
     */
    public boolean contains(String key) {
        String hashedKey = hashKey(key);
        int keyId = Integer.parseInt(hashedKey);
        Node responsibleNode = findSuccessor(keyId);

        if (responsibleNode == selfNode) {
            return containsLocal(key);
        } else {
            ChordProtocol responsibleChord = (ChordProtocol) responsibleNode.getProtocol(CHORD_PROTOCOL_ID);
            return responsibleChord.containsLocal(key);
        }
    }

    /**
     * Affiche le contenu local du stockage DHT
     */
    public void printLocalStorage() {
        if (!DEBUGChord) return;
        synchronized (localStorage) {
            System.out.println("Node " + nodeIdString + " local storage (" + localStorage.size() + " entries):");
            for (java.util.Map.Entry<String, Object> entry : localStorage.entrySet()) {
                System.out.println("  " + entry.getKey() + " -> " + entry.getValue());
            }
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    //  RÉPLICATION ET TOLÉRANCE AUX PANNES
    // ════════════════════════════════════════════════════════════════════════

    /**
     * PUT avec réplication : stocke sur le nœud responsable + k successeurs
     */
    public void putReplicated(String key, Object value) {
        String hashedKey = hashKey(key);
        int keyId = Integer.parseInt(hashedKey);

        // 1. Trouver le nœud responsable principal
        Node primaryNode = findSuccessor(keyId);
        if (primaryNode == null) return;

        // 2. Collecter les k+1 nœuds (responsable + k successeurs)
        java.util.List<Node> replicaNodes = getReplicaNodes(primaryNode, REPLICATION_FACTOR);

        // 3. Stocker sur tous les nœuds de réplication
        for (Node replicaNode : replicaNodes) {
            if (replicaNode != null && isAlive(replicaNode)) {
                ChordProtocol replicaChord = (ChordProtocol) replicaNode.getProtocol(CHORD_PROTOCOL_ID);
                if (replicaChord != null) {
                    replicaChord.putLocal(key, value);
                    // Enregistrer cette réplique
                    replicaChord.registerReplica(key, selfNode);
                }
            }
        }

        if (DEBUGChord) {
            System.out.println("DHT PUT REPLICATED: key=" + key + " (hash=" + hashedKey + ") stored on " + replicaNodes.size() + " nodes");
        }
    }

    /**
     * GET avec réplication : récupère depuis n'importe quelle réplique disponible
     */
    public Object getReplicated(String key) {
        String hashedKey = hashKey(key);
        int keyId = Integer.parseInt(hashedKey);

        // 1. Trouver le nœud responsable principal
        Node primaryNode = findSuccessor(keyId);
        if (primaryNode == null) return null;

        // 2. Essayer de récupérer depuis les répliques (dans l'ordre)
        java.util.List<Node> replicaNodes = getReplicaNodes(primaryNode, REPLICATION_FACTOR);

        for (Node replicaNode : replicaNodes) {
            if (replicaNode != null && isAlive(replicaNode)) {
                ChordProtocol replicaChord = (ChordProtocol) replicaNode.getProtocol(CHORD_PROTOCOL_ID);
                if (replicaChord != null) {
                    Object value = replicaChord.getLocal(key);
                    if (value != null) {
                        if (DEBUGChord) {
                            System.out.println("DHT GET REPLICATED: key=" + key + " (hash=" + hashedKey + ") retrieved from Node " + replicaChord.nodeIdString);
                        }
                        return value;
                    }
                }
            }
        }

        if (DEBUGChord) {
            System.out.println("DHT GET REPLICATED: key=" + key + " (hash=" + hashedKey + ") not found");
        }
        return null;
    }

    /**
     * Obtient la liste des nœuds de réplication (responsable + k successeurs)
     */
    private java.util.List<Node> getReplicaNodes(Node primaryNode, int k) {
        java.util.List<Node> replicas = new java.util.ArrayList<>();
        replicas.add(primaryNode);

        Node current = primaryNode;
        for (int i = 0; i < k; i++) {
            if (current != null && isAlive(current)) {
                ChordProtocol currentChord = (ChordProtocol) current.getProtocol(CHORD_PROTOCOL_ID);
                if (currentChord != null) {
                    current = currentChord.successor;
                    if (current != null && !replicas.contains(current)) {
                        replicas.add(current);
                    }
                }
            }
        }

        return replicas;
    }

    /**
     * Enregistre qu'un nœud détient une réplique de cette clé
     */
    private void registerReplica(String logicalKey, Node replicaNode) {
        synchronized (keyReplicas) {
            keyReplicas.computeIfAbsent(logicalKey, k -> new java.util.HashSet<>()).add(replicaNode);
        }
    }

    /**
     * Vérifie et répare la réplication pour toutes les clés locales
     */
    public void checkAndRepairReplication() {
        synchronized (localStorage) {
            for (String logicalKey : localStorage.keySet()) {
                int keyId;
                try {
                    keyId = Integer.parseInt(hashKey(logicalKey));
                } catch (NumberFormatException ex) {
                    continue;
                }
                Node primaryNode = findSuccessor(keyId);

                if (primaryNode == selfNode) {
                    // Je suis le nœud responsable, vérifier la réplication
                    java.util.List<Node> expectedReplicas = getReplicaNodes(selfNode, REPLICATION_FACTOR);
                    Object value = localStorage.get(logicalKey);

                    for (Node replicaNode : expectedReplicas) {
                        if (replicaNode != selfNode && replicaNode != null && isAlive(replicaNode)) {
                            ChordProtocol replicaChord = (ChordProtocol) replicaNode.getProtocol(CHORD_PROTOCOL_ID);
                            if (replicaChord != null && replicaChord.getLocal(logicalKey) == null) {
                                // Réplique manquante, la restaurer
                                replicaChord.putLocal(logicalKey, value);
                                if (DEBUGChord) {
                                    System.out.println("REPLICATION REPAIR: Restored key " + logicalKey + " on Node " + replicaChord.nodeIdString);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    //  SUCCESSOR LIST
    // ════════════════════════════════════════════════════════════════════════

    private void updateSuccessorList() {
        if (successorList == null) successorList = new Node[SUCCESSOR_LIST_SIZE];
        Node current = successor;
        for (int i = 0; i < SUCCESSOR_LIST_SIZE; i++) {
            if (current == null || !isAlive(current)) {
                successorList[i] = null;
                continue;
            }
            successorList[i] = current;
            ChordProtocol cp = (ChordProtocol) current.getProtocol(CHORD_PROTOCOL_ID);
            if (cp == null) break;
            current = cp.successor;
        }

        if (DEBUGChord) {
            StringBuilder sb = new StringBuilder("Node " + nodeIdString + " (PeerSim=" + selfNode.getIndex() + ") successorList: [");
            for (int j = 0; j < SUCCESSOR_LIST_SIZE; j++) {
                Node n = successorList[j];
                if (n != null) {
                    ChordProtocol cp = (ChordProtocol) n.getProtocol(CHORD_PROTOCOL_ID);
                    sb.append(cp != null ? cp.nodeIdString : "null").append(" ");
                } else {
                    sb.append("null ");
                }
            }
            sb.append("]");
            System.out.println(sb.toString());
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    //  MISE À JOUR GLOBALE (après changement topologie)
    // ════════════════════════════════════════════════════════════════════════

    public static void updateAllNodes() {
        // Tri par chordId pour reconstruire l'anneau dans le bon ordre
        int size = Network.size();
        Node[] sorted = new Node[size];
        for (int i = 0; i < size; i++) sorted[i] = Network.get(i);
        java.util.Arrays.sort(sorted, (a, b) -> {
            ChordProtocol ca = (ChordProtocol) a.getProtocol(CHORD_PROTOCOL_ID);
            ChordProtocol cb = (ChordProtocol) b.getProtocol(CHORD_PROTOCOL_ID);
            return Integer.compare(ca != null ? ca.nodeId : 0, cb != null ? cb.nodeId : 0);
        });

        for (int i = 0; i < size; i++) {
            ChordProtocol cp = (ChordProtocol) sorted[i].getProtocol(CHORD_PROTOCOL_ID);
            if (cp == null) continue;
            cp.successor   = sorted[(i + 1) % size];
            cp.predecessor = sorted[(i - 1 + size) % size];
            cp.rebuildFingerTable();
        }
        for (int i = 0; i < size; i++) {
            ChordProtocol cp = (ChordProtocol) sorted[i].getProtocol(CHORD_PROTOCOL_ID);
            if (cp != null) { cp.stabilize(); cp.fixFingers(); }
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    //  DEBUGChord : affichage réseau et finger tables
    // ════════════════════════════════════════════════════════════════════════

    public static void printNetworkState() {
        if (!DEBUGChord) return;

        System.out.println("── Network state (cycle " + simCycle + ") ──");
        for (int i = 0; i < Network.size(); i++) {
            Node n = Network.get(i);
            ChordProtocol cp = (ChordProtocol) n.getProtocol(CHORD_PROTOCOL_ID);
            if (cp == null) continue;
            String succStr = (cp.successor   != null) ? ((ChordProtocol) cp.successor  .getProtocol(CHORD_PROTOCOL_ID)).nodeIdString : "null";
            String predStr = (cp.predecessor != null) ? ((ChordProtocol) cp.predecessor.getProtocol(CHORD_PROTOCOL_ID)).nodeIdString : "null";
            System.out.println("Node " + cp.nodeIdString + " (PeerSim=" + cp.selfNode.getIndex() + ")"
                    + " -> succ: " + succStr
                    + " pred: "    + predStr);
        }
    }

    public static void printFingerTables() {
        if (!DEBUGChord) return;  // Pas d'affichage des finger tables en mode simple

        System.out.println("── Finger tables (network size=" + Network.size() + ") ──");
        for (int i = 0; i < Network.size(); i++) {
            Node n = Network.get(i);
            ChordProtocol cp = (ChordProtocol) n.getProtocol(CHORD_PROTOCOL_ID);
            if (cp == null || cp.finger == null) continue;
            System.out.println("Node " + cp.nodeIdString + " (PeerSim=" + cp.selfNode.getIndex() + ") fingers:");
            int ringSize = 1 << cp.m;
            for (int j = 0; j < cp.finger.length; j++) {
                int start = (cp.nodeId + (1 << j)) % ringSize;
                Node fn   = cp.finger[j];
                String fnStr = (fn == null) ? "null"
                        : ((ChordProtocol) fn.getProtocol(CHORD_PROTOCOL_ID)).nodeIdString;
                System.out.println("  finger[" + j + "] (" + start + ") -> " + fnStr);
            }
        }
    }
}
