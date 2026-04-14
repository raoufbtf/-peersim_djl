package com.example.peersimdjl;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Représente une session d'apprentissage distribué dans le réseau P2P Chord.
 * Contient les métadonnées de la session : ID, nœud IDE, participants, etc.
 * 
 * ✓ SÉRIALISABLE : Peut être stockée/récupérée du DHT Chord
 * ✓ THREAD-SAFE : Accès synchronisé aux listes mutables
 * ✓ VERSIONABLE : Inclut versioning pour détecter les updates
 */
public class LearningSession implements Serializable {
    
    // ── Identifiant de sérialisation pour compatibilité ──────────────────────
    private static final long serialVersionUID = 1L;

    public enum SessionState {
        INIT,      // Initialisation
        RUNNING,   // En cours
        DONE       // Terminée
    }

    // ── Identifiants ────────────────────────────────────────────────
    public final String sessionId;           // ID unique de la session (ex: "session_001")
    public final int ideNodeId;              // ChordId du nœud IDE (électionné)
    public final String ideNodeIdString;     // Identifiant permanent du nœud IDE (N0, N1, …)
    
    // ── Métadonnées de session ───────────────────────────────────────
    public final int totalParticipants;      // Nombre total de nodes participants attendus
    public final List<Integer> activeNodeIds;    // ChordIds des nodes actuellement actifs
    public final List<String> activeNodeIdStrings; // IDs permanents des nodes actifs
    
    // ── État et Timing ──────────────────────────────────────────────
    public SessionState state;               // État courant (INIT → RUNNING → DONE)
    public long createdAtTimestamp;          // Quand la session a été créée (ms)
    public int createdAtCycle;               // Au quel cycle PeerSim
    public int lastUpdatedAtCycle;           // Dernier update
    public long lastUpdatedAtTimestamp;      // Timestamp du dernier update
    
    // ── Versioning pour détecter les updates ────────────────────────
    public int version = 0;                  // Incrémenter à chaque modification

    public LearningSession(
            String sessionId,
            int ideNodeId,
            String ideNodeIdString,
            int totalParticipants,
            long createdAtTimestamp,
            int createdAtCycle) {
        
        this.sessionId = sessionId;
        this.ideNodeId = ideNodeId;
        this.ideNodeIdString = ideNodeIdString;
        this.totalParticipants = totalParticipants;
        this.createdAtTimestamp = createdAtTimestamp;
        this.createdAtCycle = createdAtCycle;
        
        this.state = SessionState.INIT;
        this.activeNodeIds = new ArrayList<>();
        this.activeNodeIdStrings = new ArrayList<>();
        this.lastUpdatedAtCycle = createdAtCycle;
        this.lastUpdatedAtTimestamp = createdAtTimestamp;
    }

    /**
     * Ajoute un nœud actif à la session.
     */
    public synchronized void addActiveNode(int chordId, String nodeIdString) {
        if (!activeNodeIds.contains(chordId)) {
            activeNodeIds.add(chordId);
            activeNodeIdStrings.add(nodeIdString);
        }
    }

    /**
     * Retire un nœud de la liste active (ex: après crash).
     */
    public synchronized void removeActiveNode(int chordId) {
        int idx = activeNodeIds.indexOf(chordId);
        if (idx >= 0) {
            activeNodeIds.remove(idx);
            activeNodeIdStrings.remove(idx);
        }
    }

    /**
     * Transite vers l'état RUNNING.
     */
    public synchronized void transitionToRunning() {
        if (state == SessionState.INIT) {
            state = SessionState.RUNNING;
        }
    }

    /**
     * Transite vers l'état DONE.
     */
    public synchronized void transitionToDone() {
        state = SessionState.DONE;
    }

    /**
     * Met à jour le timestamp du dernier update et du cycle.
     */
    public synchronized void updateTimestamp(int cycle) {
        this.lastUpdatedAtCycle = cycle;
        this.lastUpdatedAtTimestamp = System.currentTimeMillis();
    }

    /**
     * Vérifie si cette session est plus récente qu'une autre (versioning).
     */
    public boolean isNewerThan(LearningSession other) {
        if (other == null) return true;
        return this.version > other.version;
    }

    /**
     * Vérifie si cette session est obsolète (stale).
     * Obsolète = pas mise à jour depuis plus de N cycles.
     */
    public boolean isStale(int currentCycle, int staleCycleThreshold) {
        return (currentCycle - this.lastUpdatedAtCycle) > staleCycleThreshold;
    }

    /**
     * Hook de sérialisation personnalisée (optionnel, pour logging).
     * Appelé automatiquement lors d'ObjectOutputStream.writeObject()
     */
    private void writeObject(java.io.ObjectOutputStream out) throws java.io.IOException {
        out.defaultWriteObject();  // Sérialiser tous les champs
        if (ChordProtocol.DEBUGChord) {
            System.out.println("[SERIALIZATION] LearningSession sérialisée: " + 
                this.sessionId + " v" + this.version + " state=" + this.state);
        }
    }

    /**
     * Hook de désérialisation personnalisée (optionnel, pour logging).
     * Appelé automatiquement lors d'ObjectInputStream.readObject()
     */
    private void readObject(java.io.ObjectInputStream in) throws java.io.IOException, ClassNotFoundException {
        in.defaultReadObject();  // Désérialiser tous les champs
        if (ChordProtocol.DEBUGChord) {
            System.out.println("[DESERIALIZATION] LearningSession désérialisée: " + 
                this.sessionId + " v" + this.version + " state=" + this.state);
        }
    }

    @Override
    public String toString() {
        return "LearningSession{" +
                "sessionId='" + sessionId + '\'' +
                ", ideNodeId=" + ideNodeId +
                ", ideNodeIdString='" + ideNodeIdString + '\'' +
                ", totalParticipants=" + totalParticipants +
                ", activeNodes=" + activeNodeIdStrings.size() + "/" + totalParticipants +
                ", state=" + state +
                ", createdAtCycle=" + createdAtCycle +
                ", lastUpdatedAtCycle=" + lastUpdatedAtCycle +
                ", version=" + version +
                '}';
    }
}
