# Suraksha Saathi — Ink & Cobalt

The revised system defaults to a light interface with porcelain surfaces, navy typography and cobalt actions. The worker UI stays light even when the surrounding app uses dark mode. Dark tokens remain available as a future option but do not automatically apply.

The latest worker concept uses shorter labels such as “Start practice,” “Fire safety,” and “Continue”; larger assessment choices; and a working Help screen in English and Hindi. The home screen presents one recommended lesson and one secondary lesson, with less supporting clutter. Safety-critical scenario decisions retain their original meaning.

The goal is a calm, precise training interface with a clear next action and unmistakable safety feedback. Visual refinement comes from hierarchy, space, consistent geometry and semantic colour.

## Colour roles

| Role | Light | Dark | Use |
| --- | --- | --- | --- |
| Canvas | `#F6F7FB` | `#101720` | Application background |
| Surface | `#FFFFFF` | `#192331` | Lessons, choices and records |
| Secondary surface | `#EDF1F7` | `#222F40` | Quiet icon wells and contextual areas |
| Primary text | `#14263D` | `#ECF2FA` | Instructions, questions and titles |
| Secondary text | `#55657B` | `#AEBBD0` | Supporting information |
| Primary action | `#3157D5` | `#A4BAFF` | Main buttons, selection and active navigation |
| On primary | `#FFFFFF` | `#142550` | Text and icons on primary buttons |
| Featured lesson | `#ECF1FC` | `#1C2C45` | Quiet emphasis for the recommended module |
| Success | `#13664D` | `#84DABD` | Demonstrated safe decisions; pair with an explicit status |
| Warning | `#80520B` | `#EAC482` | Caution or pending validation |
| Danger | `#AC343B` | `#FFAFB5` | Unsafe choices and blocked routes |

All tokens, including paired semantic surfaces, are in [the token source](../design/tokens.json). Use semantic names in components. A successful decision uses `success`, not `primary`; an active navigation item uses `primary`, not `success`.

A secondary mineral-teal accent is available in the concept's design controls for comparison. Ink & Cobalt is the default and the canonical token set.

## Layout and hierarchy

- Use the spacing scale 4, 8, 12, 16, 20, 24, 32, 40 and 48. Prefer 24-unit screen gutters, reducing to 16 on narrow layouts.
- One prominent screen title, one recommended lesson, one primary action. Follow with a quieter lesson row.
- Use 16-unit panel corners, 12-unit control corners and 10-unit compact labels. The preview device frame has 28-unit corners; that frame is presentation chrome, not an Android UI element.
- Use neutral separators to organise records. Reserve stronger borders for interactive controls that need a visible boundary.
- Keep normal content opaque. Avoid translucent text backplates over unpredictable camera scenes; use a solid surface for essential AR instructions.
- Preserve readable spacing when Hindi expands. Wrap supporting labels instead of truncating safety information.

## Typography

Inter is the Latin face; Noto Sans Devanagari supports Hindi. Use regular and medium weights. Production Android targets are 32 sp for introductory display text, 24 sp for screen titles, 20 sp for section titles, 16 sp for body and controls, and 14 sp for supporting labels. Respect system text scaling.

The inline concept inherits host heading sizes and previews the font families and hierarchy. It loads preview fonts from Google Fonts. The Android application must bundle approved font files for offline use; web preview font loading is not evidence of offline availability.

Avoid uppercase instruction blocks and compressed letter spacing in Indic text. Santali font and shaping choices remain subject to the localisation review already specified in the blueprint.

## Component rules

| Component | Rule |
| --- | --- |
| Brand header | Small tonal emblem, readable wordmark, compact language selection |
| Recommended lesson | Tonal surface, compact icon, module reference, title, short description and one filled action |
| Additional lesson | Full-width actionable row; icon, text and chevron; no nested button |
| Main action | Minimum 56 dp height; primary fill with its paired foreground |
| Secondary action | Neutral surface with a visible control border; minimum 48 dp height |
| Assessment choice | Minimum 64 dp target; neutral before selection; never pre-colour the correct answer |
| Navigation | Compact selected pill inside a quiet bottom bar; icon and label remain visible |
| Progress | Thin, primary-coloured segments with a readable step count |
| Safe result | Green-tinted surface plus an explicit result; practical competence remains separately labelled |
| Unsafe result | Red-tinted surface, consequence text and a clear practice action |
| Pending record | Amber status with text; no green verified badge |

Keep native keyboard focus in the concept. The implementation specification calls for a visible focus outline, adequate target sizes, and tests of text scaling and screen-reader order. Muted text must remain legible, and colour must never carry a safety distinction alone.

## Application to the admin dashboard

Use the same semantic tokens with a neutral sidebar, primary selection, porcelain/ink canvas and simple table surfaces. Put training-status filters above the roster; keep worker names and evidence links visually prominent. Use coloured status labels only where they communicate a defined state. Avoid applying a different saturated background to every metric card.

## Verification scope

Colour pairs can be checked numerically against their intended surfaces; layout and interaction require rendered inspection. Passing selected contrast pairs does not establish full accessibility conformance. Android device testing, audio, Santali rendering and production implementation remain separate work.

The palette has 14 checked contrast pairs per theme, including text, semantic status and control boundaries. The lowest tested text contrast was 5.24:1 in light mode and 6.51:1 in dark mode. For the latest light-only concept, the English home and Hindi assessment screens were visually inspected; the assessment fitted a 360-pixel browser viewport without horizontal overflow. The rendered application reported a light colour scheme while the surrounding browser remained dark. Help navigation, language switching and the Hindi fire safe-choice transition were exercised. These checks apply to the concept preview, not a production Android application.
