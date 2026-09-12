# The corpus

This directory is the **system-of-record**: the permanent, durable truth about the system. Full Cliewen changes are transient deltas on branches that get **digested** into this corpus at merge — `git log docs/` is the audit trail. Entry point for humans and agents alike; agents treat this tree as working memory when a change affects product or methodology meaning. Simple work uses no Cliewen workspace but still leaves any corpus surface it touches truthful.

## How the corpus is wired

Every artifact carries YAML frontmatter with a common core — `id`, `type`, `status`, `links`, `title` — plus small type-specific extensions. **Identity is the ID, the path is only the current address**: tooling discovers artifacts by scanning frontmatter, and external systems reference IDs, never paths.

An extracted non-decision carries `provenance: inferred` and `reversal-cost: low|high`; low explicitly permits deferral, while high-cost inferred meaning blocks an active capability joined to it by one `links:` edge. Remove `reversal-cost` when a human verifies the artifact. Decisions carry provenance in `status`. An incident analysis where the corpus was green but reality disproved a claim carries `reality: contradicted` and links the failed capability or acceptance criterion; `clue validate --reality-gaps` derives the affected-capability view. An analysis whose findings have reached durable form names those artifacts in `carried-by: [ID, …]`, which lets `clue migrate` report the spike as spent once every plan it serves is complete and no live decision or constraint still cites it.

Your repository declares whether it is an adopter of Cliewen or Cliewen's own source repository in `.clue/role.yaml` — `role: adopter` for every repository that adopted it. `clue init` writes the marker, and a repository without one is treated as an adopter. It records the role and nothing else: your Cliewen version is already carried by the managed skills' `version:` stamp and your CI caller's `clue-version` input.

There are two threads, and they meet only at the goal. The **intent thread** says what the product means:

```
VIS-001 (vision) → G-xxx (goal) → UC-xxx (use case, optional) → CAP-xxx (capability)
  → AC-xxx (acceptance criterion) → acceptance evidence
    → classified Go/JVM/Cucumber test reference, or Human acceptance brief
```

The use case is optional and links may skip it entirely; a goal reaching a capability directly is the ordinary case. The **delivery thread** says how that intent gets built, and never joins the semantic hierarchy:

```
G-xxx (goal) → P-xxx/M-xxx (plan/milestone) → CH-xxx (change) → accepted merge commit
```

Keeping them apart is why a change never edits the vision merely because it edited code.

Cross-cutting, checked against every proposal: C-xxx (constraints, including verifiable quality bars).

When a released `clue` adds or narrows a corpus obligation, preview `clue migrate` and apply its complete plan only after resolving any semantic or local-edit findings. `clue init` remains a non-destructive materializer, not an updater.

## What lives where — and when a change updates it

Each folder below holds one kind of record. A full change (the `clue-delta` loop) updates every record its work touches in the same integration; simple work remains responsible for any durable record it touches even though it uses no workspace or digest:

- **Vision** (`vision.md`) — one file, `VIS-001`, stating what this product or system is for. Optional to have, and edited only when the direction itself changes. A repository that has never stated one is valid; an unreplaced bootstrap is not.
- **Goals** (`goals/`) — who wants the system and why. A new wish enters here as `status: proposed`; a change rarely touches goals.
- **Use cases** (`use-cases/`) — optional: one actor's end-to-end path across capabilities, when the capabilities alone do not explain the outcome. Zero is a normal number, and nothing measures their absence.
- **Plans** (`plans/`) — campaigns with verifiable milestones. Every full change names the plan item it serves (or declares itself plan-less); the digest updates plan bookkeeping, including closing a plan whose last milestone the change completes.
- **Capabilities** (`capabilities/`) — one folder per capability: `README.md` (what and why), `criteria.md` (acceptance criteria as Gherkin, each tied to its declared acceptance evidence), `design.md` (how it works). **Design is documented per capability** — a change that alters a capability's behavior updates its criteria and design in the same PR.
- **Architecture** (`architecture/`) — the required system structure overview: boundaries, actors, and durable technology choices. Updated when a change alters the system's structure or public surface, not for local detail.
- **Design** (`design/`) — the required cross-cutting behavioural overview: runtime flows, interactions, and shared patterns. Capability-local design stays in each capability folder.
- **Decisions** (`decisions/`) — why future work is constrained. A future-shaping choice routes by subject: **ADR** for software or corpus architecture, **PDR** for project/process or methodology, **IDR** for implementation. Routine facts and chronology stay in their natural carriers.
- **Constraints** (`constraints/`) — rules the system must not break: laws, licenses, policies, and verifiable quality bars (a coverage floor, a response-time bound). Each names its `source` and how it is `enforcement`-checked. Updated when the outside world imposes something or a quality bar moves.
- **Analysis** (`analysis/`) — findings from spikes and extractions. Historical records: written once, never rewritten.

## Status vocabularies

**The default lifecycle is `draft` → `active`.** It applies to every artifact type, including your own — a type `clue validate` does not recognize is validated against this default, so you can add your own artifact types under `docs/` without changing the tool. Only the types below differ, each for a stated reason. There is no `retired`: retiring a default-lifecycle artifact means deleting its file and naming it in a successor's optional `supersedes: [ID, …]` frontmatter field, whose named ID must no longer exist in the corpus — retirement is an event, not a status a file rests in. A **completed** plan is the exception a reader should know about: it records what a finished campaign referenced while it ran, so its `links:` may still name a retired artifact and validation accepts that. An active plan repoints like anything else, and a link to an ID no artifact ever declared fails everywhere, because that is a typo rather than history.

| Type | Statuses | Why not the default |
|---|---|---|
| goal | `proposed` → `accepted` | proposed goals are the inbox |
| plan | `draft` → `active` → `completed` | `completed` is immutable |
| decision | `inferred` → `verified` | provenance lives in status; human acceptance promotes |
| change, tasks | `open` | transient workspace artifacts |
| open-questions | `open` → `resolved` | transient workspace artifacts |

Each taxonomy README carries a generated index between the `clue:index` markers. A row states its artifact's identity, a status badge, and a sentence saying what the artifact is about. **The sentence is yours** — it is seeded once from the artifact's body and regeneration never rewrites it. **The badge is the generator's**: `clue scaffold` refreshes it from the artifact on every run, so a row cannot go on claiming a status its artifact no longer has. A row you left without a badge gains none, a row linking several artifacts is left alone, and a constraint's badge shows its enforcement rather than its status.

## Folders

<!-- clue:index:start -->
- [VIS-001 — Replace this with what this product or system is](vision.md) · `draft` — The one statement of what this product or system is for; goals link to it.
- [goals/](goals/README.md) — G-xxx: who wants it, why
- [plans/](plans/README.md) — P-xxx: campaigns and milestones
- [use-cases/](use-cases/README.md) — UC-xxx: optional actor journeys across capabilities
- [capabilities/](capabilities/README.md) — CAP-xxx: one folder per capability (README / criteria / design)
- [architecture/](architecture/README.md) — the whole, the expensive-to-change
- [design/](design/README.md) — cross-cutting behaviour, flows, and patterns
- [decisions/](decisions/README.md) — ADR-xxx, PDR-xxx, and IDR-xxx future-shaping choices
- [constraints/](constraints/README.md) — C-xxx: laws, licenses, policies, and verifiable quality bars you must not break
- [analysis/](analysis/README.md) — spike findings, extraction reports
<!-- clue:index:end -->
