import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import type { Question } from '@/types'

export const useInterviewStore = defineStore('interview', () => {
  const sessionId = ref<number | null>(null)
  const remainingTime = ref(0)
  const questions = ref<Question[]>([])
  const resumeTitle = ref('')
  const jobTitle = ref('')
  const currentQuestionId = ref<number | null>(null)

  let timerHandle: ReturnType<typeof setInterval> | null = null

  const formattedRemainingTime = computed(() => {
    const minutes = Math.floor(remainingTime.value / 60)
    const seconds = remainingTime.value % 60
    return `${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`
  })

  const currentQuestion = computed(() =>
    questions.value.find((q) => q.id === currentQuestionId.value),
  )

  function initSession(payload: {
    sessionId: number
    questions: Question[]
    resumeTitle: string
    jobTitle: string
    durationSeconds: number
    currentQuestionId?: number
  }): void {
    sessionId.value = payload.sessionId
    questions.value = [...payload.questions]
    resumeTitle.value = payload.resumeTitle
    jobTitle.value = payload.jobTitle
    currentQuestionId.value =
      payload.currentQuestionId ?? payload.questions[0]?.id ?? null
    remainingTime.value = payload.durationSeconds
    startTimer()
  }

  function startTimer(): void {
    stopTimer()
    timerHandle = setInterval(() => {
      if (remainingTime.value > 0) {
        remainingTime.value -= 1
      } else {
        stopTimer()
      }
    }, 1000)
  }

  function stopTimer(): void {
    if (timerHandle) {
      clearInterval(timerHandle)
      timerHandle = null
    }
  }

  function setCurrentQuestion(questionId: number): void {
    currentQuestionId.value = questionId
  }

  function addQuestion(question: Question): void {
    questions.value.push(question)
    currentQuestionId.value = question.id
  }

  function reset(): void {
    stopTimer()
    sessionId.value = null
    remainingTime.value = 0
    questions.value = []
    resumeTitle.value = ''
    jobTitle.value = ''
    currentQuestionId.value = null
  }

  return {
    sessionId,
    remainingTime,
    questions,
    resumeTitle,
    jobTitle,
    currentQuestionId,
    formattedRemainingTime,
    currentQuestion,
    initSession,
    startTimer,
    stopTimer,
    setCurrentQuestion,
    addQuestion,
    reset,
  }
})
