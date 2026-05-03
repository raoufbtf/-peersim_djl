package com.example.peersimdjl;

import com.example.peersimdjl.websocket.WebSocketEventBridge;
import com.example.peersimdjl.events.Communication;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

public final class SimulationCommEventLogger {

    public static final String PREFIX = "[COMM_EVENT_JSON]";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    private static final java.util.concurrent.atomic.AtomicLong SEQ = new java.util.concurrent.atomic.AtomicLong(0L);
    private static volatile WebSocketEventBridge BRIDGE = null;

    private SimulationCommEventLogger() {
    }

    public static void emit(
            String type,
            String from,
            String to,
            Integer epoch,
            Integer cycle,
            String param,
            Double value,
            String voteCount,
            Double threshold,
            String detail) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            long seq = SEQ.incrementAndGet();
            payload.put("seq", seq);
            payload.put("type", type);
            payload.put("from", from);
            payload.put("to", to);
            payload.put("epoch", epoch);
            payload.put("cycle", cycle);
            payload.put("param", param);
            payload.put("value", value);
            payload.put("voteCount", voteCount);
            payload.put("threshold", threshold);
            payload.put("detail", detail);
            String tsStr = LocalTime.now().format(TIME_FORMAT);
            payload.put("timestamp", tsStr);
            payload.put("ts", System.currentTimeMillis());

            System.out.println(PREFIX + OBJECT_MAPPER.writeValueAsString(payload));

            // If a WebSocket bridge has been registered, publish the same event immediately
            // using the Communication DTO so that STOMP subscribers receive it at the exact
            // same moment as the log line above.
            if (BRIDGE != null) {
                try {
                    Communication comm = new Communication(
                            seq,
                            String.valueOf(seq) + "-" + (type != null ? type : "UNKNOWN") + "-" + (from != null ? from : "?") + "-" + (to != null ? to : "?"),
                            type,
                            from,
                            to,
                            epoch,
                            cycle,
                            param,
                            value,
                            voteCount,
                            threshold,
                            detail,
                            tsStr,
                            System.currentTimeMillis()
                    );
                    BRIDGE.sendCommunication(comm);
                } catch (Exception ignored) {
                }
            }
        } catch (Exception ignored) {
        }
    }

    public static void setBridge(WebSocketEventBridge bridge) {
        BRIDGE = bridge;
    }
}
