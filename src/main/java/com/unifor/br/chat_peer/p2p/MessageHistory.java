package com.unifor.br.chat_peer.p2p;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * In-memory message history for the current session.
 * (Requirement: "Histórico de Mensagens: registrar e exibir durante a sessão")
 */
public final class MessageHistory {

    private final List<ChatMessage> messages = Collections.synchronizedList(new ArrayList<>());

    public void addIn(String from, String text) {
        messages.add(new ChatMessage(Instant.now(), from, text, ChatMessage.Direction.IN));
    }

    public void addOut(String from, String text) {
        messages.add(new ChatMessage(Instant.now(), from, text, ChatMessage.Direction.OUT));
    }

    public void addSystem(String text) {
        messages.add(new ChatMessage(Instant.now(), "SYSTEM", text, ChatMessage.Direction.SYSTEM));
    }

    public List<ChatMessage> snapshot() {
        synchronized (messages) {
            return new ArrayList<>(messages);
        }
    }

    public String dump() {
        StringBuilder sb = new StringBuilder();
        for (ChatMessage m : snapshot()) {
            sb.append(m.format()).append(System.lineSeparator());
        }
        return sb.toString();
    }
}
