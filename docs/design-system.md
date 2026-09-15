# Suraksha Saathi — Mineral Light

The Android app and trainer dashboard keep a light canvas, white reading cards and navy text. Coloured action surfaces now communicate purpose, with explicit labels retained for people who cannot distinguish those colours. Android's shared `Ui.kt` and the dashboard button variants implement the roles; `design/tokens.json` records the palette. Earlier inline concepts are historical design explorations.

| Role | Foreground / background | Meaning |
| --- | --- | --- |
| Primary | White / `#006963` | Start, continue, save and selected navigation |
| Learning | `#3157D5` / `#E9EEFF` | Lessons, reading, ordinary choices and record inspection |
| Camera / 3D | `#6541A8` / `#F1EAFF` | Camera and spatial equipment entry points |
| Review / caution | `#80520B` / `#FFF3DC` | Assessments, hints, review and attention needed |
| Utility | `#14263D` / `#EAF0F3` | Profiles, settings and returning |
| Destructive | Red with paired pale-red or white text | Explicit destructive actions, including dashboard revocation |
| Success / error | Labelled green / red result surfaces | Feedback after a decision; never an answer hint |

Unanswered assessment options share the same style. Teal is an action colour, not a declaration of certification or practical competence. Legacy or expired certificates must retain their explicit status even if their signature is valid.

Android buttons use bounded native ripples, keyboard-focus outlines, distinct disabled colours, medium-weight labels and system-respecting click haptics. The dashboard uses hover, focus and pressed states; reduced-motion settings disable button movement. Native buttons are at least 56 dp high and wrap text. Home language/navigation controls now size to their content instead of forcing a clipped single-height button at large font settings.

Cards remain quiet reading surfaces so the actions stand out. Spacing follows 4, 8, 12, 16, 20, 24 and 32 units; controls have 12–14-unit corners and cards use 16–20. Avoid transient overlays over instructional camera text. Action names, icons where present, headings and labelled statuses carry meaning independently of colour.

Native Android currently uses system sans-serif and its available Indic glyph support; the dashboard uses Arial/sans-serif. Neither a proposed font family nor emulator Hindi rendering establishes reviewed Santali support. Large-font and rendered-screen checks are recorded in `validation.md`; full TalkBack speech and representative-worker usability remain separate gates.
