# Implementation Plan

This plan builds KuNotes for Android in phases. Each phase ends with something that runs on a device. Design details are in [ARCHITECTURE.md](../ARCHITECTURE.md) (§ refers to it) and screens in [ui/SPEC.md](../ui/SPEC.md) (numbers like 2.1 refer to SPEC §5).

Legend: `[ ]` todo · `[~]` in progress · `[x]` done

---

## Phase 0: Scaffold and spikes ✅ (Q5 moved to Phase 3)

**Goal:** an empty Dotline-themed app that builds, plus answers to the open questions in §12.

- [x] Gradle project: `settings.gradle.kts`, version catalog, modules `:core` (Kotlin/JVM) and `:app` (Android, Compose). `minSdk 26`, `targetSdk 36`, `compileSdk 37`, JVM 17, core-library desugaring.
- [x] Spotless + ktlint, Android Lint, JUnit 5 in `:core`.
- [x] CI (GitHub Actions): `spotlessCheck`, `test`, `lint`, `assembleDebug` (`.github/workflows/ci.yml`).
- [x] Debug-only spike screen (`app/src/debug/`, `SpikeActivity`) that runs Q2–Q4 on a device.
- [x] Package / application id decided (Q6): `com.kunotes.android`.
- [~] Spike Q2 (styles + hidden markers work; cursor/IME check in Phase 5): Live editor on `TextFieldState` + `OutputTransformation` vs `TextFieldValue` + `VisualTransformation` (hidden markers, per-line font size, cursor movement, Gboard + one CJK IME).
- [x] Spike Q3: `kage` reads a desktop `.kunotes-lock.age` + `Note.md.age` and writes files desktop can open.
- [x] Spike Q4 (JGit 5.13; token auth in Phase 9): JGit on API 26 and 36: clone over HTTPS with a token, merge with a conflict.
- [ ] Spike Q5 (moved to Phase 3, needs `SafVaultFs`): scan time of a 2,000-note SAF tree with `DocumentsContract`.
- [x] Record answers in ARCHITECTURE §12 (and update the affected sections).

**Done when:** CI is green, the app opens to a black screen with `KUNOTES` in Doto, and every §12 question except Q1 has a written answer.

---

## Phase 1: `:core` port (pure logic with tests) ✅

**Goal:** all non-UI behavior from desktop `kunotes-core`, ported to Kotlin with its tests. No Android dependency.

- [x] `VaultPath`, `VaultFs`, `MemoryVaultFs`, `FileVaultFs` (the File backend lives in `:core` too, it's plain JVM).
- [x] `NaturalOrder` (natord behaviour) and `VaultNode` scan: folders first, hidden rules, `.md` / `.md.age` only, `.img` hidden.
- [x] `Names.validateName` (names.rs).
- [x] `Paths`: title, breadcrumb, rename selection, remap, `canMoveInto`, ancestors, image file (paths.rs).
- [x] `FsOps`: unique names, create file/folder, rename (incl. case-only), move, import image, write recovery (fs_ops.rs + §7.2). `Trash`: `.trash/<day>/`, restore, purge by day (§7.3).
- [x] `Search`: flatten, filter, visible rows (search.rs).
- [x] `Cursor` (line/col, grapheme count) and `LineEnding` (cursor.rs, line_ending.rs).
- [x] `Format`: every formatter-bar transform (format.rs).
- [x] `OpenNotes`: open, pin, order, close, remap (tabs.rs, minus preview tabs).
- [x] `Live`, `LiveView`, `LiveTable` (live*.rs), with UTF-16 offsets.
- [x] `Lock`: create/change password, unlock, lock/unlock note, read/write note; desktop fixture tests (lock.rs).
- [x] `GitSync`: sync, conflict copy naming, `.gitignore` (git.rs) on JGit.
- [x] Tests for multi-byte text (emoji, CJK) in every text function.

**Done when:** `./gradlew :core:test` passes and covers each ported desktop test. ✅ 242 tests, including desktop lock fixtures and two-device git sync.

---

## Phase 2: Theme and Dotline components (SPEC §2–3) ✅

- [x] Fonts in `res/font` (Doto, Space Grotesk, Space Mono, TTF; Doto and Space Grotesk are variable fonts).
- [x] `ui/theme/` from `DotlineTheme.kt`; `KuNotesTheme` mapped onto `MaterialTheme`, no elevation.
- [x] Dark and light theme, following the system setting (window background and system bars too).
- [ ] Theme choice (System / Light / Dark) in Settings.
- [x] Icons (`ui/dotline/DotlineIcons.kt`): the 36 line icons from the mockups (+ `EyeOff`), 24 dp, 1.5 dp stroke.
- [x] Shared press scale and focus ring (`ui/dotline/Interaction.kt`).
- [x] Components: `ScreenTitle`, `BottomNav`, `ListGroup`/`ListRow`, `TreeRow`, `OpenNoteCard`, `BottomSheet` + `SheetHeader`, `QuickActionTile`, `PrimaryButton`/`SecondaryButton`/`DestructiveButton`, `SegmentedControl`, `TextField`, `DotMeter`, `DotArt`, `Snackbar`, `KuSwitch`, `RadioDot`, `DropdownMenu`, `FormatterBar` (shell).
- [x] A debug-only gallery screen (`app/src/debug/.../GalleryActivity`) with every component, checked on the emulator in dark and light.
- [ ] Move user-facing strings (button labels, content descriptions) into `strings.xml` when the screens are built.

**Done when:** every component has a dark and a light `@Preview` and matches the mockups side by side.

---

## Phase 3: Vault onboarding (1.1–1.3)

- [ ] `SettingsStore` (DataStore + kotlinx.serialization) and `VaultRegistry`.
- [ ] `SafVaultFs` with id cache and safe write (§7.1–7.2).
- [ ] Welcome (1.1) → Open folder sheet (1.2) → `OpenDocumentTree` + persisted permission; cancelled pick; revoked permission.
- [ ] Reopen the last vault at launch.
- [ ] Empty vault (1.3).
- [ ] Navigation shell: routes + `BottomNav` (FILES active; other tabs placeholder).

**Done when:** picking a folder on a device shows it as a vault, and it reopens after the app is killed.

---

## Phase 4: Files (2.1–2.6, 1.4)

- [ ] `VaultStore` (§4.1) with rescan on `ON_START`, after ops, pull-to-refresh, 10 s poll (§7.4).
- [ ] Files screen (2.1): header + count line, tree with expand/collapse, selected row, ⋮ and long-press.
- [ ] Open notes row (`OpenNotes` + persisted per vault).
- [ ] New note / folder sheet (1.4) with location picker and validation.
- [ ] Folder actions (2.2) and note actions (2.3), incl. copy path and share.
- [ ] Rename (2.4), Move to (2.5), Trash + Undo (2.6).
- [ ] Trash purge (30 days) on vault open.

**Done when:** create, rename, move, trash and undo work on the sample vault, and changes made in another app show up.

---

## Phase 5: Editor (3.1–3.4)

Order from SPEC §8: Source, then Preview, then Live.

- [ ] `EditorViewModel`: one buffer, load with line-ending detect, autosave 500 ms, flush on leave / `ON_STOP`, SAVED / SAVING state.
- [ ] Source (3.2): monospace field, gutter, span styling, status line with line ending.
- [ ] Formatter bar wired to `Format`, above the keyboard; status `LN, COL · CHARS`.
- [ ] Preview (3.3): commonmark-java renderer for every element in `Example.md`; Edit pill.
- [ ] Live (3.1): approach from Q2; hidden markers off the cursor line; task toggle; accent caret.
- [ ] Editor menu (3.4).
- [ ] Images: Photo Picker → `.img/`, shown in Live and Preview.
- [ ] Mode remembered per app. Switching modes never changes file bytes (UI test).

**Done when:** `Example.md` renders correctly in all three modes and the save-safety manual test passes.

---

## Phase 6: Search (4.1–4.2)

- [ ] Quick switcher: autofocus, count, highlighted matches, folder path, IME "Go", create note row.
- [ ] RECENT chips for an empty query.
- [ ] No results (4.2).

---

## Phase 7: Vaults (6.1–6.4)

- [ ] Change vault sheet (6.1).
- [ ] Vaults tab (6.2), incl. ⋮ on the open vault (SPEC known gap).
- [ ] Vault actions (6.3): open, rename in list, sync settings, notes password.
- [ ] Remove vault (6.4): release permission; optional "also delete the folder" (to system or `.trash`, decide).

---

## Phase 8: Locked notes (5.1–5.3)

- [ ] `VaultLock`: key in memory, 5-minute auto-lock, `FLAG_SECURE` on decrypted notes.
- [ ] Set password (5.1) with strength meter and match check.
- [ ] Locked note (5.2) → unlock → editor; saves through `Lock.writeNote`.
- [ ] Change password (5.3).
- [ ] Fingerprint unlock (§8.3).
- [ ] Round-trip check with desktop KuNotes and the `age` CLI.

---

## Phase 9: Git sync (7.1)

- [x] Decide Q1 (where synced vaults live): (a) app storage, `filesDir/vaults/<name>`; picked SAF folders can't sync (ARCHITECTURE §9.1, §12).
- [x] `TokenStore` (Keystore AES-GCM).
- [x] `SyncController` timing (§9.3) and `SyncWorker`, with unit tests.
- [x] Git sync screen (7.1): status widget, repository group, auto-sync switch, sign-in sheet, "can't sync" state for SAF vaults.
- [x] Clone flow (`CloneScreen`, designed in the Dotline style), error state (in the widget), conflict copies listed in the widget message.
- [ ] Try HTTPS token auth against GitHub on a device (Q4 follow-up).
- [ ] Vaults tab (6.2) "Clone from git" → `Clone` route (wire when the Vaults screen lands).

---

## Phase 10: Polish and release

- [ ] App icon (Dotline style), splash.
- [ ] Accessibility: TalkBack labels, focus ring, font scale up to 200%, reduced motion.
- [ ] Old/new Android device pass of the feature checklist below.
- [ ] Release signing, `.aab`, store listing with screenshots.

---

## Feature checklist

Run on an old (API 26–29) and a new (API 34+) device before each release.

| Feature | Old | New |
|---|---|---|
| Open folder, reopen after kill, revoked permission | [ ] | [ ] |
| Tree: sort, hidden files, expand/collapse | [ ] | [ ] |
| Create / rename / move / trash + undo | [ ] | [ ] |
| External change shows up | [ ] | [ ] |
| Live / Source / Preview on `Example.md` | [ ] | [ ] |
| Formatter bar, images | [ ] | [ ] |
| Save survives: leave note, background, swipe away | [ ] | [ ] |
| `\r\n` file keeps `\r\n` | [ ] | [ ] |
| Quick switcher | [ ] | [ ] |
| Vault switch, remove | [ ] | [ ] |
| Lock / unlock / change password / fingerprint | [ ] | [ ] |
| Locked notes open on desktop and vice versa | [ ] | [ ] |
| Git sync, conflict copy | [ ] | [ ] |

---

## Risks

| Risk | Impact | Mitigation |
|---|---|---|
| Compose text field can't do Live mode well (hidden markers, mixed font sizes, IME) | Live mode delayed or buggy | Spike in Phase 0 (Q2). Ship Source + Preview first; Live can follow in a point release. |
| SAF is slow and has no change events | Laggy tree on big vaults, stale lists | `DocumentsContract` with an id cache, background scans, light polling (§7.4). Measure in Q5. |
| SAF has no atomic replace | Half-written note on a crash | Temp + backup rename dance with a recovery pass (§7.2). |
| `kage` incompatible with desktop `age` | Locked notes unreadable across devices | Fixture tests both ways (Q3). Fallback: own minimal age implementation. |
| JGit needs a real file system and may lag on Android | Git sync limited to app-storage vaults | Q1 decided (a): only cloned vaults sync. |
| Behaviour drifts from desktop (names, sort, formats) | Vaults look different on each device | Port desktop tests verbatim; compatibility rules in CONTRIBUTION §4. |
