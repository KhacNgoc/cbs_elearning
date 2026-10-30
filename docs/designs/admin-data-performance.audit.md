# Admin and learner data loading and database query audit

Date: 2026-10-28
Status: implementation authorized by the user and applied. Findings below record the initial audit; current delivery and verification are documented in `../data-performance-implementation.md`.

## Scope and ownership

The user excludes the **Đào tạo & tài liệu** menu because another developer is optimizing it.
`AdminLayout.vue` maps that menu to `admin.courses`, `admin.unit-allocation`, and `admin.documents`.
Exclude their pages, course and lesson editors, curriculum trees, allocation UI, AI document loading, and associated service/query changes. Also reserve curriculum/catalog code for that developer to avoid adjacent changes.

Remaining scope: overview and reports; exams and question bank; users, units, groups and enrollment approvals; roles, permissions and content moderation.

The user subsequently expanded the investigation to learner pages. Include learner dashboard, profile/results, leaderboard, quiz entry, course outline, lesson interactions and shared learner requests. The exclusion remains the admin training/document menu; it does not exclude learner pages. Shared course/outline APIs still require coordination with the other developer before edits.

Shared files require narrow edits and regression checks. Do not rewrite `trainingApi`, `courseApi`, `TrainingStructureService`, course repositories, shared DTOs or course/document sections of `AdminPortal.vue` as part of this work. Coordinate ownership before removing mock branches from those shared APIs. Recheck working-tree changes before each edit; do not overwrite concurrent work.

## Confirmed findings

| Priority | Location | Evidence and consequence |
| --- | --- | --- |
| High | `CoreTraining/src/App.vue:194-200` | Authenticated startup calls `fetchProgress()` and `fetchCourses()` regardless of the active page. An admin entry therefore starts learner requests that its page may not need. This is startup behavior, not evidence that every admin page mounts together. |
| High | `CoreTrainingAPI/src/main/java/com/bidv/coretraining/controller/AdminOptionsController.java`, `options()` | Every supported page causes queries and mapping for units, groups, courses, domains and programs. The page parameter affects permission checks but does not select which collections to load. Even a users/options request processes training collections. |
| High | `CoreTrainingAPI/src/main/java/com/bidv/coretraining/service/TrainingReportService.java:30-72` | Reads all courses and users, loads progress per permitted learner, and loops through learners and published courses. Computes progress, completion and assessment metrics in Java, with possible additional ORM collection queries. Returns the complete permitted snapshot including attempt history. Exact query counts need runtime measurement. |
| High | `CoreTraining/src/views/ReportsMockup.vue:118-145` | Loads the complete report snapshot, then filters, counts distinct learners and calculates metrics in the browser. Later table pagination only limits rendered rows. |
| High | `CoreTrainingAPI/src/main/java/com/bidv/coretraining/service/QuestionBankService.java:40-45`; `CoreTraining/src/components/QuestionBankPanel.vue:372-405` | Bank API reads all questions, then filters access and sorts in Java. UI loads all returned questions, filters and paginates locally. |
| Medium | `CoreTraining/src/views/AccessAdmin.vue:938-965` | Page-specific options are used, but users and groups are still loaded as whole lists. Units page already has a separate branch; it should not be described as loading all sections. |
| Medium | `CoreTrainingAPI/src/main/java/com/bidv/coretraining/service/AccessManagementService.java:62-65,115-119` | Unit/group reads use `findAll()` followed by access filtering in Java. Group DTO mapping can involve additional relationships. |
| Medium | `CoreTraining/src/views/ContentReports.vue:169-202`; `CoreTraining/src/views/RoleAdmin.vue:355-409` | Lists use browser filtering and pagination after fetching their data. Role-member loading also returns a list before slicing. |
| Medium | `CoreTraining/src/views/AdminPortal.vue:2483-2537` | Legacy tabs always start with a course-list request when the actor can read courses, including enrollment approvals. Other requests are conditional, so the code does not establish that all tabs load all data. Changes here must avoid the excluded documents section. |
| Investigate | `CoreTraining/src/router/index.ts`, navigation guard | Calls session and forces navigation reload on authenticated navigation. Some requests may be redundant, but removing them could delay permission revocation or password-change enforcement. Establish invalidation rules before changing this behavior. |

These are source-level findings. No live browser network trace, production query plan, payload measurement or load benchmark has been collected.

## Learner findings added after scope expansion

| Priority | Location | Current evidence |
| --- | --- | --- |
| High | `CoreTraining/src/views/Dashboard.vue:423-471`; `stores/learningStore.ts` | `/api/users/me/learning` returns one workspace; the page searches, filters, sorts and slices its complete course list locally. Rendering a page does not bound the API payload. |
| High | `CoreTrainingAPI/src/main/java/com/bidv/coretraining/service/LearnerWorkspaceService.java`, `workspace()` | Reads all courses before checking publication/access; fetches all learner progress and attempts; constructs per-course status, resume and lesson DTOs, plus attempts and legacy scores. Dashboard and profile consume the same broad response. Mapping full courses before trimming lessons can initialize unnecessary entity relationships. Exact SQL count has not been measured in this investigation. |
| High | `CoreTraining/src/views/Profile.vue:453-468,522-557` | Initial load requests profile, progress and the entire workspace even for the info tab. Progress itself requests profile, so a successful normal load has two profile callers. Results group all workspace attempts, search locally and slice locally. In-flight progress deduplication does not deduplicate the direct profile request. |
| High | `CoreTrainingAPI/src/main/java/com/bidv/coretraining/service/impl/LeaderboardServiceImpl.java:27-68` | Loads all users ordered by points, filters access and active accounts in Java, skips legacy ROLE_ADMIN, and reads badge/completed-lesson collections to count them. Returns the entire eligible leaderboard; the UI renders that list without a bounded page. This is an unbounded-list issue, not FE pagination. |
| Medium | `CoreTraining/src/views/QuizView.vue:338-346`; `App.vue:194-200` | Quiz startup fetches the complete course list if the store is empty. The shared app shell also loads that list. Establish the quiz's actual metadata/progress dependencies before replacing either caller. An empty mandatory-course array is used as a progress-load condition even though an empty result can be valid. |
| Medium | `CoreTraining/src/stores/userStore.ts:238-246`; `UserServiceImpl.java`, `buildUserProgressDto()` | Progress refresh also refreshes profile and returns broad lesson/quiz state. Building progress scans all courses for mandatory assignments. Route entry, login and notification events can trigger refreshes; simultaneous calls already share `progressRequest`, so repeated invocation alone does not prove duplicate HTTP requests. |
| Medium | `CoreTrainingAPI/src/main/java/com/bidv/coretraining/service/RagChatService.java:161-172`; `LearningChatbot.vue:306-321` | Opening chat history retrieves all messages for the selected conversation and maps them to exchanges. It is loaded on demand, not automatically at every app mount. Long conversations need a bounded older-history endpoint with preserved exchange pairing. |
| Investigate | `CoreTraining/src/views/CourseOutline.vue:195-208` | Route entry deliberately forces fresh outline and progress in parallel. Assess whether course-scoped state can preserve lock/resume freshness before removing refreshes. Full outline metadata may be required for course navigation and is not automatically waste. Coordinate shared outline API ownership. |

### Preserve already implemented behavior

Notification list requests use `beforeId` and a bounded size; the backend uses a stable `(createdAt,id)` ordering and fetches one extra row. Root lesson comments have repository pagination and replies have keyset queries. Do not report them as whole-list loading bugs or rebuild these mechanisms.

The older learner performance documents contain historical findings and later implementation updates. This audit checks current source rather than treating all old findings as still open. Lesson notes already load separately; outline caching, progress write batching and resume journals must remain intact.

### Proposed learner changes

1. Separate dashboard summary/filter choices from a database-filtered course-card page. Return course-card metadata rather than every lesson's metadata. Compute summary over the entire authorized scope, not only the displayed page. Keep learning tabs, mandatory rules, localized search/sort semantics and deadline ordering explicit in the contract.
2. Load profile details for the info tab, aggregate statistics for the statistics tab, and grouped assessment results for the results tab. Fetch an assessment's attempt history only when opened. Group before paginating so attempts for one assessment do not become separate result cards. Preserve best/latest/first-pass and legacy score behavior.
3. Query leaderboard projections, counts and authorized ranks in the database. Agree on top-N versus paged browsing and return the current learner's rank separately if outside the page. Preserve current rank semantics unless explicitly changed; tie-breaking must be deterministic. Pagination on mutable points can move entries between requests and must not be advertised as a fixed snapshot.
4. Give quiz entry the metadata and scoped progress it needs. Replace the chatbot's dependence on the global full-course list with lightweight authorized course choices; otherwise removing app-level preloading would silently empty the selector.
5. Add course-scoped progress reads where needed and refresh shared state after successful mutations. Preserve access revocation, content-version changes, quiz unlocks and resume behavior; cached state alone is not authoritative.
6. Bound chat history by complete exchanges, with a load-older cursor and ownership checks on every request. Keep server-side model context/summary behavior separate from browser history pagination.

Learner acceptance evidence must include large fixtures whose API response remains bounded; stable sorting ties; filter-reset cursors; complete-scope KPI totals; one profile request for the info tab; no full attempt history until requested; correct leaderboard ranks and access scope; direct quiz/lesson URLs; and a working chatbot course selector without global full-course preloading. Reuse existing learning, workspace and security tests for completion/unlock/resume parity.

## Mock data: distinguish executable paths from names

`isMockApi()` reads `VITE_USE_MOCK_API`. Executable mock branches remain in auth, user, admin, courses, quizzes, quiz context/summary and leaderboard APIs. `courseApi` and `quizApi` also initialize mock collections at module import time. Mock progress persistence remains in `userStore`.

`ReportsMockup.vue` calls `getTrainingReport()`, which calls `/api/admin/reports/training`. The report service reads persisted entities and has no fixture fallback. Its name alone does not establish fake reporting data. Report fixtures are separate from that live reporting path.

Do not delete test fixtures merely because their names contain “mock”. Acceptance concerns production data paths. Errors must remain visible; do not substitute fake or successful-looking empty responses.

Full removal of mock support crosses shared course/quiz code owned by the other developer. Stage that cleanup separately or agree on explicit file ownership first. Do not claim all runtime mock paths are removed while excluded shared paths remain.

## Proposed approach

1. Remove unnecessary startup requests for the in-scope admin pages, preserving learner behavior and the excluded pages. Specify page request dependencies and test actual request counts.
2. Provide narrow options endpoints/projections for in-scope pages. Keep existing training options responses unchanged. Load modal-only choices when the modal opens; use bounded search for potentially large choices.
3. Move access restrictions, search, status filtering, ordering and pagination into database queries for question bank, users, groups, approvals and moderation. Apply authorization before the page limit and count. Preserve parameterized searches and deterministic tie-breaking by ID.
4. Replace report snapshots with database-filtered summary and dimension queries, paged detail queries and an on-demand attempt-history endpoint. Export must query the complete authorized filtered result separately; exporting the displayed page would change existing behavior.
5. Add only indexes justified by query predicates and ordering. Check existing indexes before proposing DDL. Compare query plans on representative PostgreSQL data.

Use parameterized SQL/JPQL projections for ordinary reads and aggregates. A function/view may be appropriate for shared report facts if it prevents rule duplication. A stored procedure is not a prerequisite for database execution. Do not move file handling, external service calls, or transactional business validation into SQL simply to reduce Java code.

## Pagination decision

Use keyset pagination for large sequential lists. Response proposal: `items`, `nextCursor`, `hasMore`; fetch at most `size + 1`. Encode the full stable sort tuple, for example `(updated_at, id)` for descending question history. Validate cursor shape and sort/filter context; reset cursor history when filters change. Authorization remains mandatory on every request.

Keyset does not preserve arbitrary jumps to page N. Proposed UI: previous/next with cursor history for large operational lists. A bounded offset page remains an option for small configuration lists or a confirmed requirement for numbered-page jumps. Avoid exact `COUNT(*)` on every keyset request unless the screen needs it. Keyset is not a consistent snapshot across concurrent updates; define acceptable movement when mutable sort fields change.

PostgreSQL documents that skipped OFFSET rows still require server computation and that ORDER BY must determine a unique order: https://www.postgresql.org/docs/current/queries-limit.html. Matching B-tree ordering can support ORDER BY with LIMIT: https://www.postgresql.org/docs/current/indexes-ordering.html.

## Report and security invariants

- Preserve the existing rules documented in `docs/training-report-database.md`: published content, mandatory assignments, current content completion, required quiz passes, latest score per assessment, first pass, deadline override and missing historical completion dates.
- A learner/course pair must appear once even if multiple assignments or groups match. Use EXISTS or deduplicated assignment relations before aggregation.
- Actor scope includes applicable descendant units and assigned-course restrictions. Database count, summaries, details, choices and exports must use the same authorized relation.
- Preserve distinct learner counts and denominators. Do not average already rounded group percentages.
- Verify SQL rounding against Java rules at pass thresholds. Test absent assessments, incomplete history and empty courses explicitly.
- Keep mutation validation and read-time authorization. Moving filters to SQL must not widen access.

## Delivery and verification

First agree on scope and pagination UX, then perform fresh-context EGM design review and prepare the implementation plan. Implement in small stages with regression tests before each behavioral change.

Required evidence: page mount and route-change request counts; debounce and stale-response handling; bounded payloads; cursor duplicate/tie cases; authorization before limit/count; report parity against existing cases; frontend build; relevant backend service and security tests. PostgreSQL query-plan evidence is required before claiming measured database performance gains; H2 tests alone do not establish those gains.

Existing useful tests include `examListPerformance.test.mjs`, `quizDetailLoading.test.mjs`, `reportingMetrics.test.mjs`, `navigationHome.test.mjs`, `TrainingReportServiceTest`, `AccessSecurityIntegrationTest` and `LearningQueryAuditTest`. The current exam summary service already uses repository pagination; preserve that work rather than rebuilding it without evidence.

## Approval recorded

The user authorized implementation and repeatedly requested continuation. Sequential lists use cursor navigation; numbered pages remain where required by the existing interface. Shared course/quiz APIs received branch-only removal of runtime mock behavior; live endpoint contracts were preserved. Excluded training/document pages and their services were left unchanged.
