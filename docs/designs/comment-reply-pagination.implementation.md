# Comment reply pagination — implementation result

Date: 2026-10-27 (Asia/Saigon).

## Behavior

- Root comments keep the existing “Xem thêm thảo luận” button and twenty comments per page, newest first.
- Each root initially includes at most five replies, oldest first, ordered by creation timestamp and ID.
- Each thread independently shows “Xem thêm phản hồi” when more exist. Clicking loads five more; its button shows a spinner/loading text and prevents duplicate requests. Failed requests show an inline error and can be retried without losing existing replies.
- The backend limits returned replies. Initial previews use one window query for the displayed root comments, fetching at most six records per root to detect whether more exist, then returning five. No full reply collection is fetched merely to hide it in the browser.
- Additional pages use an opaque timestamp/ID cursor. Deleting the cursor's reply does not skip the next page. New replies remain visible without advancing the server cursor; pages merge without duplicate IDs.
- Switching lessons discards stale page responses. Successful reply deletion invalidates an older pending page so it cannot restore the deleted reply.
- Mock API responses clone the full mock database before truncating initial results; loading the course or saving local UI state cannot accidentally truncate stored mock replies.
- Lesson access is checked before reading replies. Foreign-lesson roots and reply IDs passed as roots are rejected; malformed cursors return 400.

## API

Root comment DTO adds `repliesHasMore`, `replyCursor`; comments also expose `createdAt` for stable client ordering while retaining the existing display `date`.

`GET /api/lessons/{lessonId}/comments/{rootCommentId}/replies?after={cursor}` returns `{content, nextCursor, hasMore}`. Omit `after` for the first five replies. Page size is fixed at five.

## Changed files

Frontend:

- `CoreTraining/src/types/index.ts`
- `CoreTraining/src/api/courseApi.ts`
- `CoreTraining/src/stores/courseStore.ts`
- `CoreTraining/src/utils/commentReplies.ts` (new)
- `CoreTraining/src/views/LessonDetail.vue`
- `CoreTraining/src/components/lesson/LessonDiscussion.vue`
- `CoreTraining/tests/commentReplies.test.mjs` (new)
- `CoreTraining/tests/commentRepliesUi.test.mjs` (new)

Backend:

- `CoreTrainingAPI/src/main/java/com/bidv/coretraining/lesson/entity/Comment.java`
- `CoreTrainingAPI/src/main/java/com/bidv/coretraining/lesson/dto/CommentDto.java`
- `CoreTrainingAPI/src/main/java/com/bidv/coretraining/lesson/dto/CommentReplyPageDto.java` (new)
- `CoreTrainingAPI/src/main/java/com/bidv/coretraining/mapper/CourseMapper.java`
- `CoreTrainingAPI/src/main/java/com/bidv/coretraining/lesson/repository/CommentRepository.java`
- `CoreTrainingAPI/src/main/java/com/bidv/coretraining/course/service/CourseService.java`
- `CoreTrainingAPI/src/main/java/com/bidv/coretraining/course/service/impl/CourseServiceImpl.java`
- `CoreTrainingAPI/src/main/java/com/bidv/coretraining/course/controller/CourseController.java`
- `CoreTrainingAPI/src/main/resources/migrations/20261027_comment_reply_pagination.sql` (new, optional index)
- `CoreTrainingAPI/src/test/java/com/bidv/coretraining/learner/service/LearningQueryAuditTest.java`
- `CoreTrainingAPI/src/test/java/com/bidv/coretraining/security/AccessSecurityIntegrationTest.java`

Plan: `docs/superpowers/plans/2026-10-27-comment-reply-pagination.md`.

## Verification

- Initial API regression observed RED: twelve replies returned where five expected; GREEN after the bounded query.
- Frontend tests observed RED before implementation, then passed. A further deferred-response regression reproduced a deleted reply being restored by a pending page; invalidating that pending request fixed it.
- Focused frontend run: `node --experimental-strip-types --test tests/commentReplies.test.mjs tests/commentRepliesUi.test.mjs tests/learningPagesPerformance.test.mjs` — 21 passed (six new reply tests plus fifteen learning-page tests).
- Full frontend run: `node --experimental-strip-types --test tests/*.test.mjs` — 117 tests, 116 passed, one failure in unchanged `roleTypes.test.mjs:87` (null `getAttribute` for a learner role control). No role-management files were changed by this feature.
- Production build: `npm run build` passed. Existing Vite chunk size and router dynamic import warnings remain.
- Backend focused regression: 52 passed across LearningQueryAuditTest (18), AccessSecurityIntegrationTest#commentReplyEndpointLimitsPagesAndEnforcesLessonAccess (1), CourseMapperTest (3), JwtTokenProviderTest (4), ContentToolsIntegrationTest (12), LearnerWorkspaceServiceTest (4), LessonProgressContentTest (10).
- Backend tests used Java 17 and offline Maven with `-Daether.syncContext.named.factory=noop`. Normal target classes were being overwritten with invalid class descriptors; a temporary copy of the existing POM changed only build output to `target/reply-pagination-check`, then ran the selected suites. The temporary POM was removed after verification. Original POM is unchanged; Surefire reports remain under that ignored output directory.
- Backend tests cover tied timestamps, pages of five/five/remainder, cursor deletion, new replies between requests, empty/exactly-five threads, bounded preview entity loading, malformed cursors, and HTTP lesson/anonymous access checks.
- Independent review completed. The one deletion/page race finding was fixed and independently rechecked; no material findings remained.
- `git diff --check` passed.

## Rollout and limits

No live PostgreSQL execution, browser validation, deployment, or commit was performed. H2 integration tests exercised the window query and keyset logic. No data columns change. For schema-managed deployments, the optional SQL adds the `(parent_comment_id, created_at, id)` index; Hibernate schema-update deployments can create the same index from the entity declaration.
