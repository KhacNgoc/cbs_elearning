<template>
  <div ref="scrollContainer" class="word-preview" @scroll="savePosition">
    <div v-if="loading" class="word-preview-state">
      <LoaderCircle class="word-preview-spinner" :size="32" /><span
        >Đang tải tài liệu Word...</span
      >
    </div>
    <div v-else-if="error" class="word-preview-state word-preview-error">
      <FileWarning :size="38" /><strong>Không thể preview tài liệu</strong>
      <p>{{ error }}</p>
    </div>
    <div
      v-show="!loading && !error"
      ref="previewContainer"
      class="word-preview-content"
    ></div>
  </div>
</template>

<script setup lang="ts">
import { nextTick, onMounted, ref, watch } from "vue";
import { FileWarning, LoaderCircle } from "lucide-vue-next";
import { axiosClient } from "../../api/axiosClient";

const props = defineProps<{
  url: string;
  originalFileName?: string;
  initialScrollOffset?: number;
  initialPage?: number;
}>();
const emit = defineEmits<{
  "position-change": [value: { page: number; scrollOffset: number }];
}>();
const scrollContainer = ref<HTMLElement | null>(null);
function savePosition() {
  if (loading.value || !scrollContainer.value) return;
  const pages = Array.from(
    previewContainer.value?.querySelectorAll("section.docx-page") || [],
  );
  const top = scrollContainer.value.getBoundingClientRect().top;
  const page = Math.max(
    1,
    pages.findIndex((el) => el.getBoundingClientRect().bottom > top + 24) + 1,
  );
  emit("position-change", {
    page,
    scrollOffset: scrollContainer.value.scrollTop,
  });
}
const previewContainer = ref<HTMLElement | null>(null);
const loading = ref(false);
const error = ref("");

const renderDocument = async () => {
  if (!previewContainer.value) return;
  const fileName = (props.originalFileName || props.url).toLowerCase();
  if (!fileName.endsWith(".docx")) {
    error.value =
      "Định dạng .doc cũ không hỗ trợ preview trên trình duyệt. Hãy tải xuống hoặc sử dụng file .docx.";
    previewContainer.value.innerHTML = "";
    return;
  }

  loading.value = true;
  error.value = "";
  previewContainer.value.innerHTML = "";
  try {
    const response = await axiosClient.get<ArrayBuffer>(props.url, {
      responseType: "arraybuffer",
    });
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
      "Không tải được nội dung tài liệu. Bạn vẫn có thể sử dụng nút Tải xuống.";
  } finally {
    loading.value = false;
  }
};

onMounted(renderDocument);
watch(() => props.url, renderDocument);
</script>

<style scoped>
.word-preview {
  height: 100%;
  overflow: auto;
  padding: 1.5rem;
  background: var(--surface-e8eeec);
}
.word-preview-content {
  min-height: 100%;
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
.word-preview-spinner {
  color: var(--primary-ink);
  animation: word-spin 0.8s linear infinite;
}
@keyframes word-spin {
  to {
    transform: rotate(360deg);
  }
}
@media (max-width: 600px) {
  .word-preview {
    padding: 0.5rem;
  }
  .word-preview-content :deep(.docx-wrapper) {
    padding: 0;
  }
}
</style>
