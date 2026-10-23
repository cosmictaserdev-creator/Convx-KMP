# KMP Data and Persistence Inventory

**Updated:** 2026-10-24
**Related plan:** [KMP migration plan](KMP_MIGRATION_PLAN.md)

This is the Phase 4 starting inventory. Android storage remains the production source of truth while new-target adapters are evaluated.

## Current Android storage

### Library database

- Room database file: `song.db`; current schema version: 40.
- The schema has 18 entities and 3 views. Checked-in schema snapshots cover versions 1 through 40.
- `MusicDatabase.kt` declares 36 Room auto-migrations and 6 manual migrations, including the explicit 39-to-40 migration. It also sets SQLite WAL/PRAGMA behavior and custom query/transaction executors.
- `DatabaseDao` contains 199 Room query and mutation annotations. It returns Room entities and relation graphs and also depends on Innertube DTOs and Android/JVM utilities.
- A fresh literal-name scan matched 59 app source files for `MusicDatabase` or `DatabaseDao`, including the database/DAO declarations: 27 ViewModels, 7 playback files, 7 UI files, 4 utilities, and the remaining composition-root, DI, database, history, AI, and module files. Koin registers the Room instance and DAO. `AiPlaylistModifier` previously constructed a second database instance; it now receives the Koin-owned instance through its Compose call site.
- `fallbackToDestructiveMigrationOnDowngrade()` is enabled. Do not change the database name, schema, or upgrade path as part of KMP extraction.

### Settings and credentials

- `PreferenceKeys.kt` declares 367 Preferences keys used throughout the app.
- `DataStore.kt` exposes Android `Context`-based Preferences, synchronous convenience reads backed by a process snapshot, and Compose lifecycle helpers. The first uncached read can block on `runBlocking(Dispatchers.IO)`.
- `App` initializes MMKV, copies four legacy SharedPreferences files into MMKV once, and mirrors selected session values in MMKV to avoid blocking startup reads. DataStore remains the settings owner for those mirrored session values.
- Settings and secrets are not yet behind shared interfaces. New clients need separate settings and secure-secret adapters; they should not reuse Android storage classes.

### Backup and restore

The current archive stores the raw `song.db` file, the serialized DataStore file, and selected home-background files. Restore closes Room, overwrites the database file, removes persisted playback-state files, and restarts the Android activity. This is an Android-specific recovery procedure, not a portable backup contract. A cross-client format needs explicit versioning, validation, and rollback behavior before it replaces this path.

## KMP boundary

Keep Room entities, DAO interfaces, SQLite migrations, Android `Context`, DataStore/MMKV implementations, and raw-file recovery in platform adapters. Define common repository contracts around app-facing models and flows, then map the existing Room graph at the Android boundary. Preserve the exact Android schema and preference names during this work.

The first Phase 4 contract now lives in `shared-kmp/history-core`: `ListenHistoryRepository` exposes a `Flow<Int>` event count and a `clear()` operation. The Android `RoomHistoryLocalDataSource` implements it for Home, the app shell, and the privacy setting that clears listen history; history pagination still returns Room `EventWithSong` relations and remains Android-only. This is a first seam, not a completed history repository migration.

`shared-kmp/history-core` also owns the calendar grouping policy for local listen-history rows. Android maps the Room event date and row identity into the common policy, then adapts the grouped result back to the existing Android UI type. The `history-core` common tests passed with KMP proof run `40007010916`.

The next read seam is `shared-kmp/library-core`: it defines `PlaylistSummary`, playlist ordering, a read-only `PlaylistRepository`, an `ObservePlaylists` use case, and a common Koin module. The Android `RoomPlaylistRepository` maps existing Room relation rows to that contract, and the app sidebar resolves the shared use case through Koin. The common Koin and repository test, Android linkage, iOS, and desktop target matrix passed in run `40009471058`; production GMS and FOSS graph integration remain in runs `40009471013` and `40009498970`.

The current search-history slice adds `shared-kmp/search-core` with a platform-neutral entry model, observation and mutation repository contract, and a common Koin command use case. Android's `RoomSearchHistoryRepository` adapts the existing Room DAO, while search UI, suggestions, and privacy settings now go through the shared contract. The Android Room entity and schema are unchanged. These local changes have not yet been pushed or verified by Actions.

The persistence engine for iOS and desktop is not selected yet. An isolated Room KMP prototype is configured under `migration-spike/room-kmp-proof/`, using Kotlin 2.3.10, AGP 9.4.1, KSP 2.3.6, Room 2.8.4, and the bundled SQLite driver. Run `39994422571` passed code generation and compilation for a representative DAO on Android, iOS simulator, and Windows desktop. Run `39995675459` additionally passed an in-memory insert and Flow query on Windows desktop and the iOS simulator; Android passed code generation/compilation only. The Android and desktop KSP checks run sequentially because they write Room schema output to the same path. This still does not use the app's schema 40, persistent files, real repository operations, migrations, or backup/import, and does not select Room for new clients.

## Next proof gates

1. Room KMP code-generation and target compilation proof: complete in Actions run `39994422571`.
2. Representative Room KMP runtime/query smoke proof: complete on Windows and iOS simulator in Actions run `39995675459`; Android compile/codegen passed there.
3. Trace library, history, playlist, lyrics, and settings consumers to their current DAO/store entry points; define repository/settings contracts around the operations clients actually call. A source scan matched 59 files; history count/grouping and playlist observation are KMP-verified, while production GMS/FOSS integration is in progress.
4. Prototype a disk-backed schema migration, real repository operations, and backup/import rollback checks. Keep Android schema snapshots and backup behavior unchanged.
5. Choose new-client persistence after those results are reviewed; only then begin user-data migration work.
