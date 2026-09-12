# Analysis

Findings from spikes, investigations, and extractions — risks and unknowns retired *before* they are built on.

An analysis document (AN-xxx) records what was investigated, what was found, and what it means for plans and changes. Findings are **historical records**: written once at the end of a spike, then never rewritten — a later spike that learns more writes a new document. Plans and proposals cite findings instead of re-arguing them.

An incident where the corpus was green but later evidence contradicted it carries `reality: contradicted` and links every capability or acceptance criterion whose claim failed, in addition to the carriers that failed to prevent the incident. `clue validate --reality-gaps` derives the affected-capability view from those edges; it is not a production telemetry channel.

A spike exists to retire a risk, so it is not permanent truth: it is a measurement pinned to the revision it observed. When its findings reach a durable artifact — an architecture or design overview, a decision, a capability — name those artifacts in `carried-by: [ID, …]`. Once every plan the analysis serves is complete, and provided no live decision or constraint still cites it, `clue migrate` reports it as spent so a human can decide whether it has anything left to say. A standing rule whose evidence is gone is readable but no longer reviewable, so a citation keeps the spike; where the citation records provenance rather than evidence, remove it from the live record first and the spike is reported on the next preview. That report is a candidate and never a verdict: nothing is deleted by a command. Retiring an approved one deletes the file and names it in a successor's `supersedes:`, with Git history as the archive — never empty the document instead, which keeps its index row and the reader's navigation cost while removing the only part with value.

<!-- clue:index:start -->
<!-- clue:index:end -->
