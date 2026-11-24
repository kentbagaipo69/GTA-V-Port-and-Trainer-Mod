# Crash and background-work audit — 0.9.6

Audited the latest 0.9.5 bundled APK, its added Java components and native bridge, available emulator crash logs, and Android service/window/power state. This release hardens failure paths; it does not claim the original game is crash-free or measure physical-phone battery savings.

## Issues addressed

1. **Responses could outlive their intended timeout.** Socket timeouts applied separately to every byte. A slow peer could keep the worker occupied much longer than 2.5 seconds for a status request or 15 seconds for an action response. Reads now share a monotonic total response deadline. Connection establishment remains separately limited to 1.5 seconds. Commands are still sent at most once and uncertain results are never automatically retried.
2. **A hidden trainer could retain an in-flight status socket.** Removing the final panel subscriber stopped future polls but did not close a currently blocked read-only probe. Cleanup now cancels that probe and closes its socket. Already-sent game-changing commands retain their bounded confirmation wait; cancelling their reads would not undo the action. A late result no longer marks a client with no visible subscribers as ready.
3. **Overlay failure cleanup depended on window operations succeeding.** A keyboard/window cleanup exception could interrupt removal, and recreating the bubble from the close action lacked its own error guard. Cleanup now unsubscribes the panel first, clears its window reference before removal, and handles keyboard/window failures independently. Close-to-bubble handles window recreation failure without allowing that exception to escape its click handler. This is defensive hardening for permission/window races, not a reproduced device-specific crash diagnosis.

## Features preserved

All 284 existing game/bridge/resource/catalog entries were compared with 0.9.5 and are byte-identical, including the native bridge, original game DEX and catalogues. Character/animal models, vehicles, weapons, movement, clothing, money, teleport, world controls, limited mission-skip behavior, performance presets, automatic startup and foreground-only overlay behavior remain included. No exporter or mod importer was added.

The bridge's per-frame effect limit remains in place. The native server waits on blocking sockets instead of spinning while idle. The added trainer has no wake lock, alarm/job scheduling or repeating background app-monitoring loop. Its periodic status poll is tied to panel subscriptions.

## Verification

- Three new transport regressions passed: slow probe and action replies timed out at approximately 0.51 seconds with a 0.5-second test budget; cancelling a read-only probe closed the socket and released the worker promptly.
- All 17 existing transport scenarios passed, covering identity/pairing checks, no duplicate mutation, malformed/truncated replies and representative existing commands.
- APK signing, alignment, archive integrity and original-game preservation checks passed. Updated APK uses version code 20.
- Updated-build lifecycle tests passed for Home, app switching, screen-off, wake/return, force-close and direct launcher restoration. Both service records and overlay windows were checked. The test was adjusted to wait for asynchronous service startup rather than asserting between two mismatched snapshots.
- Revoking overlay permission during startup triggered a caught `BadTokenException`, not an uncaught app crash. After Home, no overlay service remained and Android reported zero held wake locks. Permission was restored after testing.
- The pre-update background snapshot contained no active overlay service and Android reported zero held wake locks. Historical audio/launch wake-lock records were present; historical records are not evidence of a currently held lock.
- No current game/trainer crash appeared in the inspected crash buffer. The historical Bluetooth-process crash was unrelated and was not treated as an app finding.

## Limits

Complete game data and a physical phone were unavailable. Native gameplay crashes, long-session memory behavior, GPU load and actual battery drain cannot be ruled out by this audit. Android may keep a stopped app process cached; that is different from a running overlay foreground service. This update does not forcibly terminate the original game process or remove its existing services/features.
