# Phase 2: standalone dedicated multiplayer

This records Phase 2's completion state. Later Sodium rendering work and revised limitations are
documented in [Phase 3](phase3-sodium-multicamera.md); Phase 2 did not load optional renderers.

## Progress checklist

- [x] Read the Phase 2 specification; retain the Phase 1 dismount fix.
- [x] Add a separate multiplayer test source set and disposable launch configurations.
- [x] Launch one dedicated-server JVM and two independent Minecraft client JVMs.
- [x] Record each process's exact loaded mods and reject optional renderer contamination.
- [x] Validate shared portal delivery, destination view submission and remote chunks.
- [x] Validate sequential and controlled near-simultaneous crossings and peer uniqueness.
- [x] Validate disconnect/reconnect while the other client remains functional.
- [x] Validate mounted chest-boat travel and both clients' vehicle tracking.
- [x] Complete actual standalone server restart and post-restart traversal.
- [x] Complete final resource/exit-code/log audit and record final evidence.

## Architecture and isolation

`gradle/phase2.gradle` reuses Loom's development classpaths and launch settings. The
`multiplayer` source set is separate from `gametest`; the Phase 1 suite is not loaded.
The coordinator starts three real child Java processes, each with its own working
directory, options, Minecraft state and normal TCP connection. Neither client creates
an integrated server. Files carry test commands/observations only; actual login,
movement, entity tracking and portal traffic use the ordinary Minecraft connection.
Movement holds the client's forward key; fixture positioning uses ordinary server
teleports and waits for their acknowledgement.

Every run needs a fresh `build/phase2/<run-id>` directory. Existing runs are refused,
not erased. Each run has `server`, `clientA` and `clientB` subdirectories. The server
binds loopback on an ephemeral port, never 25565. Only its generated properties have
`online-mode=false`, allowing deterministic, distinct offline UUIDs for PortalTestA
and PortalTestB. EULA acceptance is confined to that disposable server directory.
Production world, mods, configs, AutoModpack and backups are not accessed. The Java
25 JDK executables installed at `E:\MinecraftServer\bin` are used to run Gradle/game
processes; the production server itself is not started or changed.

Runtime validation uses Minecraft 26.3, Loader 0.19.5 and Fabric API 0.163.0+26.3
via a command-line override. Committed dependency versions are not raised. The
test profile excludes Sodium, Iris, Nvidium and shaders. Each process writes a
`mods-<role>-<pid>.json` manifest; forbidden renderers invalidate the run.

## Evidence and assertions

`phase2-results.json` records stages, PIDs, port, process exit codes and failure
details. `evidence-<n>.json` captures server and both clients after each successful
stage. Separate stdout/stderr files and each process's own logs remain available.
`fixture.json` records the persistent ordinary portal UUIDs. Each client asserts
that its singleplayer server is null. The server asserts dedicated mode. Both
clients must receive the same portal UUID, submit a destination view, and load
Nether chunks. Player/vehicle UUIDs must occur exactly once across relevant loaded
client worlds and server entity stores; there is no relaxed duplicate/ghost check.

Disconnect checks require empty client renderer/extractor maps, removed per-player
server loading records and zero remaining portal tickets after both clients leave.
Final server manager checks run after Immersive Portals' own cleanup listeners,
not prematurely at Fabric's SERVER_STOPPED event. This is bounded owned-resource
verification, not heap-level leak proof.

The coordinator has per-action/startup/exit deadlines and a 12-minute overall limit.
Its finally block terminates only child processes it started, with bounded waits.
Status files use atomic replacement with bounded Windows sharing-violation retries;
partially written JSON is never accepted as valid state.

## Development attempts

- `mp01`: real shared-portal and A-crossing evidence, then Windows denied a test
  status-file atomic rename while it was read. Added bounded replacement retries.
- `mp02`: sequential outward crossings passed, then the coordinator's file reader
  failed during a concurrent replacement. Read the complete file into a string,
  close the handle, then parse; bounded I/O retries preserve atomic publication.
- `mp03`: all travel/reconnect/mounted/disconnect stages passed. The shutdown
  witness ran at Fabric SERVER_STOPPED, before Immersive Portals' runServer-return
  cleanup. Moved the test observer to the mod's cleanup event after its registered
  listeners; the empty-manager assertion is unchanged. This was not a runtime leak.
- `mp04`: the cleanup observer was still registered before the runtime's listener
  because mod initializer order is not dependency order. The same assertion failed.
  Registering the observer at SERVER_STARTED ensures all runtime initializers have
  completed before it subscribes to the cleanup event.
- `mp05`: complete travel/mounted/disconnect and zero-manager shutdown checks
  passed. Restart asserted portal entities before asynchronous entity loading
  completed. The readiness check now waits up to 400 server ticks for the exact
  saved UUIDs, with both fixture chunks kept loaded. No portals are recreated on
  restart, and missing entities still fail readiness.
- `mp06`: PASS, all 17 stages; Gradle exit 0, 1 minute 29 seconds. Both clients
  and both standalone server instances exited 0. No Phase 2 runtime fix required.

No new shipped runtime change has been needed in Phase 2. The existing Phase 1
`super.removeVehicle()` portal-transfer detach fix is retained.

## Reproduction

From `E:\ImmersivePortalsMod`, in PowerShell:

```powershell
$env:JAVA_HOME='E:\MinecraftServer'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat runPhase2Multiplayer '-Pfabric_version=0.163.0+26.3' '-Pphase2_run=mp06' --console=plain
```

Choose a new run ID to repeat; the harness refuses to overwrite existing evidence.
The initial launch-only check was `preparePhase2` with `-Pphase2_run=prepare1`.
Executed scenario attempts use `mp01`, `mp02`, `mp03`, `mp04`, `mp05`, `mp06` with the same API
override. Console transcripts are in `build/phase2-inspection/<run-id>.log`.

The NVIDIA warning and separate optional-mod audit remain documented in
[nvidium-compatibility-audit.md](nvidium-compatibility-audit.md). They are not part
of this baseline, and baseline success does not approve Nvidium for production.

## Final report (A–S)

### A. Process topology

Successful run: `build/phase2/mp06`, 2026-10-19. Loopback port **63494**.
All processes used Java **25.0.4.1**, Minecraft **26.3**, Loader **0.19.5**.

| Process | PID | Exit | Working directory under mp06 |
| --- | ---: | ---: | --- |
| Standalone dedicated server, initial | 30400 | 0 | server |
| Independent Minecraft client A | 81780 | 0 | clientA |
| Independent Minecraft client B | 101716 | 0 | clientB |
| Standalone dedicated server, restarted same world | 43904 | 0 | server |

Loaded core mods: Fabric API **0.163.0+26.3**, Immersive Portals **6.0.6**,
DimLib **1.1.0-port.1+mc26.3**, Cloth Config **26.3.159**, cloth-basic-math
**0.6.1**, MidnightLib **1.9.3**, MixinExtras **0.5.5**, plus the test-only
`immersive_portals_multiplayer` **1.0.0** and Fabric's client-gametest API
**6.0.9+4be74c3f5d**. Every individual Fabric module/version is recorded in the
four `mods-<role>-<pid>.json` manifests. Both clients use distinct offline UUIDs:
A `af12dea0-90ed-36fd-8a12-10b13fa5ef8b`, B `00af4cef-0b2f-3fcb-8fe3-00b62ac48866`.

### B. Isolation

PASS. All game state is disposable. No production world/config/mod/AutoModpack/
backup/EULA files were accessed or changed; no jars installed to production.
The production directory's JDK executables were used, as described above.
Sodium, Iris, Nvidium, shaders and unrelated production mods were absent from
both baseline clients. Authentication was disabled only in generated test settings.

### C–L. Results

| Requirement | Result | Evidence |
| --- | --- | --- |
| C. Dedicated server | PASS | Independent initial and restart PIDs, dedicated-mode assertion |
| D. Client A | PASS | Real TCP login, independent PID, no integrated server |
| E. Client B | PASS | Real TCP login, independent PID, distinct UUID |
| F. Player tracking | PASS | One UUID per player across server entities/client worlds; peer observations after crossings |
| G. Sequential crossings | PASS | A outward, B outward, A return, B return; authoritative positions/orientations/acknowledgements |
| H. Near-simultaneous crossings | PASS | Both forward commands issued before awaiting either; both finish independently; no same-tick claim |
| I. Disconnect/reconnect | PASS | A loading record removed, B traverses; A reconnects with persisted UUID/dimension/position |
| J. Server restart | PASS | Clean exit; new JVM opens same world; exact saved portal UUIDs persist; both reconnect and traverse |
| K. Mounted multiplayer | PASS | Chest boat, A mounted across dimensions, B observes; one vehicle UUID on server and both clients |
| L. Cleanup | PASS | Empty client renderer/extractor maps; no players/tracking/portal tickets after disconnect; zero manager entries after final cleanup |

`phase2-results.json` contains 17 PASS stages. `evidence-1.json` through
`evidence-17.json` record stage-end observations. Intermediate mounted assertions
execute before the fixture is dismounted/discarded; the stage-end mounted snapshot
therefore shows its subsequent cleanup and A returned to the Overworld. The
scenario does not claim to retest the Phase 1 detailed inventory matrix.

Both server shutdown witnesses report `trackedPlayers=0`, `ticketManagers=0`.
All four stderr files are empty. The suspicious packet-log scan returned `[]`.
There were no invalid movement, unknown-dimension/teleport, malformed packet,
encoder/decoder, mixin, portal persistence or watchdog failures in this run.

### M. Defects and investigated warnings

No new production runtime defect was reproduced. The Phase 1 mounted-transfer
detach fix remains unchanged and passed the multiplayer regression.

Test-harness defects fixed, classified P2 (test reliability):

1. Windows briefly locked JSON evidence during replacement (`mp01`/`mp02`).
   Atomic publication now has bounded sharing-violation retries; readers close
   file handles before parsing. Covered by the complete `mp06` scenario.
2. Shutdown observation preceded cleanup (`mp03`), and the first event-based
   observer still registered before the runtime listener (`mp04`). Subscribe
   at SERVER_STARTED, after all initializers, to the runtime's final cleanup
   event. Covered by zero-manager assertions at both actual process exits.
3. Restart observation assumed terrain load also completed entity loading
   (`mp05`). Hold fixture chunks loaded and wait up to 400 ticks for saved UUIDs;
   never recreate them. Covered by real restart and both post-restart crossings.

Reviewed warnings/errors retained in logs, not hidden or altered:

- Mounted A receives one redundant chest-boat add-entity packet. The existing
  `MixinClientPacketListener.onHandleAddEntity` guard cancels replacement when
  the existing entity has passengers. The scenario separately checks the exact
  vehicle UUID occurs once in the destination on the server and both clients,
  and A remains mounted/connected. No duplicate vehicle or corrupt tracking was
  observed. This is a handled packet, not a claim of zero redundant traffic.
- New disposable flat-world generation logs `No key layers in MapLike[{}]`
  while parsing the empty custom generator settings. Startup proceeds; fixture
  floor assertions, actual save/reopen and traversals
  pass. The reopened world does not repeat this generation error.
- Fresh clients log invalid default anisotropic filtering value 0 and a missing
  `minecraft:end_of_frame` post effect. Both continue and submit destination
  views successfully. No renderer workaround was introduced for these logs.
- Offline profile lookup/authentication warnings are consistent with the
  intentionally unauthenticated loopback test. Prototype renderer limitations
  and the low-memory recommendation are existing informational limitations.

### N. Nvidium observation

Separate prior audit: the Immersive Portals warning identifies NVIDIA hardware,
not Nvidium detection. Current Nvidium `0.4.4-beta7-26.3` requires Sodium exactly
`0.9.2`; the fork's default `0.9.3-alpha.1` does not satisfy that requirement.
The matching pair passed bounded prior core/lifecycle checks, but same-dimension
views remain unsupported with Sodium. No production safety approval is inferred.
Neither renderer mod participated in Phase 2. See the separate compatibility audit.

### O. Exact validation commands

PowerShell environment: `JAVA_HOME=E:\MinecraftServer`, its `bin` prepended to PATH.
Working directory: `E:\ImmersivePortalsMod`. Relevant commands, with output captured
to `build/phase2-inspection/<run-id>.log`:

```text
.\gradlew.bat preparePhase2 -Pfabric_version=0.163.0+26.3 -Pphase2_run=prepare1 --console=plain
.\gradlew.bat runPhase2Multiplayer -Pfabric_version=0.163.0+26.3 -Pphase2_run=mp01 --console=plain
.\gradlew.bat runPhase2Multiplayer -Pfabric_version=0.163.0+26.3 -Pphase2_run=mp02 --console=plain
.\gradlew.bat runPhase2Multiplayer -Pfabric_version=0.163.0+26.3 -Pphase2_run=mp03 --console=plain
.\gradlew.bat runPhase2Multiplayer -Pfabric_version=0.163.0+26.3 -Pphase2_run=mp04 --console=plain
.\gradlew.bat runPhase2Multiplayer -Pfabric_version=0.163.0+26.3 -Pphase2_run=mp05 --console=plain
.\gradlew.bat runPhase2Multiplayer -Pfabric_version=0.163.0+26.3 -Pphase2_run=mp06 --console=plain
```

`git diff --check` also passed (a pre-existing LF/CRLF advisory is informational).
Each orchestration invokes compilation and resource processing before launching.
No `clean` or production launch command was used.

### P. Files changed in Phase 2

- `build.gradle`: apply the new test-only Gradle script.
- `gradle/phase2.gradle`: isolated source set, configurations, launch argfiles/task.
- `gradle/phase2-coordinator.groovy`: bounded three-process scenario and evidence.
- `src/multiplayer/java/qouteall/imm_ptl/multiplayer/Protocol.java`: isolated JSON protocol/profile audit.
- `src/multiplayer/java/qouteall/imm_ptl/multiplayer/MultiplayerServerDriver.java`: server fixtures/observations.
- `src/multiplayer/java/qouteall/imm_ptl/multiplayer/MultiplayerClientTest.java`: real client automation/observations.
- `src/multiplayer/java/qouteall/imm_ptl/multiplayer/mixin/ChunkTrackingAccessor.java` and
  `TicketStorageAccessor.java`: read-only test access to owned state.
- `src/multiplayer/resources/fabric.mod.json` and `multiplayer-tests.mixins.json`:
  test-only metadata/entrypoints/mixins.
- `docs/phase2-multiprocess.md`: progress checklist and report.

Generated artifacts are confined to `build`, including disposable worlds/logs,
launch plans and evidence. Earlier Phase 1 changes in the working tree are retained;
they are not new Phase 2 edits. No shipped runtime, dependency-version or production
configuration change was made in Phase 2. Nothing was committed or deployed.

### Q. Deliberately not rerun

Phase 1 full inventory/persistence matrix, Vulkan, Sodium, Iris/shader packs,
Nvidium, nine-camera comparisons and the full visual suite. No runtime change
required their repetition. No production-world or full production-pack test.

### R. Remaining risks

This proves the bounded two-client development-runtime scenario on this Windows/
OpenGL machine. It does not prove long-session stability, high player counts,
heap-level absence of leaks, authenticated account services, packaged full-modpack
compatibility or optional renderer safety. Existing one-layer prototype/mirror/
same-dimension Sodium limitations remain. Near-simultaneous means independently
issued commands in a short controlled interval, not guaranteed identical ticks.

### S. Exactly one recommended next phase

**Phase 3: Sodium same-dimension portal view support on isolated clients.**
Keep its compatibility/rendering work separate from this clean baseline. Phase 3
has not been started.
