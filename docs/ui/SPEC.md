# KuNotes for Android: UI spec

Mobile version of [KuNotes](https://github.com/sofyan-rs/kunotes-gpui), built with **Kotlin + Jetpack Compose**.
The design uses the **Dotline** design system: monochrome, dark first, one red signal colour, and dot-matrix display type.

This folder holds:

| Path | What it is |
|---|---|
| `SPEC.md` | This file: screens, flow, behaviour, Compose mapping |
| `design-system/README.md` | Dotline design system rules: principles, colour use, type, layout, dots, motion, icons, voice |
| `design-system/tokens.json` | Dotline tokens (colours for dark and light, type, spacing, radius, sizes). Source of truth for the theme |
| `design-system/compose/DotlineTheme.kt` | Starter Compose theme generated from the tokens (colours, type, spacing, radius, `DotlineTheme {}`); wire up the fonts |
| `design/screens/*.dc.html` | One file per screen (390×844). This is HTML markup with inline styles: every exact px value, colour and copy string is in here. Open it as reference; it is not meant to be run |
| `design/canvas.json` | Canvas layout. The `title` of each board gives the screen number used below |

**With Claude Code:** put this folder in the repo (for example `docs/design/`) and start with a prompt like:
"Read docs/design/SPEC.md, the design system in docs/design/design-system/ and the screen files in docs/design/design/screens/. Set up the Compose project with DotlineTheme and the shared components from section 3 first, then build the screens in the order of section 8."

> Reading a screen file: everything inside `<x-dc>…</x-dc>` is the screen. `<sc-for list="{{x}}">` is a loop over data built in the `<script>` at the bottom, and `{{name}}` is a value from that script. Links (`<a href="Other.dc.html">`) are the navigation between screens.

---

## 1. Product rules (from the desktop app)

- A **vault** is a folder on disk. Notes are plain `.md` files and the files are the only source of truth: no database.
- Changes made to files outside the app show up live.
- Every note has 3 view modes on the same buffer: **Live** (formats while you type and hides markers away from the cursor), **Source** (raw markdown with highlighting) and **Preview** (read only). The desktop **Split** mode is dropped on phones.
- **Autosave**: debounced atomic writes, so there's no save button.
- **Locked notes**: encrypted with `age`, one password per vault. A note locks again after 5 minutes. The key file is `.kunotes-lock.age` and a locked note is `Name.md.age`. Keep this compatible with desktop.
- **Git sync** is optional. A note changed on two devices keeps both versions.
- **Out of scope** (same as desktop): wikilinks, backlinks, graph view, tags, plugins.

---

## 2. Design tokens → Compose theme

Read `design-system/tokens.json` and `design-system/README.md`; `design-system/compose/DotlineTheme.kt` already turns them into Kotlin. Dark is the primary theme. Light values exist for a later light theme.

### Colours (dark)

| Token | Hex | Use |
|---|---|---|
| `bg` | `#000000` | Screen background |
| `surface` | `#111111` | List groups, cards, sheets |
| `surface-raised` | `#1c1c1c` | Inputs, pressed or selected rows, segmented-control well |
| `line` | `#262626` | Hairlines between rows |
| `line-strong` | `#6b6b6b` | Borders of secondary buttons, chips, checkboxes |
| `ink` | `#ffffff` | Primary text and icons; fill of primary buttons |
| `ink-muted` | `#a6a6a6` | Secondary text |
| `ink-faint` | `#808080` | Captions and inactive tab labels (on `bg`/`surface` only) |
| `on-ink` | `#000000` | Text on an `ink` fill |
| `accent` | `#e5211c` | **Signal only**: caret, active-tab dot, current-note dot, switch on, destructive fill |
| `accent-text` | `#ff5a52` | Red text (destructive rows, errors) |
| `positive` | `#4ad991` | Synced, match. Always paired with a word or glyph |
| `scrim` | `rgba(0,0,0,0.72)` | Behind sheets and dialogs |

Rule: red appears at most once or twice per screen.

### Type

Fonts: **Doto** (display, dot-matrix), **Space Grotesk** (text), **Space Mono** (labels and data). All three are on Google Fonts; bundle them in `res/font`.

| Style | Font | Size / line | Weight | Use |
|---|---|---|---|---|
| `dot-xl` | Doto | 72/72 | 900 | One hero number (sync "2 MIN AGO") |
| `dot-m` | Doto | 32/36 | 700 | Screen titles in CAPS (`PERSONAL`, `SEARCH`, `VAULTS`) |
| `title` | Space Grotesk | 22/28 | 500 | Sheet and dialog titles |
| `headline` | Space Grotesk | 17/24 | 500 | Row titles, big buttons |
| `body` | Space Grotesk | 15/22 | 400 | Paragraphs, buttons |
| `body-s` | Space Grotesk | 13/18 | 400 | Row subtitles, helper text |
| `label` | Space Mono | 11/14, +0.12em, UPPERCASE | 400 | Overlines, tab labels, chips |
| `data` | Space Mono | 13/18 | 400 | Paths, counts, status |

Never use Doto under 24 sp or for body text.

Editor content sizes: H1 30/36 bold, H2 22/28 semibold, body 16/26, inline code Space Mono 14 on `surface-raised`.

### Spacing, shape, size

- Spacing on a 4 dp grid: `4, 8, 12, 16 (screen margin), 24 (between sections), 32 (under a screen title), 48`.
- Radius: `8` (small), `16` (list groups, inputs, cards), `28` (sheet top corners, dialogs), `999` (pills: buttons, chips, segmented control, switch).
- Touch target ≥ 48 dp. List rows ≥ 56 dp (72 dp for two-line vault rows).
- Icon: 24 dp, line style, 1.5 dp stroke, square caps, mitred joins, `currentColor`.
- Depth comes from surface steps, never shadows. Set `tonalElevation = 0` and `shadowElevation = 0` everywhere.
- Motion: 120–160 ms ease, buttons scale to 0.97 on press, no bounce.
- Focus: 2 dp `ink` ring, offset 2 dp.

Suggested files: `ui/theme/Color.kt`, `Type.kt`, `Shape.kt`, `Spacing.kt`, plus `KuNotesTheme` mapping onto `MaterialTheme` (`darkColorScheme(background = Bg, surface = Surface, surfaceVariant = SurfaceRaised, primary = Ink, onPrimary = OnInk, error = Accent, outline = LineStrong, outlineVariant = Line …)`).

---

## 3. Shared components

Build these once. They repeat across screens.

| Component | Notes | Seen in |
|---|---|---|
| `ScreenTitle` | `dot-m` caps text, optional ▾ (opens vault switcher) | Files, Search, Sync, Vaults |
| `BottomNav` | 4 tabs: FILES, SEARCH, SYNC, VAULTS. 88 dp incl. inset. Active = `ink` + 4 dp red dot above icon, inactive = `ink-faint` | 2.1, 7.1, 6.2, 1.3 |
| `ListGroup` + `ListRow` | `surface` block, radius 16, hairline `line` between rows. Row: leading icon, title (`headline`), optional subtitle (`body-s` muted), trailing (count / chevron / ⋮ / switch / radio) | Most screens |
| `TreeRow` | `ListRow` + chevron (▸/▾) + indent 28 dp per depth | 2.1 |
| `OpenNoteCard` | 148×88, `surface`, radius 16, icon, name, mono caption (EDITING / 1 H AGO / LOCKED), red dot if current | 2.1 |
| `BottomSheet` | `surface`, top radius 28, handle = 5 dots (4 dp, `line-strong`), over `scrim` | 2.2, 2.3, 6.1, 6.3, 1.2 |
| `SheetHeader` | 48 dp icon tile (`surface-raised`, r16) + title + mono subtitle | 2.2, 2.3, 6.3 |
| `QuickActionTile` | 3-column grid, 72 dp tall, `surface-raised`, icon + `label` | 2.2, 2.3 |
| `PrimaryButton` | pill, `ink` fill, `on-ink` text, 56 dp (48 dp in sheets) | everywhere |
| `SecondaryButton` | pill, 1.5 dp `line-strong` border, `ink` text | everywhere |
| `DestructiveButton` | pill, `accent` fill, white text | 6.4 |
| `SegmentedControl` | pill well `surface-raised`, selected segment `ink` fill + `on-ink` text, `label` text with icon | Editor, New item |
| `TextField` | 56 dp, `surface-raised`, radius 16, border 1.5 dp: `ink` when focused, `line-strong` idle, `accent` on error. Label above in `label` style. Optional suffix (`.md`), trailing icon (clear, show password), trailing status (MATCH in `positive`) | Rename, New item, Passwords, Search |
| `DotMeter` | row of 4 dp dots, lit = `ink`, unlit = `line-strong` at 50% | Set password strength |
| `DotArt` | grid of 8 dp dots (4 dp gap) from a string pattern (`X` lit, `R` red, `.` unlit). Used for empty states and the lock | 1.1, 1.3, 4.2, 5.2 |
| `Snackbar` | `surface-raised`, border `line-strong`, radius 16, icon + text + `UNDO` (`label`, bold) | 2.6 |
| `KuSwitch` | 52×32 pill, on = `accent` track + white thumb, off = `surface-raised` + `line-strong` border | 7.1 |
| `RadioDot` | 22–24 dp ring; selected = `ink` ring + red centre | 2.5, 6.1 |
| `FormatterBar` | horizontal scroll row of 48 dp buttons on `surface`, sits above the keyboard (`imePadding()`), status line under it (LN, COL · CHARS) | 3.1, 3.2 |
| `DropdownMenu` | `surface-raised`, radius 16, 1.5 dp `line-strong` border, no shadow, rows 48 dp | 3.4 |

---

## 4. Navigation and flow

```
First launch ─▶ Open-folder explainer ─▶ (Android SAF picker) ─▶ Empty vault ─▶ New note ─▶ Editor
                    └─ Clone from git ─▶ Git sync

Bottom nav:  FILES (2.1) · SEARCH (4.1) · SYNC (7.1) · VAULTS (6.2)

Files ─┬─ tap note ─▶ Editor Live (3.1) ⇄ Source (3.2) ⇄ Preview (3.3)
       │                 └─ ⋮ ─▶ Editor menu (3.4) ─▶ Rename / Move / Lock / Trash
       ├─ tap locked note ─▶ Locked note (5.2) ─▶ Editor
       ├─ ⋮ on note ─▶ Note actions (2.3) ─▶ Rename (2.4) · Move (2.5) · Lock (5.1) · Trash ─▶ Undo (2.6)
       ├─ ⋮ on folder ─▶ Folder actions (2.2) ─▶ New note/folder (1.4) · Rename · Move · Trash
       ├─ New note FAB / folder+ ─▶ New item (1.4)
       ├─ search icon ─▶ Quick switcher (4.1) ─▶ No results (4.2)
       └─ "PERSONAL ▾" ─▶ Change vault (6.1)

Vaults (6.2) ─ ⋮ ─▶ Vault actions (6.3) ─▶ Notes password ─▶ Change password (5.3)
                                        └─ Remove vault ─▶ Confirm (6.4)
Lock note when the vault has no password yet ─▶ Set password (5.1)
```

Suggested routes (Navigation Compose):
`welcome`, `files?path=`, `editor/{noteId}?mode=live|source|preview`, `search`, `sync`, `vaults`, `password/set`, `password/change/{vaultId}`, `unlock/{noteId}`, `move/{itemId}`.
Sheets and dialogs are composables shown from screen state: note actions, folder actions, new item, rename, change vault, vault actions, remove vault, editor menu, open-folder explainer.

---

## 5. Screens

Numbers match the canvas board titles. File = `design/screens/<file>`.

### 1 · First launch

**1.1 Welcome**, `First-Launch.dc.html`
- Shown when no vault is saved.
- DotArt of a note page with a red caret dot. `KUNOTES` in dot-m, then the title "Markdown notes in a folder you own." and a muted paragraph under it.
- Buttons: **Open a folder** (primary) → 1.2, **Clone from git** (secondary) → 7.1.

**1.2 Open a folder**, `Open-Folder.dc.html`
- Bottom sheet that explains the system picker before it opens. It has 2 numbered steps (numbers in Doto) and a privacy note: "KuNotes can only read and write inside the folder you pick."
- **Choose folder** launches `ActivityResultContracts.OpenDocumentTree()`. Then call `takePersistableUriPermission` (read + write) and save the vault.
- Handle a cancelled pick (stay on the sheet) and a permission later revoked (show 1.2 again with an explanation).

**1.3 Empty vault**, `Empty-Vault.dc.html`
- Files screen with 0 notes: DotArt empty folder, "No notes yet", the hint that files added from other apps appear on their own, and **New note** / **New folder** buttons. Bottom nav is shown.

**1.4 New note / folder**, `New-Item.dc.html`
- Bottom sheet above the keyboard. Segmented control **NOTE | FOLDER** (state).
- Title switches between "New note" and "New folder". Location button "In /Projects ▾" opens the folder picker (2.5 style).
- Name field autofocused. The `.md` suffix shows only for notes. An empty name gives "Untitled" (then "Untitled 2" …).
- **Create**: a note opens in Live mode, a folder appears in the tree.
- Validate: no `/`, no duplicate name in the folder (show the error under the field in `accent-text`).

### 2 · Files

**2.1 Files (home)**, `Main.dc.html`
- Header: vault name in dot-m with ▾ (→ 6.1), search (→ 4.1), new folder. Subline in `data`: "9 notes · ● Synced 2 min ago".
- **OPEN NOTES**: horizontal row of `OpenNoteCard`s, the mobile replacement for desktop tabs, most recent first. Pinned notes stay at the front. A red dot marks the note being edited.
- **VAULT**: the tree in one `ListGroup`. Folders show a chevron + count; tapping a folder toggles it. Notes show name + "2 min ago · 645 chars". Locked notes use the lock icon and "Locked · encrypted". The selected note row has a `surface-raised` background. ⋮ on a note → 2.3, ⋮ on a folder → 2.2. Long-press can open the same sheets.
- Sort: folders first, then natural sort (`a2` before `a10`, like desktop). Hide dot-files and `.img`.
- FAB pill **New note** → 1.4. Bottom nav: FILES active.

**2.2 Folder actions**, `Folder-Actions.dc.html`
- Header "Projects · /Projects · 1 folder · 3 notes". Tiles: NEW NOTE, NEW FOLDER (both create inside this folder), COPY PATH.
- Rows: Rename folder, Move to…, **Move to trash** (red, subtitle "With everything inside: 4 items").

**2.3 Note actions**, `Note-Actions.dc.html`
- Header "Example · Example.md · 645 chars". Tiles: PIN, COPY PATH, SHARE (Android share sheet with the .md).
- Rows: Rename → 2.4, Move to… → 2.5, Lock note → 5.1 (or encrypt straight away if a password exists), **Move to trash** (red) → 2.6.

**2.4 Rename**, `Rename.dc.html`
- Sheet above the keyboard with the name field (text preselected), fixed `.md` suffix, clear button, and the helper "The file is renamed on disk. Open tabs follow it."
- Buttons **Cancel** / **Rename**. Same validation as 1.4. The same sheet renames folders (no suffix).

**2.5 Move to folder**, `Move-To.dc.html`
- Full screen. Radio list of every folder, indented by depth. The current folder is disabled and tagged CURRENT.
- A "New folder" row at the end. The primary button reads "Move to Projects".

**2.6 Moved to trash + Undo**, `Trash-Undo.dc.html`
- After a delete the item leaves the list and a snackbar shows "“Example” moved to trash · UNDO" (about 5 s).
- Use the system trash where possible: `MediaStore` `IS_TRASHED` or a vault `.trash/` folder; pick one. Undo restores the file.

### 3 · Editor

The editor has one buffer and 3 modes. The mode choice is remembered per app.

**3.1 Live**, `Editor-Live.dc.html`
- Top bar: back, note title + `PERSONAL / EXAMPLE.MD` (`label`), save state (✓ SAVED / ● SAVING), ⋮ → 3.4.
- Segmented control **LIVE | SOURCE | PREVIEW**.
- Content renders formatting inline. The markdown marker (`#`, `**` …) shows in `#6b6b6b` only on the cursor line. The caret is a 2 dp `accent` bar. Task checkboxes toggle on tap.
- `FormatterBar` above the keyboard: B, I, S | H1 (cycles H1–H3), link, image, checklist, bullets, code block, quote, table (and numbered list, rule, inline code as you scroll). The status line reads `LN 1, COL 8 · 645 CHARS`.
- Port `kunotes-core/live*.rs` logic (or rewrite in Kotlin): parsing with CommonMark (e.g. `org.commonmark` or `intellij-markdown`) + `AnnotatedString` / `VisualTransformation` on a `BasicTextField` with `TextFieldState`.

**3.2 Source**, `Editor-Source.dc.html`
- Monospace (Space Mono 13/24) with a line-number gutter (current line in `ink`, current line background `surface`). Markers are drawn in `ink-faint`, heading text bold, code muted.
- Same formatter bar. The status line adds the line ending (`LF`).

**3.3 Preview**, `Editor-Preview.dc.html`
- Read-only render: headings, emphasis, inline code, task lists, blockquote (2 dp `line-strong` left rule), fenced code on `surface` r16, tables in a bordered r16 box that scrolls sideways.
- Floating **Edit** pill → Live. Footer `READ ONLY · 645 CHARS`.

**3.4 Editor menu**, `Editor-Menu.dc.html`
- `DropdownMenu` from ⋮: Rename, Move to…, Pin note, Copy path, Share, divider, Lock note, **Move to trash** (red).

### 4 · Search

**4.1 Quick switcher**, `Quick-Switcher.dc.html`
- Desktop Ctrl/⌘+K. Autofocused field (border `ink`) with a result count.
- **MATCHES**: name with the matched part in `ink` semibold + underline and the rest muted, plus the folder path in `data`. The top result is highlighted (`surface-raised`) and opens on IME action "Go".
- "Create note “query”" row. With an empty query, show **RECENT** chips.
- Port `kunotes-core/search.rs` (name match, natural ordering).

**4.2 No results**, `No-Results.dc.html`
- DotArt magnifier, "No note named “budget”", "Search matches note names in this vault.", **Create note “budget”**.

### 5 · Locked notes

**5.1 Set notes password**, `Set-Password.dc.html`
- Shown on the first Lock note in a vault with no password.
- Password field (+ show), `DotMeter` strength + `STRONG · 16 CHARS`, confirm field with MATCH, checkbox "Unlock with fingerprint" (BiometricPrompt + Android Keystore to wrap the password).
- Warning card: "There's no reset…". Button **Set password and lock**.

**5.2 Locked note**, `Unlock.dc.html`
- Opening a `.md.age` note shows DotArt lock (red keyhole dots), "This note is locked", the 5-minute auto-lock explanation, the VAULT PASSWORD field, the fingerprint checkbox, **Unlock**, and **Not now**.
- On success open the editor. After 5 min, or when the app goes to the background (decide which), re-lock and drop plaintext from memory.

**5.3 Change notes password**, `Change-Password.dc.html`
- Fields: current password, new password, confirm. The error state is shown on the current field (`accent` border + `accent-text` message).
- Copy: "Your 3 locked notes stay locked. Only the password that opens them changes." This assumes only `.kunotes-lock.age` is re-encrypted with the new password, matching desktop. Check `kunotes-core/lock.rs`.
- The helper says other synced devices will ask for the new password.

### 6 · Vaults

**6.1 Change vault**, `Vault-Switcher.dc.html`
- Sheet from "PERSONAL ▾": radio list of saved vaults (name + `data` path · count). The selected vault has a red radio centre.
- **Manage** → 6.2. Buttons: Open folder → 1.2, Clone repo → 7.1, **Open {name}**.

**6.2 Vaults**, `Vaults.dc.html`
- Bottom nav VAULTS. The **ON THIS PHONE** list: the current vault is tagged ● OPEN; the others have ⋮ → 6.3.
- **ADD A VAULT**: Open a folder (→ 1.2), Clone from git (→ 7.1).
- Known gap: the open vault has no ⋮ yet. Add one, so password and remove are reachable for it too.

**6.3 Vault actions**, `Vault-Actions.dc.html`
- Rows: Open vault, Rename in list (display name only), Sync settings (→ 7.1, shows On/Off), Notes password (→ 5.3, or 5.1 if not set), **Remove vault** (red) → 6.4.

**6.4 Remove vault**, `Remove-Vault.dc.html`
- Centred alert dialog "Remove “Work”?": the folder and notes stay on the phone.
- Optional checkbox "Also delete the folder" moves it to trash; notes synced with git stay in the repository.
- **Cancel** / **Remove** (accent fill). Also release the persisted SAF permission.

### 7 · Git sync

**7.1 Git sync**, `Git-Sync.dc.html`
- Widget (`surface`, r28): LAST SYNC label, ✓ Up to date (`positive`), hero `2` (dot-xl) + `MIN AGO`, `main · 3 files pushed · 0 pulled`, **Sync now**.
- **REPOSITORY** group: Remote (URL in mono, truncated), Sign-in (Access token), Auto-sync switch ("After edits and when you open the app").
- Note: "A note changed on two devices keeps both versions, so nothing is overwritten."
- Android has no system git: use **JGit** (`org.eclipse.jgit`) with an HTTPS token encrypted with an Android Keystore key. Run sync in WorkManager.
- Not designed yet: the clone flow, the error state and the conflict list.

---

## 6. Data and platform notes

- **Storage**: SAF tree URIs via `DocumentFile`, or `DocumentsContract` for speed (DocumentFile is slow on big trees; cache the tree and refresh on change). Watch changes with a `ContentObserver` or periodic rescan for "live external sync".
- **Encryption**: an age-compatible Kotlin/JVM library (check one like `kage`) so files stay readable by desktop and the `age` CLI.
- **Images**: insert from the Photo Picker (`PickVisualMedia`) and copy into `.img/` next to the note (same as desktop).
- **Settings to persist** (DataStore): saved vaults + last opened vault, open notes order + pinned, view mode, auto-sync, biometric on/off.
- **No in-app updater**: updates come through Play Store.

---

## 7. Not designed yet (backlog)

- Git: clone flow (URL, token, target folder, progress), sync error, conflict list.
- Editor: insert image, table editing on a narrow screen, managing open notes (close, close others, pin marker on cards).
- Settings screen (theme, default view mode, font size, auto-lock time, fingerprint).
- Light theme (tokens already in `tokens.json`).
- ⋮ for the currently open vault on 6.2.

---

## 8. Suggested build order

1. Theme + fonts + shared components (section 3).
2. Vault onboarding (1.1–1.3) with SAF.
3. Files tree (2.1) + note and folder sheets (2.2–2.6, 1.4).
4. Editor: Source first (plain text + highlighting), then Preview, then Live.
5. Quick switcher (4.x).
6. Vaults (6.x).
7. Locked notes (5.x).
8. Git sync (7.x).
