<template>
  <main class="report-page">
    <header class="page-heading">
      <div>
        <h1>Chi tiết báo cáo nội dung</h1>
        <p class="page-description">Theo dõi báo cáo của bạn và phản hồi từ quản trị viên.</p>
      </div>
      <div class="page-actions">
        <RouterLink v-if="report" class="back-link" :to="`/lesson/${encodeURIComponent(report.lessonId)}`">Quay lại bài học</RouterLink>
        <button class="btn btn-secondary" :disabled="loading" @click="load">Tải lại</button>
      </div>
    </header>
    <p v-if="loading" class="state-card" role="status">Đang tải báo cáo…</p>
    <div v-if="error" class="state-card report-error" role="alert">
      <strong>Không tải được báo cáo</strong>
      <p>{{ error }}</p>
      <button class="btn btn-secondary" @click="load">Thử lại</button>
    </div>
    <article v-if="report" class="report-detail">
      <section class="report-summary" aria-labelledby="report-lesson-title">
        <div class="summary-heading">
          <div class="lesson-copy">
            <span class="eyebrow">Báo cáo #{{ report.id }}</span>
            <h2 id="report-lesson-title">{{ report.lessonTitle }}</h2>
            <p class="course-name">{{ report.courseName }}</p>
          </div>
          <span class="status-badge" :class="report.status.toLowerCase()">{{ reportStatus[report.status] }}</span>
        </div>
        <dl class="report-facts">
          <div><dt>Ngày gửi báo cáo</dt><dd><time :datetime="report.createdAt">{{ formatDate(report.createdAt) }}</time></dd></div>
          <div><dt>Cập nhật gần nhất</dt><dd><time v-if="report.reviewedAt" :datetime="report.reviewedAt">{{ formatDate(report.reviewedAt) }}</time><span v-else>Chưa có cập nhật xử lý</span></dd></div>
        </dl>
      </section>
      <div class="report-columns">
        <section class="report-panel" aria-labelledby="submitted-title">
          <header class="panel-heading">
            <h3 id="submitted-title">Nội dung đã gửi</h3>
            <p>Thông tin bạn đã phản ánh về bài học.</p>
          </header>
          <div class="panel-body">
            <p class="report-content">{{ report.content }}</p>
            <div v-if="report.images.length" class="evidence-section">
              <h4>Ảnh minh chứng <span>{{ report.images.length }}</span></h4>
              <div class="evidence-grid">
                <figure v-for="(src, index) in report.images" :key="src">
                  <ProtectedImage :src="src" :alt="`Ảnh minh chứng ${index + 1}`" />
                  <figcaption>Ảnh minh chứng {{ index + 1 }}</figcaption>
                </figure>
              </div>
            </div>
          </div>
        </section>
        <section class="report-panel response-panel" aria-labelledby="response-title">
          <header class="panel-heading">
            <h3 id="response-title">Phản hồi xử lý</h3>
            <p>Kết quả xem xét từ quản trị viên.</p>
          </header>
          <div class="panel-body">
            <template v-if="report.resolution">
              <p class="report-resolution">{{ report.resolution }}</p>
              <div v-if="report.reviewerName" class="reviewer-details">
                <span>Người phản hồi</span>
                <strong>{{ report.reviewerName }}</strong>
                <time v-if="report.reviewedAt" :datetime="report.reviewedAt">{{ formatDate(report.reviewedAt) }}</time>
              </div>
            </template>
            <div v-else class="awaiting-response">
              <strong>Báo cáo chưa có phản hồi.</strong>
              <p>Phản hồi từ quản trị viên sẽ hiển thị tại đây.</p>
            </div>
          </div>
        </section>
      </div>
    </article>
  </main>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue';
import { useRoute } from 'vue-router';
import { contentToolsApi, contentToolsError, reportStatus, type LessonReport } from '../api/contentToolsApi';
import ProtectedImage from '../components/ProtectedImage.vue';

const route = useRoute();
const report = ref<LessonReport | null>(null);
const loading = ref(false);
const error = ref('');
let request = 0;
const formatDate = (value: string) => new Date(value).toLocaleString('vi-VN');
async function load() {
  const current = ++request;
  report.value = null;
  error.value = '';
  loading.value = true;
  try {
    const id = Number(route.params.id);
    if (!Number.isSafeInteger(id) || id < 1) throw new Error('Invalid report');
    const result = await contentToolsApi.report(id);
    if (current === request) report.value = result;
  } catch (e) {
    if (current === request) error.value = contentToolsError(e);
  } finally {
    if (current === request) loading.value = false;
  }
}
watch(() => route.params.id, load, { immediate: true });
</script>

<style scoped>
.report-page { width: 100%; max-width: 1160px; margin: 0 auto; padding: 2rem; color: var(--text-main); }
.page-heading { display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 1rem; margin-bottom: 1.5rem; }
h1 { font-size: 1.5rem; line-height: 1.4; }
.page-description { margin-top: .4rem; font-size: .85rem; color: var(--text-muted); line-height: 1.6; }
.page-actions { display: flex; align-items: center; gap: 1rem; flex-wrap: wrap; }
.page-actions .btn, .report-error .btn { font-size: .85rem; }
.back-link { color: var(--primary-ink); font-size: .85rem; font-weight: 600; text-decoration: none; }
.back-link:hover { text-decoration: underline; }
.back-link:focus-visible { outline: 2px solid var(--primary-color); outline-offset: 4px; border-radius: 3px; }
.report-detail { display: grid; gap: 1.25rem; }
.report-summary, .report-panel, .state-card { min-width: 0; border: 1px solid var(--border-color); border-radius: var(--radius-md); background: var(--bg-card); box-shadow: var(--shadow-sm); }
.report-summary { overflow: hidden; }
.summary-heading { display: flex; align-items: flex-start; justify-content: space-between; flex-wrap: wrap; gap: 1rem; padding: 1.5rem; }
.lesson-copy { min-width: 0; flex: 1 1 260px; }
.eyebrow { display: block; margin-bottom: .55rem; color: var(--text-muted); font-size: .75rem; font-weight: 700; letter-spacing: .04em; text-transform: uppercase; }
.lesson-copy h2 { font-size: 1.2rem; line-height: 1.5; overflow-wrap: anywhere; }
.course-name { margin-top: .4rem; color: var(--text-muted); font-size: .85rem; line-height: 1.6; overflow-wrap: anywhere; }
.status-badge { display: inline-flex; align-items: center; gap: .45rem; padding: .45rem .75rem; border: 1px solid var(--border-color); border-radius: 999px; color: var(--text-main); background: var(--bg-main); font-size: .75rem; font-weight: 700; line-height: 1.4; }
.status-badge::before { content: ''; width: 6px; height: 6px; border-radius: 50%; background: currentColor; flex-shrink: 0; }
.status-badge.resolved { background: var(--primary-light); color: var(--primary-ink); border-color: var(--primary-light); }
.status-badge.new, .status-badge.in_review { color: var(--primary-ink); }
.status-badge.dismissed { color: var(--text-muted); }
.report-facts { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 1.25rem; padding: 1rem 1.5rem; border-top: 1px solid var(--border-color); background: var(--bg-main); }
.report-facts dt { font-size: .75rem; color: var(--text-muted); margin-bottom: .35rem; }
.report-facts dd { margin: 0; font-size: .85rem; font-weight: 600; line-height: 1.6; overflow-wrap: anywhere; }
.report-columns { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 1.25rem; align-items: start; }
.report-panel { overflow: hidden; }
.panel-heading { padding: 1.15rem 1.5rem; border-bottom: 1px solid var(--border-color); }
.panel-heading h3 { font-size: 1rem; line-height: 1.5; }
.panel-heading p { margin-top: .3rem; color: var(--text-muted); font-size: .8rem; line-height: 1.6; }
.panel-body { padding: 1.5rem; }
.report-content, .report-resolution { margin: 0; white-space: pre-wrap; overflow-wrap: anywhere; font-size: .9rem; line-height: 1.8; }
.response-panel { border-top: 3px solid var(--primary-color); }
.response-panel .panel-heading { padding-top: calc(1.15rem - 2px); }
.reviewer-details { display: grid; gap: .25rem; margin-top: 1.5rem; padding-top: 1rem; border-top: 1px solid var(--border-color); }
.reviewer-details span, .reviewer-details time { color: var(--text-muted); font-size: .75rem; line-height: 1.6; }
.reviewer-details strong { font-size: .85rem; line-height: 1.6; }
.awaiting-response { padding: 1.25rem; border: 1px dashed var(--border-color); border-radius: 8px; background: var(--bg-main); }
.awaiting-response strong { font-size: .9rem; line-height: 1.6; }
.awaiting-response p { margin-top: .4rem; color: var(--text-muted); font-size: .8rem; line-height: 1.7; }
.evidence-section { margin-top: 1.5rem; padding-top: 1.15rem; border-top: 1px solid var(--border-color); }
.evidence-section h4 { display: flex; align-items: center; gap: .5rem; font-size: .8rem; margin-bottom: .85rem; }
.evidence-section h4 span { padding: .1rem .4rem; border-radius: 5px; background: var(--bg-main); color: var(--text-muted); font-size: .7rem; }
.evidence-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: .85rem; }
figure { min-width: 0; margin: 0; }
figure :deep(img) { display: block; width: 100%; max-height: 300px; object-fit: contain; border: 1px solid var(--border-color); border-radius: 8px; background: var(--bg-main); }
figcaption { margin-top: .4rem; color: var(--text-muted); font-size: .75rem; line-height: 1.6; }
.state-card { padding: 2rem; font-size: .9rem; line-height: 1.7; }
.report-error { color: var(--ink-b91c1c); }
.report-error p { margin: .4rem 0 1rem; }
@media (max-width: 800px) { .report-columns { grid-template-columns: minmax(0, 1fr); } }
@media (max-width: 550px) {
  .report-page { padding: 1rem; }
  h1 { font-size: 1.25rem; }
  .page-actions { width: 100%; justify-content: space-between; }
  .summary-heading, .panel-heading, .panel-body { padding: 1rem; }
  .report-facts { grid-template-columns: minmax(0, 1fr); gap: .85rem; padding: 1rem; }
  .lesson-copy h2 { font-size: 1.05rem; }
  .evidence-grid { grid-template-columns: minmax(0, 1fr); }
}
</style>
