# Phase 6 — Las Ratingas actual-pack portal compatibility

Completed 2026-10-19 (America/Mexico_City). **PASS — suitable for controlled production deployment preparation**, within the specified portal-integration criteria. This is not blanket certification of every feature of every mod. Unresolved recipe and bootstrap issues remain explicitly documented below.

## Progress checklist

- [x] Inspect specification, working tree, actual mods and versions.
- [x] Create stopped-world clones with before/after/copy SHA-256 verification.
- [x] Isolate Minecraft, Voice Chat and AutoModpack on loopback.
- [x] Preserve original evidence when production changes independently.
- [x] Honor user selection to certify the updated current pack.
- [x] Fix reproduced Polymer and claim-protection defects narrowly.
- [x] Verify actual distribution, full clients and representative portal interactions.
- [x] Test new Better Nether/worldgen and Naturalist portal surfaces.
- [x] Independently validate snapshot hashes, player/dimension and portal NBT.
- [x] Confirm saving restored and stop disposable JVMs cleanly.
- [x] Audit logs and verify current production source unchanged.
- [x] Preserve tested candidate, source provenance and report locally; no deployment.

## A. Source environment

Production: `E:\MinecraftServer`, Minecraft 26.3, Loader 0.19.5, Fabric API 0.164.0+26.3, Temurin Java 25.0.4.1+1. Read-only throughout this workflow.

The first stopped-world copy contained 977 files / approximately 2.12 GiB. Production subsequently independently ran 15:02–19:17:59 and its pack/world changed. Comparison against the morning manifest correctly detected those independent changes. Original clone/evidence were preserved.

After the user selected **“Certify the updated current pack”**, a second stopped-world copy captured **1,226 files / 3,666,579,016 bytes**, with every source-before/source-after/destination SHA-256 matching. Final verification at 19:56 found all 1,226 current source files unchanged and no world file-set differences. No IP jar is installed in production.

Evidence roots: `build/phase6-current-inspection` for current source/results; `build/phase6-inspection` for live harness and historical evidence; its `frozen-pack` directory preserves the earlier pack results. Important current evidence: `source-runtime-manifest.json`, `clone-copy-result.json`, `production-final-verification.json`.

## B. Clone and isolation

Current: `E:\MinecraftServer-ImmersivePortals-Test-Current`; original retained: `E:\MinecraftServer-ImmersivePortals-Test`. Both have `.immersive-portals-phase6-test-clone`.

Copied active world, mods/config/defaultconfigs, required libraries/versions/launcher, relevant root permission/configuration files and AutoModpack host files. Excluded historical backups/restore points, restore transactions, logs/crashes/debug, caches, JDK, credentials/staging, earlier test artifacts, production start/firewall scripts and root snapshot markers. Exact copied paths/category exclusions are in the manifests.

Clone-only networking: Minecraft **127.0.0.1:25681**, Voice **127.0.0.1:25682 UDP**, AutoModpack loopback HOLEPUNCH sharing Minecraft TCP. Resourceful Config Web remains disabled. No firewall/router/No-IP changes. Initial online-mode/whitelist baseline started successfully; automation subsequently uses clone-only offline mode, secure-profile enforcement disabled and whitelist disabled.

Test heaps: server Xms512M/Xmx4G, clients Xmx2G with four-chunk render distance and muted audio. Production RAM/config was not changed; normal user launcher profiles were not modified.

## C. Actual inventory

Current source: **57 server jars + 8 host-client files**, with three duplicated dependency files deduplicated in distribution. Test server has 59 jars including IP and instrumentation; **192 logical loaded mods** including nested mods/test driver. Actual client distribution: **54 files**; full client: **161 logical mods** including instrumentation. Jar counts and nested logical IDs are distinct.

Exact inventory: [phase6-current-mod-inventory.md](phase6-current-mod-inventory.md). Hashes/dependencies: `source-mod-inventory.json`; actual files: `distributed-paths.txt`; client runtime versions: `clientA-mods.json`.

| Integration | Version |
| --- | --- |
| Better Nether / BCLib / WorldWeaver (wover) | 26.301.2 |
| Naturalist / Trade Cycling | 2.0.6 / 1.0.23+26.3 |
| REI / Architectury / Cloth Config | 26.3.824 / 22.0.5 / 26.3.159 |
| Waystones / OPAC | 26.3.0.1 / 0.33.0 |
| Voice Chat | 2.6.24+26.3 |
| Xaero Minimap / World Map / XaeroLib | 26.6.0 / 1.47.0 / 1.7.17 |
| Graves / Polymer bundle | 3.13.0+26.3 / 0.18.4+26.3 |
| ChronoDawn / Chunky | 0.12.0 / 1.5.3 |
| Wizards / Archers | 3.1.4+26.3 |
| Paladins / Rogues | 3.1.4.1+26.3 |
| Jewelry / Spell Engine / Spell Power | 2.5.1 / 1.10.9 / 1.6.2 |
| Trinkets Updated | 4.2.1+26.3 |
| Local World Backups | metadata 1.2.0, filename world-backups-1.0.0.jar |
| AutoModpack | 5.0.0-rc.4 |

Sodium, Iris, Nvidium, Lithium and FerriteCore are absent. None was added. Current production removed the duplicate Fabric API 0.163 jar and JEI, and added REI/new worldgen/mob/trading mods independently of this workflow. Retained mezz_config was not cleaned up.

Observed server-only exclusions: Chunky, Gazebo, Graves, Ledger, Polymer, Vanish, Veinminer and World Backups. The test driver is additionally excluded only in the clone. IP is distributed. Host files include AmbientSounds, CreativeCore, REI, Xaero World Map, mezz_config and the duplicated Architectury/Cloth/Fabric API dependencies.

## D. Release candidate

`build/libs/immersive-portals-6.0.6-mc26.3-fabric-lasratingas-rc1.jar`

- SHA-256: `DC762CD70C1649DA31D1FB2FCE4882B5FEE9690BDA8FE619CE944017DD9B58C9`
- Size: **2,953,373 bytes**; byte-identical to the development jar tested in both current full clients/server.
- HEAD: `c5a83c77cb493b4fbab2663a6d4f0de004895ea8`, branch `26.3`.
- **Dirty working tree**, including uncommitted Phase 1–6 changes. No commit made; HEAD alone does not reproduce this build.
- Source snapshot, per-file hashes, working-tree patch/status and build instructions accompany the artifact in `build/phase6-release`. A second clean-machine byte-for-byte rebuild was not performed.
- Original mod ID `immersive_portals`, authors, attribution and Apache-2.0 license retained. DimLib is nested; Fabric API comes from the actual pack. Test instrumentation is separate and absent from the RC.

## E. Server startup — PASS

Complete current pack reaches ready state; Overworld, Nether, End and ChronoDawn load. No dependency conflict, duplicate-ID fatal, registry conflict or mixin fatal. Current baseline ready at 19:37:02; final run ready at 19:38:08. Approximately 26 seconds total startup, not merely vanilla's short world-preparation `Done` time. BCLib inspection rewrote zero chunks/files.

## F. Client startup/join — PASS, bootstrap caveat

Both full current-pack clients reach title, mount downloaded pack, join, render and interact. Final client JVMs exit 0. No full-pack dependency/mixin crash. Intermittent native bootstrap failure before IP loads is retained in L; first-attempt installation reliability is not certified.

## G. Core portals — PASS

Two simultaneous same-dimension views, real walking traversal, 90-degree rolled portal, Overworld ↔ Nether and Overworld ↔ ChronoDawn. Both clients return correctly; server records exactly one instance per UUID. Main-world rendering/tracking remain functional. Current `portal-*.json` and screenshots retain intermediate crossing states. No immediate corruption observed; this is not an exhaustive corruption or long-session proof.

## H. High-risk integrations

| Integration | Result |
| --- | --- |
| Waystones | **PASS**: native menu teleport with remote world loaded, then real portal crossing; correct positions/dimensions. |
| OPAC | **PASS after fix**: non-OP survival break/use denied through portal into claimed Nether chunk; owner break/chest UI allowed. Local controls pass. Server block state, denial feedback and callback destination recorded. Exception path restores all contexts. |
| Voice Chat | **PASS networking / PARTIAL audio**: both UDP clients authenticate/validate and remain functional across crossings. Positional/dimensional audio: **MANUAL TEST REQUIRED**. |
| Xaero | **PASS bounded switching**: HUD remains active, no crossing exceptions, separate current-profile `null`/`DIM-1`/`chronodawn$chronodawn` world-map caches. No exhaustive image comparison. |
| RPG/equipment | **PASS**: original serialized inventory, Fire Robe, Ruby Ring in real Trinkets slot and attributes retained through same/Nether/Chrono travel. Exact one-time ChronoDawn Chronicle Book grant separately expected. Normal spell use after travel damages controlled cow from 100 to 94 health. |
| Graves | **PASS after Polymer fixes**: controlled normal death creates visible grave with portal present; respawn and ordinary shift/use recover inventory and Trinkets. |
| World Backups / Restore Points | **PASS capture**: barrier, snapshot, independent hashes/NBT and saving restoration pass. No destructive restore or full archive/UI re-certification. |
| Chunky + Better Nether | **PASS bounded generation**: Nether center 4096,4096, square radius 16, exactly nine chunks complete with remote portal loading active. No trim/scale test. |
| ChronoDawn | **PASS**: remote render, both players enter/return. No production progression changed. |
| Better Nether / BCLib / Wover | **PASS portal surfaces**: blue obsidian tile wall visible through Nether portal; tiny Nether generation succeeds. REI/Wover recipe issue remains unresolved separately. |
| Naturalist | **PASS bounded entity rendering**: deer visible through same-dimension portal; client-world/packet mixins coexist in this run. Not every mob behavior tested. |
| Trade Cycling | **PASS startup/coexistence only**: audited mixin config targets merchant/villager access and container keyboard UI. No reproduced portal issue; reroll gameplay not tested. |
| REI | **PARTIAL feature coverage**: normal chest/menu item/search panel works; local recipe fallback errors, completeness not certified. No portal travel/rendering failure observed. |

Current restore point: `rp-20261019-194833-324385e2-503b-42b0-992d-83aa48c8e12f`, inside current clone's `Restore Points`.

**1,012 files / 3,244,706,878 bytes**, `VALIDATED`. Forced player/world save and chunk/POI barriers complete for all four dimensions at 19:48:33. Consistency capture lasts **4,267 ms**, restores all per-dimension saving flags at 19:48:37; async validation completes 19:48:49. Independent checker verifies every size/SHA-256, level.dat, 26.3 player/advancement paths, dimension regions, both complete portal entity NBT records and four readable global portal SavedData files. Evidence: `snapshot-hashes.json`, `snapshot-portal-validation.log`, `server-state.json`. Source-change validation and 30-second capture limit retained. Automatic backup interval remains 120 minutes; restore points remain manual-only.

## I. Two-client full-pack smoke — PASS

A crosses while B stays connected; B crosses; both return. Nether/Chrono records include intermediate states and one server UUID instance per player. Full Phase 2 concurrency matrix not repeated.

## J. AutoModpack — PASS with transient-download caveat

Fresh current profiles obtain 54 files via actual clone generation/transfer, normal cryptographic validation and review callbacks. Review of unknown custom jars is restricted to the authorized IP artifact. Server, distributed object and mounted client IP hashes match D. Instrumentation is pinned separately, not distributed. Retry completes verified transfers despite cache-lock errors; this does not certify AutoModpack itself as defect-free.

## K. Performance observations

Not a benchmark. A 2,161 ms / 43-tick overload warning occurs during controlled ChronoDawn fixture construction/loading. A second 4,228 ms / 84-tick warning coincides with the 4.267-second consistent snapshot capture; this pause is a practical backup limitation, not hidden by async validation timing. No repeating overload pattern outside these operations. Final active loaded counts: Overworld 1,225, Nether 961, ChronoDawn 1,993, including fixture loads; not a leak measurement. Background clients around 10 FPS are throttled and not portal performance evidence. MSPT comparison not collected.

## L. Log audit

Full current logs are retained in current evidence; original failure evidence remains in `build/phase6-inspection`.

| Finding | Classification/disposition |
| --- | --- |
| Polymer context, grave NPE, remote claim bypass | **Confirmed P1 defects, fixed**; M below. |
| REI `ClientRecipeFallback` → Wover `No biome bootstrap context` | **Unresolved new-pack error**, nonfatal observed. Both clients load zero local fallback recipe displays; item overlay works. Stack suggests REI local tag loading versus Wover bootstrap lifecycle and contains no IP frame, but causation/preexistence are not proven. No no-IP control or recipe-completeness claim. Per user direction, no repeated pack matrix for this non-portal feature. |
| AutoModpack HTTP `OverlappingFileLockException` | **Unresolved transfer warning** in SecretsStore cache locks during parallel requests; retries complete verified download. No IP frame, not proven pre-existing. No version/config workaround applied. |
| Bootstrap B native 0xC0000005 at font loading | **Unresolved bootstrap/platform issue**, occurs with vanilla + AutoModpack before IP loads. Serial retry succeeds; final full-pack sessions exit cleanly. No hs_err or established root cause. FPS change was not established as a fix. |
| Viewer-list probe immediately after teleport | **Expected harness ordering failure** before recipient chunk tracking settles. After settling, actual bridge probe passes. Final packet sample has 1,817 present contexts, zero missing. |
| Deliberately thrown block callback | **Expected regression instrumentation**, proves finally restoration. |
| Optional Create/FieldGuide classes, refmaps, OPAC disabled temporary mixins | **Expected optional warnings**, no fatal initialization failure. |
| Fresh malilib config missing, optional attribute/icon warnings, offline Realms auth | **Nonfatal setup/content warnings**, not demonstrated IP defects. |
| Early interface-injected client jar, probe packaging/config activation, malformed disposable pinned-mod line | **Harness/build setup defects corrected** before final run, not silently accepted as successes. |

No final fatal registry/mixin failure, unknown-packet failure, invalid-move correction loop, duplicate-player error or failed snapshot validation observed. Finite log inspection is not proof of absence of all leaks.

## M. Defects and exact runtime changes

**P1: Polymer packet recipient/tracking.** Original IP copied vanilla's private single-chunk send, bypassing Polymer hooks. Unchanged Phase 5 reproduction: 81 missing contexts, zero present. `PlayerChunkLoading.sendChunkPacket` now calls actual vanilla `PlayerChunkSender.sendChunk` through new `IEPlayerChunkSender`, inside recipient Fabric PacketContext and existing dimension redirection. This preserves Polymer construction/tail virtual-entity tracking and vanilla debug hooks. Existing Fabric attachment sync is retained once because its injection wraps the outer batch caller IP cancels. Invoker registered in `imm_ptl.mixins.json`. Final current probe: **1,817 present / 0 missing**, both UUIDs, Overworld/Nether/ChronoDawn.

**P1: Polymer 26.3 IP bridge stubs.** Virtual bridge returns null/false and lacks redirection; core viewer overloads throw `Not implemented!`. Real normal death reproduced grave NPE after inventory removal. Optional new `MixinPolymerImmersivePortalsUtils` and `MixinPolymerCoreImmersivePortalsUtils` implement viewer lists, watch predicate and finally-safe redirection. New `imm_ptl_polymer.mixins.json` is registered, plugin-gated on Polymer virtual entity; `build.gradle` explicitly retains only these ported classes instead of enabling the dormant unported compatibility config. No Polymer hard dependency. Regression calls both core overloads and virtual bridge, injects redirected exception, then tests normal death, respawn, visible grave and recovery. Relevant vanilla-only core sanity passes without Polymer.

**P1: OPAC remote breaking bypass.** Non-OP player could break protected Nether block from Overworld through portal; actual AIR server state reproduced. Fabric callbacks directly read game-mode level outside IP's vanilla field redirects; initial attack callback could also skip because of untransformed range. `MixinServerPlayerGameMode` wraps block-break handling and deferred break tick, assigning actual target world and restoring in finally. `BlockManipulationServer` checks world bounds and calls AttackBlockCallback with destination only when Fabric's vanilla-range guard would skip it, avoiding duplicate callbacks; denial acknowledges/resends block and block-entity state under redirection. Retests prove remote/local denied and owner allowed. Recorded callback world is Nether while player remains Overworld. Exception regression confirms game-mode level, interaction context and packet redirection restore.

No other mod's gameplay configuration, protection or version was weakened/changed.

## N. Build and tests run

Use Java from `E:\MinecraftServer`, Gradle wrapper 9.6.0, explicit actual API override. Successful build and narrow-fix logs are preserved; final relevant core regression: **BUILD SUCCESSFUL in 44 s**. No runtime changes after tested candidate.

```powershell
# JAVA_HOME=E:\MinecraftServer; prepend its bin to PATH
.\gradlew.bat phase6DriverJar assemble '-Pfabric_version=0.164.0+26.3' --no-daemon --console=plain
.\gradlew.bat assemble '-Pfabric_version=0.164.0+26.3' --console=plain
.\gradlew.bat runClientGametest '-Pfabric_version=0.164.0+26.3' '-Pclient_test=core' '-Pclient_run_dir=build/phase6/core-regression' '-Penable_sodium=false' '-Penable_iris=false' --no-daemon --console=plain
```

Executed harness entry points, from E:\ImmersivePortalsMod (not instructions to overwrite existing clones):

```powershell
& tools/phase6-setup.ps1 -CloneRoot 'E:\MinecraftServer-ImmersivePortals-Test-Current' -EvidenceRoot 'E:\ImmersivePortalsMod\build\phase6-current-inspection'
& tools/phase6-current-isolate.ps1
& tools/phase6-server.ps1 -Run current-fullpack -Clone 'E:\MinecraftServer-ImmersivePortals-Test-Current'
& tools/phase6-clients.ps1 -Clone 'E:\MinecraftServer-ImmersivePortals-Test-Current' -ProfileRoot 'E:\ImmersivePortalsMod\build\phase6\current'
& tools/phase6-client-run.ps1 -Role A -Run current-fullpack-fixed-profile
& tools/phase6-client-run.ps1 -Role B -Run current-fullpack
& tools/phase6-portals.ps1
& tools/phase6-verify-snapshot.ps1 -Id 'rp-20261019-194833-324385e2-503b-42b0-992d-83aa48c8e12f' -Clone 'E:\MinecraftServer-ImmersivePortals-Test-Current' -EvidenceRoot build/phase6-current-inspection
```

Bootstrap precedes full-client runs. Real native Waystones buttons, walking, attack/use keys, spell, death/respawn/grave use, OPAC claim/interaction, `polymer-virtual-check`, `break-finally-check`, tiny Chunky and `restorepoint create phase6-current-pack` exercised through separate test drivers. Owned server stdin/Fabric actions used, not RCON. Test state/blocks only changed in marked clone. Exact assertions and screenshots are retained; command ACK alone was not treated as proof of successful Minecraft behavior.

## O. Tests deliberately not run

No full Phase 1–5 suite, nine-camera matrix, renderer stress, huge generation, destructive restore, production deployment, unrelated upgrades or added renderer mods. No exhaustive recipes, trading gameplay, mobs/spells, audio, long-session leak test or clean-machine reproducibility run. No claim of 30,000-chunk repair readiness. Latest user direction honored: retain passed results and restrict new-mod checks to relevant portal surfaces.

## P. Production safety

**No Phase 6 writes to E:\MinecraftServer.** Current source 1,226/1,226 hashes and world file set match. Independent morning-to-evening production changes are expressly distinguished. No production IP jar, restart, AutoModpack generation, firewall/No-IP action, backup deletion or restore. Both final clients and current server exit 0 after graceful shutdown. Clones/snapshots preserved.

## Q. Certification status

**PASS — suitable for controlled production deployment preparation**, for the specified bounded portal integration. No remaining observed P0/P1 portal incompatibility after the three narrow fixes. This is not blanket assurance for REI recipes, bootstrap reliability or all gameplay. L/R retain unresolved observations rather than labeling them harmless or proven pre-existing.

## R. Remaining risks

REI/Wover recipe completeness and bootstrap/cache-lock behavior need separate targeted investigation if users are affected. Positional/dimensional voice audio needs human listening. Finite two-client coverage does not establish every Polymer lifecycle edge, arbitrary packet consumer, every mob/spell or infinite-session stability. Certification applies to captured inventory/hashes, not future modpack changes or different normal client profiles. Earlier renderer phases do not automatically certify adding Sodium/Iris/Nvidium to this full pack.

## S. Next step

**PHASE 7 — CONTROLLED PRODUCTION DEPLOYMENT PREPARATION**

Recommended only; not started. Candidate remains local, not installed in production.
