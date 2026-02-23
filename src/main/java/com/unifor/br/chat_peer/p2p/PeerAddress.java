package com.unifor.br.chat_peer.p2p;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Objects;

public final class PeerAddress {
    public final String host;
    public final int port;

    public PeerAddress(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public static PeerAddress parse(String s) {
        if (s == null || s.isBlank()) throw new IllegalArgumentException("Empty peer address");
        String[] parts = s.trim().split(":");
        if (parts.length != 2) throw new IllegalArgumentException("Invalid peer address: " + s);
        return new PeerAddress(parts[0], Integer.parseInt(parts[1]));
    }

    public String normalizeHost() {
        try {
            InetAddress addr = InetAddress.getByName(host);
            return addr.getHostAddress();
        } catch (UnknownHostException e) {
            return host;
        }
    }

    @Override public String toString() { return host + ":" + port; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PeerAddress that)) return false;
        return port == that.port && Objects.equals(normalizeHost(), that.normalizeHost());
    }

    @Override public int hashCode() { return Objects.hash(normalizeHost(), port); }
}
