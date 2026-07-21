<template>
  <div class="discussion-section">
    <div class="section-title"><div><span class="eyebrow">Trao đổi</span><h2>Thảo luận & góp ý</h2></div><MessageSquare :size="24" /></div>
    <div class="feedback-form">
      <div class="rating-row"><span>Đánh giá bài học</span><StarRating :model-value="rating" @update:model-value="$emit('update:rating', $event)" /></div>
      <textarea :value="comment" rows="4" placeholder="Nhập câu hỏi hoặc góp ý của bạn..." @input="$emit('update:comment', ($event.target as HTMLTextAreaElement).value)"></textarea>
      <button class="submit-button" :disabled="!comment.trim()" @click="$emit('submit')">Gửi góp ý <Send :size="16" /></button>
    </div>
    <div class="comments-list">
      <div v-if="comments.length === 0" class="empty-comments">Chưa có góp ý. Hãy bắt đầu cuộc thảo luận.</div>
      <article v-for="item in comments" v-else :key="item.id" class="comment">
        <span class="avatar">{{ item.avatar }}</span>
        <div><div class="comment-meta"><strong>{{ item.user }}</strong><span>{{ item.date }}</span></div><StarRating :model-value="item.rating" readonly /><p>{{ item.content }}</p></div>
      </article>
    </div>
  </div>
</template>
<script setup lang="ts">
import { MessageSquare, Send } from 'lucide-vue-next';
import type { Comment } from '../../types';
import StarRating from '../StarRating.vue';
defineProps<{ comments: Comment[]; rating: number; comment: string }>();
defineEmits<{ 'update:rating': [value: number]; 'update:comment': [value: string]; submit: [] }>();
</script>
