<template>
  <div class="powerpoint-preview">
    <div v-if="loading" class="powerpoint-preview-state">
      <LoaderCircle class="powerpoint-preview-spinner" :size="32" />
      <span>Đang tải bản trình chiếu PowerPoint...</span>
    </div>
    <div v-else-if="error" class="powerpoint-preview-state powerpoint-preview-error">
      <FileWarning :size="38" />
      <strong>Không thể preview bản trình chiếu</strong>
      <p>{{ error }}</p>
    </div>
    <div v-show="!loading && !error" ref="scrollContainer" class="powerpoint-preview-scroll" @scroll="savePosition">
      <div ref="previewContainer" class="powerpoint-preview-content"></div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { FileWarning, LoaderCircle } from 'lucide-vue-next';
import { axiosClient } from '../../api/axiosClient';
import type { PptxViewer } from '@aiden0z/pptx-renderer';

const props = defineProps<{ url: string; originalFileName?: string; initialScrollOffset?: number; initialPage?: number }>();
const emit=defineEmits<{'position-change':[value:{page:number;scrollOffset:number}]}>();
function savePosition(){if(loading.value||!scrollContainer.value)return;emit('position-change',{page:(viewer?.currentSlideIndex || 0)+1,scrollOffset:scrollContainer.value.scrollTop});}
const previewContainer = ref<HTMLElement | null>(null);
const scrollContainer = ref<HTMLElement | null>(null);
const loading = ref(false);
const error = ref('');
let viewer: PptxViewer | null = null;
let requestId = 0;

const destroyViewer = () => {
  viewer?.destroy();
  viewer = null;
  if (previewContainer.value) previewContainer.value.innerHTML = '';
};

const renderDocument = async () => {
  const currentRequest = ++requestId;
  destroyViewer();

  const fileName = (props.originalFileName || props.url).toLowerCase();
  if (!fileName.endsWith('.pptx')) {
    loading.value = false;
    error.value = 'Định dạng .ppt cũ không hỗ trợ preview trực tiếp. Hãy tải file xuống hoặc chuyển sang định dạng .pptx.';
    return;
  }

  loading.value = true;
  error.value = '';
  try {
    const response = await axiosClient.get<ArrayBuffer>(props.url, { responseType: 'arraybuffer' });
    const { PptxViewer, RECOMMENDED_ZIP_LIMITS } = await import('@aiden0z/pptx-renderer');
    await nextTick();
    if (currentRequest !== requestId || !previewContainer.value) return;

    const opened = await PptxViewer.open(response.data, previewContainer.value, {
      fitMode: 'contain',
      zipLimits: RECOMMENDED_ZIP_LIMITS,
      lazySlides: true,
      lazyMedia: true,
      scrollContainer: scrollContainer.value || undefined,
      listOptions: { windowed: true, initialSlides: 3, batchSize: 3 }
    });
    if(currentRequest!==requestId){opened.destroy();return;}viewer=opened;
    loading.value=false;await nextTick();if(props.initialPage && props.initialPage>1)await viewer.goToSlide(Math.min(props.initialPage-1,viewer.slideCount-1),{behavior:'instant'});
    else if(scrollContainer.value)scrollContainer.value.scrollTop=props.initialScrollOffset || 0;
  } catch (cause) {
    console.error('Failed to preview PowerPoint document:', cause);
    if (currentRequest === requestId) {
      error.value = 'Không tải được nội dung PowerPoint. Bạn vẫn có thể sử dụng nút Tải xuống.';
    }
  } finally {
    if (currentRequest === requestId) loading.value = false;
  }
};

onMounted(renderDocument);
watch(() => props.url, renderDocument);
onBeforeUnmount(() => {
  requestId += 1;
  destroyViewer();
});
</script>

<style scoped>
.powerpoint-preview { height: 100%; min-height: 0; background: #111827; }
.powerpoint-preview-scroll { height: 100%; overflow: auto; padding: 1.25rem; }
.powerpoint-preview-content { width: min(100%, 1180px); min-height: 100%; margin: 0 auto; }
.powerpoint-preview-content :deep(.pptx-viewer-slide),
.powerpoint-preview-content :deep([data-slide-index]) { margin: 0 auto 1.25rem; box-shadow: 0 8px 24px rgba(0, 0, 0, .32); }
.powerpoint-preview-state { min-height: 100%; display: grid; place-content: center; justify-items: center; gap: .65rem; padding: 1rem; color: var(--ink-cbd5e1); text-align: center; }
.powerpoint-preview-error svg { color: var(--ink-eab308); }
.powerpoint-preview-error p { max-width: 500px; }
.powerpoint-preview-spinner { color: var(--ink-22a06b); animation: powerpoint-spin .8s linear infinite; }
@keyframes powerpoint-spin { to { transform: rotate(360deg); } }
@media (max-width: 600px) { .powerpoint-preview-scroll { padding: .5rem; } }
</style>
