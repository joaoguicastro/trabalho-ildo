package com.unifor.br.chat_peer;

import com.unifor.br.chat_peer.p2p.DiscoveryService;
import com.unifor.br.chat_peer.p2p.PeerNode;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Locale;
import java.util.Scanner;

/**
 * P2P Chat (console).
 *
 * Commands:
 *   /connect host port   -> connect to a peer
 *   /peers               -> list connected peers
 *   /history             -> show session history
 *   /discover            -> send a discovery broadcast (LAN, UDP multicast)
 *   /exit                -> safe shutdown
 *
 * Any other text is broadcast to all connected peers.
 */
public class Chat {

    public static void main(String[] args) throws Exception {
        Locale.setDefault(new Locale("pt", "BR"));

        Scanner scanner = new Scanner(System.in);

        System.out.print("Digite o nome do usuário: ");
        String userName = scanner.nextLine().trim();
        if (userName.isBlank()) userName = "anon";

        System.out.print("Digite a porta para escutar: ");
        int port = Integer.parseInt(scanner.nextLine().trim());

        boolean forward = true; // multi-hop broadcast with loop prevention
        PeerNode node = new PeerNode(userName, port, forward);

        // Console output: whenever a message arrives, print it immediately.
        node.onDisplay = System.out::println;

        node.start();

        // Optional discovery (LAN): can be disabled if needed
        DiscoveryService discovery = null;
        try {
            discovery = new DiscoveryService(node);
            discovery.start();
        } catch (Exception e) {
            node.history().addSystem("Discovery UDP indisponível: " + e.getMessage());
        }

        // Optional initial manual connect
        System.out.print("Deseja conectar a outro peer agora? (s/n): ");
        String resp = scanner.nextLine().trim();
        if (resp.equalsIgnoreCase("s")) {
            System.out.print("Digite o host (IP ou nome): ");
            String host = scanner.nextLine().trim();
            System.out.print("Digite a porta do peer: ");
            int peerPort = Integer.parseInt(scanner.nextLine().trim());
            node.connectTo(host, peerPort);
        }

        System.out.println("\n=== Chat iniciado ===");
        System.out.println("Digite mensagens para enviar ou comandos:");
        System.out.println("  /connect host port | /peers | /history | /discover | /exit\n");

        BufferedReader input = new BufferedReader(new InputStreamReader(System.in));

        while (true) {
            String line = input.readLine();
            if (line == null) break;

            line = line.trim();
            if (line.isEmpty()) continue;

            if (line.startsWith("/")) {
                String[] parts = line.split("\\s+");
                String cmd = parts[0].toLowerCase();

                switch (cmd) {
                    case "/connect" -> {
                        if (parts.length < 3) {
                            System.out.println("Uso: /connect <host> <porta>");
                            continue;
                        }
                        String host = parts[1];
                        int p = Integer.parseInt(parts[2]);
                        node.connectTo(host, p);
                    }
                    case "/peers" -> {
                        var peers = node.connectedPeersSnapshot();
                        if (peers.isEmpty()) {
                            System.out.println("Nenhum peer conectado.");
                        } else {
                            System.out.println("Peers conectados:");
                            for (var peer : peers) System.out.println(" - " + peer);
                        }
                    }
                    case "/history" -> System.out.print(node.history().dump());
                    case "/discover" -> {
                        if (discovery != null) discovery.announceDiscover();
                        node.connectKnownPeers();
                        System.out.println("Discovery acionado.");
                    }
                    case "/exit" -> {
                        System.out.println("Saindo...");
                        if (discovery != null) discovery.close();
                        node.safeClose();
                        return;
                    }
                    default -> System.out.println("Comando desconhecido: " + cmd);
                }
            } else {
                node.broadcastUserText(line);
            }
        }

        // EOF
        if (discovery != null) discovery.close();
        node.safeClose();
    }
}
