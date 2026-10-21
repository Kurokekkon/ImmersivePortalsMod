# NVIDIA / Sodium / Nvidium compatibility audit

**Phase 4 update (2026-10-19):** the Phase 3 runtime reproduced missing geometry with active
Nvidium. A narrowly scoped Nvidium temporal-visibility adapter now passes the deterministic
same-world/multi-camera/cross-world/roll/near-plane checks and a 180.8-second stress run with
nine crossings. This validates Nvidium 0.4.4-beta7-26.3 + Sodium 0.9.2 on the tested RTX 3060 /
NVIDIA 617.42 / OpenGL environment, not universal support or long-session driver safety.
See [the Phase 4 report](phase4-nvidium-compatibility.md) for failures, fix, exact manifest,
resource observations and focused regressions. Production remains untouched.

Historical audit before stabilization Phase 3. Same-dimension Sodium rendering has since been restored
**without Nvidium**; see [the Phase 3 report](phase3-sodium-multicamera.md). The Nvidium combination has
now been tested separately with the Phase 4 adapter. Findings below describe the earlier test state.

Checked 2026-10-19 for Minecraft 26.3, Fabric Loader 0.19.5, Java 25 and Fabric API
0.163.0+26.3. All execution used generated disposable worlds under this repository's build
tree. Production, client packs and committed dependencies were not changed.

## Warning identification

The observed Immersive Portals warning identifies **NVIDIA hardware**, not the **Nvidium mod**.
`IPModMainClient.showNvidiaVideoCardWarning()` checks the graphics vendor and Sodium's absence;
it does not check for Nvidium. The English text is `imm_ptl.nvidia_warning`. The Phase 1 core
and dedicated logs contain this warning but their mod lists contain neither Sodium nor Nvidium.
The warning links to the historical [Sodium NVIDIA driver issue #1486](https://github.com/CaffeineMC/sodium/issues/1486).
It is not evidence of a reproduced crash with the present driver or of Nvidium being installed.

## Exact version compatibility

The current [official Nvidium 26.3 release](https://modrinth.com/mod/nvidium/version/0.4.4-beta7-26.3)
is `0.4.4-beta7-26.3` (Modrinth ID `8mVK1zbk`, jar commit `27ceb72`). Its downloaded
`fabric.mod.json` declares client environment, Minecraft 26.3, Loader >=0.18.4 and Sodium
**exactly 0.9.2**. Modrinth's required dependency `bAZQdGpg` resolves to
`mc26.3-0.9.2-fabric`. Both the API response and actual jar metadata were inspected.

| Combination | Finding |
| --- | --- |
| Fork default Sodium 0.9.3-alpha.1 + current Nvidium | BLOCKED by Nvidium's declared Sodium 0.9.2 requirement; not launched with dependency overrides |
| Sodium 0.9.2 + Nvidium 0.4.4-beta7-26.3 | Matching declared versions; tested below |
| Feature-complete production approval | Original audit did not approve it. Phase 4 now validates the pinned 0.9.2 pair only on the tested NVIDIA/OpenGL environment; full production-pack/long-session approval remains outside these tests |

No version checks, mod metadata, driver workarounds or portal safeguards were bypassed.
The Sodium 0.9.2 selection was a temporary Gradle property override for this audit only.
Nvidium was copied only into `build/clientgametest/mods` for testing and removed afterward.
The inspected jar is retained outside that load directory in `build/compatibility-audit`.

## Runtime evidence

Test hardware: RTX 3060, NVIDIA driver 617.42, Windows, OpenGL. Iris/shader packs absent.
The logs identify both exact mod versions and `Nvidium: Enabling Nvidium` after all capabilities
were met. This was an active-renderer test, not a jar-presence-only check. Sodium reported its
`NVIDIA_THREADED_OPTIMIZATIONS_BROKEN` workaround active; it was left enabled.

```text
gradlew.bat runClientGametest -Pfabric_version=0.163.0+26.3 -Pclient_test=core -Penable_sodium=true -Psodium_path=maven.modrinth:sodium:mc26.3-0.9.2-fabric --console=plain
gradlew.bat runClientGametest -Pfabric_version=0.163.0+26.3 -Pclient_test=dedicated -Penable_sodium=true -Psodium_path=maven.modrinth:sodium:mc26.3-0.9.2-fabric --console=plain
```

- Core OpenGL test: PASS. Cross-dimension view checks, including gold-wall pixels near the
  portal plane, traversal, vanilla dimension changes and disconnect cleanup completed.
- Same-dimension rendering: **known unsupported with Sodium**, not a pass. The existing core
  test deliberately asserts that no such view is drawn. The runtime warning confirms this.
- Dedicated lifecycle: PASS for all 16 executed scenario checks plus the suite aggregate;
  two-real-client scenario SKIPPED. Real TCP login, both travel directions, reconnect,
  same-world restart, ordinary/global portal persistence, chest-boat/player inventory,
  portal ticket release and disconnect/stop cleanup passed. JUnit: 18 total, 0 failures,
  1 skipped. See `nvidium-sodium-dedicated.log` and `nvidium-dedicated.xml`.

Evidence is retained in `build/compatibility-audit/nvidium-sodium-core.log`,
`nvidium-sodium-dedicated.log`, `nvidium-version.json` and `nvidium-fabric.mod.json`.

## Production decision

The matched pair passing bounded tests does not establish long-session driver stability,
two-real-client tracking, compatibility with the full client pack, Iris shader packs or Vulkan.
Nvidium's own [project page](https://modrinth.com/mod/nvidium) cautions that its uncommon mesh-shader
technology may terminate Minecraft unexpectedly. Nvidium is a client renderer, not a server mod.

The original audit recommended the tested vanilla path because of the then-existing Sodium
same-dimension gap. Phase 3 supersedes that Sodium finding; Phase 4 adds bounded
Nvidium evidence, without universal driver or production-pack certification.
Do not ship Nvidium with Sodium 0.9.3-alpha.1 by ignoring
its version constraint. The original audit made no runtime fix; Phase 4's adapter is a
separate tested change. Neither phase installed anything into production.
