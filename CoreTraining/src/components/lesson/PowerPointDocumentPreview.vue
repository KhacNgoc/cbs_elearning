<template>
  <div class="powerpoint-preview">
    <DocumentZoomControls v-if="pageCount && !error" v-model="zoom" :disabled="loading" />
    <DocumentPageNavigation
      v-if="pageCount && !error"
      :page="page"
      :total="pageCount"
      :disabled="loading || turningPage"
      @go="go"
    />
    <div v-if="loading" class="powerpoint-preview-state">
      <LoaderCircle class="powerpoint-preview-spinner" :size="32" />
      <span>Đang tải bản trình chiếu PowerPoint...</span>
    </div>
    <div
      v-else-if="error"
      class="powerpoint-preview-state powerpoint-preview-error"
    >
      <FileWarning :size="38" />
      <strong>Không thể preview bản trình chiếu</strong>
      <p>{{ error }}</p>
    </div>
    <div
      v-show="!loading && !error"
      ref="scrollContainer"
      class="powerpoint-preview-scroll"
    >
      <div class="powerpoint-page-surface">
        <div ref="previewContainer" class="powerpoint-preview-content"></div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { FileWarning, LoaderCircle } from "lucide-vue-next";
import DocumentZoomControls from "./DocumentZoomControls.vue";
import DocumentPageNavigation from "./DocumentPageNavigation.vue";
import { axiosClient } from "../../api/axiosClient";
import type { PptxViewer } from "@aiden0z/pptx-renderer";

const props = defineProps<{
  url: string;
  originalFileName?: string;
  initialScrollOffset?: number;
  initialPage?: number;
}>();
const emit = defineEmits<{
  "position-change": [value: { page: number; scrollOffset: number }];
}>();
function savePosition() {
  emit("position-change", { page: page.value, scrollOffset: 0 });
}
const zoom = ref(100);
const page = ref(1);
const pageCount = ref(0);
const turningPage = ref(false);
let observer: ResizeObserver | null = null;
let resizeFrame = 0;
function fitSlide() {
  if (!viewer || !scrollContainer.value || !previewContainer.value) return;
  const area = scrollContainer.value;
  const width = Math.max(1, area.clientWidth - 104);
  const height = Math.max(1, area.clientHeight - 64);
  previewContainer.value.style.width = `${(Math.min(width, (height * viewer.slideWidth) / viewer.slideHeight) * zoom.value) / 100}px`;
}
async function go(value: number) {
  const activeViewer = viewer;
  const currentRequest = requestId;
  if (!activeViewer || loading.value || turningPage.value || !Number.isFinite(value))
    return;
  const next = Math.min(Math.max(1, Math.floor(value)), pageCount.value);
  turningPage.value = true;
  try {
    await activeViewer.goToSlide(next - 1);
    if (viewer !== activeViewer || currentRequest !== requestId) return;
    page.value = next;
    savePosition();
  } catch (cause) {
    if (currentRequest === requestId) {
      console.error("Failed to change PowerPoint slide:", cause);
      error.value = "Không hiển thị được trang PowerPoint. Vui lòng tải lại.";
    }
  } finally {
    if (currentRequest === requestId) turningPage.value = false;
  }
}
const previewContainer = ref<HTMLElement | null>(null);
const scrollContainer = ref<HTMLElement | null>(null);
const loading = ref(false);
const error = ref("");
let viewer: PptxViewer | null = null;
let requestId = 0;

const destroyViewer = () => {
  viewer?.destroy();
  viewer = null;
  if (previewContainer.value) previewContainer.value.innerHTML = "";
};

const renderDocument = async () => {
  const currentRequest = ++requestId;
  destroyViewer();
  pageCount.value = 0;
  zoom.value = 100;
  page.value = 1;
  turningPage.value = false;

  const fileName = (props.originalFileName || props.url).toLowerCase();
  if (!fileName.endsWith(".pptx")) {
    loading.value = false;
    error.value =
      "Định dạng .ppt cũ không hỗ trợ preview trực tiếp. Hãy tải file xuống hoặc chuyển sang định dạng .pptx.";
    return;
  }

  loading.value = true;
  error.value = "";
  try {
    const response = await axiosClient.get<ArrayBuffer>(props.url, {
      responseType: "arraybuffer",
    });
    const { PptxViewer, RECOMMENDED_ZIP_LIMITS } =
      await import("@aiden0z/pptx-renderer");
    await nextTick();
    if (currentRequest !== requestId || !previewContainer.value) return;

    const opened = await PptxViewer.open(
      response.data,
      previewContainer.value,
      {
        fitMode: "contain",
        zipLimits: RECOMMENDED_ZIP_LIMITS,
        lazySlides: true,
        lazyMedia: true,
        renderMode: "slide",
      },
    );
    if (currentRequest !== requestId) {
      opened.destroy();
      return;
    }
    viewer = opened;
    pageCount.value = viewer.slideCount;
    fitSlide();
    page.value = Math.min(Math.max(1, props.initialPage || 1), pageCount.value);
    await viewer.goToSlide(page.value - 1);
    if (currentRequest !== requestId) return;
    loading.value = false;
    await nextTick();
    fitSlide();
    savePosition();
  } catch (cause) {
    console.error("Failed to preview PowerPoint document:", cause);
    if (currentRequest === requestId) {
      error.value =
        "Không tải được nội dung PowerPoint. Bạn vẫn có thể sử dụng nút Tải xuống.";
    }
  } finally {
    if (currentRequest === requestId) loading.value = false;
  }
};

onMounted(() => {
  observer = new ResizeObserver(() => {
    cancelAnimationFrame(resizeFrame);
    resizeFrame = requestAnimationFrame(fitSlide);
  });
  if (scrollContainer.value) observer.observe(scrollContainer.value);
  void renderDocument();
});
watch(() => props.url, renderDocument);
watch(zoom, () => {
  fitSlide();
  if (zoom.value === 100 && scrollContainer.value) {
    scrollContainer.value.scrollTop = 0;
    scrollContainer.value.scrollLeft = 0;
  }
});
onBeforeUnmount(() => {
  requestId += 1;
  observer?.disconnect();
  cancelAnimationFrame(resizeFrame);
  destroyViewer();
});
</script>

<style scoped>
.powerpoint-preview {
  position: relative;
  height: 100%;
  min-height: 0;
  background: #111827;
}
.powerpoint-preview-scroll {
  height: 100%;
  overflow: auto;
  padding: 12px 52px 52px;
}
.powerpoint-page-surface {
  width: max-content;
  min-width: 100%;
  min-height: 100%;
  display: flex;
}
.powerpoint-preview-content {
  width: min(100%, 1180px);
  flex-shrink: 0;
  margin: auto;
}
.powerpoint-preview-content :deep(.pptx-viewer-slide),
.powerpoint-preview-content :deep([data-slide-index]) {
  margin: 0 auto;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.32);
}
.powerpoint-preview-state {
  min-height: 100%;
  display: grid;
  place-content: center;
  justify-items: center;
  gap: 0.65rem;
  padding: 1rem;
  color: var(--ink-cbd5e1);
  text-align: center;
}
.powerpoint-preview-error svg {
  color: var(--ink-eab308);
}
.powerpoint-preview-error p {
  max-width: 500px;
}
.powerpoint-preview-spinner {
  color: var(--ink-22a06b);
  animation: powerpoint-spin 0.8s linear infinite;
}
@keyframes powerpoint-spin {
  to {
    transform: rotate(360deg);
  }
}
</style>
