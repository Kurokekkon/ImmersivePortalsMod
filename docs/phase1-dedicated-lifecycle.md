# Phase 1: dedicated lifecycle validation

Baseline: branch `26.3`, commit `c5a83c77cb493b4fbab2663a6d4f0de004895ea8`.
Production `E:\MinecraftServer` is never a test run directory. No deployment is part of this phase.

## Progress

- [x] Inspect clean checkout and Fabric's existing dedicated test harness.
- [x] Add explicit PASS / FAIL / SKIPPED JUnit XML and log reporting for conditional client tests.
- [x] Add a narrow client test selector; preserve default full-suite entrypoints.
- [x] Implement real-network bidirectional traversal, reconnect and save/stop/reopen scenarios.
- [x] Implement ordinary/global portal transform checks after actual restart.
- [x] Implement real-client chest-boat and player inventory/identity assertions.
- [x] Observe actual portal-type tickets through a test-only accessor.
- [x] Complete dedicated runtime validation against Fabric API `0.163.0+26.3`.
- [x] Validate optional-test skip reporting.
- [x] Run relevant unit tests and headless GameTests once as final consolidated checks.
- [x] Review final diff and record evidence/remaining limitations.

## Commands

Use Java 25 and the wrapper. Committed Fabric API stays `0.161.0+26.3`.

```text
gradlew.bat runClientGametest -Pfabric_version=0.163.0+26.3 -Pclient_test=dedicated --console=plain
gradlew.bat runClientGametest -Pfabric_version=0.163.0+26.3 -Pclient_test=mounted --console=plain
gradlew.bat runClientGametest -Pfabric_version=0.163.0+26.3 -Pclient_test=iris --console=plain
gradlew.bat test --tests '*TeleportRequestCheckTest' --tests '*ImplRemoteProcedureCallTest' --tests '*RemoteCallRegistrationTest' runGametest -Pfabric_version=0.163.0+26.3 --console=plain
gradlew.bat assemble validateAccessWidener runClientGametest -Pfabric_version=0.163.0+26.3 -Pclient_test=core --console=plain
gradlew.bat processGametestResources -Pfabric_version=0.163.0+26.3 --console=plain
```

`client_test` accepts `all` (default), `dedicated`, `mounted`, `core`, or `iris`. It filters only the
generated test-mod manifest, preserving Fabric's per-entrypoint setup/cleanup. Tests remain
outside the shipped mod. Selecting an unavailable dedicated/Iris scenario fails the Gradle
invocation while its JUnit result explicitly says SKIPPED; a green task cannot certify it.
Reports are in `build/clientgametest/test-results/`. Unreached scenarios are SKIPPED.

The dedicated test needs accepted EULA state at `build/clientgametest/eula.txt`, as required
by Fabric's `TestDedicatedServerContext` API. The user explicitly authorized that test-local
file in this session. No production EULA is read or changed, and no build task accepts the
EULA automatically.

## Scope and safety

The test verifies that its client run directory is `build/clientgametest`, creates a unique
previously absent `phase1-world-<UUID>` below it and binds the server to loopback on a temporary
port. Existing worlds are never reused for initial creation. Test worlds are retained for
diagnosis rather than deleted after failure.

Fabric's `worldBuilder().createServer()` deletes its target world. It is called only for the
initial unique world. To restart the SAME saved world, a narrow test-only adapter uses Fabric's
existing `DedicatedServerImplUtil.start` and `TestDedicatedServerContextImpl`. This coupling
to Fabric implementation classes is isolated to the test and must be reviewed on API upgrades.

Two real clients are SKIPPED: this harness drives the one Minecraft client singleton in its
JVM and defaults to `max-players=1`. Mock players would bypass the tracking path. No second
client framework is introduced.

Ticket checks filter the actual ticket storage by `ImmPtlChunkTickets.TICKET_TYPE`; they do
not expect vanilla or forced tickets to disappear. Cleanup waits use the existing tracking
update/purge constants, plus bounded asynchronous delivery time.

## Evidence

All validation used Fabric API `0.163.0+26.3`, Java 25, Loader 0.19.5 and Minecraft 26.3.
The committed API remains `0.161.0+26.3`.

| Check | Result | Evidence under `build/phase1-inspection/` |
| --- | --- | --- |
| Dedicated startup, TCP login, portal creation/view submission and both travel directions | PASS | `final-dedicated.log` |
| Normal disconnect, reconnect to the running server, identity/position and remote-world reinitialization | PASS | `final-dedicated.log` |
| Actual save/stop/reopen of the same world; ordinary and global portal UUID/dimension/axis/rotation/scale preservation | PASS | `final-dedicated.log` |
| Bidirectional travel after restart | PASS | `final-dedicated.log` |
| Mounted chest boat, entity uniqueness, no source entity, vehicle contents and server/client player inventory | PASS | `mounted-fix-1.log`, `final-dedicated.log` |
| Portal-type tickets, manager bookkeeping, visible-dimension loader and remote chunks released after deletion | PASS | `final-dedicated.log` |
| Client world/renderer/extractor cleanup on disconnect, ticket-manager cleanup after stop | PASS | `final-dedicated.log` |
| Two real clients / cross-player tracking / sequential two-player crossings | SKIPPED | Harness limitation described above |
| Networking/teleport/RPC unit tests | PASS: 14 tests | `final-unit-gametest.log` |
| Headless GameTests including mounted correction regression | PASS: 13 (12 project + Minecraft always-pass) | `final-unit-gametest.log` |
| Normal OpenGL core portal sanity, packaging and class-tweaker validation | PASS | `final-core-client.log` |
| Missing Iris prerequisite | SKIPPED: 2 cases, 0 passes; selected invocation correctly exits 1 | `skip-reporting.log` |
| Default client selection restored | PASS: all 5 original entrypoints | `default-selection.log` |

The dedicated JUnit XML at `build/clientgametest/test-results/dedicated-lifecycle.xml`
contains 16 passed scenario checks plus one passed suite aggregate, and one skipped two-client
scenario. The optional report `iris.xml` contains only skipped cases, not false passes.

## Defect reproduced and fixed

**P2: conditional tests silently looked like passes.** Dedicated and Iris entrypoints previously
logged missing prerequisites and returned normally. They now write explicit JUnit skipped cases;
selecting one as the required scenario exits nonzero if unavailable. The missing-Iris probe
confirmed 2 skipped cases, 0 passes and Gradle exit 1. Default optional behavior does not make
Iris mandatory, and missing external shader packs are reported separately from a tested minimal pack.

**P1: mounted cross-dimension travel disconnected a real client.** The full lifecycle run
and two focused mounted reproductions showed an obsolete source-world position correction,
forced client world reversals and then `Invalid move player packet received`. All reported
coordinates/angles were finite; movement/teleport validation must not be relaxed.

Root cause: `ip_stopRidingWithoutTeleportRequest()` called `super.stopRiding()`. That enters
`LivingEntity.stopRiding()` and its normal dismount placement, which requests a teleport while
the portal transfer is moving the mounted player. Handling that correction can cause another
dimension change/correction inside the acknowledgement handler, invalidating its pending state.

The runtime fix changes only that helper to `super.removeVehicle()`: detach the rider without
normal dismount placement. Normal user dismount behavior stays on its existing path. The real
mounted regression and a new headless assertion that transfer does not leave an unintended
awaiting teleport protect this fix. No packet validation, distance limits or save behavior changed.

Initial traversal failures were a **fixture issue**, not this runtime defect: Nether fill
commands had failed with `That position is not loaded`. The fixture now loads the required
chunks and asserts the prepared floor/air before testing. Input is released in `finally` on
traversal or failure. Temporary packet instrumentation was removed after diagnosis.

## Remaining limits / next phase

- The dedicated server is a real `DedicatedServer` with a TCP connection in Fabric's client-test
  JVM. This is not a packaged standalone-server/full-modpack certification. Headless tests also
  exercise the server environment separately.
- Two-real-player tracking remains unverified. The next phase should be exactly that focused
  multiplayer scenario on a disposable server, using two genuine clients; do not start it here.
- Vehicle inventory evidence covers one chest boat, one rider and known vanilla contents, not
  all modded vehicles or arbitrary multi-passenger configurations.
- Runtime-created dimensions' known persistence gap remains outside this phase. The persistence
  checks here use the normally registered Overworld and Nether.
- No GPU/memory-profiler claim is made. Tests assert owned lifecycle state, ticket release and
  remote chunk cleanup, rather than proving zero leaks under stress.
- Vulkan, Sodium, Iris shader packs and the nine-pose accuracy suite were deliberately not rerun:
  the runtime fix affects server dismount handling, not those rendering integrations.

Only `MixinServerPlayer.ip_stopRidingWithoutTeleportRequest` changed in shipped runtime code.
Other changes are test selection, test cases/reporting, a test-only ticket accessor and this
checklist. No production files, dependencies, publishing setup or attribution were changed.

Subsequent requested compatibility checks are recorded separately in
[the NVIDIA/Sodium/Nvidium audit](nvidium-compatibility-audit.md). They do not change the
vanilla-renderer Phase 1 evidence or certify same-dimension Sodium views.
