# Lesson content types and report reply notifications

Date: 2026-10-27
Status: Implemented after user approval and independent review. See lesson-content-types.implementation.md for verification and remaining suite failures.

## Existing behavior verified in code

- `LessonContentEditor.vue` displays video, text, and attachments together. Attachments accept PDF, Word, and PowerPoint.
- `LessonDocument` stores file format but has no field distinguishing required learning content from supporting resources.
- `LessonProgressService.saveProgress` requires all lesson documents to be completed alongside available text and video.
- `LessonReportService.review` saves status and resolution but does not call the notification service.
- The report controller currently has no learner endpoint for reading a report and its resolution. Learner notification navigation therefore also needs an authorized report detail endpoint and view.
- `docs/designs/lesson-progress-content-versioning.md` is an existing, unapproved design. This change must not silently implement its broader versioning work. Its document-change rules will need alignment with the new distinction between learning content and supporting resources.

## Requested behavior

- Create and edit forms have a multiple-selection dropdown named “Nội dung bài học”, with text, video, and PowerPoint options.
- Only selected content editors are shown. Supporting attachments stay available separately.
- The learner header shows a Slide button alongside Video and Text when slide content exists. Supporting PowerPoint attachments do not cause the Slide button to appear.
- Backend validation and saved data reflect the selection; hiding a field alone is insufficient.
- Supporting attachments never block completion or contribute to lesson progress.
- Completion depends on every selected learning component. Existing required quiz conditions remain applicable.
- An administrator's report reply creates an in-app notification for the reporter. The learner can open and read the reply, with access restricted to their own reports.

## Proposed implementation boundaries

1. Explicitly store selected content types and the purpose of each document. Keep format and purpose separate: a PowerPoint file can be required slide content or a supporting attachment.
2. Reuse the existing PowerPoint upload/viewing infrastructure after verifying its completion events. The required slide editor accepts PPTX, the format the installed viewer can render and report completion for; the supporting attachment editor retains existing formats including PPT/PPTX.
3. Require valid content for each selected type and at least one selected type when publishing. Preserve draft behavior where applicable after inspecting current publication validation.
4. Exclude unselected content from persisted active lesson content. Retain temporary editor values when toggling selections before save, so an accidental toggle does not immediately erase the form input.
5. Reuse current text completion and video threshold rules. Require completion of every content slide deck; supporting file activity has no effect. Apply the same rules to automatic completion, explicit completion, quiz completion, summaries, and frontend indicators.
6. Preserve existing awarded lesson/course achievements. Recalculate unfinished progress using eligible content only, without treating previously unopened slides as completed. Verify that read paths do not leave attachment-only blockers cached indefinitely.
7. Notify the reporter when a nonempty reply is added or changed. Saving an identical reply must not create another notification. Save the reply and notification transactionally, reusing existing notification delivery. Link to a learner-readable report detail view rather than an administrator page.

## Confirmed migration rule

All existing attachments remain supporting resources, including PPT/PPTX. New files uploaded through the attachment section also remain supporting resources regardless of format. Only PowerPoint files uploaded through the new Slide content section are required slide content. Existing text and video remain learning content.

File format must never determine whether a resource is required. The upload section determines its purpose, and backend validation enforces that slide content contains a supported PowerPoint file.

## Concrete changes to review

- Add a document purpose field, defaulting to supporting, to `LessonDocument` and its DTO/projections. Populate the default for old rows in a database migration. Keep supporting and slide files distinguishable in every API response.
- Use the current separate document storage/viewer infrastructure for slides; do not build a second PowerPoint renderer. Keep document position storage keyed by document ID and count completion only for documents whose purpose is slide content.
- Add the content type selector and a separate Slide upload section in `LessonContentEditor.vue`. Check all create-lesson entry points so new and existing lessons use the same behavior.
- Update `LessonLearningHeader.vue`, `LessonDetail.vue`, `LessonMediaStage.vue`, initial content selection, and sidebar document rendering. The Slide button opens required slide content; supporting files remain accessible from their own list. For multiple slide files, provide selection within the Slide section.
- Keep the current video threshold of 90% and text-end rule. Slide completion requires reaching the final slide using the existing viewer's completion event. Verify PPT/PPTX support and viewer errors before relying on this event.
- Update `LessonProgressService` and every completion path, including quiz completion. Attachment-only lessons have no learning content and must not be automatically completed by opening an attachment. Existing published attachment-only lessons need an explicit empty-content state and administrator guidance; no file is silently promoted to required content.
- Add report reply notification creation to `LessonReportService.review`. Add an owner-authorized detail endpoint and a learner reply display so the notification leads to readable content. Preserve administrator course permissions and existing optimistic locking.
- Check existing lesson document IDs during update: the current editor service clears and rebuilds document rows. Preserve stable IDs for unchanged slide files so saving unrelated fields does not invalidate slide bookmarks or completion. Validate that reused IDs belong to the lesson.

## Verification plan

- Form selection controls editor visibility and persisted payload; reopening edit restores selections.
- Learner Slide button exists only for required slide content, and switches correctly alongside Video and Text.
- Text-only, video-only, slide-only, and mixed lessons use only their selected content.
- Supporting PDF, Word, and PowerPoint files never block or advance completion.
- Multiple slide decks require all required decks, if multiple decks are supported.
- Deselecting content cannot leave hidden required content on the server.
- Existing achievements remain awarded; unfinished records are evaluated against eligible content.
- Required quiz cannot bypass incomplete learning content.
- New/changed reply notifies the correct learner once; unchanged reply and failed updates do not notify.
- Learners cannot read other learners' report details; notifications open a learner-accessible reply.

## Final implementation decisions

- Derive selected types from saved text/video/SLIDE files rather than adding a redundant persisted content type list. The form selections filter saved values.
- User follow-up: allow only one required PPTX file per lesson. The form opens a single styled upload card and replaces its file in place. Both frontend and backend reject multiple SLIDE documents on save. Existing multiple-slide data remains visible for explicit removal of extras; it is not silently deleted. Legacy PPT stays available as a supporting resource; authors convert it to PPTX before uploading as required Slide content.
- Every existing attachment defaults to SUPPORTING. No existing file is automatically promoted to Slide content.
- Preserve IDs for unchanged files. Replacement files and supporting-to-slide purpose changes receive fresh IDs, preventing inherited completion evidence.
- Recalculate current eligibility on progress saves and reads. Batch all-progress requirement reads into two metadata queries; a cold-context regression test verifies three total SQL queries for four and twelve progress rows.
- Empty drafts remain allowed. Explicit publication, allocation-triggered publication, and edits to published lessons require text/video/Slide content. Empty rich-text markup is normalized on new saves.
- Required quiz and learning content must both be complete, regardless of their completion order. Existing awarded achievements are retained.
- Independent review findings about quiz order, metadata queries, and empty rich text were addressed. Final focused independent recheck found no remaining material issues.
