# Phase 4: Nvidium compatibility

## Progress checklist

- [x] Read the specification and preserve the Phase 1–3 working tree.
- [x] Audit exact Nvidium jar hooks and Phase 3 intersections before runtime edits.
- [x] Create a new isolated OpenGL client profile with the exact required versions.
- [x] Complete unchanged-runtime deterministic baseline with activation evidence (FAIL, reproduced).
- [x] Apply minimal Nvidium-specific compatibility fix and pass deterministic checks.
- [x] Complete bounded three-minute stress and post-stress cleanup.
- [x] Review native/Java/GPU evidence, manifests and resource observations.
- [x] Update compatibility documentation and final report A–S; no deployment.

## Static audit

Baseline HEAD: `c5a83c77cb493b4fbab2663a6d4f0de004895ea8` plus the existing Phase 1–3
working-tree fixes. Runtime source hashes were recorded before Phase 4 in
`build/phase4-inspection/baseline-runtime-sha256.json`. The first two baseline runs
used unchanged Phase 3 runtime code; the subsequently required fix is below.

The exact Nvidium `0.4.4-beta7-26.3` jar (embedded commit `27ceb72`) was decompiled
with the already available CFR tool into `build/phase4-inspection/nvidium`.
Its metadata requires Sodium 0.9.2. The jar and dependency metadata were not patched.

- Nvidium's `MixinSodiumWorldRenderer` records the viewport at every `setupTerrain`.
  Its `renderLayer` hook cancels Sodium drawing, uses its own solid/translucent path
  and raw OpenGL state, and skips Sodium's cutout pass. Thus Phase 3's immediate
  extract/draw pairing supplies the appropriate viewport before every portal draw.
- Each Sodium RenderSectionManager owns a NvidiumWorldRenderer. Its pipeline owns
  scene uniforms, section/region visibility, draw commands, frameId and temporal
  visibility history. These persist between cameras, requiring pixel/order tests;
  the static audit alone does not establish correctness.
- Nvidium intercepts build-result upload into its own section/region storage, and
  section removal deletes corresponding Nvidium data with the default keep-distance
  setting of 32. Its destroy hook deletes the pipeline, upload/download streams and
  terrain arena. No custom cache settings are used by the isolated profile.
- Nvidium supplies per-draw projection/model-view/camera uniforms and per-viewport
  region culling. Its upload/download streams and direct OpenGL dispatches are
  separate from Sodium's dynamic uniform storage fixed in Phase 3. No speculative
  changes to their lifetime, fences or shaders were made.
- Nvidium checks OpenGL mesh-shader/bindless/sparse-buffer capabilities; global
  IS_ENABLED can also change when renderer state is initialized. Tests therefore
  require the enabled flag, live per-world renderer, nonempty regions, increasing
  actual terrain frame counter and correct pixels, not just jar presence.
- Immersive Portals' NVIDIA hardware warning is conditional on NVIDIA hardware
  **and Sodium being absent**. It does not detect Nvidium. That warning code is
  unchanged, and no driver/system settings are altered.

## Isolation and commands

Only `build/phase4/client/mods` receives the cached Nvidium jar. Loom launches the
current development mod, Fabric API 0.163.0+26.3 and Sodium 0.9.2 there. No Iris,
shader packs or production mods/configs/worlds are copied. Integrated test worlds
are generated disposable state; production ports/EULA/backups are not used.

The test-only run-directory property preserves all previous defaults. From the
repository root, using the installed Java 25 executables:

```powershell
$env:JAVA_HOME='E:\MinecraftServer'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat runClientGametest '-Pfabric_version=0.163.0+26.3' '-Pclient_test=phase4' '-Pclient_run_dir=build/phase4/client' '-Penable_sodium=true' '-Psodium_path=maven.modrinth:sodium:mc26.3-0.9.2-fabric' --console=plain
```

Initial console evidence: `build/phase4-inspection/baseline.log`; exact loaded mods,
GPU/driver, per-world renderer identities, frame counters, region/chunk counts and
Nvidium debug timing/memory metrics are in `build/phase4/client/phase4-evidence`.

## Reproduced incompatibility and fix

Both `baseline.log` and `baseline-repro.log` fail the first distant same-world view
with Nvidium actually active. The second run waits longer; the marker still samples
sky RGB (176,205,255). The screenshot also shows missing main-world wall geometry.
The first activation witnesses show a live renderer, 12 regions and terrain-frame
counter advancing from 868 to 986. The initial manifest/screenshots are preserved
under `baseline-evidence`.

Root cause: Nvidium first draws the previous render invocation's terrain command
list, then computes current visibility, and its temporal pass draws only newly
revealed sections (current visibility bit 1, prior visibility bit 0). Alternating
main/portal cameras shares that history, so one view's visibility suppresses geometry
in another. This is specific to Nvidium's active pipeline, not the previously known
vanilla ViewArea limitation.

`MixinNvidiumRenderPipeline` gives a cold history to each portal draw and the first
main draw after it. It sets the previous region count to zero and clears the existing
section-history buffer with ordered OpenGL buffer/shader-storage barriers. Nvidium's
own current-frame temporal pass then draws all freshly visible sections. That pass
is enabled locally for a fresh camera even if temporal coherence was configured off;
the configuration object/file is never modified. Normal main-only rendering resumes
Nvidium's original history behavior automatically. No mesh renderer is replaced,
Nvidium remains active, no GPU buffers are allocated per view, and no fence/validation
is bypassed.

The adapter is an optional client-only mixin, loaded only with Nvidium present. The
compile-only dependency pins exact Modrinth version ID `8mVK1zbk`; it is not bundled
or added to ordinary runtime profiles. Its SHA256 matches the tested runtime jar:
`C106EFCF40ECD08A7EA9851D9AE52601C41393FDB034E2156D71D2A6C4A2C3BF`.

`fix-3.log`: PASS, 51 seconds. Single distant view, two destinations, reverse order,
shared destination poses, cross-world pixels and return traversal, roll, 0.03-block
near-plane view, rolled traversal, camera restoration, and cleanup all passed with
Nvidium active. Two earlier fixed runs reached cross-world checks but their arbitrary
screen sample hit dark gold-block texture grout (green channel 99/100). The fixture
now projects a known gold block-face center; color thresholds are unchanged. These
test-fixture failures and screenshots remain in `fix-1.log` / `fix-2.log`.

After deterministic cleanup: zero targets/views, both far chunk locations unloaded,
121 local Overworld chunks and zero Nether client chunks. Nvidium's inactive Nether
renderer still has its bounded eight-region mesh cache because Sodium processes
deferred chunk events when that world is next extracted; disconnect destroys the
world renderer. This is recorded rather than claiming zero GPU regions immediately
after portal removal.

## Final report A–S

### A. Exact environment

Minecraft 26.3; Fabric Loader 0.19.5; Fabric API runtime 0.163.0+26.3; Sodium
0.9.2+mc26.3; Nvidium 0.4.4-beta7-26.3 (embedded commit 27ceb72); Java 25.0.4.1;
RTX 3060; NVIDIA 617.42; Windows; OpenGL (driver string 3.3.0 NVIDIA 617.42).
The exact loaded-mod manifest is archived with the runtime evidence. Iris and
shader packs were absent. Existing committed Sodium/API defaults were not changed.

### B. Nvidium activation — PASS

Logs report `All capabilities met` / `Enabling Nvidium`; IS_ENABLED remains true,
each loaded world has a live NvidiumWorldRenderer, nonempty terrain regions and
an increasing RenderPipeline terrain-frame counter. Nvidium debug output reports
its actual GPU terrain phases/memory. Correct marker pixels are required as well.

### C. Pre-fix baseline — FAIL

Phase 3 did not already work unchanged. Two unchanged-runtime attempts reproduced
the missing same-world destination and main-world geometry with Nvidium active,
including a longer wait. First meaningful failure is the positive emerald pixel
assertion, not a native crash or disabled renderer. See the root-cause/fix section.

### D–J. Deterministic validation

| Requirement | Result | Evidence |
| --- | --- | --- |
| D. Distant same-world single view | PASS | Emerald marker, live advancing Nvidium renderer, restored main camera |
| E. Two same-world views | PASS | A emerald, B lapis; identical under reversed order |
| Distinct poses in the same destination area | PASS | A emerald, B gold from another pose in the same room |
| F. Overworld → Nether → Overworld | PASS | Projected gold-block face and actual walking both directions |
| G. Rolled / near-plane views | PASS | Asymmetric emerald/gold wall maps vertically at 90-degree roll; 0.03-block near-plane image |
| H. Traversal | PASS | Same-world, cross-world and rolled travel; no yaw-reversal recurrence |
| I. Chunk visibility | PASS | Both far destinations visible; 219 total world chunks for two destinations, not 219 remote-only chunks |
| J. Cleanup | PASS | Zero targets/cached views, far locations unloaded, 121 local chunks; client worlds released on disconnect |

The original Phase 3 pixel and structural restoration assertions remain: main
renderer/render-state pointer, Sodium camera position/angles/cull matrix, and main
purple marker. No exception-only rendering pass is counted. Cross-world gold
validation projects a known block-face center to avoid dark texture grout.

### K. Short stress

**PASS: 180.795 seconds, nine actual portal crossings and 18 periodic samples.**
The scenario holds two same-world destination views, changes yaw/pitch repeatedly,
walks through a portal periodically and returns through an ordinary test teleport,
and periodically checks pixels, stable renderer identities, region/chunk counts
and target reuse. Total Nvidium regions stayed **20 → 20**; both world-renderer
identities remained fixed. Overworld chunks peaked at **253**, then returned to
121 after cleanup. Far locations unloaded; cached views/targets returned to zero.
Every sampled A/B image remained correct. Two views reused two targets once the
earlier cross-world view expired. Evidence: `stress.log`, `stress-result.json` and
the 18 `stress-<n>.json` observations, archived in `stress-evidence` with screenshots.
The entire process exited 0 after 3m51s including startup, deterministic checks,
stress and final roll/cleanup. No native crash was observed. This is not a
long-session or heap-level certification.

### Focused regression results

- Sodium 0.9.2 without Nvidium: PASS (`regression-sodium092.log`, 37 seconds).
- Sodium 0.9.3-alpha.1 without Nvidium: PASS (`regression-sodium093.log`, 38 seconds).
- Vanilla core and final jar build: PASS (`regression-vanilla-build.log`, 37 seconds).

Both use fresh isolated profiles and the existing Phase 3 multi-camera/roll/cleanup
test, whose baseline guard explicitly rejects Nvidium. This verifies optional adapter
absence and preserves generic Sodium behavior without rerunning the full Phase 3 matrix.
The vanilla run checks absent optional targets, same-/cross-world pixels, near-plane
views, traversal and cleanup. The jar includes only the compatibility mixin/config,
not Nvidium itself or any test/driver classes.

### L. NVIDIA warning / driver observation

The existing warning code was not modified. It is expected not to appear with
Sodium present, because its original condition requires Sodium's absence. It is
a NVIDIA-hardware warning, not Nvidium detection. The final vanilla regression
without Sodium logged the original NVIDIA driver-warning chat message, confirming
it still appears when its original condition holds. No driver/system setting was
changed. No native NVIDIA/OpenGL crash was observed in fixed or stress runs;
failed baseline/fixture runs are Java assertion failures. Short tests cannot
establish long-session driver stability or safety on other drivers/GPUs.

### M. Defects

- **P1: Nvidium temporal visibility shared between cameras.** Reproduced twice;
  cold per-camera visibility fixes missing/wrong geometry while keeping Nvidium
  active. The regression retains the original positive pixel assertion, reverse
  order, main marker, shared destination poses, traversal and cleanup checks.
- **P2 test fixture: gold texture grout sample.** The arbitrary screen offset
  landed on RGB 158,100,22 / 157,99,22 at the strict color boundary. The wall was
  visibly present. Projecting a known block-face center fixes sampling without
  reducing color thresholds. No additional rendering fix was made for this.
- The known far-separated vanilla ViewArea limitation remains unchanged and is
  not treated as a Phase 4 defect.

### N. Files changed in Phase 4

- `build.gradle`: optional compile-only exact Nvidium target, isolated test run
  directory property, focused deterministic/stress test selectors.
- `src/main/java/qouteall/imm_ptl/core/compat/IPCompatMixinPlugin.java`: load the
  Nvidium adapter only when Nvidium is present.
- `src/main/java/qouteall/imm_ptl/core/compat/nvidium/mixin/MixinNvidiumRenderPipeline.java`:
  per-camera temporal visibility repair, limited to portal draws and main re-entry.
- `src/main/resources/imm_ptl_nvidium.mixins.json` and `fabric.mod.json`: optional
  client adapter registration; no hard runtime Nvidium dependency or bundled jar.
- `src/gametest/java/qouteall/imm_ptl/gametest/SodiumMulticameraTests.java`: reuse
  existing assertions with explicit Phase 4 activation/cross-world/stress hooks.
- `src/gametest/java/qouteall/imm_ptl/gametest/NvidiumTestSupport.java`: exact
  environment/activation evidence, known-face pixels, focused scenes and stress.
- `docs/phase4-nvidium-compatibility.md`, `docs/nvidium-compatibility-audit.md`,
  `docs/26.3-port-progress.md`: progress, results and precise support limitations.

Earlier Phase 1–3 working-tree changes remain intact. No generic Sodium rendering,
camera transforms, common/server/network, authority, packet or ticket code changed
in Phase 4. Generated profiles, copied test jars, logs/images/manifests and build
artifacts remain under `build`; nothing is committed or deployed.

### O. Exact commands

PowerShell, repository root, JAVA_HOME=`E:\MinecraftServer` and its `bin` prepended
to PATH. All game directories below are disposable. The base deterministic command:

```text
.\gradlew.bat runClientGametest -Pfabric_version=0.163.0+26.3 -Pclient_test=phase4 -Pclient_run_dir=build/phase4/client -Penable_sodium=true -Psodium_path=maven.modrinth:sodium:mc26.3-0.9.2-fabric --console=plain
```

It produced `baseline.log`, `baseline-repro.log`, `fix-1.log`, `fix-2.log`,
`fix-3.log` as the documented baseline/adapter/fixture changes were made. A final
targeted repeat, `final-cleanup.log`, adds a direct destruction witness: both
retained RenderSectionManager test references report null Nvidium renderers after
disconnect, which occurs only after Nvidium's renderer.delete() returns. The test
then clears its own retained references. `after-disconnect.json` records two
destroyed managers and an empty world-renderer map. The
stress command changes only the test selector:

```text
.\gradlew.bat runClientGametest -Pfabric_version=0.163.0+26.3 -Pclient_test=phase4_stress -Pclient_run_dir=build/phase4/client -Penable_sodium=true -Psodium_path=maven.modrinth:sodium:mc26.3-0.9.2-fabric --console=plain
```

Final focused regressions and artifact build:

```text
.\gradlew.bat runClientGametest -Pfabric_version=0.163.0+26.3 -Pclient_test=phase3 -Pclient_run_dir=build/phase4/sodium092 -Penable_sodium=true -Psodium_path=maven.modrinth:sodium:mc26.3-0.9.2-fabric --console=plain
.\gradlew.bat runClientGametest -Pfabric_version=0.163.0+26.3 -Pclient_test=phase3 -Pclient_run_dir=build/phase4/sodium093 -Penable_sodium=true --console=plain
.\gradlew.bat assemble runClientGametest -Pfabric_version=0.163.0+26.3 -Pclient_test=core -Pclient_run_dir=build/phase4/vanilla -Penable_sodium=false --console=plain
git diff --check
```

Evidence root: `build/phase4-inspection`; live profile evidence under
`build/phase4/client/phase4-evidence`. The cached Nvidium jar is copied only into
that profile's `mods` directory. No metadata override, dependency bypass or clean.

The final runtime jar SHA256 is
`2FA9FE477752AE0976AF78E14E86EEA664C897F2053D4C752FEFDC2E37BA63C7`.
It remains only at `build/libs/immersive-portals-6.0.6-mc26.3-fabric.jar`.
Machine-readable summary: `build/phase4-inspection/phase4-results.json`.

### P. Deliberately not run

Full Phase 1 lifecycle/inventory matrix, full Phase 2 three-process multiplayer,
Vulkan/Nvidium, Iris, shader packs, nine-camera vanilla suite, long-session driver
stress, production world and full production pack. The adapter is client-only and
Nvidium-specific; it does not justify repeating those unrelated matrices.

### Q. Production safety

No production world/config/mod/AutoModpack/backup/EULA files or production ports
were accessed for tests or modified. No jar was copied into production. The Java
25 executable installation under `E:\MinecraftServer\bin` was used for tools;
all game state and copied test mods were confined to repository/build directories.

### R. Scope of support

**Validated with Nvidium 0.4.4-beta7-26.3 + Sodium 0.9.2 on the tested NVIDIA/OpenGL
environment, with the Phase 4 Nvidium visibility adapter.** Same-world single/multiple
views, cross-world views, traversal, roll, near-plane camera and bounded cleanup/stress
passed on Minecraft 26.3 / Loader 0.19.5 / Fabric API 0.163.0+26.3 / Java 25.0.4.1.

The final support statement is limited to this exact NVIDIA/OpenGL environment,
one portal layer and at most two visible views. It does not certify other drivers,
GPUs, long sessions, arbitrary configurations, dynamic-resolution targets, shader
packs or the full production modpack. Nvidium still requires Sodium 0.9.2; no attempt
was made to force it onto 0.9.3-alpha.1. Vulkan/Nvidium was not exercised or forced.

### S. Exactly one recommended Phase 5

**Iris active-shader same-dimension / multi-camera compatibility on isolated clients.**
This remains separate from Nvidium; no Phase 5 work has been started.
