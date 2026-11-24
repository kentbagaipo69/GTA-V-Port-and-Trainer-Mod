# Trainer disappearing at game startup — 0.9.7

The reported symptom was the floating trainer disappearing while the game continues.

## Cause found

The original launcher's `LauncherActivity$97.run()` starts the game activity, finishes the launcher, and posts a delayed task. After 1,500 ms, `LauncherActivity$97$1.run()` calls `Process.killProcess(Process.myPid())` unless the original download service is running.

Earlier bundled builds hosted the overlay service in that launcher process. The original launcher's intentional process shutdown could therefore kill the trainer during the transition into the separate `:game` process. This fits the reported disappearance; no crash log from the phone was available to rule out additional causes.

## Fix

The overlay service now runs in a dedicated `:trainer` process within the same APK. The game continues using `:game`. The launcher's shutdown no longer terminates the overlay's host process.

The game activity still owns the service binding. Leaving the game releases it, removes the overlay and stops the service. This change does not add background polling or change the game's original shutdown logic. The trainer remains private to the app. Separate APKs are not required for this fix.

## Verification

- Built a separate, disposable regression-test app using the actual trainer service and lifecycle code. Its launcher starts a synthetic game activity in another process, then kills its own process after the original 1.5-second delay.
- After that shutdown, the launcher PID was absent, the synthetic game and trainer processes remained alive, and the overlay window and foreground service remained present with a binding owned by the game activity.
- Pressing Home removed the test overlay service. The disposable test app was uninstalled afterward.
- Final APK signature, alignment and integrity checks passed. It installs as an update with version code 21.
- Final-build lifecycle tests passed for startup, Home, switching apps, screen-off, return/wake, force-close, and direct launcher restoration. The emulator was left on Home with no overlay service or window.
- All 284 game, native bridge, resource and catalogue entries match 0.9.6 byte-for-byte. Existing features remain included.

The handoff regression is synthetic: it does not load the game engine. Full gameplay on the affected phone still needs verification because complete game data is unavailable here.

## Install

Install `GTAV-Trainer-Bundled-0.9.7-experimental.apk` over the existing patched game, retaining its data. Open the game normally with overlay permission enabled. The bubble should remain available when gameplay starts. No separate trainer installation is needed.
