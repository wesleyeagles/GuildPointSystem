# Especificação Técnica — Sistema de Pontos de Guild (RF Online)

> Documento de referência para desenvolvimento. Use-o como fonte única de verdade para regras de negócio e arquitetura.

---

## 1. Visão Geral

Sistema web para gestão de pontos de guild do jogo **RF Online (Rising Force Online)**, com:

- Cadastro/login de membros (email+senha ou Discord)
- Aprovação de membros por hierarquia (Líder / Administrador / Moderador)
- Sistema de Objetivos e Eventos para distribuição de pontos
- Log auditável de todas as ações relevantes
- Dashboard com ranking (pódio)
- Sistema de itens (Weapon, Armor, Accessories, Misc) com upgrades via Talics
- Sistema de Leilões em tempo real, com chat, lances rápidos, regra de reserva de pontos entre leilões simultâneos, e desempate via roleta
- Sistema de Party (PT) e lista de espera (LFG) por mapa, para organização de grupo no servidor

### 1.1 Stack

| Camada | Tecnologia |
|---|---|
| Frontend | React + React Query + SCSS (tema centralizado para troca fácil de cores) |
| Backend | Java + Spring Boot |
| Tempo real | WebSocket (STOMP sobre SockJS recomendado) |
| Banco de dados | PostgreSQL (ver seção 8 — Decisão de Banco de Dados) |

---

## 2. Papéis e Permissões (Roles)

| Role | Permissões |
|---|---|
| **Membro** | Visualiza dashboard, logs, resgata eventos, edita o próprio perfil, dá lances em leilões |
| **Moderador** | Tudo do Membro + aprova cadastros |
| **Administrador** | Tudo do Moderador + cria Objetivos, Eventos, Leilões, Itens, ajusta pontos |
| **Líder** | Mesmas permissões de Administrador (papel único de maior hierarquia, possivelmente 1 por guild) |

> **Regra de implementação:** todo endpoint sensível deve validar role no backend (não confiar apenas no frontend).

---

## 3. Autenticação e Cadastro

### 3.1 Métodos de login
- Email + senha
- OAuth via Discord

### 3.2 Cadastro via Email/Senha
Campos do formulário:

| Campo | Obrigatório | Observação |
|---|---|---|
| Email | Sim | |
| Senha | Sim | |
| Avatar | Não | Upload de imagem |
| Nickname | Sim | |
| Raça | Sim | Vem de seed do backend |
| Classe | Sim | Vem de seed do backend |

### 3.3 Cadastro via Discord
- Nickname e Avatar vêm automaticamente do Discord.
- **Raça e Classe NÃO são preenchidos no cadastro.**
- No primeiro login, exibir um **modal obrigatório** solicitando Raça e Classe, com opção de editar o Nickname sugerido pelo Discord.
- Usuário não acessa o restante da aplicação até preencher esse modal.

### 3.4 Aprovação de Cadastro
- Todo cadastro (independente do método) entra com status `PENDENTE`.
- Apenas Líder, Administrador ou Moderador podem aprovar/rejeitar.
- Usuário pendente não deve ter acesso às funcionalidades da aplicação (apenas tela de "aguardando aprovação").

---

## 4. Perfil do Membro

Local onde o próprio usuário pode alterar:
- Nickname
- Classe
- Raça
- Avatar

**Regra de log:** qualquer alteração de Nickname, Classe ou Raça deve gerar entrada no log visível a todos os membros, contendo valor antigo e novo.

### 4.1 Ações de Líder/Administrador no perfil de um membro
No mesmo local do perfil, Líder/Administrador podem:

1. **Adicionar pontos diretamente** → exige motivo (texto obrigatório)
2. **Adicionar um Evento manualmente** ao membro → também concede pontos
3. **Remover pontos**, via duas modalidades, ambas exigindo motivo:
   - `AJUSTE`
   - `LEILÃO` (quando aplicável, dedução decorrente de um leilão)

Toda alteração de pontos deve ser registrada em log.

---

## 5. Objetivos e Eventos

### 5.1 Objetivo (`Objetivo`)
Criado por Líder/Administrador. Campos:

| Campo | Obrigatório | Observação |
|---|---|---|
| Nome | Sim | |
| Pontos | Sim | Quantidade de pontos que o objetivo vale |
| Tipo | Sim | `NORMAL` ou `LIMITADO` |
| Quantidade (limite) | Sim, se tipo = LIMITADO | Quantidade de resgates permitidos por dia |

**Regra de limite diário:**
- Se o Objetivo é `LIMITADO` com quantidade N, cada membro só pode resgatar eventos vinculados a esse objetivo **N vezes por dia**.
- Cada resgate registra `claimedAt`.
- Antes de permitir novo resgate, o backend deve contar quantos `claimedAt` daquele membro+objetivo existem no dia corrente e comparar com N.

### 5.2 Evento (`Evento`)
Criado por Líder/Administrador a partir de um Objetivo. Campos:

| Campo | Obrigatório | Observação |
|---|---|---|
| Objetivo vinculado | Sim | |
| Duração | Sim | 5min, 15min, 30min, 1h |
| Senha | Sim | 4 caracteres |

**Comportamento:**
- Ao ser criado, o Evento é transmitido em tempo real (WebSocket) para todos os membros via **toast** contendo input de senha.
- Membro digita a senha → se correta, resgata o evento e ganha os pontos do Objetivo vinculado.
- Após resgatado por um membro, **aquele evento específico não pode mais ser resgatado pelo mesmo membro** (não reaparece o toast/oportunidade para ele).
- Evento expira ao final da duração definida.

### 5.3 Negação de Resgate
- Na tela de Logs de "Eventos Resgatados", Líder/Administrador podem **negar** um resgate específico.
- Ao negar: os pontos concedidos ao membro são deduzidos automaticamente, e o log deve refletir essa reversão.

---

## 6. Sistema de Logs

Página única listando **todos** os eventos auditáveis do sistema, visível a todos os membros.

### 6.1 Eventos que devem gerar log

| Ação | Dados mínimos no log |
|---|---|
| Objetivo criado/editado/deletado | quem criou, nome, data criação, última edição, quem editou, valor em pontos, se é limitado, qual o limite |
| Evento criado | quem criou, duração, contador regressivo em tempo real, objetivo vinculado |
| Evento resgatado | quem resgatou, quando, nome do objetivo |
| Resgate negado | quem negou, pontos revertidos |
| Pontos ajustados manualmente | quem ajustou, membro afetado, motivo, quantidade |
| Leilão criado | quem criou, quando, item(ns) |
| Nickname/Classe/Raça alterados | valor antigo, valor novo, membro |
| Cadastro aprovado/rejeitado | quem aprovou/rejeitou, quando |

> Recomenda-se modelar uma tabela `log` genérica com `type`, `actorId`, `targetId`, `payload (JSON)`, `createdAt`, para acomodar todos os tipos acima sem precisar de uma tabela por entidade.

---

## 7. Dashboard

- **Ranking de membros** ordenado por pontos (decrescente).
- Top 3 em destaque visual (estilo pódio).
- **Lista dos últimos eventos**, com possibilidade de resgatar (inserir senha) diretamente pela dashboard, desde que o evento ainda esteja dentro da duração.

---

## 8. Itens

Todos os itens (Weapon, Armor, Accessories, Misc) exigem **imagem** no momento da criação.

### 8.1 Weapon

| Campo | Obrigatório | Observação |
|---|---|---|
| Nome | Sim | |
| Rarity | Sim | Seed: Normal, Purple, Intense, Orange, Leon, Relic, PVP, Special, Event |
| Level | Sim | |
| Subtype | Sim | Seed: Axe, Mace, Staff, Spear, Bow, Crossbow, Firearm, Launcher, Grenade Launcher, Knife, Sword, Throwing Knife |
| Attack Mínimo / Máximo | Sim | Default = 0 |
| Force Attack Mínimo / Máximo | Sim | Default = 0 |
| Cast | Não | Seed a definir (skill que a arma pode ativar) |
| Special Effects | Não | Até 4 |
| Upgrade (Talics) | Não | 0 a 7 níveis — ver 8.1.1 |
| Description | Não | |

#### 8.1.1 Talics de Weapon
- **Keen Talic** (incremento de Attack, aplicado sobre Attack/Force min e max):
  1→5% · 2→13% · 3→25% · 4→50% · 5→80% · 6→135% · 7→200%
- **Talics de elemento** (escolher no máximo 1):
  - Sacredfire Talic → Fogo
  - Belief Talic → Água
  - Guard Talic → Terra
  - Glory Talic → Vento

> Regra: uma Weapon pode ter Keen Talic **e** 1 talica elemental simultaneamente, mas **apenas 1 talica de elemento por item**.

### 8.2 Armor

| Campo | Obrigatório | Observação |
|---|---|---|
| Nome | Sim | |
| Rarity | Sim | Seed: Normal, Intense, Orange, Superior, Hero |
| Level | Sim | |
| Subtype | Sim | Seed: Helmet, Upper, Lower, Gloves, Shoes |
| Class | Sim | Warrior, Ranger, Force, Launcher |
| AvgDefPower | Sim | Number |
| Defense Success Rate | Sim | Number |
| Special Effects | Não | Até 4 |
| Description | Não | |
| Upgrade (Talics) | Não | Ver 8.2.1 |

#### 8.2.1 Talics de Armor
- **Favor Talic** (todos os subtypes, incremento sobre AvgDefPower):
  1→5% · 2→13% · 3→25% · 4→50% · 5→80% · 6→135% · 7→200%

- **Wisdom Talic** (exclusiva de Helmet — Continuing profit skill/force cancel probability):
  1→10% · 2→15% · 3→20% · 4→25% · 5→30% · 6→35% · 7→50%

- **Grace Talic** (exclusiva de Gloves — accuracy increase):
  1→10 · 2→20 · 3→30 · 4→45 · 5→60 · 6→75 · 7→100

- **Darkness Talic** (exclusiva de Gloves — ignore rate of opponent's blocking):
  1→2% · 2→5% · 3→10% · 4→20% · 5→30% · 6→40% · 7→50%

- **Mercy Talic** (exclusiva de Shoes — dodge increase):
  1→10 · 2→20 · 3→30 · 4→45 · 5→60 · 6→75 · 7→100

> Regra: um item de Armor pode combinar a Talic genérica do subtype (Favor) **com** a talica exclusiva do seu subtype (ex: Helmet pode ter Favor + Wisdom simultaneamente, cada uma em seu próprio nível 0–7).

### 8.3 Accessories

| Campo | Obrigatório |
|---|---|
| Nome | Sim |
| Raça | Sim |
| Subtype | Sim — Ring ou Amulet |
| Special Effects | Não — até 4 |

### 8.4 Misc

| Campo | Obrigatório |
|---|---|
| Nome | Não |
| Descrição | Não |

### 8.5 Modelagem sugerida
Dado que cada tipo de item tem campos e regras de talic muito distintos, recomenda-se:
- Tabela base `item` (id, type, name, rarity, image, description, createdBy, createdAt...)
- Tabelas específicas por tipo (`item_weapon`, `item_armor`, `item_accessory`, `item_misc`) relacionadas 1:1 com `item`
- Tabela `item_talic` (itemId, talicType, level, slot) para representar os upgrades aplicados, validando no backend as regras de exclusividade por subtype/elemento descritas acima.

---

## 9. Leilões

### 9.1 Criação
Criado por Líder/Administrador. Campos:

| Campo | Obrigatório |
|---|---|
| Item ou kit de itens (com quantidades) | Sim |
| Duração | Sim — 5m, 15m, 30m, 1h, 3h, 6h, 12h, 24h |

- Registrar em log: quem criou, quando.
- Notificação em tempo real (toast) para todos os membros, com link direto para o leilão.

### 9.2 Tela do Leilão (tempo real via WebSocket)
Deve exibir:
- Item(ns) leiloados: nome, itens, quantidades
- Campo de lance + botões de lance rápido (ex: +100 soma ao lance atual)
- Indicação de quem está vencendo, ou se há empate e quantos membros empatados
- Maior lance atual
- Chat do leilão:
  - Histórico de lances (quem, quanto, quando)
  - Mensagens de texto entre membros
  - Suporte a envio de imagens

### 9.3 Regras de tempo
- Se um lance for dado com **15 segundos ou menos** restantes → tempo do leilão é estendido para **25 segundos**.
- Ao zerar o tempo sem lance algum nos últimos segundos, um **bot do chat** anuncia sequencialmente: `DOLE 1`, `DOLE 2`, `DOLE 3`.
  - Se alguém der lance durante a janela de qualquer DOLE → tempo é estendido em **+10 segundos**.
  - Se ninguém der lance em nenhum DOLE → leilão é encerrado, vencedor declarado e pontos debitados.

### 9.4 Regra 1 — Limite de lance
- Um membro nunca pode dar lance maior do que seu saldo total de pontos.

### 9.5 Regra 2 — Reserva de pontos entre leilões simultâneos
- Pontos comprometidos em um lance vencedor de um leilão ficam **reservados** e indisponíveis para lances em outros leilões simultâneos.
- Exemplo: membro com 500 pontos, vencendo Leilão 1 com lance de 300 → só pode usar 200 pontos em outro leilão simultâneo.
- Se o lance do membro for superado no Leilão 1, os pontos reservados devem ser **liberados em tempo real** (via WebSocket) para uso em outros leilões.
- **Implementação sugerida:** manter um saldo "disponível" calculado dinamicamente (saldo total − soma dos lances vencedores ativos do membro em todos os leilões em aberto), recalculado a cada mudança de estado de lance.

### 9.6 Regra de Empate
- Lance igual ao lance atual **só é permitido se for exatamente o saldo máximo disponível do membro** (ele não tem como superar, apenas igualar).
- Exemplo válido: lance atual = 500 (Membro 1); Membro 2 tem exatamente 500 pontos disponíveis → pode igualar a 500.
- Exemplo inválido: lance atual = 500; membro tem 505 disponíveis → **não pode** dar lance de 500 (deve ser obrigado a superar, já que tem saldo para isso).
- Empates podem ocorrer entre N membros, sem limite.

### 9.7 Desempate (Roleta)
Quando o leilão termina empatado:
1. Inicia um contador anunciando o início do desempate.
2. Abre-se uma roleta (estilo pizza/roda) com os nomes dos membros empatados.
3. A roleta gira com seed aleatória definindo o vencedor.
4. Duração mínima de giro: **10 segundos**, com desaceleração gradual até parar no vencedor.

### 9.8 Encerramento
- Vencedor declarado → pontos do lance vencedor são deduzidos (linked à modalidade `LEILÃO` descrita na seção 4.1).
- Registrar em log o resultado final do leilão.

---

## Party e lista de espera (LFG)

Facilitador para montar PT no RF Online (limite de **8 membros** no jogo). Não altera pontos; coordenação social entre membros **APROVADO** com perfil completo.

### Mapas (abas)
- `GERAL`, `CAULDRON`, `ELAN` (extensível no backend via enum/migration).
- PTs listadas **por mapa**; cada mapa tem sua **própria lista de espera**.

### PT (party)
- Membro sem PT pode **criar** uma PT no mapa da aba ativa (torna-se líder).
- Máximo **8** membros por PT; um membro só pode estar em **uma** PT por vez.
- **Pedido de entrada:** membro solicita; o **líder aceita ou recusa**.
- **Convite:** líder convida (ex.: da lista de espera); o **convidado aceita ou recusa**.
- Líder pode **dissolver** a PT; qualquer membro pode **sair**. Se o líder sair e restarem membros, a liderança passa ao membro que entrou **primeiro** (`joined_at`).

### Lista de espera (LFG)
- Membro **sem PT** pode entrar na lista de espera de um mapa (nota opcional, até 200 caracteres).
- **Uma entrada por membro** no sistema; ao mudar de mapa na LFG, a entrada é atualizada (não acumula em vários mapas).
- Ao entrar em uma PT (aceite de pedido ou convite), sai automaticamente da LFG.

### Tempo real
- Tópico STOMP `/topic/parties/{MAP}` com evento `BOARD_UPDATED` após mutações no mapa.

---

## 10. Arquitetura Frontend (React)

Estrutura de pastas obrigatória — sempre seguir este padrão para novas features:

```
src/
├── Features/
│   └── Auction/
│       ├── Auction.tsx
│       ├── Auction.types.ts
│       ├── utils/
│       │   └── mask.ts
│       ├── contexts/
│       │   └── AuctionContext.tsx
│       └── components/
│           └── Roulette/
│               ├── Roulette.tsx
│               ├── Roulette.types.ts
│               ├── Roulette.styles.scss
│               └── Roulette.test.ts
│
├── Shared/
│   ├── ui/
│   │   └── components/
│   │       └── Button/
│   │           ├── Button.tsx
│   │           ├── Button.types.ts
│   │           ├── Button.styles.scss
│   │           └── Button.test.ts
│   └── utils/
│       └── ...
│
└── Domain/
    └── Auction/
        └── hooks/
            └── useAuction.ts   # encapsula GET, POST, PUT, DELETE via React Query
```

### 10.1 Convenções
- **Features/**: cada funcionalidade de página/rota vive isolada aqui (componente raiz + tipos + contexto + subcomponentes locais + estilos + testes).
- **Shared/**: componentes de UI reutilizáveis entre features (Design System) e utilitários genéricos.
- **Domain/**: hooks de acesso a dados por entidade de domínio, usando React Query (`useQuery`/`useMutation`), isolando toda chamada HTTP/WebSocket do restante da aplicação.
- Cada componente deve ter, quando aplicável: `.tsx`, `.types.ts`, `.styles.scss`, `.test.ts` no mesmo diretório.
- Estilos em SCSS com **tema centralizado** (variáveis de cor em um único arquivo, ex: `Shared/styles/theme.scss`) para permitir reskin fácil.
- Comunicação em tempo real (Eventos, Leilões, Logs) deve passar por um client WebSocket único e compartilhado (ex: `Shared/websocket/socketClient.ts`), com subscriptions por canal/tópico.

---

## 11. Arquitetura Backend (Java + Spring Boot)

### 11.1 Sugestão de organização por camadas (Domain-Driven, modular por feature)
```
com.guild.app
├── auth/            (login, cadastro, discord oauth, aprovação)
├── member/          (perfil, raça, classe, avatar)
├── objective/        (Objetivos)
├── event/            (Eventos, resgates, negação)
├── points/           (ajustes manuais, histórico de pontos)
├── item/             (Weapon, Armor, Accessories, Misc, Talics)
├── auction/          (leilões, lances, chat, roleta de desempate)
├── party/            (PT, LFG, convites e pedidos de entrada)
├── log/              (log genérico de auditoria)
├── websocket/        (configuração STOMP, canais/tópicos)
└── common/           (exceptions, configs, security, seeds)
```

### 11.2 WebSocket — canais sugeridos
| Tópico | Uso |
|---|---|
| `/topic/events` | Novo evento criado, contagem regressiva, resgates |
| `/topic/auctions/{id}` | Lances, chat, tempo restante de um leilão específico |
| `/topic/logs` | Novo log gerado, refletido em tempo real na página de logs |
| `/topic/points/{memberId}` | Atualização de saldo disponível (regra de reserva entre leilões) |
| `/topic/parties/{MAP}` | Quadro de PTs e LFG do mapa (`GERAL`, `CAULDRON`, `ELAN`) |

### 11.3 Segurança
- Toda mutação de Objetivo, Evento, Leilão, Item e ajuste de pontos deve validar role no backend (Líder/Administrador), nunca confiando em validação apenas no frontend.
- Resgate de evento e lance em leilão devem ser **idempotentes/atômicos** (evitar duplo resgate ou condição de corrida em lances simultâneos) — usar transações com lock otimista ou pessimista conforme necessidade.

---

## 12. Banco de Dados

**Recomendação: PostgreSQL.**

Motivos:
- Suporte robusto a transações ACID, essencial para a lógica de pontos/lances concorrentes.
- Tipo `JSONB` nativo, útil para o campo `payload` da tabela de log genérica e para `special_effects` dos itens.
- Bom suporte a constraints e índices compostos, necessários para a regra de limite diário de Objetivos (`memberId + objectiveId + data`).
- Maturidade no ecossistema Spring Boot (Spring Data JPA/Hibernate) e fácil escalabilidade.

> Para o estado efêmero do leilão (timers, contadores ativos), considerar uso complementar de **Redis** como cache/estado em memória, evitando sobrecarregar o Postgres com escritas de alta frequência durante lances — sincronizando o estado final no Postgres ao encerrar o leilão.

---

## 13. Pontos em Aberto (decisões pendentes para próxima etapa)

- [ ] Definir a seed completa de **Cast** (skills/forces/classkills) para Weapon.
- [ ] Confirmar se "Líder" é único por guild ou pode haver múltiplos.
- [ ] Definir política de timeout/expiração de leilões sem nenhum lance.
- [ ] Definir formato de upload e limite de tamanho para imagens de itens, avatares e chat do leilão.
