import type { Comment, ReplyPage } from "../types";

export const compareReplies = (a: Comment, b: Comment) => {
  const first = a.createdAt || a.date;
  const second = b.createdAt || b.date;
  return (first < second ? -1 : first > second ? 1 : 0) || a.id - b.id;
};

export function mergeReplies(existing: Comment[], incoming: Comment[]): Comment[] {
  const replies = new Map(existing.map((reply) => [reply.id, reply]));
  incoming.forEach((reply) => replies.set(reply.id, reply));
  return [...replies.values()].sort(compareReplies);
}

// Mock cursors carry the ordering key, so deleting their row cannot skip replies.
export function mockReplyPage(replies: Comment[], after?: string | null): ReplyPage {
  let remaining = [...replies].sort(compareReplies);
  if (after) {
    const key = JSON.parse(atob(after)) as { createdAt: string; id: number };
    remaining = remaining.filter((reply) => compareReplies(reply, { ...reply, ...key }) > 0);
  }
  const content = structuredClone(remaining.slice(0, 5));
  const last = content.at(-1);
  const hasMore = remaining.length > 5;
  return {
    content,
    hasMore,
    nextCursor: hasMore && last ? btoa(JSON.stringify({ createdAt: last.createdAt || last.date, id: last.id })) : null,
  };
}

export function mockInitialComment(comment: Comment): Comment {
  const page = mockReplyPage(comment.replies || []);
  return { ...structuredClone(comment), replies: page.content, repliesHasMore: page.hasMore, replyCursor: page.nextCursor };
}
