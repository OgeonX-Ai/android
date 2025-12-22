# Codex Agent Guidelines

## Memory of successful fixes
- After each successful fix, write a memory entry in `memory/examples/`.
- Each entry must include: Issue Description, State, Action, Result, Diff Patch, and Rationale.
- Keep entries short, factual, and scoped to the repo.
- If a fix is later invalid, annotate the entry with an "Outdated" note instead of deleting it.

## Verification and retries
- Always verify changes by running the relevant workflow/test when possible.
- If verification fails, adjust strategy and retry with a clean state; avoid compounding changes.
- Stop after 2-3 failed attempts and surface a clear summary for human review.

## Safety and scope
- Make minimal, targeted changes. Avoid unrelated refactors.
- Use least-privilege tokens; prefer `GITHUB_TOKEN`, fall back to PAT secrets only when required.
- Do not assume files or APIs exist; confirm before using them.

## Cross-repo reuse
- Reuse patterns from `memory/examples/` when a new issue matches prior symptoms.
- Keep project-specific details inside project memory entries only.
