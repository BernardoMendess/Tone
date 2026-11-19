# Tone — Marketplace de Timbres Digitais

Plataforma full-stack de marketplace especializada na comercialização de ativos de áudio digitais (presets/patches, capturas neurais e Impulse Responses) para pedaleiras e processadores de guitarra, construída com **Java + Spring Boot 3.5**, **Angular 19**, **PostgreSQL 17** e **Amazon S3 / MinIO** com acesso privado.

---

## Cobertura dos Requisitos

### Módulo do Comprador
- **RF01 — Pesquisa e Filtragem Cruzada:** Busca textual combinada com filtros cruzados por gênero musical, equipamento de destino (pedaleira), equipamento de origem (amp/cab), tipo de captação e formato do ativo (`GET /api/products`, `GET /api/products/filters`).
- **RF02 — Player de Demonstração Nativo:** Reprodução imediata diretamente nos cards do catálogo e na página de detalhes do timbre, com barra de progresso/waveform clicável.
- **RF03 — Carrinho e Checkout Integrado (PIX prioritário):** Carrinho persistido por usuário, checkout com suporte prioritário a **PIX** (EMVCo BR Code com CRC16-CCITT válido + QR Code SVG + Copia e Cola + webhook HMAC assinado) e Cartão de Crédito.
- **RF04 — Biblioteca Digital Vitalícia:** Área logada listando todos os timbres individuais e patches desbloqueados via pacotes (bundles), histórico de pedidos e download vitalício seguro.
- **RF05 — Avaliações com Compra Verificada:** Validação no backend exigindo compra paga do timbre (ou de um bundle que o inclua) antes de liberar nota (1–5 estrelas) e comentário.
- **RF06 — Favoritos (Wishlist):** Salvamento rápido nos cards/detalhes e página dedicada de favoritos.

### Módulo do Criador
- **RF07 — Cadastro de Produto:** Formulário multipart com metadados técnicos, upload de binário privado do patch, áudio de demonstração (normalizado e comprimido automaticamente) e link opcional de vídeo (YouTube/Reels).
- **RF08 — Pacotes (Bundles):** Agrupamento de 2+ timbres do criador sob preço promocional com cálculo automático de desconto e desbloqueio integral na biblioteca do comprador.
- **RF09 — Versionamento de Patches:** Upload de novas versões binárias com changelog que atualiza `currentVersion`, gera notificações para todos os compradores verificados (inclusive compradores de bundles) e libera o download imediatamente.
- **RF10 — Dashboard Financeiro:** Métricas de quantidade vendida, receita bruta, taxa retida pela plataforma, saldo líquido disponível, série mensal, extrato detalhado e solicitação de saque via PIX.
- **RF11 — Gestão de Perfil Público:** Personalização de marca, slug (`/creators/:slug`), biografia, rig principal, redes sociais e portfólio público reproduzível.

### Módulo Administrativo
- **RF12 — Aprovação de Criadores (Curadoria):** Fluxo de candidatura (`PENDING` → fila de curadoria no `/admin` → `APPROVED`/`REJECTED` com motivo e notificação ao candidato). Apenas criadores aprovados podem publicar produtos.
- **RF13 — Motor de Split de Pagamento:** Divisão automática por item e por criador no momento do checkout (incluindo carrinhos multi-criador), liquidação via webhook e configuração dinâmica do take rate da plataforma.

### Requisitos Não Funcionais
- **RNF01 — UI/UX Minimalista Audio-First:** Design neutro de estúdio, foco na audição e especificações técnicas em tipografia monospace, sem excesso de cores.
- **RNF02 — Segurança de Arquivos Digitais:** Patches binários ficam fora de rotas públicas. O backend gera **Presigned URLs** temporárias (AWS SDK v2 `S3Presigner` no modo `s3`, ou token HMAC-SHA256 com TTL curto no modo `local`) somente após validar propriedade da compra.
- **RNF03 — Normalização de Áudio:** Pipeline DSP no backend (`AudioProcessingService`) normaliza demos para **-14.0 LUFS / dBFS RMS** com limiter suave em `-1.0 dBTP` (além de `DynamicsCompressorNode` no player Web Audio e suporte opcional a `ffmpeg loudnorm`).
- **RNF04 — Proteção contra Ripping de IRs:** Previews são limitados em duração, reamostrados/comprimidos com companding μ-law + dispersão de fase all-pass (ou MP3 96 kbps via FFmpeg) e entregues com `Cache-Control: no-store` e `Content-Disposition: inline`.
- **RNF05 — Resiliência do Player:** `AudioPlayerService` singleton em nível de raiz + `<app-global-player>` montado fora do `<router-outlet>`, mantendo o áudio tocando sem interrupções durante toda a navegação.
- **RNF06 — Responsividade Mobile-First:** Layout adaptável com barra de filtros colapsável em smartphones, tabelas com scroll fluido e player inferior compacto.

---

## Estrutura do Projeto

```text
Tone/
├── docker-compose.yml        # PostgreSQL 17 + MinIO (S3 privado) + inicializador de bucket
├── backend/                  # Java 21+ / Spring Boot 3.5.6 / Flyway / JPA / AWS SDK S3
│   └── src/main/java/com/tone/
│       ├── audio/            # Normalização -14 LUFS, anti-IR ripping e sintetizador de demos
│       ├── config/           # DataSeeder (catálogo inicial + contas de demonstração)
│       ├── controller/       # REST APIs (Catalog, Audio, Cart, Checkout, Library, Creator, Admin)
│       ├── domain/           # Entidades JPA e enums
│       ├── payment/          # Gateway PIX EMVCo + assinaturas HMAC de webhook
│       ├── security/         # JWT stateless + RBAC
│       ├── service/          # Regras de negócio (Split, Curadoria, Versionamento, Reviews)
│       └── storage/          # FileStorageService (LocalVault HMAC + Amazon S3 / MinIO)
└── frontend/                 # Angular 19 Standalone + Signals + Web Audio Global Player
    └── src/app/
        ├── core/             # Models, Interceptors, Guards, Auth/Audio/Cart/Wishlist/API Services
        ├── shared/           # Navbar, GlobalPlayer, ToneCard, PixQr
        └── pages/            # Catalog, ProductDetail, Checkout, Library, Wishlist,
                              # CreatorProfile, CreatorStudio, Admin, Auth
```

---

## Como Executar

### Opção 1 — Desenvolvimento Rápido (Zero Config: H2 em modo PostgreSQL + Private Vault local)

1. **Backend (porta `8080`):**
   ```bash
   cd backend
   mvn spring-boot:run
   ```
   *(O perfil padrão `dev` aplica automaticamente a migração Flyway `V1__init_schema.sql`, gera os arquivos binários no cofre privado e sintetiza as 8 demos de guitarra normalizadas em -14 LUFS.)*

2. **Frontend Angular (porta `4200` com proxy para `/api`):**
   ```bash
   cd frontend
   npm install
   npm start
   ```
   Acesse: **http://localhost:4200**

### Opção 2 — Stack Completa de Produção Local (PostgreSQL 17 + MinIO S3 Privado)

1. Suba o PostgreSQL e o MinIO (bucket privado `tone-private` com acesso público bloqueado):
   ```bash
   docker compose up -d
   ```
2. Inicie o backend com o perfil `postgres`:
   ```bash
   cd backend
   mvn spring-boot:run -Dspring-boot.run.profiles=postgres
   ```
3. Inicie o frontend:
   ```bash
   cd frontend
   npm start
   ```

---

## Contas de Demonstração (Seed)

Na tela **Entrar (`/auth`)** existem botões de acesso rápido para testar todos os papéis:

| Papel | E-mail | Senha | O que testar |
|---|---|---|---|
| **Comprador** | `buyer@tone.com` | `Buyer@123` | Biblioteca Digital (com patch atualizado `v1.2.0` + itens de bundle), Presigned Downloads, Favoritos, Avaliações Verificadas, Carrinho e Checkout PIX |
| **Criador Aprovado** | `lucas@tone.com` | `Creator@123` | Dashboard Financeiro (split 85%/15%), Saque PIX, Upload de Novo Timbre, Versionamento de Patches, Criação de Bundles e Perfil Público |
| **Criador Pendente** | `pedro@tone.com` | `Creator@123` | Experiência de criador aguardando aprovação na fila de curadoria |
| **Administrador** | `admin@tone.com` | `Admin@123` | Fila de Curadoria de Criadores (aprovar/recusar Pedro Rocha) e ajuste do Take Rate global do motor de split |

---

## Testes Automatizados

```bash
# Backend (JUnit 5 + SpringBootTest + MockMvc + Audio DSP)
cd backend
mvn test

# Frontend (Build de produção TypeScript estrito)
cd frontend
npm run build
```
