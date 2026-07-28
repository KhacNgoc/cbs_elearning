<template>
  <div class="admin-shell">
    <aside class="admin-shell-sidebar" aria-label="Điều hướng quản trị">
      <nav>
        <button
          v-for="item in navigation"
          :key="item.id"
          :class="{ active: activeSection === item.id }"
          @click="openSection(item.id)"
        >
          <component :is="item.icon" :size="18" />
          <span>{{ item.label }}</span>
        </button>
      </nav>
      <div class="admin-shell-footer">
        <Settings :size="18" />
        <span>ROLE_ADMIN</span>
      </div>
    </aside>
    <section class="admin-shell-content">
      <RouterView />
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed } from "vue";
import { useRoute, useRouter } from "vue-router";
import { BookOpen, HelpCircle, Settings, Users } from "lucide-vue-next";

const route = useRoute();
const router = useRouter();
const navigation = [
  { id: "overview", label: "Tổng quan", icon: Settings },
  { id: "users", label: "Thành viên", icon: Users },
  { id: "lessons", label: "Bài học", icon: BookOpen },
  { id: "exams", label: "Đề thi trắc nghiệm", icon: HelpCircle },
];

const activeSection = computed(() =>
  route.path.includes("/admin/quizzes")
    ? "exams"
    : typeof route.query.tab === "string"
      ? route.query.tab
      : "overview",
);

const openSection = (tab: string) =>
  router.push({ path: "/admin", query: tab === "overview" ? {} : { tab } });
</script>

<style scoped>
.admin-shell {
  --admin-text-caption: 0.75rem;
  --admin-text-label: 0.8125rem;
  --admin-text-body: 0.875rem;
  --admin-heading-section: 1.125rem;
  --admin-heading-page: 1.5rem;
  display: flex;
  min-height: calc(100dvh - 65px);
  max-height: calc(100dvh - 65px);
  margin: -2rem;
  overflow: hidden;
  background: var(--bg-main);
}

.admin-shell-sidebar {
  position: sticky;
  top: 0;
  align-self: stretch;
  width: 280px;
  flex: 0 0 280px;
  display: flex;
  flex-direction: column;
  background: var(--primary-color);
  color: white;
  box-shadow: 2px 0 10px rgba(0, 0, 0, 0.05);
}

.admin-shell-sidebar nav {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 0.5rem;
  padding: 2rem 1rem 1.5rem;
}

.admin-shell-sidebar button {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  padding: 0.85rem 1.25rem;
  border: 0;
  border-radius: var(--radius-md);
  background: transparent;
  color: rgba(255, 255, 255, 0.75);
  cursor: pointer;
  font-weight: 600;
  text-align: left;
}

.admin-shell-sidebar button:hover {
  background: rgba(255, 255, 255, 0.08);
  color: white;
}

.admin-shell-sidebar button.active {
  background: white;
  color: var(--primary-color);
  box-shadow: var(--shadow-sm);
}

.admin-shell-footer {
  display: flex;
  align-items: center;
  gap: 0.55rem;
  margin: 0 1rem 1rem;
  padding: 1rem 0.5rem;
  border-top: 1px solid rgba(255, 255, 255, 0.15);
  font-size: 0.75rem;
  font-weight: 700;
}

.admin-shell-content {
  min-width: 0;
  flex: 1;
  align-self: stretch;
  overflow-y: auto;
  overflow-x: hidden;
  background: white;
  color: var(--text-main);
  font-size: var(--admin-text-body);
  line-height: 1.5;
  scrollbar-gutter: stable;
}

.admin-shell-content :deep(.card-title) {
  font-size: var(--admin-heading-section);
  line-height: 1.3;
}

.admin-shell-content :deep(.card-subtitle),
.admin-shell-content :deep(.header-desc),
.admin-shell-content :deep(.field-help),
.admin-shell-content :deep(.video-source-help),
.admin-shell-content :deep(.selected-file-meta) {
  font-size: var(--admin-text-caption);
  line-height: 1.5;
}

.admin-shell-content :deep(.form-control),
.admin-shell-content :deep(input),
.admin-shell-content :deep(textarea),
.admin-shell-content :deep(select),
.admin-shell-content :deep(td) {
  font-size: var(--admin-text-body);
}

.admin-shell-content :deep(label),
.admin-shell-content :deep(th),
.admin-shell-content :deep(.btn),
.admin-shell-content :deep(button) {
  font-size: var(--admin-text-label);
}

.admin-shell-content :deep(th),
.admin-shell-content :deep(.form-group > label) {
  line-height: 1.4;
}

.admin-shell-content :deep(.admin-tab-content .tab-pane:not(.overview-pane) .card) {
  padding: 0;
  border: 0;
  border-radius: 0;
  box-shadow: none;
}

@media (max-width: 760px) {
  .admin-shell {
    margin: -1rem;
    max-height: none;
    overflow: visible;
  }

  .admin-shell-sidebar {
    display: none;
  }

  .admin-shell-content {
    overflow: visible;
  }
}
</style>
