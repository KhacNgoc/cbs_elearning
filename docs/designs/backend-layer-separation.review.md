# Independent design review: backend layer separation

Date: 2026-10-31
Scope: `backend-layer-separation.design.md`, with current query, controller, service, security/configuration and query-test code inspected.

The selected layered architecture is feasible. No architectural blocker was found. The following implementation safeguards should be made explicit in the plan before source changes; they do not require expanding the approved architecture.

## Major: preserve permission semantics while defining the scope boundary

Affected scope: design lines 26, 30, 38, 46; `LearnerDashboardQuery.visible/facts`, `AdminListQueryService.courseScope/unitScope`, `TrainingReportQuery`, `LeaderboardReadService.page`.

The design assigns permission decisions to services and SQL scope enforcement to repositories without identifying the data contract between them. Current queries implement several distinct policies: legacy versus dynamic roles, assigned-course-only roles, owner versus program scope, inactive/cyclic organization hierarchies, learner grants and registration, and differing permissions for editor access. Passing only a user ID or a flattened set of allowed units can change these rules. Conversely, mechanically relocating the classes retains policy decisions in repositories.

Recommendation: record the internal scope contract and responsibility split in the implementation plan. Services resolve actor/policy inputs; repositories compile those inputs into the existing relational predicates. Preserve per-role distinctions rather than merging scopes across permissions. Shared SQL relations belong in a persistence collaborator, not an API DTO or service return type. In particular, replace the dashboard service's use of `facts().sql()` with a repository operation for the scoped resume eligibility check. Preserve the same scope relation for rows, counts, aggregates, assessment history and export. Cover representative legacy/dynamic, inactive-hierarchy and empty-scope cases with focused existing or regression tests.

## Major: preserve transaction coverage and security-check ordering at public service entry points

Affected scope: design lines 26, 54, 62; `LearnerDeadlineController.list/save`, `AdminOptionsController.options`, `LessonKnowledgeSourceController` and `LessonKnowledgeSourceService`.

Moving methods across classes and packages can remove transactional advice or move entity traversal beyond its transaction. Deadline writes modify a managed user's deadline collection; options traverse lazy entity relationships. Knowledge-source authorization currently occurs before invoking `list/save/outline`; `save` calls `outline` internally under the existing write transaction. Replacing this with public-interface calls or applying a class-wide read-only annotation can alter transaction coverage. Missing-lesson controller lookup currently uses `orElseThrow()` before the service's `ResourceNotFoundException`, so reusing the service lookup without checking error mapping can also change HTTP error behavior.

Recommendation: put the existing method-level read/write transaction settings on concrete public service implementations and verify that controller calls cross the Spring proxy. Keep authorization before persistence changes and remote outline requests. Preserve the existing missing-resource/error precedence or explicitly identify any pre-existing inconsistency instead of silently changing it. Test a persisted deadline update/reset and authorized/unauthorized knowledge-source operations, in addition to compilation.

## Major: migrate nested data contracts transitively

Affected scope: design lines 14, 44, 56; `LearnerDashboardService.Overview`, `LearnerDashboardQuery.Card`, `ProfileAssessmentQuery.Group/History`, `AdminListQueryService.groups`, and access/training option DTOs.

Moving only the top-level query records leaves DTOs referencing service-owned nested records. Dashboard cards and assessment records currently refer to `LearnerWorkspaceService.Resume/Attempt`; admin groups/options refer to `AccessManagementService.GroupDto/UnitDto` and `TrainingStructureService.DomainDto/ProgramDto`. That retains reverse dependencies when repository projections use them. Existing internal service methods also take entities, whereas the document's general service-contract wording allows DTOs/scalars only; preserving entity-based business collaboration must be distinguished from exposing entities through controllers.

Recommendation: inventory and move the complete reachable response-record graph to `dto`, retaining component names, types, nullability and JSON shape. Keep legitimate internal entity parameters for policies/managed-entity use cases when necessary; no entity responses may cross the HTTP boundary. Ensure architecture checks detect fully qualified and nested type references, not just explicit imports.

## Spring wiring verification

No current custom Spring Data scan configuration was found in the inspected application. Plain custom repository contracts must neither extend Spring Data's marker interfaces nor receive Spring Data repository-definition annotations. Give each exactly one annotated implementation; retain generated JPA repositories. Existing query tests use `@Import` of concrete query classes and one leaderboard test directly constructs its service, so those imports/constructions need migration alongside production wiring. Run a context-backed wiring test that resolves the new service and custom repository interfaces; compile-only validation cannot detect duplicate or missing beans.

The document addresses the feature-spec standard's problem, scope, alternatives, architecture, risks and verification at a suitable depth for an approved structural refactor. No new infrastructure, data migration, or separate domain model is required. This review did not execute tests or edit production source.
