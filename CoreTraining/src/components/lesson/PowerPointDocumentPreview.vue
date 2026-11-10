<template>
  <div class="powerpoint-preview">
    <DocumentZoomControls
      v-if="pageCount && !error"
      v-model="zoom"
      :disabled="loading"
    />
    <DocumentPageNavigation
      v-if="pageCount && !error"
      :page="page"
      :total="pageCount"
      :disabled="loading || turningPage"
      :prev-disabled="!canGoPrev"
      :next-disabled="!canGoNext"
      @go="onNavigate"
    />
    <SkeletonLoader
      v-if="loading"
      variant="media"
      label="Đang tải bản trình chiếu PowerPoint"
    />
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
import SkeletonLoader from "../SkeletonLoader.vue";
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { FileWarning } from "lucide-vue-next";
import DocumentZoomControls from "./DocumentZoomControls.vue";
import DocumentPageNavigation from "./DocumentPageNavigation.vue";
import {
  axiosClient,
  DOCUMENT_DOWNLOAD_TIMEOUT_MS,
} from "../../api/axiosClient";
import type { Viewer } from "@web-ppt/viewer-core";

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
const page = ref(1);
const pageCount = ref(0);
const turningPage = ref(false);
const animationDone = ref(0);
const animationTotal = ref(0);
const hasPendingAnimation = ref(false);
const previewContainer = ref<HTMLElement | null>(null);
const scrollContainer = ref<HTMLElement | null>(null);
const loading = ref(false);
const error = ref("");

let viewer: Viewer | null = null;
let requestId = 0;
let observer: ResizeObserver | null = null;
let resizeFrame = 0;

const canGoPrev = computed(
  () => page.value > 1 || animationDone.value > 0,
);
const canGoNext = computed(
  () => page.value < pageCount.value || hasPendingAnimation.value,
);

function syncViewerState() {
  if (!viewer) return;
  page.value = viewer.index + 1;
  pageCount.value = viewer.count;
  animationDone.value = viewer.animationDone;
  animationTotal.value = viewer.animationTotal;
  hasPendingAnimation.value = viewer.hasPendingAnimation;
  emit("position-change", {
    page: page.value,
    scrollOffset: 0,
    completed:
      page.value === pageCount.value &&
      !viewer.hasPendingAnimation,
  });
}

function fitSlide() {
  if (!viewer || !scrollContainer.value || !previewContainer.value) return;
  const area = scrollContainer.value;
  // Leave room for side arrows, bottom chrome, and a small inset so the slide edge is not clipped.
  const availW = Math.max(1, area.clientWidth - 112);
  const availH = Math.max(1, area.clientHeight - 72);
  const slideW = Math.max(1, viewer.presentation.width || 960);
  const slideH = Math.max(1, viewer.presentation.height || 540);
  const contain = Math.min(availW / slideW, availH / slideH);
  const scale = contain * (zoom.value / 100);
  // Host exactly matches the scaled slide; Web-PPT zoom=1 fills that box without cropping edges.
  previewContainer.value.style.width = `${slideW * scale}px`;
  previewContainer.value.style.height = `${slideH * scale}px`;
  viewer.setZoom(1);
}

async function onNavigate(target: number) {
  const active = viewer;
  const currentRequest = requestId;
  if (!active || loading.value || turningPage.value) return;
  turningPage.value = true;
  try {
    if (target > page.value) active.next();
    else if (target < page.value) active.prev();
    if (viewer !== active || currentRequest !== requestId) return;
    syncViewerState();
  } catch (cause) {
    if (currentRequest === requestId) {
      console.error("Failed to change PowerPoint slide:", cause);
      error.value = "Không hiển thị được trang PowerPoint. Vui lòng tải lại.";
    }
  } finally {
    if (currentRequest === requestId) turningPage.value = false;
  }
}

function destroyViewer() {
  viewer?.destroy();
  viewer = null;
  if (previewContainer.value) previewContainer.value.innerHTML = "";
}

const renderDocument = async () => {
  const currentRequest = ++requestId;
  destroyViewer();
  pageCount.value = 0;
  zoom.value = 100;
  page.value = 1;
  animationDone.value = 0;
  animationTotal.value = 0;
  hasPendingAnimation.value = false;
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
      timeout: DOCUMENT_DOWNLOAD_TIMEOUT_MS,
    });
    const [{ parse }, { Viewer: WebPptViewer }] = await Promise.all([
      import("@web-ppt/core"),
      import("@web-ppt/viewer-core"),
    ]);
    await nextTick();
    if (currentRequest !== requestId || !previewContainer.value) return;

    const presentation = await parse(response.data);
    if (currentRequest !== requestId || !previewContainer.value) return;

    const opened = new WebPptViewer(previewContainer.value, presentation, {
      animate: true,
      skipHidden: true,
      media: "player",
    });
    if (currentRequest !== requestId) {
      opened.destroy();
      return;
    }
    viewer = opened;
    viewer.onChange = () => syncViewerState();
    viewer.onAnimStep = () => syncViewerState();

    const start = Math.min(
      Math.max(1, props.initialPage || 1),
      Math.max(1, viewer.count),
    );
    if (start !== 1) viewer.goTo(start - 1, "forward");
    // Jump to end-build when restoring progress so learners resume the finished frame.
    if ((props.initialPage || 1) > 1) viewer.finishAnimations();

    syncViewerState();
    loading.value = false;
    await nextTick();
    fitSlide();
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
  background: var(--bg-main);
  -webkit-user-select: none;
  user-select: none;
}
.powerpoint-preview :is(input, textarea, select) {
  -webkit-user-select: text;
  user-select: text;
}
.powerpoint-preview-scroll {
  height: 100%;
  overflow: auto;
  padding: 16px 56px 56px;
  box-sizing: border-box;
}
.powerpoint-page-surface {
  width: max-content;
  min-width: 100%;
  min-height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
}
.powerpoint-preview-content {
  position: relative;
  flex-shrink: 0;
  margin: auto;
  overflow: visible;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.32);
}
.powerpoint-preview-content :deep(svg) {
  display: block;
  max-width: none;
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
</style>
