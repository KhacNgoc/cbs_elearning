import { createRouter, createWebHistory } from 'vue-router';
import type { RouteRecordRaw } from 'vue-router';
import Dashboard from '../views/Dashboard.vue';
import LessonDetail from '../views/LessonDetail.vue';
import QuizView from '../views/QuizView.vue';
import Leaderboard from '../views/Leaderboard.vue';
import Login from '../views/Login.vue';
import AdminPortal from '../views/AdminPortal.vue';
import AdminQuizDetail from '../views/AdminQuizDetail.vue';
import AdminLayout from '../views/AdminLayout.vue';
import Profile from '../views/Profile.vue';
import { useNavigationStore } from '../stores/navigationStore';
import { clearAuthSession } from '../utils/authSession';
import { authApi } from '../api/authApi';

const routes: Array<RouteRecordRaw> = [
  {
    path: '/login',
    name: 'Login',
    component: Login
  },
  {
    path: '/',
    name: 'Dashboard',
    component: Dashboard,
    meta: { requiresAuth: true, pageKey: 'dashboard' }
  },
  {
    path: '/lesson/:id',
    name: 'LessonDetail',
    component: LessonDetail,
    props: true,
    meta: { requiresAuth: true, pageKey: 'lesson' }
  },
  {
    path: '/quiz/:topicId',
    name: 'QuizView',
    component: QuizView,
    props: true,
    meta: { requiresAuth: true, pageKey: 'quiz' }
  },
  {
    path: '/leaderboard',
    name: 'Leaderboard',
    component: Leaderboard,
    meta: { requiresAuth: true, pageKey: 'leaderboard' }
  },
  {
    path: '/profile',
    name: 'Profile',
    component: Profile,
    meta: { requiresAuth: true, pageKey: 'profile' }
  },
  {
    path: '/change-password',
    name: 'ChangePassword',
    component: () => import('../views/ChangePassword.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/admin',
    component: AdminLayout,
    meta: { requiresAuth: true, requiresAdmin: true, pageKey: 'admin.overview' },
    children: [
      {
        path: '',
        name: 'AdminPortal',
        component: AdminPortal
      },
      {
        path: 'lessons/:topicId',
        name: 'AdminTopicLessons',
        component: AdminPortal,
        meta: { pageKey: 'admin.lessons' }
      },
      {
        path: 'quizzes/new',
        name: 'AdminQuizCreate',
        component: AdminQuizDetail,
        meta: { pageKey: 'admin.exams' }
      },
      {
        path: 'quizzes/:scopeType(topic|lesson)/:scopeId',
        name: 'AdminQuizDetail',
        component: AdminQuizDetail,
        meta: { pageKey: 'admin.exams' }
      }
    ]
  },
  {
    path: '/:pathMatch(.*)*',
    redirect: '/'
  }
];

const router = createRouter({
  history: createWebHistory(),
  routes
});

// Navigation Guard to protect routes
router.beforeEach(async (to, _, next) => {
  const requiresAuth = to.matched.some(record => record.meta.requiresAuth);
  const requiresAdmin = to.matched.some(record => record.meta.requiresAdmin);
  const isMock = import.meta.env.VITE_USE_MOCK_API === 'true';
  let isAuthenticated = isMock
    ? !!localStorage.getItem('accessToken')
    : false;
  let role = localStorage.getItem('userRole') || '';
  let mustChangePassword = localStorage.getItem('mustChangePassword') === 'true';

  if (!isMock && (requiresAuth || to.name === 'Login')) {
    try {
      const session = await authApi.session();
      localStorage.setItem('userRole', session.role);
      localStorage.setItem('username', session.username);
      localStorage.setItem('fullName', session.fullName);
      localStorage.setItem('mustChangePassword', session.mustChangePassword ? 'true' : 'false');
      role = session.role;
      mustChangePassword = session.mustChangePassword;
      isAuthenticated = true;
    } catch {
      clearAuthSession();
    }
  }

  if (requiresAuth && !isAuthenticated) {
    next('/login');
  } else if (isAuthenticated && mustChangePassword && to.name !== 'ChangePassword') {
    next('/change-password');
  } else if (requiresAdmin && role !== 'ROLE_ADMIN') {
    next('/');
  } else if (to.name === 'Login' && isAuthenticated) {
    next(mustChangePassword ? '/change-password' : '/');
  } else {
    if (requiresAuth) {
      try {
        const navigationStore = useNavigationStore();
        await navigationStore.load();
        const tabKey = to.path === '/admin' && typeof to.query.tab === 'string'
          ? `admin.${to.query.tab}`
          : undefined;
        const pageKey = tabKey || [...to.matched].reverse()
          .map(record => record.meta.pageKey as string | undefined)
          .find(Boolean);
        if (pageKey && !navigationStore.allowedPageIds.has(pageKey)) {
          next('/');
          return;
        }
      } catch (error) {
        console.error('Failed to load page permissions:', error);
      }
    }
    next();
  }
});

export default router;
