# Backend Layer Separation Implementation Plan

> Execute continuously using Superpowers, with independent EGM design review and final code review. User approved implementation on 2026-10-31.

**Goal:** Enforce controller -> service interface -> implementation -> repository interface -> implementation, with DTO response boundaries.

**Spec:** backend-layer-separation.design.md

**Tech stack:** Java 17, Spring Boot 3.3.1, JPA/JDBC, Maven/JUnit.

## Global constraints

- Preserve existing working-tree changes, API schemas, query semantics, scope, ordering and transaction coverage.
- Use existing lowercase <domain>.service.impl convention and <domain>.repository.impl for custom persistence.
- Keep generated Spring Data implementations; do not duplicate CRUD.
- Do not introduce ports/adapters packages or new runtime dependencies.
- Work in current user checkout to preserve the uncommitted query changes. Snapshot saved at /tmp/cbs-before-layer-refactor.tar.gz. No automatic commits.

## Review focus

- Role scope stays paired with the same role before count/pagination.
- Streaming CSV executes inside its original transaction scope.
- Spring slice imports resolve concrete implementations after interface extraction.
- Nested DTO migrations preserve JSON fields and constructors.
- JPA-generated repositories coexist with custom repository implementations without ambiguous beans.

## Task 1: Baseline and architecture regression gate

- [ ] Run Maven tests with Java 17 and record failures/skips.
- [ ] Add LayerArchitectureTest using existing JUnit tooling: no controller persistence dependency; no JDBC/EntityManager in service; no repository-to-service dependency; business services expose interfaces with implementations in impl.
- [ ] Run the gate and observe existing violations before edits.

## Task 2: Query persistence boundaries

Files: seven mixed query/service classes identified in the design; new repository interfaces/impl classes; dto response models; affected query controllers/tests.

- [ ] Extract JDBC/EntityManager code into repository implementations with contracts.
- [ ] Keep current-user/permission and validation decisions in services; keep scope predicates before page/count boundaries.
- [ ] Move query response/filter records into dto; avoid repository imports of service types.
- [ ] Add service entry points for report/profile controllers and update tests.
- [ ] Compile and run relevant existing query/read tests.

## Task 3: Controller persistence boundaries

Files: AdminOptionsController, NavigationController, LearnerDeadlineController, LessonKnowledgeSourceController; their service contracts/impl classes; dto contracts and focused tests.

- [ ] Move business orchestration and repository calls into services.
- [ ] Keep transport binding and response headers in controllers.
- [ ] Add authorization/delegation regression tests and run them.

## Task 4: Backend service contracts and shared models

Files: remaining concrete Spring business services, <domain>.service.impl implementations, shared dto types, all consumers and tests referencing moved implementations.

- [ ] Move shared public data records to dto before extracting service interfaces.
- [ ] Extract public instance methods into service contracts and move bodies/annotations to impl.
- [ ] Preserve stateless helper classes and move helpers out of service only where dependency direction requires it.
- [ ] Update constructors, Spring test imports, mocks and wiring. Concrete implementations may be referenced only by wiring/tests.
- [ ] Compile production/test sources and run the architecture gate.

## Task 5: Integrated verification and review

- [ ] Run full Java 17 test suite; run PostgreSQL-gated tests against a dedicated available test DB or report explicit skips.
- [ ] Independently review final changes against the design; fix actionable findings.
- [ ] Run final architecture, compilation and relevant regression checks after fixes.
- [ ] Update README with responsibility rules and test commands; record outcomes and unresolved environment limits.

## Progress ledger

- Plan approved for execution by user's instruction to implement. Execution details are routine choices, not a new permission gate.
- Initial default-JDK baseline failed during testCompile (JDK 24/Lombok compatibility); rerunning with installed Java 17.
