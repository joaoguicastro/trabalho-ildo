# Relatório – Chat P2P Descentralizado

## 1. Visão Geral
O objetivo do projeto é criar um sistema de chat descentralizado baseado em P2P, permitindo comunicação entre múltiplos usuários sem servidor central.  
Cada peer executa simultaneamente:
- um **servidor TCP** (aceita múltiplas conexões)
- um **cliente TCP** (cria conexões com outros peers)

## 2. Arquitetura

### 2.1. Componentes
- **PeerNode** (`p2p/PeerNode.java`)
  - Abre `ServerSocket` e aceita conexões
  - Mantém mapa de conexões ativas
  - Implementa broadcast local e (opcional) encaminhamento multi-hop
  - Realiza troca de lista de peers (descoberta via TCP)
  - Controla histórico de mensagens e encerramento seguro

- **PeerConnection** (`p2p/PeerConnection.java`)
  - Encapsula `Socket`, `BufferedReader` e `PrintWriter`
  - Mantém metadados do peer remoto (username e porta)

- **ProtocolMessage** (`p2p/ProtocolMessage.java`)
  - Define um protocolo simples linha-a-linha:
    - `HELLO` (handshake com username e porta)
    - `MSG` (mensagens do chat)
    - `PEERS_REQ/PEERS_RES` (troca de lista de peers)
    - `BYE` (saída)

- **DiscoveryService** (`p2p/DiscoveryService.java`) – *Opcional*
  - Descoberta em LAN via UDP multicast:
    - `DISCOVER` e `HERE`
  - Apenas “encontra peers”; mensagens continuam em TCP

- **MessageHistory / ChatMessage**
  - Guarda e formata o histórico da sessão (com timestamps)

- **Chat** (UI Console)
  - Interpreta comandos e repassa ações para o `PeerNode`

### 2.2. Fluxos principais
**A) Inicialização**
1. Usuário informa nome e porta.
2. `PeerNode.start()` cria `ServerSocket` e inicia loop de accept.
3. Discovery (opcional) envia `DISCOVER`.

**B) Conexão (TCP)**
1. Peer A chama `connectTo(host,port)` para o Peer B.
2. A envia `HELLO` e `PEERS_REQ`.
3. B responde `HELLO` e `PEERS_RES`.
4. Ambos atualizam `knownPeers` e tentam auto-conectar aos endereços recebidos.

**C) Mensagens / Broadcast**
1. Usuário digita um texto → `broadcastUserText`.
2. Nó cria `MSG` com `id` (UUID) e envia para todas as conexões.
3. Ao receber `MSG`, o peer:
   - registra no histórico e exibe na interface
   - se encaminhamento estiver habilitado, reenvia para outros peers
   - usa `seenMessageIds` para evitar loops

**D) Encerramento seguro**
- Comando `/exit`:
  - envia `BYE` (best-effort)
  - fecha sockets e encerra threads de IO

## 3. Decisões Técnicas
- **TCP para mensagens**: garante entrega e ordem por conexão.
- **Protocolo linha-a-linha**: simples de debugar e suficiente para os requisitos.
- **Handshake (HELLO)**: identifica o usuário e melhora o requisito de “nome do remetente”.
- **Multi-hop broadcast**: encaminhamento opcional + de-duplicação (UUID) evita tempestade/loops.
- **Descoberta**:
  - **TCP peer exchange** (PEERS_REQ/RES): funciona em qualquer rede onde exista ao menos um contato inicial.
  - **UDP multicast opcional**: elimina (na LAN) a necessidade de informar IP/porta manualmente.

## 4. Dificuldades Encontradas
- Evitar loops no broadcast (solução: IDs + cache LRU).
- Manter o mapeamento correto de peers quando a porta remota ainda é desconhecida (solução: “promoteKey” após HELLO).
- Encerramento de threads bloqueadas em `accept()` (solução: fechar o `ServerSocket` no shutdown).

## 5. Requisitos Atendidos (Checklist)
- [x] Conexões múltiplas simultâneas
- [x] Identificação do usuário remetente (HELLO/MSG)
- [x] Broadcast para todos os peers conectados
- [x] Histórico de mensagens da sessão
- [x] Mecanismo de descoberta (troca de peers + UDP opcional)
- [x] Encerramento seguro
- [ ] GUI (opcional, não implementada nesta versão)

## 6. Como Demonstrar
1. Inicie 3 peers (A, B, C) em portas diferentes.
2. Na mesma LAN, use `/discover` para auto-conectar (ou conecte manualmente A→B e B→C).
3. Envie mensagens em A e observe broadcast nos demais.
4. Use `/history` para mostrar registro.
5. Use `/exit` e valide que as conexões são fechadas.
