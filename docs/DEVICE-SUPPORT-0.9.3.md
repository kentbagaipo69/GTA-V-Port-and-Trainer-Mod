# Device support and performance presets — 0.9.3

This release adds Low, Medium and High performance presets to the bundled trainer. It does **not** make the supplied game compatible with every Android device.

Open the game once, long-press its icon, choose **Trainer**, then tap **Game performance**. Save and exit gameplay before changing a preset. Select a preset, tap **Apply preset**, and relaunch the game. **Restore previous** restores the five launcher settings saved before the first preset was applied, including whether each setting was originally unset.

| Preset | Maximum long edge of render resolution | FPS target | Graphics preset | Textures |
| --- | --- | --- | --- | --- |
| Low | 960 pixels | 30 | Low | Normal |
| Medium | 1,280 pixels | 30 | Game default | Normal |
| High | 1,600 pixels | 60 | Game default | High |

Resolutions preserve the screen's aspect ratio to within rounding to even pixels, and do not upscale beyond the physical display. All presets enable VSync. High deliberately avoids the port's Ultra preset; it still increases GPU load and can increase heat. FPS values are targets, not measured or guaranteed frame rates.

The initial suggestion uses Android's low-RAM flag and total physical RAM: low-RAM devices or up to 4 GiB suggest Low; up to 8 GiB suggests Medium; larger memory suggests High. This is a starting heuristic, not GPU detection, a benchmark, or a minimum-RAM guarantee. Select Low if a higher preset performs poorly. No preset is applied automatically.

## What these settings actually change

The original launcher stores string settings in its `launcher` preferences. Inspection of its DEX confirms that `settingsArgs` reads `resolution`, `fpsTarget`, `quality`, `textureQuality`, and `vsync`, translating them into the game's existing width/height, `androidFpsTarget`, graphics-quality, texture-quality and VSync options. The new selector writes those same settings; it does not introduce an unrelated display-refresh hint.

Existing custom launch arguments can override resolution and texture choices. The original launcher's low-RAM logic can also force normal textures. Driver selections, paths, saves, custom graphics entries and unrelated launcher settings are preserved. The presets are applied when the game is launched, not live during gameplay.

## Remaining compatibility limits

- Android 11 or later. The original port and added code use APIs at this level; lowering the manifest requirement alone would not provide older Android support.
- ARM64 only. The supplied native game libraries have no 32-bit ARM or x86 builds. Supporting those architectures requires a suitable game-engine build, not an APK packaging change.
- A compatible Vulkan 1.3 graphics driver. The original launcher checks this requirement and retains its existing custom-driver flow. A preset cannot add missing GPU capabilities. No driver has been downloaded or changed by this update.
- Compatible complete game data and sufficient device memory/storage are still required. An APK-only installation is not sufficient for gameplay.
- All seven packaged native libraries have 16 KB ELF load-segment alignment, and APK alignment verification passes. Actual gameplay on a 16 KB-page device has not been tested. Android's [16 KB page-size guidance](https://developer.android.com/guide/practices/page-sizes) distinguishes binary alignment from runtime compatibility.

## Verification boundary

The profile calculations were tested at RAM thresholds and on six phone, tablet and foldable display shapes, in both orientations. Checks cover aspect ratio, even dimensions, no upscaling, FPS values, and invalid input rejection. These tests do not imply physical-device compatibility testing.

APK signing, archive integrity and alignment checks passed. Version code 17 installed as an update in the Android Studio emulator. The Performance dialog opened; Low persisted through a full app restart; Medium and High saved their expected resolutions/FPS targets; Restore previous returned the original defaults after multiple preset changes and disabled itself when no backup remained.

The release retains the bridge CPU-work reduction from 0.9.2. Original game DEX, assets and native game libraries are preserved. Full gameplay, sustained FPS, thermal behavior and memory pressure across real low-, medium- and high-end devices remain unverified because complete game data and those devices are unavailable here.
