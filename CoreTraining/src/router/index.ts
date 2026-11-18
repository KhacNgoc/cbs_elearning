import {useUserStore} from '../stores/userStore';
import { createRouter, createWebHistory } from 'vue-router';
import type { RouteRecordRaw } from 'vue-router';
const Dashboard = () => import('../views/Dashboard.vue');
const LessonDetail = () => import('../views/LessonDetail.vue');
const QuizView = () => import('../views/QuizView.vue');
const Leaderboard = () => import('../views/Leaderboard.vue');
const Login = () => import('../views/Login.vue');
const AdminOverview = () => import('../views/AdminOverview.vue');
const AdminQuizDetail = () => import('../views/AdminQuizDetail.vue');
const Profile = () => import('../views/Profile.vue');
import { useNavigationStore } from '../stores/navigationStore';
import { reportPageKey } from '../utils/reportingViews';
import { clearAuthSession, getCachedAuthSession, isSignedOut, setCachedAuthSession } from '../utils/authSession';
import { authApi } from '../api/authApi';

const TrainingAdmin = () => import('../views/TrainingAdmin.vue');
const LessonEditor = () => import('../views/LessonEditor.vue');
const CourseOutline = () => import('../views/CourseOutline.vue');

const routes: Array<RouteRecordRaw> = [
  ...(import.meta.env.DEV ? [{ path: '/mockups/reports', name: 'ReportsMockup', component: () => import('../views/ReportsPreview.vue') }] : []),
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
    path: '/flashcards',
    name: 'FlashcardLibrary',
    component: () => import('../views/FlashcardLibrary.vue'),
    meta: { requiresAuth: true, pageKey: 'flashcards' }
  },
  {
    path: '/lesson-reports/:id',
    name: 'LessonReportDetail',
    component: () => import('../views/LessonReportDetail.vue'),
    meta: { requiresAuth: true }
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
      {path:'permissions',name:'AdminPermissions',component:()=>import('../views/PermissionAdmin.vue'),meta:{pageKey:'admin.permissions'}},
      {path:'roles',name:'AdminRoles',component:()=>import('../views/RoleAdmin.vue'),meta:{pageKey:'admin.roles'}},
      {path:'access',redirect:to=>to.query.tab==='users'?'/admin/users':to.query.tab==='units'?'/admin/units':'/admin/permissions'},
      {path:'unit-allocation',name:'UnitAllocationAdmin',component:()=>import('../views/UnitAllocationAdmin.vue'),meta:{pageKey:'admin.unit-allocation'}},
      {path:'curriculum',name:'TrainingCurriculum',component:TrainingAdmin,meta:{pageKey:'admin.curriculum'}},
      {path:'catalog',name:'TrainingCatalog',component:TrainingAdmin,meta:{pageKey:'admin.catalog'}},
      {path:'courses',name:'TrainingCourses',component:TrainingAdmin,meta:{pageKey:'admin.courses'}},
      {path:'courses/:courseId',name:'TrainingCourseDetail',component:TrainingAdmin,meta:{pageKey:'admin.courses'}},
      {path:'courses/:courseId/chapters/:chapterId',name:'TrainingChapterDetail',redirect:to=>({name:'TrainingCourseDetail',params:{courseId:to.params.courseId},query:{...to.query,chapter:String(to.params.chapterId)}}),meta:{pageKey:'admin.courses'}},
      {path:'lessons/:lessonId/edit',name:'LessonEditor',component:LessonEditor,meta:{pageKey:'admin.courses'}},
      {
        path: '',
        name: 'AdminPortal',
        component: AdminOverview
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

type AuthSession = NonNullable<ReturnType<typeof getCachedAuthSession>>;
type GuardedRoute = { path: string; query: Record<string, unknown>; matched: { meta: Record<string, unknown> }[] };

// The backend authorizes every request, so after the first check the guard trusts the cached
// session and menu and refreshes them in the background. Awaiting both requests on every click
// used to freeze navigation whenever the backend was slow.
const SESSION_REVALIDATE_MS = 30_000;
let sessionCheckedAt = 0;
let revalidation: Promise<void> | null = null;

function applySession(session: AuthSession) {
  setCachedAuthSession(session);
  sessionCheckedAt = Date.now();
  localStorage.setItem('userRole', session.role);
  localStorage.setItem('username', session.username);
  localStorage.setItem('fullName', session.fullName);
  localStorage.setItem('mustChangePassword', session.mustChangePassword ? 'true' : 'false');
}

function patchUserStore(session: AuthSession) {
  useUserStore().$patch({isAuthenticated:true,role:session.role,roles:session.roles || [session.role],permissions:session.permissions || [],roleNames:session.roleNames || {},systemAdmin:!!session.systemAdmin,username:session.username,fullName:session.fullName,mustChangePassword:session.mustChangePassword});
}

function pageKeyOf(route: GuardedRoute) {
  const tabKey = route.path === '/admin' && typeof route.query.tab === 'string'
    ? `admin.${route.query.tab}`
    : route.path === '/admin' && typeof route.query.report === 'string'
      ? reportPageKey(route.query.report)
      : undefined;
  return tabKey || [...route.matched].reverse()
    .map(record => record.meta.pageKey as string | undefined)
    .find(Boolean);
}

function revalidateInBackground() {
  if (revalidation || Date.now() - sessionCheckedAt < SESSION_REVALIDATE_MS) return;
  revalidation = (async () => {
    try {
      const session = await authApi.session();
      applySession(session);
      patchUserStore(session);
      const navigationStore = useNavigationStore();
      await navigationStore.load(true);
      // Permissions may have changed since the page opened: leave a page that is no longer allowed.
      const current = router.currentRoute.value;
      if (!current.matched.some(record => record.meta.requiresAuth)) return;
      if (session.mustChangePassword && current.name !== 'ChangePassword') {
        await router.replace('/change-password');
        return;
      }
      const pageKey = pageKeyOf(current);
      if (pageKey && !navigationStore.allowedPageIds.has(pageKey)) await router.replace(navigationStore.homeRoute);
    } catch {
      // A 401 already redirects to login in the axios interceptor; other failures keep the cache.
    } finally {
      revalidation = null;
    }
  })();
}

// Navigation Guard to protect routes
router.beforeEach(async (to, from, next) => {
  const requiresAuth = to.matched.some(record => record.meta.requiresAuth);
  const requiresAdmin = to.matched.some(record => record.meta.requiresAdmin);
  let session = getCachedAuthSession();

  if (requiresAuth || to.name === 'Login') {
    if (session) {
      revalidateInBackground();
    } else if (!isSignedOut()) {
      try {
        session = await authApi.session();
        applySession(session);
      } catch {
        clearAuthSession();
        session = null;
      }
    }
  }
  const isAuthenticated = !!session;
  const mustChangePassword = session?.mustChangePassword
    ?? localStorage.getItem('mustChangePassword') === 'true';

  if(session) patchUserStore(session);

  if (requiresAuth && !isAuthenticated) {
    next('/login');
  } else if (isAuthenticated && mustChangePassword && to.name !== 'ChangePassword') {
    next('/change-password');
  } else if (requiresAdmin && !(session?.permissions || []).some(p=>p.endsWith(':read'))) {
    next('/');
  } else if (to.name === 'Login' && isAuthenticated) {
    next(mustChangePassword ? '/change-password' : '/');
  } else {
    if(to.path==='/admin' && typeof to.query.tab==='string' && ['users','units','roles','permissions','courses','lessons'].includes(to.query.tab)) {next(`/admin/${to.query.tab==='lessons'?'courses':to.query.tab}`);return;}
    if (requiresAuth) {
      try {
        const navigationStore = useNavigationStore();
        if (!navigationStore.loaded) await navigationStore.load();
        if (to.name === 'Dashboard' && (!from.matched.length || from.name === 'Login' || from.name === 'ChangePassword') && navigationStore.homeRoute !== '/') {
          next({path:navigationStore.homeRoute,replace:true});
          return;
        }
        const pageKey = pageKeyOf(to);
        if (pageKey && !navigationStore.allowedPageIds.has(pageKey)) {
          next(requiresAdmin && to.path==='/admin' ? navigationStore.homeRoute : '/');
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
