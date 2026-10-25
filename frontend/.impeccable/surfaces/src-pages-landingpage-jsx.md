---
version: 1
slug: "src-pages-landingpage-jsx"
primary_target: "src/pages/LandingPage.jsx"
related_targets: ["src/components/landing"]
---

# Landing (/) surface brief

Scope: public landing at `/` (`src/pages/LandingPage.jsx` + `src/components/landing/*`). Visitor mode: Persuade.

Audience: property administrators and administration companies in Colombian propiedad horizontal evaluating SAED on desktop; residents and doormen secondary. Job: understand that SAED connects administration, gatehouse, residents and finances on one shared record, then go to `/suscripciones` (plans) or `/login`.
Proof: the product shown working with illustrative data, always labeled "Datos de ejemplo"; verified technical facts only (tenant isolation, Wompi PSE/cards, QR, activation link by email, browser-only, Ley 675 modules). No metrics, customers, testimonials, ROI or setup-time claims. Roadmap items (plate recognition, live quorum, push) may appear only as clearly unbuilt pieces.
Constraints: pinned palette (Blanco, Perla, Azul pastel, Lavanda, Menta, Índigo SAED, Azul tinta) is light mode; the previous dark palette (#070B14 ground) is dark mode; glassmorphism requested; 15s film in `public/videos/`; prices only from the API on `/suscripciones`; `LandingFooter` and `LandingPricing` are also used by `SuscripcionesPage`.

## Direction contract

THESIS: SAED is the build manual for a residential complex that works: each step clicks one module onto the same base while earlier steps stay ghosted. Refuses the category default of headline + product screenshot + row of equal feature cards.

OWN-WORLD: Azul pastel instruction pages ruled by a stud-dot module grid; every piece outlined with an Azul tinta keyline; pieces are frosted-glass slabs; the new piece of each step is Índigo, inventory pieces Menta and Lavanda; dashed guide arrows; oversized expanded step numerals; 1:1 call-out boxes with square corners and a keyline. Dark mode: the same manual printed on #070B14 with luminous keylines.

STORY: the visitor sees the complex assemble (base, gatehouse QR, packages and parking, dues and Wompi, assemblies and PQRS, resident phone), understands that every piece shares one record, watches the finished build in the film, checks the verified specs and the pieces still unbuilt, then takes the final step: choose a plan.

FIRST VIEWPORT: left page: headline and one-line offer, primary "Ver planes" button and the units control ("¿Cuántas unidades?") that remaps the model live; right page: the complex model on the stud grid at large scale, giant step numeral, the gatehouse piece dropping along its dashed arrow, a 1:1 call-out of the QR invitation in the corner. Nav is the manual's top strip.

FORM: brick build-instruction book (games-toys-physics-play-brick-build-instructions), first dealt challenger of a user-requested bolder re-roll, chosen by the user. seed:a45b971e9b3005d8

Signature interaction: scroll-pinned assembly — each step drops its piece along its arrow onto the same model (600ms expo-out), earlier pieces ghost; reduced motion snaps between assembled states. Raises: every label stitched to its piece (from the quilt); one units control remaps the whole model (from the type specimen); hierarchy by piece size on a fixed ramp (from the star atlas).

FINISH: unreviewed and undocumented is unfinished; this build ends with the finish review, the verdict, DESIGN.md, and every shipping raster carrying its provenance
