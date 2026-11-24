# Trainer Lab — public source snapshot

Custom trainer and integration work by **Kent Vincent Bagaipo**. Snapshot: **0.9.7 experimental**.

This repository contains the custom Android trainer UI, overlay lifecycle integration, native bridge implementation and selected tests. The overlay runs in a separate `:trainer` process so the original launcher's intentional process shutdown does not terminate it. Foreground activity bindings control its lifetime.

See [features and requirements](docs/FEATURES.md), [startup fix](docs/STARTUP-FIX-0.9.7.md) and [audit](docs/AUDIT-0.9.6.md). Those documents describe the experimental private APK; this source publication does **not** distribute that APK.

## Included

- `trainer/`: custom Java interface, overlay, transport, wardrobe and performance settings; custom vector icon and standalone manifest.
- `integration/`: custom launcher/lifecycle classes and manifest fragment for integration with a compatible game build.
- `bridge/`: custom bridge and command-handling implementation.
- `tests/`: transport, timeout, wardrobe and performance-profile checks.

## Deliberately excluded

Signing keys/keystores, signing properties, pairing secrets, private packaging/build files, APKs, native binaries, original game DEX/source, decompiled mission scripts, RPF archives, original game graphics/audio/data, third-party catalogue dumps, generated private configuration and original native instruction fingerprints are not included.

This is a **source snapshot, not a turnkey build of the game or trainer**. Runtime catalogue JSON files are omitted. The bridge additionally requires locally generated `bridge_config.h`, `native_guards.h`, `private_hook_profile.h`, `vehicle_spawn.h` and `destinations.h`; the game-specific fingerprints must come from a legally obtained compatible build. Existing hard-coded offsets are specific to the supported experimental port and must not be assumed valid for other builds.

Copy the `.java.example` files to the corresponding `.java` names only in your private workspace. Generate a new random pairing secret for both client and bridge; do not reuse a published application's secret. Configure your own signing identity. The empty certificate configuration fails closed. Generated configuration is ignored by Git. Private release packaging scripts are not published.

`tests/BridgeWireTest.java` and `tests/BridgeDeadlineTest.java` can be compiled with `trainer/src/dev/gtav/trainer/BridgeWire.java` using JDK 17 and run as `dev.gtav.trainer.BridgeWireTest` and `dev.gtav.trainer.BridgeDeadlineTest`. The performance-profile test likewise uses `PerformanceProfile.java`. These tests do not invoke the game engine.

## Status and credits

Experimental, offline single-player integration. Full gameplay on physical devices remains unverified. The APK requirements are Android 11+, ARM64, compatible Vulkan 1.3 graphics and compatible game data. Universal device/mod compatibility is not claimed.

Original GTA V content, the original Android port and third-party work retain their respective authors' credits and rights. This project is unofficial and not endorsed by Rockstar Games. Public source visibility does not grant rights to distribute the original game or imply a new license for third-party content. No open-source license is assigned by this publication.
