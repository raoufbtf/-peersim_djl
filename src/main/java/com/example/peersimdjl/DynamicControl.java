package com.example.peersimdjl;

import peersim.core.*;
import peersim.config.*;
import java.util.*;

/**
 * Contrôle dynamique de la simulation Chord.
 *
 * Gère les événements de vie du réseau (ajout/retrait/crash de nœuds),
 * la stabilisation globale de l'anneau et différents scénarios de test
 * (lookup, DHT, réplication, migration de données).
 */
public class DynamicControl implements Control {

    // Use the shared ChordProtocol debug flag for consistency
    // private static final boolean DEBUG = true;

    private int    pid;
    private Random rand       = new Random();
    private int    cycleCount = 0;
    private int    addCount   = 0;

    /**
     * Construit le contrôleur dynamique en lisant le PID Chord.
     *
     * @param prefix Préfixe de configuration PeerSim.
     */
    public DynamicControl(String prefix) {
        this.pid = Configuration.getPid(prefix + ".pids");
    }

    // ════════════════════════════════════════════════════════════════════════
    //  UTILITAIRES  m
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Calcule la taille logique de l'espace d'identifiants (`m`).
     */
    private int computeM(int networkSize) {
        if (networkSize <= 1) return 3;
        int m = (int) Math.ceil(Math.log(networkSize) / Math.log(2));
        return Math.max(3, m);
    }

    /**
     * Retourne la valeur courante de `m` à partir d'un nœud existant.
     */
    private int getCurrentM() {
        if (Network.size() == 0) return 3;
        ChordProtocol c = (ChordProtocol) Network.get(0).getProtocol(pid);
        return (c != null) ? c.m : 3;
    }

    /**
     * Applique une nouvelle valeur de `m` à tous les nœuds et redimensionne les fingers.
     */
    private void updateMForAllNodes(int newM) {
        for (int i = 0; i < Network.size(); i++) {
            ChordProtocol c = (ChordProtocol) Network.get(i).getProtocol(pid);
            if (c == null) continue;
            c.m = newM;
            Node[] newFinger = new Node[newM];
            if (c.finger != null) {
                int copyLen = Math.min(c.finger.length, newM);
                System.arraycopy(c.finger, 0, newFinger, 0, copyLen);
            }
            c.finger = newFinger;
        }
        stabilizeAllNodes();
    }

    /**
     * Vérifie la cohérence de `m` entre tous les nœuds du réseau.
     */
    private void verifyM() {
        int expected = getCurrentM();
        for (int i = 0; i < Network.size(); i++) {
            ChordProtocol c = (ChordProtocol) Network.get(i).getProtocol(pid);
            if (c != null && c.m != expected)
                System.out.println("WARNING: Node " + c.nodeIdString + " (PeerSim=" + c.selfNode.getIndex() + ")"
                        + " a m=" + c.m + " mais attendu m=" + expected);
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    //  NETTOYAGE DES RÉFÉRENCES MORTES  (cœur du correctif)
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Parcourt TOUS les nœuds du réseau et efface toute référence
     * (successor, predecessor, successorList, finger[]) vers un nœud qui
     * n'est plus dans le réseau PeerSim.
     * Doit être appelée juste après Network.remove(), avant stabilizeAllNodes().
     */
    private void purgeDeadReferences() {
        for (int i = 0; i < Network.size(); i++) {
            ChordProtocol c = (ChordProtocol) Network.get(i).getProtocol(pid);
            if (c == null) continue;
            c.cleanupDeadReferences();   // délégué à la méthode déjà présente dans ChordProtocol
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    //  STABILISATION GLOBALE
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Lance plusieurs rounds de stabilisation/fixFingers pour converger plus vite.
     */
    private void stabilizeAllNodes() {
        // Plus de rounds pour que la correction se propage jusqu'aux nœuds distants
        for (int round = 0; round < 5; round++) {
            for (int i = 0; i < Network.size(); i++) {
                ChordProtocol c = (ChordProtocol) Network.get(i).getProtocol(pid);
                if (c != null) {
                    c.cleanupDeadReferences(); // re-purger à chaque round
                    c.stabilize();
                    c.fixFingers();
                }
            }
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    //  networkContains  (gardé ici aussi pour usage local)
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Vérifie si une référence de nœud est encore présente dans `Network`.
     */
    private boolean networkContains(Node n) {
        for (int i = 0; i < Network.size(); i++) {
            if (Network.get(i) == n) return true;
        }
        return false;
    }

    // ════════════════════════════════════════════════════════════════════════
    //  CRASH CIBLÉ PAR ID
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Supprime le nœud dont le ChordId correspond à `targetChordId`.
     */
    private void crashNodeById(int targetChordId) {
        Node targetNode = null;
        int  targetIndex = -1;
        for (int i = 0; i < Network.size(); i++) {
            Node n = Network.get(i);
            ChordProtocol cp = (ChordProtocol) n.getProtocol(pid);
            if (cp != null && cp.nodeId == targetChordId) {
                targetNode = n;
                targetIndex = i;
                break;
            }
        }
        if (targetNode == null) return;
        
        ChordProtocol cp = (ChordProtocol) targetNode.getProtocol(pid);
        System.out.println("\n>>> TARGETED CRASH: Node " + cp.nodeIdString + " (PeerSim=" + cp.selfNode.getIndex() + ", ChordId=" + cp.nodeId + ") removed");
        
        // Réparer les voisins directs avant suppression
        if (cp.predecessor != null && cp.successor != null) {
            ChordProtocol predChord = (ChordProtocol) cp.predecessor.getProtocol(pid);
            ChordProtocol succChord = (ChordProtocol) cp.successor.getProtocol(pid);
            if (predChord != null) predChord.successor   = cp.successor;
            if (succChord != null) succChord.predecessor = cp.predecessor;
        }
        
        Network.remove(targetIndex);
        purgeDeadReferences();
        stabilizeAllNodes();
        System.out.println("<<< Crash recovery completed\n");
    }

    // ════════════════════════════════════════════════════════════════════════
    //  CRASH SIMULÉ
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Simule un crash aléatoire d'un nœud puis reconstruit la topologie.
     */
    private void simulateCrash() {
        if (Network.size() <= 1) return;

        int  index     = rand.nextInt(Network.size());
        Node crashNode = Network.get(index);
        ChordProtocol cp = (ChordProtocol) crashNode.getProtocol(pid);
        if (cp == null) return;

        System.out.println("Simulated crash: Node " + cp.nodeIdString + " (PeerSim=" + cp.selfNode.getIndex() + ") removed");

        // Réparer les voisins directs avant suppression
        if (cp.predecessor != null && cp.successor != null) {
            ChordProtocol predChord = (ChordProtocol) cp.predecessor.getProtocol(pid);
            ChordProtocol succChord = (ChordProtocol) cp.successor.getProtocol(pid);
            if (predChord != null) predChord.successor   = cp.successor;
            if (succChord != null) succChord.predecessor = cp.predecessor;
        }

        // Suppression physique du réseau
        Network.remove(index);

        // ── CORRECTIF PRINCIPAL ─────────────────────────────────────────────
        // 1. Purger TOUTES les références vers ce nœud mort dans tout le réseau
        purgeDeadReferences();
        // 2. Reconstruire la topologie avec des références saines
        stabilizeAllNodes();
        // ───────────────────────────────────────────────────────────────────

        if (ChordProtocol.DEBUGChord) {
            ChordProtocol.printNetworkState();
            ChordProtocol.printFingerTables();
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    //  BOUCLE PRINCIPALE
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Exécute les actions dynamiques planifiées au cycle courant.
     *
     * @return `false` pour continuer la simulation.
     */
    @Override
    public boolean execute() {
        cycleCount++;
   if (ChordProtocol.DEBUGChord) System.out.println("DynamicControl execute cycle " + cycleCount);

        // Crash périodique
        if (cycleCount > 1 && cycleCount % 5 == 0 && Network.size() > 3) {
            simulateCrash();
        }

        // Scénario de test
        if      (cycleCount == 2)  addNewNode();
        else if (cycleCount == 8)  addNewNode();
        else if (cycleCount == 10) crashNodeById(0);  // CRASH ID 0 (PeerSim=0)
        else if (cycleCount == 12) removeNode();
        else if (cycleCount == 15) testLookup();
        else if (cycleCount == 5) testSimpleDHT();
        else if (cycleCount == 18) testDHTBasic();
        else if (cycleCount == 22) testDHTAfterCrash();
        else if (cycleCount == 25) testReplication();
        else if (cycleCount == 28) testDataMigration();

        // Affichage tous les 3 cycles
        if (cycleCount % 3 == 0 && ChordProtocol.DEBUGChord) printNetworkState();

    

        return false;
    }

    // ════════════════════════════════════════════════════════════════════════
    //  AJOUT D'UN NŒUD
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Ajoute un nouveau nœud au réseau, le fait joindre l'anneau et stabilise.
     */
    private void addNewNode() {
        addCount++;
        int newId = 7 + addCount; // 8, 9, …

        // 1. Calculer le nouveau m AVANT d'ajouter
        int newM = computeM(Network.size() + 1);
        if (newM != getCurrentM()) updateMForAllNodes(newM);

        // 2. Créer le nœud
        Node newNode = (Node) Network.prototype.clone();
        ChordProtocol newChord = (ChordProtocol) newNode.getProtocol(pid);
        newChord.nodeId   = newId * 2;      // nodeId indépendant
        newChord.nodeIdString = ChordProtocol.generateUniqueStringId();  // ID PERMANENT et IMMUABLE
        newChord.selfNode = newNode;
        newChord.m        = newM;
        newChord.finger   = new Node[newM];
        if (newChord.successorList == null)
            newChord.successorList = new Node[3];

        // 3. Ajouter au réseau
        Network.add(newNode);
        if (Network.size() < 2) return;

        // 4. Join via un nœud existant (pas le nouveau lui-même)
        Node existingNode = Network.get(rand.nextInt(Network.size() - 1));
        newChord.join(existingNode);
        newChord.rebuildFingerTable();

        // 5. Purger + stabiliser
        purgeDeadReferences();
        stabilizeAllNodes();

        ChordProtocol via = (ChordProtocol) existingNode.getProtocol(pid);
        System.out.println("Added Node " + newChord.nodeIdString + " (PeerSim=" + newChord.selfNode.getIndex() + ")"
                + " | m=" + newM
                + " | joined via Node " + (via != null ? via.nodeIdString + " (PeerSim=" + via.selfNode.getIndex() + ")" : "?"));

        if (ChordProtocol.DEBUGChord) {
            ChordProtocol.printNetworkState();
            ChordProtocol.printFingerTables();
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    //  SUPPRESSION VOLONTAIRE D'UN NŒUD
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Retire volontairement un nœud du réseau avec correction des pointeurs voisins.
     */
    private void removeNode() {
        if (Network.size() <= 1) return;

        int   index        = rand.nextInt(Network.size());
        Node  nodeToRemove = Network.get(index);
        ChordProtocol chord = (ChordProtocol) nodeToRemove.getProtocol(pid);
        if (chord == null) return;

        // Réparer les voisins directs
        if (chord.predecessor != null && chord.successor != null) {
            ChordProtocol predChord = (ChordProtocol) chord.predecessor.getProtocol(pid);
            ChordProtocol succChord = (ChordProtocol) chord.successor.getProtocol(pid);
            if (predChord != null) predChord.successor   = chord.successor;
            if (succChord != null) succChord.predecessor = chord.predecessor;
        }

        chord.leave();
        Network.remove(index);

        // ── CORRECTIF PRINCIPAL ─────────────────────────────────────────────
        purgeDeadReferences();
        stabilizeAllNodes();
        // ───────────────────────────────────────────────────────────────────

        System.out.println("Removed Node " + chord.nodeIdString + " (PeerSim=" + chord.selfNode.getIndex() + ")");

        if (ChordProtocol.DEBUGChord) {
            ChordProtocol.printNetworkState();
            ChordProtocol.printFingerTables();
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    //  TEST LOOKUP
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Exécute un lookup aléatoire pour valider le routage Chord.
     */
    private void testLookup() {
        if (Network.size() == 0) return;
        Node startNode = Network.get(rand.nextInt(Network.size()));
        if (startNode == null) return;
        ChordProtocol startChord = (ChordProtocol) startNode.getProtocol(pid);
        if (startChord == null) return;

        int  testId = rand.nextInt(20);
        Node found  = startChord.findSuccessor(testId);

        if (found != null) {
            ChordProtocol foundChord = (ChordProtocol) found.getProtocol(pid);
            System.out.println("Dynamic lookup for ID " + testId
                    + " -> Node " + (foundChord != null ? foundChord.nodeIdString + " (PeerSim=" + foundChord.selfNode.getIndex() + ")" : "null"));
        } else {
            System.out.println("Dynamic lookup for ID " + testId + " -> null");
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    //  AFFICHAGE ÉTAT RÉSEAU
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Affiche une vue synthétique de l'anneau au cycle courant.
     */
    private void printNetworkState() {
        System.out.println("Network state at cycle " + cycleCount + ":");
        for (int i = 0; i < Network.size(); i++) {
            Node node   = Network.get(i);
            ChordProtocol chord = (ChordProtocol) node.getProtocol(pid);
            if (chord == null || chord.successor == null) continue;
            ChordProtocol succChord = (ChordProtocol) chord.successor.getProtocol(pid);
            System.out.println("  Node " + chord.nodeIdString + " (PeerSim=" + chord.selfNode.getIndex() + ")"
                    + " -> succ: " + (succChord != null ? succChord.nodeIdString + " (PeerSim=" + succChord.selfNode.getIndex() + ")" : "null"));
        }
        verifyM();
    }

    // ════════════════════════════════════════════════════════════════════════
    //  TESTS DHT
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Réalise un test minimal PUT/GET local-distribué sur la DHT.
     */
    private void testSimpleDHT() {
        System.out.println("\n=== SIMPLE DHT TEST ===");

        // Test basique PUT/GET
        Node node = Network.get(0);
        ChordProtocol cp = (ChordProtocol) node.getProtocol(pid);
        if (cp != null) {
            cp.put("testKey", "testValue");
            Object value = cp.get("testKey");
            System.out.println("DHT Test: put/get -> " + value);

            // Afficher le stockage
            cp.printLocalStorage();
        }

        System.out.println("=== SIMPLE DHT TEST END ===\n");
    }

    /**
     * Exécute une campagne basique PUT/GET sur plusieurs nœuds.
     */
    private void testDHTBasic() {
        System.out.println("\n=== DHT BASIC TEST ===");

        // Test PUT
        System.out.println("Testing DHT PUT operations...");
        for (int i = 0; i < Network.size(); i++) {
            Node node = Network.get(i);
            ChordProtocol cp = (ChordProtocol) node.getProtocol(pid);
            if (cp != null) {
                cp.put("testKey" + i, "testValue" + i);
            }
        }

        // Test GET
        System.out.println("Testing DHT GET operations...");
        for (int i = 0; i < Network.size(); i++) {
            Node node = Network.get(i);
            ChordProtocol cp = (ChordProtocol) node.getProtocol(pid);
            if (cp != null) {
                Object value = cp.get("testKey" + i);
                System.out.println("GET testKey" + i + " -> " + value);
            }
        }

        // Afficher le stockage local de chaque nœud
        System.out.println("Local storage contents:");
        for (int i = 0; i < Network.size(); i++) {
            Node node = Network.get(i);
            ChordProtocol cp = (ChordProtocol) node.getProtocol(pid);
            if (cp != null) {
                cp.printLocalStorage();
            }
        }

        System.out.println("=== DHT BASIC TEST END ===\n");
    }

    /**
     * Vérifie l'accessibilité des données après des changements topologiques.
     */
    private void testDHTAfterCrash() {
        System.out.println("\n=== DHT AFTER CRASH TEST ===");

        // Tester la récupération après crash
        System.out.println("Testing DHT operations after network changes...");

        // Ajouter quelques nouvelles clés
        for (int i = 0; i < Math.min(3, Network.size()); i++) {
            Node node = Network.get(i);
            ChordProtocol cp = (ChordProtocol) node.getProtocol(pid);
            if (cp != null) {
                cp.put("postCrashKey" + i, "postCrashValue" + i);
            }
        }

        // Tester la récupération
        for (int i = 0; i < Math.min(3, Network.size()); i++) {
            Node node = Network.get(i);
            ChordProtocol cp = (ChordProtocol) node.getProtocol(pid);
            if (cp != null) {
                Object value = cp.get("postCrashKey" + i);
                System.out.println("GET postCrashKey" + i + " -> " + value);
            }
        }

        // Afficher le stockage final
        System.out.println("Final local storage contents:");
        for (int i = 0; i < Network.size(); i++) {
            Node node = Network.get(i);
            ChordProtocol cp = (ChordProtocol) node.getProtocol(pid);
            if (cp != null) {
                cp.printLocalStorage();
            }
        }

        System.out.println("=== DHT AFTER CRASH TEST END ===\n");
    }

    // ════════════════════════════════════════════════════════════════════════
    //  TESTS RÉPLICATION ET MIGRATION
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Teste les opérations PUT/GET avec réplication sur successeurs.
     */
    private void testReplication() {
        System.out.println("\n=== REPLICATION TEST ===");

        // Tester PUT/GET avec réplication
        Node node = Network.get(0);
        ChordProtocol cp = (ChordProtocol) node.getProtocol(pid);
        if (cp != null) {
            cp.putReplicated("replicatedKey1", "replicatedValue1");
            cp.putReplicated("replicatedKey2", "replicatedValue2");

            Object value1 = cp.getReplicated("replicatedKey1");
            Object value2 = cp.getReplicated("replicatedKey2");

            System.out.println("REPLICATION TEST: replicatedKey1 -> " + value1);
            System.out.println("REPLICATION TEST: replicatedKey2 -> " + value2);
        }

        // Afficher le stockage sur tous les nœuds pour voir la réplication
        System.out.println("Storage after replication:");
        for (int i = 0; i < Network.size(); i++) {
            Node n = Network.get(i);
            ChordProtocol c = (ChordProtocol) n.getProtocol(pid);
            if (c != null) {
                c.printLocalStorage();
            }
        }

        System.out.println("=== REPLICATION TEST END ===\n");
    }

    /**
     * Teste la conservation de l'accès aux données après migration de clés.
     */
    private void testDataMigration() {
        System.out.println("\n=== DATA MIGRATION TEST ===");

        // Ajouter des données avant l'arrivée d'un nouveau nœud
        System.out.println("Adding data before node join...");
        for (int i = 0; i < Math.min(2, Network.size()); i++) {
            Node node = Network.get(i);
            ChordProtocol cp = (ChordProtocol) node.getProtocol(pid);
            if (cp != null) {
                cp.put("migrationKey" + i, "migrationValue" + i);
            }
        }

        // Attendre un cycle pour la migration
        System.out.println("Waiting for migration to complete...");

        // Vérifier que les données sont toujours accessibles après migration
        System.out.println("Checking data accessibility after migration:");
        for (int i = 0; i < Math.min(2, Network.size()); i++) {
            Node node = Network.get(i);
            ChordProtocol cp = (ChordProtocol) node.getProtocol(pid);
            if (cp != null) {
                Object value = cp.get("migrationKey" + i);
                System.out.println("GET migrationKey" + i + " -> " + value);
            }
        }

        System.out.println("=== DATA MIGRATION TEST END ===\n");
    }
}