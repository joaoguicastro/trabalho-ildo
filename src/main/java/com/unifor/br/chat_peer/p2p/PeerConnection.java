package com.unifor.br.chat_peer.p2p;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Wraps a TCP socket + buffered streams and stores metadata about the remote peer.
 */
public final class PeerConnection implements Closeable {

    private final Socket socket;
    private final BufferedReader in;
    private final PrintWriter out;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    private volatile String remoteUser = "desconhecido";
    private volatile int remoteListenPort = -1;

    public PeerConnection(Socket socket) throws IOException {
        this.socket = socket;
        this.in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        this.out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
    }

    public Socket socket() { return socket; }

    public String remoteHost() { return socket.getInetAddress().getHostAddress(); }

    public void setRemoteHello(String username, int listenPort) {
        this.remoteUser = username == null || username.isBlank() ? "desconhecido" : username;
        this.remoteListenPort = listenPort;
    }

    public String remoteUser() { return remoteUser; }

    public int remoteListenPort() { return remoteListenPort; }

    public PeerAddress remoteAddress() { return new PeerAddress(remoteHost(), remoteListenPort > 0 ? remoteListenPort : socket.getPort()); }

    public void send(ProtocolMessage msg) {
        if (closed.get()) return;
        out.println(msg.toLine());
    }

    public String readLine() throws IOException { return in.readLine(); }

    public boolean isClosed() { return closed.get() || socket.isClosed(); }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) return;
        try { socket.close(); } catch (IOException ignored) {}
        try { in.close(); } catch (IOException ignored) {}
        out.close();
    }
}
