# Backend layer separation

Date: 2026-10-31
Status: Superseded by backend-layer-separation.design.md after the user selected service/repository interfaces and implementations. No implementation changes made.

## Intent

Keep business decisions in services and database operations in repositories. Remove reverse dependencies and controller access to persistence. Preserve API behavior, permissions, query scope, pagination, ordering, transaction behavior, and database schema.

## Findings

Seven service-package classes directly use JDBC or EntityManager:

- AdminListQueryService
- LearnerDashboardQuery
- ProfileAssessmentQuery
- TrainingReportQuery
- LearnerDashboardService
- LearnerQuizEntryService
- LeaderboardReadService

Four controllers directly reference repositories:

- AdminOptionsController
- NavigationController
- LearnerDeadlineController
- LessonKnowledgeSourceController

LearnerDashboardQuery and TrainingReportQuery already have Repository annotations. Other classes mix business orchestration and database access. Shared response records nested in services/query classes couple consumers to those classes. LearnerDashboardQuery and ProfileAssessmentQuery also reference records owned by LearnerWorkspaceService.

The current backend uses controller, service, repository, entity, dto, and security packages. It is not yet a strict Clean Architecture implementation: services consume Spring Data repositories and persistence entities. Moving classes alone does not establish dependency inversion or isolate the domain from persistence.

Four query files already contain uncommitted edits. Preserve the working-tree versions, including their existing behavior, throughout the refactor.

## Proposed approach: separate existing layers first

1. Move database query execution and result mapping into repository/query classes with Repository annotations. Extract persistence methods from mixed services rather than moving whole services.
2. Keep use-case orchestration, current-user resolution, permission decisions, validation, and transactions in services. Repository queries must still enforce the authorized scope before counts, aggregates, and pagination. Do not fetch broad datasets and filter permissions in memory.
3. Route controllers through services, including report/profile reads and the four controllers listed above. Controllers retain HTTP request parsing, response headers, and response streaming.
4. Move shared request filters and response records into dto packages. No repository may import service/controller types. Keep query-internal SQL context types private to the persistence layer where possible.
5. Preserve the existing SQL semantics, HTTP routes, JSON shape, CSV export behavior, error responses, and public pagination conventions. Retain transaction coverage for database access and streaming operations.
6. Update affected injection points and tests. Add executable architecture checks for the scoped boundaries: services do not use JDBC/EntityManager, controllers do not access repositories, repositories do not depend on services/controllers, and scoped controllers expose DTOs rather than query implementation types.

This establishes enforceable layer boundaries using the current package conventions. It does not claim to complete a full Clean Architecture migration.

## Alternative: full Clean Architecture migration

Introduce application-owned persistence ports, infrastructure adapters, domain models independent of JPA, and mappings between domain, persistence, and HTTP models. This aligns dependency direction with strict Clean Architecture, but affects far more than the query classes and requires a separate inventory of every use case and adapter. It should be explicitly selected rather than implied by a folder move.

## Verification

- Establish a test baseline before source edits and rerun the same relevant suites afterward.
- Compile production and test sources to verify constructor, import, and record migrations.
- Run existing dashboard, assessment, awards, administration, report, leaderboard, quiz-entry, and affected controller tests.
- PostgreSQL query tests are conditional on cbs.postgres.tests=true. Run them against a dedicated test database if available; otherwise report them as skipped, not passing.
- Verify authorization scope, counts before pagination, stable ordering, JSON fields, and CSV output through existing tests and focused regression tests where coverage is missing.
- Review the final dependency graph and diff, preserving all pre-existing edits.

## Approval boundary

The user has requested implementation and stated the desired responsibilities. The remaining architectural choice is whether this work should enforce boundaries within the existing layered backend or migrate the entire backend to strict Clean Architecture ports and adapters. Resolve that choice before preparing the final design and implementation plan.
