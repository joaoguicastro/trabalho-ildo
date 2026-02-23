# Chat P2P Descentralizado (TCP)

Este projeto implementa um chat **peer-to-peer (P2P)** sem servidor central.  
Cada instância atua como **cliente e servidor** ao mesmo tempo:

- **TCP** para troca de mensagens e conexões.
- **UDP multicast (opcional)** apenas para **descoberta** de peers na LAN (mensagens do chat continuam 100% em TCP).

## Como executar

### 1) Abrir o projeto
Abra a pasta `chat-peer/chat-peer` no IntelliJ (ou IDE equivalente).

### 2) Rodar
Execute a classe:

- `com.unifor.br.chat_peer.Chat`

Ao iniciar, informe:
- nome do usuário
- porta local (ex: 5000, 5001, 5002...)

### 3) Comandos
No chat:
- `/connect host porta` → conecta em um peer
- `/peers` → lista peers conectados
- `/history` → imprime histórico da sessão
- `/discover` → envia broadcast de descoberta (LAN) e tenta auto-conectar
- `/exit` → encerra com fechamento seguro

## Demonstração sugerida
1. Abra 3 terminais/instâncias:
   - Peer A na porta 5000
   - Peer B na porta 5001
   - Peer C na porta 5002
2. Use `/discover` (mesma rede) **ou** conecte manualmente A→B e B→C.
3. Envie mensagens em A e verifique broadcast (incluindo multi-hop quando habilitado).

## Estrutura
- `com.unifor.br.chat_peer.p2p` → núcleo P2P (PeerNode, protocolo, histórico, discovery)
- `com.unifor.br.chat_peer.Chat` → UI de console (entrada de comandos)


## Executar com Maven (Java 21)

1) Instale JDK 21 e Maven.
2) Na raiz do projeto (onde está o `pom.xml`):

```bash
mvn -DskipTests clean compile exec:java
```

Isso executa a classe `com.unifor.br.chat_peer.Chat`.
