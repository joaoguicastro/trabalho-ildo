package com.unifor.br.chat_peer;

import com.unifor.br.chat_peer.p2p.DiscoveryService;
import com.unifor.br.chat_peer.p2p.PeerNode;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Locale;
import java.util.Scanner;

public class Chat {

    public static void main(String[] args) throws Exception {
        Locale.setDefault(new Locale("pt", "BR"));

        Scanner scanner = new Scanner(System.in);

        System.out.print("Digite o nome do usuário: ");
        String userName = scanner.nextLine().trim();
        if (userName.isBlank()) userName = "anon";

        int port = 0;
        boolean forward = false;

        PeerNode node = new PeerNode(userName, port, forward);

        node.onDisplay = System.out::println;

        node.start();

        DiscoveryService discovery = null;
        try {
            discovery = new DiscoveryService(node);
            discovery.start();
        } catch (Exception e) {
            node.history().addSystem("Discovery UDP indisponível: " + e.getMessage());
        }

        System.out.println("\n=== Chat iniciado ===");
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

        if (discovery != null) discovery.close();
        node.safeClose();
    }
}