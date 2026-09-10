# Working together on Stumps

This project is worked on by **two people**, each on their own computer, each often working through their own AI coding assistant (Claude Code). We don't work in the same room at the same time, so GitHub is the only thing that keeps our two copies of the project in sync.

The rules below exist for one reason: **so neither of us ever overwrites the other person's work.** Follow them every single time, no exceptions — they only take a few seconds and they prevent real headaches later.

---

## The one rule that matters most

> **Before you write a single line of code, `git pull` first. Before you stop working, commit and push.**

That's it. Everything below is just the details of how to do that safely.

---

## Starting a work session (do this every time, before touching any code)

```bash
git status
git pull origin main
```

- `git status` shows whether you (or your AI) left anything uncommitted from last time. If there's anything there, deal with it first (commit it or discard it on purpose) — don't pull on top of a messy working folder.
- `git pull origin main` downloads whatever the other person pushed since you last worked, and merges it into your local copy.
- **Only after `git pull` finishes cleanly should you (or your AI) start making changes.** If you skip this step, you're working on an outdated copy of the project, and your changes might conflict badly with what's already been pushed.

If `git pull` reports a conflict, stop and read the "If a conflict happens" section below before doing anything else.

## Ending a work session (do this every time you stop, even mid-task)

```bash
git add -A
git commit -m "a short, clear description of what changed"
git push origin main
```

- Do this **every time you stop for the day** — not just when a feature is "fully done." A half-finished feature that's committed and pushed is much safer than one sitting only on your own laptop.
- Write commit messages that actually describe what changed (e.g. `"Fix scoring bug when a batsman retires"`, not `"update"` or `"fix"`) — future-you and the other person will thank you.
- If `git push` is rejected because the other person pushed something first, run `git pull origin main` again, resolve anything it flags, and push again.

## Coordinate before starting something big

Since we're not branching separately for every feature, a quick heads-up prevents most conflicts before they happen:

- Before starting a bigger task, send a quick message like *"I'm about to work on the scoring screen"* so the other person doesn't start touching the same files at the same time.
- Small, frequent commits (several times a session) are much safer than one giant commit at the end of a long session — the less time between pull → work → push, the less chance of a real conflict.

## If a conflict happens

A "merge conflict" just means Git found two different changes to the *same lines* of the *same file* and doesn't know which one to keep. It looks scary the first time but is simple to fix:

1. Run `git status` — it lists every file with a conflict.
2. Open each of those files. Git marks the conflicting section like this:
   ```
   <<<<<<< HEAD
   (your version of the code)
   =======
   (the other person's version)
   >>>>>>> origin/main
   ```
3. Decide which version to keep (or combine both by hand), then delete the `<<<<<<<`, `=======`, and `>>>>>>>` marker lines completely — only real code should remain.
4. Once every conflicted file is fixed: `git add -A`, then `git commit` (Git already prepares a "merge" commit message for you — you can keep it), then `git push origin main`.
5. If it's confusing, stop and ask — don't guess on a conflict inside code you don't fully understand yet.

## Things to never do

- **Never `git push --force`** (or `-f`). This can permanently erase the other person's commits from GitHub. There is almost never a real reason to force-push on this project — if a normal `git push` is rejected, `git pull` first instead.
- **Never `git reset --hard`** or delete/recreate the repo to "start clean" without asking the other person first — it can throw away real, un-pushed work.
- **Never commit `app/google-services.json`, `local.properties`, build folders, or `.apk`/`.aab` files** — `.gitignore` already keeps these out automatically. Don't fight it or force-add them.

## Sharing `app/google-services.json`

This file connects the app to our shared Firebase backend (the same one both of us should be using, so we see each other's test data, tournaments, etc. in the same place). It's deliberately **not** in GitHub, because it contains this specific Firebase project's keys and shouldn't be public.

Instead, share it directly (chat app, email, etc.) — whoever set up the Firebase project sends the file to the other person **once**, who then drops it into their own `app/` folder locally. After that, it just sits there on each machine and Git ignores it, so this is a one-time step, not something you repeat per session.

---

## Rules for AI assistants (Claude Code) working on this project

If you are an AI assistant picking up work on this project, follow this exactly:

1. **At the very start of any session, before writing or editing any code:** run `git status` then `git pull origin main`. If there are uncommitted local changes already sitting in the working folder when you start, stop and ask the human what to do with them rather than pulling on top of them.
2. **Before ending a task or a session** (including when told to "continue working" phase-by-phase without stopping): commit and push what you've done, with a clear commit message, rather than leaving finished work only on the local machine.
3. **Never `git push --force`, never `git reset --hard`, never amend a commit the human didn't just make in this same session.** If a push is rejected, pull and merge normally instead.
4. **If a `git pull` produces a merge conflict:** do not guess at resolving it silently. Show the human what's conflicting and confirm before deciding which side to keep, unless the resolution is completely unambiguous (e.g. the same formatting-only change on both sides).
5. Everything in `CLAUDE.md` (code comment style, etc.) still applies on top of these rules.
