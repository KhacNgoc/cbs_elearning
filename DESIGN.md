# CBS E-Learning Enterprise Design System (Impeccable Standard)

Design system and visual tokens for CBS E-Learning Admin and Learner portals, adhering to the Impeccable craft floor standards for enterprise Operate and Experience surfaces.

## 1. Visual World & Direction
- **Identity**: Bank-grade enterprise learning platform. Precision, trustworthiness, high scanability, restrained elegance.
- **Theme**: Deep space slate navy (`#090e17` ground, `#0f172a` panels, `#162238` elevated surfaces).
- **Brand Accents**:
  - Primary Accent: Precision Cyan / Sky (`#0284c7` primary, `#38bdf8` highlight)
  - Success / Mandatory: Banking Emerald (`#10b981`, soft tint `rgba(16, 185, 129, 0.12)`)
  - Warning / Optional: Warm Amber (`#f59e0b`, soft tint `rgba(245, 158, 11, 0.12)`)
  - Critical / Danger: Rose (`#f43f5e`, soft tint `rgba(244, 63, 94, 0.12)`)
  - Neutral Ramp: `#f8fafc` (titles), `#e2e8f0` (labels/body), `#94a3b8` (secondary), `#64748b` (muted/hints), `#334155` (borders).

## 2. Typography & Hierarchy
- **Font Stack**: Clean system sans (`Inter`, system-ui, -apple-system, sans-serif).
- **Scale (Tight Ratio 1.15 - 1.2)**:
  - Page Title: `1.4rem` – `1.5rem`, weight `700`, tracking `-0.02em`
  - Section Title: `1.05rem` – `1.15rem`, weight `600`, tracking `-0.01em`
  - Body & Form Controls: `0.84rem` – `0.875rem`, line-height `1.5`
  - Badges, Meta & Hints: `0.72rem` – `0.78rem`, font-variant-numeric `tabular-nums`
- **Rules**:
  - Numbers, codes, and sequence numbers strictly use monospace or `tabular-nums`.
  - No gradient text. Emphasis comes from weight, clear contrast, and semantic color tints.
  - Generous whitespace between major cards (24px / 1.5rem), tight grouping within form items (6px - 8px).

## 3. Surface & Depth Architecture
- **Layer 0 (Ground)**: `#090e17`
- **Layer 1 (Card / Container)**: `#0f172a` with `border: 1px solid rgba(255, 255, 255, 0.07)` and `box-shadow: 0 4px 20px -2px rgba(0, 0, 0, 0.45)`.
- **Layer 2 (Inputs / Sub-panels / Insets)**: `#131d31` with `border: 1px solid rgba(255, 255, 255, 0.1)`.
- **Layer 3 (Popovers / Dropdowns / Tooltips)**: `#1a263d` with `border: 1px solid rgba(56, 189, 248, 0.25)` and layered shadow `box-shadow: 0 10px 25px -5px rgba(0, 0, 0, 0.6)`.

## 4. Interactive Components & States
- **Every interactive element must have all 5 states**: Default, Hover, Focus-visible, Active, Disabled.
- **Buttons**:
  - Primary: High-contrast cyan/sky gradient with subtle glow on hover, `active:scale-[0.98]`.
  - Secondary / Outline: Translucent slate with crisp border, brightening on hover.
  - Destructive: Subdued rose outline, becoming vivid solid rose on hover.
- **Tables**:
  - Sticky contrasting header with subtle uppercase tracking.
  - Hover highlight on rows (`rgba(56, 189, 248, 0.03)`).
  - High-contrast semantic pill badges with status dots.
