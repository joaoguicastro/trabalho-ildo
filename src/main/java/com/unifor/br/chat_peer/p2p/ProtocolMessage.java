package com.unifor.br.chat_peer.p2p;

import java.util.Objects;
import java.util.UUID;

public final class ProtocolMessage {

    public enum Type { HELLO, MSG, PEERS_REQ, PEERS_RES, BYE }

    public final Type type;
    public final String id;

    public final String username;
    public final Integer listenPort;

    public final String from;
    public final String text;

    public final String peersCsv;

    private ProtocolMessage(Type type, String id, String username, Integer listenPort, String from, String text, String peersCsv) {
        this.type = type;
        this.id = id;
        this.username = username;
        this.listenPort = listenPort;
        this.from = from;
        this.text = text;
        this.peersCsv = peersCsv;
    }

    public static ProtocolMessage hello(String username, int listenPort) {
        return new ProtocolMessage(Type.HELLO, UUID.randomUUID().toString(), username, listenPort, null, null, null);
    }

    public static ProtocolMessage msg(String from, String text) {
        return new ProtocolMessage(Type.MSG, UUID.randomUUID().toString(), null, null, from, escape(text), null);
    }

    public static ProtocolMessage peersReq() {
        return new ProtocolMessage(Type.PEERS_REQ, UUID.randomUUID().toString(), null, null, null, null, null);
    }

    public static ProtocolMessage peersRes(String requestId, String peersCsv) {
        return new ProtocolMessage(Type.PEERS_RES, requestId, null, null, null, null, peersCsv == null ? "" : peersCsv);
    }

    public static ProtocolMessage bye(String username) {
        return new ProtocolMessage(Type.BYE, UUID.randomUUID().toString(), username, null, null, null, null);
    }

    public String toLine() {
        return switch (type) {
            case HELLO -> "HELLO|" + id + "|" + safe(username) + "|" + listenPort;
            case MSG -> "MSG|" + id + "|" + safe(from) + "|" + safe(text);
            case PEERS_REQ -> "PEERS_REQ|" + id;
            case PEERS_RES -> "PEERS_RES|" + id + "|" + safe(peersCsv);
            case BYE -> "BYE|" + id + "|" + safe(username);
        };
    }

    public static ProtocolMessage parse(String line) {
        if (line == null || line.isBlank()) throw new IllegalArgumentException("Empty protocol line");
        String[] parts = line.split("\\|", -1);
        String rawType = parts[0].trim();
        Type type = Type.valueOf(rawType);

        return switch (type) {
            case HELLO -> {
                if (parts.length < 4) throw new IllegalArgumentException("Invalid HELLO: " + line);
                String id = parts[1];
                String username = parts[2];
                int port = Integer.parseInt(parts[3]);
                yield new ProtocolMessage(Type.HELLO, id, username, port, null, null, null);
            }
            case MSG -> {
                if (parts.length < 4) throw new IllegalArgumentException("Invalid MSG: " + line);
                String id = parts[1];
                String from = parts[2];
                String text = unescape(parts[3]);
                yield new ProtocolMessage(Type.MSG, id, null, null, from, text, null);
            }
            case PEERS_REQ -> {
                if (parts.length < 2) throw new IllegalArgumentException("Invalid PEERS_REQ: " + line);
                yield new ProtocolMessage(Type.PEERS_REQ, parts[1], null, null, null, null, null);
            }
            case PEERS_RES -> {
                if (parts.length < 3) throw new IllegalArgumentException("Invalid PEERS_RES: " + line);
                yield new ProtocolMessage(Type.PEERS_RES, parts[1], null, null, null, null, parts[2]);
            }
            case BYE -> {
                if (parts.length < 3) throw new IllegalArgumentException("Invalid BYE: " + line);
                yield new ProtocolMessage(Type.BYE, parts[1], parts[2], null, null, null, null);
            }
        };
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    private static String escape(String s) {
        if (s == null) return "";
        // Escape backslash first, then pipe
        return s.replace("\\", "\\\\").replace("|", "\\|");
    }

    private static String unescape(String s) {
        if (s == null) return "";
        StringBuilder out = new StringBuilder();
        boolean escaping = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (escaping) {
                out.append(c);
                escaping = false;
            } else if (c == '\\') {
                escaping = true;
            } else {
                out.append(c);
            }
        }
        if (escaping) out.append('\\'); // best-effort
        return out.toString();
    }

    @Override public String toString() { return toLine(); }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ProtocolMessage that)) return false;
        return type == that.type && Objects.equals(id, that.id);
    }

    @Override public int hashCode() { return Objects.hash(type, id); }
}
