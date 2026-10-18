Dotline is a monochrome, dot-matrix design system for mobile apps. Everything is black, white and grey; one red signal colour marks what is live, active or irreversible. Type does the decorating: a dot-matrix display face for numbers and titles, a geometric grotesk for reading, a mono for labels and data.

## Principles

- **Less, then less again.** Show one idea per screen. If an element doesn't help someone act, remove it.
- **Red is a signal, not a colour.** `accent` appears at most once or twice per screen: the record button, the current-note dot, a destructive action.
- **Numbers are the hero.** Readouts (time, battery, steps) are set large in `dot-l` / `dot-xl`; their labels stay small in `label`.
- **Honest, mechanical feel.** Flat surfaces, hairlines, dots and pills. No gradients, no drop shadows, no glass.

## Content

- Voice is calm, short and literal: "Wi-Fi off", "Charging · 42 min left", not "Oops! Looks like…".
- Sentence case for titles, rows and buttons ("Quiet hours"). Uppercase only in `label` (mono overlines, tab labels, chips) and in `dot-m` screen titles ("SETTINGS").
- Address the user as "you" only when needed; prefer verbs ("Turn on", "Pair device").
- No emoji. Use a middle dot ` · ` to join short facts.
- Units in mono after the value, with a thin gap: `86` `%`, `21` `°C`.

## Colour

- Dark is the primary theme: `bg` is true black, content sits on `surface`, pressed and inset areas use `surface-raised`. Light mirrors it on `#f2f2f2`.
- Text: `ink` for primary, `ink-muted` for secondary, `ink-faint` for captions on `bg` and `surface` only.
- Primary actions invert: `ink` fill with `on-ink` text.
- `accent` is a fill (with `on-accent` on top); for red text use `accent-text`.
- `positive` confirms (charged, connected) and always comes with a word or glyph — status never relies on hue alone.
- Hairlines between rows use `line`; anything that marks a control's edge uses `line-strong` (3:1+ on every surface).
- Focus: a solid 2px `focus` ring, offset 2px, on every interactive element.

## Type

- `dot-xl` — lock-screen clock and one hero numeral. `dot-l` — widget values. `dot-m` — screen titles in capitals. Never set paragraphs or anything under 24px in Doto; it is for display only.
- `title`, `headline`, `body`, `body-s` (Geist) carry all reading text. Row titles are `headline`, subtitles `body-s` in `ink-muted`.
- `label` (Geist Mono, uppercase, +0.12em tracking) for overlines, widget labels, chips and tabs. `data` for readouts, status-bar text and row values.

## Layout & shape

- 4px grid. Screen margins `space-4`; gap between widgets `space-3`; between sections `space-5`; under a screen title `space-6`.
- Home screens use a 2-column widget grid of 168px squares (`radius-l`), with 2-wide widgets spanning both columns.
- Lists group into `surface` blocks with `radius-m`; rows are at least 56px tall.
- Buttons, chips, switches and segmented controls are pills (`radius-pill`). Every hit area is at least `touch-target` (48px).
- Bottom sheets use `radius-l` top corners over `scrim`.
- Depth comes from surface steps, not shadows.

## Dots

The dot is the system's motif. Use `dot` (4px) circles on a `dot-pitch` (8px) grid for meters (`DotMeter`), the active-tab indicator, live indicators and empty-state patterns. Lit dots are `ink` or `accent`; unlit dots are `line-strong` at 50% opacity.

## Motion

Quick and mechanical: 120–160ms ease for presses and toggles, buttons scale to 0.97 on press. No bounces. Respect reduced-motion by removing transforms.

## Iconography

24px [Lucide](https://lucide.dev) line icons on a 24px grid, 2px stroke (`stroke`), round caps and joins, drawn in `currentColor` so they follow `ink` / `ink-faint`. The components carry a small built-in set (`Dotline.glyphs`: home, grid, circle, sliders, bell); extend it in the same style. No filled or duotone icons, no emoji.

## Brand mark

Dotline has no logo yet: set the name in `dot-m` when one is needed.
