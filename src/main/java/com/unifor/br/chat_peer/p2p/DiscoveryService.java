package com.unifor.br.chat_peer.p2p;

import java.io.Closeable;
import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;


public final class DiscoveryService implements Closeable {

    private static final String GROUP = "230.0.0.0";
    private static final int PORT = 4446;

    private final PeerNode node;
    private final AtomicBoolean running = new AtomicBoolean(false);

    private MulticastSocket socket;
    private InetAddress group;

    private final ExecutorService pool = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r);
        t.setDaemon(true);
        t.setName("p2p-discovery");
        return t;
    });

    public DiscoveryService(PeerNode node) {
        this.node = Objects.requireNonNull(node);
    }

    public void start() throws IOException {
        if (!running.compareAndSet(false, true)) return;

        group = InetAddress.getByName(GROUP);
        socket = new MulticastSocket(PORT);
        socket.setReuseAddress(true);
        socket.joinGroup(group);

        pool.submit(this::listenLoop);

        // announce ourselves once, then periodically could be added; keep simple
        announceDiscover();
    }

    /** Broadcast a "DISCOVER" packet to the multicast group. */
    public void announceDiscover() {
        if (!running.get()) return;
        String payload = "DISCOVER|" + node.listenPort() + "|" + node.username();
        send(payload);
    }

    private void listenLoop() {
        byte[] buf = new byte[2048];
        while (running.get()) {
            try {
                DatagramPacket packet = new DatagramPacket(buf, buf.length);
                socket.receive(packet);

                String data = new String(packet.getData(), packet.getOffset(), packet.getLength(), StandardCharsets.UTF_8).trim();
                String[] parts = data.split("\\|", -1);
                if (parts.length < 3) continue;

                String type = parts[0];
                int port = Integer.parseInt(parts[1]);
                String user = parts[2];

                String host = packet.getAddress().getHostAddress();
                PeerAddress peer = new PeerAddress(host, port);

                if (peer.port == node.listenPort() && (host.equals("127.0.0.1") || host.equals("0:0:0:0:0:0:0:1"))) {
                    continue;
                }

                if ("DISCOVER".equals(type)) {
                    node.addKnownPeer(peer);
                    // reply with HERE
                    sendUnicast(packet.getAddress(), "HERE|" + node.listenPort() + "|" + node.username());
                } else if ("HERE".equals(type)) {
                    node.addKnownPeer(peer);
                }

                // best-effort auto connect
                node.connectKnownPeers();

            } catch (Exception ignored) {
            }
        }
    }

    private void send(String payload) {
        try {
            byte[] data = payload.getBytes(StandardCharsets.UTF_8);
            DatagramPacket packet = new DatagramPacket(data, data.length, group, PORT);
            socket.send(packet);
        } catch (IOException ignored) {}
    }

    private void sendUnicast(InetAddress addr, String payload) {
        try {
            byte[] data = payload.getBytes(StandardCharsets.UTF_8);
            DatagramPacket packet = new DatagramPacket(data, data.length, addr, PORT);
            socket.send(packet);
        } catch (IOException ignored) {}
    }

    @Override
    public void close() {
        if (!running.compareAndSet(true, false)) return;
        try { if (socket != null && group != null) socket.leaveGroup(group); } catch (IOException ignored) {}
        if (socket != null) socket.close();
        pool.shutdownNow();
    }
}
