# Multi-agent email assistant with local LLMs

Java framework in which an organisation of agents proposes replies to emails, using only a
language model running **locally** (Ollama, LM Studio, llama.cpp...). A router agent selects the
most suitable specialised agent (students, colleagues, administration, research project...). That
agent drafts a reply with **its own system prompt and its own private context**. A reviewer agent
checks the draft, and the user validates, edits or rejects it. Nothing is ever sent automatically.

```
Email -> Router -(CFP)-> Specialists bid -> Router selects -> Specialist (+ Memory, own context) -> local LLM
      -> Reviewer -> Router -> User (accept / edit / reject with feedback / re-route)
```

The project report is in [docs/report/report.pdf](docs/report/report.pdf) (Word version: `docs/report/report.docx`).
See [docs/DESIGN.md](docs/DESIGN.md) for the multi-agent organisation, the architecture, the design
patterns and the answers to the design questions.

Group: Ikram BENCHALAL, Aya HADDOUN, Nada ZINA, Mohamed Amine HANNOUN (M2 Artificial Intelligence, Multi-Agent Systems course).

## Requirements

- **JDK 21 or later** (tested with JDK 23). The project has **no external dependency**: HTTP, JSON and the Swing GUI rely only on the JDK.
- A local inference engine, for example [Ollama](https://ollama.com). Without one, the application still runs with `--mock`, which uses a fake LLM.

## Build

Windows (PowerShell):
```powershell
./build.ps1
```
Linux / macOS / Git Bash:
```sh
./build.sh
```
Both produce `mail-assistant.jar`. With Maven installed, `mvn package` also works (`target/mail-assistant.jar`).

## Configure the local LLM

1. Install Ollama, then download a model (3B parameters runs on a laptop CPU with 16 GB of RAM):
   ```sh
   ollama pull qwen2.5:3b
   ```
   Larger models such as `qwen2.5:7b`, `llama3.1:8b` or `mistral:7b` give better answers but are slower.
2. Edit `config/app.properties`:
   ```properties
   # ollama | openai (LM Studio, llama.cpp, vLLM...) | mock
   llm.provider=ollama
   llm.baseUrl=http://localhost:11434
   llm.model=qwen2.5:3b
   ```
   - For "thinking" models (qwen3, deepseek-r1), `llm.think=false` asks Ollama to answer without the reasoning phase. Some Ollama versions ignore it, and a non-thinking model is then preferable.
   - For LM Studio or `llama-server`, use `llm.provider=openai` and `llm.baseUrl=http://localhost:1234` (or the server's port).
   - An agent can use its own model through the `llm.model` property in its `agent.properties`.
   - Non-local URLs are refused unless `llm.allowRemote=true`, because emails are confidential.

## Run

```sh
java -jar mail-assistant.jar               # graphical interface
java -jar mail-assistant.jar --cli         # console interface
java -jar mail-assistant.jar --mock        # without any LLM (fake answers)
java -jar mail-assistant.jar --eval        # routing experiments on data/testset
java -jar mail-assistant.jar --eval --drafts   # + draft experiments (ablation), needs the LLM
java -jar mail-assistant.jar --set llm.model=qwen2.5:7b --set routing.strategies=rules   # override settings
```
On a Windows console, add `-Dstdout.encoding=UTF-8` (and run `chcp 65001`) to display accents correctly.

### GUI

- **Inbox**:
  1. Select an email and click *Propose a reply*.
  2. The window shows the routing decision: status (CONFIDENT / AMBIGUOUS / FALLBACK), the score of each agent and the reasons.
  3. The proposed reply appears in an editable area, with the review of the reviewer agent.
  4. You can **Accept & send** (the possibly edited text goes to `data/mail/outbox/`), **Reject & regenerate** with a comment, **Regenerate with agent X** (re-route), **Discard**, or **Show prompt** to see exactly what was sent to the LLM.
- **Agents**:
  - Create, modify or delete agents: description, routing hints, budgets, forbidden information and **system prompt**.
  - Attach context documents.
  - Changes are saved to `data/agents/` and applied immediately.
- **Organisation trace**: every message exchanged between agents (FIPA-like performatives), live. It is also logged in `logs/trace.log`.

## Repository layout

```
config/app.properties        global configuration (LLM, routing, memory, review, experiments)
data/user/                   the assisted user: identity, signature, global preferences, forbidden info
data/contacts.csv            address book: address (or *@domain pattern); name; role; tags; notes
data/agents/<id>/            one folder per agent (editable without touching the code):
    agent.properties           description, keywords:weight, sender patterns/roles/tags, budgets...
    system_prompt.md           the agent's instructions
    context/                   private documents (context/pinned/ = always sent, others = retrieved by relevance)
data/mail/inbox/             received emails (.eml)
data/mail/history/           past exchanges imported into the memory at startup
data/mail/outbox/            validated replies (generated)
data/memory/threads/         persistent conversation memory, one JSON file per thread (generated)
data/testset/                labelled emails for the experiments (X-Expected-Agent, X-Expected-Facts)
docs/DESIGN.md               design document (organisation, architecture, patterns, design questions)
docs/report/                 project report (PDF and Word) and its diagrams
results/                     reports (eval-*.md) and agent traces of the experiments
src/main/java/mas/mail/
    comm/      AclMessage, Performative, MessageBus (mediator), MessageListener (observer)
    agent/     Agent, AbstractAgent, RouterAgent, SpecializedAgent, MemoryAgent, ReviewerAgent,
               AgentFactory, PromptBuilder, protocol/ (message contents)
    routing/   RoutingStrategy + ContractNet / LlmClassifier / ThreadContinuity / WeightedVoting,
               ProfileCompetenceEvaluator, DecisionPolicy
    llm/       LlmClient + OllamaClient / OpenAiCompatibleClient / MockLlmClient, LlmClientFactory
    context/   ContextSource, DirectoryContextSource, ContextRetriever, Bm25Scorer
    memory/    ConversationRepository (+ file implementation), HistoryWindowStrategy (+ 2 implementations)
    config/    AppConfig, AgentProfile (+ repository), UserProfile, ContactDirectory
    mail/      EmailParser, MailSource, MailSender (folder implementations)
    app/       MailAssistant (facade), ConsoleApp, TraceLogger
    eval/      Evaluator (experiments)
    ui/        Swing GUI
```

## Adding an agent

Create `data/agents/<id>/` with `agent.properties`, `system_prompt.md` and `context/`, or click
*New agent* in the GUI. No code change is needed: the agent joins the organisation, bids in calls
for proposals, and becomes selectable by the LLM router from its description.

## Experiments

`--eval` compares the routing configurations listed in `eval.routingConfigs` on `data/testset`. It
reports accuracy, detection of ambiguous emails, fallback correctness and a confusion matrix.
`--drafts` adds an ablation study on the drafts:
- full multi-agent setup;
- no context;
- no system prompt;
- a single generic agent.

The drafts are measured by the coverage of the expected facts, review issues, length and time.
Reports are written to `results/`.
