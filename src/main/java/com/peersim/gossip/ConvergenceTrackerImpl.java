package com.peersim.gossip;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class ConvergenceTrackerImpl implements ConvergenceTracker {

    private final String nodeId;
    private final Set<String> votes;
    private final AtomicBoolean quorumLogged;

    public ConvergenceTrackerImpl() {
        this("unknown");
    }

    public ConvergenceTrackerImpl(String nodeId) {
        this.nodeId = nodeId;
        this.votes = ConcurrentHashMap.newKeySet();
        this.quorumLogged = new AtomicBoolean(false);
    }

    @Override
    public void recordVote(String peerId) {
        votes.add(peerId);
    }

    @Override
    public boolean hasQuorum(int totalPeers, double threshold) {
        int required = (int) Math.ceil(threshold * totalPeers);
        boolean quorum = votes.size() >= required;

        if (quorum && quorumLogged.compareAndSet(false, true)) {
            System.out.println("[QUORUM] " + nodeId + " - convergence vote sent");
        }

        return quorum;
    }

    @Override
    public void reset() {
        votes.clear();
        quorumLogged.set(false);
    }
}
