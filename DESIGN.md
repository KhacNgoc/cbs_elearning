# CBS E-Learning Enterprise Design System (Impeccable Standard)

Design system and visual tokens for CBS E-Learning Admin and Learner portals, adhering to the Impeccable craft floor standards for enterprise Operate and Experience surfaces.

## 1. Visual World & Brand Direction
- **Identity**: Bank-grade enterprise learning platform (BIDV Emerald & Gold). Precision, trustworthiness, high scanability, restrained elegance.
- **Theme Support**: Dual Light & Dark mode via semantic CSS variables (`:root` & `:root[data-theme="dark"]`).
- **Brand Accents**:
  - Primary Brand: BIDV Emerald Green (`--primary-color: #005B41` light / `#08785c` dark, `--primary-ink: #005B41` light / `#62dcb5` dark, `--primary-light: #e6f0ed` light / `#173b33` dark)
  - Secondary Brand Accent: Metallic Gold (`--accent-color: #D4AF37`, `--accent-secondary: #F5A623`)
  - Success / Mandatory: `--success-ink` (`#00694b` / `#7cddba`), `--success-surface` (`#edf7f2` / `#19382f`), `--success-border` (`#b5dace` / `#366a58`)
  - Warning / Optional: `--warning-ink` (`#925b09` / `#f7ce79`), `--warning-surface` (`#fffbeb` / `#372f20`), `--warning-border` (`#f5d78e` / `#75603b`)
  - Critical / Danger: `--danger-ink` (`#b42332` / `#ffadb5`), `--danger-surface` (`#fff1f2` / `#3c232c`), `--danger-border` (`#fecdd3` / `#7e3c4b`)

## 2. Concentric Border-Radius & Geometry
- **Outer Card (`.card`)**: `14px` with `1px solid var(--border-color)` and `var(--shadow-sm)`
- **Inset Containers / Popovers (`.sub-courses-picker`, `.searchable-dropdown-menu`)**: `12px`
- **Interactive Controls (`.form-control`, `.btn`, `.radio-card`, `.searchable-trigger`)**: `10px`
- **List Items & Action Buttons (`.dropdown-item`, `.course-checkbox-item`, `.btn-remove-allocation`)**: `8px`
- **Code & Step Badges (`<code>`, `.step-number`)**: `6px`
- **Status Pills & KPI Chips (`.type-pill`, `.mode-badge`, `.kpi-chip`, `.current-unit-chip`)**: `9999px` (pill shape)

## 3. Typography & Hierarchy
- **Scale**:
  - Page Title: `1.5rem`, weight `700`, tracking `-0.02em`
  - Section Title: `0.96rem` – `1.125rem`, weight `700`, tracking `-0.01em`
  - Body & Form Controls: `0.84rem` – `0.875rem`, line-height `1.5`
  - Badges, Meta & Hints: `0.72rem` – `0.78rem`, `font-variant-numeric: tabular-nums`
- **Rules**:
  - Numbers, codes, and sequence numbers strictly use monospace or `tabular-nums`.
  - Emphasis comes from weight, clear contrast, and semantic Emerald/Gold brand tints.

## 4. Modal Layout (Admin Pages)
- **Default component**: use `AdminModal` for new modals so the rules below apply automatically. Custom modals, including native `<dialog>` elements, must follow the same rules.
- **Fixed header & footer**: header and footer never scroll or shrink. Only the content area between them scrolls (`flex: 1 1 auto; min-height: 0; overflow-y: auto`). The dialog itself never scrolls.
- **Stable height**: a modal whose content loads or changes after opening (tables, search results, pagination, tabs, images) must keep one height, using `fixed-height` on `AdminModal`: `min(760px, viewport - margin)`. Loading, empty and full states all share that height.
- **Content-sized modals**: forms and confirmations whose content is known when they open may size to their content, capped at the viewport height.
