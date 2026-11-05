You are working in the apex-amc repository (github.com/jcham2024-arch/apex-amc).

Read these in order before touching any file:
1. AGENTS.md — project scope, non-negotiable safety rules, coding conventions,
   and the index of supplemental instructions.
2. BUILD_INSTRUCTIONS.md — the sequenced completion plan and the definition of done.
3. README.md — especially the current status table and "known incomplete" list.
4. HANDOFF.md and REVIEW.md — current known state and prior review findings.

Then:
- Inspect package.json scripts and the real directory tree, and reconcile any
  discrepancy between those files and the repo before you trust them.
- Produce a short execution plan for the next phase only. Do not attempt the whole
  plan in one pass.
- Start with Phase 0 (truth audit) unless BUILD_INSTRUCTIONS.md says otherwise.

Hard constraints:
- Never invent load data, velocities, pressures, bullet dimensions, or provenance.
  If a value has no source, leave the record out and add it to the missing-data list.
- Do not change a solver constant, drag table, or published number without adding
  a provenance citation and a regression test.
- Do not weaken or bypass `npm run verify`, lint, typecheck, or any data gate.
- Do not add production dependencies without a written justification and approval.
- Update README status, AGENTS.md known-incomplete items, and HANDOFF.md in the
  same change that fixes or closes an item. Stale docs are treated as defects.
- At the end of a session, write a HANDOFF entry: what changed, what passed,
  what is blocked, and the exact next command.

First deliverable: a Phase 0 audit report (current gate status, recipe count,
manufacturer coverage, field-app bug list, doc drift list) and a proposed
Phase 1 task breakdown. Stop and wait for approval before Phase 1.