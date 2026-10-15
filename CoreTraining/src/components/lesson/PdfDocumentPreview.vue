<template>
  <div class="pdf-preview">
    <div class="pdf-toolbar">
      <button :disabled="loading || page <= 1" @click="go(page - 1)">
        Trang trước</button
      ><label
        >Trang
        <input
          :value="page"
          type="number"
          min="1"
          :max="pageCount || 1"
          :disabled="loading"
          @change="go(Number(($event.target as HTMLInputElement).value))"
        />
        / {{ pageCount || "—" }}</label
      ><button :disabled="loading || page >= pageCount" @click="go(page + 1)">
        Trang sau
      </button>
    </div>
    <p v-if="loading" class="pdf-state" role="status">
      Đang tải tài liệu PDF...
    </p>
    <p v-if="error" class="pdf-state" role="alert">{{ error }}</p>
    <div ref="container" class="pdf-canvas-container">
      <canvas ref="canvas" :aria-label="`Trang ${page} của tài liệu PDF`" />
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
import workerUrl from "pdfjs-dist/build/pdf.worker.min.mjs?url";
import { axiosClient } from "../../api/axiosClient";
GlobalWorkerOptions.workerSrc = workerUrl;
const props = defineProps<{ url: string; initialPage?: number }>();
const emit = defineEmits<{
  "position-change": [value: { page: number; scrollOffset: number }];
}>();
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
  observer: ResizeObserver | null = null;
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
    const width = Math.max(200, container.value.clientWidth - 24);
    const ratio = window.devicePixelRatio || 1;
    const scaled = sheet.getViewport({
      scale: Math.min(width / viewport.width, 2) * ratio,
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
  } catch {
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
    void draw();
  });
  if (container.value) observer.observe(container.value);
});
watch(() => props.url, load);
onBeforeUnmount(() => {
  request++;
  drawRequest++;
  observer?.disconnect();
  renderTask?.cancel();
  void task?.destroy();
});
</script>
<style scoped>
.pdf-preview {
  height: 100%;
  display: flex;
  flex-direction: column;
  background: var(--surface-e8eeec);
  min-height: 0;
}
.pdf-toolbar {
  display: flex;
  justify-content: center;
  align-items: center;
  flex-wrap: wrap;
  gap: 0.7rem;
  padding: 0.6rem;
  background: var(--bg-card);
  flex-shrink: 0;
}
.pdf-toolbar button {
  padding: 0.4rem 0.6rem;
  border: 1px solid var(--border-color);
  border-radius: 6px;
  background: var(--bg-main);
  color: var(--primary-ink);
}
.pdf-toolbar button:disabled {
  opacity: 0.4;
}
.pdf-toolbar input {
  width: 65px;
  padding: 0.3rem;
  border: 1px solid var(--border-color);
  border-radius: 4px;
}
.pdf-toolbar label {
  font-size: 0.8rem;
}
.pdf-canvas-container {
  flex: 1;
  overflow: auto;
  padding: 12px;
  text-align: center;
  min-height: 0;
}
.pdf-canvas-container canvas {
  display: block;
  margin: auto;
  max-width: 100%;
  background: var(--surface-ffffff);
}
.pdf-state {
  text-align: center;
  padding: 1rem;
  color: var(--text-muted);
}
</style>
