# Immersive Portals — Minecraft 26.3 Fabric Port

An unofficial port and stabilization fork of Immersive Portals 6.0.6 for Minecraft Java 26.3. See through portals and travel between dimensions seamlessly, with fixes for multiplayer, renderer compatibility, protection mods and positional voice chat.

Based on the 26.3 port from [CaseBacon/ImmersivePortalsMod](https://github.com/CaseBacon/ImmersivePortalsMod), originally developed by [qouteall and iPortalTeam](https://github.com/iPortalTeam/ImmersivePortalsMod). Original authorship, notices and Apache-2.0 licensing are retained. This is not an official upstream release.

## Download

[**Download RC2 for Minecraft 26.3 (Fabric)**](https://github.com/Kurokekkon/ImmersivePortalsMod/raw/refs/heads/26.3/downloads/immersive-portals-6.0.6-mc26.3-fabric-rc2.jar)

Filename: `immersive-portals-6.0.6-mc26.3-fabric-rc2.jar` — 2,955,099 bytes. Install this jar on both the server and clients, replacing the previous Immersive Portals jar.

This is the tested RC2 binary under a neutral filename; its contents are unchanged. [SHA-256 checksum](downloads/SHA256SUMS.txt).

## Requirements and installation

- Minecraft Java **26.3** and **Java 25**.
- Fabric Loader **0.19.5 or newer** and compatible Fabric API. The RC2 targeted tests used Fabric API **0.164.0+26.3**.
- Install matching Immersive Portals builds on **both clients and server**. Keep only one Immersive Portals jar in each `mods` folder.
- DimLib is included in the built jar. Optional compatibility mods are not bundled.

## Fixes and improvements

### Core port and networking

- Adapted client/server integration, persistence, packet handling and rendering to Minecraft 26.3.
- Rebuilt portal views using the newer rendering APIs, with destination clipping and shaped/rotated portal surfaces.
- Restored remote-world lighting updates, chunk tracking and renderer resizing.
- Preserved ordering between ordinary and dimension-redirected packets.
- Replaced reflection-by-name remote calls with registered allowlisted calls and typed codecs.
- Retained migration from the older global-portal data location.

### Travel and multiplayer

- Closed a mounted-player bypass of portal teleport validation.
- Reject invalid coordinates and unknown dimensions; resynchronize rejected travel safely.
- Fixed mounted transfers triggering unintended dismount teleport corrections.
- Fixed facing direction after traversal through rolled portals.
- Validated two-client crossings, reconnects, chest-boat travel, portal persistence across server restart and owned loading-resource cleanup.

### Terrain loading

- Replaced FPS-based client loading adjustments with memory-based adjustments, so shaders or an FPS cap no longer unnecessarily shrink the terrain loaded through portals.
- Report loading capacity immediately after joining.
- Fixed chunk tickets being classified as failed before Minecraft applied them.
- Existing configurable loading-radius limits remain in effect.

### Sodium

- Restored same-dimension and multiple-camera portal views through sequential extraction/drawing and restoration of the main view's state.
- Fixed unsafe shared terrain-uniform buffer rotation and gave portal views their own cloud buffers.
- Tested Sodium **0.9.2** and **0.9.3-alpha.1**; see the renderer reports for backend-specific coverage.

### Nvidium

- Fixed missing terrain caused by visibility history being shared between main and portal cameras.
- Added an optional adapter that resets the affected visibility history when switching cameras, while keeping Nvidium active.
- Validated **Nvidium 0.4.4-beta7-26.3 + Sodium 0.9.2** on the tested NVIDIA/OpenGL environment. This is not blanket GPU/driver certification.

### Iris and shaders

- Restored active-shader same-dimension and multiple-camera portal views using bounded camera-owned shader pipelines.
- Isolated uniforms, previous-camera state, temporal textures, shadow resources and render targets.
- Fixed portal extraction overwriting main-view fog/lighting scratch state.
- Added owned pipeline and shader-wrapper cleanup on reload, expiry and disconnect.
- Tested **Iris 1.11.6 + Complementary Reimagined r5.9.3**, with a smaller sanity set for **Complementary Unbound r5.9.3**. Other packs are not certified.

### Polymer and protection compatibility

- Restored Polymer recipient context and virtual-entity tracking during remote chunk delivery.
- Implemented missing Polymer–Immersive Portals bridge behavior.
- Fixed the reproduced grave-creation exception in the tested Polymer-based grave integration; validated death, respawn, grave visibility and recovery.
- Fixed remote block breaking bypassing Open Parties and Claims protection.
- Run protection callbacks in the destination world and restore temporary state even on exceptions.

### Simple Voice Chat — RC2

- Fixed portal-dependent dropped audio when the audio worker searched for a speaker in the temporary dimension used for portal rendering.
- Speaker lookup now uses the local player's physical world.
- This compatibility patch lives **inside Immersive Portals**. The Simple Voice Chat jar is unchanged, and no custom Voice Chat build is needed.
- Tested with **Simple Voice Chat 2.6.24**: 400 controlled post-fix speaker lookups succeeded across first person, third person and traversal scenarios.
- A two-player playtest reported clear audio with no recurrence of the portal-related dropouts. Broader multiplayer testing remains limited.

## Current limitations

- One portal rendering layer; at most two visible portal views per frame.
- Mirrors and recursive portal rendering are not supported by the current port renderer.
- Far-separated same-dimension views have a known vanilla-renderer limitation.
- Translucent objects in front of a portal can composite incorrectly.
- Some older integrations/features remain quarantined; this is not complete feature parity with upstream.
- A reported visual seam could not be reproduced and has not recurred in the latest playtest. Its root cause remains unconfirmed.
- Compatibility applies to documented versions and test environments. Long sessions, arbitrary modpacks and every GPU/backend combination are not certified.

## Building

See [building-rc2.md](docs/building-rc2.md) for the tested build command and integration-harness limitations.

## Testing and documentation

Tests used isolated worlds and server copies, including real dedicated-server/client processes, persistence, mounted travel, rendering and targeted mod compatibility. Test drivers are not packaged in the release jar.

- [Dedicated lifecycle](docs/phase1-dedicated-lifecycle.md)
- [Two-client multiplayer](docs/phase2-multiprocess.md)
- [Sodium](docs/phase3-sodium-multicamera.md)
- [Nvidium](docs/phase4-nvidium-compatibility.md)
- [Iris shaders](docs/phase5-iris-active-shaders.md)
- [Modpack integration](docs/phase6-modpack-compatibility.md)
- [RC2 voice fix and seam investigation](docs/phase81-targeted-client-compatibility.md)

Reports describe results at their phase's completion; later fixes and playtest feedback supersede earlier limitations only where explicitly documented.

## Credits and license

Original Immersive Portals: qouteall, iPortalTeam and upstream contributors. Base 26.3 port: CaseBacon and contributors. See source history and retained notices for attribution.

Licensed under [Apache License 2.0](LICENSE). This fork is independently maintained; please report fork-specific problems to this repository rather than assuming upstream supports this build.
