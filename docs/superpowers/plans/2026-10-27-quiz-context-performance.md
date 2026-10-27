# Lightweight quiz context implementation plan

**Goal:** Remove full course/content responses from quiz editing and the question bank without weakening access checks.

**Architecture:** GET /api/admin/quizzes/courses returns scoped course options and exam actions. GET /api/admin/quizzes/context accepts a lessonId or courseId, resolves authorization, and returns scalar quiz metadata for that scope only. Lesson rows are projected in SQL, without content/question/comment/document entities. Frontend stores context locally; it fetches lesson options on course selection. Existing full course endpoint remains compatible.

- [x] Add regression tests for lightweight loading, scope checks and zero content hydration.
- [x] Implement scalar repository projection and authorized context service/endpoints.
- [x] Switch quiz editor and question bank to local context; preserve embedded editor, settings, read-only behavior, retry, create/detail navigation and post-save refresh.
- [x] Verify frontend tests, backend audit tests and production build. Review changes and record limitations.

## Changed files

- Backend: `dto/QuizLessonContextDto.java`, `repository/LessonRepository.java`, `service/QuizContextService.java`, `controller/AdminController.java`, and test `service/LearningQueryAuditTest.java`.
- Frontend: `src/api/quizContextApi.ts`, `src/views/AdminQuizDetail.vue`, `src/components/QuestionBankPanel.vue`, `tests/quizDetailLoading.test.mjs`, `tests/questionBankContext.test.mjs`.

## Verification and limits

- Frontend: 41 passed across quiz detail, question bank context, exam list, learning page performance, lesson content tests. Covers initial loading/error/retry, scoped requests, no global store writes, deferred lesson options, stale responses, reused create/detail routes, and pending creation settings surviving question-load retry. RED observed for removed full payload and pending settings retry before fixes.
- Backend: all 22 LearningQueryAuditTest tests passed. Includes denied scope, mismatched course/lesson, missing scope, course options without lessons, selected lesson metadata, retained passing score/required flag, and zero Lesson/LessonContent/Quiz/Question/Comment/LessonDocument entity hydration for context.
- UTF-8 response fixture: course with 24 lessons and long private lesson text returns one lesson and 359 bytes. This is a test fixture measurement, not the user's live 622 KB response measured again.
- Production frontend build passes; pre-existing chunk/router warnings remain. No browser timing measurement.
- Independent review found no scope leak; create/detail settings and retry findings fixed and covered by regression tests.
- Existing authorization policy and course-scope metadata resolution are reused. Course options return all authorized course metadata; lesson/content collections are excluded. No permission/session cache was added. Full course endpoint remains unchanged for other consumers.
- Backend tests use temporary isolated build output `target/quiz-context-check` to avoid concurrent IDE class writes; temporary POM removed after validation. No schema migration required. Restart backend for the new endpoints.
