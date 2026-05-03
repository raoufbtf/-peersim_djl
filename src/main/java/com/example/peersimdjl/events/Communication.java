package com.example.peersimdjl.events;

public class Communication {

    private final Long seq;
    private final String id;
    private final String type;
    private final String from;
    private final String to;
    private final Integer epoch;
    private final Integer cycle;
    private final String param;
    private final Double value;
    private final String voteCount;
    private final Double threshold;
    private final String detail;
    private final String timestamp;
    private final Long ts;

    public Communication(
            Long seq,
            String id,
            String type,
            String from,
            String to,
            Integer epoch,
            Integer cycle,
            String param,
            Double value,
            String voteCount,
            Double threshold,
            String detail,
            String timestamp,
            Long ts) {
        this.seq = seq;
        this.id = id;
        this.type = type;
        this.from = from;
        this.to = to;
        this.epoch = epoch;
        this.cycle = cycle;
        this.param = param;
        this.value = value;
        this.voteCount = voteCount;
        this.threshold = threshold;
        this.detail = detail;
        this.timestamp = timestamp;
        this.ts = ts;
    }

    public Long getSeq() {
        return seq;
    }

    public String getId() {
        return id;
    }

    public String getType() {
        return type;
    }

    public String getFrom() {
        return from;
    }

    public String getTo() {
        return to;
    }

    public Integer getEpoch() {
        return epoch;
    }

    public Integer getCycle() {
        return cycle;
    }

    public String getParam() {
        return param;
    }

    public Double getValue() {
        return value;
    }

    public String getVoteCount() {
        return voteCount;
    }

    public Double getThreshold() {
        return threshold;
    }

    public String getDetail() {
        return detail;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public Long getTs() {
        return ts;
    }
}
