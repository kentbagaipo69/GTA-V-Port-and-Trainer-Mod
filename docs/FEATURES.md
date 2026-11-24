# Trainer Lab for Android

**Version 0.9.7 · Experimental bundled build**
**Custom trainer and patched build author: Kent Vincent Bagaipo**

Trainer Lab adds a floating trainer to the Android game package `com.thegwchr.fuzzy`. The game, trainer interface and connection bridge are bundled into one APK. It is intended for offline, single-player use.

> This is an experimental build. Installation and trainer startup have been tested in an Android Studio emulator; full gameplay with complete game data has not been verified. Listed controls are implemented, but their results depend on the supported game build and available assets.

## Features

| Area | Included controls |
| --- | --- |
| Performance | Low, Medium and High presets using the game's existing resolution, FPS, graphics, texture and VSync settings. RAM-based starting suggestion and restore-previous option. Apply from **Game performance**, then relaunch the game. |
| Floating overlay | Draggable bubble, expandable controls, adjustable opacity (50–100%), panel size (80–130%) and bubble size (40–96 dp), with saved preferences. |
| Characters and animals | Searchable catalogue of 1,109 player models, including NPCs, animals, Michael, Franklin and Trevor. Changes your own character's model. |
| Vehicles | Searchable catalogue of 202 vehicles, including cars, motorcycles, planes, helicopters, blimps, boats and submarines. Spawn, repair, wash, place on ground and toggle the engine. |
| Player | God mode, health and armor refill, invisibility, wanted-level controls, no ragdoll, super jump, fast run and fast swim (1.49×). A control turns off trainer effects. |
| Weapons | Catalogue of 101 weapons, weapon grants, unlimited-ammo toggle, no-reload toggle and remove-all-weapons action. |
| Clothes and accessories | Inspect available drawable and texture variants for the current model across 12 clothing component slots and 13 prop slots. Apply clothing, change or remove supported accessories, and randomize an outfit. |
| Teleport | Choose from 24 named destinations, remember your current position and return to it. |
| Money | Check story-character balances, add money to Michael, Franklin or Trevor, and enable a cash-refill floor of one billion. Cash refill is not a literally unlimited balance. |
| World | Weather selection, time selection, paused clock and traffic-spawning toggle. |
| Connection | Bridge status and command feedback, including errors when a command cannot run. |
| Mission | Request the game's existing whole-mission skip prompt when an eligible mission-failure screen offers it. See the limitations below. |

Catalogue entries do not guarantee that a model exists in your installed game data. Clothing and accessories vary by character model. Spawn aircraft in open space and watercraft on suitable water.

## Requirements

- Android 11 or later on an ARM64 device.
- A compatible Vulkan 1.3 graphics driver. Low settings cannot make unsupported GPU hardware compatible.
- Compatible game data for this game build. The APK alone does not provide a complete game-data installation.
- Permission to display over other apps for the floating overlay.
- An Android launcher that supports app shortcuts to access the bundled trainer.

## Install and open the trainer

1. Back up your saves and game data before testing an experimental build.
2. Install `GTAV-Trainer-Bundled-0.9.7-experimental.apk`. It can update a compatible earlier patched build signed with the same certificate. If Android reports a signature conflict, preserve your saves before considering any uninstall.
3. Open the game. On first use, choose **Enable overlay** and grant **Display over other apps** to **Grand Theft Auto V**. Return to the app; the game launcher opens with the floating trainer bubble. You can choose **Continue without** to launch without the overlay.
4. Tap the floating bubble to open the controls. Opening or returning to the game automatically starts the bubble when overlay permission is enabled. Leaving for Home, another app or screen-off removes the overlay and stops its service.
5. For performance presets or manual overlay controls, long-press the game icon and choose **Trainer**. The overlay stays off on this settings screen; **Start overlay** opens the game.
6. Open the game, load an offline session and use the floating bubble to expand the trainer.

Drag the bubble to reposition it. Both **×** and **−** close the controls back into the bubble; tap it to reopen. Use **⋯** to adjust opacity and size or choose **Stop overlay** to remove the bubble completely. Stop is also available in the trainer screen and notification. Returning to the game starts it again.

The standalone Trainer Lab APK is no longer required. Stop its overlay if it is still installed to avoid duplicate bubbles. Its settings are not automatically migrated to the bundled trainer.

The mission-data exporter and automatic startup scan are removed from this release.

## Mission skip: what it can do

The mission control requests a built-in skip prompt only when the game is already offering a **whole-mission skip** on a mission-failure screen. You must confirm the prompt in the game.

It does not force-pass arbitrary missions, skip a mission during ordinary gameplay, or substitute a section-only skip for a whole-mission skip. A successful command response means the input was sent; it does not prove the mission completed. Rewards, save progression and actual mission completion remain unverified in full gameplay.

## Mod importing: planned, not included

The goal is to select compatible downloaded mod files through the trainer and install them into the correct game-data location, with backup and restore support. **Version 0.9.7 does not include an RPF or mod importer.**

Support for every mod from [GTA5-Mods](https://www.gta5-mods.com/) cannot be promised. Copying an RPF file does not establish compatibility: a mod may need registration, matching assets, a particular game version or a separate script loader. PC ASI/DLL plugins are not directly loadable Android plugins.

For example, the author of the [GTA V Add-On Car Helper](https://github.com/Jayydoodle/GTA5AddOnCarHelper) documents both copying DLC packs and updating DLC list entries. An Android importer must account for the supported game's loading process as well as the files it copies.

Future support needs testing by mod type and game build. There is no verified compatible-mod list for this release yet.

## Testing status and limitations

- Version 0.9.2 removes redundant effect updates within a game frame to reduce avoidable trainer CPU work. Temperature improvements on physical devices have not been measured.
- APK building, signing, alignment and emulator installation were checked.
- Normal game-launch routing and the bundled trainer shortcut route were checked.
- Bundled overlay service startup was checked in the emulator.
- Native command tests exercised 1,523 catalogue selections using simulated game-native responses. These tests do not demonstrate live gameplay behavior.
- Full gameplay, mission progression, performance improvements and compatibility with third-party mods have not been verified.

The added Java code uses shrinking and obfuscation, and the custom launcher checks its signing certificate. These measures do not make an APK impossible to modify or copy. The current experimental APK retains a development signing certificate for compatibility with earlier patched builds; a public production release needs a secured release-signing setup.

## Reporting a problem

Include your device model, Android version, Trainer Lab version, game-data version if known, the selected control and exact steps to reproduce the issue. Include the displayed error or a relevant crash log when available. Remove account information and other personal data from logs before sharing them.

## Credits

Custom trainer and patched build: **Kent Vincent Bagaipo**.

Grand Theft Auto V and its original game content belong to their respective rights holders, including Rockstar Games. The original Android port and third-party mods retain their authors' credits and applicable terms. This project is unofficial and is not endorsed by Rockstar Games. The custom-build credit does not claim authorship of the original game, port or third-party content.
