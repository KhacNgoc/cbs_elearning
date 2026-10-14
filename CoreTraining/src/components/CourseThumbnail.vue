<template>
  <div class="course-thumbnail" :class="{ compact }">
    <img v-if="src && !failed" :key="src" :src="src" :alt="name" :class="{ loaded }" loading="lazy" @load="loaded = true" @error="failed = true" />
    <div v-else class="thumbnail-placeholder"><BookOpen :size="compact ? 24 : 38" /><span v-if="!compact">{{ name }}</span></div>
  </div>
</template>
<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { BookOpen } from 'lucide-vue-next';
import { axiosClient } from '../api/axiosClient';
const props = defineProps<{ thumbnail?: string | null; name: string; compact?: boolean }>();
const failed = ref(false);
const loaded = ref(false);
const src = computed(() => props.thumbnail ? /^https?:\/\//.test(props.thumbnail) ? props.thumbnail : `${axiosClient.defaults.baseURL?.replace(/\/$/, '') || ''}${props.thumbnail}` : '');
watch(src, () => { failed.value = false; loaded.value = false; });
</script>
<style scoped>
.course-thumbnail{aspect-ratio:16/9;overflow:hidden;background:var(--primary-light);color:var(--primary-ink)}
.course-thumbnail img{display:block;width:100%;height:100%;object-fit:cover;opacity:0;transition:opacity var(--motion-duration) ease}.course-thumbnail img.loaded{opacity:1}
.thumbnail-placeholder{display:flex;flex-direction:column;align-items:center;justify-content:center;gap:.7rem;height:100%;padding:1rem;text-align:center}
.thumbnail-placeholder span{font-weight:600;max-width:85%;font-size:.9rem}.compact{width:68px;flex-shrink:0;border-radius:8px}.compact .thumbnail-placeholder{padding:.5rem}
</style>
