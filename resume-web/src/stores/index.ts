import { createPinia } from 'pinia'

export const pinia = createPinia()

export { useInterviewStore } from './interview'
export { useReportStore } from './report'
export { useUserStore } from './user'
