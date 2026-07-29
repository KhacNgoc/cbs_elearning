<template>
  <div class="app-container">
    <!-- Navbar Header (only when logged in) -->
    <nav class="navbar" v-if="userStore.isAuthenticated">
      <router-link to="/" class="nav-brand">
        <div class="nav-logo">B</div>
        <span>BIDV Core Training</span>
      </router-link>
      
      <div class="nav-links">
        <router-link v-for="item in navigationStore.mainItems" :key="item.id" :to="item.route" class="nav-link">
          <component :is="navigationIcon(item.icon)" class="nav-icon" />
          <span>{{ item.label }}</span>
        </router-link>
      </div>

      <div class="nav-user-menu">
        <router-link to="/profile" class="nav-user" title="Xem hồ sơ cá nhân">
          <div class="nav-user-avatar">{{ userAvatarLetter }}</div>
          <span>{{ userStore.fullName || 'quantrh' }}</span>
        </router-link>
        <button class="btn-logout" @click="handleLogout" title="Đăng xuất">
          <LogOut class="logout-icon" />
        </button>
      </div>
    </nav>

    <!-- Main Router View Content -->
    <main class="main-content">
      <router-view v-slot="{ Component }">
        <transition name="page" mode="out-in">
          <component :is="Component" />
        </transition>
      </router-view>
    </main>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted } from 'vue';
import { useUserStore } from './stores/userStore';
import { useCourseStore } from './stores/courseStore';
import { useNavigationStore } from './stores/navigationStore';
import { BookOpen, ClipboardCheck, HelpCircle, Home, LogOut, Settings, Trophy, UserCircle, Users } from 'lucide-vue-next';

const userStore = useUserStore();
const courseStore = useCourseStore();
const navigationStore = useNavigationStore();
const iconRegistry = { BookOpen, ClipboardCheck, HelpCircle, Home, Settings, Trophy, UserCircle, Users };
const navigationIcon = (name: string) => iconRegistry[name as keyof typeof iconRegistry] || Home;

const userAvatarLetter = computed(() => {
  return (userStore.fullName || userStore.username || 'Q').charAt(0).toUpperCase();
});

const handleLogout = () => {
  navigationStore.clear();
  userStore.logout();
};

onMounted(async () => {
  if (userStore.isAuthenticated) {
    await Promise.all([
      userStore.fetchProgress(),
      courseStore.fetchTopics(),
      navigationStore.load()
    ]);
  }
});
</script>

<style>
/* Page transition animation */
.page-enter-active,
.page-leave-active {
  transition: opacity 0.2s ease, transform 0.2s ease;
}

.page-enter-from {
  opacity: 0;
  transform: translateY(10px);
}

.page-leave-to {
  opacity: 0;
  transform: translateY(-10px);
}

.nav-icon {
  width: 18px;
  height: 18px;
}

.nav-user-menu {
  display: flex;
  align-items: center;
  gap: 0.75rem;
}

.nav-user {
  transition: var(--transition);
}

.nav-user:hover,
.nav-user.router-link-active {
  background: #d7e9e3;
  box-shadow: inset 0 0 0 1px rgba(0, 91, 65, 0.16);
}

.btn-logout {
  background: none;
  border: none;
  color: var(--text-muted);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0.5rem;
  border-radius: var(--radius-sm);
  transition: var(--transition);
}

.btn-logout:hover {
  background-color: #fee2e2;
  color: #ef4444;
}

.logout-icon {
  width: 18px;
  height: 18px;
}
</style>
