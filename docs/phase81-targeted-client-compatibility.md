# Phase 8.1 — targeted client compatibility

2026-10-20, America/Mexico_City. Production is read-only during this phase. **Voice packet-drop defect reproduced and fixed in a local RC2 candidate. Visual seam not reproduced. No deployment.**

## Progress

- [x] Read Phase 8.1 and preserve installed/certified RC1.
- [x] Confirm reported stack: no optional renderer; ordinary obsidian portal.
- [x] Inspect native portal construction, view/target lifecycle and exact Voice Chat 2.6.24 code/bytecode.
- [x] Reuse marked Phase 6 clone/client; no production world copy.
- [x] Reproduce voice lookup failure using actual packet-processing code on an audio worker.
- [x] Separately test the listener-camera hypothesis.
- [x] Apply narrow optional client compatibility fix; no shared renderer/camera changes.
- [x] Run affected voice/third-person/traversal checks and bounded seam probes.
- [x] Produce separately packaged opt-in diagnostics and preserve candidate/source evidence.
- [x] Stop disposable JVMs cleanly; verify production RC1/config/mod hashes unchanged.

## A. Issue A — portal seam: NOT REPRODUCED

The user confirmed a normal obsidian portal and no Sodium/Iris/Nvidium. The test uses the actual distributed pack with its vanilla OpenGL renderer, RTX 3060 / NVIDIA 617.42. No renderer permutation was added.

**Geometry first:** `PortalGenInfo.generateBiWayBiFacedPortal` creates f1/f2 (opposite faces in source) and t1/t2 (opposite faces in destination), not one entity/target per obsidian-frame block. The normal 2×3 opening is one full rectangular face from the observer's side. Runtime geometry report contains all four UUIDs, origins, normals and sizes; only one source face/target rendered from the test viewpoint. The controlled fixture builds real obsidian frames/placeholders and uses this native generation routine; destination placement is deliberately displaced to Nether 10000,101,10000 for the audio test. Fire ignition/frame-search itself was not retested.

An additional controlled doorway consists of two adjacent ordinary portal rectangles with identical destination transformation and independent UUIDs/targets. This explicitly exercises the proposed two-half case without merging entities.

| Targeted seam scenario | Observed FPS / cap | Portal draws | Old-frame target draws |
| --- | --- | --- | --- |
| Native obsidian frame, stationary/slow camera rotation | 30 / 30 | 113 | 0 |
| Native obsidian frame, faster camera rotation | ~138 / 140 | 379 | 0 |
| Adjacent rectangles, camera rotation | ~133 / 140 | 980 | 0 |
| Adjacent rectangles, slow sneaking then faster normal lateral movement | ~139 / 140 | 726 | 0 |

Requested 144 FPS is clamped by the Minecraft option to 140; the report records the actual setting. VSync was disabled in disposable-client variants only. No uncapped/Vulkan/shader/stress matrix was needed. Screenshots of native and adjacent views were inspected: no split or stale half was identified. Moving outside the frustum naturally omits whole views; that is not accepted as proof of a seam fix. No temporal video or exhaustive pixel-comparison claim.

**Target/view audit:** cache is UUID → View; each view owns its target and render state. FRAME_VIEWS clears every extraction. MAX_VIEWS=2 truncates the sorted list of whole portals. A missing destination or failed extraction is not added; a failed draw is not added to drawnViews. Targets are composed only after successful renderView. One portal's triangles share the same sampled target. The GL command stream orders writes before composition; CPU diagnostics establish submission/frame consistency, not independent GPU fence completion or monitor scanout correctness.

No N/N−1 split was found, so **no visual renderer fix was invented**. Display tearing/frame presentation and affected players' exact geometry remain possible investigation directions, not established causes.

**Bounded future diagnostics:** `build/libs/immersive-portals-phase81-diagnostics.jar`, separate from RC2 and not installed in production/normal profiles. Client-only `/ipdiag start` and `/ipdiag stop` capture at most 4,096 draw rows, 256 listener samples, 100 controlled audio samples (only the test driver can enable those), 16 errors and a capped target map. Real-client commands only enable passive diagnostics: no fake speaker, packet injection, camera override or movement. Output goes to that client's `debug/ip-phase81-<timestamp>-<uuid>.json`. No default per-frame file/log spam. The harness exercises the probe data path; the separately packaged chat-command entrypoint is compiled but was not installed in a real-player profile here.

Each draw records extraction frame, Minecraft GameRenderer.render callback index, portal UUID/entity/origin/size, target identity and most recent submitted/render frame, destination camera position/quaternion, projection/view hashes and draw order. It also records actual FPS cap/VSync, camera type and loaded mod versions. Final movement capture has zero render-frame mismatches. If the seam returns, a short video plus this opt-in report, portal creation details and affected client's display/VSync settings will distinguish stale target data from frame presentation. No claim that diagnostics resolve the seam.

## B. Issue B — Simple Voice Chat: REPRODUCED

**The proposed listener-camera jump was not reproduced.** Voice Chat's GameRenderer mixin calls its render-tick event at `GameRenderer.render` HEAD. CameraTracker updates there, outside the nested LevelRenderer.render calls used for portal views. Baseline listener samples stayed at the actual main camera both with the portal hidden and visible. No portal destination camera was sampled. First-/third-person metadata and normal plugin camera overrides remain unmodified.

**Actual root cause:** `AudioChannel.writeToSpeaker` runs on an audio worker and reads `Minecraft.level` when looking up a PlayerSoundPacket's sender. `ClientWorldLoader.withSwitchedWorld` temporarily changes that singleton to the portal destination during remote rendering, while explicitly retaining the local player's physical level. A real nearby speaker can therefore be missing from both UUID and fallback entity searches. The method returns before playback. This produces portal-dependent packet loss without a listener jump or UDP connectivity failure.

The deterministic test adds one synthetic nearby RemotePlayer to the marked disposable client's physical world, constructs a normal PlayerSoundPacket and invokes the **real private writeToSpeaker implementation on a worker while actual portal rendering holds its temporary context**. A test-only hook observes speaker lookup and aborts once a valid speaker is found, before hardware playback/recording. Null lookup runs through the real fallback/early return. No microphone or full codec/audio recording framework. This proves the packet-processing defect, not subjective speech quality or the real-world percentage of dropped packets.

| Baseline RC1 | Controlled calls | Destination-render calls | Early return/drop |
| --- | --- | --- | --- |
| Portal invisible, same physical location | 100 | 0 | 0 |
| Portal visible | 100 | 50 | 50 |

Visible-case calls alternated between real Overworld lookup and temporary Nether lookup. All 50 temporary-world samples lost the real speaker. CameraTracker had zero portal-camera samples in both cases.

**Fix:** a client-only, optional IP mixin substitutes the local player's physical ClientLevel for the two `Minecraft.level` reads in Voice Chat's `writeToSpeaker` (primary UUID search and fallback search). If no local player exists, it leaves the original value unchanged. Real traversal updates the player's level normally. No camera freeze, positional-audio disable, plugin override, UDP modification, Voice Chat jar modification or shared world-switch change.

Exactly four runtime source/resource files changed relative to Phase 6/7:

- `src/main/java/qouteall/imm_ptl/core/compat/voicechat/mixin/MixinVoiceChatAudioChannel.java` — new narrow world-read adapter.
- `src/main/resources/imm_ptl_voicechat.mixins.json` — client-only mixin registration.
- `src/main/resources/fabric.mod.json` — register that config; no new hard dependency.
- `src/main/java/qouteall/imm_ptl/core/compat/IPCompatMixinPlugin.java` — apply only when voicechat is loaded.

## C. Targeted tests and exact commands

JDK from E:\MinecraftServer; project workdir E:\ImmersivePortalsMod. No clean/build-all/test-suite command:

```powershell
.\gradlew.bat phase6DriverJar '-Pfabric_version=0.164.0+26.3' --no-daemon --console=plain
.\gradlew.bat assemble phase6DriverJar phase81DiagnosticsJar '-Pfabric_version=0.164.0+26.3' --no-daemon --console=plain
.\gradlew.bat phase6DriverJar phase81DiagnosticsJar '-Pfabric_version=0.164.0+26.3' --no-daemon --console=plain
& tools/phase6-server.ps1 -Run phase81-baseline -Clone E:\MinecraftServer-ImmersivePortals-Test-Current
& tools/phase6-server.ps1 -Run phase81-fixed -Clone E:\MinecraftServer-ImmersivePortals-Test-Current
& tools/phase6-client-run.ps1 -Role A -Run phase81-probe1
& tools/phase6-client-run.ps1 -Role A -Run phase81-verified-fixed
& tools/phase81-voice-tests.ps1
```

Baseline and fixed native obsidian fixtures: `phase6-control -Target server -Action phase81-fixture` with mode obsidian; adjacent fixtures with mode adjacent. Camera variants: phase81-options (30/144 requested), look sweeps around yaw 180; phase81-start/stop captures. Lateral test uses shift/strafe held/released in finally. Scripts/control actions are test-only and refuse ordinary production use through the existing clone/client marker/loopback guards.

Test source/setup corrections were incremental compile-only fixes (protected portal dimensions use getters; Fabric 26.3 calls its API ClientCommands). Test-driver copying before a detached AutoModpack update completed caused a disposable-profile REPLAN_REQUIRED. It was resolved by waiting for the update to finish with profile files stable, then verifying active jar EB65… before counting fixed results. Earlier RC1-loaded sessions were not falsely counted as fixed tests. No AutoModpack/REI upstream patch or production setting changed.

Machine evidence: `build/phase81-inspection`, including case-summary.json, original/fixed raw captures, voice-regression-summary.json, seam-movement-summary.json, geometry, screenshots, build logs and process exits. Detailed normal update logs remain in `build/phase6-inspection`.

## D. Deliberately not run

Full historical Phases 1–6, full certification, multiplayer lifecycle matrix, nine-camera suite, Vulkan, Sodium-only matrix, Iris/Nvidium stress and unrelated gameplay tests were skipped because shared render/camera/network infrastructure did not change. No second client JVM was needed for the deterministic synthetic-speaker test. No normal player account/microphone or subjective audio recording was tested. No production portal, restart, claim/Waystone/grave or world edit.

## E. Direct regressions — PASS

| Fixed candidate | Calls / portal-context calls | Failed speaker lookups | Camera portal jumps | Stable distance / attenuation |
| --- | --- | --- | --- | --- |
| Invisible, first person | 100 / 0 | 0 | 0 | 2.0000 / 0.9583333 |
| Visible, first person | 100 / 50 | 0 | 0 | 2.0000 / 0.9583333 |
| Visible, third person back | 100 / 50 | 0 | 0 | 4.4721 / 0.9068305 |
| Visible after Nether crossing | 100 / 50 | 0 | 0 | 2.0000 / 0.9583333 |

All 400 fixed speaker calls use the physical world, including all 150 deliberately overlapping remote-render calls. The listener updates normally through real Overworld → Nether → Overworld walking; camera samples cover both dimensions with zero temporary portal-camera samples. Native portal view/traversal and same-dimension adjacent views still render. Final movement probe has 726 draws and zero stale-target/render-frame mismatches. The runtime renderer was not modified.

Test JVMs stop cleanly with exit 0. Known offline-profile authentication/skin timeouts, optional-mod warnings and prior recipe/bootstrap observations were not treated as new camera defects or silently fixed. A fresh microphone/manual multiplayer retest is still needed to judge perceived audio improvement and any remaining latency source.

## F. RC2 — CREATED

`build/libs/immersive-portals-6.0.6-mc26.3-fabric-lasratingas-rc2.jar`

- SHA256: **EB65EA142D7A27D8FD6C716A9276B643C1C027C754B7F6F7B9EC909306A43CD7**
- Size: **2,955,099 bytes**.
- Byte-identical to the fixed development jar actually loaded by the test server/client.
- Addresses the reproduced Voice Chat physical-world speaker lookup defect only. Not advertised as a seam fix.
- HEAD `c5a83c77cb493b4fbab2663a6d4f0de004895ea8`, branch 26.3, **dirty/uncommitted** prior phase plus Phase 8.1 changes. No commit/publication. Tested source manifest/archive and changes are preserved under `build/phase81-release`.
- Original mod ID/version 6.0.6, attribution/license/nested dependencies retained. Diagnostic/test classes are not in the RC2 jar.
- RC1 build/staged/production copies retain certified SHA256 DC762CD7…D9B58C9. RC2 not copied into production or its AutoModpack.

## G. Production

**E:\MinecraftServer remained untouched by Phase 8.1.** No writes, startup/shutdown, generation, live-world copy or deployment. Initial/final protected-file hash comparison has zero changes; production RC1 remains **DC762CD70C1649DA31D1FB2FCE4882B5FEE9690BDA8FE619CE944017DD9B58C9**. Test clone alone held Shooting Star aside to match the user's intentional removal. No change to normal client profiles. Only disposable test processes were stopped.

## H. Recommendation

**DEPLOY RC2 FOR MANUAL RETEST**, after separate explicit approval and the applicable deployment safeguards. Not done in this phase. Retest speech with portal visible/hidden, then ordinary crossings with one friend. For the still-unreproduced seam, obtain real-player video plus the opt-in bounded diagnostic report. No visual source fix until evidence supports one.
