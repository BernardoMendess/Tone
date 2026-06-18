# Tone — Especificação Geral do Projeto

## Visão Geral

**Tone** é uma plataforma de marketplace focada na comercialização de ativos de áudio digitais — **capturas (captures)**, **presets** e **Impulse Responses (IRs)** — para pedaleiras e processadores de efeito de guitarra.

---

## Stack Tecnológica

| Camada    | Tecnologia                  | Versão        |
|-----------|-----------------------------|---------------|
| Backend   | Java (Spring Boot)          | Última estável |
| Frontend  | Angular                     | Última estável |
| Testes    | JUnit 5 + Mockito (back), Jasmine/Karma (front) | — |

---

## Diretrizes de Código e Estilo

### Princípios Gerais
- **Clean Code:** Código limpo, legível e bem organizado.
- **SOLID:** Aplicar rigorosamente os cinco princípios em todo o backend.
- **Nomenclatura em português:** Nomes de variáveis, métodos e classes em português, fáceis de entender.
- **Comentários pontuais:** Comentar apenas onde realmente necessário para esclarecer lógica não óbvia.
- **Modularização:** Modularizar o máximo possível.
- **Boas práticas:** Seguir as boas práticas de cada linguagem/framework.

### Estratégia de Testes
- **Testes unitários:** Para cada método implementado, escrever testes unitários correspondentes.
- **Testes de integração:** Para cada task/feature concluída, implementar testes de integração que validem o fluxo ponta a ponta.

---

### Frontend
- **Tecnologia:** Angular
- **Paleta de Cores (Design System - Dark & Minimalista):**
  - **Fundo Principal (Background):** Preto puro (`#000000`) ou um tom extremamente escuro (ex: `#0A0A0A`) para um visual sofisticado e imersivo.
  - **Superfícies (Cards, Modais, Menus):** Tons de cinza escuro (ex: `#1A1A1A` ou `#27272A`) para criar profundidade e separar os elementos sem a necessidade de bordas pesadas.
  - **Texto e Ícones:** Branco ou tons de cinza muito claro (ex: `#F4F4F5` para títulos, `#A1A1AA` para textos descritivos/secundários), garantindo leitura confortável.
  - **Regra de Implementação:** Centralize todos os códigos hexadecimais em um único arquivo de tema (ex: `src/styles/theme.ts` ou `constants/Colors.ts`). **Nunca** utilize cores *hardcoded* (direto nas propriedades `style`) dentro dos componentes.

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
C:\Users\berna\OneDrive\Documentos\Obsidian Vault\neurinvest
```

### Regras de Alimentação
- **Atualização Contínua:** Sempre que houver mudança de arquitetura, criação de nova entidade, nova regra de negócio ou refatoração importante, criar ou atualizar a nota correspondente no Obsidian.
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
