import { reactive } from 'vue'

export type ToastTone = 'success' | 'error' | 'info'

export interface ConfirmOptions {
  title: string
  message: string
  confirmLabel?: string
  cancelLabel?: string
  tone?: 'danger' | 'primary'
}

interface ToastMessage {
  id: number
  message: string
  tone: ToastTone
}

interface ConfirmRequest extends ConfirmOptions {
  id: number
  resolve: (value: boolean) => void
}

const state = reactive<{
  toasts: ToastMessage[]
  confirmation: ConfirmRequest | null
}>({ toasts: [], confirmation: null })

let nextId = 1

function dismissToast(id: number): void {
  const index = state.toasts.findIndex((toast) => toast.id === id)
  if (index >= 0) state.toasts.splice(index, 1)
}

function toast(message: string, tone: ToastTone = 'info'): void {
  const id = nextId++
  state.toasts.push({ id, message, tone })
  window.setTimeout(() => dismissToast(id), tone === 'error' ? 6000 : 4000)
}

function confirm(options: ConfirmOptions): Promise<boolean> {
  state.confirmation?.resolve(false)
  return new Promise((resolve) => {
    state.confirmation = { ...options, id: nextId++, resolve }
  })
}

function resolveConfirmation(value: boolean): void {
  const request = state.confirmation
  if (request === null) return
  state.confirmation = null
  request.resolve(value)
}

export function useNotifications() {
  return {
    state,
    toast,
    confirm,
    dismissToast,
    resolveConfirmation,
  }
}
