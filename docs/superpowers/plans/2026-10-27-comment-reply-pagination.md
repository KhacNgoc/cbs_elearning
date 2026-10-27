# Comment reply pagination

User authorized implementation: initially display five replies and load more on demand. Use a button, matching existing root comment pagination (twenty roots per load).

## Contract

- Each root comment has at most five initial replies, `repliesHasMore: boolean`, and `replyCursor: string | null`.
- Each comment also exposes `createdAt` as an ISO local timestamp for stable ordering when merging newly posted replies with older pages; existing `date` stays unchanged for display.
- GET `/api/lessons/{lessonId}/comments/{rootCommentId}/replies?after={opaqueCursor}` returns `{content, nextCursor, hasMore}`. No `after` means the first page. The server fixes each page at five and fetches six to detect more.
- Cursor contains the last displayed reply's createdAt and ID. Query sorts by createdAt, ID ascending. The cursor does not depend on the continued existence of a deleted reply.
- Lesson access is checked before reads; root must belong to that lesson and must have no parent. Invalid cursors return 400.
- Initial replies use one bounded batch query for all roots, not a query per root; do not hydrate all replies just to truncate them.
- Keep existing root pagination and reply/delete permissions unchanged.

## Implementation

- [x] Root: backend regression tests first; batch first six replies per root with SQL row_number, retain five; keyset reply endpoint and cursor validation; DTO metadata.
- [x] Frontend task: API/types/mock behavior, load-more reply button with independent per-thread loading/error state, merge pages without duplicates in createdAt/ID order; preserve current lesson guards and posted replies.
- [x] Tests: first page 5, subsequent 5, final remainder; tied timestamps, cursor deletion, foreign lesson/root, malformed cursor, bounded entity/query counts; frontend retry/merge/state and build.
- [x] Independent final review; record verification and unresolved suite limits. Do not commit or deploy.

Result: docs/designs/comment-reply-pagination.implementation.md. Independent review complete; all targeted tests passed, full frontend retains one roleTypes failure.
