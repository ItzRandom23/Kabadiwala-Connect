# Kabadiwala Connect Android UI design system

## Direction

Dark-first, modern neon-purple and practical. The visual language is a premium circular-economy operations tool: confident cards, restrained gradients, clear status semantics and motion that improves orientation without delaying a network action.

## Palette

| Token | Dark | Light | Use |
|---|---|---|---|
| Background | `#090611` | `#F8F5FF` | App window and large empty areas |
| Background secondary | `#0D0818` | `#F0E8FF` | Navigation and grouped sections |
| Surface | `#151022` | `#FFFFFF` | Cards and forms |
| Surface elevated | `#211632` | `#F0E8FF` | Dialogs, selected cards and raised controls |
| Primary violet | `#9B5CFF` | `#6D28D9` | Primary actions, active navigation and progress |
| Strong violet | `#7C3AED` | `#6D28D9` | Filled buttons and selected controls |
| Neon magenta | `#F04DFF` | `#B91CC5` | Hero gradient endpoint and emphasis |
| Electric cyan | `#35D9FF` | `#007F9E` | Connectivity, QR, scan and information |
| Success mint | `#35F2A1` | `#087A50` | Confirmed/synced states |
| Warning amber | `#FFC857` | `#996000` | Review, pending and caution |
| Error coral | `#FF5C7A` | `#B42343` | Failed, rejected and hazardous states |
| Primary text | `#F8F3FF` | `#21142F` | Headings and high-priority values |
| Secondary text | `#B9AEC8` | `#675A73` | Supporting copy |
| Outline | `#403353` | `#CBBBDD` | Borders, dividers and focus affordances |

The implementation is in `app/src/main/java/com/irinteractivestudios/kabadiwalaconnect/ui/theme/Color.kt` and `Theme.kt`. Dynamic Material You colours are not enabled by default, so the brand remains stable across devices.

## Components and hierarchy

- Headings use large, high-contrast typography; supporting copy stays compact and readable.
- Cards use rounded Material shapes with violet or cyan borders only for selection, active sync, QR and other meaningful states.
- Hero sections use a violet-to-magenta gradient sparingly. Normal body text never glows.
- Dashboard totals use strong numeric hierarchy; route, pool and settlement evidence uses compact data cards.
- Status combines colour, icon and label. Colour is never the only signal.
- Loading uses existing state components and cached evidence is labelled rather than silently shown as current.
- Hazardous-material warnings use an error container, warning icon, explicit text and an acknowledgement control.

## Motion and performance

- Target transition duration: 180–300ms.
- Use spring/expansion motion only for selection and progressive disclosure.
- QR scanning, active sync and urgent actions may use a controlled pulse; no continuous decoration.
- Network actions are never delayed by animation. Existing Compose state uses immutable data classes and keyed lists.
- Image evidence is attached through the existing picker path; release networking remains HTTPS-only and Retrofit/OkHttp stays the single transport stack.

## Accessibility and localization

- Touch targets use the existing 48dp minimum patterns.
- Text and controls use theme on-colour pairs rather than low-opacity body text.
- Icons have content descriptions on actionable controls and status labels remain textual.
- `AppearanceManager` defaults to the dark flagship palette while System and Light remain user-selectable.
- English is the current complete language. Hindi core-journey translation remains `ANDROID PARTIAL`; all new backend fields are modelled without introducing Android-only backend strings.

## Role surfaces

- Household: Sell, pickup progress, nearby Kabadiwala selection, data-bearing device preparation and passport entry points.
- Kabadiwala: pickup desk, inventory, route advantage, demand/pool suggestions, QR handover and growth/safety passport.
- Recycler: demand, bulk-lot offers, formal QR scan, QC/variance and review states.
- Admin: service contract is capability-gated and intentionally not reachable from the three operational role navigations until an explicit Admin account surface is added.
