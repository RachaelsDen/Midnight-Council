# Contributing to Midnight Council

Thanks for your interest in contributing! This project is in early alpha, and we welcome contributions of all kinds: bug reports, fixes, features, docs, and ideas.

Midnight Council is a Minecraft Fabric mod that runs a BotC-inspired social deduction game management tool alongside proximity voice chat. It handles seating, nominations, voting, timers, and encrypted voice routing, all inside the Minecraft server.

## Prerequisites

- **Java 25 JDK**. Required. Install Eclipse Temurin, Oracle JDK, or another Java 25 distribution. The toolchain is managed via the Foojay resolver, so Gradle will download the right JDK automatically if one is available on the system path.
- **Git**. For cloning and contributing.
- **IntelliJ IDEA** (recommended). Install the Minecraft Development plugin for first-class Fabric support. Other IDEs work too, but IntelliJ has the smoothest path for Loom projects.

## Getting the Source

```bash
git clone https://github.com/RachaelsDen/Midnight-Council.git
cd Midnight-Council
```

The project uses Fabric Loom's split source sets. Server-side code lives in `src/main/`, and client-side code lives in `src/client/`. Keep that split in mind when adding new classes.

## Building

Standard build:

```bash
./gradlew build
```

### btrfs filesystem note

If your working directory is on a btrfs filesystem, you MUST use this longer form instead:

```bash
./gradlew clean build --no-daemon --no-build-cache --no-configuration-cache -Dorg.gradle.workers.max=1
```

btrfs has a race condition with Gradle's binary test result files. Shorter forms like `./gradlew test` or `./gradlew --max-workers=1 build` are NOT enough, because the `--no-build-cache` and `--no-configuration-cache` flags are what actually prevent the race. If tests fail inexplicably on your machine, try the command above before investigating further.

If the Gradle wrapper JAR is missing from your clone, it can be restored from a standard Fabric Loom template or a sibling project.

## Running Tests

```bash
./gradlew test
# Or the btrfs-safe variant above if you hit flaky failures
```

The project has 557 tests across 54 test files. All game logic is covered by pure Java unit tests, so you do not need a running Minecraft server or client to exercise the core behavior. The test stack is:

- JUnit Jupiter 5.10.2
- Mockito 5.12.0

## Project Structure

A quick map of where things live. See [Architecture](docs/architecture.md) for the deeper design notes.

| Directory | Description |
|---|---|
| `src/main/java/.../api/` | Pure Java game logic. Zero MC/Fabric imports, enforced by `ApiPurityTest`. |
| `src/main/java/.../fabric/` | Fabric adapter layer: entrypoints, commands, networking. |
| `src/main/java/.../voice/` | Voice transport implementation (server-side UDP). |
| `src/client/java/.../client/` | Client mod: GUI screens, voice audio I/O. |
| `src/test/java/` | Test suite (557 tests). |

## Code Conventions

**API purity.** The `api/` package MUST have zero `net.minecraft.*` or `net.fabricmc.*` imports. This is enforced by `ApiPurityTest`, and violations will fail the build. All platform-specific code belongs in `fabric/adapter/` implementations of the SPI interfaces. Game logic calls SPI interfaces and never touches platform APIs directly.

**Instance-based state.** All state is instance-based for testability. Do NOT introduce static singletons. If you need shared access to something, pass it through constructors.

**No role logic.** Do NOT add character role logic. No classes, interfaces, or methods referencing character roles (demons, townsfolk, minions, and so on). Midnight Council is a game management tool, not a role system. This is a hard rule, not a style preference.

**SPI pattern.** Platform-specific behavior goes in `fabric/adapter/` implementations. Game logic in `api/` talks to SPI interfaces only.

## Git Workflow

**Issue first.** Create or identify an issue before starting any implementation. If one does not exist, open one. No implementation work should land without a linked issue.

**Branch.** Create a feature branch from `dev` if it exists, otherwise from `main`.

**Protected branches.** Never direct-push to `main` or `dev`. Treat both as protected. All changes go through pull requests and require review.

**Commit messages.** Keep them clean and concise. Describe what and why, not how. Do NOT add attribution or footer lines of any kind: no `Co-authored-by`, no `Worked with`, no similar boilerplate.

**Issue and PR bodies.** Use plain multiline text. Write actual line breaks instead of escaped `\n` sequences.

**Pull requests.** Open a PR against `dev` if it exists, otherwise against `main`. The PR body must contain a close reference (`Closes #N` or `Fixes #N`). Every PR must be linked to an issue.

### PR Size Guide

Keep pull requests reviewable. Prefer `size/L` or smaller. If a task would produce an XL or XXL PR, split it into smaller issue/branch/PR units before implementation.

| Size | Lines Changed |
|---|---|
| XS | 0-9 |
| S | 10-29 |
| M | 30-99 |
| L | 100-499 |
| XL | 500-999 |
| XXL | 1000+ |

Large generated files, lockfiles, vendored data, and mechanical refactors should be called out in the PR body so reviewers know what changed and how to review it.

## Reporting Issues

Use GitHub Issues. A good report includes:

- Minecraft version
- Fabric Loader version
- Java version
- Mod version
- Steps to reproduce
- Expected vs actual behavior
- Relevant logs

The more detail you include up front, the faster someone can dig in.
