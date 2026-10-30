import type { Comment } from "../types";

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
