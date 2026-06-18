# API Reference

This reference documents the public API surface of the `dev.kgoodwin.midnightcouncil.api` package: 42 types spread across five subpackages. These are the pure-Java types that define the game model, event system, voice abstractions, and seating layouts.

The `api/` package has ZERO `net.minecraft.*` or `net.fabricmc.*` imports. This is enforced by `ApiPurityTest`. All platform-specific code lives in `fabric/` adapter implementations. The test walks every `.java` file under `src/main/java/dev/kgoodwin/midnightcouncil/api` and asserts that no source file contains a `net.minecraft` or `net.fabricmc` substring. Because the game logic has no compile-time dependency on Minecraft or Fabric, it can be unit-tested in isolation with plain JUnit and Mockito.

## Contents

1. [SPI Interfaces](#spi-interfaces-package-devkgoodwinmidnightcouncilapi)
2. [Value Types and GamePhase](#value-types-and-gamephase)
3. [Game Package](#game-package-apigame)
4. [Event Package](#event-package-apievent)
5. [Voice Package](#voice-package-apivoice)
6. [Seating Package](#seating-package-apiseating)
7. [Serialization Formats](#serialization-formats)

---

## SPI Interfaces (package `dev.kgoodwin.midnightcouncil.api`)

The mod uses a Service Provider Interface pattern to keep game logic decoupled from the platform. Seven interfaces define the contract between the pure game layer and the Fabric runtime. Each extends `PlatformInterface` and is implemented by a `Fabric*Adapter` class in the `fabric/` layer.

### PlatformInterface

Marker interface with no methods. Every adapter implements it, which lets game code hold a typed reference to any platform dependency without depending on Minecraft classes.

```java
public interface PlatformInterface {
}
```

### ConfigAdapter

Key/value configuration store backed by a properties file. Callers ask for typed values by key and get an `Optional` back.

```java
public interface ConfigAdapter extends PlatformInterface {
    void load();
    void save();
    <T> Optional<T> get(String key, Class<T> valueType);
    void set(String key, Object value);
}
```

> *Implementation: `fabric/adapter/FabricConfigAdapter.java`*

### SchedulerAdapter

Tick-based task scheduler. Minecraft's server tick runs 20 times per second, so one tick equals 50 milliseconds.

```java
public interface SchedulerAdapter extends PlatformInterface {
    void runNextTick(Runnable task);
    void runAfterDelay(long delayTicks, Runnable task);
}
```

> *Implementation: `fabric/adapter/FabricSchedulerAdapter.java`*

### NetworkAdapter

Payload-based messaging between the server and clients. Channels are string identifiers. The nested `PayloadHandler` functional interface is the callback shape receivers register.

```java
public interface NetworkAdapter extends PlatformInterface {
    void broadcastPublicPayload(String channel, byte[] payload);
    void sendStorytellerPayload(PlayerReference storyteller, String channel, byte[] payload);
    void registerReceiver(String channel, PayloadHandler handler);

    @FunctionalInterface
    interface PayloadHandler {
        void handle(PlayerReference playerReference, String channel, byte[] payload);
    }
}
```

> *Implementation: `fabric/adapter/FabricNetworkAdapter.java`*

### LoggerAdapter

Thin logging facade that routes through the platform's logger without leaking it into the API layer.

```java
public interface LoggerAdapter extends PlatformInterface {
    void debug(String message);
    void info(String message);
    void warn(String message);
    void error(String message);
    void error(String message, Throwable throwable);
}
```

> *Implementation: `fabric/adapter/FabricLoggerAdapter.java`*

### PermissionAdapter

Single permission check. "Storyteller" in this mod maps to a server operator with OP permissions.

```java
public interface PermissionAdapter extends PlatformInterface {
    boolean isStoryteller(PlayerReference playerReference);
}
```

> *Implementation: `fabric/adapter/FabricPermissionAdapter.java`*

### WorldAdapter

World interaction: blocks, entities, sound, particles, and player position lookup.

```java
public interface WorldAdapter extends PlatformInterface {
    void setBlock(Position position, String blockType);
    void clearBlock(Position position);
    String getBlockType(Position position);
    void spawnEntity(String entityType, Position position);
    Optional<Position> getPlayerPosition(PlayerReference playerReference);
    void playSound(Position position, String soundId, float volume, float pitch);
    void spawnParticles(String particleId, Position position, int count);
}
```

> *Implementation: `fabric/adapter/FabricWorldAdapter.java`*

---

## Value Types and GamePhase

### PlayerReference

A record that identifies a player by a string value (typically a name or UUID string). The compact constructor rejects null and blank values.

```java
public record PlayerReference(String value) {
    public static PlayerReference ofName(String value);
    public static PlayerReference ofUuid(UUID uuid);
    public static PlayerReference from(UUID uuid);   // alias for ofUuid
}
```

`ofName` wraps a raw string. `ofUuid` converts a `java.util.UUID` to its string form. `from` delegates to `ofUuid`.

### Position

A pure-Java three-dimensional coordinate. No validation, no behavior beyond holding three doubles.

```java
public record Position(double x, double y, double z) {
}
```

### GamePhase

Enum of the nine phases a session moves through, plus transition logic.

Constants: `IDLE`, `SETUP`, `SEATING`, `DAY`, `NOMINATION`, `VOTING`, `EXECUTION`, `NIGHT`, `GAME_OVER`.

```java
public enum GamePhase {
    boolean canTransitionTo(GamePhase target);
    GamePhase transitionTo(GamePhase target);
    boolean isInGame();
}
```

`canTransitionTo` consults a static allowed-transition table. `transitionTo` returns the target on success or throws `IllegalStateException` when the transition is not allowed. `isInGame` returns true for `DAY`, `NOMINATION`, `VOTING`, `EXECUTION`, and `NIGHT`, false for the other four.

The allowed transitions are:

| From | Allowed targets |
|---|---|
| `IDLE` | `SETUP` |
| `SETUP` | `SEATING` |
| `SEATING` | `DAY`, `NIGHT` |
| `DAY` | `NOMINATION`, `NIGHT`, `GAME_OVER` |
| `NOMINATION` | `VOTING`, `DAY`, `NIGHT` |
| `VOTING` | `EXECUTION`, `DAY`, `NIGHT` |
| `EXECUTION` | `DAY`, `NIGHT` |
| `NIGHT` | `DAY`, `GAME_OVER` |
| `GAME_OVER` | `IDLE` |

---

## Game Package (`api.game`)

Fourteen types that model the session, its mutable state, player roster, and the managers that drive nominations, votes, executions, timers, and persistence.

### GameSession

The top-level facade most callers interact with. It owns a `GameState` and a `GameEventDispatcher`, and exposes phase transitions plus player roster mutations. Player add and remove operations are only legal during the `SETUP` phase.

```java
public class GameSession {
    public GameSession();
    public GameState getState();
    public GameEventDispatcher getDispatcher();

    public void transitionPhase(GamePhase target);
    public void startSetup();
    public void startSeating();
    public void startGame();
    public void startNight();
    public void endGame();
    public void resetSession();

    public PlayerEntry addPlayer(PlayerReference playerRef, String displayName, int seatNumber);
    public PlayerEntry addStoryteller(PlayerReference playerRef, String displayName);
    public void removePlayer(PlayerReference playerRef);
    public void setPlayerAlive(PlayerReference playerRef, boolean alive);
    public void setPlayerAsleep(PlayerReference playerRef, boolean asleep);
}
```

`startGame` and `startNight` both validate the non-storyteller player count is between 5 and 15 inclusive, throwing `IllegalStateException` otherwise. `transitionPhase` increments the day or night counter when entering `DAY` (from `NIGHT` or `SEATING`) or `NIGHT`, and clears the nominated seat unless the target is `VOTING` or `EXECUTION`. Every transition dispatches a `PhaseChanged` event. `resetSession` clears all state back to `IDLE` and dispatches `PhaseChanged` if the session was not already idle.

### GameState

Mutable state holder for a single session. Owns the phase, player registry, nominated and marked seats, timer flag, and day/night counters.

```java
public class GameState {
    public GameState();

    public GamePhase getPhase();
    public void setPhase(GamePhase phase);

    public PlayerRegistry getPlayers();

    public OptionalInt getNominatedSeat();
    public void setNominatedSeat(int nominatedSeat);
    public void clearNominatedSeat();

    public OptionalInt getMarkedSeat();
    public void setMarkedSeat(int markedSeat);
    public void clearMarkedSeat();

    public boolean isTimerActive();
    public void setTimerActive(boolean timerActive);

    public int getDayCount();
    public void setDayCount(int dayCount);
    public void incrementDayCount();

    public int getNightCount();
    public void setNightCount(int nightCount);
    public void incrementNightCount();

    public int getAliveCount();
}
```

`setPhase` delegates to `GamePhase.transitionTo`, so assigning an illegal transition throws. Seat numbers and counts must be non-negative or an `IllegalArgumentException` is thrown.

### PlayerRegistry

Dual-indexed registry that maps both by `PlayerReference` and by seat number. Insertion order is preserved (backed by `LinkedHashMap`).

```java
public class PlayerRegistry {
    public PlayerEntry register(PlayerEntry playerEntry);
    public PlayerEntry claim(PlayerEntry playerEntry);   // delegates to register
    public Optional<PlayerEntry> getBySeatNumber(int seatNumber);
    public Optional<PlayerEntry> getByPlayerReference(PlayerReference playerReference);
    public Optional<PlayerEntry> unclaim(PlayerReference playerReference);
    public int getAliveCount();
    public Collection<PlayerEntry> getPlayers();          // unmodifiable
    public boolean isClaimed(PlayerReference playerReference);
    public void clear();
}
```

`register` rejects duplicate player references and duplicate seat numbers. `getAliveCount` counts non-storyteller players whose life state is `ALIVE`. `getPlayers` returns an unmodifiable view.

### PlayerEntry

Mutable per-player data. Seat number, display name, and storyteller flag are immutable after construction. Life and sleep state have setters and convenience methods.

```java
public class PlayerEntry {
    public PlayerEntry(int seatNumber, String displayName, boolean storyteller, PlayerReference playerReference);
    public PlayerEntry(int seatNumber, String displayName, LifeState lifeState,
                       SleepState sleepState, boolean storyteller, PlayerReference playerReference);

    public int getSeatNumber();
    public String getDisplayName();
    public LifeState getLifeState();
    public void setLifeState(LifeState lifeState);
    public SleepState getSleepState();
    public void setSleepState(SleepState sleepState);
    public boolean isStoryteller();
    public PlayerReference getPlayerReference();

    public boolean isAlive();
    public boolean isSleeping();
    public void kill();
    public void revive();
    public void sleep();
    public void wake();
}
```

The four-argument constructor defaults `lifeState` to `ALIVE` and `sleepState` to `AWAKE`. Seat numbers must be non-negative. Display names cannot be null or blank.

### VoteManager

Tracks a single vote at a time. A vote captures yes/no responses from each eligible voter (alive, non-storyteller players seated in seat order) and resolves the tally once everyone has voted.

Constructor takes a `GameEventDispatcher`. Dispatches `VoteResolved` when the tally completes.

```java
public class VoteManager {
    public VoteManager(GameEventDispatcher dispatcher);

    public void startVote(GameState state, PlayerReference nominee);
    public void castVote(PlayerReference voter, boolean yes);
    public VoteState getVoteState(PlayerReference player);
    public boolean isVoteInProgress();
    public Optional<PlayerReference> getNominee();
    public List<PlayerReference> getVoteOrder();   // unmodifiable
    public void reset();

    public enum VoteState {
        NOT_VOTED, VOTED_YES, VOTED_NO
    }
}
```

`startVote` requires the `VOTING` phase and that no vote is already running. `castVote` rejects ineligible voters and players who already voted. The threshold for a majority is `(eligibleCount / 2) + 1`.

### NominationManager

Per-day nomination tracking. Each alive, non-storyteller player may nominate once per day, and each nominee may only be nominated once per day.

Constructor takes a `GameEventDispatcher`. Dispatches `NominationOpened` on a successful nomination.

```java
public class NominationManager {
    public NominationManager(GameEventDispatcher dispatcher);

    public boolean canNominate(GameState state, PlayerReference nominator, PlayerReference nominee);
    public void nominate(GameState state, PlayerReference nominator, PlayerReference nominee);
    public boolean hasNominated(PlayerReference player);
    public Optional<PlayerReference> getNominatorFor(PlayerReference nominee);
    public int getNominationsToday();
    public void resetForNewDay(GameState state);
}
```

`canNominate` returns false unless both players are registered, the phase is `NOMINATION`, the nominator is alive, and neither the nominator nor nominee has already participated today. `nominate` throws `IllegalStateException` if the phase is wrong or the rules disallow the nomination. `resetForNewDay` clears the day's records and clears the nominated seat on the state.

### ExecutionManager

Storyteller-driven execution during the `EXECUTION` phase. Only a registered storyteller can execute, and only alive players can be executed.

Constructor takes a `GameEventDispatcher`. Dispatches `ExecutionResolved` with the method string `"lynch"`.

```java
public class ExecutionManager {
    public ExecutionManager(GameEventDispatcher dispatcher);

    public void execute(GameState state, PlayerReference storyteller, PlayerReference player);
    public boolean canExecuteBy(GameState state, PlayerReference storyteller);
    public boolean canExecute(GameState state, PlayerReference player);
}
```

`execute` kills the player, records their seat as the marked seat on the state, and fires `ExecutionResolved`. `canExecuteBy` checks whether the given reference is a registered storyteller. `canExecute` checks the phase is `EXECUTION` and the player is alive.

### TimerManager

Discussion and nomination timers backed by the scheduler. Defaults are 180 seconds for discussion and 30 seconds for nomination, overridable through config keys `discussionTimerSeconds` and `nominationTimerSeconds`.

```java
public class TimerManager {
    public TimerManager(SchedulerAdapter scheduler, ConfigAdapter config, GameEventDispatcher dispatcher);

    public void startDiscussionTimer(GameState state);
    public void startNominationTimer(GameState state);
    public void stopTimer(GameState state);
    public boolean isTimerRunning();
    public long getRemainingSeconds();
    public TimerType getTimerType();

    public enum TimerType {
        NONE, DISCUSSION, NOMINATION
    }
}
```

Constructor requires all three dependencies (scheduler, config, dispatcher). Starting a timer dispatches `TimerStarted` and schedules a callback that fires `TimerExpired` when the delay elapses. `stopTimer` dispatches `TimerStopped`. A generation counter guards against stale callbacks from a previously stopped timer firing. `getRemainingSeconds` returns 0 when no timer is running.

### PlayerAndSeatManager

An alternative higher-level facade over `PlayerRegistry` that auto-assigns seat numbers, dispatches player state events, and enforces the 5 to 15 player capacity. Useful when you want automatic seat assignment rather than explicit seat numbers.

Constructor takes a `GameEventDispatcher`.

```java
public class PlayerAndSeatManager {
    public PlayerAndSeatManager(GameEventDispatcher dispatcher);

    public PlayerEntry join(PlayerReference player, String displayName, boolean isStoryteller);
    public PlayerEntry claimSeat(PlayerReference player, int seatNumber);
    public Optional<PlayerEntry> unclaimSeat(PlayerReference player);
    public Optional<PlayerEntry> leave(PlayerReference player);
    public Optional<PlayerEntry> getPlayer(PlayerReference player);
    public Collection<PlayerEntry> getAllPlayers();
    public int getPlayerCount();
    public boolean hasPlayer(PlayerReference player);
}
```

`join` auto-assigns seat 0 to storytellers and the next free seat (starting at 1) to everyone else. `claimSeat` re-registers an existing player at a chosen seat, preserving their life and sleep state. Storytellers cannot claim player seats.

### PersistenceManager

JSON file persistence using format version 1. Reads and writes `GameState` to and from disk via a hand-written JSON parser (no external JSON library dependency).

```java
public class PersistenceManager {
    public void saveToFile(GameState state, Path file) throws IOException;
    public GameState loadFromFile(Path file) throws IOException;
}
```

`saveToFile` creates parent directories if needed, then writes UTF-8 JSON. `loadFromFile` reads the file, validates the version field, reconstructs the state, and cross-checks that nominated and marked seats reference loaded players. The JSON includes `sleepState` per player. These are utility methods: nothing in the mod auto-saves or auto-loads. Callers decide when to persist.

### GameStateCodec

Binary wire format version 2, used for client-server state synchronization. A stateless utility class with two static methods.

```java
public final class GameStateCodec {
    public static byte[] encode(GameState state);
    public static GameStateSnapshot decode(byte[] bytes);
}
```

`encode` converts the state to a `GameStateSnapshot` then writes a version byte, the phase name, counts, optional seats, timer flag, and a player array using `DataOutputStream`. `decode` reads the bytes back into a `GameStateSnapshot`, validating version, non-negative counts, and duplicate detection for seats and player references. The codec omits `SleepState` entirely: on decode, every player's sleep state is hardcoded to `AWAKE`. Sleep state is server-authoritative, and the client never reads it. `encode` wraps `IOException` in `UncheckedIOException`; `decode` does the same.

### GameStateSnapshot

Immutable record that captures a point-in-time view of game state. The wire format carries this across the network.

```java
public record GameStateSnapshot(
    GamePhase phase,
    int dayCount,
    int nightCount,
    OptionalInt nominatedSeat,
    OptionalInt markedSeat,
    boolean timerActive,
    List<PlayerSnapshot> players
) {
    public static GameStateSnapshot from(GameState state);

    public record PlayerSnapshot(
        int seatNumber,
        String displayName,
        LifeState lifeState,
        SleepState sleepState,
        boolean storyteller,
        String playerReference
    ) {
        public static PlayerSnapshot from(PlayerEntry entry);
    }
}
```

The outer record's compact constructor copies the players list defensively. `PlayerSnapshot` stores the player reference as a plain `String` (the `value()` of the `PlayerReference`), not as a `PlayerReference` itself.

### LifeState

```java
public enum LifeState {
    ALIVE, DEAD
}
```

### SleepState

```java
public enum SleepState {
    AWAKE, SLEEPING
}
```

---

## Event Package (`api.event`)

A lightweight event bus built around a marker interface, a dispatcher, and ten event records.

### GameEvent

Marker interface that every event implements.

```java
public interface GameEvent {
}
```

### GameEventDispatcher

Type-keyed listener registry. Listeners register against a specific event class, and the dispatcher routes each dispatched event to every matching handler.

```java
public class GameEventDispatcher {
    public <T extends GameEvent> void registerListener(Class<T> eventClass, Consumer<T> handler);
    public void dispatch(GameEvent event);
}
```

The dispatcher is thread-safe. It stores handlers in a `ConcurrentHashMap` keyed by event class, with each value a `CopyOnWriteArrayList`. Handlers are isolated: if one handler throws, the exception is caught and swallowed so the remaining handlers still run.

### Event records

Every event is a record that implements `GameEvent`. Below are their components.

| Event | Components |
|---|---|
| `PhaseChanged` | `GamePhase oldPhase`, `GamePhase newPhase` |
| `NominationOpened` | `PlayerReference nominator`, `PlayerReference nominee` |
| `VoteResolved` | `PlayerReference nominee`, `int voteCount`, `int threshold` |
| `ExecutionResolved` | `PlayerReference executed`, `String method` |
| `PlayerStateChanged` | `PlayerReference player`, `String changeType` |
| `TimerStarted` | `TimerType timerType`, `long durationSeconds` |
| `TimerExpired` | `TimerType timerType`, `long durationSeconds` |
| `TimerStopped` | (no components) |

`PlayerStateChanged.changeType` is a free-form string. Producers use values like `"joined"`, `"left"`, `"killed"`, `"revived"`, `"asleep"`, `"awake"`, `"join"`, `"leave"`, `"seat_claim"`, and `"seat_unclaim"`, depending on which manager produced the event. `ExecutionResolved.method` is `"lynch"`.

`TimerStarted` and `TimerExpired` reference `TimerManager.TimerType` from the game package.

---

## Voice Package (`api.voice`)

Five types defining the voice chat abstractions. The transport implementation, codec, and crypto live in the `fabric/` and `voice/` layers.

### VoiceServer

Interface for the voice server lifecycle and connection management.

```java
public interface VoiceServer {
    void start(int port);
    void stop();
    void connect(VoiceClientConnection connection);
    void disconnect(VoiceClientConnection connection);
    Collection<VoiceClientConnection> getConnections();
    void sendAudio(AudioPacket packet);
    boolean isRunning();
}
```

### VoiceClientConnection

Interface representing a single connected voice client.

```java
public interface VoiceClientConnection {
    PlayerReference getPlayerId();
    MicrophoneState getMicrophoneState();
    void setMicrophoneState(MicrophoneState state);
    Position getPosition();
    boolean isConnected();
    void sendPacket(AudioPacket packet);
    long getLastPacketTime();
}
```

### AudioPacket

Record carrying one encoded audio frame from a sender. The `encodedData` byte array is defensively copied both in the compact constructor and in the accessor, so callers cannot mutate the internal array. `equals` and `hashCode` are overridden to compare the array by value.

```java
public record AudioPacket(
    PlayerReference senderId,
    byte[] encodedData,
    long sequenceNumber,
    long timestamp
) {
    public byte[] encodedData();   // returns a clone
    public int length();
}
```

### VoiceRoutingStrategy

Interface for deciding which connections receive a given audio packet, based on the current game state.

```java
public interface VoiceRoutingStrategy {
    Collection<VoiceClientConnection> route(VoiceServer server, AudioPacket packet, GameState state);
}
```

### MicrophoneState

```java
public enum MicrophoneState {
    MUTED, ACTIVE, PUSH_TO_TALK
}
```

### Voice security overview

Voice chat uses ECDH key exchange (X25519) for session establishment, with AES-256-GCM encryption for all audio traffic. Directional traffic keys (separate keys for client-to-server and server-to-client) prevent reflection attacks. Replay protection is enforced via monotonic sequence numbers.

---

## Seating Package (`api.seating`)

Three types defining the directional enum, the seat layout record, and the catalog of layouts for 5 to 15 players.

### Direction

```java
public enum Direction {
    N, NE, E, SE, S, SW, W, NW
}
```

### SeatLayout

Record bundling the positions and facing directions for one town-circle arrangement.

```java
public record SeatLayout(
    List<Position> seatPositions,
    List<Position> lightPositions,
    List<Position> leverPositions,
    List<Direction> seatDirections,
    List<String> seatColors
) {
}
```

The record has no compact constructor, so the lists are not defensively copied. Callers that need immutability should not retain references to the lists passed in.

### SeatLayouts

Final utility class holding the catalog of layouts and the shared color list.

```java
public final class SeatLayouts {
    public static final List<String> SEAT_COLORS;
    public static SeatLayout getLayout(int playerCount);
}
```

`SEAT_COLORS` is an immutable list of 15 Minecraft dye color names: `red`, `orange`, `yellow`, `lime`, `green`, `cyan`, `light_blue`, `blue`, `purple`, `pink`, `white`, `black`, `brown`, `gray`, `light_gray`.

`getLayout` returns the precomputed layout for a player count from 5 through 15. Counts outside that range throw `IllegalArgumentException`. Each layout's color list is a sublist of `SEAT_COLORS` matching the player count.

---

## Serialization Formats

The mod maintains two distinct serialization formats for game state. They serve different purposes and intentionally differ in what they include.

**JSON v1 (Persistence).** Used by `PersistenceManager` for save and restore to disk files. This format is versioned as `1` in the JSON payload and includes a `sleepState` field per player. The parser is hand-written (no external JSON dependency), supports the standard JSON grammar with surrogate-pair handling, and caps nesting depth at 64.

**Binary v2 (Wire).** Used by `GameStateCodec` for client-server synchronization over the network. This format is versioned as `2` (a leading byte) and writes fields with `DataOutputStream`. It omits `sleepState` entirely. On decode, every player's sleep state is hardcoded to `SleepState.AWAKE`.

**Why they differ.** The wire format only carries what the client needs to render the HUD and seating chart: phase, players, seats, and counts. Sleep state is a server-side game mechanic the client never reads, so shipping it over the wire would be wasted bytes. The persistence format, by contrast, is the full authoritative snapshot meant to restore a session exactly, so it keeps sleep state.
