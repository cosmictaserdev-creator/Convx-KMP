# KMP Migration Execution Status

**Plan:** [KMP migration plan](KMP_MIGRATION_PLAN.md)  
**Updated:** 2026-10-23

This tracker records execution against the proposed plan. Android remains the shipping baseline. The first production KMP core modules are being wired on this branch; GitHub Actions must validate them before the pilot is considered complete.

## Phase gates

| Phase | Status | Evidence / next action |
| --- | --- | --- |
| 0. Discovery | In progress | Android capabilities, module boundaries, persistence, toolchain, CI, and performance baseline inventoried below. First-wave OS floors are locked; product parity and distribution decisions remain open. |
| 1. Toolchain proof | In progress | GitHub Actions run `39977338597` passed common tests, Android KMP linkage, iOS simulator host with Xcode 16.4 selected, and desktop packaging on Ubuntu 22.04, Windows 2022, macOS 15 arm64, and macOS 15 Intel x86_64. Android GMS debug runs `39975655215` and `39978143702` passed with the Koin-only app graph; FOSS build/lint run `39978231167` passed. Minimum-OS install and launch checks remain open. |
| 2. Shared seams and Koin | In progress | Koin is selected across the first-wave clients. Charts, search, lyrics appearance, updater, browse, account, radio-station, Artist Items, History, Local Music, Local Home, online search suggestions, local playlists, Radio screen, Apple Library, catalog details, Stats, Artist Songs, Explore, Song Insight, AI DJ, Backup/Restore, Library sections, playlist management/import, New Release, local search, account settings, Jam, Listen Together, Lyrics Menu, and Lyrics Provider passed GMS debug builds through run `39970521025`. Every active Android ViewModel and the application dependency graph now use Koin; Hilt annotations, modules, bridges, and dependencies are removed. The final Universal GMS debug build passed in run `39975655215`. |
| 3. Safe pilots | In progress | Moved portable lyrics parser/state sources and Apple catalog models/mappers into `shared-kmp/lyrics-core` and `shared-kmp/canvas-core`. The KMP proof builds those modules with the candidate toolchain; the Android build compiles the same source directories under its shipping toolchain. |
| 4-8 | Not started | Keep Android release builds passing while moving through the remaining phase gates. |

### Toolchain candidate under test

The isolated KMP build uses Kotlin 2.4.20, Compose Multiplatform 1.10.2, AGP 9.3.1, Gradle 9.6.0, and JDK 21. The production Android build remains on Kotlin 2.3.10 and AGP 9.4.1. GitHub Actions has passed with Xcode 16.4 selected and packages for Ubuntu 22.04, Windows 2022, macOS 15 arm64, and macOS 15 Intel x86_64. Runtime install and launch checks at the locked minimum OS versions remain open.

## First-wave support floors

These are the product floors for the first proof. They define intended support, not a claim that packaging or runtime compatibility has already passed.

| Target | Minimum | Initial architecture |
| --- | --- | --- |
| iOS | 17.0 | arm64 device; arm64 simulator in CI |
| macOS | 14.0 | arm64 and x86_64 |
| Windows | Windows 10, version 22H2 | x86_64 |
| Linux | Ubuntu 22.04 LTS | x86_64 |
| Browser / Kotlin/Wasm | Out of scope | No target |

## Koin migration inventory

The Android `App` now starts Koin as its application dependency container. ViewModels, app-scoped services, caches, repositories, widgets, and Android service/activity dependencies are registered in Koin. Hilt annotations, entry points, provider modules, and Gradle dependencies were removed. The Koin-only graph passed Universal GMS debug builds in GitHub Actions runs `39975655215` and `39978143702`.

## Current platform and capability inventory

The Android application is the only shipped client in this workspace. There is no iOS app or desktop app yet. Each row below is therefore a migration scope decision, not a parity claim.

| Capability | Android today | iOS / desktop direction | Open policy |
| --- | --- | --- | --- |
| Browse, search, artists, albums, playlists | Implemented in Android app and provider modules | Share portable models, request mapping, use-case rules, and screen state; share mobile screens where they fit; give desktop its own composition | Launch-critical providers per target |
| Library and local media | Android database and Android file access | Share library rules and source descriptors; use native file-access adapters | Local-library expectations, removable media, and durable access |
| Playback, queue, repeat, shuffle, downloads | Android Media3 and Android services | Share queue rules and playback state; use native playback adapters | Desktop engine, supported formats, background behavior, and outputs |
| Lyrics and artwork | Android app, `:lyrics`, and `:artwork` | Start with portable parsers, models, and state; keep decoding and OS audio clocks in adapters | Provider availability per target |
| SMB and WebDAV | Android storage module, including Media3 data sources and encrypted server storage | Share source descriptors and transfer policy only after target support is proven | Whether both protocols ship on iOS and desktop |
| Spotify import and account state | Android app and `:spotify` | Share import mapping and portable auth state; adapt browser handoff and secret storage per OS | Provider scope and OAuth flow per target |
| Discord, Last.fm, recognition, Listen Together | Android app and integration modules | Share protocol/domain behavior only where platform and provider constraints allow | Which capabilities ship on iOS and desktop |
| Equalizer, stem separation, music recognition | Android implementation and native/runtime dependencies | Share settings and orchestration where portable; otherwise expose explicit capability limits | Android-only status versus target equivalents |
| Notifications, lock screen, Android Auto, Cast, widgets, self-updater | Android-specific | Keep in Android shell unless a separate native equivalent is approved | Equivalent behavior is not promised |

## Module and data boundary findings

| Area | Current evidence | Migration treatment |
| --- | --- | --- |
| `:canvas` | Ktor is wired directly to OkHttp; catalog and playlist export code also touches `File`, `StringReader`, and `Dispatchers.IO`. Six artwork, Apple catalog model, link, and mapping sources now live in `shared-kmp/canvas-core`; transport and file work remain in the Android/JVM adapter. | Validate `canvas-core` on Android, iOS, and desktop; keep transport and file operations behind adapters |
| `:lyrics` | Depends on `:innertube`, Ktor/OkHttp, and Jsoup. Eight pure lyrics model, state, and parser sources now live in `shared-kmp/lyrics-core`; provider and HTML dependencies remain in `:lyrics`. | Validate `lyrics-core` on Android, iOS, and desktop; keep provider and HTML dependencies in adapters |
| `:innertube` | Uses Ktor/OkHttp and NewPipe Extractor | Keep extractor behind a target adapter; identify portable DTOs and parsers individually |
| `:integrations` | Ktor/OkHttp and JSON protocol code | Split portable protocol/model logic from target transport and SDK code |
| `:spotify` | Uses Android WebKit and OkHttp-backed Ktor; native audio engine is separate | Keep Android auth/engine implementation; extract import transformations and portable auth rules only after provider scope is decided |
| `:network-storage` | Includes Android `Context`/Keystore storage, `Uri`, Media3 data sources, SMBJ, and OkHttp | Do not convert wholesale; share source descriptors and transfer policy behind platform adapters |
| `:spine` | Uses Android logging, Android QuickJS, and JVM file APIs | Keep Android-bound; no first-wave KMP conversion identified |
| `:hig` | Android Compose UI depends on third-party backdrop/capsule libraries; no UI has moved yet | Share tokens and controls that compile and render across targets; replace or adapt Android-only pieces as needed. Mobile screens may share Compose Multiplatform UI; desktop keeps its own screen composition. |
| `:app` | 936 Kotlin files total, including 849 main-source and 78 test files; Koin, Room, DataStore/MMKV, Media3, and OS integrations. Queue shuffle order is extracted into pure `ShuffleOrderPolicy`; `PlayerConnection` implements the portable `PlaybackController` contract and exposes a read-only, Cast-aware `PlaybackSnapshot` flow while retaining Media3 and Cast behind the Android adapter. Koin is selected for all first-wave clients and is now the app's only dependency injection container. Charts, search, local search, online-search suggestions, Explore, New Release, Home, lyrics appearance, Lyrics Menu, Lyrics Provider, updater, browse, account, account settings, radio, artist-items, history, local music, local home, local playlist, Apple Library, artist, artist songs, artist albums, album, top playlist, library songs, albums, artists, playlists, mixes, Song Insight, AI DJ, backup/restore, stats, automatic playlists, playlist management, Spotify import, Apple Music import, Jam, Listen Together, network storage, network browsing, online playlist, cached playlist, and equalizer ViewModels use Koin. | Keep the Android shell; move portable feature slices alongside shared code |
| Room | Schema version 40; snapshots 1-40 are checked in; current database wrapper is Android-specific | Preserve database name, schema, and migration chain. No Android persistence change in this phase. |

## Build and performance baseline

- The existing PR workflow runs on Ubuntu with JDK 21 and builds/lints Universal FOSS Debug. The existing build workflow also builds Universal GMS Debug. Neither workflow currently validates iOS or desktop targets.
- The Android app's configured minimum is API 26. FOSS and GMS variants remain required Android gates.
- [Android startup and scroll baseline](performance/2026-10-23-baseline.md) records the measured release startup and frame-latency baseline. It reports 788 ms median cold launch and 64.34% deadline misses in the manual 120 Hz rapid-scroll capture; it does not claim sustained 120 FPS.
- Local builds of `:app:assembleUniversalFossDebug` and `:app:assembleUniversalGmsDebug` both passed on 2026-10-23 with JDK 21 and Gradle 9.6.0. Both include the app and its module graph, native ABI work, and APK packaging. After moving `LyricsEntry` and the app parser implementation into `:lyrics`, `:lyrics:testDebugUnitTest`, Android facade tests `:app:testUniversalFossDebugUnitTest --tests com.convx.music.lyrics.LyricsUtilsTest`, cursor tests `:app:testUniversalFossDebugUnitTest --tests com.convx.music.lyrics.LyricsTextSpacingTest`, and both FOSS/GMS app Kotlin compile tasks passed. Canvas runs passed `:canvas:test`, including Apple playlist mapping, CanvasArtwork serialization/fallback, and Apple artwork sizing/serialization tests; `:app:testUniversalFossDebugUnitTest --tests com.convx.music.viewmodels.AppleMusicSongMatchingTest`; and both FOSS/GMS app Kotlin compile tasks. Earlier focused runs also passed `:lyrics:assembleDebug` and `:app:testUniversalFossDebugUnitTest --tests com.convx.music.playback.ShuffleOrderPolicyTest`. Existing deprecation and code-quality warnings were emitted; none failed the successful builds.
- A local attempt to build the official Universal/arm32 GMS/FOSS release matrix failed after 44 minutes at `:app:compileArm32GmsReleaseKotlin`. The Kotlin daemon reported insufficient memory while compiling `LyricsUtils.romanizeHindi`; Gradle's fallback compilation also failed from memory pressure. This Windows run produced no verified release artifact set. The focused debug module tests and both debug Kotlin compiles passed; do not treat them as proof of release packaging.
- GitHub Actions is connected to the user-provided remote. KMP run `39956560957` passed common tests, Android shared linkage, iOS simulator host, and desktop packaging on Ubuntu 22.04, Windows 2022, and macOS 15. Android GMS debug build `39956561190` passed after adding Koin to the app root and migrating Charts; build `39957756634` passed after migrating search ViewModels and their Home/Explore call sites; build `39958950245` passed after migrating lyrics appearance; build `39960447129` passed after moving the updater graph to Koin; build `39962120673` passed after migrating browse, account, radio-station, and online-search ViewModels; build `39963625004` passed after migrating Artist Items; build `39965202776` passed after migrating History; build `39965771182` passed after migrating Local Music, Local Home, and online-search suggestions; build `39966439260` passed after migrating Local Playlist; build `39967432370` passed after migrating Radio and Apple Library; build `39968045873` passed after migrating catalog details; build `39968645122` passed after migrating Stats and Artist Songs; build `39969016348` passed after migrating Explore, Song Insight, AI DJ, and Backup/Restore; build `39969305168` passed after migrating Library sections; build `39969575533` passed after playlist/import migration; build `39969848506` passed after New Release, local search, and account settings; build `39970263804` passed after Jam and Listen Together; build `39970521025` passed after Lyrics Menu and Lyrics Provider. Intermediate builds `39971136711` and `39971723867` failed in Hilt generation due to colliding bridge methods; the Hilt graph and plugin have since been removed. The first Koin-only app build `39973072562` exposed missing direct Navigation Compose and cache visibility issues, which were fixed. Build `39974491682` then found stale Wrapped screen provider references, which were replaced with direct Koin resolution; GMS runs `39975655215` and `39978143702` both passed and uploaded the APK. FOSS dispatch `39977866622` failed before compilation because an obsolete FFTW setup expected a deleted path; after removing that unused setup, FOSS build/lint run `39978231167` passed. Refreshed KMP run `39974491493` passed common tests, Android linkage, iOS simulator host, and desktop packaging; run `39977338597` also passed after pinning Xcode 16.4 and adding the macOS Intel x86_64 package job. Earlier KMP run `39954733608` exposed source filtering and a test fixture mismatch; both were fixed before the successful run. Earlier local KMP resolution failure was caused by this workspace blocking outbound access to `dl.google.com`.
- The KMP proof remains under `migration-spike/kmp-toolchain/` with candidate plugins isolated in its own version catalog. Production lyrics and canvas common sources now live under `shared-kmp/`; the proof builds them as KMP modules, while the root Android modules compile the same directories under the shipping toolchain. This avoids loading two AGP versions into the Android build until the production toolchain is upgraded. Playback contracts remain in the proof module. No UI source has moved yet.
- The KMP workflow watches `shared-kmp/**`, the proof, future `shared`/`iosApp`/`desktopApp` modules, and Gradle settings/catalog/wrapper changes so edits to these inputs rerun the proof.

## Remaining Phase 0 decisions

These decisions do not need to block an isolated toolchain proof, but they must be assigned before the corresponding feature enters a shared implementation:

1. Launch-critical streaming providers and integrations on iOS and desktop.
2. Local-library, removable-media, and network-share expectations on each target.
3. Listen Together support on iOS and desktop.
4. Desktop playback engine, codecs, outputs, and background behavior.
5. Whether equalizer, stem separation, recognition, Cast, widgets, Auto, and self-update remain Android-only.
6. New-platform persistence choice after a real schema/query migration proof.
7. Apple, Windows, and desktop distribution/signing account ownership.

## Next gate

1. Push the lyrics/canvas KMP pilot and use GitHub Actions to validate Android linkage, common tests, the iOS simulator framework/host, desktop packages, and both Android variants.
2. Add runtime install/launch checks for the locked minimum OS versions.
3. Mark Phase 3 complete only after the pilot passes all connected targets; keep Android releasable while starting the next pilot.
