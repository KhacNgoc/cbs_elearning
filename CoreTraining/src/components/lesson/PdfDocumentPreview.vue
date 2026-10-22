<template>
  <div class="pdf-preview">
    <DocumentZoomControls v-if="pageCount && !error" v-model="zoom" :disabled="loading" />
    <DocumentPageNavigation
      v-if="pageCount && !error"
      :page="page"
      :total="pageCount"
      :disabled="loading"
      @go="go"
    />
    <p v-if="loading" class="pdf-state" role="status">
      Đang tải tài liệu PDF...
    </p>
    <p v-if="error" class="pdf-state" role="alert">{{ error }}</p>
    <div ref="container" class="pdf-canvas-container">
      <div class="pdf-page-surface">
        <canvas
          v-show="!loading && !error"
          ref="canvas"
          :aria-label="`Trang ${page} của tài liệu PDF`"
        />
      </div>
    </div>
  </div>
</template>
<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { getDocument, GlobalWorkerOptions } from "pdfjs-dist";
import type {
  PDFDocumentProxy,
  PDFDocumentLoadingTask,
  RenderTask,
} from "pdfjs-dist";
// Bundle as a worker so production serves a .js asset with a JavaScript MIME type.
import workerUrl from "pdfjs-dist/build/pdf.worker.min.mjs?worker&url";
import DocumentZoomControls from "./DocumentZoomControls.vue";
import DocumentPageNavigation from "./DocumentPageNavigation.vue";
import { axiosClient } from "../../api/axiosClient";
GlobalWorkerOptions.workerSrc = workerUrl;
const props = defineProps<{ url: string; initialPage?: number }>();
const emit = defineEmits<{
  "position-change": [value: { page: number; scrollOffset: number }];
}>();
const zoom = ref(100);
const page = ref(1),
  pageCount = ref(0),
  loading = ref(false),
  error = ref(""),
  canvas = ref<HTMLCanvasElement | null>(null),
  container = ref<HTMLElement | null>(null);
let pdf: PDFDocumentProxy | null = null,
  task: PDFDocumentLoadingTask | null = null,
  renderTask: RenderTask | null = null,
  request = 0,
  drawRequest = 0,
  observer: ResizeObserver | null = null,
  resizeFrame = 0;
async function draw() {
  if (!pdf || !canvas.value || !container.value) return;
  const current = ++drawRequest,
    previous = renderTask;
  previous?.cancel();
  await previous?.promise.catch(() => {});
  if (current !== drawRequest) return;
  const doc = pdf,
    requested = page.value;
  try {
    const sheet = await doc.getPage(requested);
    if (
      current !== drawRequest ||
      doc !== pdf ||
      requested !== page.value ||
      !canvas.value ||
      !container.value
    )
      return;
    const viewport = sheet.getViewport({ scale: 1 });
    const width = Math.max(1, container.value.clientWidth - 104);
    const height = Math.max(1, container.value.clientHeight - 64);
    const ratio = window.devicePixelRatio || 1;
    const scaled = sheet.getViewport({
      scale: Math.min(width / viewport.width, height / viewport.height, 2) * (zoom.value / 100) * ratio,
    });
    canvas.value.width = scaled.width;
    canvas.value.height = scaled.height;
    canvas.value.style.width = `${scaled.width / ratio}px`;
    canvas.value.style.height = `${scaled.height / ratio}px`;
    renderTask = sheet.render({ canvas: canvas.value, viewport: scaled });
    await renderTask.promise;
  } catch (e) {
    if (
      current === drawRequest &&
      (e as Error).name !== "RenderingCancelledException"
    )
      error.value = "Không hiển thị được trang PDF. Vui lòng tải lại.";
  }
}
async function load() {
  const id = ++request;
  loading.value = true;
  error.value = "";
  pageCount.value = 0;
  zoom.value = 100;
  drawRequest++;
  renderTask?.cancel();
  await task?.destroy();
  pdf = null;
  try {
    const data = (
      await axiosClient.get<ArrayBuffer>(props.url, {
        responseType: "arraybuffer",
      })
    ).data;
    if (id !== request) return;
    const loadingTask = getDocument({ data: new Uint8Array(data) });
    task = loadingTask;
    const doc = await loadingTask.promise;
    if (id !== request) {
      await loadingTask.destroy();
      return;
    }
    pdf = doc;
    pageCount.value = doc.numPages;
    page.value = Math.min(Math.max(1, props.initialPage || 1), doc.numPages);
    await nextTick();
    await draw();
    emit("position-change", { page: page.value, scrollOffset: 0 });
  } catch (cause) {
    console.error("Failed to load PDF document:", cause);
    if (id === request)
      error.value = "Không tải được PDF. Bạn có thể tải tài liệu xuống.";
  } finally {
    if (id === request) loading.value = false;
  }
}
async function go(value: number) {
  if (!pdf || !Number.isFinite(value)) return;
  page.value = Math.min(Math.max(1, Math.floor(value)), pageCount.value);
  await draw();
  emit("position-change", { page: page.value, scrollOffset: 0 });
}
onMounted(() => {
  void load();
  observer = new ResizeObserver(() => {
    cancelAnimationFrame(resizeFrame);
    resizeFrame = requestAnimationFrame(() => {
      void draw();
    });
  });
  if (container.value) observer.observe(container.value);
});
watch(() => props.url, load);
watch(zoom, async () => {
  await draw();
  if (zoom.value === 100 && container.value) {
    container.value.scrollTop = 0;
    container.value.scrollLeft = 0;
  }
});
onBeforeUnmount(() => {
  request++;
  drawRequest++;
  observer?.disconnect();
  cancelAnimationFrame(resizeFrame);
  renderTask?.cancel();
  void task?.destroy();
});
</script>
<style scoped>
.pdf-preview {
  position: relative;
  height: 100%;
  display: flex;
  flex-direction: column;
  background: var(--surface-e8eeec);
  min-height: 0;
}
.pdf-canvas-container {
  flex: 1;
  overflow: auto;
  padding: 12px 52px 52px;
  text-align: center;
  min-height: 0;
}
.pdf-page-surface {
  width: max-content;
  min-width: 100%;
  min-height: 100%;
  display: flex;
}
.pdf-canvas-container canvas {
  display: block;
  flex-shrink: 0;
  margin: auto;
  max-width: none;
  background: var(--surface-ffffff);
}
.pdf-state {
  text-align: center;
  padding: 1rem;
  color: var(--text-muted);
}
</style>
