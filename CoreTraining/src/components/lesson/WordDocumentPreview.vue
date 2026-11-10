<template>
  <div class="word-preview-shell">
    <DocumentZoomControls v-if="!loading && !error" v-model="zoom" />
    <DocumentPageNavigation v-if="pageCount && !loading && !error" :page="page" :total="pageCount" :disabled="loading" @go="go" />
    <div ref="scrollContainer" class="word-preview" @scroll="savePosition">
      <SkeletonLoader v-if="loading" variant="document" :rows="12" label="Đang tải tài liệu Word" />
      <div v-else-if="error" class="word-preview-state word-preview-error">
        <FileWarning :size="38" /><strong>Không thể preview tài liệu</strong>
        <p>{{ error }}</p>
      </div>
      <div
        v-show="!loading && !error"
        ref="previewContainer"
        class="word-preview-content"
        :style="{ zoom: (fitScale * zoom) / 100 }"
      ></div>
    </div>
  </div>
</template>

<script setup lang="ts">
import SkeletonLoader from "../SkeletonLoader.vue";
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { FileWarning } from "lucide-vue-next";
import DocumentZoomControls from "./DocumentZoomControls.vue";
import DocumentPageNavigation from "./DocumentPageNavigation.vue";
import {
  axiosClient,
  DOCUMENT_DOWNLOAD_TIMEOUT_MS,
} from "../../api/axiosClient";
import { assertZipWithinLimits, ZipSafetyError } from "../../utils/zipSafety";

const props = defineProps<{
  url: string;
  originalFileName?: string;
  initialScrollOffset?: number;
  initialPage?: number;
}>();
const emit = defineEmits<{
  "position-change": [
    value: { page: number; scrollOffset: number; completed?: boolean },
  ];
}>();
const zoom = ref(100);
const fitScale = ref(1);
const page = ref(1);
const pageCount = ref(0);
let pageAnchor: { page: number; scrollTop: number } | null = null;
let observer: ResizeObserver | null = null;
let resizeFrame = 0;
function fitDocument() {
  const page =
    previewContainer.value?.querySelector<HTMLElement>("section.docx-page");
  if (!page || !scrollContainer.value) return;
  fitScale.value = Math.min(
    1,
    Math.max(1, scrollContainer.value.clientWidth - 104) /
      (page.offsetWidth + 40),
  );
}
const scrollContainer = ref<HTMLElement | null>(null);
function savePosition() {
  if (loading.value || !scrollContainer.value) return;
  const pages = Array.from(
    previewContainer.value?.querySelectorAll("section.docx-page") || [],
  );
  const top = scrollContainer.value.getBoundingClientRect().top;
  pageCount.value = pages.length;
  const firstVisible = pages.findIndex((el) => el.getBoundingClientRect().bottom > top + 24);
  // At low zoom the browser can clamp scrollTop before the last page reaches
  // the top. Keep the page explicitly selected by navigation until the user scrolls.
  if (pageAnchor && Math.abs(scrollContainer.value.scrollTop - pageAnchor.scrollTop) < 1) {
    page.value = pageAnchor.page;
  } else {
    pageAnchor = null;
    page.value = Math.max(1, firstVisible >= 0 ? firstVisible + 1 : pages.length);
  }
  emit("position-change", {
    page: page.value,
    scrollOffset: scrollContainer.value.scrollTop,
    completed: pages.length > 0 && page.value === pages.length,
  });
}
function go(value: number) {
  if (loading.value || error.value || !scrollContainer.value || !Number.isFinite(value)) return;
  const pages = Array.from(previewContainer.value?.querySelectorAll<HTMLElement>('section.docx-page') || []);
  const next = Math.min(Math.max(1, Math.floor(value)), pages.length);
  const target = pages[next - 1];
  if (!target) return;
  scrollContainer.value.scrollTop += target.getBoundingClientRect().top - scrollContainer.value.getBoundingClientRect().top;
  pageAnchor = { page: next, scrollTop: scrollContainer.value.scrollTop };
  savePosition();
}
const previewContainer = ref<HTMLElement | null>(null);
const loading = ref(false);
const error = ref("");

const renderDocument = async () => {
  if (!previewContainer.value) return;
  page.value = 1;
  pageCount.value = 0;
  pageAnchor = null;
  const fileName = (props.originalFileName || props.url).toLowerCase();
  if (!fileName.endsWith(".docx")) {
    error.value =
      "Định dạng .doc cũ không hỗ trợ preview trên trình duyệt. Hãy tải xuống hoặc sử dụng file .docx.";
    previewContainer.value.innerHTML = "";
    return;
  }

  zoom.value = 100;
  loading.value = true;
  error.value = "";
  previewContainer.value.innerHTML = "";
  try {
    const response = await axiosClient.get<ArrayBuffer>(props.url, {
      responseType: "arraybuffer",
      timeout: DOCUMENT_DOWNLOAD_TIMEOUT_MS,
    });
    assertZipWithinLimits(response.data);
    const { renderAsync } = await import("docx-preview");
    await nextTick();
    if (previewContainer.value) {
      await renderAsync(response.data, previewContainer.value, undefined, {
        className: "docx-page",
        inWrapper: true,
        breakPages: true,
        ignoreWidth: false,
        ignoreHeight: false,
      });
      loading.value = false;
      await nextTick();
      fitDocument();
      await nextTick();
      if (scrollContainer.value) {
        const pages = Array.from(
          previewContainer.value.querySelectorAll("section.docx-page"),
        );
        const target =
          pages[
            Math.min(
              pages.length - 1,
              Math.max(0, (props.initialPage || 1) - 1),
            )
          ];
        scrollContainer.value.scrollTop =
          props.initialPage && props.initialPage > 1 && target
            ? target.getBoundingClientRect().top -
              scrollContainer.value.getBoundingClientRect().top +
              scrollContainer.value.scrollTop
            : props.initialScrollOffset || 0;
      }
    }
  } catch (cause) {
    console.error("Failed to preview Word document:", cause);
    error.value =
      cause instanceof ZipSafetyError
        ? "Tài liệu quá lớn để xem trực tiếp trên trình duyệt. Vui lòng sử dụng nút Tải xuống."
        : "Không tải được nội dung tài liệu. Bạn vẫn có thể sử dụng nút Tải xuống.";
  } finally {
    loading.value = false;
    if (!error.value) savePosition();
  }
};

onMounted(() => {
  observer = new ResizeObserver(() => {
    cancelAnimationFrame(resizeFrame);
    resizeFrame = requestAnimationFrame(fitDocument);
  });
  if (scrollContainer.value) observer.observe(scrollContainer.value);
  void renderDocument();
});
onBeforeUnmount(() => {
  observer?.disconnect();
  cancelAnimationFrame(resizeFrame);
});
watch(zoom, () => {
  if (zoom.value === 100 && scrollContainer.value)
    scrollContainer.value.scrollLeft = 0;
});
watch([zoom, fitScale], async () => {
  pageAnchor = null;
  await nextTick();
  if (!loading.value && !error.value) savePosition();
});
watch(() => props.url, renderDocument);
</script>

<style scoped>
.word-preview-shell {
  position: relative;
  height: 100%;
  min-height: 0;
  padding-bottom: 52px;
  background: var(--surface-e8eeec);
}
.word-preview {
  height: 100%;
  overflow: auto;
  padding: 1.5rem 52px;
  background: var(--surface-e8eeec);
}
.word-preview-content {
  min-height: 100%;
  width: max-content;
  min-width: 100%;
}
.word-preview-content :deep(.docx-wrapper) {
  padding: 20px;
  background: transparent;
}
.word-preview-content :deep(.docx-wrapper > section.docx-page) {
  margin: 0 auto 20px;
  box-shadow: 0 4px 18px rgba(15, 35, 29, 0.14);
}
.word-preview-state {
  min-height: 100%;
  display: grid;
  place-content: center;
  justify-items: center;
  gap: 0.65rem;
  color: var(--text-muted);
  text-align: center;
}
.word-preview-error svg {
  color: var(--ink-b7791f);
}
.word-preview-error p {
  max-width: 460px;
}
@media (max-width: 600px) {
  .word-preview {
    padding: 0.5rem 40px;
  }
}
</style>
