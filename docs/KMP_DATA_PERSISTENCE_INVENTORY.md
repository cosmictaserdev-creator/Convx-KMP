# KMP Data and Persistence Inventory

**Updated:** 2026-10-24
**Related plan:** [KMP migration plan](KMP_MIGRATION_PLAN.md)

This is the Phase 4 starting inventory. Android storage remains the production source of truth while new-target adapters are evaluated.

## Current Android storage

### Library database

- Room database file: `song.db`; current schema version: 40.
- The schema has 18 entities and 3 views. Checked-in schema snapshots cover versions 1 through 40.
- `MusicDatabase.kt` declares 36 Room auto-migrations and 6 manual migrations, including the explicit 39-to-40 migration. It also sets SQLite WAL/PRAGMA behavior and custom query/transaction executors.
- The 39-to-40 migration adds `song.isPodcastEpisode` with a default of false. `app/src/androidTest/.../RoomSchemaMigrationTest.kt` creates a database from checked-in schema 39, runs the production migration, validates against schema 40, and checks that an existing song row survives with the default flag. Runs `40031953814`, `40035972036`, and `40039692127` did not return a migration result; the last instrumentation process was killed before reporting a migration assertion. Commit `14aeefc` batches historical schema migrations into fresh emulator processes; retry `40042378370` is still in progress. Treat the production migration chain as unverified until every batch passes.
- `DatabaseDao` contains 199 Room query and mutation annotations. It returns Room entities and relation graphs and also depends on Innertube DTOs and Android/JVM utilities.
- A fresh literal-name scan matched 59 app source files for `MusicDatabase` or `DatabaseDao`, including the database/DAO declarations: 27 ViewModels, 7 playback files, 7 UI files, 4 utilities, and the remaining composition-root, DI, database, history, AI, and module files. Koin registers the Room instance and DAO. `AiPlaylistModifier` previously constructed a second database instance; it now receives the Koin-owned instance through its Compose call site.
- `fallbackToDestructiveMigrationOnDowngrade()` is enabled. Do not change the database name, schema, or upgrade path as part of KMP extraction.

### Settings and credentials

- `PreferenceKeys.kt` declares 367 Preferences keys used throughout the app.
- `DataStore.kt` exposes Android `Context`-based Preferences, synchronous convenience reads backed by a process snapshot, and Compose lifecycle helpers. The first uncached read can block on `runBlocking(Dispatchers.IO)`.
- `App` initializes MMKV, copies four legacy SharedPreferences files into MMKV once, and mirrors selected session values in MMKV to avoid blocking startup reads. DataStore remains the settings owner for those mirrored session values.
- Settings and secrets are not yet behind shared interfaces. New clients need separate settings and secure-secret adapters; they should not reuse Android storage classes.

### Backup and restore

The current archive stores the raw `song.db` file, the serialized DataStore file, and selected home-background files. Restore validates archive paths, stages recognized files, validates staged settings and database contents, and commits file replacements with rollback support. The focused path policy check passed in run `40037217193`; archive validation and staged restore rollback passed in GMS runs `40041924243` and `40042378272`. The archive remains Android-specific and does not define a portable cross-client format.

## KMP boundary

Keep Room entities, DAO interfaces, SQLite migrations, Android `Context`, DataStore/MMKV implementations, and raw-file recovery in platform adapters. Define common repository contracts around app-facing models and flows, then map the existing Room graph at the Android boundary. Preserve the exact Android schema and preference names during this work.

The first Phase 4 contract now lives in `shared-kmp/history-core`: `ListenHistoryRepository` exposes a `Flow<Int>` event count and a `clear()` operation. The Android `RoomHistoryLocalDataSource` implements it for Home, the app shell, and the privacy setting that clears listen history; history pagination still returns Room `EventWithSong` relations and remains Android-only. This is a first seam, not a completed history repository migration.

`shared-kmp/history-core` also owns the calendar grouping policy for local listen-history rows. Android maps the Room event date and row identity into the common policy, then adapts the grouped result back to the existing Android UI type. The `history-core` common tests passed with KMP proof run `40007010916`.

`shared-kmp/library-core` defines playlist summaries/details, filtering and ordering policy, observation, and commands for duplicate checks, add/pin, mutations, and cover updates. Android maps Room rows through `RoomPlaylistRepository`. The sidebar, add-to-playlist ViewModel, main library, Apple-style playlist page, local home, and local-playlist detail metadata consume common models. Common sort-policy verification passed in KMP run `40043047197`; KMP and GMS runs `40043713308` and `40043713310` are validating cover updates. Playlist-song Room relations and playback objects remain Android-specific.

The search-history slice in commit `5ea55c9` adds `shared-kmp/search-core` with a platform-neutral entry model, observation and mutation repository contract, and a common Koin command use case. Android's `RoomSearchHistoryRepository` adapts the existing Room DAO, while search UI, suggestions, and privacy settings now go through the shared contract. The Android Room entity and schema are unchanged. KMP verification passed in `40011323691`; GMS/FOSS runs failed on an invalid Compose Koin import, corrected in `12d7ba7`. Fresh Android verification is queued with the playlist-summary changes in `e04a7dc`.

The persistence engine for iOS and desktop is not selected yet. An isolated Room KMP prototype is configured under `migration-spike/room-kmp-proof/`, using Kotlin 2.3.10, AGP 9.4.1, KSP 2.3.6, Room 2.8.4, and the bundled SQLite driver. Runs `39994422571` and `39995675459` passed target compilation and an in-memory insert/Flow query. Commit `310dda9` adds a sample version-1-to-2 disk migration, verifies data after reopen, and exercises versioned logical snapshot import/rejection. Run `40022590356` passed Android compilation, Windows desktop persistence tests, iOS simulator compilation, and the iOS disk-backed persistence test. This prototype still does not use the app's schema 40, verify the production upgrade chain, serialize a portable backup archive, or select Room for new clients.

## Next proof gates

1. Room KMP code-generation and target compilation proof: complete in Actions run `39994422571`.
2. In-memory Room runtime/query proof: complete on Windows and iOS simulator in Actions run `39995675459`; Android compile/codegen passed there.
3. Disk-backed sample migration, reopen, repository, and snapshot-validation proof: passed on Android, Windows desktop, and the iOS simulator in Actions run `40022590356`.
4. Trace remaining library, history, lyrics, and settings consumers; add contracts around the operations clients actually call. Keep the Android schema snapshots and archive format unchanged.
5. Finish the released schema 1-to-40 proof. Staged backup validation and rollback have CI coverage; select new-client persistence only after the production migration and query-parity evidence is complete.
