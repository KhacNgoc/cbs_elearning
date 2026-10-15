<template>
  <div class="lesson-rich-text" v-html="renderLessonText(content)"></div>
</template>
<script setup lang="ts">
import { renderLessonText } from "../../utils/lessonText";
import "../../assets/styles/lesson-content.css";
defineProps<{ content: string }>();
</script>
