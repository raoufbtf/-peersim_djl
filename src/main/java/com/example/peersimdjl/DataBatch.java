package com.example.peersimdjl;

import java.io.Serializable;

/**
 * Représente un batch de données distribué dans PeerSim.
 *
 * Chaque batch peut être sérialisé et stocké dans le DHT Chord avec une clé
 * de la forme {@code batch_<id>}.
 */
public class DataBatch implements Serializable {

    private static final long serialVersionUID = 1L;

    public enum BatchStatus {
        CREATED,
        PROCESSING,
        ASSIGNED,
        STORED,
        COMPLETED
    }

    public final String batchId;
    public final double[][] data;
    public int assignedNodeId;
    public String assignedNodeIdString;
    public int processingNodeId;
    public String processingNodeIdString;
    public BatchStatus status;

    public DataBatch(String batchId, double[][] data) {
        this.batchId = batchId;
        this.data = data;
        this.assignedNodeId = -1;
        this.assignedNodeIdString = null;
        this.processingNodeId = -1;
        this.processingNodeIdString = null;
        this.status = BatchStatus.CREATED;
    }

    public synchronized void markProcessing(int chordId, String nodeIdString) {
        this.processingNodeId = chordId;
        this.processingNodeIdString = nodeIdString;
        this.status = BatchStatus.PROCESSING;
    }

    public synchronized void assignToNode(int chordId, String nodeIdString) {
        this.assignedNodeId = chordId;
        this.assignedNodeIdString = nodeIdString;
        this.status = BatchStatus.ASSIGNED;
    }

    public synchronized void markStored() {
        this.status = BatchStatus.STORED;
    }

    public synchronized void markCompleted() {
        this.status = BatchStatus.COMPLETED;
    }

    public int rowCount() {
        return data == null ? 0 : data.length;
    }

    @Override
    public String toString() {
        return "DataBatch{" +
                "batchId='" + batchId + '\'' +
                ", rows=" + rowCount() +
                ", assignedNodeId=" + assignedNodeId +
                ", assignedNodeIdString='" + assignedNodeIdString + '\'' +
                ", processingNodeId=" + processingNodeId +
                ", processingNodeIdString='" + processingNodeIdString + '\'' +
                ", status=" + status +
                '}';
    }
}