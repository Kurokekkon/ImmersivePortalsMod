# Phase 3: Sodium multi-camera portal views

## Progress

- [x] Audit suppression, its original commit and exact installed Sodium bytecode.
- [x] Reproduce missing same-dimension destination view before runtime changes.
- [x] Implement bounded sequential camera rendering and main-state restoration.
- [x] Pixel/structural tests for two same-world views, order, transforms and cleanup.
- [x] Primary Sodium OpenGL, consolidated Vulkan, Sodium 0.9.2, vanilla regression.
- [x] Relevant lightweight Iris sanity and final report A–P.

## Audit and reproduction

Commit `b2288199` introduced the one-camera-per-renderer guard. The installed
Sodium 0.9.3-alpha.1 jar was inspected/decompiled into `build/phase3-inspection`.
`LevelExtractorMixin` calls `SodiumWorldRenderer.setupTerrain` during extraction.
It changes the shared RenderSectionManager's camera, asynchronous cull results,
region-owned render lists and cached batches. UniformBufferManager also has a
one-update-per-preparation flag. Extracting all portal views before drawing the
main world would therefore overwrite the main camera's terrain, and extracting
two destinations before drawing either would leave the last camera's state.

`PortalViewRenderer.extractViews` explicitly skips the player's own dimension
and repeat destination dimensions when `supportsSeveralCamerasPerRenderer` is
false. Targets, lightmaps, camera render state and fog already belong to each
cached View; terrain state does not. Vanilla's shared extractor/render state
pointers are restored with finally blocks. ClientWorldLoader selects each
dimension's existing renderer and extractor; chunk notifications belong to that
world, so adding independent terrain renderers would duplicate meshes and
complicate updates unnecessarily.

Before changing runtime code, the existing core test was changed to require the
same-dimension view. Sodium 0.9.3-alpha.1 OpenGL reproduced the exact failure:
`the same-dimension portal destination view was not drawn (Phase 3 regression)`.
The earlier cross-dimension gold-wall pixel checks and traversal passed first;
the same-dimension portal entity had arrived before the failing assertion.
Evidence: `build/phase3-inspection/pre-fix.log`, failed after 39 seconds.

## Chosen architecture

For Sodium, queue visible views during normal extraction; after the main world
has actually drawn, extract and draw each portal consecutively. Sodium's own
setup rebuilds camera-dependent visibility and prepares its draw buffers for each
camera. Sodium terrain uniform storage appends a distinct slice for each camera,
and rotates only once per actual frame. Each cached view owns its cloud geometry
and GPU buffers, sharing only immutable cloud texture resource data. Rotating the
shared renderer per view is unsafe: the two-view test reproduced a same-submit
fence wait when its uniform ring wrapped. That first error caused a subsequent
PreparedFrame failure; fixing the lifetime resolved both without changing the GPU
backend or synchronization safeguards.
No renderer, mesh cache or worker pool is allocated per portal or frame.
Restore the main Sodium terrain camera/visibility through its setup API in a
finally block, restore its matrix context, and restore shared dispatchers and GPU
state. The vanilla extraction path remains eager. At most two views are submitted
per frame, using existing cached targets and expiry/cleanup.

The old guard is only relaxed once this alternate scheduling is active, rather
than allowing several eager extractions to corrupt shared terrain state. No
common/server/networking, teleport or ticket lifecycle changes are planned.

## OpenGL implementation validation

Sodium 0.9.3-alpha.1: `opengl-verified.log`, PASS (51 seconds). Core cross-world
gold-wall pixel checks at normal and near-plane positions, crossing and return;
same-world emerald pixels at normal and near-plane positions; two remote portals
256/512 blocks away; reversed submission order; shared destination with distinct
camera poses; asymmetric emerald/gold wall rolled 90 degrees; rolled traversal;
main-world purple marker, renderer state and Sodium camera/cull-matrix restoration;
remote chunk and target cleanup all passed.

Two-view pixels: A=(20,158,57), B=(26,54,108), identical under reversed order.
Shared-destination B=(204,173,52), proving distinct camera views of the same room.
Two views, two targets, 219 loaded world chunks; after removal, zero retained
views/targets and both remote chunk locations released, 121 local chunks remaining.

The rotated traversal regression reproduced yaw 0 instead of 180 twice even after
waiting for movement synchronization. The old client Euler extraction assumes no
roll. `CameraAngles.fromRotation` derives yaw/pitch from the transformed forward
direction and preserves yaw at a vertical pole; only the client TransformationManager
uses the new helper. Server/common quaternion math, teleport validation, packets and
authority are unchanged. Six targeted unit tests passed, and actual rolled traversal
now has matching server/client yaw -180. No Phase 2 rerun is required for these
client-only changes.

Consolidated Vulkan primary run: PASS, 64 seconds (`vulkan-primary.log`), same
pixel results, both travel paths and cleanup. Log explicitly reports Vulkan on
NVIDIA driver 617.42. No GPU backend workaround or fence/validation bypass.

Focused Sodium 0.9.2 OpenGL compatibility: PASS, 54 seconds
(`opengl-sodium092.log`), same core and multi-camera assertions, no compatibility
shim or dependency-version edit. Nvidium was absent. This preserves the Sodium
version prerequisite for a later Nvidium audit; it does not certify Nvidium's
own multi-camera state.

## Additional vanilla limitation investigation

The required vanilla core single same-world/cross-world checks passed, but the
extra far-away two-view scene showed sky at A instead of its wall. This was
reproduced against an **unmodified runtime from HEAD c5a83c77** in
`build/phase3-baseline`, created with `git archive`. Only a test fixture and its
entrypoint metadata were added to that disposable copy. The same scene, with a
longer 200-tick compilation wait, failed with exactly the same RGB (176,205,255).
Evidence: `vanilla-baseline-far.log`; current run: `opengl-vanilla.log`.

Vanilla's renderer repositions its bounded ViewArea while drawing each camera,
unlike Sodium's section map. Far-separated same-world views remain an existing
vanilla prototype limitation, not introduced by this patch. It is deliberately
not solved by broadening the Sodium phase into vanilla renderer redevelopment.
The final vanilla regression uses the requested core checks and one nine-pose
comparison run; the extra far-view failure is retained and disclosed.

## Final report A–P

### A. Root cause

Sodium keeps visibility trees/lists, region draw batches and per-camera terrain uniforms on
one world renderer. The initial port extracted all portal cameras before drawing, which
would overwrite pending main/portal terrain state. Commit b2288199 therefore suppressed
same-world and repeat-destination views. This phase changes scheduling and buffer ownership,
rather than just deleting that guard.

### B. Architecture

Sodium: draw main world, then extract/draw each queued view immediately, then restore main
terrain camera, frustum/cull matrix and renderer matrices. Existing chunk meshes are shared.
Each view has its own existing camera/render state/fog/lightmap/target and now cloud renderer,
with immutable cloud texture data shared. Terrain uniform storage appends slices and rotates
once per actual frame. Remote world renderers also finish once per frame. Java/GPU state is
restored in finally blocks, including setup/lightmap failure paths. Vanilla retains eager
extraction. No legacy stencil, recursion, mirrors or new world-renderer instances are enabled.

Shader packs retain the original one-camera guard: their per-camera temporal histories are
outside this phase. Iris without a shader pack receives only a lightweight sanity check.

### C. Exact Phase 3 file changes

| File | Purpose |
| --- | --- |
| `build.gradle` | Focused phase3/combined/vanilla/accuracy selectors, including lists of test entrypoints |
| `src/main/java/qouteall/imm_ptl/core/compat/sodium_compatibility/SodiumInterface.java` | Sequential scheduling/restoration contract |
| `src/main/java/qouteall/imm_ptl/core/compat/sodium_compatibility/SodiumInterfaceOnPresent.java` | Enable multi-camera scheduling without shaders; restore main terrain state; rotate remote uniform stores once per frame |
| `src/main/java/qouteall/imm_ptl/core/portal_view/PortalViewRenderer.java` | Deferred Sodium extraction, per-view cloud ownership, finally restoration and resource counters |
| `src/main/java/qouteall/imm_ptl/core/portal_view/CameraAngles.java` | New client-only roll-safe look-angle extraction |
| `src/main/java/qouteall/imm_ptl/core/render/TransformationManager.java` | Use the roll-safe client angle conversion |
| `src/main/java/qouteall/imm_ptl/core/mixin/client/portal_view/LevelRendererAccessor.java` | Swap/restore the view-owned cloud renderer |
| `src/main/java/qouteall/imm_ptl/core/mixin/client/portal_view/CloudRendererAccessor.java` | New access to immutable cloud texture data |
| `src/main/resources/imm_ptl.mixins.json` | Register the new vanilla cloud accessor |
| `src/gametest/java/qouteall/imm_ptl/gametest/ClientPortalTests.java` | Require same-world view pixels, including near-plane camera |
| `src/gametest/java/qouteall/imm_ptl/gametest/SodiumMulticameraTests.java` | New two-view/order/shared-destination/roll/traversal/state/resource regression |
| `src/gametest/java/qouteall/imm_ptl/gametest/mixin/PortalViewOrderMixin.java` | Test-only reversed submission order |
| `src/gametest/resources/fabric.mod.json` | Register new test entrypoint |
| `src/gametest/resources/immersive_portals_gametest.mixins.json` | Register test-only order perturbation |
| `src/test/java/qouteall/imm_ptl/core/portal_view/CameraAnglesTest.java` | Six roll, unrolled roundtrip and vertical-pole checks |
| `docs/26.3-port-progress.md` | Replace obsolete Sodium limitation with tested implementation/status |
| `docs/nvidium-compatibility-audit.md` | Mark pre-Phase-3 findings historical; retain untested Nvidium warning |
| `docs/phase2-multiprocess.md` | Link historical Phase 2 completion state to current rendering status |
| `docs/phase3-sodium-multicamera.md` | Audit, progress checklist, findings and final report |

Earlier Phase 1/2 working-tree edits remain intact. No new server/common/network changes,
committed dependency version changes, publishing configuration changes or production installs.
Generated evidence, archived baseline and jars remain under `build`.

### D. Pre-fix reproduction

`pre-fix.log`: expected FAIL at the positive same-world view assertion, with Sodium
0.9.3-alpha.1. Portal entity delivery succeeded and the old suppression warning appeared.
Cross-world pixels/traversal passed before that assertion. Runtime code was unchanged when
this reproduction ran. The failed test was retained; no negative suppression expectation
remains in the core test.

### E–I. Validation results

| Requirement | Status | Evidence |
| --- | --- | --- |
| E. Sodium 0.9.3-alpha.1 same-world and cross-world destination views | PASS | Positive view assertions and emerald/gold pixels, normal and near plane |
| Two cameras sharing the same renderer, different destinations | PASS | A emerald, B lapis, 256/512 blocks away |
| Reversed view order | PASS | Identical sampled colors, both views drawn |
| Same destination area, distinct transformed poses | PASS | A emerald and B gold from the same room |
| Rotated view and traversal orientation | PASS | 90-degree roll maps asymmetric markers vertically; server/client yaw -180 after travel |
| Main camera/render state/cull matrix | PASS | Structural assertions plus purple main-world marker |
| Remote chunk visibility and cleanup | PASS | Visible marker chunks, bounded counts, both remote locations unloaded |
| F. OpenGL | PASS | `opengl-verified.log` |
| G. Vulkan | PASS | `vulkan-primary.log`, one consolidated run |
| H. Sodium 0.9.2 | PASS | `opengl-sodium092.log`, same implementation |
| I. Required vanilla same-world/cross-world regression | PASS | `vanilla-final-accuracy.log` |
| Vanilla nine-camera comparison | PASS | Nine poses, reported bad fractions 0.0000; means 0.00–0.27/255 |
| Iris 1.11.6 without shaders, lightweight core sanity | PASS | `iris-startup-sanity.log`; full shaders not certified |

Environment: Java 25.0.4.1, Minecraft 26.3, Loader 0.19.5, Fabric API 0.163.0+26.3,
RTX 3060, NVIDIA 617.42, Windows. Primary runtime logs identify Sodium
0.9.3-alpha.1+mc26.3; the compatibility log identifies 0.9.2+mc26.3. Nvidium absent.
The required successful process runs exited 0; expected reproductions exited 1.
`phase3-results.json` records these separately, including the existing vanilla far-view failure.

The extra vanilla far-separated two-view scene is **not supported**. It failed identically
on the unmodified HEAD runtime and this patch; it is not counted as a passing test. This
does not invalidate the Sodium multi-camera result or the requested vanilla core regression.

### J. Performance and resources

Two active views/targets; 219 loaded world chunks in the two-destination scene, then 121
local chunks after removal. Both far locations disappear from the client chunk map; all
cached view targets expire and disconnect clears client worlds. Existing MAX_VIEWS=2 and
200-frame expiry remain. Cloud resources are allocated per cached view, never per frame,
and close on expiry/disconnect. No duplicated world renderer, mesh pool or worker pool.
This is bounded scenario evidence, not a stress test, heap leak proof or frame-time benchmark.
The existing cache is expiry-bounded, not a new hard GPU-memory budget across many portal UUIDs.

### K. Defects found

1. **P1, shared Sodium camera state:** the pre-fix suppression hid unsafe eager reuse.
   Fixed with sequential extraction/drawing and main-state restoration; covered by two
   destinations, shared destination, reverse order, positive pixels and structural checks.
2. **P1, same-submit GPU fence wait:** first two-view implementation rotated the shared
   uniform ring per camera; view B attempted to wait on the current submission. Fixed by
   append-only per-camera terrain uniform slices, once-per-frame rotation and view-owned
   cloud buffers. `multicamera-1.log` preserves the first root cause and subsequent cascade;
   final OpenGL/Vulkan tests pass without bypassing any fence or validation.
3. **P1, rolled traversal yaw reversal:** reproduced twice, including after waiting for
   authoritative movement. The old inverse Euler conversion assumes no roll. Client-only
   forward-vector conversion fixes it; six unit tests and actual rolled traversal cover it.
4. **Existing vanilla far-view limitation:** same sky-color failure on current runtime and
   untouched HEAD after a longer wait. No general vanilla renderer redesign attempted.

No final-run renderer-state, missing-section, concurrent modification, framebuffer, Sodium
state, GPU fence or client crash errors were observed. Logs still contain existing invalid
default anisotropic-filtering values and offline/authentication/profile lookup warnings;
these did not block rendering. The failed exploratory runs remain available for inspection.

### L. Exact test commands

PowerShell from `E:\ImmersivePortalsMod`, with `JAVA_HOME=E:\MinecraftServer` and its `bin`
prepended to PATH. Commands below use the project wrapper and the runtime-only Fabric API
override. No `clean` was run. Log files are in `build/phase3-inspection`.

```text
.\gradlew.bat runClientGametest -Pfabric_version=0.163.0+26.3 -Pclient_test=core -Penable_sodium=true --console=plain
# Above: pre-fix.log, then sequential-1.log before the larger regression existed.
.\gradlew.bat runClientGametest -Pfabric_version=0.163.0+26.3 -Pclient_test=phase3 -Penable_sodium=true --console=plain
# Above: multicamera-1.log and multicamera-2.log during diagnosis.
.\gradlew.bat runClientGametest -Pfabric_version=0.163.0+26.3 -Pclient_test=phase3_all -Penable_sodium=true --console=plain
# Above: opengl-primary.log (roll failure), opengl-verified.log (PASS), vulkan-primary.log (PASS).
.\gradlew.bat test --tests *CameraAnglesTest runClientGametest -Pfabric_version=0.163.0+26.3 -Pclient_test=phase3_all -Penable_sodium=true --console=plain
# opengl-final.log: six unit tests and first complete passing combined run.
.\gradlew.bat runClientGametest -Pfabric_version=0.163.0+26.3 -Pclient_test=phase3_all -Penable_sodium=true -Psodium_path=maven.modrinth:sodium:mc26.3-0.9.2-fabric --console=plain
.\gradlew.bat runClientGametest -Pfabric_version=0.163.0+26.3 -Pclient_test=phase3_all -Penable_sodium=false --console=plain
# Above: extra vanilla far-scene investigation, not the required final regression.
git archive --format=zip --output=build/phase3-inspection/baseline.zip HEAD
# Expanded only into build/phase3-baseline; added baseline-only fixture/entrypoint.
.\gradlew.bat -p build/phase3-baseline runClientGametest -Pfabric_version=0.163.0+26.3 -Penable_sodium=false --console=plain
.\gradlew.bat assemble test --tests *portal_view.* runClientGametest -Pfabric_version=0.163.0+26.3 -Pclient_test=phase3_vanilla -Penable_sodium=false --console=plain
.\gradlew.bat assemble runClientGametest -Pfabric_version=0.163.0+26.3 -Pclient_test=core -Penable_sodium=true -Penable_iris=true --console=plain
git diff --check
```

Vulkan was selected only in disposable `build/clientgametest/options.txt` via
`preferredGraphicsBackend:"vulkan"`, then returned to `"default"` for OpenGL. The initial
options were backed up in the inspection directory. Iris sanity temporarily set
`enableShaders=false` in that disposable profile; its original file/absence was restored.
No extra shader pack was installed. Relevant PNGs are archived per profile under the
inspection directory. JUnit XML records 18 relevant tests, zero failures/errors.
The final assembled jar is `build/libs/immersive-portals-6.0.6-mc26.3-fabric.jar`;
inspection confirmed no test/multiplayer-driver classes were packaged.

### M. Deliberately not run

Full Phase 1 dedicated/inventory matrix, full Phase 2 two-client lifecycle, shader-pack
certification, Nvidium tests, recursion/mirrors, full Vulkan permutations, broad camera
matrices on every backend, full production pack and production world. One consolidated
Vulkan set and one vanilla nine-pose run were sufficient; no server/network code changed.

### N. Remaining limitations

One portal layer, at most two views per frame, no mirrors/recursion. Existing vanilla
far-separated same-world views remain unsupported. Active shader packs retain their
single-camera guard; shader temporal histories, long sessions, dense portal populations
and Nvidium camera-state compatibility remain unverified. Normal and 0.03-block near-plane
pixels plus actual continuous crossings are tested; no separate screenshot certification
is claimed for every partially crossed pose or rotation/gravity combination.
Sodium 0.9.2 passing preserves the dependency prerequisite for Nvidium, not a production
approval of that combination. The committed default Sodium/API versions are unchanged.

### O. Production safety

Production server, world, mods, configs, AutoModpack, backups, EULA and port were untouched.
No development jar was installed there. Only the Java 25 installation under
`E:\MinecraftServer\bin` was used to execute tools; all game state was disposable under
the repository's build tree. Nothing was committed, published or deployed.

### P. Exactly one recommended Phase 4

**Focused Sodium 0.9.2 + Nvidium multi-camera compatibility on isolated clients.**
Validate Nvidium's own camera/visibility state before considering the combination for
production. Phase 4 has not been started.
