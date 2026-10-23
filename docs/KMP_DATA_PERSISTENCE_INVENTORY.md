# KMP Data and Persistence Inventory

**Updated:** 2026-10-23  
**Related plan:** [KMP migration plan](KMP_MIGRATION_PLAN.md)

This is the Phase 4 starting inventory. Android storage remains the production source of truth while new-target adapters are evaluated.

## Current Android storage

### Library database

- Room database file: `song.db`; current schema version: 40.
- The schema has 18 entities and 3 views. Checked-in schema snapshots cover versions 1 through 40.
- `MusicDatabase.kt` declares 36 Room auto-migrations and 6 manual migrations, including the explicit 39-to-40 migration. It also sets SQLite WAL/PRAGMA behavior and custom query/transaction executors.
- `DatabaseDao` contains 199 Room query and mutation annotations. It returns Room entities and relation graphs and also depends on Innertube DTOs and Android/JVM utilities.
- 58 app source files refer to `MusicDatabase` or `DatabaseDao`. Koin registers the Room instance and DAO. `AiPlaylistModifier` previously constructed a second database instance; it now receives the Koin-owned instance through its Compose call site.
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

The persistence engine for iOS and desktop is not selected yet. Compare candidates in an isolated KMP prototype against the actual repository/query needs, migration behavior, transactions, and export/import flow. Run that prototype on GitHub Actions before choosing an engine or migrating user data.

## Next proof gates

1. Trace library, history, playlist, lyrics, and settings consumers to their current DAO/store entry points; route direct database construction through the existing Koin graph.
2. Define repository and settings contracts using the operations the clients actually call, including flows and transactions.
3. Prototype the candidate database and settings adapters on Android, iOS simulator, and desktop Actions runners. Verify the existing Android schema snapshots and backup/restore path remain unchanged.
4. Choose new-client persistence after the prototype results are reviewed; only then begin data migration work.
