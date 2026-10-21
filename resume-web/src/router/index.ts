import { createRouter, createWebHistory } from 'vue-router'
import Home from '@/views/Home.vue'
import LoginView from '@/views/LoginView.vue'
import RegisterView from '@/views/RegisterView.vue'
import ProfileView from '@/views/ProfileView.vue'
import InterviewRoom from '@/views/InterviewRoom.vue'
import Report from '@/views/Report.vue'
import { getToken } from '@/utils/auth'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      name: 'home',
      component: Home,
    },
    {
      path: '/login',
      name: 'login',
      component: LoginView,
      meta: { guestOnly: true },
    },
    {
      path: '/register',
      name: 'register',
      component: RegisterView,
      meta: { guestOnly: true },
    },
    {
      path: '/profile',
      name: 'profile',
      component: ProfileView,
      meta: { requiresAuth: true },
    },
    {
      path: '/interview',
      name: 'interview',
      component: InterviewRoom,
      meta: { requiresAuth: true },
    },
    {
      path: '/report/:id',
      name: 'report',
      component: Report,
      meta: { requiresAuth: true },
    },
  ],
})

router.beforeEach((to, _from, next) => {
  const hasToken = !!getToken()

  if (to.meta.requiresAuth && !hasToken) {
    next({ path: '/login', query: { redirect: to.fullPath } })
    return
  }

  if (to.meta.guestOnly && hasToken) {
    next({ path: '/' })
    return
  }

  next()
})

export default router
