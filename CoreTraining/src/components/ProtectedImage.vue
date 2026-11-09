<template>
  <img v-if="url" :src="url" :alt="alt" />
  <span v-else-if="error" role="status">Không tải được ảnh minh chứng.</span>
  <SkeletonLoader v-else variant="media" height="10rem" label="Đang tải ảnh" />
</template>
<script setup lang="ts">
import SkeletonLoader from "./SkeletonLoader.vue";
import { ref, watch, onBeforeUnmount } from "vue";
import { axiosClient } from "../api/axiosClient";
const props = defineProps<{ src: string; alt: string }>();
const url = ref(""),
  error = ref(false);
let request = 0;
watch(
  () => props.src,
  async (src) => {
    const id = ++request;
    if (url.value) URL.revokeObjectURL(url.value);
    url.value = "";
    error.value = false;
    try {
      const blob = (await axiosClient.get<Blob>(src, { responseType: "blob" }))
        .data;
      if (id === request) url.value = URL.createObjectURL(blob);
    } catch {
      if (id === request) error.value = true;
    }
  },
  { immediate: true },
);
onBeforeUnmount(() => {
  request++;
  if (url.value) URL.revokeObjectURL(url.value);
});
</script>
