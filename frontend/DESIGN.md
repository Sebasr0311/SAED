---
name: SAED 2.0
description: One shared record for the whole residential complex, presented as the build manual of a complex that works.
colors:
  blanco: "#FFFFFF"
  perla: "#F7F9FE"
  azul-pastel: "#DFEBFF"
  lavanda: "#EEE8FF"
  menta: "#DCF4EB"
  indigo-saed: "#5267DA"
  indigo-deep: "#4155C6"
  azul-tinta: "#1D2942"
  tinta-suave: "#3A4766"
  verde-ok: "#0F6B4F"
  noche-ground: "#070B14"
  noche-page: "#0B1222"
  noche-paper: "#0F172A"
  noche-ink: "#EEF3FF"
  noche-ink-2: "#B4C0DC"
  indigo-luminoso: "#A3B0FF"
  menta-luminosa: "#86E3C1"
typography:
  display:
    fontFamily: "Archivo, Plus Jakarta Sans, system-ui, sans-serif"
    fontSize: "clamp(2.25rem, 1.5rem + 2.6vw, 3.625rem)"
    fontWeight: 850
    lineHeight: 1.02
    letterSpacing: "-0.025em"
    fontVariation: "'wdth' 112, 'wght' 850"
  headline:
    fontFamily: "Archivo, Plus Jakarta Sans, system-ui, sans-serif"
    fontSize: "clamp(1.875rem, 1.3rem + 2vw, 3rem)"
    fontWeight: 800
    lineHeight: 1.06
    letterSpacing: "-0.02em"
    fontVariation: "'wdth' 112, 'wght' 800"
  title:
    fontFamily: "Archivo, Plus Jakarta Sans, system-ui, sans-serif"
    fontSize: "clamp(1.25rem, 1.1rem + 0.6vw, 1.625rem)"
    fontWeight: 750
    lineHeight: 1.18
    letterSpacing: "-0.01em"
    fontVariation: "'wdth' 106, 'wght' 750"
  numeral:
    fontFamily: "Archivo, Plus Jakarta Sans, system-ui, sans-serif"
    fontSize: "clamp(3.5rem, 2.5rem + 3vw, 6rem)"
    fontWeight: 900
    lineHeight: 0.8
    letterSpacing: "-0.02em"
    fontFeature: "'tnum', 'lnum'"
    fontVariation: "'wdth' 125, 'wght' 900"
  lede:
    fontFamily: "Plus Jakarta Sans, system-ui, sans-serif"
    fontSize: "clamp(1.0625rem, 1rem + 0.35vw, 1.25rem)"
    fontWeight: 400
    lineHeight: 1.55
  body:
    fontFamily: "Plus Jakarta Sans, system-ui, sans-serif"
    fontSize: "1.0625rem"
    fontWeight: 400
    lineHeight: 1.65
  label:
    fontFamily: "Plus Jakarta Sans, system-ui, sans-serif"
    fontSize: "0.9375rem"
    fontWeight: 700
    lineHeight: 1.2
  caption:
    fontFamily: "Plus Jakarta Sans, system-ui, sans-serif"
    fontSize: "0.875rem"
    fontWeight: 600
    lineHeight: 1.4
  stamp:
    fontFamily: "Archivo, Plus Jakarta Sans, system-ui, sans-serif"
    fontSize: "0.75rem"
    fontWeight: 800
    lineHeight: 1.5
    fontVariation: "'wdth' 125, 'wght' 800"
rounded:
  square: "0px"
  piece: "3px"
  control: "6px"
spacing:
  stud: "24px"
  gutter-sm: "16px"
  gutter-md: "24px"
  gutter-lg: "32px"
  section-sm: "64px"
  section-lg: "104px"
components:
  button-primary:
    backgroundColor: "{colors.indigo-saed}"
    textColor: "{colors.blanco}"
    typography: "{typography.label}"
    rounded: "{rounded.control}"
    padding: "0 18px"
    height: "44px"
  button-primary-hover:
    backgroundColor: "{colors.indigo-deep}"
  button-primary-lg:
    backgroundColor: "{colors.indigo-saed}"
    textColor: "{colors.blanco}"
    rounded: "{rounded.control}"
    padding: "0 22px"
    height: "52px"
  button-glass:
    backgroundColor: "rgba(255, 255, 255, 0.86)"
    textColor: "{colors.azul-tinta}"
    typography: "{typography.label}"
    rounded: "{rounded.control}"
    padding: "0 18px"
    height: "44px"
  button-glass-hover:
    backgroundColor: "{colors.blanco}"
  button-quiet:
    backgroundColor: "transparent"
    textColor: "{colors.azul-tinta}"
    rounded: "{rounded.control}"
    padding: "0 18px"
    height: "44px"
  icon-button:
    backgroundColor: "{colors.blanco}"
    textColor: "{colors.azul-tinta}"
    rounded: "{rounded.control}"
    size: "44px"
  callout:
    backgroundColor: "{colors.blanco}"
    textColor: "{colors.azul-tinta}"
    typography: "{typography.caption}"
    rounded: "{rounded.square}"
    width: "300px"
  piece:
    backgroundColor: "rgba(255, 255, 255, 0.6)"
    textColor: "{colors.azul-tinta}"
    rounded: "{rounded.piece}"
  piece-new:
    backgroundColor: "rgba(82, 103, 218, 0.82)"
    textColor: "{colors.blanco}"
    rounded: "{rounded.piece}"
  part-piece-menta:
    backgroundColor: "rgba(220, 244, 235, 0.82)"
    textColor: "{colors.azul-tinta}"
    rounded: "{rounded.piece}"
  part-piece-lavanda:
    backgroundColor: "rgba(238, 232, 255, 0.82)"
    textColor: "{colors.azul-tinta}"
    rounded: "{rounded.piece}"
  units-control:
    backgroundColor: "{colors.blanco}"
    textColor: "{colors.azul-tinta}"
    rounded: "{rounded.square}"
    padding: "16px"
    width: "520px"
  stamp:
    backgroundColor: "{colors.perla}"
    textColor: "{colors.azul-tinta}"
    typography: "{typography.stamp}"
    rounded: "{rounded.square}"
    padding: "2px 8px"
  chip:
    backgroundColor: "transparent"
    textColor: "{colors.azul-tinta}"
    rounded: "{rounded.square}"
    padding: "2px 8px"
  top-strip:
    backgroundColor: "rgba(247, 249, 254, 0.68)"
    textColor: "{colors.azul-tinta}"
    height: "64px"
---

# Design System: SAED 2.0

## Overview

**Creative North Star: "Manual de armado del conjunto"**

The marketing surfaces read as a brick-build instruction book for a residential complex. Azul pastel pages are ruled by a 24px stud-dot module grid; every piece, page, call-out and control is outlined with an Azul tinta keyline; pieces are frosted-glass slabs that click onto one shared base. The piece added in the current step is Índigo, inventory pieces are Menta and Lavanda, earlier pieces sit ghosted behind dashed keylines, and oversized wide step numerals pace the reading. Hierarchy comes from piece size on a fixed S/M/L ramp and from type width, not from color washes.

Density is calm and generous: one spread, one model, one call-out at a time. Glass is the requested material, but it always sits inside a keyline; blur never stands alone as the edge of a surface. Dark mode is the same manual printed on night ink (Noche ground) with luminous keylines and luminous Índigo.

**Scope boundary.** This world is scoped to the `.saed-manual` class (tokens live as `--m-*` custom properties in `src/components/landing/manual.css`) and covers the public landing at `/` plus the shared `LandingNavbar` and `LandingFooter`, which self-scope and also render on `/suscripciones`. The authenticated app UI (AppShell, dashboards, shadcn `ui/*` components, `src/index.css` / Tailwind theme) keeps its incumbent system and must not inherit these tokens or rules. The `/suscripciones` body and `LandingPricing` have not been migrated into this world yet (see Don'ts).

**Key Characteristics:**
- 24px stud module: background dot grid, brick studs at 24px pitch, parts tray on the same grid.
- 1.5px Azul tinta keylines on every piece; dashed keylines for guides, ghosts, page gutters and unbuilt pieces.
- Frosted-glass slabs (6–18px blur) always bounded by a keyline.
- One Índigo "new piece" per step; Menta and Lavanda for inventory.
- Archivo variable at expanded widths (106–125%) for display, numerals and stamps; Plus Jakarta Sans for reading.
- Square 1:1 call-outs stamped "1:1" and footed "Datos de ejemplo".
- Expo-out motion: pieces drop along dashed arrows; reduced motion snaps between assembled states.

## Colors

The pinned SAED light identity on pastel paper, with a single saturated Índigo reserved for what is new or actionable.

### Primary
- **Índigo SAED** (indigo-saed): fill of the primary button, the new piece of each step (at 82% alpha over glass), the range fill, the active stage-track tick and step-mark ticks.
- **Índigo profundo** (indigo-deep): primary hover, the keyline and studs of the new piece, guide arrows, and Índigo text (active step numeral, link hover, final-step numeral) where Índigo SAED would be too light for text.

### Secondary
- **Menta** (menta): inventory pieces in the parts tray and built pieces in the finished set (82% alpha glass); also the solid background of the "ok" status row with **Verde ok** (verde-ok) text.
- **Lavanda** (lavanda): the Roles group in the parts tray, base plate and towers in the finished set, taken parking bays (82% alpha glass).

### Neutral
- **Azul pastel** (azul-pastel): the ground of every manual page, carrying the stud-dot grid (dots at 12% Azul tinta; 24% on stud-dense frames).
- **Perla** (perla): the page of a spread, step cards, parts page, footer, stamps and table headers.
- **Blanco** (blanco): paper for call-outs, the units control, spec table, FAQ and icon buttons; glass pieces are Blanco at 60% (86% for the base plate and glass buttons).
- **Azul tinta** (azul-tinta): all ink and every keyline. A softer keyline at 22% alpha divides rows inside a container only.
- **Tinta suave** (tinta-suave): secondary text, captions, notes, table data.

### Dark mode
- **Noche** (noche-ground / noche-page / noche-paper): ground, page, paper. Ink becomes noche-ink and noche-ink-2; keylines become rgba(214, 227, 255, 0.8) with soft keylines at 20%; Índigo text, focus and new-piece keylines become **Índigo luminoso** (indigo-luminoso); the new piece fill is rgba(98, 116, 232, 0.72); Menta and Lavanda drop to 13–16% alpha tints; ok text is **Menta luminosa** (menta-luminosa). Selection inverts to Azul pastel on Azul tinta.

### Named Rules
**The One New Piece Rule.** Índigo fill marks only the piece being added in the current step, the primary action and progress. Inventory and built pieces are Menta or Lavanda; earlier pieces are ghosted, never a second Índigo.

**The Pinned Palette Rule.** Light mode uses only the seven pinned identity colors plus their alpha glass mixes and three derived inks (Índigo profundo, Tinta suave, Verde ok). No new hues.

## Typography

**Display Font:** Archivo variable (wdth 62–125, wght 100–900), with Plus Jakarta Sans and system-ui fallback
**Body Font:** Plus Jakarta Sans (400–800), with system-ui fallback

**Character:** An expanded, heavy grotesque sets the manual's headings and numerals like printed instruction pages; a warm humanist sans carries every sentence a buyer reads.

### Hierarchy
- **Display** (850, wdth 112, clamp 2.25–3.625rem, 1.02): the hero headline only; balanced, max 15ch.
- **Headline** (800, wdth 112, clamp 1.875–3rem, 1.06): section titles.
- **Title** (750, wdth 106, clamp 1.25–1.625rem, 1.18): sub-section titles such as "Piezas en fabricación".
- **Numeral** (900, wdth 125, tabular lining figures, 0.8 line-height): giant step numerals (hero 4–6rem, steps 3.5–5.5rem, final 4.5–6rem); a 2rem small numeral shows the live unit count.
- **Lede** (400, clamp 1.0625–1.25rem, 1.55, max 46ch) and **Body** (400, 1.0625rem, 1.65, max 62ch; FAQ answers 68ch), both in Tinta suave.
- **Label** (700, 0.9375rem): buttons, strip links (600), units label (700, 1rem), FAQ questions (700, 1.0625rem).
- **Caption** (600, 0.875rem): model captions, call-out body, parts labels (700); large parts labels switch to Archivo 800 wdth 112.
- **Stamp** (Archivo 800, wdth 125, 0.75rem): "1:1" scale marks, "Próximamente", the brand version tag, part counts (0.8125rem).

### Named Rules
**The Width Ramp Rule.** Rank is carried by Archivo width and weight (106 → 112 → 125, 750 → 850 → 900), not by color or letter-spacing. Numerals are always the widest, heaviest cut.

**The Sentence Case Rule.** Every label, stamp and heading in the manual is sentence case with no tracking. The world has no uppercase tracked labels.

## Layout

A 24px stud module underlies the whole surface: the root ground repeats a radial dot every 24px, the parts tray is a CSS grid of 24px cells (S = 5×6, M = 7×6, L = 10×7 studs), and the complex model sizes itself in container units (`--m = 100cqw / 30`, aspect 30:22) so every piece snaps to the same module at any width.

Container max 1296px with 16 / 24 / 32px gutters at <640 / ≥640 / ≥1024px. Sections pad 64px vertically, 104px at ≥1024px; section heads are a 12px-gap stack, max 760px, 40px above content. The sticky top strip is 64px tall; scroll padding is 80px.

The hero is a two-page spread: stacked on mobile, split 1fr / 1fr at 1024px with a dashed gutter keyline between pages. The assembly is a 5fr / 7fr split at 1024px: steps scroll on the left (each min 60vh, threaded by a dashed guide rule with tick connectors to each numeral) while the model stage stays sticky at 88px on the right with a six-tick progress track. Below 1024px each step carries its own framed model and call-out. Specs split 7fr / 5fr, FAQ 4fr / 8fr, the final step 5fr / 6fr, the footer 2fr / 1fr / 1fr at 768px.

## Elevation & Depth

A hybrid of frosted glass and soft drop shadows under slabs. Depth reads as pieces sitting on the page: a tight negative-spread shadow directly under each slab, a larger one under whole pages and the sticky stage. Glass call-outs add a 1px inner highlight on their top edge. Ghosted pieces lose their shadow; hidden pieces float above their drop point.

### Shadow Vocabulary
- **Slab** (`box-shadow: 0 10px 18px -12px rgba(29, 41, 66, 0.5), 0 2px 4px -1px rgba(29, 41, 66, 0.12)`): pieces, primary button, call-outs, units control, step cards, spec table, FAQ.
- **Page** (`box-shadow: 0 28px 48px -28px rgba(29, 41, 66, 0.55), 0 6px 12px -6px rgba(29, 41, 66, 0.14)`): hero spread, parts page, sticky stage, film screen, final step.
- **Glass highlight** (`box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.7), <slab>`): glass call-outs; the strip uses an inset bottom highlight.
- Dark mode uses the same geometry in pure black at 0.9–0.95.

### Named Rules
**The Keyline-Bounded Glass Rule.** Every blurred surface (pieces 6px, glass button 8px, film screen 10px, strip 14px, glass call-out 18px) carries a 1.5px keyline. Blur never defines an edge on its own.

## Shapes

Edges are square or barely softened. Call-outs, pages, spreads, the units control, tags, chips, stamps and tables are square (0). Bricks and pieces take a 3px corner; buttons and icon buttons 6px, the focus ring follows at 6px. The only rounded silhouette is the resident phone piece (0.6 module), because it depicts a phone.

Solid keylines mean built; dashed keylines mean guide, ghost, page gutter or unbuilt. Bricks carry a row of studs above their top edge (60% dash at 24px or one-module pitch). Labels are stitched to their piece with a short 1.5px connector line, and parts labels with an L-shaped stitch.

## Components

### Buttons
Tactile, keylined and short of playful.
- **Shape:** gently softened (6px), 1.5px keyline, 44px min height (52px large), 18px / 22px horizontal padding, 8px icon gap, 18px icons.
- **Primary:** Índigo SAED fill, white label, slab shadow; hover deepens to Índigo profundo.
- **Glass:** Blanco at 86% with 8px blur; hover goes solid paper with an Índigo keyline.
- **Quiet:** transparent with no keyline; hover adds keyline and paper. In the mobile menu it keeps its keyline.
- **Press:** 1px downward nudge (150ms expo-out). Focus: 2.5px Índigo outline, 3px offset.
- **Icon button:** 44px square, paper fill, keyline; hover to ground; disabled at 40% opacity.

### Chips and Stamps
- **Chip:** square, 1.5px keyline, 0.75rem bold, 14px icon, transparent ground; used inside call-outs.
- **Stamp:** square, keyline, Perla fill, Archivo wide 0.75rem; marks "1:1" scale and "Próximamente" on unbuilt pieces.

### Cards / Containers
- **Corner Style:** square (0) for pages and call-outs; 3px for pieces.
- **Background:** Perla pages, Blanco paper, Azul pastel stud frames.
- **Shadow Strategy:** slab for objects, page for spreads (see Elevation).
- **Border:** 1.5px Azul tinta always; 1px soft keyline only between rows.
- **Internal Padding:** call-out 7–10px, units control 16px, step card 18px, final step 28px / 48px.

### Inputs / Fields
- **Units control:** paper card with keyline and slab shadow; label left, live 2rem numeral right; −/+ icon buttons flank a range whose 10px keylined track fills Índigo to the value and whose 22×28px paper thumb has a 3px corner. Caret is Índigo.

### Navigation
- **Top strip:** sticky, 64px, Perla at 68% with 14px blur and saturate, 1.5px keyline bottom. Brand mark in a keylined white square, wide Archivo wordmark and a keylined version tag. Links are 600 weight with a 2px Índigo underline on hover; actions are theme icon button, quiet "Iniciar sesión", primary "Ver planes". Below 1024px a menu toggle opens a panel separated by a dashed keyline with 1px soft dividers.

### Complex Model (signature)
The assembled complex on the stud grid. Pieces are absolutely placed in module units with states `hidden` (transparent, raised by its drop distance), `new` (Índigo glass, Índigo keyline and studs, top z-index), `ghost` (72% opacity, dashed keyline, no shadow), `built` (Menta, Lavanda for plate and towers, Índigo for the phone). Dashed Índigo guide arrows show the drop path. A stitched tag labels the new piece. The units control remaps tower count live.

### 1:1 Call-out (signature)
Square paper (or glass) box: keylined header with title and a "1:1" stamp, a grid body of rows, QR, bays or phone cards, and a dashed-top foot reading "Datos de ejemplo". Glass call-outs overlap the model so keylines show through the blur.

### Step Mark and Parts Tray (signature)
The step mark is a giant numeral read as "n de 6" over a row of keylined ticks, the current one Índigo. The parts tray sorts modules into S/M/L bricks by role on the stud grid, grouped under wide Archivo titles separated by dashed rules.

### Motion
One easing, expo-out `cubic-bezier(0.16, 1, 0.3, 1)`. Pieces transition 600ms (hiding 280ms); the hero intro drops the new piece 650ms after a 350ms delay with its guide fading in at 150ms; the final set settles 520ms with a 45ms stagger and a slight overshoot. UI state changes run 150ms; the FAQ chevron rotates 250ms. Under `prefers-reduced-motion`, transforms and keyframes are removed and pieces cross-fade 150ms between assembled states.

## Do's and Don'ts

### Do:
- **Do** outline every piece, page, call-out and control with a 1.5px Azul tinta keyline (luminous rgba(214, 227, 255, 0.8) in dark mode).
- **Do** keep every new surface on the 24px stud module and size model pieces in module units.
- **Do** use dashed keylines for guides, ghosts, page gutters and unbuilt items, solid for built ones.
- **Do** label every illustrative screen "Datos de ejemplo" in the call-out foot.
- **Do** give the current step exactly one Índigo piece and leave earlier pieces ghosted.
- **Do** set numerals and stamps in Archivo at 125% width with tabular lining figures.
- **Do** provide a reduced-motion path that snaps between assembled states.

### Don't:
- **Don't** let glass stand without a keyline, or use blur as the only edge of a surface.
- **Don't** use uppercase tracked labels, kickers or eyebrows above headings; the manual speaks in sentence case.
- **Don't** use hard offset shadows; depth is the soft slab and page shadow under pieces.
- **Don't** round call-outs, pages or tables; pill shapes and 16–24px radii belong to the incumbent app UI, not this world.
- **Don't** introduce hues outside the pinned palette, or a second Índigo piece in one step.
- **Don't** apply `--m-*` tokens or these rules to the authenticated app UI; it keeps its incumbent system.
