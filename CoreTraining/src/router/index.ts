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
    meta: { requiresAuth: true }
  },
  {
    path: '/lesson/:id',
    name: 'LessonDetail',
    component: LessonDetail,
    props: true,
    meta: { requiresAuth: true }
  },
  {
    path: '/quiz/:topicId',
    name: 'QuizView',
    component: QuizView,
    props: true,
    meta: { requiresAuth: true }
  },
  {
    path: '/leaderboard',
    name: 'Leaderboard',
    component: Leaderboard,
    meta: { requiresAuth: true }
  },
  {
    path: '/profile',
    name: 'Profile',
    component: Profile,
    meta: { requiresAuth: true }
  },
  {
    path: '/admin',
    component: AdminLayout,
    meta: { requiresAuth: true, requiresAdmin: true },
    children: [
      {
        path: '',
        name: 'AdminPortal',
        component: AdminPortal
      },
      {
        path: 'quizzes/new',
        name: 'AdminQuizCreate',
        component: AdminQuizDetail
      },
      {
        path: 'quizzes/:scopeType(topic|lesson)/:scopeId',
        name: 'AdminQuizDetail',
        component: AdminQuizDetail
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
router.beforeEach((to, _, next) => {
  const token = localStorage.getItem('accessToken');
  const role = localStorage.getItem('userRole') || '';
  const requiresAuth = to.matched.some(record => record.meta.requiresAuth);
  const requiresAdmin = to.matched.some(record => record.meta.requiresAdmin);

  if (requiresAuth && !token) {
    next('/login');
  } else if (requiresAdmin && role !== 'ROLE_ADMIN') {
    next('/');
  } else if (to.name === 'Login' && token) {
    next('/');
  } else {
    next();
  }
});

export default router;
