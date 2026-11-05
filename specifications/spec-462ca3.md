# Product Completeness Review and Build Plan
## Self-Upgrading AI Chat App with Cloud Model, Sandbox, and File Creation

---

## Purpose

This document is an instructional build-and-review guide for the `jcham2024-arch/Google-Ai-repo` project.

It defines the target product, assesses the current repository against that target, and lays out the pillars, decisions, risks, and phased plan required to ship a coherent product.

Use this document to:

- Align the team on what "complete" means.
- Prioritize work across product pillars, not just code layers.
- Make explicit the product decisions that cannot be answered by code alone.
- Sequence the build so that safety-critical foundations land before product surface.

---

## Audience

- Product designers and PMs defining the experience.
- Engineers implementing the pillars.
- Reviewers validating completeness against a shared checklist.

---

## How to Use This Document

1. Read the MVP target statement.
2. Read the current-state assessment to calibrate expectations.
3. Walk each pillar and mark what is present, partial, or missing.
4. Resolve the open product decisions before coding begins.
5. Execute the phased build plan.
6. Use the acceptance checklist to declare the MVP complete.

---

## 1. MVP Target

A single-tenant Android app where a user can:

1. Configure a cloud chat model via API base URL and API key.
2. Chat with the model.
3. Let the model operate on files in a sandbox (create, edit, list, read).
4. Let the model read the app's own source code so it can reason about itself.
5. Let the model propose upgrades by writing files into a dedicated area.
6. Review, export, and recompile externally.

No in-app compilation. No in-app build pipeline. The app produces files; the user rebuilds and updates.

Everything below is judged against whether it supports that loop end to end.

---

## 2. Current-State Assessment

Short version: the repository has a strong safety and provider skeleton but almost no product surface.

| Pillar | Present? | Notes |
|---|---|---|
| Provider presets (Gemini / OpenAI-compatible) | Partial | Preset table exists; default model name is wrong. |
| Chat model abstraction | Partial | `ChatModelPort` exists but is bypassed; `tool_calls` not parsed. |
| Tool registry + security interlock | Partial | L0–L3 model exists; iteration counter is global (bug). |
| Audit log | Partial | Hash chain exists but is non-deterministic (bug). |
| Sandbox filesystem | Missing | No scoped-storage design, no sandbox directory contract. |
| Self-code visibility | Missing | No mechanism for the model to read its own source. |
| Upgrade proposal flow | Missing | No diff, no approval, no export. |
| Chat UI / screens | Missing | No UI completeness description. |
| Onboarding / setup | Missing | No first-run flow, no key validation UX. |
| Consent UX for tools | Missing | Security interlock exists but no user-facing approval flow. |
| Audit log viewer (user-facing) | Missing | Log is written, not surfaced. |
| Persistence of chats / settings | Missing | Not described. |
| Export / handoff to recompile | Missing | No artifact format. |

**Product reading:** the repository is a safety kernel in search of a product. That is not a criticism of the kernel; it is the gap to close.

---

## 3. Pillar-by-Pillar Completeness

### Pillar A — Configuration and Connection

**Needed:**

- A Settings → Model Provider screen.
- Fields: Base URL, API Key, Model ID, optional Header overrides, optional System prompt.
- A Test Connection button returning a real, user-readable result (auth OK, model not found, rate limited, network error).
- Model discovery where the provider supports `GET /models` (OpenAI-compatible), with a fallback manual field.
- Secure key storage via Android Keystore. The key must not live in plaintext preferences.
- Multiple profiles (at least one active profile, others saved) so the user can switch providers without retyping.
- Connection status indicator on the chat screen (green / amber / red) with the last error.
- Key rotation flow.

**Currently missing:** all of it as a UX contract. The repository has provider presets but no form, no validation surface, no storage contract.

**Acceptance:** a non-technical user can go from install to first successful message in under two minutes without reading a README.

---

### Pillar B — Chat Experience

**Needed:**

- Conversation list with titles, timestamps, delete, and rename.
- Message stream with markdown and code block rendering (with copy button).
- Streaming, or an explicit non-streaming mode with spinner and cancel. A fake stream is a product bug.
- Tool-call visibility: when the model calls a tool, show a compact card with tool name, arguments, result, and duration. Collapsible.
- Approval prompt inline in the message stream when a tool requires consent.
- Stop generation, regenerate, edit-and-resend.
- Error states: network, 401, 429, 5xx, malformed model output, tool failure. Each with a distinct, actionable message.
- Context/token estimate visible, or at least a "context nearly full" warning.
- New conversation entry point that resets the interlock budget.
- Export conversation as markdown — useful for the upgrade workflow.

**Currently missing:** all UI contract. Also blocked by core issues: `tool_calls` not parsed, streaming faked, iteration counter global.

---

### Pillar C — Sandbox Filesystem

This is the heart of the product and it is entirely unspecified.

**Needed:**

- A clear sandbox root. Recommended: a dedicated app-private directory tree, optionally under user-selectable storage via SAF. Do not expose the whole filesystem.
- Tool contracts the model can call:
  - `list_files(path)`
  - `read_file(path)`
  - `write_file(path, content)`
  - `append_file(path, content)`
  - `mkdir(path)`
  - `delete_file(path)` (behind explicit consent)
  - `move(path_from, path_to)`
  - `diff(path, proposed_content)` returning a unified diff
- Path traversal protection: reject `..`, absolute paths, and symlinks escaping the root. This is a security must.
- Size and count limits: max file size, max total sandbox size, max files, so a runaway model cannot fill storage.
- Binary and non-text handling: reject or base64 with a cap; never silently corrupt.
- A file browser UI so the user can see what the AI has produced.
- A viewer with syntax highlighting for common languages.
- Version history and undo for each file (at minimum, keep last N versions). Without this, a model mistake is unrecoverable.
- Empty states, e.g. "no files yet — try asking the assistant to…".

**Currently missing:** every item. This pillar needs a design doc before code.

---

### Pillar D — Self-Code Visibility

This is the most distinctive pillar and it is also unspecified.

**Needed — choose one model and commit:**

1. Bundled source manifest (recommended for v1). Ship a read-only, versioned bundle of the app's source, or a curated subset: module structure, interfaces, key files. Expose it to the model via a read-only tool (`read_own_source(path)` and `list_own_source()`). This is deterministic, safe, and easy to test.
2. Live filesystem access via SAF to the actual project. More powerful, but requires the user to grant a folder and manage staleness. Higher risk.

**Regardless of model:**

- A manifest file (e.g. `self_manifest.json`) describing modules, entry points, and logical file boundaries. This is what the model reasons over.
- Context budgeting. The model cannot read the whole app every turn. Rule: manifest always in context; individual files on demand.
- A "who am I" system prompt describing what the app is, what modules exist, and what it cannot change (e.g. security interlock).
- Version pinning. The model should know it is reasoning against app version `X.Y.Z` so proposals are labeled with that target.

**Currently missing:** all of it.

---

### Pillar E — Self-Upgrade Workflow

The user constraint — do not compile in app; user recompiles externally — makes this a review-and-export product, not a build product.

**Needed:**

- A dedicated `proposals/` directory inside the sandbox, separate from the normal workspace. The model writes only here for upgrades.
- A `propose_patch(path, new_content, rationale)` tool that:
  - Writes the new content,
  - Generates a unified diff against the current baseline,
  - Records a rationale and model attribution,
  - Stamps the target app version.
- A Proposal Review screen:
  - File tree of proposed changes,
  - Diff view per file,
  - Rationale text,
  - Approve for export / Reject buttons,
  - Reject leaves a note the model can read next turn (feedback loop).
- An Export flow:
  - Bundle approved changes into a zip (or a git-style patch file),
  - Include a `CHANGELOG.md` the model generates,
  - Include a `README_APPLY.md` with rebuild instructions (gradle command, expected paths),
  - Share via `Intent.ACTION_SEND` or save via SAF.
- Version bump suggestion. The proposal includes a proposed `versionName` and `versionCode`.
- Idempotency. Re-exporting after more edits should not duplicate files; use a stable proposal ID.

**Currently missing:** all of it. No export, no diff, no approval, no attribution.

---

### Pillar F — Consent, Safety, and Audit (User-Facing)

The interlock exists on the code side; the UX side is absent.

**Needed:**

- A per-tool consent policy the user sets once:
  - L0 (read-only info): always allow.
  - L1 (read files, list): allow with audit.
  - L2 (write files in sandbox): allow, or ask once per session.
  - L3 (delete, network fetch, read own source outside manifest): always ask.
- A consent dialog when a tool is about to run at the "ask" tier:
  - Tool name, human-readable description, exact arguments (path, URL, size).
  - Allow once / Allow for session / Deny.
  - Deny with reason so the model can course-correct.
- A kill switch in the toolbar: Pause tools. The model can still chat, cannot call tools.
- A user-facing Audit Log screen:
  - Chronological list of every tool call with result and timestamp.
  - Filter by tool, allow, deny.
  - Verify audit chain button. This is the whole point of the hash chain and it is currently invisible.
  - Export audit log.
- A persistent consent-state chip in the chat header so the user always knows what the AI can do right now.

**Currently missing:** all user-facing pieces.

---

### Pillar G — Onboarding, Persistence, Recovery

**Needed:**

- First-run flow:
  1. Welcome and one-sentence product description.
  2. Connect a model form (base URL and key).
  3. Test Connection with success and failure.
  4. Optional: Try a starter prompt.
- Default starter prompts so the first turn is not a blank page, e.g. "Summarize what this app does", "List the files in my sandbox", "Propose a small improvement to the welcome screen".
- Persistence:
  - Chat history and settings survive process death.
  - Sandbox files survive.
  - Proposal drafts survive.
- Backup and restore of sandbox and settings (manual export and import is enough for v1).
- Diagnostics screen: app version, active provider, last error, export logs. This matters because the user is a developer; give them the tools.

**Currently missing:** all of it.

---

## 4. Cross-Cutting Gaps That Block Productization

Each of these is a "you cannot ship without it" item.

1. `tool_calls` parsing. Without this, the model cannot actually invoke the sandbox tools, so Pillars C, D, and E cannot function. This is a hard blocker.
2. Prompt-substring tool hack. Must be removed before any sandbox exists; otherwise the app will run tools the model did not ask for.
3. Deterministic audit hashing. Without it, the Verify Chain button is theater.
4. Per-session iteration budget. Without it, conversation 2 is dead on arrival.
5. Real or explicitly-disabled streaming. A fake stream is a UX lie.
6. Key storage. A plaintext API key is not acceptable in a product that lets the model write files.
7. Sandbox path hardening. Path traversal on `write_file` is a real vulnerability, not a theoretical one.

---

## 5. Scope Recommendation: MVP vs v2

To avoid scope creep, split the pillars.

### MVP (demonstrable loop end to end)

- Pillar A: single provider profile, base URL and key, Test Connection, Keystore storage.
- Pillar B: single conversation (no list), markdown rendering, tool-call cards, cancel, error states.
- Pillar C: sandbox with `list`, `read`, `write`, `append`, `mkdir`; path hardening; file browser; text viewer; last-5-versions undo.
- Pillar D: bundled source manifest only. Read-only `list_own_source` and `read_own_source` over a curated subset.
- Pillar E: `propose_patch` into `proposals/`, diff view, approve and reject, export as zip with `CHANGELOG.md` and `README_APPLY.md`.
- Pillar F: consent tiers with an ask dialog for writes and deletes; kill switch; audit log screen with Verify Chain.
- Pillar G: first-run wizard; persisted chats and sandbox; diagnostics screen.

**Explicitly out of MVP:** multiple conversations, model discovery, live SAF access to project, in-app compile, multi-user, i18n.

### v2 candidates

- Conversation list and search.
- Multiple provider profiles.
- Live SAF project access.
- Automated version bump and git-style patch export.
- Sandbox snapshots and backups.
- Proposal feedback loop across sessions (model learns from rejections).
- Cost and token accounting.

---

## 6. Product Decisions Required Before Coding

These cannot be answered by the code alone.

1. Where is the sandbox? App-private `filesDir/sandbox`, or SAF user-chosen folder, or both (private default, SAF optional)? Recommendation: private default for v1 — the only way to guarantee path hardening.
2. What is "its own code"? Full source, or a curated manifest plus key files? Recommendation: curated manifest for v1 — full source will blow context and tempt the model to propose changes to the security interlock.
3. What is the upgrade artifact? Zip of new files, unified diff patch, or both? Recommendation: both — diff for review, zip for application, plus a `MANIFEST.txt` for machine-readable intent.
4. Who owns the version? Does the model propose a version bump, or does the user set it before export? Recommendation: model proposes, user confirms in the review screen.
5. What is the trust boundary on read-own-source? Read-only forever, or can the model propose edits to its own source? Recommendation: read the whole curated set, propose edits to any file except a protected list (security interlock, audit logger, crypto, keystore code). The protected list must be visible in the UI.
6. What does deny tell the model? Free-text reason, canned reason, or silence? Recommendation: canned reason list plus optional free text — the only way the model learns.

---

## 7. Risk Register

| Risk | Why It Matters | Mitigation |
|---|---|---|
| Model deletes sandbox files | User loses work | Undo and version history are not optional; delete is L3. |
| Model proposes a patch to the security interlock | Trust collapse | Protected-file list, visible to user; proposals to protected files are rejected pre-export. |
| Model reads its own source and leaks it | Source is bundled, not secret, but keys could be nearby | Key storage in Keystore only; never in sandbox or source bundle. |
| Model triggers network fetch from a chat | Unintended egress | Network tools are L3 plus kill switch; prompt hack must be removed. |
| Sandbox fills the device | Device instability | Quotas on file size, count, and total size. |
| User cannot tell what the AI can do | Trust loss | Persistent consent-state chip in the chat header. |
| User exports a broken upgrade | Wasted iteration | Diff review, rationale, and explicit "not compiled" banner in the export. |
| API key visible in logs or audit | Key leak | Audit log must scrub secrets; Keystore only; no key in crash reports. |
| Fake streaming looks hung | Abandonment | Either real stream, or a visible thinking state with cancel. |

---

## 8. Phased Build Plan

### Phase 0 — Foundation Fixes (blockers)

- Remove prompt-substring tool execution.
- Parse `tool_calls` in the chat model client.
- Make the security interlock iteration counter per-session.
- Make audit hashing canonical and deterministic.
- Replace the brittle JSON field extractor.
- Store API keys in Keystore.

**Exit criteria:** the safety kernel is reliable enough that pillars C, D, and E have a foundation to sit on.

### Phase 1 — Connection and Chat Surface

- Settings and Model Provider screen with Test Connection.
- First-run wizard.
- Single-conversation chat surface with markdown, tool-call cards, cancel, and error states.
- Consent dialog for ask-tier tools, plus kill switch.

**Exit criteria:** a user can configure a model and complete a chat where tool calls are visible and consent is enforced.

### Phase 2 — Sandbox

- Sandbox root, path hardening, and quotas.
- Read and write tools with diff.
- File browser and viewer.
- Version history and undo.

**Exit criteria:** the model can create and edit files, and the user can inspect and revert them.

### Phase 3 — Self-Code and Proposals

- Bundled self-source manifest and read-only source tools.
- `propose_patch` tool writing into `proposals/`.
- Proposal review screen with diff and rationale.
- Approve and reject with reason.

**Exit criteria:** the model can read its own curated source and propose a patch that the user reviews.

### Phase 4 — Export and Audit

- Export flow producing a zip with changelog and README_APPLY.
- Audit log screen with Verify Chain.
- Diagnostics screen.

**Exit criteria:** the user can export approved proposals and rebuild externally; audit chain verification is user-visible.

---

## 9. Acceptance Checklist

Declare the MVP complete when all are true:

- [ ] A user can configure a provider from install to first message in under two minutes.
- [ ] Chat renders markdown and code, shows tool-call cards, and supports cancel.
- [ ] Tool calls are driven by structured model output, not prompt substrings.
- [ ] Ask-tier tools surface a consent dialog with arguments and Allow, Allow for session, and Deny with reason.
- [ ] A kill switch blocks tool calls while allowing chat.
- [ ] The model can create, read, edit, and list files in a sandbox with path hardening and quotas.
- [ ] Files can be reverted from version history.
- [ ] The model can read a curated bundle of its own source via read-only tools.
- [ ] The model can propose a patch into `proposals/` with rationale and target version.
- [ ] The user can review a diff, approve or reject with reason, and export a zip with changelog and apply instructions.
- [ ] The audit log screen lists tool calls and Verify Chain passes.
- [ ] The diagnostics screen shows app version, active provider, last error, and export logs.
- [ ] API keys are stored in Keystore and never appear in logs or the sandbox.

---

## 10. Summary for the Team

The repository today is a safety kernel: provider presets, a tool registry, a security interlock, and an audit hash chain. What it does not yet contain is the product: a settings and connection flow with secure key storage, a complete chat surface with visible tool calls and consent prompts, a sandbox filesystem with path hardening and undo, a curated self-source bundle the model can read, a proposal-and-export workflow for upgrades with diff review and a rebuild README, and a user-facing audit and diagnostics view. Before any of those pillars can be built, four core bugs must be fixed — prompt-substring tool execution must be removed, `tool_calls` must be parsed, the iteration counter must be per-session, and audit hashing must be canonical — otherwise the sandbox, the self-source, and the upgrade flow have no safe foundation to sit on.

---

## Appendix A — Glossary

- **Sandbox:** a scoped, app-controlled directory tree the model can read and write.
- **Self-source:** a curated, read-only bundle of the app's own source the model can read.
- **Proposal:** a diff and rationale produced by the model for user review.
- **Consent tier:** the L0–L3 classification that governs whether a tool runs automatically or asks the user.
- **Kill switch:** a runtime pause that blocks all tool calls while chat continues.
- **Audit chain:** a hash chain over tool calls used to prove the log has not been tampered with.
- **Export:** a zip or patch artifact handed to the user for external rebuild.

---

## Appendix B — Reviewer Questions

Use these in PR review and design review to keep the pillars honest.

- Does the change preserve the four foundation fixes from Phase 0?
- Does the change expose a user-visible surface for any new capability?
- Does the change include path hardening and quotas for new file tools?
- Does the change add audit entries for new tools?
- Does the change surface a consent prompt for any new ask-tier tool?
- Does the change expose the new behavior to the self-source manifest?
- Does the change include an export path for any new proposal type?
- Does the change update the diagnostics surface?
