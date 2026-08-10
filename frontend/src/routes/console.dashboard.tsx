import { createFileRoute, redirect } from '@tanstack/react-router'
import { authStore } from '@/stores/auth'
import { DashboardPage } from './dashboard'

export const Route = createFileRoute('/console/dashboard')({
  beforeLoad: ({ location }) => {
    if (!authStore.isAuthenticated) {
      throw redirect({ to: '/login', search: { redirect: location.href } })
    }
  },
  component: DashboardPage,
})
