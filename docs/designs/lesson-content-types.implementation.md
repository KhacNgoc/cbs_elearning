# Lesson content and report replies: implementation result

Date: 2026-10-27 (Asia/Saigon)

## Follow-up: single Slide file and upload appearance

The latest user request supersedes the multiple-required-deck authoring behavior below: each lesson may now save only one SLIDE PowerPoint file. `LessonContentSelection.vue` uses an attachment-style upload card with icon, display name, filename, file size, replacement and clear controls. Choosing Slide automatically opens one card; there is no Add Slide button. Existing extra decks are shown with removal controls to avoid silent data loss. Frontend validation and `AdminServiceImpl.buildDocuments` both enforce the limit; supporting attachments remain unrestricted by this limit.

Verification for this follow-up: 14 frontend lesson-content/component tests passed; AdminLessonContentTest 12/12 and LessonProgressContentTest 10/10 passed; production frontend build passed with the existing Vite chunk/import warnings. Tests were first run failing for the new single-file restriction and upload behavior. No live database migration or application deployment was performed.

## Follow-up: required flag per content type

Date: 2026-11-05. This supersedes "Completion requires available text, at least 90% of available video, and completion of every required slide deck" below. Ticked content is always shown to learners; only content marked required counts toward completion.

- Storage: `lesson_contents.text_required`, `video_required`, `slide_required`, all `NOT NULL DEFAULT true` (Flyway `V20261105_2`). Existing lessons therefore keep requiring all their content. The flags are exposed on `LessonDto`, including learner outline rows. On update a null flag keeps the stored value; content that is removed goes back to required, so ticking it again starts from the default.
- Rules live in `LessonContentPolicy.Requirements`. A lesson with no content at all stays uncompletable and unpublishable.
- Nothing required, required quiz: passing the quiz is the only condition, and either order of events completes the lesson. Resume opens the content rather than jumping to the quiz.
- Nothing required, no required quiz: completion needs the learner to press "Đánh dấu hoàn thành". It is not awarded on opening the lesson, because that would give 100 points and unlock the next lesson for a page view. An optional quiz never completes a lesson.
- Changing the flags never resets progress and never revokes an award. Completion already recorded stays. Requirements are evaluated when read, so a learner whose remaining work became optional is completed on their next progress save (opening the lesson is enough), not in a batch at save time. Progress on optional content is still recorded, so it counts if that content later becomes required. Tightening a requirement reopens `contentCompleted` for learners without an award.

## Delivered behavior

- Create/edit forms offer a multiple-selection content dropdown: text, video, Slide PowerPoint. Only selected editors appear; deselected content is excluded when saving. Temporary values remain while toggling before save.
- Required slides have their own upload section and `SLIDE` document purpose. All attachments, including existing and new PowerPoint files, have `SUPPORTING` purpose and do not participate in completion.
- Learners get a Slide button alongside available Video/Text buttons, a required deck selector, and a separate supporting-resource list.
- Required slide content supports PPTX. The existing renderer cannot preview legacy PPT; the UI asks authors to convert PPT to PPTX. Supporting resources still accept PPT.
- Completion requires available text, at least 90% of available video, and completion of every required slide deck. A required quiz must also be passed. Either content/quiz order works; awarded achievements are preserved.
- Existing cached incomplete requirements are recalculated without attachment blockers. Reading all progress uses batch metadata queries instead of queries per lesson.
- Unchanged files keep IDs; replacing files or changing a supporting file into required slide content uses a fresh ID. Foreign and duplicate document IDs are rejected.
- Empty drafts remain allowed; attachment-only/procedure-only lessons cannot be newly published. Empty rich-text markup is normalized on save.
- New/changed nonempty administrator report replies notify the reporter. Unchanged reply saves do not duplicate notifications. Notifications open an authenticated owner-only report detail page, including reply and processing status.
- Curriculum tree metadata edits preserve full lesson content instead of replacing it with empty values.

## Files changed

### Frontend lesson authoring and learning

- `CoreTraining/src/types/index.ts`
- `CoreTraining/src/utils/lessonContent.ts`
- `CoreTraining/src/components/lesson/LessonContentSelection.vue` (new)
- `CoreTraining/src/views/LessonContentEditor.vue`
- `CoreTraining/src/views/AdminPortal.vue`
- `CoreTraining/src/views/TrainingAdmin.vue`
- `CoreTraining/src/components/CurriculumTreeStudio.vue`
- `CoreTraining/src/components/lesson/LessonLearningHeader.vue`
- `CoreTraining/src/components/lesson/LessonMediaStage.vue`
- `CoreTraining/src/components/lesson/LessonCourseSidebar.vue`
- `CoreTraining/src/views/LessonDetail.vue`
- `CoreTraining/tests/lessonContent.test.mjs`

### Report replies

- `CoreTraining/src/api/contentToolsApi.ts`
- `CoreTraining/src/router/index.ts`
- `CoreTraining/src/views/LessonReportDetail.vue` (new)
- `CoreTraining/tests/lessonReportDetail.test.mjs` (new)
- `CoreTrainingAPI/src/main/java/com/bidv/coretraining/lesson/controller/LessonReportController.java`
- `CoreTrainingAPI/src/main/java/com/bidv/coretraining/lesson/service/LessonReportService.java`
- `CoreTrainingAPI/src/test/java/com/bidv/coretraining/security/ContentToolsIntegrationTest.java`

### Backend content model and progress

- `CoreTrainingAPI/src/main/java/com/bidv/coretraining/enums/DocumentPurpose.java` (new)
- `CoreTrainingAPI/src/main/java/com/bidv/coretraining/document/entity/LessonDocument.java`
- `CoreTrainingAPI/src/main/java/com/bidv/coretraining/document/dto/LessonDocumentDto.java`
- `CoreTrainingAPI/src/main/java/com/bidv/coretraining/document/repository/LessonDocumentRepository.java`
- `CoreTrainingAPI/src/main/java/com/bidv/coretraining/lesson/repository/LessonRepository.java`
- `CoreTrainingAPI/src/main/java/com/bidv/coretraining/lesson/repository/projection/LessonOutlineView.java`
- `CoreTrainingAPI/src/main/java/com/bidv/coretraining/policy/LessonContentPolicy.java` (new)
- `CoreTrainingAPI/src/main/java/com/bidv/coretraining/lesson/service/LessonProgressService.java`
- `CoreTrainingAPI/src/main/java/com/bidv/coretraining/learner/service/LearningOutlineService.java`
- `CoreTrainingAPI/src/main/java/com/bidv/coretraining/learner/service/LearnerWorkspaceService.java`
- `CoreTrainingAPI/src/main/java/com/bidv/coretraining/training/service/TrainingStructureService.java`
- `CoreTrainingAPI/src/main/java/com/bidv/coretraining/service/impl/AdminServiceImpl.java`
- `CoreTrainingAPI/src/main/java/com/bidv/coretraining/quiz/service/impl/QuizServiceImpl.java`
- `CoreTrainingAPI/src/main/resources/migrations/20261027_lesson_slide_content.sql` (new)
- `CoreTrainingAPI/src/test/java/com/bidv/coretraining/course/service/AdminLessonContentTest.java` (new)
- `CoreTrainingAPI/src/test/java/com/bidv/coretraining/lesson/service/LessonProgressContentTest.java` (new)
- `CoreTrainingAPI/src/test/java/com/bidv/coretraining/learner/service/LearningQueryAuditTest.java`
- `CoreTrainingAPI/src/test/java/com/bidv/coretraining/learner/service/LearnerWorkspaceServiceTest.java`
- `CoreTrainingAPI/src/test/java/com/bidv/coretraining/security/AccessSecurityIntegrationTest.java` (resume fixture now marks required learning slides explicitly)

Planning records: `docs/designs/lesson-content-types.concepts.md`, `docs/superpowers/plans/2026-10-27-lesson-content-types.md`, this result document. Existing unrelated design files were retained.

## Verification executed

- Regression tests were run failing before fixes for supporting-resource completion, cached eligibility, quiz bypass, file identity/ownership/format, empty rich text, report notification/detail access, and frontend content selection.
- `npm run build`: passed; Vite reports existing large chunks and an ineffective dynamic router import warning.
- Frontend focused tests: 10 lesson content tests and 2 report detail tests passed.
- Frontend learning performance tests: 27 passed.
- Full frontend: `node --experimental-strip-types --test tests/*.test.mjs`: 105 tests, 104 passed, 1 failed.
- Backend focused suites in the final full run: AdminLessonContentTest 10/10; LessonProgressContentTest 10/10; LearningQueryAuditTest 14/14; ContentToolsIntegrationTest 12/12; LearnerWorkspaceServiceTest 4/4. Total 50/50.
- Full backend: Maven offline, Java 17, `-Daether.syncContext.named.factory=noop -DargLine=-Djava.net.preferIPv4Stack=true test`: 148 tests, 1 failure, 8 errors; 139 passed. JVM test processes needed execution outside the filesystem sandbox.
- `git diff --check`: passed.
- Independent design/code review completed; metadata query growth and empty-rich-text findings were fixed, then independently rechecked.

## Remaining full-suite failures and limits

1. Frontend `roleTypes.test.mjs:87`: the test cannot find a learner role control and reads `getAttribute` on null. Role-management implementation and this test were not changed by this feature. A clean baseline run was not performed.
2. Backend `AccessSecurityIntegrationTest.disabledUnitRevokesAccessAndForeignEnrollmentCannotOverrideScope`: foreign-course registration produces 200 where the test expects 403. This is a scope-policy discrepancy: unchanged `AccessPolicy.visible` accepts registration as visibility and unchanged `LearningAccessService` accepts it as enrollment. The assertion fails before the disabled-unit part. A clean baseline run was not performed, so this is not presented as a proven baseline test result. This feature does not change the scope policy.
3. Eight knowledge-source errors originate from the test class's static HTTP stub: Windows JVM cannot establish a loopback connection (`Invalid argument: connect`). Initialization fails before service tests execute; the other errors cascade. Retrying with IPv4 preference did not resolve the local infrastructure error.

No browser session, production PostgreSQL migration, or deployment was executed. Tests use local fixtures/H2 and component DOM tests. Existing legacy blank HTML is not bulk rewritten; new admin saves normalize it.

## Database rollout

For deployments using Hibernate `ddl-auto=update`, the purpose column has a SUPPORTING default via the entity definition. For schema-managed deployments, apply `CoreTrainingAPI/src/main/resources/migrations/20261027_lesson_slide_content.sql` before deploying the application. The SQL adds the purpose column and defaults all existing attachments to SUPPORTING. It does not delete progress, bookmarks, quizzes, or achievements. The manual script has not been executed against production PostgreSQL.
