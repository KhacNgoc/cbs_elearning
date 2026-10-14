import {useUserStore} from '../stores/userStore';
import { createRouter, createWebHistory } from 'vue-router';
import type { RouteRecordRaw } from 'vue-router';
import Dashboard from '../views/Dashboard.vue';
import LessonDetail from '../views/LessonDetail.vue';
import QuizView from '../views/QuizView.vue';
import Leaderboard from '../views/Leaderboard.vue';
import Login from '../views/Login.vue';
import AdminPortal from '../views/AdminPortal.vue';
import AdminQuizDetail from '../views/AdminQuizDetail.vue';
import Profile from '../views/Profile.vue';
import { useNavigationStore } from '../stores/navigationStore';
import { clearAuthSession, getCachedAuthSession, setCachedAuthSession } from '../utils/authSession';
import { authApi } from '../api/authApi';

import TrainingAdmin from '../views/TrainingAdmin.vue';
import LessonEditor from '../views/LessonEditor.vue';
import CourseOutline from '../views/CourseOutline.vue';

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
  {path:'/courses/:courseId',name:'CourseOutline',component:CourseOutline,meta:{requiresAuth:true,pageKey:'course.outline'}},
  {
    path: '/lesson/:id',
    name: 'LessonDetail',
    component: LessonDetail,
    props: true,
    meta: { requiresAuth: true, pageKey: 'lesson' }
  },
  {
    path: '/quiz/:courseId',
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
    meta: { requiresAuth: true, requiresAdmin: true, pageKey: 'admin.overview' },
    children: [
      {path:'question-bank',name:'QuestionBank',component:()=>import('../views/QuestionBank.vue'),meta:{pageKey:'admin.question-bank'}},
      {path:'content-reports',name:'ContentReports',component:()=>import('../views/ContentReports.vue'),meta:{pageKey:'admin.content-reports'}},
      {path:'users',name:'AdminUsers',component:()=>import('../views/AccessAdmin.vue'),meta:{pageKey:'admin.users',accessSection:'users'}},
      {path:'units',name:'AdminUnits',component:()=>import('../views/AccessAdmin.vue'),meta:{pageKey:'admin.units',accessSection:'units'}},
      {path:'groups',name:'AdminGroups',component:()=>import('../views/AccessAdmin.vue'),meta:{pageKey:'admin.groups',accessSection:'groups'}},
      {path:'permissions',name:'AdminPermissions',component:()=>import('../views/PermissionAdmin.vue'),meta:{pageKey:'admin.permissions'}},
      {path:'roles',name:'AdminRoles',component:()=>import('../views/RoleAdmin.vue'),meta:{pageKey:'admin.roles'}},
      {path:'access',redirect:to=>to.query.tab==='users'?'/admin/users':to.query.tab==='units'?'/admin/units':to.query.tab==='groups'?'/admin/groups':'/admin/permissions'},
      {path:'catalog',name:'TrainingCatalog',component:TrainingAdmin,meta:{pageKey:'admin.catalog'}},
      {path:'courses',name:'TrainingCourses',component:TrainingAdmin,meta:{pageKey:'admin.courses'}},
      {path:'courses/:courseId',name:'TrainingCourseDetail',component:TrainingAdmin,meta:{pageKey:'admin.courses'}},
      {path:'courses/:courseId/chapters/:chapterId',name:'TrainingChapterDetail',redirect:to=>({name:'TrainingCourseDetail',params:{courseId:to.params.courseId},query:{...to.query,chapter:String(to.params.chapterId)}}),meta:{pageKey:'admin.courses'}},
      {path:'lessons/:lessonId/edit',name:'LessonEditor',component:LessonEditor,meta:{pageKey:'admin.courses'}},
      {
        path: '',
        name: 'AdminPortal',
        component: AdminPortal
      },
      {
        path: 'lessons/:courseId',
        name: 'AdminCourseLessons',
        redirect: to => `/admin/courses/${to.params.courseId}`,
        meta: { pageKey: 'admin.courses' }
      },
      {
        path: 'quizzes/new',
        name: 'AdminQuizCreate',
        component: AdminQuizDetail,
        meta: { pageKey: 'admin.exams' }
      },
      {
        path: 'quizzes/:scopeType(course|lesson)/:scopeId',
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
  let session = getCachedAuthSession();
  let isAuthenticated = isMock
    ? !!localStorage.getItem('accessToken')
    : !!session;
  let mustChangePassword = session?.mustChangePassword
    ?? localStorage.getItem('mustChangePassword') === 'true';

  if (!isMock && (requiresAuth || to.name === 'Login')) {
    try {
      session = await authApi.session();
      setCachedAuthSession(session);
      localStorage.setItem('userRole', session.role);
      localStorage.setItem('username', session.username);
      localStorage.setItem('fullName', session.fullName);
      localStorage.setItem('mustChangePassword', session.mustChangePassword ? 'true' : 'false');
      mustChangePassword = session.mustChangePassword;
      isAuthenticated = true;
    } catch {
      clearAuthSession();
    }
  }

  if(session) useUserStore().$patch({isAuthenticated:true,role:session.role,roles:session.roles || [session.role],permissions:session.permissions || [],roleNames:session.roleNames || {},systemAdmin:!!session.systemAdmin,username:session.username,fullName:session.fullName,mustChangePassword:session.mustChangePassword});

  if (requiresAuth && !isAuthenticated) {
    next('/login');
  } else if (isAuthenticated && mustChangePassword && to.name !== 'ChangePassword') {
    next('/change-password');
  } else if (requiresAdmin && !(session?.permissions || []).some(p=>p.endsWith(':read'))) {
    next('/');
  } else if (to.name === 'Login' && isAuthenticated) {
    next(mustChangePassword ? '/change-password' : '/');
  } else {
    if(to.path==='/admin' && typeof to.query.tab==='string' && ['users','units','groups','roles','permissions','courses','lessons'].includes(to.query.tab)) {next(`/admin/${to.query.tab==='lessons'?'courses':to.query.tab}`);return;}
    if (requiresAuth) {
      try {
        const navigationStore = useNavigationStore();
        await navigationStore.load(true);
        const tabKey = to.path === '/admin' && typeof to.query.tab === 'string'
          ? `admin.${to.query.tab}`
          : undefined;
        const pageKey = tabKey || [...to.matched].reverse()
          .map(record => record.meta.pageKey as string | undefined)
          .find(Boolean);
        if (pageKey && !navigationStore.allowedPageIds.has(pageKey)) {
          next(requiresAdmin && to.path==='/admin' ? navigationStore.adminItems[0]?.route || '/' : '/');
          return;
        }
      } catch (error) {
        console.error('Failed to load page permissions:', error);next('/');return;
      }
    }
    next();
  }
});

export default router;
