# Tone — Especificação Geral do Projeto

## Visão Geral

**Tone** é uma plataforma de marketplace focada na comercialização de ativos de áudio digitais — **capturas (captures)**, **presets** e **Impulse Responses (IRs)** — para pedaleiras e processadores de efeito de guitarra.

---

## Stack Tecnológica

| Camada         | Tecnologia                                      | Versão         |
|----------------|-------------------------------------------------|----------------|
| Backend        | Java (Spring Boot)                              | Última estável |
| Frontend       | Angular                                         | Última estável |
| Banco de Dados | MySQL                                           | Última estável |
| Testes         | JUnit 5 + Mockito (back), Jasmine/Karma (front) | —              |

---

## Diretrizes de Código e Estilo

### Princípios Gerais
- **Clean Code:** Código limpo, legível e bem organizado.
- **SOLID:** Aplicar rigorosamente os cinco princípios em todo o backend.
- **Nomenclatura em português:** Nomes de variáveis, métodos e classes em português, fáceis de entender.
- **Comentários pontuais:** Comentar apenas onde realmente necessário para esclarecer lógica não óbvia.
- **Modularização:** Modularizar o máximo possível.
- **Boas práticas:** Seguir as boas práticas de cada linguagem/framework.
- **Lombok:** Usar e abuse de lombok.

### Estratégia de Testes
- **Testes unitários:** Para cada método implementado, escrever testes unitários correspondentes.
- **Testes de integração:** Para cada task/feature concluída, implementar testes de integração que validem o fluxo ponta a ponta.

---

### Frontend

- **Tecnologia:** Angular 19 (standalone components, lazy loading)
- **Fonte:** Inter (via Google Fonts) — 300, 400, 500, 600, 700, 800
- **Arquivo de Tema:** `src/styles/tema.css` — **TODAS** as variáveis CSS ficam aqui. Nunca hardcode cores ou valores.
- **Animações:** `src/styles/animacoes.css` — keyframes globais e classes utilitárias.
- **Estilos Globais:** `src/styles.css` — imports, reset, scrollbar, utilitários.

#### Design System

**Cores principais (variáveis CSS):**
| Token | Valor | Uso |
|-------|-------|-----|
| `--cor-fundo-principal` | `#080808` | Background geral |
| `--cor-fundo-componente` | `#0f0f0f` | Background secondary |
| `--cor-fundo-card` | `#1c1c1f` | Cards, modais, menus |
| `--cor-fundo-elevado` | `#161618` | Inputs, áreas elevadas |
| `--cor-texto-primario` | `#f5f5f7` | Títulos, texto principal |
| `--cor-texto-secundario` | `#a0a0ab` | Texto de suporte |
| `--cor-texto-terciario` | `#6b6b76` | Placeholder, labels |
| `--cor-marca-primaria` | `#8b5cf6` | CTA, focus, highlights |
| `--cor-marca-secundaria` | `#10b981` | Sucesso, acento verde |
| `--cor-erro` | `#f87171` | Erros, alerts |
| `--cor-sucesso` | `#34d399` | Sucesso |

**Tipografia:**
- Escala: `--texto-xs` (12px) → `--texto-7xl` (72px)
- Pesos: `--peso-light` → `--peso-extrabold`
- Sempre usar `var(--fonte-principal)` em `font-family`
- Títulos: `letter-spacing: -0.02em` a `-0.04em` para aparência premium

**Espaçamento:**
- Base 4px: `--espaco-1` (4px) → `--espaco-32` (128px)
- Usar `clamp()` para padding responsivo: `var(--padding-pagina)`

**Bordas e Sombras:**
- Bordas: `var(--borda-cor)` = `rgba(255,255,255,0.07)`
- Border radius: `--raio-sm` (6px) → `--raio-3xl` (24px) → `--raio-completo` (9999px)
- Sombras: `--sombra-xs` → `--sombra-xl`, `--sombra-glow`

#### Arquitetura de Componentes

**Estrutura de arquivos:**
```
src/app/
├── compartilhados/          # Componentes reutilizáveis
│   ├── navbar/
│   ├── footer/
│   ├── layout/              # Wrapper com navbar+footer para rotas auth
│   ├── alerta/
│   └── skeleton-loader/
├── paginas/                 # Páginas/views da aplicação
│   ├── landing/             # Pública — sem guard
│   ├── login/
│   ├── cadastro/
│   ├── verificar-email/
│   ├── home/                # Rota /marketplace — com authGuard
│   ├── cadastro-produto/
│   └── cadastro-categoria/
├── guards/
├── interceptores/
├── modelos/
├── servicos/
└── validadores/
```

**Roteamento:**
- `/` → `LandingComponent` (pública, sem guard)
- `/marketplace` → `HomeComponent` (via `LayoutComponent`, com `authGuard`)
- `/login`, `/cadastro`, `/verificar-email` → standalone (sem navbar/footer)
- Rotas autenticadas são filhas de `LayoutComponent`

#### Regras de CSS

1. **Nunca** usar valores hexadecimais diretamente nos componentes — sempre usar variáveis CSS do `tema.css`
2. **Nunca** usar `!important` exceto para overrides muito específicos
3. Nomenclatura BEM-like em português: `.nome-componente__elemento--modificador`
4. Cada componente tem seu próprio arquivo `.css` — sem estilos globais no componente
5. Responsividade com breakpoints padrão:
   - Mobile: `max-width: 480px`
   - Tablet: `max-width: 768px`
   - Desktop: `max-width: 1024px`
   - Wide: `max-width: 1280px`

#### Animações

- Todas as keyframes ficam em `src/styles/animacoes.css`
- Classes utilitárias: `.animar-entrada`, `.animar-entrada-cima`, `.animar-escalar`
- Delays: `.atraso-1` a `.atraso-6` (incrementos de 50ms)
- Sempre incluir `@media (prefers-reduced-motion: reduce)` para acessibilidade
- Hover effects: `transform: translateY(-2px)` + `box-shadow` (nunca exagerar)

#### Ícones

- **SVG inline** — sem dependências externas (Heroicons-style)
- Tamanhos padrão: 14px, 16px, 18px, 20px, 22px, 24px
- Sempre adicionar `aria-hidden="true"` em SVGs decorativos
- Nunca usar emojis como ícones de interface

#### Boas Práticas Angular

- Componentes `standalone: true`
- Lazy loading em todas as rotas
- `@Input()` e `@Output()` para comunicação entre componentes
- Sem lógica de negócio nos templates — usar getters/métodos no TypeScript
- Preservar todos os `id` de elementos para compatibilidade com testes
- Não alterar TypeScript de serviços, guards, interceptors ou validators


---

## Arquitetura do Backend

### Estrutura em Camadas

```
Controller → Service → DAO (Repository)
```

- **Controller:** Apenas recebe requisições e delega para o Service. **Não deve conter lógica de negócio.**
- **Service:** Contém **toda** a lógica de negócio.
- **DAO (Repository):** Responsável exclusivamente pelo acesso a dados.

### Regras Específicas
- Utilizar **Records** e **DTOs** com moderação — apenas quando realmente necessário.
- **Validações** devem ser feitas com **Bean Validation** diretamente nos modelos (`@NotNull`, `@Size`, etc.), incluindo validações personalizadas específicas para o negócio (não apenas validações sintáticas).
- **Não adicionar lógica nos Controllers** — toda lógica deve estar no Service ou nas validações do modelo.

---

## Tom e Personalidade

- **Direto ao ponto:** Sem enrolação ou explicações desnecessárias.
- **Foco nas implementações:** Mostrar o código e as mudanças realizadas de forma clara.
- **Sem jargões desnecessários:** Comunicação objetiva e prática.

---

## Documentação e Base de Conhecimento (Obsidian)

O projeto utiliza o **Obsidian** como repositório central de conhecimento, arquitetura e decisões de negócio.

### Caminho do Vault
```
C:\Users\berna\OneDrive\Documentos\Obsidian Vault\tone
```

### Regras de Alimentação
- **Atualização Contínua:** Sempre que houver mudança de arquitetura, criação de nova entidade, nova regra de negócio ou refatoração importante, criar ou atualizar a nota correspondente no Obsidian.
- **Stack Tecnológica:** Sempre que uma nova tecnologia, framework, biblioteca ou dependência for adicionada ao projeto (seja no `pom.xml`, `package.json` ou qualquer outro manifesto), **atualizar obrigatoriamente** a nota `[[Stack Tecnológica]]` no Obsidian, adicionando a nova entrada na tabela da camada correspondente (Backend, Frontend, Testes, Ferramentas).
- **Formatação Padrão:**
  - Utilizar **Wikilinks** (`[[Nome do Arquivo]]`) para conectar conceitos.
  - Adicionar **Frontmatter (YAML)** no início de toda nova nota:
    ```yaml
    ---
    data: YYYY-MM-DD
    tipo: [arquitetura, entidade, regra-de-negocio, log]
    tags: [tone, backend, frontend]
    ---
    ```
  - Utilizar **Tags** (`#nomedatag`) no corpo do texto para facilitar indexação.
- **Evitar Duplicação:** Antes de criar um novo arquivo `.md` no Obsidian, verificar o contexto para garantir que não exista nota semelhante que deva apenas ser atualizada.
- **Organização de Pastas:** Respeitar a hierarquia de pastas do Vault.
- **Nomenclatura de Arquivos:**
  - Para código, usar o nome exato da classe/componente (ex: `ProdutoService`).
  - Para entidades, usar o nome no singular (ex: `Usuario`).
  - Para lógicas globais, usar prefixos (ex: `Regra - Cálculo de Royalties`, `Arch - Fluxo de Autenticação`).

## 🛡️ Segurança
A segurança deve ser eficiente e não atrasar o lançamento. A regra é **não reinventar a roda**.

### 1. Terceirização Crítica (BaaS & PaaS)
*   **Autenticação:** Proibido criar sistemas de login complexos do zero. Usar infraestrutura madura ou provedores externos.
*   **Pagamentos:** Nenhuma transação ou dado de cartão deve passar pelos nossos servidores. Usar infraestrutura de terceiros (Stripe).
*   **Infraestrutura:** Hospedagem gerenciada para delegar proteção básica de infra e configuração de certificados SSL.

### 2. Defesa Ativa no Código
*   **Prompting Defensivo:** Sempre instruir a IA a usar práticas seguras. Ex: *"Gere esta query garantindo sanitização contra SQL Injection"*.
*   **Análise Estática (SAST):** Manter verificadores de dependências (Snyk, Dependabot) ativos no repositório para evitar o uso de pacotes com vulnerabilidades conhecidas.

### 3. Regras Estritas para OAuth2 (Spring Security)
Ao integrar provedores externos (Google, GitHub), as seguintes travas de segurança são obrigatórias na arquitetura do backend:
*   **Proteção de Credenciais:** Proibido *hardcode*. Injetar `client-id` e `client-secret` exclusivamente via variáveis de ambiente no `application.yml`.
*   **Prevenção de Account Takeover (Sequestro de Conta):** Na classe que estende `DefaultOAuth2UserService`, ao cruzar o e-mail do provedor com o banco de dados interno, é obrigatório validar a flag de verificação do provedor (ex: `email_verified: true`). Isso impede que invasores usem contas falsas em provedores terceiros com o e-mail de usuários legítimos do sistema.
*   **Blindagem do JWT (SuccessHandler):** Após a autenticação bem-sucedida, é proibido trafegar o token JWT gerado via parâmetro de URL (`?token=...`) para o front-end, evitando exposição no histórico do navegador. O token deve ser trafegado e armazenado utilizando **Cookies HttpOnly**.

### 4. Hardening de Autenticação
*   **Rate Limiting:** Implementar rate limiting nos endpoints de autenticação (`/login`, `/cadastrar`). Máximo de 5 tentativas por minuto por IP.
*   **Account Lockout:** Após 5 tentativas de login falhas consecutivas, bloquear a conta por 15 minutos. O contador deve ser resetado após um login bem-sucedido. A constante `MAX_TENTATIVAS_FALHAS` e `MINUTOS_BLOQUEIO` devem estar definidas no Service.
*   **Validação de Usuário Ativo:** O filtro JWT deve sempre verificar se o usuário ainda existe **e** está com o campo `ativo = true` no banco de dados antes de autenticar a requisição — tokens de contas deletadas ou desativadas devem ser rejeitados.
*   **Endpoints Públicos Explícitos:** Proibido usar wildcards (`/**`) para liberar endpoints no Spring Security. Cada endpoint público deve ser listado individualmente no `ConfiguracaoSeguranca`.
*   **Credenciais via Variáveis de Ambiente:** Em produção, **todas** as credenciais sensíveis (`jwt.segredo`, `spring.datasource.username`, `spring.datasource.password`) devem ser injetadas via variáveis de ambiente sem fallback. Em desenvolvimento, fallbacks são permitidos apenas no `application.properties` base.