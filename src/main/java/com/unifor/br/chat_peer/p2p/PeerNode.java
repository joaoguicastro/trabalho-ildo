package com.unifor.br.chat_peer.p2p;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Core P2P node:
 * - Accepts multiple incoming connections (TCP server)
 * - Can connect to multiple peers (outgoing)
 * - Broadcasts messages to all connections
 * - (Optional) Forwards messages (multi-hop) with de-dup to avoid loops
 * - Exchanges peer lists to help discovery over TCP
 * - Keeps session message history
 * - Supports safe shutdown
 */
public final class PeerNode implements AutoCloseable {

    private final String username;
    private final int listenPort;

    private final MessageHistory history = new MessageHistory();

    private final AtomicBoolean running = new AtomicBoolean(false);

    private ServerSocket serverSocket;

    /** Active connections keyed by remoteHost:remoteListenPort when available. */
    private final ConcurrentMap<String, PeerConnection> connections = new ConcurrentHashMap<>();

    /** Known peers (from exchange or UDP discovery) */
    private final Set<PeerAddress> knownPeers = ConcurrentHashMap.newKeySet();

    /** Used to prevent broadcast loops on multi-hop forwarding */
    private final Deque<String> seenMessageIds = new ArrayDeque<>();
    private final Set<String> seenMessageSet = new HashSet<>();
    private final int seenMax = 2000;

    private final ExecutorService ioPool = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r);
        t.setDaemon(true);
        t.setName("p2p-io-" + t.getId());
        return t;
    });

    private final boolean forwardEnabled;

    public PeerNode(String username, int listenPort, boolean forwardEnabled) {
        this.username = Objects.requireNonNull(username);
        this.listenPort = listenPort;
        this.forwardEnabled = forwardEnabled;
    }

    public String username() { return username; }
    public int listenPort() { return listenPort; }
    public MessageHistory history() { return history; }

    public List<PeerAddress> connectedPeersSnapshot() {
        List<PeerAddress> out = new ArrayList<>();
        for (PeerConnection c : connections.values()) {
            int port = c.remoteListenPort() > 0 ? c.remoteListenPort() : c.socket().getPort();
            out.add(new PeerAddress(c.remoteHost(), port));
        }
        return out;
    }

    public void start() throws IOException {
        if (!running.compareAndSet(false, true)) return;

        serverSocket = new ServerSocket(listenPort);
        history.addSystem("Peer '" + username + "' ouvindo na porta " + listenPort);

        ioPool.submit(this::acceptLoop);

        // Shutdown hook (safe close)
        Runtime.getRuntime().addShutdownHook(new Thread(this::safeClose, "p2p-shutdown"));
    }

    private void acceptLoop() {
        while (running.get()) {
            try {
                Socket s = serverSocket.accept();
                PeerConnection conn = new PeerConnection(s);
                registerConnection(conn);
                ioPool.submit(() -> handleConnection(conn));
            } catch (SocketException se) {
                // happens when serverSocket is closed during shutdown
                break;
            } catch (IOException e) {
                history.addSystem("Erro ao aceitar conexão: " + e.getMessage());
            }
        }
    }

    /** Outgoing connect */
    public void connectTo(String host, int port) {
        if (!running.get()) throw new IllegalStateException("PeerNode not started");
        PeerAddress addr = new PeerAddress(host, port);
        if (isSelf(addr)) return;
        if (isAlreadyConnected(addr)) return;

        try {
            Socket socket = new Socket(host, port);
            PeerConnection conn = new PeerConnection(socket);
            registerConnection(conn);

            // handshake
            conn.send(ProtocolMessage.hello(username, listenPort));
            conn.send(ProtocolMessage.peersReq());

            ioPool.submit(() -> handleConnection(conn));
            history.addSystem("Conectado a " + host + ":" + port);
        } catch (IOException e) {
            history.addSystem("Falha ao conectar em " + host + ":" + port + " (" + e.getMessage() + ")");
        }
    }

    private boolean isSelf(PeerAddress addr) {
        String norm = addr.normalizeHost();
        return addr.port == listenPort && (norm.equals("127.0.0.1") || norm.equals("0:0:0:0:0:0:0:1") || norm.equalsIgnoreCase("localhost"));
    }

    private boolean isAlreadyConnected(PeerAddress addr) {
        String key1 = addr.normalizeHost() + ":" + addr.port;
        return connections.containsKey(key1);
    }

    private void registerConnection(PeerConnection conn) {
        // temporarily register by socket's remote host + remote port
        String key = conn.remoteHost() + ":" + conn.socket().getPort();
        connections.put(key, conn);
    }

    private void promoteKeyIfPossible(PeerConnection conn) {
        if (conn.remoteListenPort() <= 0) return;
        String oldKey = conn.remoteHost() + ":" + conn.socket().getPort();
        String newKey = conn.remoteHost() + ":" + conn.remoteListenPort();

        if (!oldKey.equals(newKey)) {
            connections.remove(oldKey);
            connections.put(newKey, conn);
        }
    }

    /** Broadcast local user text to all connected peers */
    public void broadcastUserText(String text) {
        ProtocolMessage msg = ProtocolMessage.msg(username, text);
        markSeen(msg.id);

        history.addOut(username, text);
        broadcastRaw(msg, null);
    }

    /** Called by discovery (UDP) or peer exchange */
    public void addKnownPeer(PeerAddress addr) {
        if (addr == null) return;
        if (isSelf(addr)) return;
        knownPeers.add(new PeerAddress(addr.normalizeHost(), addr.port));
    }

    /** Try connect to some known peers (best-effort) */
    public void connectKnownPeers() {
        for (PeerAddress p : new ArrayList<>(knownPeers)) {
            if (!isAlreadyConnected(p)) connectTo(p.host, p.port);
        }
    }

    private void handleConnection(PeerConnection conn) {
        try {
            // For inbound connections, we still announce ourselves so the other side can identify us.
            conn.send(ProtocolMessage.hello(username, listenPort));

            String line;
            while (running.get() && (line = conn.readLine()) != null) {
                ProtocolMessage msg;
                try {
                    msg = ProtocolMessage.parse(line);
                } catch (Exception parseErr) {
                    history.addSystem("Linha inválida recebida de " + conn.remoteHost() + ": " + parseErr.getMessage());
                    continue;
                }

                onMessage(conn, msg);
            }
        } catch (IOException e) {
            // read loop error
        } finally {
            unregisterConnection(conn);
            conn.close();
        }
    }

    private void unregisterConnection(PeerConnection conn) {
        connections.values().removeIf(c -> c == conn);
        history.addSystem("Conexão encerrada com " + conn.remoteHost());
    }

    private void onMessage(PeerConnection conn, ProtocolMessage msg) {
        switch (msg.type) {
            case HELLO -> {
                conn.setRemoteHello(msg.username, msg.listenPort == null ? -1 : msg.listenPort);
                promoteKeyIfPossible(conn);

                // Track as known peer
                if (msg.listenPort != null && msg.listenPort > 0) {
                    addKnownPeer(new PeerAddress(conn.remoteHost(), msg.listenPort));
                }
                history.addSystem("Handshake com " + conn.remoteUser() + "@" + conn.remoteHost() + ":" + conn.remoteListenPort());
            }
            case MSG -> {
                if (isSeen(msg.id)) return;
                markSeen(msg.id);

                String from = (msg.from == null || msg.from.isBlank()) ? conn.remoteUser() : msg.from;
                history.addIn(from, msg.text);

                // Print-friendly hook for console UI
                onDisplay.accept(history.snapshot().get(history.snapshot().size()-1).format());

                if (forwardEnabled) {
                    // Forward to all other peers except the one we received from
                    broadcastRaw(msg, conn);
                }
            }
            case PEERS_REQ -> {
                String csv = buildPeersCsv();
                conn.send(ProtocolMessage.peersRes(msg.id, csv));
            }
            case PEERS_RES -> {
                if (msg.peersCsv != null && !msg.peersCsv.isBlank()) {
                    String[] peers = msg.peersCsv.split(",");
                    for (String p : peers) {
                        try {
                            PeerAddress addr = PeerAddress.parse(p.trim());
                            // when peers are shared, host may be "self" from remote; prefer the remote host
                            if (addr.host.equalsIgnoreCase("localhost") || addr.host.equals("127.0.0.1")) {
                                addr = new PeerAddress(conn.remoteHost(), addr.port);
                            }
                            addKnownPeer(addr);
                        } catch (Exception ignored) {}
                    }
                }
                // best-effort auto-connect
                connectKnownPeers();
            }
            case BYE -> {
                history.addSystem("Peer saiu: " + msg.username);
                conn.close();
            }
        }
    }

    private String buildPeersCsv() {
        // Share only what we know (including ourselves), but without duplicates
        Set<String> out = new LinkedHashSet<>();
        out.add("localhost:" + listenPort);
        for (PeerAddress p : knownPeers) out.add(p.toString());
        for (PeerAddress p : connectedPeersSnapshot()) out.add(p.toString());
        return String.join(",", out);
    }

    private void broadcastRaw(ProtocolMessage msg, PeerConnection except) {
        for (PeerConnection c : connections.values()) {
            if (c == except) continue;
            if (c.isClosed()) continue;
            c.send(msg);
        }
    }

    private boolean isSeen(String id) {
        synchronized (seenMessageIds) {
            return seenMessageSet.contains(id);
        }
    }

    private void markSeen(String id) {
        synchronized (seenMessageIds) {
            if (seenMessageSet.contains(id)) return;
            seenMessageIds.addLast(id);
            seenMessageSet.add(id);
            while (seenMessageIds.size() > seenMax) {
                String old = seenMessageIds.removeFirst();
                seenMessageSet.remove(old);
            }
        }
    }

    // Simple display callback for the UI (console/GUI) to plug-in.
    public interface DisplaySink { void accept(String line); }
    public DisplaySink onDisplay = line -> {}; // default no-op

    /** Safe shutdown required by spec */
    public void safeClose() {
        if (!running.compareAndSet(true, false)) return;
        try {
            for (PeerConnection c : connections.values()) {
                try { c.send(ProtocolMessage.bye(username)); } catch (Exception ignored) {}
                c.close();
            }
            connections.clear();
        } finally {
            try { if (serverSocket != null) serverSocket.close(); } catch (IOException ignored) {}
            ioPool.shutdownNow();
        }
    }

    @Override
    public void close() {
        safeClose();
    }
}
