# KMP Migration Execution Status

**Plan:** [KMP migration plan](KMP_MIGRATION_PLAN.md)  
**Updated:** 2026-10-24

This tracker records execution against the proposed plan. Android remains the shipping baseline. Lyrics, canvas, playback, history, design-system, library, and HIG code now have production KMP module boundaries.

## Phase gates

| Phase | Status | Evidence / next action |
| --- | --- | --- |
| 0. Discovery | In progress | Android capabilities, module boundaries, persistence, toolchain, CI, and performance baseline inventoried below. First-wave OS floors are locked; product parity and distribution decisions remain open. |
| 1. Toolchain proof | Compile/package proof complete; runtime proof open | GitHub Actions run `40009471058` passed common tests, Android KMP linkage, iOS simulator framework/host, and desktop packaging on Ubuntu 22.04, Windows 2022, macOS 15 arm64, and macOS 15 Intel x86_64. It includes shared HIG, history grouping, the playlist repository module, and its Koin test. Production Android uses Kotlin 2.4.20 and AGP 9.3.1. Minimum-OS install and launch checks remain open. |
| 2. Shared seams and Koin | Android Koin complete; shared Koin wiring in progress | Every active Android ViewModel and the application dependency graph use Koin; Hilt annotations, modules, bridges, and dependencies are removed. Universal GMS and FOSS builds with APK upload passed in `39987312501` and `39987326490`. `libraryCoreModule` resolves `ObservePlaylists` against a platform repository in common tests (`40009471058`); production GMS passed in `40009471013`, while the FOSS run `40009498970` remained in progress for nearly a day. Search-history Koin changes are pushed in `5ea55c9`; current verification runs are KMP `40011323691`, GMS `40011323360`, and FOSS `40011333188`. Portable playback state and controller contracts are in `shared-kmp/playback-core`; more feature repository seams remain. |
| 3. Safe pilots | Complete; production integration verification in progress | Lyrics, canvas, playback, history-core, library-core, design-system, and HIG have production KMP modules. Android app, lyrics, and canvas consume Gradle project artifacts instead of directly compiling shared source folders. KMP run `40009471058` passed common tests, Android linkage, iOS simulator, and Ubuntu/Windows/macOS arm64/macOS Intel packaging. GMS run `40009471013` passed for the common playlist Koin integration. The matching FOSS run `40009498970` remained in progress for nearly a day. The search-history changes are pushed in `5ea55c9` and are being checked by KMP `40011323691`, GMS `40011323360`, and FOSS `40011333188`. |
| 4. Data and persistence | In progress | Initial schema/settings/backup inventory is recorded in [KMP data and persistence inventory](KMP_DATA_PERSISTENCE_INVENTORY.md). The extra Room instance in `AiPlaylistModifier` now uses the Koin-owned database. Room KMP run `39995675459` passed Android KSP compilation and an in-memory Room insert/Flow query on Windows desktop and iOS simulator. `shared-kmp/history-core` owns a common listen-history count, clear, and portable calendar-grouping contract. `shared-kmp/library-core` defines playlist summary and observation contracts plus a common Koin use case; Android maps Room rows at its adapter and the sidebar consumes the shared use case. The pushed `shared-kmp/search-core` slice covers search-history observation, record, delete, and clear; Android UI paths use an Android Room adapter. KMP `40011323691`, GMS `40011323360`, and FOSS `40011333188` are verifying it. Schema 40, disk-backed DB behavior, most repository operations, migrations, and backup/import remain unproven. |
| 5-8 | Not started | Keep Android releasable while moving through shared mobile slices, playback parity, desktop product work, and release gates. |

### Toolchain candidate under test

The KMP build uses Kotlin 2.4.20, Compose Multiplatform 1.10.2, AGP 9.3.1, Gradle 9.6.0, and JDK 21. Production Android has been aligned to Kotlin 2.4.20 and AGP 9.3.1. GitHub Actions run `40009471058` passed with Xcode 16.4 selected and packages for Ubuntu 22.04, Windows 2022, macOS 15 arm64, and macOS 15 Intel x86_64. Runtime install and launch checks at the locked minimum OS versions remain open.

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
| `shared-kmp/history-core` | Common `ListenHistoryRepository` exposes listen-history count as a `Flow<Int>`; common calendar grouping now handles recent-day/week/month buckets. The Android Room adapter still owns `EventWithSong` paging. | Add portable history rows and repository queries before moving history presentation |
| `shared-kmp/library-core` | Defines common playlist order and `PlaylistSummary` / `PlaylistRepository` types, an `ObservePlaylists` use case, and `libraryCoreModule`. Android maps Room `Playlist` relations through `RoomPlaylistRepository`; the navigation sidebar resolves the shared use case through Koin. Other library screens and writes still use Room types. | Verify common Koin module and Android adapter; move remaining reads and writes behind app-facing models, feature by feature |
| `:hig` | Its Compose controls, themes, icons, and glass components now live in `commonMain`. Luminance sampling uses common Compose Canvas APIs. KMP run `40005440870` compiled and packaged the module for Android, iOS simulator, Windows, Linux, and macOS; no Android imports remain in common source. | Validate rendering and behavior on actual clients; keep screen composition target-specific where appropriate |
| `:app` | 936 Kotlin files total, including 849 main-source and 78 test files; Koin, Room, DataStore/MMKV, Media3, and OS integrations. Queue shuffle order, playback snapshot, and the `PlaybackController` contract live in `shared-kmp/playback-core`. `PlayerConnection` implements the portable controller contract and exposes a read-only, Cast-aware snapshot flow while retaining Media3 and Cast behind the Android adapter. The app, `:lyrics`, `:canvas`, and `:library-core` now consume actual KMP project artifacts. Koin is the app's only dependency injection container. Feature ViewModels across charts, search, browse, library, playback, account, integrations, and settings use Koin. | Keep the Android shell; move portable feature slices alongside shared code |
| Room | Schema version 40; snapshots 1-40 are checked in; current database wrapper is Android-specific | Preserve database name, schema, and migration chain. No Android persistence change in this phase. |

## Build and performance baseline

- GitHub Actions builds/lints Universal FOSS Debug and builds Universal GMS Debug. The separate KMP workflow validates shared common code, Android linkage, iOS simulator, and Windows/Linux/macOS packaging; these package jobs are compile/package proofs, not product install or launch checks.
- The Android app's configured minimum is API 26. FOSS and GMS variants remain required Android gates.
- [Android startup and scroll baseline](performance/2026-10-23-baseline.md) records the measured release startup and frame-latency baseline. It reports 788 ms median cold launch and 64.34% deadline misses in the manual 120 Hz rapid-scroll capture; it does not claim sustained 120 FPS.
- Local builds of `:app:assembleUniversalFossDebug` and `:app:assembleUniversalGmsDebug` both passed on 2026-10-23 with JDK 21 and Gradle 9.6.0. Both include the app and its module graph, native ABI work, and APK packaging. After moving `LyricsEntry` and the app parser implementation into `:lyrics`, `:lyrics:testDebugUnitTest`, Android facade tests `:app:testUniversalFossDebugUnitTest --tests com.convx.music.lyrics.LyricsUtilsTest`, cursor tests `:app:testUniversalFossDebugUnitTest --tests com.convx.music.lyrics.LyricsTextSpacingTest`, and both FOSS/GMS app Kotlin compile tasks passed. Canvas runs passed `:canvas:test`, including Apple playlist mapping, CanvasArtwork serialization/fallback, and Apple artwork sizing/serialization tests; `:app:testUniversalFossDebugUnitTest --tests com.convx.music.viewmodels.AppleMusicSongMatchingTest`; and both FOSS/GMS app Kotlin compile tasks. Earlier focused runs also passed `:lyrics:assembleDebug` and `:app:testUniversalFossDebugUnitTest --tests com.convx.music.playback.ShuffleOrderPolicyTest`. Existing deprecation and code-quality warnings were emitted; none failed the successful builds.
- A local attempt to build the official Universal/arm32 GMS/FOSS release matrix failed after 44 minutes at `:app:compileArm32GmsReleaseKotlin`. The Kotlin daemon reported insufficient memory while compiling `LyricsUtils.romanizeHindi`; Gradle's fallback compilation also failed from memory pressure. This Windows run produced no verified release artifact set. The focused debug module tests and both debug Kotlin compiles passed; do not treat them as proof of release packaging.
- GitHub Actions is connected to the user-provided remote. Earlier runs document the Koin migration and initial shared-module proofs. KMP run `40009471058` passed common tests (history and playlist/Koin), Android linkage, iOS simulator, and Ubuntu/Windows/macOS arm64/macOS Intel packaging. Production GMS run `40009471013` passed. FOSS run `40009498970` remained in progress for nearly a day and was asked to cancel. The search-history commit `5ea55c9` is under fresh KMP (`40011323691`), GMS (`40011323360`), and FOSS (`40011333188`) Actions verification. Production toolchain alignment passed GMS in `39999803253`; actual project-artifact integration passed GMS in `40002612595`, while KMP proof `40002612615` also passed. The prior failures encountered while aligning root Gradle plugin resolution and moving lyrics APIs across module boundaries were fixed before these passes.
- Production KMP projects are included in the root build. The Android app, `:lyrics`, and `:canvas` now consume project artifacts; the production toolchain is Kotlin 2.4.20, Compose Multiplatform 1.10.2, AGP 9.3.1, Gradle 9.6.0, and JDK 21. The HIG module has been converted to KMP, with its source in `commonMain`.
- The KMP workflow watches the production KMP modules, HIG source, migration proof, and Gradle settings/catalog/wrapper changes so edits to these inputs rerun the proof.

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

1. Review the KMP `40011323691`, GMS `40011323360`, and FOSS `40011333188` results for commit `5ea55c9`; fix any failures.
2. Broader library and playlist access still uses Room directly; move remaining reads and writes behind app-facing repository models, feature by feature.
3. Prototype a disk-backed schema migration, real repository operations, and backup/import rollback checks before choosing new-client persistence; keep Android storage unchanged until rollback checks pass.
