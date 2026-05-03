package com.peersim.gossip;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class GossipNetworkImpl implements GossipNode {

    private static final long MAX_WAIT_MS = 200L;
    private static final ConcurrentHashMap<String, GossipNetworkImpl> REGISTRY = new ConcurrentHashMap<>();

    private final String nodeId;
    private final int k;
    private final int r;
    private final AtomicInteger localVersion;
    private final ConcurrentHashMap<String, Integer> knownVersions;
    private final ConcurrentHashMap<String, CountDownLatch> pendingAcks;
    private final LossVector localLossVector;
    private volatile List<String> currentNeighbors;

    public GossipNetworkImpl(String nodeId, int K, int R) {
        this.nodeId = nodeId;
        this.k = Math.max(0, K);
        this.r = Math.max(0, R);
        this.localVersion = new AtomicInteger(0);
        this.knownVersions = new ConcurrentHashMap<>();
        this.pendingAcks = new ConcurrentHashMap<>();
        this.localLossVector = new LocalLossVector();
        this.currentNeighbors = Collections.emptyList();
        REGISTRY.put(nodeId, this);
    }

    @Override
    public void gossipLoss(double currentLoss, int version) {
        localVersion.accumulateAndGet(version, Math::max);
        knownVersions.put(nodeId, version);
        localLossVector.update(nodeId, currentLoss, version);

        List<String> neighbors = selectNeighbors();
        currentNeighbors = neighbors;

        for (String targetId : neighbors) {
            GossipMessage msg = new GossipMessage(UUID.randomUUID().toString(), nodeId, currentLoss, version);
            System.out.println("[GOSSIP] " + nodeId + " → " + targetId + " | loss=" + currentLoss + " | v=" + version);
            sendWithAck(targetId, msg);
        }
    }

    @Override
    public void receiveLoss(String peerId, double loss, int version) {
        processIncomingLoss(null, peerId, loss, version);
    }

    @Override
    public List<String> getNeighbors() {
        return new ArrayList<>(currentNeighbors);
    }

    private boolean sendWithAck(String targetId, GossipMessage msg) {
        for (int attempt = 0; attempt <= r; attempt++) {
            GossipNetworkImpl target = REGISTRY.get(targetId);
            if (target == null) {
                sleepQuietly(MAX_WAIT_MS);
                continue;
            }

            CountDownLatch latch = new CountDownLatch(1);
            pendingAcks.put(msg.msg_id, latch);
            try {
                target.receiveMessage(msg, nodeId);
                if (latch.await(MAX_WAIT_MS, TimeUnit.MILLISECONDS)) {
                    return true;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            } finally {
                pendingAcks.remove(msg.msg_id, latch);
            }
        }
        return false;
    }

    private void receiveMessage(GossipMessage msg, String senderId) {
        processIncomingLoss(msg.msg_id, msg.peerId, msg.loss, msg.version);
        GossipNetworkImpl sender = REGISTRY.get(senderId);
        if (sender != null) {
            sender.ack(msg.msg_id);
        }
    }

    private void processIncomingLoss(String msgId, String peerId, double loss, int version) {
        Integer currentKnown = knownVersions.get(peerId);
        if (currentKnown != null && version <= currentKnown) {
            if (msgId != null) {
                ack(msgId);
            }
            return;
        }

        knownVersions.put(peerId, version);
        localVersion.accumulateAndGet(version, Math::max);
        localLossVector.update(peerId, loss, version);

        if (msgId != null) {
            ack(msgId);
        }
    }

    private void ack(String msgId) {
        CountDownLatch latch = pendingAcks.get(msgId);
        if (latch != null) {
            latch.countDown();
        }
    }

    private List<String> selectNeighbors() {
        List<String> candidates = new ArrayList<>(REGISTRY.keySet());
        candidates.remove(nodeId);
        Collections.shuffle(candidates, ThreadLocalRandom.current());

        int limit = Math.min(k, candidates.size());
        return new ArrayList<>(candidates.subList(0, limit));
    }

    private static void sleepQuietly(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static final class GossipMessage {
        private final String msg_id;
        private final String peerId;
        private final double loss;
        private final int version;

        private GossipMessage(String msg_id, String peerId, double loss, int version) {
            this.msg_id = msg_id;
            this.peerId = peerId;
            this.loss = loss;
            this.version = version;
        }
    }

    private static final class LocalLossVector implements LossVector {
        private final ConcurrentHashMap<String, LossSample> samples;

        private LocalLossVector() {
            this.samples = new ConcurrentHashMap<>();
        }

        @Override
        public void update(String peerId, double loss, int version) {
            samples.compute(peerId, (key, current) -> {
                if (current == null || version > current.version) {
                    return new LossSample(loss, version);
                }
                return current;
            });
        }

        @Override
        public boolean isConverged(double epsilon) {
            return getMaxMinDelta() <= epsilon;
        }

        @Override
        public double getMaxMinDelta() {
            if (samples.isEmpty()) {
                return 0.0d;
            }

            double min = Double.POSITIVE_INFINITY;
            double max = Double.NEGATIVE_INFINITY;

            for (LossSample sample : samples.values()) {
                if (sample.loss < min) {
                    min = sample.loss;
                }
                if (sample.loss > max) {
                    max = sample.loss;
                }
            }

            return max - min;
        }
    }

    private static final class LossSample {
        private final double loss;
        private final int version;

        private LossSample(double loss, int version) {
            this.loss = loss;
            this.version = version;
        }
    }
}