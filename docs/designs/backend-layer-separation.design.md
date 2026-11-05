# Backend service and repository boundaries

Date: 2026-10-31
Status: Design proposed for approval.

## Selected architecture

Packages are grouped by domain first, then by layer: `<domain>/controller`, `<domain>/service`, `<domain>/service/impl`, `<domain>/repository`, `<domain>/repository/impl`, `<domain>/dto`, `<domain>/entity`. Shared packages such as enums, mapper, policy, config, and security stay at the backend root. The user selected service interfaces with implementations and repository interfaces with implementations. Do not introduce application/infrastructure package roots.

Dependency flow:

controller -> service interface -> <domain>.service.impl implementation -> repository interface -> <domain>.repository.impl implementation -> database

Services may collaborate through other service interfaces. Repository implementations must not depend on services or controllers. DTOs and entities must not depend on controller/service/repository implementations.

Use lowercase impl package names, matching existing <domain>.service.impl and Java package conventions.

## Responsibilities

### Controller

Handle HTTP binding, request validation at the transport boundary, response status/headers, and output streaming. Inject service interfaces. Do not access repositories, JDBC, EntityManager, SQL contexts, or persistence implementation classes. Return DTOs for structured application data, never JPA entities.

### Service and <domain>.service.impl

Put business service contracts in service and their concrete Spring Service classes in <domain>.service.impl. Interfaces expose use-case methods using DTOs and appropriate scalar arguments. Implementations resolve the current actor, make permission and business decisions, coordinate repositories, and own transaction boundaries.

Extract database operations into repositories. Keep business-derived response fields and orchestration in services; returning DTO projections from repositories does not require relocating business logic to persistence.

Do not turn stateless policies, validators, or value types into service interfaces simply because they currently reside in service. Classify these by their actual responsibility and prevent repository dependencies on service-owned helper types.

### Repository and <domain>.repository.impl

Put custom database contracts in repository and JDBC/EntityManager implementations in <domain>.repository.impl, with Repository annotations on implementations. Inject interfaces into services.

Existing interfaces extending JpaRepository retain framework-generated implementations. They already satisfy the interface/implementation separation; do not handwrite copies of Spring Data CRUD. Custom SQL implementations use explicit interfaces and concrete classes. Avoid bean ambiguity and accidental Spring Data repository discovery for custom contracts.

For read use cases, select the required columns and map rows/projections to DTOs. Filtering, ordering, authorized scope predicates, counts, aggregates, and pagination remain in the database. Services supply the authorized scope; database queries enforce that scope before paging or aggregation.

Entity-returning persistence methods remain valid internally for write use cases and business rules requiring managed entities. Entities do not become API responses. Do not convert all CRUD repositories to DTO-only contracts, which would interfere with updates, relationships, and dirty checking.

### DTO

Move shared filters and response records out of services/query implementations into dto. DTOs contain only fields required by existing API contracts; do not add entity graphs or expose entity relationships. Preserve JSON names, nullability, nesting, and pagination conventions during this structural refactor.

Query-internal SQL fragments, parameters, and persistence contexts are implementation details, not public API DTOs. Keep them inside repository implementations or persistence-specific collaborators.

## Scope and sequence

Apply this convention across the Java backend, in buildable increments:

1. Establish verification for the current working tree and preserve all existing uncommitted edits in the four query files.
2. Separate persistence and business logic in AdminListQueryService, LearnerDashboardQuery, ProfileAssessmentQuery, TrainingReportQuery, LearnerDashboardService, LearnerQuizEntryService, and LeaderboardReadService. Replace query implementations consumed by controllers with service interfaces and move shared data contracts to dto.
3. Route AdminOptionsController, NavigationController, LearnerDeadlineController, and LessonKnowledgeSourceController through service interfaces. Reuse existing services where responsibility matches; add narrowly scoped services otherwise.
4. Audit remaining controllers and business services. Convert concrete business service dependencies to service interfaces and move implementations into <domain>.service.impl. Update application wiring, security/configuration integration, and tests where these classes are referenced. Preserve utility/policy roles rather than generating empty abstractions for every class.
5. Remove reverse dependencies, concrete implementation imports outside wiring/tests, and service/query-owned response types from the affected public contracts. Check the full backend against the agreed layer rules.

Service class names remain recognizable. For example, LearnerDashboardService becomes the contract and LearnerDashboardServiceImpl implements it; LearnerDashboardQuery becomes an interface such as LearnerDashboardQueryRepository with LearnerDashboardQueryRepositoryImpl handling SQL. Final repository grouping may reuse existing repositories when the responsibility is the same.

## Compatibility and exclusions

Preserve routes, response schemas, permission outcomes, error behavior, page numbering, sorting, CSV export format, and transaction coverage. Preserve database schema and existing SQL semantics. Avoid new dependencies unless a required verification gate cannot be implemented with current test tooling.

Do not migrate to ports/adapters or replace JPA entities with a separate domain model. This is layered architecture with interface boundaries, not a claim of complete Clean Architecture isolation from Spring/JPA.

## Verification and acceptance

- Establish the existing test baseline before source changes.
- Compile production and test sources after each ownership move.
- Run affected query, service, controller, security, and Spring wiring tests, then the backend test suite.
- Run PostgreSQL-dependent tests with cbs.postgres.tests=true against a dedicated test database when available. Report skipped database tests explicitly.
- Preserve authorization scope before count/page boundaries, response JSON fields, stable sort order, and CSV behavior through existing tests and focused regressions where needed.
- Add executable architecture checks using existing tooling: controllers cannot access persistence; services cannot execute JDBC/EntityManager operations; repositories cannot depend on services/controllers; production consumers use service/repository contracts rather than implementation classes.
- Review dependency changes and the final diff. Existing uncommitted behavior changes must remain intact.

## Alternatives

- Moving query files alone: rejected because it preserves mixed responsibilities and reverse dependencies.
- Application ports with infrastructure adapters and a separate domain model: not selected by the user because it changes the current structure too extensively.
- Service/repository interfaces with impl packages: selected by the user; extends an existing convention already used by CourseService and CourseServiceImpl.

## Review gate

Before implementation, obtain user approval of this concrete design and perform independent EGM design review. Incorporate any accepted corrections, then prepare the implementation plan and execute the refactor. No product code has been changed at this stage.
