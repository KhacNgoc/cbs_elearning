# Lesson content and report reply implementation plan

Goal: Separate required text/video/slides from supporting attachments and notify learners about report replies.

Spec: `docs/designs/lesson-content-types.concepts.md`, with explicit user authorization to implement on 2026-10-27.

Architecture: Keep existing document storage and renderers. Add `purpose: SUPPORTING | SLIDE` to document entities, DTOs and frontend types, defaulting to SUPPORTING for all old files. Content types are derived from persisted text, video and SLIDE documents; editor selections control what is submitted, avoiding a redundant stored selector that could diverge from actual content.

Execution: Use Superpowers TDD and subagent-driven-development for independent frontend/report work, with root implementing backend lesson content. Work in this explicitly supplied workspace; preserve existing untracked designs. Do not commit or deploy.

## Constraints and decisions

- Old attachments and files uploaded in attachments remain SUPPORTING regardless of format.
- Only files uploaded in Slide section have SLIDE purpose; backend validates PowerPoint format.
- Existing text/video thresholds remain unchanged (text end, 90% video); all required slide decks must reach their end.
- Preserve awarded achievements; no broad content versioning redesign.
- Frontend primary tabs include Slide only for SLIDE files; supporting files remain separately accessible.
- Empty-content drafts are allowed; publishing a lesson requires real learning content, not only attachments/procedure metadata.
- Report replies must be readable by the report owner through an authorized endpoint, not an admin URL.

## Review focus

- Forged SLIDE purpose on PDF/Word and foreign document IDs are rejected.
- Editing unchanged files preserves IDs; replacing a file does not reuse completion evidence.
- Quiz pass and explicit completion cannot bypass content requirements.
- Old cached incomplete progress is recalculated without waiting for another attachment completion event.
- Report notifications deduplicate unchanged reply saves and cannot expose another user's report.

## Task 1: Backend lesson content (root)

Files: LessonDocument/entity DTO/repository/projections, AdminServiceImpl, LearningOutlineService, LessonProgressService, QuizServiceImpl, TrainingStructureService, SQL migration, focused JUnit tests.

- [x] Add regression tests for supporting files versus required slide completion and invalid slide formats; run to show failure.
- [x] Add document purpose and backwards-safe migration; expose it through mapping/outline.
- [x] Preserve unchanged document IDs and validate ownership; require fresh identity for replaced files.
- [x] Recalculate content from text/video/SLIDE files across save/read/quiz/explicit completion paths, preserving achievements.
- [x] Require learning content when publishing; run backend focused tests and full suite.

## Task 2: Forms and learner UI (frontend implementer)

Files: frontend types, lessonContent utility/tests, LessonContentEditor, legacy AdminPortal lesson form, TrainingAdmin creation path as needed, learner header/detail/sidebar/media components.

Interface: `LessonDocument.purpose?: 'SUPPORTING' | 'SLIDE'`; omitted means SUPPORTING. Continue passing documents in the lesson documents array, with explicit purpose for Slide uploads. Existing progress API uses DOCUMENT with document ID.

- [x] Write/run behavioral tests for required slides versus supporting PowerPoint and initial content selection.
- [x] Add multi-select content dropdown and only show selected input panels in all lesson create/edit forms. Submit selected content only, preserve local toggled values until save.
- [x] Add separate slide upload editor and supporting attachment list, with correct purposes.
- [x] Add learner Slide tab and deck selection; do not auto-select supporting files as lesson content.
- [x] Run focused frontend tests and production build.

## Task 3: Report reply notification (report implementer)

Files: LessonReportService/Controller, report tests, frontend contentTools API, learner report display and notification destination routing.

- [x] Write/run tests for reply notification, unchanged reply deduplication and owner-only read access.
- [x] Notify reporter transactionally on nonempty new/changed reply; use existing LESSON_REPORT notification type.
- [x] Add owner-authorized detail endpoint and learner-readable reply destination.
- [x] Run focused tests/build as appropriate; do not modify lesson learning/form components owned by Task 2.

## Task 4: Integration and independent review

- [x] Inspect actual design reviewer findings and incorporate necessary fixes.
- [x] Run relevant frontend tests/build, backend tests, and whitespace diff validation.
- [x] Independent review final diff for spec coverage and completion/access bypasses; address actionable findings.
- [x] Record results, changed file groups, and migration/deployment limitations. No claims of browser/DB verification unless executed.

Completion record: implementation and independent review complete. Verification executed with remaining full-suite failures documented in docs/designs/lesson-content-types.implementation.md; no deployment or commit performed.
