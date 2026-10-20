<template>
  <div class="theme-control" :class="{ 'theme-floating': floating }">
    <button
      type="button"
      class="theme-toggle"
      :title="
        isDark ? 'Chuyển sang giao diện sáng' : 'Chuyển sang giao diện tối'
      "
      :aria-label="
        isDark ? 'Chuyển sang giao diện sáng' : 'Chuyển sang giao diện tối'
      "
      :aria-pressed="isDark"
      @click="setTheme(isDark ? 'light' : 'dark')"
    >
      <Sun v-if="isDark" :size="19" /><Moon v-else :size="19" />
    </button>
    <button
      v-if="preference !== 'system'"
      type="button"
      class="theme-system"
      title="Dùng giao diện theo thiết bị"
      aria-label="Dùng giao diện theo thiết bị"
      @click="setTheme('system')"
    >
      <Monitor :size="13" />
    </button>
  </div>
</template>
<script setup lang="ts">
import { Monitor, Moon, Sun } from "lucide-vue-next";
import { useTheme } from "../composables/useTheme";
defineProps<{ floating?: boolean }>();
const { isDark, preference, setTheme } = useTheme();
</script>
<style scoped>
.theme-control {
  position: relative;
  flex-shrink: 0;
  display: flex;
  align-items: center;
}
.theme-toggle {
  display: grid;
  place-items: center;
  width: 40px;
  height: 40px;
  border: 1px solid var(--border-color);
  border-radius: 12px;
  color: var(--text-main);
  background: var(--bg-card);
  cursor: pointer;
}
.theme-toggle:hover {
  background: var(--primary-light);
  color: var(--primary-ink);
}
.theme-toggle:focus-visible,
.theme-system:focus-visible {
  outline: 2px solid var(--primary-ink);
  outline-offset: 3px;
}
.theme-system {
  position: absolute;
  right: -4px;
  bottom: -6px;
  display: grid;
  place-items: center;
  width: 22px;
  height: 22px;
  background: var(--bg-card);
  color: var(--text-muted);
  border: 1px solid var(--border-color);
  border-radius: 50%;
  cursor: pointer;
}
.theme-floating {
  position: fixed;
  top: 20px;
  right: 20px;
  z-index: 100;
}
@media (max-width: 560px) {
  .theme-toggle {
    width: 36px;
    height: 36px;
  }
}
</style>
