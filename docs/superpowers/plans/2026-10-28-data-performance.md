# Admin and Learner Data Performance Implementation Plan

> **For agentic workers:** Use superpowers:subagent-driven-development for independent file groups, with test-first changes and final independent review.

**Goal:** Bound list payloads, execute filtering/paging/aggregation in database queries, remove runtime mock selection, and stop unrelated page requests while preserving existing access and learning rules.

**Architecture:** Page-specific read endpoints and DTOs; parameterized queries with authorization applied before limit/count; cursor lists for sequential histories; bounded numbered pages where existing UI requires arbitrary jumps. Keep admin training/documents paths unchanged. Do not replace SQL pagination with Java subList. Preserve legacy endpoints used by excluded pages until their owner migrates them.

**Stack:** Vue/Pinia/TypeScript, Spring Boot 3.3/JPA/JDBC, PostgreSQL; H2 for parity/security integration tests.

## Task 1 — Learner dashboard

- [x] Test dashboard data contract with more records than page size, complete-scope counts, search/filter/sort and access restrictions.
- [x] Add separate learner summary and paged course-card reads, avoiding whole workspace/attempt history on dashboard. Use DB filtering/ordering/page boundaries and metadata projections; retain existing workspace contract for other consumers until migrated.
- [x] Update `Dashboard.vue` and dedicated store/API without touching admin training pages or their service. Preserve resume and mutation refresh behavior.
- [x] Run frontend learning tests and backend workspace/security tests.

## Task 2 — Admin lists and options

- [x] Test page-specific options do not read unrelated repositories; test authorized bounded lists and search/order ties.
- [x] Add page-specific in-scope options and DB-paged bank, users/groups, enrollment and moderation list contracts; retain excluded training options semantics.
- [x] Update their Vue consumers with server pagination, debounced filters and stale-response guards. Preserve group membership operations and role assignment.
- [x] Run focused frontend and backend authorization tests.

## Task 3 — Learner profile and leaderboard

- [x] Test no duplicate profile reads; lazy result/history reads; bounded leaderboard and deterministic ranks.
- [x] Add grouped assessment result pages and attempt history reads, database aggregation for statistics, bounded leaderboard projection/count queries. Preserve quiz revision, legacy scores, best/latest rules and scope.
- [x] Migrate `Profile.vue` and `Leaderboard.vue`; avoid full workspace loads when unnecessary. Keep endpoints independent from admin training files.
- [x] Run learner/profile and access regression checks.

## Task 4 — Shared requests, reports, runtime mock removal

- [x] Test shared startup does not fetch unrelated learner catalog/progress, direct quiz URLs still work, and chat selector works without global preload.
- [x] Remove unconditional catalog preload, deduplicate profile reads and migrate quiz/chat choices to targeted endpoints. Bound chat history in complete exchanges.
- [x] Replace report full snapshots with DB-filtered report facts, aggregated summaries/dimensions, paged details and separate export/history paths. Maintain completion, deadline and permission parity.
- [x] Remove production runtime mock branches in in-scope API modules and stores. Shared course/quiz modules require surgical branch-only edits; do not modify training/document endpoint contracts or their behavior. Test fixtures remain allowed.
- [x] Run frontend build, all frontend tests, relevant backend suite; inspect excluded-path diff and document PostgreSQL validation limits.

## Review focus

- Access filters before pagination/count, including mixed dynamic roles and descendant units.
- Null/deleted/unpublished content; empty chapters and required assessments.
- Aggregate totals do not depend on page length; grouped revisions paginate before histories load.
- Changing filter/page ignores older responses; failed reads expose retry.
- No edits to admin training/allocation/document UI or their contracts; no overwritten concurrent changes.

## Execution rulings

User's “triển khai sửa cho tôi” approves the proposed scope and execution. Use the existing checkout and recheck diffs during work to respect concurrent ownership; do not create or commit a branch without need. Perform independent Goldfish review before implementation. Add implementation decisions and test results to the delivery report.

## Verification record

Implementation and focused checks executed. See `../../data-performance-implementation.md` for fresh test results, preserved ownership boundaries and the existing failing security regression. Checked tasks mean work/checks were executed; they do not mean the entire baseline backend suite is green.
