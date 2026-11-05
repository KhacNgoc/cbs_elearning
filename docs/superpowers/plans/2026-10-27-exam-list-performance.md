# Exam list performance implementation plan

**Goal:** Remove per-lesson question requests from the exam list and defer unrelated page modules.

**Architecture:** A scoped summary endpoint groups question counts in SQL and pages summary rows. A dedicated exam list view uses that endpoint, with debounced search and stale-response protection. Existing detail/edit APIs remain unchanged. Session and permission refresh remain unchanged for access revocation safety.

- [x] Add regression checks for SQL aggregation/paging and exam list loading.
- [x] Add summary DTO, repository projection query, scoped service and GET /api/admin/quizzes.
- [x] Add dedicated exam list view and API client; retain course filter, search, paging and create/detail navigation.
- [x] Lazy-load route pages and legacy admin sections; hoist legacy question loader to fix immediate watcher initialization.
- [x] Run focused backend/frontend tests and production build; document results.

## Verification

Changed files: frontend `src/api/quizSummaryApi.ts`, `src/views/AdminExamList.vue`, `src/views/AdminOverview.vue`, `src/views/AdminPortal.vue`, `src/router/index.ts`, `tests/examListPerformance.test.mjs`; backend `quiz/dto/QuizSummaryDto.java`, `quiz/service/QuizSummaryService.java`, `quiz/repository/QuestionRepository.java`, `controller/AdminController.java`, `learner/service/LearningQueryAuditTest.java` (test source).

- Frontend: `node --experimental-strip-types --test tests/examListPerformance.test.mjs tests/learningPagesPerformance.test.mjs tests/lessonContent.test.mjs tests/notificationCenter.test.mjs`: 36 passed.
- Backend: `LearningQueryAuditTest`: 20 passed, including two new summary checks. Used temporary POM pointing build output to target/exam-list-check to avoid concurrent IDE class writes; removed POM after verification.
- New checks cover one initial summary request, debounce, server page parameters, stale response rejection, retry, scoped results, literal wildcard search, page count, no question/content hydration and empty scope. Aggregation itself uses at most two SQL statements; scope resolution uses the existing authorization path and is not included in that query bound.
- `npm run build`: passed. Entry JS approximately 319 KB versus 843 KB before route lazy loading, uncompressed. Existing router import/chunk warnings remain.
- No browser wall-clock measurement; no claim of a specific millisecond improvement. Restart backend to expose the new endpoint.
- Authentication session and menu permission refresh remain intact. No migration required.
- Independent review found no other material issue. Residual limitation: existing authorization still loads courses and eager access metadata to determine scope; it has not been redesigned in this patch.
