<template>
  <main class="flashcard-review-page animate-fade-in">
    <header class="review-header">
      <RouterLink to="/" class="back-link"><ChevronLeft :size="18" /> Trang chủ</RouterLink>
      <span class="eyebrow">ÔN TẬP FLASHCARD</span>
      <h1>{{ queue?.courseName || "Ôn tập" }}</h1>
      <p v-if="queue">
        {{ queue.dueTotal }} thẻ đến hạn{{ queue.dueTotal > queue.cards.length ? ` · phiên này ôn ${queue.cards.length} thẻ` : "" }}.
        Thẻ trả lời "Chưa nhớ" sẽ quay lại sau 10 phút.
      </p>
    </header>

    <PageLoading v-if="loading" label="Đang tải thẻ cần ôn..." />
    <div v-else-if="error" class="card review-state" role="alert">
      <p>{{ error }}</p>
      <button class="btn btn-primary" @click="load">Thử lại</button>
    </div>
    <div v-else-if="!queue?.cards.length" class="card review-state">
      <CheckCircle2 :size="30" />
      <strong>Bạn đã ôn hết thẻ đến hạn của khóa này</strong>
      <p>Tiếp tục học bài mới để mở thêm flashcard.</p>
      <RouterLink class="btn btn-primary" :to="`/courses/${encodeURIComponent(courseId)}`">Mở khóa học</RouterLink>
    </div>
    <section v-else class="card review-card">
      <FlashcardStudy :cards="queue.cards" show-lesson @exit="router.push('/')" />
    </section>
  </main>
</template>

<script setup lang="ts">
import { computed, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { CheckCircle2, ChevronLeft } from "lucide-vue-next";
import PageLoading from "../components/PageLoading.vue";
import FlashcardStudy from "../components/flashcards/FlashcardStudy.vue";
import { apiErrorMessage, flashcardApi, type FlashcardReviewQueue } from "../api/flashcardApi";

const route = useRoute();
const router = useRouter();
const courseId = computed(() => String(route.query.courseId || ""));
const queue = ref<FlashcardReviewQueue | null>(null);
const loading = ref(false);
const error = ref("");

async function load() {
  if (!courseId.value) {
    await router.replace("/");
    return;
  }
  loading.value = true;
  error.value = "";
  try {
    queue.value = await flashcardApi.dueQueue(courseId.value);
  } catch (e) {
    error.value = apiErrorMessage(e, "Không tải được thẻ cần ôn.");
  } finally {
    loading.value = false;
  }
}

watch(courseId, load, { immediate: true });
</script>

<style scoped>
.flashcard-review-page {
  width: 100%;
  max-width: 860px;
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  gap: 1.2rem;
  min-width: 0;
}
.back-link {
  display: inline-flex;
  align-items: center;
  gap: 0.2rem;
  width: fit-content;
  margin-bottom: 0.6rem;
  color: var(--primary-ink);
  font-size: 0.85rem;
  font-weight: 600;
  text-decoration: none;
}
.eyebrow {
  display: block;
  font-size: 0.7rem;
  letter-spacing: 1px;
  color: var(--primary-ink);
  font-weight: 700;
}
.review-header h1 {
  margin: 0.3rem 0;
  font-size: 1.5rem;
  letter-spacing: -0.02em;
  overflow-wrap: anywhere;
}
.review-header p {
  margin: 0;
  color: var(--text-muted);
  font-size: 0.88rem;
}
.review-card {
  padding: 1.25rem;
}
.review-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.6rem;
  padding: 2.5rem 1rem;
  text-align: center;
  color: var(--success-ink);
}
.review-state p {
  margin: 0;
  color: var(--text-muted);
}
@media (max-width: 600px) {
  .review-card {
    padding: 0.9rem;
  }
}
</style>
