<template>
  <div class="app-container" :class="{ 'admin-route': showAdminSidebar }">
    <!-- Navbar Header (only when logged in and not on change-password page) -->
    <nav class="navbar" v-if="userStore.isAuthenticated && route.name !== 'ChangePassword'">
      <div class="nav-brand-group">
        <button v-if="showAdminSidebar" type="button" class="sidebar-toggle" :aria-label="sidebarVisible ? 'Ẩn sidebar' : 'Hiện sidebar'" :title="sidebarVisible ? 'Ẩn sidebar' : 'Hiện sidebar'" :aria-expanded="sidebarVisible" aria-controls="admin-navigation-sidebar" @click="sidebarVisible = !sidebarVisible">
          <component :is="sidebarVisible ? PanelLeftClose : PanelLeftOpen" :size="21" />
        </button>
      <router-link :to="showAdminSidebar ? (navigationStore.adminItems[0]?.route || '/') : '/'" class="nav-brand">
        <div class="nav-logo">B</div>
        <span>BIDV Core Training</span>
      </router-link>
      </div>
      
      <div v-if="!userStore.hasAdminAccess" class="nav-links" aria-label="Điều hướng học viên">
        <router-link v-for="item in navigationStore.mainItems" :key="item.id" :to="item.route" class="nav-link">
          <component :is="navigationIcon(item.icon)" class="nav-icon" />
          <span>{{ item.label }}</span>
        </router-link>
      </div>

      <div class="nav-user-menu">
        <div id="learning-chatbot-launcher"></div>
        <NotificationCenter />
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
      <component :is="showAdminSidebar ? AdminLayout : 'div'" v-bind="showAdminSidebar ? { sidebarVisible } : {}" @navigate="closeMobileNavigation">
        <router-view v-slot="{ Component }">
          <transition name="page" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </component>
    </main>

    <LearningChatbot
      v-if="showLearningChatbot"
      :locked="chatbotLocked"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import { useRoute } from 'vue-router';
import { useUserStore } from './stores/userStore';
import { useCourseStore } from './stores/courseStore';
import { useNavigationStore } from './stores/navigationStore';
import NotificationCenter from './components/NotificationCenter.vue';
import LearningChatbot from './components/LearningChatbot.vue';
import AdminLayout from './views/AdminLayout.vue';
import { BookOpen, ClipboardCheck, HelpCircle, Home, LogOut, PanelLeftClose, PanelLeftOpen, Settings, Trophy, UserCircle, Users } from 'lucide-vue-next';

const route = useRoute();
const userStore = useUserStore();
const courseStore = useCourseStore();
const navigationStore = useNavigationStore();
const showAdminSidebar = computed(() => userStore.isAuthenticated && userStore.hasAdminAccess && route.name !== 'ChangePassword');
const sidebarVisible = ref(localStorage.getItem('adminSidebarVisible') !== 'false');
function closeMobileNavigation(){if(window.matchMedia('(max-width: 768px)').matches)sidebarVisible.value=false;}
watch(sidebarVisible, visible => {
  try { localStorage.setItem('adminSidebarVisible', String(visible)); } catch { /* Keep the current session usable if storage is unavailable. */ }
});
const iconRegistry = { BookOpen, ClipboardCheck, HelpCircle, Home, Settings, Trophy, UserCircle, Users };
const navigationIcon = (name: string) => iconRegistry[name as keyof typeof iconRegistry] || Home;
const showLearningChatbot = computed(() => userStore.isAuthenticated && route.name !== 'ChangePassword');
const chatbotLocked = computed(() => route.name === 'QuizView');

const userAvatarLetter = computed(() => {
  return (userStore.fullName || userStore.username || 'Q').charAt(0).toUpperCase();
});

const handleLogout = async () => {
  navigationStore.clear();
  await userStore.logout();
};

onMounted(async () => {
  if (userStore.isAuthenticated) {
    await Promise.all([
      userStore.fetchProgress(),
      courseStore.fetchCourses(),
      navigationStore.load()
    ]);
  }
});
</script>

<style>
/* Page transition animation */
.page-enter-active {
  transition: opacity var(--motion-duration) var(--motion-easing), transform var(--motion-duration) var(--motion-easing);
}
.page-leave-active {
  transition: opacity var(--motion-fast) ease, transform var(--motion-fast) ease;
  pointer-events: none;
}

.page-enter-from {
  opacity: 0;
  transform: translateY(6px);
}

.page-leave-to {
  opacity: 0;
  transform: translateY(-4px);
}

.nav-icon {
  width: 18px;
  height: 18px;
}

.nav-brand-group { display: flex; align-items: center; gap: .75rem; min-width: 0; }
.sidebar-toggle { display: inline-flex; align-items: center; justify-content: center; flex: 0 0 40px; width: 40px; height: 40px; border: 1px solid var(--border-color); border-radius: 8px; background: white; color: var(--primary-color); cursor: pointer; }
.sidebar-toggle:hover { background: var(--primary-light); }
.sidebar-toggle:focus-visible { outline: 2px solid var(--primary-color); outline-offset: 3px; }

.nav-user-menu {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 0.75rem;
}

#learning-chatbot-launcher {
  display: flex;
  align-items: center;
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

@media (max-width: 560px) {
  .navbar {
    flex-wrap: wrap;
  }

  .nav-links {
    order: 3;
    flex: 1 0 100%;
  }

  .nav-user-menu {
    gap: 0.35rem;
  }
}
</style>
