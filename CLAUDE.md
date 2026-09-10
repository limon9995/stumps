# Stumps — Project Notes

## Code comments — required, beginner-friendly style

The project owner is learning to code from this project. **Every file, every function, every non-trivial block of logic must have comments written in simple, plain language** — as if explaining it to a child or a complete beginner. This applies to all code in this repository, past and future:

- Explain *what* a function does and *why* it exists, not just restate the code in words.
- Prefer short, plain sentences over jargon. If a technical term is unavoidable (e.g. "coroutine", "Flow", "Room DAO"), briefly say what it means in context.
- Comment class-level purpose, function-level purpose, and any non-obvious lines (tricky conditionals, math, workarounds).
- This is the opposite of "terse professional code with minimal comments" — favor over-explaining for this project.
- When editing or adding to an existing uncommented file, add comments to the parts you touch.

This rule applies automatically to all future work in this project — no need to ask again.

## Two-person GitHub workflow — read CONTRIBUTING.md first, every session

This project is actively worked on by two people (each often through their own AI assistant), syncing through the same GitHub repo. **Before writing or editing any code in a new session, run `git status` then `git pull origin main`** — see `CONTRIBUTING.md` in the project root for the full rules (ending a session with commit+push, handling merge conflicts, never force-pushing, and how `app/google-services.json` is shared outside of Git). Follow `CONTRIBUTING.md` exactly; it is not optional.
