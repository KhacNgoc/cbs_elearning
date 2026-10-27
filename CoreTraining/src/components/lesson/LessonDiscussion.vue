<template>
  <div class="discussion-section">
    <div class="section-title">
      <div>
        <span class="eyebrow">Trao đổi</span>
        <h2>Thảo luận & góp ý</h2>
      </div>
      <MessageSquare :size="24" />
    </div>
    <div class="feedback-form">
      <textarea
        :value="comment"
        rows="4"
        placeholder="Nhập câu hỏi hoặc góp ý của bạn..."
        @input="
          $emit('update:comment', ($event.target as HTMLTextAreaElement).value)
        "
      ></textarea>
      <button
        class="submit-button"
        :disabled="!comment.trim()"
        @click="$emit('submit')"
      >
        Gửi góp ý <Send :size="16" />
      </button>
    </div>

    <div class="comments-list">
      <div v-if="comments.length === 0" class="empty-comments">
        Chưa có góp ý. Hãy bắt đầu cuộc thảo luận.
      </div>
      <article
        v-for="item in comments"
        v-else
        :key="item.id"
        class="comment-thread"
      >
        <div class="comment">
          <span class="avatar">{{ item.avatar }}</span>
          <div class="comment-body">
            <div class="comment-bubble">
              <div class="comment-meta">
                <strong>{{ item.user }}</strong
                ><span>{{ item.date }}</span>
              </div>
              <div v-if="item.rating" class="historical-lesson-rating"><small>Đánh giá bài học trước đây</small><StarRating :model-value="item.rating" readonly/></div>
              <p>{{ item.content }}</p>
            </div>
            <div class="comment-actions">
              <button
                class="reply-action"
                @click="openReply(item.id, item.id, item.user)"
              >
                <Reply :size="14" /> Trả lời
              </button>
              <button
                v-if="canDelete(item)"
                class="delete-action"
                @click="requestDelete(item.id)"
              >
                <Trash2 :size="14" /> Gỡ
              </button>
            </div>
          </div>
        </div>

        <div v-if="item.replies?.length" class="reply-list">
          <div
            v-for="reply in item.replies"
            :key="reply.id"
            class="comment reply-comment"
          >
            <span class="avatar reply-avatar">{{ reply.avatar }}</span>
            <div class="comment-body">
              <div class="comment-bubble">
                <div class="comment-meta">
                  <strong>{{ reply.user }}</strong
                  ><span>{{ reply.date }}</span>
                </div>
                <p>
                  <strong v-if="reply.replyToUser" class="mention"
                    >@{{ reply.replyToUser }}</strong
                  >
                  {{ reply.content }}
                </p>
              </div>
              <div class="comment-actions">
                <button
                  class="reply-action"
                  @click="openReply(item.id, reply.id, reply.user)"
                >
                  <Reply :size="14" /> Trả lời
                </button>
                <button
                  v-if="canDelete(reply)"
                  class="delete-action"
                  @click="requestDelete(reply.id)"
                >
                  <Trash2 :size="14" /> Gỡ
                </button>
              </div>
            </div>
          </div>
        </div>

        <div v-if="item.repliesHasMore" class="reply-pagination">
          <button
            class="reply-load-more btn btn-outline"
            :disabled="replyPageStates?.[item.id]?.loading"
            :aria-busy="Boolean(replyPageStates?.[item.id]?.loading)"
            @click="emit('load-more-replies', item.id)"
          >
            <span v-if="replyPageStates?.[item.id]?.loading" class="reply-loading-spinner" aria-hidden="true"></span>
            {{ replyPageStates?.[item.id]?.loading ? "Đang tải phản hồi…" : "Xem thêm phản hồi" }}
          </button>
          <p v-if="replyPageStates?.[item.id]?.error" role="alert" class="reply-load-error">
            {{ replyPageStates[item.id]?.error }}
          </p>
        </div>

        <div v-if="replyingTo?.rootId === item.id" class="reply-composer">
          <span class="avatar composer-avatar">{{ currentUserInitial }}</span>
          <div>
            <div class="replying-label">
              Đang trả lời <strong>@{{ replyingTo.user }}</strong>
              <button aria-label="Hủy trả lời" @click="cancelReply">
                <X :size="14" />
              </button>
            </div>
            <textarea
              ref="replyInput"
              v-model="replyContent"
              rows="2"
              :placeholder="`Viết phản hồi cho ${replyingTo.user}...`"
              @keydown.ctrl.enter.prevent="sendReply"
            ></textarea>
            <div class="reply-composer-actions">
              <small>Ctrl + Enter để gửi</small>
              <button :disabled="!replyContent.trim()" @click="sendReply">
                Gửi phản hồi <Send :size="14" />
              </button>
            </div>
          </div>
        </div>
      </article>
    </div>
    <button v-if="hasMore" class="btn btn-outline" :disabled="loadingMore" @click="emit('load-more')">
      {{ loadingMore ? "Đang tải…" : "Xem thêm thảo luận" }}
    </button>
    <ConfirmModal
      :open="commentToDelete !== null"
      title="Gỡ bình luận?"
      message="Bình luận này và các phản hồi liên quan sẽ bị gỡ. Thao tác này không thể hoàn tác."
      confirm-label="Gỡ bình luận"
      @cancel="cancelDelete"
      @confirm="confirmDelete"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, ref } from "vue";
import { MessageSquare, Reply, Send, Trash2, X } from "lucide-vue-next";
import type { Comment, ReplyPageState } from "../../types";
import ConfirmModal from "../ConfirmModal.vue";
import StarRating from "../StarRating.vue";

const props = defineProps<{
  comments: Comment[];
  hasMore?: boolean;
  loadingMore?: boolean;
  replyPageStates?: Record<number, ReplyPageState>;
  comment: string;
  currentUsername: string;
  isAdmin: boolean;
}>();
const emit = defineEmits<{
  "update:comment": [value: string];
  submit: [];
  "load-more": [];
  "load-more-replies": [rootCommentId: number];
  reply: [rootCommentId: number, replyToCommentId: number, content: string];
  delete: [commentId: number];
}>();

const replyingTo = ref<{
  rootId: number;
  targetId: number;
  user: string;
} | null>(null);
const replyContent = ref("");
const replyInput = ref<HTMLTextAreaElement | null>(null);
const commentToDelete = ref<number | null>(null);
const currentUserInitial = computed(() =>
  (localStorage.getItem("username") || "U").charAt(0).toUpperCase(),
);
const canDelete = (comment: Comment) =>
  props.isAdmin || comment.user === props.currentUsername;
const requestDelete = (commentId: number) => {
  commentToDelete.value = commentId;
};
const cancelDelete = () => {
  commentToDelete.value = null;
};
const confirmDelete = () => {
  if (commentToDelete.value === null) return;
  emit("delete", commentToDelete.value);
  commentToDelete.value = null;
};

const openReply = async (rootId: number, targetId: number, user: string) => {
  replyingTo.value = { rootId, targetId, user };
  replyContent.value = "";
  await nextTick();
  replyInput.value?.focus();
};
const cancelReply = () => {
  replyingTo.value = null;
  replyContent.value = "";
};
const sendReply = () => {
  if (!replyingTo.value || !replyContent.value.trim()) return;
  emit(
    "reply",
    replyingTo.value.rootId,
    replyingTo.value.targetId,
    replyContent.value.trim(),
  );
  cancelReply();
};
</script>

<style scoped>
.comment-thread {
  padding: 1rem 0;
  border-bottom: 1px solid var(--border-color);
}
.comment-thread > .comment,
.reply-comment {
  padding: 0;
  border: 0;
}
.comment-body {
  min-width: 0;
}
.comment-bubble {
  padding: 0.7rem 0.85rem;
  border-radius: 8px;
  background: var(--surface-f2f6f4);
}
.comment-bubble p {
  margin-top: 0.35rem;
  color: var(--ink-43534d);
  line-height: 1.5;
}
.comment-actions {
  margin: 0.3rem 0 0 0.55rem;
  display: flex;
  align-items: center;
  gap: 0.8rem;
}
.reply-action,
.delete-action {
  display: inline-flex;
  align-items: center;
  gap: 0.25rem;
  border: 0;
  background: transparent;
  cursor: pointer;
  font-size: 0.75rem;
  font-weight: 700;
}
.reply-action {
  color: var(--primary-ink);
}
.delete-action {
  color: var(--ink-b42318);
}
.delete-action:hover {
  color: var(--ink-8f1710);
}
.reply-list {
  margin: 0.7rem 0 0 3rem;
  padding-left: 0.9rem;
  display: grid;
  gap: 0.65rem;
  border-left: 2px solid var(--line-d8e5e0);
}
.reply-pagination {
  margin: 0.75rem 0 0 3rem;
}
.reply-load-more { display: inline-flex; align-items: center; gap: .5rem; }
.reply-loading-spinner { width: 14px; height: 14px; flex-shrink: 0; border: 2px solid currentColor; border-right-color: transparent; border-radius: 50%; animation: reply-loading-spin .7s linear infinite; }
@keyframes reply-loading-spin { to { transform: rotate(360deg); } }
@media (prefers-reduced-motion: reduce) { .reply-loading-spinner { animation: none; } }
.reply-load-error {
  margin-top: 0.35rem;
  color: var(--ink-b42318);
  font-size: 0.8rem;
}
.reply-comment {
  grid-template-columns: 32px 1fr;
}
.reply-avatar,
.composer-avatar {
  width: 32px;
  height: 32px;
  font-size: 0.75rem;
}
.mention {
  color: var(--primary-ink);
}
.reply-composer {
  margin: 0.75rem 0 0 3rem;
  display: grid;
  grid-template-columns: 32px 1fr;
  gap: 0.6rem;
}
.reply-composer textarea {
  width: 100%;
  padding: 0.65rem 0.75rem;
  resize: vertical;
  border: 1px solid var(--line-cfd9d5);
  border-radius: 8px;
  outline: none;
}
.reply-composer textarea:focus {
  border-color: var(--primary-color);
  box-shadow: 0 0 0 3px rgba(0, 91, 65, 0.1);
}
.replying-label {
  margin-bottom: 0.35rem;
  display: flex;
  align-items: center;
  gap: 0.3rem;
  color: var(--text-muted);
  font-size: 0.72rem;
}
.replying-label button {
  width: 22px;
  height: 22px;
  display: grid;
  place-items: center;
  border: 0;
  border-radius: 50%;
  background: var(--surface-e5ece9);
  color: var(--ink-64748b);
  cursor: pointer;
}
.reply-composer-actions {
  margin-top: 0.35rem;
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.reply-composer-actions small {
  color: var(--text-muted);
}
.reply-composer-actions button {
  padding: 0.45rem 0.7rem;
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
  border: 0;
  border-radius: 6px;
  background: var(--primary-color);
  color: white;
  cursor: pointer;
  font-weight: 700;
}
.reply-composer-actions button:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}
@media (max-width: 600px) {
  .reply-list,
  .reply-pagination,
  .reply-composer {
    margin-left: 1rem;
  }
}
</style>
