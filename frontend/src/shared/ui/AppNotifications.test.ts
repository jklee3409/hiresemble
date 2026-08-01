import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it } from 'vitest'

import AppNotifications from './AppNotifications.vue'
import { useNotifications } from './notifications'

describe('AppNotifications', () => {
  beforeEach(() => {
    const notifications = useNotifications()
    notifications.resolveConfirmation(false)
    notifications.state.toasts.splice(0)
  })

  it('renders success toasts and exposes an accessible confirmation with focus return', async () => {
    const opener = document.createElement('button')
    document.body.append(opener)
    opener.focus()
    const wrapper = mount(AppNotifications, { attachTo: document.body })
    const notifications = useNotifications()

    notifications.toast('저장했어요.', 'success')
    const confirmation = notifications.confirm({
      title: '자료를 삭제할까요?',
      message: '원본과 분석 결과가 삭제돼요.',
      confirmLabel: '자료 삭제',
    })
    await flushPromises()

    expect(document.body.textContent).toContain('저장했어요.')
    const dialog = document.querySelector<HTMLElement>('[role="alertdialog"]')
    expect(dialog?.getAttribute('aria-modal')).toBe('true')
    expect(document.activeElement?.textContent).toContain('취소')
    dialog?.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }))
    await expect(confirmation).resolves.toBe(false)
    await flushPromises()
    expect(document.activeElement).toBe(opener)

    wrapper.unmount()
    opener.remove()
  })
})
