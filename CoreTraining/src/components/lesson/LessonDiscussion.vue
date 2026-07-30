<template>
  <div class="discussion-section">
    <div class="section-title">
      <div><span class="eyebrow">Trao đổi</span><h2>Thảo luận & góp ý</h2></div>
      <MessageSquare :size="24" />
    </div>
    <div class="feedback-form">
      <div class="rating-row">
        <span>Đánh giá bài học</span>
        <StarRating :model-value="rating" @update:model-value="$emit('update:rating', $event)" />
      </div>
      <textarea :value="comment" rows="4" placeholder="Nhập câu hỏi hoặc góp ý của bạn..."
        @input="$emit('update:comment', ($event.target as HTMLTextAreaElement).value)"></textarea>
      <button class="submit-button" :disabled="!comment.trim()" @click="$emit('submit')">
        Gửi góp ý <Send :size="16" />
      </button>
    </div>

    <div class="comments-list">
      <div v-if="comments.length === 0" class="empty-comments">Chưa có góp ý. Hãy bắt đầu cuộc thảo luận.</div>
      <article v-for="item in comments" v-else :key="item.id" class="comment-thread">
        <div class="comment">
          <span class="avatar">{{ item.avatar }}</span>
          <div class="comment-body">
            <div class="comment-bubble">
              <div class="comment-meta"><strong>{{ item.user }}</strong><span>{{ item.date }}</span></div>
              <StarRating v-if="item.rating" :model-value="item.rating" readonly />
              <p>{{ item.content }}</p>
            </div>
            <div class="comment-actions">
              <button class="reply-action" @click="openReply(item.id, item.id, item.user)">
                <Reply :size="14" /> Trả lời
              </button>
              <button v-if="canDelete(item)" class="delete-action" @click="requestDelete(item.id)">
                <Trash2 :size="14" /> Gỡ
              </button>
            </div>
          </div>
        </div>

        <div v-if="item.replies?.length" class="reply-list">
          <div v-for="reply in item.replies" :key="reply.id" class="comment reply-comment">
            <span class="avatar reply-avatar">{{ reply.avatar }}</span>
            <div class="comment-body">
              <div class="comment-bubble">
                <div class="comment-meta"><strong>{{ reply.user }}</strong><span>{{ reply.date }}</span></div>
                <p><strong v-if="reply.replyToUser" class="mention">@{{ reply.replyToUser }}</strong> {{ reply.content }}</p>
              </div>
              <div class="comment-actions">
                <button class="reply-action" @click="openReply(item.id, reply.id, reply.user)">
                  <Reply :size="14" /> Trả lời
                </button>
                <button v-if="canDelete(reply)" class="delete-action" @click="requestDelete(reply.id)">
                  <Trash2 :size="14" /> Gỡ
                </button>
              </div>
            </div>
          </div>
        </div>

        <div v-if="replyingTo?.rootId === item.id" class="reply-composer">
          <span class="avatar composer-avatar">{{ currentUserInitial }}</span>
          <div>
            <div class="replying-label">
              Đang trả lời <strong>@{{ replyingTo.user }}</strong>
              <button aria-label="Hủy trả lời" @click="cancelReply"><X :size="14" /></button>
            </div>
            <textarea ref="replyInput" v-model="replyContent" rows="2"
              :placeholder="`Viết phản hồi cho ${replyingTo.user}...`"
              @keydown.ctrl.enter.prevent="sendReply"></textarea>
            <div class="reply-composer-actions">
              <small>Ctrl + Enter để gửi</small>
              <button :disabled="!replyContent.trim()" @click="sendReply">Gửi phản hồi <Send :size="14" /></button>
            </div>
          </div>
        </div>
      </article>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, ref } from 'vue';
import { MessageSquare, Reply, Send, Trash2, X } from 'lucide-vue-next';
import type { Comment } from '../../types';
import StarRating from '../StarRating.vue';

const props = defineProps<{
  comments: Comment[];
  rating: number;
  comment: string;
  currentUsername: string;
  isAdmin: boolean;
}>();
const emit = defineEmits<{
  'update:rating': [value: number];
  'update:comment': [value: string];
  submit: [];
  reply: [rootCommentId: number, replyToCommentId: number, content: string];
  delete: [commentId: number];
}>();

const replyingTo = ref<{ rootId: number; targetId: number; user: string } | null>(null);
const replyContent = ref('');
const replyInput = ref<HTMLTextAreaElement | null>(null);
const currentUserInitial = computed(() => (localStorage.getItem('username') || 'U').charAt(0).toUpperCase());
const canDelete = (comment: Comment) =>
  props.isAdmin || comment.user === props.currentUsername;
const requestDelete = (commentId: number) => {
  if (window.confirm('Bạn có chắc muốn gỡ bình luận này?')) {
    emit('delete', commentId);
  }
};

const openReply = async (rootId: number, targetId: number, user: string) => {
  replyingTo.value = { rootId, targetId, user };
  replyContent.value = '';
  await nextTick();
  replyInput.value?.focus();
};
const cancelReply = () => {
  replyingTo.value = null;
  replyContent.value = '';
};
const sendReply = () => {
  if (!replyingTo.value || !replyContent.value.trim()) return;
  emit('reply', replyingTo.value.rootId, replyingTo.value.targetId, replyContent.value.trim());
  cancelReply();
};
</script>

<style scoped>
.comment-thread { padding: 1rem 0; border-bottom: 1px solid var(--border-color); }
.comment-thread > .comment, .reply-comment { padding: 0; border: 0; }
.comment-body { min-width: 0; }
.comment-bubble { padding: .7rem .85rem; border-radius: 8px; background: #f2f6f4; }
.comment-bubble p { margin-top: .35rem; color: #43534d; line-height: 1.5; }
.comment-actions { margin: .3rem 0 0 .55rem; display: flex; align-items: center; gap: .8rem; }
.reply-action, .delete-action { display: inline-flex; align-items: center; gap: .25rem; border: 0; background: transparent; cursor: pointer; font-size: .75rem; font-weight: 700; }
.reply-action { color: var(--primary-color); }
.delete-action { color: #b42318; }
.delete-action:hover { color: #8f1710; }
.reply-list { margin: .7rem 0 0 3rem; padding-left: .9rem; display: grid; gap: .65rem; border-left: 2px solid #d8e5e0; }
.reply-comment { grid-template-columns: 32px 1fr; }
.reply-avatar, .composer-avatar { width: 32px; height: 32px; font-size: .75rem; }
.mention { color: var(--primary-color); }
.reply-composer { margin: .75rem 0 0 3rem; display: grid; grid-template-columns: 32px 1fr; gap: .6rem; }
.reply-composer textarea { width: 100%; padding: .65rem .75rem; resize: vertical; border: 1px solid #cfd9d5; border-radius: 8px; outline: none; }
.reply-composer textarea:focus { border-color: var(--primary-color); box-shadow: 0 0 0 3px rgba(0, 91, 65, .1); }
.replying-label { margin-bottom: .35rem; display: flex; align-items: center; gap: .3rem; color: var(--text-muted); font-size: .72rem; }
.replying-label button { width: 22px; height: 22px; display: grid; place-items: center; border: 0; border-radius: 50%; background: #e5ece9; color: #64748b; cursor: pointer; }
.reply-composer-actions { margin-top: .35rem; display: flex; align-items: center; justify-content: space-between; }
.reply-composer-actions small { color: var(--text-muted); }
.reply-composer-actions button { padding: .45rem .7rem; display: inline-flex; align-items: center; gap: .3rem; border: 0; border-radius: 6px; background: var(--primary-color); color: white; cursor: pointer; font-weight: 700; }
.reply-composer-actions button:disabled { opacity: .45; cursor: not-allowed; }
@media (max-width: 600px) {
  .reply-list, .reply-composer { margin-left: 1rem; }
}
</style>
