# Midnight Council

A Blood on the Clocktower-inspired social deduction game tool and proximity voice chat mod for Minecraft Fabric.

> ⚠️ **Early Alpha (v0.1)**: This project is under active development. Features may change, and breaking changes are expected between releases.

## What It Does

Midnight Council gives server operators a complete storyteller toolkit for running social deduction games in Minecraft. The storyteller (a server operator with OP permissions) drives the entire match: seating arrangements for 5 to 15 players in a town circle, nominations, votes, executions, and day/night phase transitions. Built-in timers keep discussion and nomination phases on track. Game state is held in memory for the duration of the session.

The mod also ships with proximity voice chat over encrypted UDP using the Opus codec. Players hear each other based on in-game distance. Up close, voices are clear. Move apart, and they fade. During the night phase, dead and sleeping players can't speak or hear the living. All voice traffic is secured with ECDH key exchange and AES-GCM encryption, with replay protection via sequence numbers.

**Scope note.** This mod provides game management tools and voice chat. It does NOT include automated role logic, character assignments, or ability tracking. The storyteller handles all game rules manually.

## Requirements

| Requirement | Version |
|---|---|
| Minecraft | 26.1.2 |
| Fabric Loader | ≥0.19.2 |
| Fabric API | ≥0.150.0+26.1.2 |
| Java | ≥25 (hard requirement) |

## Quick Install (Players)

1. Install [Fabric Loader](https://fabricmc.net/) for Minecraft 26.1.2
2. Download the latest Midnight Council JAR from [GitHub Releases](../../releases)
3. Place the JAR file in your `.minecraft/mods/` directory
4. Launch Minecraft with the Fabric profile
5. **Note**: All players must install the mod for voice chat and UI features. Vanilla clients can connect but won't see the HUD, seating chart, or hear voice chat.

## Quick Start (Operators)

```text
/midnight setup          # Enter setup phase (requires OP)
# Players run: /midnight join   # Each player joins the roster
/midnight start          # Begin seating phase (requires OP)
```

For the full command reference and game lifecycle, see the [Usage Guide](docs/usage-guide.md).

## Documentation

- [Usage Guide](docs/usage-guide.md): Commands, game lifecycle, and configuration
- [API Reference](docs/api-reference.md): Public API surface for developers
- [Architecture](docs/architecture.md): Design patterns and project structure
- [Contributing](CONTRIBUTING.md): Build setup and development workflow

## License

This project is licensed under the MIT License. See [LICENSE](LICENSE) for details.

## Acknowledgments

Inspired by Blood on the Clocktower by The Pandemonium Institute. This is an unofficial fan-made tool and is not affiliated with or endorsed by The Pandemonium Institute.
