package com.example.peersimdjl.events;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class SimulationEventPublisherTest {

    @Test
    void publish10Events_getLast10_returns10InOrder() {
        SimulationEventPublisher publisher = new SimulationEventPublisher(null);
        for (int i = 0; i < 10; i++) {
            publisher.publish(new SimulationEvent(java.time.Instant.now(), "INFO", "TEST", "s", "n", "msg" + i, Map.of()));
        }
        List<SimulationEvent> events = publisher.getLast(10);
        assertEquals(10, events.size());
        for (int i = 0; i < 10; i++) {
            assertEquals("msg" + i, events.get(i).getMessage());
        }
    }

    @Test
    void publish6000Events_bufferSizeStaysWithinLimit() {
        SimulationEventPublisher publisher = new SimulationEventPublisher(null);
        for (int i = 0; i < 6000; i++) {
            publisher.publish(new SimulationEvent(java.time.Instant.now(), "INFO", "TEST", "s", "n", "msg", Map.of()));
        }
        List<SimulationEvent> events = publisher.getLast(6000);
        assertTrue(events.size() <= 5000);
    }

    @Test
    void concurrentPublishFrom10Threads_noException_sizeWithinLimit() throws Exception {
        SimulationEventPublisher publisher = new SimulationEventPublisher(null);
        int threadCount = 10;
        int perThread = 100;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int t = 0; t < threadCount; t++) {
            executor.submit(() -> {
                try {
                    for (int i = 0; i < perThread; i++) {
                        publisher.publish(new SimulationEvent(java.time.Instant.now(), "INFO", "TEST", "s", "n", "msg", Map.of()));
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();
        assertTrue(publisher.getLast(10000).size() <= 5000);
    }
}
