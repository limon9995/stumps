# 🏏 Stumps

**Stumps** is a native Android app for ball-by-ball cricket scoring, tournament management, and live match broadcasting — built entirely with **Jetpack Compose** and **Material 3**.

It's designed for local cricket organizers, clubs, and casual players who want a proper scoring tool without needing an internet connection to score a match, while still being able to share live scores with friends and run full tournaments with points tables and leaderboards.

## Features

- **Ball-by-ball live scoring** — runs, extras, wickets, undo, auto-generated ball-by-ball commentary, and a wagon wheel / over-runs chart, all working fully offline
- **Live broadcast** — turn on a shareable code so friends can watch the score update in real time from another device, no login required
- **Tournament management** — create tournaments, register teams, auto-generate fixtures, and track a live points table and Orange Cap / Purple Cap leaderboards
- **Clubs & teams** — register a club, manage saved teams and rosters, and reuse them across matches and tournaments
- **Social** — follow clubs, tournaments, and teams; browse a cross-app search for public clubs/tournaments
- **Player profile & stats** — career batting/bowling/fielding stats, broken down by match format (T10/T20/ODI/Club)
- **PDF & text scorecard export/share** for any completed match
- **Cricket news feed** pulled live from ESPN Cricinfo's RSS feed
- **Full light/dark theme support**, following the system setting

## Tech stack

- **UI:** 100% Jetpack Compose, Material 3 (custom theme, shapes, typography, and a shared card/empty-state design system)
- **Navigation:** Navigation-Compose with animated screen transitions and a bottom nav bar
- **Architecture:** MVVM — Repository pattern with a lightweight manual DI container
- **Local storage:** Room (offline-first scoring/tournament data) + DataStore (user preferences)
- **Backend:** Firebase — Authentication (email/password + Google Sign-In), Firestore (live broadcast mirroring, cloud club/tournament directory), Storage, Crashlytics, Analytics
- **Language:** Kotlin, Coroutines & Flow throughout

## Building it yourself

1. Clone the repo
2. Create a Firebase project, enable Authentication + Firestore, and download your own `google-services.json` into `app/` (not included in this repo, since it contains project-specific keys)
3. Open in Android Studio and run — or build from the command line with `./gradlew assembleDebug`

## Project status

Actively developed as a personal/portfolio project. The codebase is written with heavily beginner-friendly comments throughout, since it's also being used as a learning project alongside development.
