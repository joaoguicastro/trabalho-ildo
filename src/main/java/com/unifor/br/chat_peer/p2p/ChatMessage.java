package com.unifor.br.chat_peer.p2p;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public final class ChatMessage {
    public final Instant timestamp;
    public final String from;
    public final String text;
    public final Direction direction;

    public enum Direction { IN, OUT, SYSTEM }

    public ChatMessage(Instant timestamp, String from, String text, Direction direction) {
        this.timestamp = timestamp;
        this.from = from;
        this.text = text;
        this.direction = direction;
    }

    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

    public String format() {
        String ts = FMT.format(timestamp);
        return switch (direction) {
            case SYSTEM -> "[" + ts + "] * " + text;
            case OUT -> "[" + ts + "] " + from + " (você): " + text;
            case IN -> "[" + ts + "] " + from + ": " + text;
        };
    }
}
