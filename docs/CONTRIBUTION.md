# Contributing

How to set up, build, and work on Dotnote for Android. Read [ARCHITECTURE.md](ARCHITECTURE.md) for the design before a non-trivial change.

---

## 1. Prerequisites

- **Android Studio** (current stable). It bundles a JDK and the Gradle tooling.
- **JDK 17** for command-line builds (`java -version`). Set it as the Gradle JDK in Android Studio too.
- **Android SDK** with platform 37 (compile) and 36, plus current build-tools (SDK Manager). `ANDROID_HOME` should point at it (on macOS usually `~/Library/Android/sdk`).
- A device or emulator on Android 8.0+ (API 26). Test on at least one recent Android (API 34+) and one old one (API 26–29), since SAF and the Photo Picker behave differently.
- Optional, for lock compatibility checks: the [`age`](https://age-encryption.org) CLI and a desktop Dotnote build.

No global Gradle install is needed: always use the wrapper (`./gradlew`).

---

## 2. Build and run

```bash
./gradlew :core:test                 # fast pure-logic tests (JVM, no emulator)
./gradlew :app:assembleDebug         # debug APK
./gradlew :app:installDebug          # install on the connected device/emulator
./gradlew test                       # all JVM unit tests
./gradlew :app:connectedDebugAndroidTest   # instrumented / Compose UI tests (needs a device)
./gradlew lint                       # Android Lint, must be clean
./gradlew spotlessCheck              # ktlint formatting (spotlessApply to fix)
```

The debug build also has a spike screen that runs the Phase 0 checks (desktop-locked note, JGit sync, Live field) on the device: `adb shell am start -n com.dotnote.android/com.dotnote.spike.SpikeActivity`. Its code lives in `app/src/debug/` and never ships in release builds.

For manual testing, copy desktop's `fixtures/sample-vault/` (from the kunotes-gpui repo) onto the device, e.g. `adb push sample-vault /sdcard/Documents/`, and open it with "Open a folder". `Example.md` covers every preview element.

### Release builds

`./gradlew :app:assembleRelease` makes a signed APK, `./gradlew :app:bundleRelease` an `.aab` for the Play Store. Signing comes from `keystore.properties` in the project root (git-ignored), with `storeFile`, `storePassword`, `keyAlias`, `keyPassword`. Without it, release builds are unsigned. Back up the keystore and its password: without them no update can be published for the same app.

---

## 3. Project structure

See ARCHITECTURE §3. In short:

- `core/`: pure Kotlin/JVM logic, ported from desktop `kunotes-core`, with tests. No Android imports.
- `app/`: the Compose app. `vault/`, `lock/`, `sync/`, `settings/` hold Android-facing stores; `ui/` holds screens grouped by feature, with design-system components in `ui/dotline/`.
- `docs/`: this guide and ARCHITECTURE.md.

### Reading order for newcomers

1. The README screenshots, then run the app (what it does).
2. `ARCHITECTURE.md` §3–§4.
3. `core/.../VaultPath.kt`, `VaultFs.kt`, `VaultNode.kt`, then their tests.
4. `app/.../vault/VaultStore.kt` and `ui/files/`.
5. `ui/dotline/` (the components) and the debug-only component gallery.

---

## 4. Code conventions

### General
- Kotlin official style, enforced by ktlint (Spotless). Android Lint clean, warnings treated as errors.
- Every file starts with a short KDoc comment saying what it's for. KDoc on public declarations; otherwise comment *why*, not *what*.
- Prefer plain classes, data classes, sealed classes, and functions over clever generics or DSLs.
- Don't use `!!` in app code. Don't swallow exceptions (`runCatching {}` with the result ignored, empty `catch`). Show or log them (§6).
- Follow ARCHITECTURE §3 structure rules: one main type per file, at most two folder levels, files under ~400 lines, private by default.

### Compose patterns
- Screen composable takes its ViewModel; everything below it takes plain values and lambdas (stateless, previewable).
- Collect state with `collectAsStateWithLifecycle()`.
- Every reusable component gets a `@Preview` in both the dark and the light theme. Check new screens in both (`adb shell cmd uimode night yes|no`).
- Use theme values (`DotlineTheme.colors.*`, `.type.*`, `.spacing.*`), never hard-coded colours or sizes. Sizes belong in the theme or the component, not in the screen.
- No elevation (`tonalElevation = 0.dp`); the only shadow is the soft one under dropdown menus. Dotline rules: monochrome with red (`accent`) at most once or twice per screen, Doto only ≥ 24 sp, touch targets ≥ 48 dp, Lucide icons via `DotlineIcons`, styling after Nothing OS 5.0 (pill shapes, borderless fields, hairline dividers).
- Long-running work belongs in a store or ViewModel coroutine, never in a composable.

### Threading
- **No I/O on the main thread.** File access, encryption, and git run on `Dispatchers.IO` (or `Default` for CPU work).
- Saves run in the app scope so leaving a screen can't cancel them (ARCHITECTURE §4.3).

### Desktop compatibility
- Behaviour shared with desktop (names, sort order, hidden files, formatter transforms, conflict names, lock format) is ported **with its tests**. If you change one of these, check the desktop repo and keep both in step.
- Names must pass `Names.validateName` (Windows-strict, because vaults are shared across systems).
- Paths in `:core` are `VaultPath`, never string-joined, never a `Uri`.
- Text ranges are UTF-16 indices on code-point boundaries. Test emoji and CJK.
- Preserve line endings (`LineEnding`).

### Data safety (non-negotiable)
- Every write to a user file goes through `VaultFs.writeAtomic` (or `Lock.writeNote` for a locked note). A locked note is never written in plain text.
- Deletes go through `FsOps.trash` (moves to `.trash/`). Only the trash purge and the write-recovery pass may call `VaultFs.delete`.
- Pending saves are flushed when leaving a note, switching vaults, and on `ON_STOP`.
- Any change that touches loading or saving needs a manual test: type, then immediately leave the note / background the app / swipe it away from Recents, reopen, and check nothing was lost.
- Never log note text, passwords, vault keys, or tokens.

---

## 5. Testing

| Layer | How |
|---|---|
| `:core` | JUnit 5 on the JVM. Use `MemoryVaultFs`, or `FileVaultFs` on a temp dir for real-file cases. Ported desktop tests keep their names so the two suites can be compared. |
| Lock compatibility | Fixture files made by desktop (`core/src/test/resources/lock-fixture/`) must decrypt; files made by `:core` are checked with the `age` CLI before a release. |
| Stores / ViewModels | JVM tests with `kotlinx-coroutines-test` and a `MemoryVaultFs`. |
| UI | Compose UI tests (`createAndroidComposeRule`) for flows: create, rename, move, trash + undo, mode switch keeps bytes. |
| Manual | Every screen in the README feature list, on an old and a new Android version, before each release. |

Every bug fix in `:core` gets a regression test. New format transforms need tests for an empty selection, multi-byte text, and the start and end of the document.

---

## 6. Errors and logging

- `:core` throws `CoreError` (sealed) with user-facing messages; reuse desktop wording where the case exists.
- Stores catch, then emit `VaultEvent.Error`, shown as a snackbar.
- Log with the `Logger` wrapper (`debug`, `warn`, `error`), never `println`.

---

## 7. Dependencies

- All versions in `gradle/libs.versions.toml`. Reference them as `libs.…`.
- Before adding a library: is it maintained, does it work on API 26, what does it add to APK size, and do we really need it? `:core` dependencies must be plain JVM (no Android).
- Upgrades of Compose BOM, AGP, Kotlin, JGit, or kage go in their own PR with a smoke test on a device.

---

## 8. Git workflow

- Branch from `main`: `feat/…`, `fix/…`, `docs/…`, `chore/…`.
- Use [Conventional Commits](https://www.conventionalcommits.org/): `feat(files): move to folder`, `fix(save): flush on stop`.
- Keep PRs small: one feature or fix per PR.

### PR checklist
- [ ] `./gradlew test lint spotlessCheck` passes.
- [ ] New `:core` logic has tests; ported logic has the desktop tests.
- [ ] Data-safety rules (§4) respected; save/load changes tested manually.
- [ ] UI checked in dark and light (screenshot in the PR); README screenshots retaken if a shown screen changed.
- [ ] ARCHITECTURE.md updated if the design changed.

---

## 9. Scope guard

Dotnote is intentionally minimal: **open a folder, browse it, edit markdown.** Proposals for wikilinks, backlinks, tags, graph, plugins, or a sync service start as a discussion issue, not a PR. Anything that would make a vault look different to desktop Dotnote needs a matching desktop change first.
