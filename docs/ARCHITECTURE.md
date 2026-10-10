# Architecture

KuNotes for Android is the phone version of [KuNotes](https://github.com/sofyan-rs/kunotes-gpui), a minimal markdown vault app. It is written in **Kotlin + Jetpack Compose** and styled with the **Dotline** design system (`docs/ui/design-system/`).

Scope is the same as on desktop: **open a folder, browse it, edit markdown.** There are no wikilinks, backlinks, graph view, tags, or plugins. Files are the only source of truth, so there is no database. Git sync with the user's own remote is optional.

Screens, flow, and copy are specified in [`docs/ui/SPEC.md`](ui/SPEC.md). This file covers how the app is built. Section numbers (§) refer to this file unless they say "SPEC §".

---

## 1. Goals and non-goals

### Goals
- Ship the screens in SPEC §5 with the Dotline look (SPEC §2–3).
- **Stay compatible with desktop.** A vault edited on Android and on desktop (directly, or through git) must look the same to both: plain `.md` files, the same locked-note format (`.kunotes-lock.age`, `Name.md.age`), the same conflict-copy names, the same hidden-file rules, line endings preserved.
- **Never lose user data:** debounced autosave, flush when leaving a note or the app, safe writes (§7.2), deletes go to a trash folder with undo.
- Stay responsive on large vaults: no file I/O on the main thread.
- Keep logic testable: pure logic lives in the `:core` module (plain Kotlin/JVM, no Android) with fast unit tests.

### Non-goals
- Wikilinks, backlinks, tags, graph, plugins, a KuNotes sync service.
- The desktop **Split** mode (screens are too narrow).
- Several vaults open at once. One vault is open; the others are saved in the list.
- Rich-text WYSIWYG. Live mode styles the markdown text; it never keeps a second document model.
- An in-app updater. Updates come through the store.

---

## 2. Tech stack

| Concern | Choice | Notes |
|---|---|---|
| Language | Kotlin 2.x, JVM target 17 | Coroutines + Flow for all async work. |
| UI | Jetpack Compose (BOM), Material 3 as the base | Dotline theme on top (§5). Material components are used only where they can be restyled to Dotline. |
| Navigation | Navigation Compose | Routes from SPEC §4. Sheets and dialogs are state, not routes. |
| State | `ViewModel` + `StateFlow` | One ViewModel per screen; shared state in app-scoped stores (§4). |
| Dependency wiring | Manual (`AppContainer` in `KuNotesApp`) | No DI framework. Easy to read, enough for this size. |
| Settings | DataStore + `kotlinx.serialization` JSON | One typed `Settings` object (§4.4). |
| Vault access | Storage Access Framework (`OpenDocumentTree` + `DocumentsContract`) | Behind a `VaultFs` interface (§7). |
| Markdown preview | `commonmark-java` + GFM extensions (tables, task lists, strikethrough) | Rendered to Compose blocks (§6.4). |
| Live / Source styling | Port of desktop `live.rs` (line scan, not a full parser) | §6.3. |
| Locked notes | `kage` (age in Kotlin) | Must read and write the same files as desktop's `age` crate (§8). |
| Biometrics | `androidx.biometric` + Android Keystore | Wraps the vault key (§8.3). |
| Git sync | JGit **5.13** | No system git on Android (§9). JGit 6/7 call Java 11+ APIs that Android lacks (crashed on API 30 in the Phase 0 spike); 5.13 is the last Java 8 line. |
| Background work | WorkManager | Sync after leaving the app (§9.3). |
| Images | Photo Picker (`PickVisualMedia`) | Copied into `.img/` next to the note, like desktop. |
| Logging | `android.util.Log` behind a tiny `Logger` in `:core` | No `println`. |
| Tests | JUnit 5 in `:core`; Compose UI tests + Robolectric where needed in `:app` | §11 in CONTRIBUTION. |

Exact versions live in `gradle/libs.versions.toml`.

**Application id:** `com.kunotes.android` (Kotlin packages under `com.kunotes`).

**SDK levels:** `minSdk 26` (Android 8.0), `targetSdk 36`, `compileSdk 37` (current AndroidX releases require compiling against 37; this doesn't change runtime behaviour). 26 gives `java.nio.file` (JGit), adaptive icons, and Keystore features we need.

**Build stack:** Gradle 9.8 (wrapper), AGP 9.4 with built-in Kotlin, Kotlin 2.4, JDK 17 toolchain.

---

## 3. Project structure

Two Gradle modules, mirroring desktop's two crates.

```
kunotes-android/
├── settings.gradle.kts
├── build.gradle.kts
├── gradle/libs.versions.toml        # all dependency versions
├── core/                            # LIBRARY: pure Kotlin/JVM, no Android, fully unit-tested
│   └── src/
│       ├── main/kotlin/com/kunotes/core/
│       │   ├── VaultPath.kt         # vault-relative path value type ("Projects/Plan.md")
│       │   ├── VaultFs.kt           # file-system interface + FsEntry (§7)
│       │   ├── FileVaultFs.kt       # VaultFs over java.io.File (§7.1)
│       │   ├── MemoryVaultFs.kt     # in-memory VaultFs for tests
│       │   ├── CoreError.kt         # sealed error type, user-facing messages
│       │   ├── TextRange.kt         # UTF-16 [start, end) range
│       │   ├── NoteNames.kt         # .md / .md.age rules, "Untitled 2" naming
│       │   ├── VaultNode.kt         # scan → tree, hidden rules, natural sort        (node.rs)
│       │   ├── NaturalOrder.kt      # a2 < a10, case-insensitive                     (natord)
│       │   ├── Names.kt             # validateName, Windows-strict                   (names.rs)
│       │   ├── Paths.kt             # title, breadcrumb, remap, canMoveInto          (paths.rs)
│       │   ├── FsOps.kt             # create/rename/move/import image, save recovery (fs_ops.rs)
│       │   ├── Trash.kt             # .trash/<day>/, restore, purge (Android only, §7.3)
│       │   ├── Search.kt            # flatten, filter, visibleRows                   (search.rs)
│       │   ├── OpenNotes.kt         # open-notes list, pin, order, remap             (tabs.rs)
│       │   ├── Format.kt            # formatter-bar transforms → Edit                (format.rs)
│       │   ├── LineFormat.kt        # line-prefix toggles used by Format
│       │   ├── Cursor.kt            # line/col, grapheme count                       (cursor.rs)
│       │   ├── LineEnding.kt        # detect/apply/normalize                         (line_ending.rs)
│       │   ├── Graphemes.kt         # user-perceived characters (emoji, flags)       (unicode-segmentation)
│       │   ├── WordSegments.kt      # word boundaries for word moves                 (unicode-segmentation)
│       │   ├── Live.kt              # markdown spans                                 (live.rs)
│       │   ├── LiveLine.kt, LiveView.kt, LineView.kt  # per-line view, marker hiding, offset mapping (live_view.rs)
│       │   ├── LiveTable.kt         # table cells                                    (live_table.rs)
│       │   ├── LiveBuffer.kt        # text + selection + undo                        (live_buffer.rs)
│       │   ├── LiveLists.kt, LiveText.kt  # task toggle, list continuation, whitespace rules
│       │   ├── Lock.kt, VaultKey.kt # vault key, lock/unlock notes (kage)            (lock.rs)
│       │   ├── GitSync.kt           # JGit sync                                      (git.rs)
│       │   └── GitConflicts.kt      # conflict copies "Plan (conflict 2026-10-10).md"
│       └── test/kotlin/…            # unit tests; fixtures in test/resources
└── app/                             # the Android app
    └── src/main/kotlin/com/kunotes/
        ├── KuNotesApp.kt            # Application; builds AppContainer
        ├── AppContainer.kt          # creates and holds the stores
        ├── MainActivity.kt          # single activity, NavHost
        ├── vault/
        │   ├── VaultStore.kt        # open vault: tree, selection, ops, events (§4.1)
        │   ├── VaultRegistry.kt     # saved vaults, SAF permissions
        │   └── SafVaultFs.kt        # VaultFs over DocumentsContract (§7.1)
        ├── settings/SettingsStore.kt
        ├── lock/
        │   ├── VaultLock.kt         # unlocked key in memory, auto-lock (§8.2)
        │   └── BiometricKey.kt      # Keystore wrap/unwrap (§8.3)
        ├── sync/
        │   ├── SyncController.kt    # when to sync, status (§9.3)
        │   ├── SyncWorker.kt        # WorkManager job
        │   └── TokenStore.kt        # Keystore-encrypted access token
        └── ui/
            ├── theme/               # Color, Type, Shape, Spacing, KuNotesTheme (from DotlineTheme.kt)
            ├── dotline/             # design-system components (SPEC §3): ListRow, BottomSheet, …
            ├── navigation/          # routes, BottomNav host
            ├── welcome/             # 1.1, 1.2
            ├── files/               # 1.3, 1.4, 2.1–2.6
            ├── editor/              # 3.1–3.4 (live/ subfolder for the Live field)
            ├── search/              # 4.1, 4.2
            ├── lock/                # 5.1–5.3
            ├── vaults/              # 6.1–6.4
            └── sync/                # 7.1
```

The right-hand comments name the desktop file each `:core` file ports. Port the **tests** too: they are the compatibility contract.

### Why two modules?

- **`:core`** has no Android dependency. Its tests run on the JVM in seconds, without an emulator. Anything that can be written without Android belongs here: paths, names, sorting, parsing, text transforms, encryption, git.
- **`:app`** does layout, lifecycle, permissions, and wiring. It calls `:core` for the real work.

`:core` never sees a `Uri` or a `Context`. It talks to files through `VaultFs` and `VaultPath` (§7), so the same code runs on a SAF tree, a plain folder, and an in-memory test file system.

### Rules for adding code

1. **Group UI by feature** (`ui/files/`, `ui/editor/`). The only shared UI folder is `ui/dotline/`, which holds the design-system components from SPEC §3 and nothing else.
2. **One main type per file, named after the file:** `VaultStore.kt` → `VaultStore`, `FileTreeScreen.kt` → `FileTreeScreen`.
3. **At most two folder levels under `com/kunotes/`** (`ui/editor/live/` is the deepest).
4. **Every file starts with a short KDoc comment** saying what it's for.
5. **Keep files under ~400 lines.** Past that, split by responsibility.
6. **Private by default.** `internal` for module helpers, `public` only for what another module needs.
7. **Composables are stateless where possible:** state in, events out. The screen-level composable takes the ViewModel; children take plain values and lambdas.
8. **Tests:** `:core` tests next to the code's package under `src/test`. `:app` UI tests under `src/androidTest` (or `src/test` with Robolectric).

### Where does my code go?

| I'm writing… | Put it in |
|---|---|
| Logic that doesn't need Android (parsing, paths, text math, encryption, git) | `:core` + tests |
| A Dotline component used by several screens | `app/ui/dotline/` |
| A screen or sheet | that feature's folder in `app/ui/` |
| State shared by several screens | an app-scoped store in `AppContainer` (`VaultStore`, `VaultLock`, …) |
| Anything touching `Context`, `Uri`, permissions, Keystore | `:app`, outside `ui/` (`vault/`, `lock/`, `sync/`) |

---

## 4. Runtime model

```
           MainActivity ── NavHost ── screens (Composable + ViewModel)
                                          │ collect StateFlow / call actions
                                          ▼
   ┌───────────────────────── AppContainer (app scope) ─────────────────────────┐
   │ VaultRegistry   VaultStore        VaultLock       SyncController  Settings │
   │ (saved vaults)  (open vault tree) (unlocked key)  (git status)    Store    │
   └───────┬────────────────┬─────────────────┬──────────────┬─────────────────┘
           │                │ :core FsOps      │ :core Lock    │ :core GitSync
           ▼                ▼                  ▼               ▼
        DataStore        VaultFs (SAF / File)  ─────────────▶ disk (source of truth)
```

### 4.1 `VaultStore`

The single source of truth for the open vault. App-scoped, so it survives screen changes.

- `state: StateFlow<VaultState>`: vault id and name, root `VaultNode`, expanded folders, selected path, note count, scan status.
- `events: SharedFlow<VaultEvent>`: `Error(message)`, `Moved(old, new)`, `Trashed(path, undo token)`, `Renamed(old, new)` (open notes and the editor follow these).
- Actions: `open(vault)`, `refresh()`, `toggle(folder)`, `select(path)`, `createNote`, `createFolder`, `rename`, `move`, `trash`, `undoTrash`. Each runs on `Dispatchers.IO` through `:core` `FsOps`, then rescans (or patches the tree) and emits.
- Errors are never swallowed: a failed op emits `VaultEvent.Error`, which the UI shows as a snackbar.

### 4.2 Editor state

`EditorViewModel` (one per open note) holds the **one markdown `String` buffer**, the selection, the view mode, the line ending, and the save state (`Saved` / `Saving` / `Error`). Live, Source, and Preview all read and write that same buffer. Switching modes never changes file bytes.

### 4.3 Threading

- Main thread: Compose and state updates only.
- `Dispatchers.IO`: every `VaultFs` call, scans, saves, encryption, git.
- `Dispatchers.Default`: span parsing for large notes, markdown parse for Preview.
- Saves run in the **application scope** (`AppContainer.appScope`), not `viewModelScope`, so leaving the editor can't cancel a pending save.

### 4.4 Settings

`SettingsStore` keeps one `@Serializable data class Settings` in DataStore:

```kotlin
@Serializable
data class Settings(
    val vaults: List<SavedVault> = emptyList(),   // id, display name, kind (SAF/File), tree URI or path
    val lastVaultId: String? = null,
    val openNotes: Map<String, List<SavedOpenNote>> = emptyMap(), // per vault: path, pinned
    val viewMode: ViewMode = ViewMode.Live,       // remembered per app (SPEC 3)
    val syncedVaults: Set<String> = emptySet(),   // auto-sync on
    val biometricVaults: Set<String> = emptySet(),
)
```

Secrets (git token, wrapped vault key) are **not** in `Settings`; see §8.3 and §9.2.

### 4.5 Lifecycle

`ProcessLifecycleOwner` drives app-wide reactions:

| Event | Reaction |
|---|---|
| `ON_START` | Rescan the open vault (§7.4). Start the auto-sync check (§9.3). Reload the open note if it changed on disk and has no unsaved edits. |
| `ON_STOP` | **Flush pending saves.** Enqueue a sync job if the vault is synced and dirty. Start the background lock timer (§8.2). |
| Process death | Nothing in memory matters: saves were flushed at `ON_STOP`, the vault key is gone (so locked notes are locked again). |

---

## 5. Theme and design system

- `ui/theme/` is `docs/ui/design-system/compose/DotlineTheme.kt` split into `Color.kt`, `Type.kt`, `DotlineRadius.kt`, `Spacing.kt`, `KuNotesTheme.kt`. `tokens.json` is the source of truth; if a token changes, change it there first.
- `KuNotesTheme` provides `LocalDotlineColors`, `LocalDotlineType`, `LocalDotlineSpacing` and maps onto `MaterialTheme` (`darkColorScheme(background = Bg, surface = Surface, …)`, SPEC §2).
- Fonts in `res/font`: Doto, Space Grotesk, Space Mono (TTF, not woff2).
- **Dark and light are both supported** and follow the phone's setting (`KuNotesTheme(dark = isSystemInDarkTheme())`); a System / Light / Dark choice in Settings comes later. Dark is the design reference: every screen is designed dark first, then checked in light. The window background before Compose draws comes from `res/values{,-night}/`, and system-bar icons follow the theme (`SystemBarStyle.auto`). Every component `@Preview` is shown in both themes.
- Rules enforced in review: red (`accent`) at most once or twice per screen; Doto never under 24 sp; `tonalElevation = 0` and `shadowElevation = 0` everywhere; touch targets ≥ 48 dp; press = scale 0.97 in 120–160 ms, no bounce.
- Icons: 24 dp line icons, 1.5 dp stroke, square caps, as `ImageVector`s in `ui/dotline/DotlineIcons.kt` (path data copied from the mockups). No filled icons, no emoji.
- Components live in `ui/dotline/` (SPEC §3), one per file, each with dark and light `@Preview`s. Press feedback (`pressScale`) and the focus ring (`focusRing`) are shared in `Interaction.kt`. A debug-only gallery (`adb shell am start -n com.kunotes.android/com.kunotes.gallery.GalleryActivity`) shows them all.

---

## 6. Components

### 6.1 Files (SPEC 2.1)

- `FilesViewModel` maps `VaultState` to rows with `:core` `Search.visibleRows(root, expanded)` and renders them in one `LazyColumn` (`TreeRow`, indent 28 dp per depth). Keys are the `VaultPath`, so rows keep identity across rescans.
- Sort and hide rules come from `:core` `VaultNode` and match desktop: folders first, natural sort, dot-files and `.img` hidden, only `.md` and `.md.age` shown.
- **Open notes** (`OpenNoteCard` row) is the mobile form of desktop tabs, backed by `:core` `OpenNotes` (port of `tabs.rs`): most recent first, pinned at the front (pin mark on the card), renames and moves remap entries. Long press on a card: Pin/Unpin, Close, Close others, Close all unpinned (closing only removes the card; the note stays). At most 8 unpinned cards are kept; the oldest drops off. The red dot marks the note opened last. No "preview tab" concept on Android.

### 6.2 Sheets and dialogs

Note actions, folder actions, new item, rename, move, change vault, vault actions, remove vault, editor menu: each is a composable shown from screen state (`ModalBottomSheet` restyled to Dotline, or a full-screen route for Move-To). Validation uses `:core` `Names.validateName` (Windows-strict, same as desktop) plus "no duplicate in this folder"; the error shows under the field in `accent-text`.

### 6.3 Editor (SPEC 3.1–3.4)

One `EditorScreen` with a `SegmentedControl` (LIVE | SOURCE | PREVIEW) over the same buffer.

**Source.** `BasicTextField` in Space Mono 13/24 with a line-number gutter. Styling comes from `:core` `Live.spans(text)` mapped to `SpanStyle`s (markers `ink-faint`, heading text bold, code muted). Markers are never hidden.

**Live.** Same field and same spans, plus `:core` `LiveView`: on every line except the one(s) holding the selection, marker characters are hidden and the line is styled like Preview (H1 30/36 bold, H2 22/28 semibold, body 16/26, inline code on `surface-raised`). Task boxes toggle on tap. The caret is a 2 dp `accent` bar.

Implementation plan, decided in the Phase 0 spike (PLAN):
1. Preferred: `BasicTextField(state: TextFieldState, outputTransformation = …)`. It gives built-in undo and IME handling, and maps offsets for hidden characters itself. Needs the current Compose version to allow styles from an output transformation.
2. Fallback: `BasicTextField(value: TextFieldValue, visualTransformation = …)` with an `OffsetMapping` built from `LiveView.toDisplay/toSource`. Undo then comes from a port of `live_buffer.rs`.

Either way the stored text is the raw markdown; the transformation only changes what is drawn.

**Formatter bar.** A horizontal row above the keyboard (`imePadding()`). Each button calls a `:core` `Format` function (`bold(text, selection) → Edit`) and applies the returned `Edit` to the buffer as one undo step. The status line shows `LN, COL · CHARS` from `:core` `Cursor` (and the line ending in Source).

**Images.** Photo Picker → copy into `<note folder>/.img/` with `FsOps.importImage` → insert `![](.img/name.png)` with `Format.images`. Live and Preview load images through `VaultFs` (not file paths).

**Text ranges.** Desktop uses UTF-8 byte offsets; Kotlin strings are UTF-16. In `:core` every range is a **UTF-16 index into the `String`, never inside a surrogate pair**. Character counts are grapheme clusters (`BreakIterator`), so an emoji counts as one. Tests cover emoji and CJK.

### 6.4 Preview (SPEC 3.3)

`commonmark-java` (+ tables, task list items, strikethrough) parses on `Dispatchers.Default`, 150 ms after the last edit (desktop `PREVIEW_DELAY`). A small renderer turns the AST into a `LazyColumn` of block composables: headings, paragraphs (`AnnotatedString` for inline styles and links), lists and task lists, blockquote with a 2 dp `line-strong` rule, fenced code on `surface` r16, tables in a bordered r16 box that scrolls sideways, images, rules. Read only; the floating **Edit** pill switches to Live.

### 6.5 Quick switcher (SPEC 4.1–4.2)

`Search.flattenFiles` + `Search.filterFiles` (case-insensitive name match, natural order), matched part highlighted. Empty query shows RECENT chips from `OpenNotes`. IME action "Go" opens the top result. "Create note “query”" uses the same path as New note.

---

## 7. Storage

### 7.1 `VaultFs`

`:core` defines one small interface; everything else in `:core` uses it.

```kotlin
interface VaultFs {
    fun list(dir: VaultPath): List<Entry>                 // name, isDir, size, lastModified
    fun exists(path: VaultPath): Boolean
    fun read(path: VaultPath): ByteArray
    fun writeAtomic(path: VaultPath, bytes: ByteArray)    // §7.2
    fun createDir(path: VaultPath)
    fun rename(path: VaultPath, newName: String): VaultPath
    fun move(path: VaultPath, intoDir: VaultPath): VaultPath
    fun delete(path: VaultPath)                           // only FsOps.trash/purge may call this (§7.3)
}
```

`VaultPath` is a vault-relative path with `/` separators (`"Projects/Plan.md"`, root = `""`). It is what `:core` stores, compares, and persists. Each backend resolves it.

| Backend | Used for | How |
|---|---|---|
| `SafVaultFs` | Folders picked with "Open a folder" (SPEC 1.2) | `DocumentsContract` queries on the persisted tree URI. A cache maps `VaultPath` → document id and is rebuilt on each scan. Avoid `DocumentFile` (too slow on large trees). |
| `FileVaultFs` | Vaults in app storage, e.g. cloned from git (§9.1); tests | `java.io.File` under a root folder. |
| `MemoryVaultFs` | `:core` tests | In memory. |

### 7.2 Safe writes

Desktop writes a hidden temp file then renames it over the target. Android:

- **`FileVaultFs`:** same as desktop: write `.Name.md.kunotes.tmp`, `fsync`, `renameTo`.
- **`SafVaultFs`:** SAF can't rename over an existing document, so: write `.Name.md.kunotes.tmp` fully and close it; rename the original to `.Name.md.kunotes.bak`; rename the temp to `Name.md`; delete the `.bak`. Every step leaves either the old or the new file under a name we can recover. On vault open, a recovery pass fixes leftovers (a `.bak` with no target → rename back; a stray `.tmp` → delete). All temp names start with `.`, so the tree never shows them and the desktop `.gitignore` (`.*.kunotes.tmp`) already ignores the temp.

Line endings: the editor works on `\n` text; `LineEnding.detect` on load and `apply` on save, so `\r\n` files stay `\r\n`.

### 7.3 Trash

Android has no system trash for SAF trees. Deleting moves the item into **`.trash/<day it was deleted>/` at the vault root** (`.trash/2026-10-10/Plan.md`; hidden, `Plan 2.md` on collision), and the snackbar's UNDO moves it back to where it was (`Trash.restore`). On vault open, `Trash.purge` deletes day folders older than 30 days. The day folder, not the file's edit time, decides the age, so a note untouched for months still gets its 30 days. Anything else in `.trash/` is never purged. That purge and the write recovery are the only code allowed to call `VaultFs.delete`. When git sync is connected, `.trash/` goes in `.git/info/exclude` (not synced), so `.gitignore` stays byte-identical to desktop's.

### 7.4 External changes

There is no reliable change notification for SAF trees (other apps write through the media provider or raw paths). So:
- Rescan on `ON_START`, after our own ops, after a sync, and on pull-to-refresh.
- While the Files screen is visible, it rescans every 10 s, or every 3× the last scan time on big vaults (see Q5).
- `FileVaultFs` vaults use `FileObserver` instead of polling.
- Scans run on `Dispatchers.IO` and are debounced (500 ms, desktop `DEBOUNCE`). The open note is reloaded only after a sync, or on `ON_START` when it has no unsaved edits (desktop ARCHITECTURE §7.3).

### 7.5 Vault onboarding and permissions

- "Choose folder" launches `ActivityResultContracts.OpenDocumentTree()`, then `takePersistableUriPermission(READ | WRITE)`, then saves a `SavedVault`.
- Android 11+ won't let the user pick the storage root or `Download/` itself; the explainer sheet (SPEC 1.2) should say "pick or create a folder".
- On open, check `persistedUriPermissions`. If the permission is gone, show SPEC 1.2 again with the explanation.
- Removing a vault (SPEC 6.4) calls `releasePersistableUriPermission`.

### 7.6 Autosave

The editor saves 500 ms after the last edit (desktop `SAVE_DELAY`) through `FsOps`/`writeAtomic` (or `Lock.writeNote` for locked notes). Pending saves are flushed when leaving the note, switching vaults, and on `ON_STOP`. The top bar shows ✓ SAVED / ● SAVING; a failed save shows the error and keeps the buffer.

---

## 8. Locked notes

### 8.1 Format (must match desktop `lock.rs`)

- Vault key: a random age X25519 identity, stored ASCII-armored and encrypted with the vault password (age **scrypt** recipient) in `.kunotes-lock.age` at the vault root.
- A locked note is `Name.md.age`: the note text encrypted to the vault key's recipient, **ASCII-armored** (git sees text).
- Minimum password length 8 characters (`MIN_PASSWORD_LEN`). Same error texts as desktop.
- **scrypt memory.** scrypt work factor n uses 2^n KiB of memory. The app sets `android:largeHeap="true"` (heap 192 → 512 MiB on the test emulator; 2^18 failed with OutOfMemory without it). Phase 0 timings on the emulator: unlock at 2^16 ≈ 0.3 s, 2^17 ≈ 0.6 s, 2^18 ≈ 1.5 s. Unlock accepts up to 2^20. New keys made on the phone use the factor chosen in Phase 8 (see Q7).
- Changing the password rewrites only `.kunotes-lock.age`; locked notes don't change.
- Compatibility test: `:core` tests decrypt fixture files made by desktop KuNotes, and desktop-made files decrypt files made by `:core` (checked with the `age` CLI).

### 8.2 `VaultLock`

Holds the unlocked vault key in memory, per vault. Unlocking runs scrypt on `Dispatchers.Default` (about a second, on purpose). The key is dropped, and open locked notes close, after **5 minutes without use** (desktop `IDLE_LOCK`); the timer keeps running while the app is in the background, and process death drops the key anyway. Screens showing a decrypted note set `FLAG_SECURE` (no screenshots, blank in Recents).

### 8.3 Fingerprint unlock

"Unlock with fingerprint" stores the **vault key** (not the password) encrypted with an Android Keystore AES key that requires `BIOMETRIC_STRONG` authentication (`setUserAuthenticationRequired(true)`, invalidated on new enrollment). Wrapping the key skips the slow scrypt step and keeps working after a password change on another device (the key doesn't change). If the Keystore key is invalidated, fall back to the password.

---

## 9. Git sync

### 9.1 Where synced vaults live

JGit needs a real `java.io.File` work tree; it can't use a SAF tree. **Decided (Q1, option a):** a git-synced vault lives in app-specific storage, `context.filesDir/vaults/<name>`, saved as a `VaultKind.File` vault (`VaultRegistry.addAppFolder`) and read with `FileVaultFs`. It is created by **Clone from git** (`ui/sync/CloneScreen.kt`: `GitSync.clone`, then the token is saved, the vault added, auto-sync turned on and the vault opened). A folder picked with SAF can't be synced; the SYNC tab explains that and offers **Clone from git**. Trade-off: other apps can't see the files of a cloned vault.

### 9.2 Behaviour (must match desktop `git.rs`)

- One sync = commit local changes, fetch, merge the remote, push.
- When a file changed on both sides, this device's version stays and the other is saved next to it as `Name (conflict YYYY-MM-DD).md` (`.md.age` keeps both extensions). Same naming function as desktop, ported with its tests.
- On connect, write desktop's `.gitignore` (same bytes) if the vault has none; `.trash/` goes in `.git/info/exclude`, so the two never conflict.
- Auth: HTTPS with a personal access token. The token is encrypted with a Keystore AES key and stored in app storage (not in `Settings`): `sync/TokenStore.kt`, AES-GCM, one file per vault in `files/git-sign-in/`, removed when the vault is removed. `security-crypto`'s `EncryptedSharedPreferences` is deprecated; don't use it.

### 9.3 `SyncController`

`sync/SyncController.kt`, app-scoped (built in `sync/SyncSetup.kt`, which also hooks it to `ProcessLifecycleOwner`). Status (`LAST SYNC`, up to date, files pushed/pulled, error, conflict copies) as a `StateFlow`; `changedFiles` emits the paths a sync changed so an open note can reload. The git calls go through the `SyncGit` interface, so the timing is unit-tested with a fake and virtual time. One `Mutex` keeps the foreground sync and the background job from running git at the same time. Timing follows desktop where it makes sense on a phone (only for vaults in `Settings.syncedVaults`):
- On app start (`ON_START`) when auto-sync is on.
- 60 s after the last edit while the app is open (desktop `AFTER_EDIT`): `NoteFiles.write` calls `noteSaved()`; trash/rename/move (`VaultEvent`s) count as edits.
- On `ON_STOP`, if something was saved since the last sync, a one-time WorkManager job (`sync/SyncWorker.kt`, network required, 3 s delay so flushed saves land first) runs `GitSync.commitAndPushQuickly` (20 s timeout). A save that lands after `ON_STOP` enqueues the job directly.
- **Sync now** button any time.

---

## 10. Errors and logging

- `:core` throws `CoreError` (sealed class) with user-facing messages, matching desktop wording where the case exists.
- `:app` catches at the store level and emits `VaultEvent.Error` → snackbar. No silent `runCatching {}` on I/O; every catch either shows the error or logs why it's safe to ignore.
- `Logger` in `:core`, backed by `android.util.Log` in `:app`. Never log note contents, passwords, keys, or tokens.

---

## 11. Feature summary

| Feature | Android | Notes |
|---|---|---|
| Open folder as vault | SAF picker | §7.5 |
| Several saved vaults, switch | ✓ | SPEC 6.x; desktop remembers only the last one |
| File tree, create, rename, move, trash + undo | ✓ | `.trash/` instead of OS trash (§7.3) |
| Live / Source / Preview | ✓ | Split dropped |
| Formatter bar | ✓ | Above the keyboard |
| Images | Photo Picker → `.img/` | |
| Autosave, safe writes | ✓ | §7.2, §7.6 |
| Open notes (tabs) | Cards on Files | §6.1 |
| Quick switcher | Search tab | |
| External changes | Rescan + poll | §7.4 |
| Locked notes | ✓, + fingerprint | §8 |
| Git sync | JGit + token | §9; Q1 open |
| Updater | — | Store updates |

---

## 12. Open questions

Each is answered in the Phase 0 spike or before the phase that needs it. Record answers here and in PLAN.

- **Q1. Git and SAF.** JGit can't work on a SAF tree. Options: (a) synced vaults live in app storage (`FileVaultFs`) — simple, but other apps can't see those files; (b) request "All files access" (`MANAGE_EXTERNAL_STORAGE`) and use `FileVaultFs` everywhere — simplest code, real atomic renames, `FileObserver`, but Play Store review needs a justification and the SPEC 1.2 privacy copy changes; (c) keep a JGit repo in app storage and mirror it to the SAF folder — keeps SAF, but two copies and a mirror to get right. ✅ **Decided (Phase 9): (a).** Synced vaults are cloned into `filesDir/vaults/<name>` (`VaultKind.File`); picked SAF folders can't sync, and the SYNC tab says so and offers **Clone from git** (§9.1). (b) or (c) can be revisited if users ask to sync a folder other apps can see.
- **Q2. Live editor API.** ✅ Mostly answered: Compose foundation 1.12 lets an `OutputTransformation` add `SpanStyle`/`ParagraphStyle` (`TextFieldBuffer.addStyle`) and hide characters (`replace(start, end, "")`); the Phase 0 spike screen shows headings at 30/22 sp, bold/italic/strike/code styled, and markers hidden off the cursor line. Still to check in Phase 5: cursor movement across hidden markers, IME (Gboard, Samsung, CJK), and inline-code background height.
- **Q3. kage compatibility.** ✅ kage 0.8 does scrypt and armor. Desktop-made key + note decrypt in JVM tests and on the emulator (bytes match); a Kotlin-made vault opens in desktop `kunotes-core`. Follow-up in Q7.
- **Q4. JGit on Android.** ✅ JGit 7 crashes on API 30 (`InputStream.readNBytes`, Java 11). JGit 5.13.5 works: two-device sync through a bare repo with a conflict copy passes on the emulator. HTTPS token auth still to try against a real host (Phase 9).
- **Q5. SAF performance.** ✅ Measured on the API 30 emulator: a full scan of 2,008 notes in 41 folders takes 0.7–8 s (off the main thread). So scans never overlap (one queued rescan at most), polling waits at least 3× the last scan time (10 s minimum), and character counts are read only for notes visible in the tree (cached by modification time). A faster scan (only expanded folders) is a later optimization if real vaults need it.
- **Q6. Package and application id.** ✅ Decided: application id `com.kunotes.android`. Kotlin packages stay under `com.kunotes` (`com.kunotes.core`, `com.kunotes.ui.theme`, …), and the `:app` namespace is `com.kunotes`.
- **Q7. scrypt work factor across devices.** Desktop's `age` crate picks the factor by timing the computer (~1 s), so a fast desktop may write 2^19–2^20, which needs 512 MiB–1 GiB to unlock: more than a phone gives an app even with `largeHeap`. Recommended fix on desktop: pin the factor (e.g. 18) when saving `.kunotes-lock.age`. Android side: ✅ new keys made on the phone use 2^17 (128 MiB, ~0.6 s on the emulator; `VaultLock.NEW_KEY_WORK_FACTOR`); still to confirm on a low-end phone.
