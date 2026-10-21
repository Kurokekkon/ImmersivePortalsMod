# Phase 5: active Iris shader multi-camera compatibility

Completed 2026-10-19. No production deployment. Validated primary stack: Minecraft 26.3,
Loader 0.19.5, API 0.163.0+26.3, Java 25, Sodium 0.9.3-alpha.1,
Iris 1.11.6, Complementary Reimagined r5.9.3, OpenGL.

## Progress checklist

- [x] Inspect Phase 3 scheduling, current shader guard, clipping/lightmaps,
  Iris pipeline/temporal ownership, Phase 4 hooks and quarantined legacy renderer.
- [x] Reproduce the active-shader restriction before changing runtime code.
- [x] Implement bounded Iris-specific camera state ownership.
- [x] Single same-dimension view.
- [x] Two cameras, reversed order and shared destination poses.
- [x] Cross-dimension rendering and return traversal.
- [x] Roll, near-plane and main camera state assertions.
- [x] Shader reload and target/chunk/pipeline cleanup.
- [x] One second shader pack sanity (Unbound r5.9.3).
- [x] One three-minute active shader stress run (final scope fix: 180.753 seconds).
- [x] Applicable focused regressions and backend assessment.
- [x] Final artifact, evidence and support statement.

## Initial architecture audit

`PortalViewRenderer` queues Sodium cameras and extracts each immediately before
drawing it. The main view draws first. Each view already owns a target, camera,
cloud buffers, lightmap, fog and projection buffer. The active-shader guard skips
same-dimension destinations and a second view of any destination dimension.

Iris 1.11.6 `PipelineManager` caches pipelines by dimension, not camera.
`IrisRenderingPipeline.beginLevelRendering` advances its camera-position tracker,
updates custom uniforms, clears/resizes G-buffers and shadow resources.
`MatrixUniforms.Previous` stores prior matrices per uniform supplier;
`ProgramUniforms` updates PER_FRAME uniforms only once per system frame.
Composite/final passes mutate ping-pong/temporal targets. Sharing the pipeline
would therefore retain stale current-camera uniforms and overwrite temporal
history. Saving only Java matrices cannot preserve texture history.

Iris selects the destination program set through `Iris.getCurrentDimension()`
and uses the swapped `GameRenderer.mainCamera()`. Clipping patches only G-buffer
programs; shadow programs are intentionally unclipped. Standard perspective is
preserved because shader effects reconstruct depth. Phase 4's Nvidium hook is
independent and remains unchanged; Nvidium is excluded from this phase's client.

The quarantined old renderer assumes old stencil/shadow swapping APIs and will
not be restored. Reproduction and subsequent implementation evidence follow.

## Reproduction evidence

`build/phase5-inspection/baseline.log`: FAIL as expected, with
`ACTIVE_SHADER_LIMITATION: existing same-dimension portal is suppressed`.
The log positively identifies ComplementaryReimagined_r5.9.3.zip and an active
`IrisRenderingPipeline`, a live portal entity and zero queued portal views.
The screenshot is retained in the disposable client's screenshots directory.
An earlier invocation did not reach tests because PowerShell split an unquoted
dotted Gradle property; all subsequent commands quote `-P` arguments.

## Chosen architecture

Keep the Phase 3 sequential extraction/draw schedule and existing chunk meshes.
`IrisViewPipelines` owns an access-ordered cache of at most `MAX_VIEWS` (2)
camera pipelines, keyed by portal UUID with a destination-program-dimension
check. A steady scene allocates no new pipelines per frame. Each pipeline has
its own uniforms/previous matrices/camera tracker, G-buffers, temporal ping-pong
textures, composites, final pass and shadow resources. The destination uses its
actual pack program set, not a synthetic dimension or hard-coded shader source.

The optional PipelineManager hook selects the scoped camera pipeline during a
portal draw. It does not insert it into Iris' main dimension map or reset global
frame/time counters. The main pipeline pointer and Iris singleton scratch state
are restored in `finally`. An outer scratch-state scope covers extraction as
well as drawing: Iris' `MixinFogRenderer.setupFog` updates singleton fog and
`MixinLightTexture.extract` resets singleton darkness before pipeline drawing
begins. A draw-only scope captures these values too late. The focused canary
regression reproduced `darkness=0.137 -> 0.0` in
`extraction-pre-fix.log` before extending the scope.
Main camera, renderer, target, lightmap, projection,
fog and Sodium visibility are restored by the existing renderer scopes.

Expiry, dimension-key replacement, bounded eviction, shader reload and disconnect
release owned camera pipelines. Iris' static override-wrapper map otherwise
retains programs after pipeline destruction: release removes only wrappers for
the owned pipeline's programs, closes their VAOs, then delegates shader/texture/
framebuffer cleanup to Iris. Wrapper `.close()` is deliberately not used because
it also closes programs, which the Iris pipeline itself owns.

## Temporal and shadow audit

Complementary's `lib/antialiasing/taa.glsl` reprojects with previous camera
position, previous model-view/projection and `colortex2` history; properties also
derive frame-modulo uniforms from Iris' frame counter. TAA/history are not
globally disabled. Camera-owned pipelines isolate both Java history and GPU
history. Main temporal update notification must not advance during portal draws;
the test attaches a counter to the real main pipeline's notifier to assert this.
Tests also inspect that main and portal pipelines own distinct render targets.

Each visible shader portal performs one normal Iris shadow pass, not recursive
or nested shadows. Reusing the main map at a distant destination would give
incorrect coverage. With at most two visible views this is bounded; two-view
samples measured about 4 ms in portal rendering at roughly 110–120 FPS (120 FPS
harness cap), including ordinary portal draws and some one-time compilation
costs in the running average. These are approximate CPU wall timings, not GPU
profiling or production performance promises. Full camera pipelines cost more
VRAM than shaderless targets. First use/reload and cache eviction can produce
visible shader compilation stalls. Perfect shadow parity across arbitrary packs
is not claimed.

The tested profile is Complementary HIGH, zero user option overrides. Destination
geometry is visibly shader shaded. Standard projection plus the existing
G-buffer clip uniform is retained, including a deliberately red occluder between
the virtual camera and destination plane that must disappear through clipping.

## Backend assessment

Vulkan: NOT APPLICABLE for this Iris 1.11.6 stack. Its actual `IrisRenderSystem`
casts the backend to `GlDevice`, and shader overrides use `GlProgram`, OpenGL
vertex arrays and GL framebuffer operations. Sodium's separately validated
Vulkan support does not make this Iris implementation backend-neutral. No Iris
or Vulkan engine patch is attempted.

## Runtime defects and regression coverage

1. **High: active-pack same-dimension/multi-camera suppression.** Reproduced
   before runtime edits (`baseline.log`). The guard prevented sharing Iris'
   dimension-owned camera state; simply removing it would mix PER_FRAME uniforms,
   previous-camera suppliers and GPU history. Fixed with bounded camera ownership.
   Positive destination pixels, reverse order, shared destination poses, roll,
   main state and temporal ownership checks cover the replacement.
2. **High: main fog/lighting scratch overwritten during extraction.** A draw-only
   scope begins after Iris fog/lightmap extraction hooks. The deterministic
   `phase5_state_probe` canary failed `0.137 -> 0.0` in `extraction-pre-fix.log`.
   Scope now starts before extraction; `extraction-fixed-canary.log` passes.
   `extraction-fixed.log` also passed the structural check but later failed a
   gold pixel assertion because the initial canary itself was left active after
   checking it. The test now restores the original darkness immediately after
   asserting preservation. Pixel thresholds were not weakened.
3. **Medium: per-camera shader wrappers retained by Iris' global cache.** Found
   by auditing exact resource ownership while introducing camera pipelines.
   Owned program wrappers are removed and their VAOs closed before Iris destroys
   the pipeline. Stress tracks cache size, target count and bounded pipelines;
   reload/expiry/disconnect are covered. No unrelated Iris caches are cleared.

## Files changed in stabilization Phase 5

Runtime:

- `compat/iris_compatibility/IrisInterface.java`: optional scratch/camera lifecycle hooks.
- `compat/iris_compatibility/IrisInterfaceOnPresent.java`: delegate optional hooks.
- `compat/iris_portal_view/IrisViewPipelines.java`: bounded camera ownership,
  singleton preservation and program-wrapper/VAO cleanup.
- `compat/iris_portal_view/mixin/IrisPipelineManagerAccessor.java`: scoped pipeline pointer.
- `compat/iris_portal_view/mixin/MixinIrisPipelineManager.java`: select/release camera pipelines.
- `compat/iris_portal_view/mixin/IrisPipelineAccessor.java`: owned shader program set for cleanup.
- `compat/iris_portal_view/mixin/IrisRenderSystemAccessor.java`: Iris wrapper cache access.
- `compat/sodium_compatibility/SodiumInterfaceOnPresent.java`: allow multiple cameras
  now that the Iris adapter owns shader histories; no terrain setup/math changes.
- `portal_view/PortalViewRenderer.java`: scoped adapter calls, including extraction,
  and release on view expiry/cleanup; existing scheduling and finally restoration remain.
- `src/main/resources/imm_ptl_iris.mixins.json`: register the optional hooks/accessors.

The Java paths above are under `src/main/java/qouteall/imm_ptl/core/`.
Test/build/docs: `build.gradle` targeted selectors; new `IrisMulticameraTests.java`
and `IrisTestSupport.java`; focused Iris hooks in `SodiumMulticameraTests.java`
and test-only `mixin/PortalViewOrderMixin.java`; this report and
`docs/26.3-port-progress.md`. Existing dirty changes from Phases 1–4 are preserved.
No Phase 5 server/common/network code changes; no Phase 2 rerun.

## Commands

All commands run in `E:\ImmersivePortalsMod`, PowerShell, after:

```powershell
$env:JAVA_HOME='E:\MinecraftServer'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
```

Only installed Java executables are used there; no production files are modified.
Primary commands (same arguments, change only the selector for the documented
baseline/probe/stress runs):

```powershell
.\gradlew.bat runClientGametest '-Pfabric_version=0.163.0+26.3' '-Pclient_test=phase5_baseline' '-Pclient_run_dir=build/phase5/client' '-Penable_sodium=true' '-Penable_iris=true' --console=plain
.\gradlew.bat runClientGametest '-Pfabric_version=0.163.0+26.3' '-Pclient_test=phase5' '-Pclient_run_dir=build/phase5/client' '-Penable_sodium=true' '-Penable_iris=true' --console=plain
.\gradlew.bat runClientGametest '-Pfabric_version=0.163.0+26.3' '-Pclient_test=phase5_state_probe' '-Pclient_run_dir=build/phase5/client' '-Penable_sodium=true' '-Penable_iris=true' --console=plain
.\gradlew.bat runClientGametest '-Pfabric_version=0.163.0+26.3' '-Pclient_test=phase5_stress' '-Pclient_run_dir=build/phase5/client' '-Penable_sodium=true' '-Penable_iris=true' --console=plain
.\gradlew.bat runClientGametest '-Pfabric_version=0.163.0+26.3' '-Pclient_test=phase5_secondary' '-Pclient_run_dir=build/phase5/client' '-Penable_sodium=true' '-Penable_iris=true' --console=plain
```

## Tests deliberately not run

No Phase 1, Phase 2 two-client rerun, full Phase 3 version/backend permutations,
Phase 4 180-second stress, nine-camera comparison, unsupported Iris/Vulkan run,
Nvidium with Iris, recursive/mirror/GUI portals or full production modpack tests.
No server/common/network changes or shared camera/projection math changes warrant
those suites. Shader stress was repeated after the extraction-boundary fix,
because that changed the state scopes being certified.

## Sources

Pipeline audit uses the exact cached Iris 1.11.6 jar, decompiled into
`build/phase5-inspection/iris`; previous rendering decisions are in commits
`b2288199`, `5e85d8f5`, `c86c3674`, `2df4a519`.
Pack downloads use the publisher's Modrinth projects, matching the official
[Complementary manual download page](https://www.complementary.dev/manual-download/):
[Reimagined r5.9.3](https://modrinth.com/shader/complementary-reimagined/version/Bqen1mJX).
Exact API download metadata/hashes are retained under `build/phase5-inspection`.

## Final report: A–U

**A. Root cause:** Iris' dimension-owned pipelines contain camera-specific
PER_FRAME uniforms, CPU history and GPU temporal targets. The original guard
suppressed cameras that would share them. Removing only the guard is unsafe.

**B. Architecture:** at most two cached camera-owned pipelines; real destination
program sets; sequential draw; finally restoration around extraction and draw;
explicit expiry/reload/disconnect cleanup. See the ownership audit above.

**C. Exact environment:**

| Component | Tested value |
|---|---|
| Minecraft | 26.3 |
| Fabric Loader | 0.19.5 |
| Fabric API | 0.163.0+26.3 (runtime override; committed default unchanged) |
| Sodium | 0.9.3-alpha.1+mc26.3 |
| Iris | 1.11.6+mc26.3 |
| Primary pack | ComplementaryReimagined_r5.9.3.zip, HIGH, zero user overrides |
| Secondary pack | ComplementaryUnbound_r5.9.3.zip, HIGH, zero user overrides |
| Java | Eclipse Temurin 25.0.4.1+1, 64-bit |
| GPU | NVIDIA GeForce RTX 3060 |
| NVIDIA driver | 617.42 |
| Backend / OS | OpenGL / Windows 11 |
| Immersive Portals / DimLib | 6.0.6 / 1.1.0-port.1+mc26.3 |

**D. Pre-fix reproduction:** PASS (the unchanged-runtime test intentionally
FAILED with an active pack/pipeline and an existing suppressed portal).

**E. Primary pack:** PASS for single same-dimension destination, two cameras,
reverse order, shared destination/distinct poses, cross-dimension destination,
outbound/return travel, roll, 0.03-block near-plane, composited near-plane image,
clipping, main restoration and target/pipeline/chunk cleanup.

**F. Second pack:** PASS for single same-dimension, two cameras, reverse order,
shared destination, cross-dimension view/return traversal and cleanup. No second
pack stress/roll/reload matrix was run (`secondary.log`, 55 seconds).

**G. Main camera restoration:** PASS across **23,362** checked portal-rendering
frames in the final primary run, plus the focused extraction canary. Checks
cover Iris pipeline identity, camera, target, lightmap, GPU projection/fog/globals,
Iris matrices/fog/lighting/tick/cloud values, shadow singleton state and main
temporal notifier. Sodium position/angles/culling matrix are checked separately.

**H. Temporal / shadow state:** independent histories and targets; global system
frame count is not advanced for portal cameras. Each visible camera has one
ordinary Iris shadow pass and its own map; no recursion or nested passes.
Maximum two portal pipelines. First-use/reload compilation stalls and extra
shader VRAM/shadow cost remain; arbitrary-pack parity is not certified.

**I. Resource reload:** PASS. `Iris.reload()` clears camera pipelines; subsequent
positive two-camera destination pixels confirm recreated pipelines and live
targets. The primary pack remains enabled.

**J. Chunk visibility:** PASS. Two-destination scene: **219** loaded Overworld
chunks. After removal: **121** local chunks, both far destination chunks
released, zero retained portal views/targets/pipelines. No chunk loader redesign.

**K. OpenGL:** PASS in the exact environment above.

**L. Vulkan:** NOT APPLICABLE for this Iris implementation; see backend audit.

**M. Bounded stress:** PASS, **180.7531275 seconds**, **11 crossings** including
a Nether round trip, **17** positive pixel samples, stable **2** targets and
**25** wrapper-cache entries during sampling. Camera pipeline count stayed at 2.
Transient travel/reload stages can retain a third expiring view target; final
cleanup is zero. Last stress sample: 120 FPS; cumulative portal CPU time about
1.98 ms/frame (includes earlier setup stages, not GPU time). No native crash,
GL error or rendering assertion failure in the successful final run.
The earlier architecture stress also passed (180.650 seconds); it predates the
extraction-boundary extension and does not substitute for the final run.

**N. Regressions:**

| Run | Result | Evidence |
|---|---|---|
| Sodium 0.9.3-alpha.1, no Iris/Nvidium; core plus two-camera suite | PASS, 52 seconds | `regression-sodium.log` |
| Sodium 0.9.2 + active Nvidium 0.4.4-beta7-26.3; focused two-camera sanity | PASS, 39 seconds | `regression-nvidium.log` |
| Vanilla same-dimension/cross-dimension core + assemble | PASS, 37 seconds | `regression-vanilla-build.log` |

All logs are in `build/phase5-inspection`. Successful processes exited 0.
The original NVIDIA warning was positively observed in the vanilla run and
remains unchanged. Known test-profile anisotropic-filter value 0 and offline
Realms authentication warnings remain; shader creation produced bounded
integrated-server catch-up warnings. These were not hidden or treated as GL
failures. No Phase 5 native `hs_err_pid` crash file was found.

**O. Defects found:** see the three prioritized defects/regressions above.

**P. Files changed:** see the explicit file list above. Machine-readable
`runtime-changes.json` reconstructs the Phase 4 baseline and proves exactly ten
runtime source/resource changes; none are server/common/network or Nvidium hooks.

**Q. Exact final regression/build commands:**

```powershell
.\gradlew.bat runClientGametest '-Pfabric_version=0.163.0+26.3' '-Pclient_test=phase3_all' '-Pclient_run_dir=build/phase5/sodium' '-Penable_sodium=true' '-Penable_iris=false' --console=plain
.\gradlew.bat runClientGametest '-Pfabric_version=0.163.0+26.3' '-Pclient_test=phase4_sanity' '-Pclient_run_dir=build/phase5/nvidium' '-Penable_sodium=true' '-Penable_iris=false' '-Psodium_path=maven.modrinth:sodium:mc26.3-0.9.2-fabric' --console=plain
.\gradlew.bat assemble runClientGametest '-Pfabric_version=0.163.0+26.3' '-Pclient_test=core' '-Pclient_run_dir=build/phase5/vanilla' '-Penable_sodium=false' '-Penable_iris=false' --console=plain
```

Primary/probe/secondary/stress commands are listed earlier. `git diff --check`
passed; the pre-existing DedicatedServerPortalTests line-ending advisory remains.

**R. Tests deliberately not run:** listed above. No `clean`, production test,
full modpack certification, unrelated server suite or broad renderer matrix.

**S. Production safety:** `E:\MinecraftServer` is unchanged. No development jar,
shader, config, mod, AutoModpack, world, EULA or production port was modified or
used for test world data. Its installed JDK executables were used to run Gradle.
All test worlds/profiles/evidence are repository/build-owned. No deployment or
commit was made; existing worktree changes were preserved.

**T. Support statement:** Validated with **Iris 1.11.6 and Complementary Shaders
Reimagined r5.9.3** on **Minecraft 26.3, Sodium 0.9.3-alpha.1, OpenGL, RTX 3060,
driver 617.42**. Same-dimension/cross-dimension destination views and distinct
camera views sharing a destination renderer work in these tests, with traversal,
main-state isolation, cleanup and reload. **Unbound r5.9.3** passed the smaller
portability sanity. This does not certify every pack/GPU, long sessions,
Nvidium with Iris, or the user's full production modpack.

**U. Recommended Phase 6:**

**FULL MODPACK COMPATIBILITY CERTIFICATION ON A DISPOSABLE COPY OF THE USER'S
CURRENT SERVER/CLIENT ENVIRONMENT.**

Phase 6 has not begun.

## Artifact

`build/libs/immersive-portals-6.0.6-mc26.3-fabric.jar`

SHA256:
`10DA74A2147845912957D876C9AE85CA9D19F3651A90C611C086FF321D4EC796`

Verified: camera adapter/mixins included; Iris/Sodium/Nvidium dependency classes
and gametest/multiplayer drivers are not bundled. Not deployed. The prior Phase 4
artifact is retained as `build/phase5-inspection/phase4-baseline.jar`.
