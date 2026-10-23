# KMP Migration Execution Status

**Plan:** [KMP migration plan](KMP_MIGRATION_PLAN.md)  
**Updated:** 2026-10-23

This tracker records execution against the proposed plan. Android remains the shipping baseline. No production module has been converted to Kotlin Multiplatform.

## Phase gates

| Phase | Status | Evidence / next action |
| --- | --- | --- |
| 0. Discovery | In progress | Android capabilities, module boundaries, persistence, toolchain, CI, and performance baseline inventoried below. First-wave OS floors are locked; product parity and distribution decisions remain open. |
| 1. Toolchain proof | In progress | GitHub Actions run `39956560957` passed common tests, Android KMP linkage, iOS simulator host, and Ubuntu, Windows, and macOS desktop packaging. The proof exercises Koin composition on Android, iOS, and desktop. Android GMS debug workflow `39956561190` also passed with the first Koin app feature. Xcode 16.4 was observed; Xcode pinning, both macOS package architectures, and minimum-OS install checks remain open. |
| 2. Shared seams and Koin | In progress | Koin is selected across the first-wave clients. The Android app starts Koin; Charts, search, lyrics appearance, updater, browse, account, radio-station, Artist Items, History, Local Music, Local Home, online search suggestions, local playlists, Radio screen, Apple Library, and catalog-detail ViewModels passed GMS debug builds, most recently Actions run `39968045873`. Stats and Artist Songs are building in `39968645122`; Explore, Song Insight, AI DJ, and Backup/Restore are building in `39969016348`; Library sections are building in `39969305168`. Playlist management, automatic playlists, Spotify import, and Apple Music import now use Koin and await their GMS build. |
| 3-8 | Not started | Keep Android release builds passing while moving through the phase gates. |

### Toolchain candidate under test

The isolated proof uses the candidate range recorded in the plan: Kotlin 2.4.20, Compose Multiplatform 1.10.2, AGP 9.3.1, Gradle 9.6.0, and JDK 21. The production Android build remains on Kotlin 2.3.10 and AGP 9.4.1. The Xcode version is not pinned yet; the macOS workflow reports its installed version for the compatibility review. The desktop packaging matrix currently uses one macOS runner image, so it does not yet prove both arm64 and x86_64 packages. Linux packaging is configured for Ubuntu 22.04 but has not run. Windows and macOS minimum-version install/launch checks still need their supported OS images.

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

The Android `App` now starts Koin alongside the remaining Hilt graph. Koin feature modules are assembled there, and migrated features no longer carry Hilt ViewModel bindings or call sites. A source scan of the current worktree finds 16 remaining `@HiltViewModel` declarations in 16 Kotlin files, five `@AndroidEntryPoint` components, and five Hilt `@Module` files. Keep the Hilt plugin and runtime until those bindings and entry points are migrated.

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
| `:canvas` | Twelve main Kotlin files; Ktor is wired directly to OkHttp; catalog and playlist export code also touches `File`, `StringReader`, and `Dispatchers.IO`. Canvas artwork, Apple artwork/artist DTOs, Apple playlist link parsing, playlist DTOs, pagination validation, catalog response mapping, and Apple artwork/color JSON mapping are isolated as common-compatible production sources and included in the proof. | First pilot candidate after moving engine and file access behind platform boundaries and validating common models/tests |
| `:lyrics` | Depends on `:innertube`, Ktor/OkHttp, and Jsoup. Provider-facing `LrcLib.parseLrc` delegates to pure `LrcParser`; the app's standard/rich-sync parser delegates to `TimedLyricsParser`. `LyricsEntry` and the primary-line cursor now live in this module, alongside lyric model, gap, and word-grouping sources with no platform or third-party imports. | Validate these exact production sources on connected KMP runners, then move them into the shared module after Phase 1; keep provider and HTML dependencies in adapters |
| `:innertube` | Uses Ktor/OkHttp and NewPipe Extractor | Keep extractor behind a target adapter; identify portable DTOs and parsers individually |
| `:integrations` | Ktor/OkHttp and JSON protocol code | Split portable protocol/model logic from target transport and SDK code |
| `:spotify` | Uses Android WebKit and OkHttp-backed Ktor; native audio engine is separate | Keep Android auth/engine implementation; extract import transformations and portable auth rules only after provider scope is decided |
| `:network-storage` | Includes Android `Context`/Keystore storage, `Uri`, Media3 data sources, SMBJ, and OkHttp | Do not convert wholesale; share source descriptors and transfer policy behind platform adapters |
| `:spine` | Uses Android logging, Android QuickJS, and JVM file APIs | Keep Android-bound; no first-wave KMP conversion identified |
| `:hig` | Compose UI depends on third-party backdrop/capsule libraries | Reuse tokens and selected stable controls only after target rendering and fallback checks |
| `:app` | 936 Kotlin files total, including 849 main-source and 78 test files; Hilt, Room, DataStore/MMKV, Media3, and OS integrations. Queue shuffle order is extracted into pure `ShuffleOrderPolicy`; `PlayerConnection` implements the portable `PlaybackController` contract and exposes a read-only, Cast-aware `PlaybackSnapshot` flow while retaining Media3 and Cast behind the Android adapter. Koin is now selected for all first-wave clients; the app root starts Koin, with Charts, search, online-search suggestions, Explore, lyrics appearance, updater, browse, account, radio, artist-items, history, local music, local home, local playlist, Apple Library, artist, artist songs, artist albums, album, top playlist, library songs, albums, artists, playlists, mixes, Song Insight, AI DJ, backup/restore, stats, automatic playlists, playlist management, Spotify import, and Apple Music import ViewModels migrated. Hilt remains for the rest of the Android graph. | Keep the Android shell; move Hilt bindings to Koin incrementally alongside shared feature slices |
| Room | Schema version 40; snapshots 1-40 are checked in; current database wrapper is Android-specific | Preserve database name, schema, and migration chain. No Android persistence change in this phase. |

## Build and performance baseline

- The existing PR workflow runs on Ubuntu with JDK 21 and builds/lints Universal FOSS Debug. The existing build workflow also builds Universal GMS Debug. Neither workflow currently validates iOS or desktop targets.
- The Android app's configured minimum is API 26. FOSS and GMS variants remain required Android gates.
- [Android startup and scroll baseline](performance/2026-10-23-baseline.md) records the measured release startup and frame-latency baseline. It reports 788 ms median cold launch and 64.34% deadline misses in the manual 120 Hz rapid-scroll capture; it does not claim sustained 120 FPS.
- Local builds of `:app:assembleUniversalFossDebug` and `:app:assembleUniversalGmsDebug` both passed on 2026-10-23 with JDK 21 and Gradle 9.6.0. Both include the app and its module graph, native ABI work, and APK packaging. After moving `LyricsEntry` and the app parser implementation into `:lyrics`, `:lyrics:testDebugUnitTest`, Android facade tests `:app:testUniversalFossDebugUnitTest --tests com.convx.music.lyrics.LyricsUtilsTest`, cursor tests `:app:testUniversalFossDebugUnitTest --tests com.convx.music.lyrics.LyricsTextSpacingTest`, and both FOSS/GMS app Kotlin compile tasks passed. Canvas runs passed `:canvas:test`, including Apple playlist mapping, CanvasArtwork serialization/fallback, and Apple artwork sizing/serialization tests; `:app:testUniversalFossDebugUnitTest --tests com.convx.music.viewmodels.AppleMusicSongMatchingTest`; and both FOSS/GMS app Kotlin compile tasks. Earlier focused runs also passed `:lyrics:assembleDebug` and `:app:testUniversalFossDebugUnitTest --tests com.convx.music.playback.ShuffleOrderPolicyTest`. Existing deprecation and code-quality warnings were emitted; none failed the successful builds.
- A local attempt to build the official Universal/arm32 GMS/FOSS release matrix failed after 44 minutes at `:app:compileArm32GmsReleaseKotlin`. The Kotlin daemon reported insufficient memory while compiling `LyricsUtils.romanizeHindi`; Gradle's fallback compilation also failed from memory pressure. This Windows run produced no verified release artifact set. The focused debug module tests and both debug Kotlin compiles passed; do not treat them as proof of release packaging.
- GitHub Actions is connected to the user-provided remote. KMP run `39956560957` passed common tests, Android shared linkage, iOS simulator host, and desktop packaging on Ubuntu 22.04, Windows 2022, and macOS 15. Android GMS debug build `39956561190` passed after adding Koin to the app root and migrating Charts; build `39957756634` passed after migrating search ViewModels and their Home/Explore call sites; build `39958950245` passed after migrating lyrics appearance; build `39960447129` passed after moving the updater graph to Koin; build `39962120673` passed after migrating browse, account, radio-station, and online-search ViewModels; build `39963625004` passed after migrating Artist Items; build `39965202776` passed after migrating History; build `39965771182` passed after migrating Local Music, Local Home, and online-search suggestions; build `39966439260` passed after migrating Local Playlist; build `39967432370` passed after migrating Radio and Apple Library; build `39968045873` passed after migrating catalog details. Stats/Artist Songs build `39968645122`, Explore/media utility build `39969016348`, and Library sections build `39969305168` are in progress. Earlier KMP run `39954733608` exposed source filtering and a test fixture mismatch; both were fixed before the successful run. Earlier local KMP resolution failure was caused by this workspace blocking outbound access to `dl.google.com`.
- The spike lives under `migration-spike/kmp-toolchain/`; it keeps KMP plugin versions and the Android host dependency in its own version catalog. It includes production lyrics sync, `LyricsEntry`, both parsers, lyric model, gap, word-grouping, and primary-line cursor sources; playback shuffle, snapshot, and controller contracts; and canvas/Apple artwork and artist serialization plus Apple playlist link/models/pagination/response mapping directly as common code. The root `settings.gradle.kts` and production module graph are unchanged.
- The KMP workflow path filters cover the proof, portable canvas, lyrics, and playback sources, future `shared`/`iosApp`/`desktopApp` modules, and Gradle settings/catalog/wrapper changes so those edits rerun the proof.

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

1. Resolve the candidate dependencies on an online runner and build Android linkage, common tests, the iOS simulator framework/host, and desktop packages.
2. Pin the Xcode version after checking the reported runner version against the candidate Kotlin/Compose tuple, and add a runner for each macOS package architecture.
3. Mark Phase 1 complete only after GitHub Actions passes all first-wave targets. Keep the proof isolated from production modules until then.
