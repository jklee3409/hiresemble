import type { Locator, Page } from '@playwright/test'

/*
 * `AppSelect`는 native `<select>`가 아니므로 `selectOption`을 쓸 수 없다.
 * 사용자와 같은 순서(trigger 열기 → 항목 고르기)로 값을 바꾼다.
 */
export async function chooseAppOption(
  page: Page,
  accessibleName: string,
  optionLabel: string,
  scopedTrigger?: Locator,
): Promise<void> {
  const trigger = scopedTrigger ?? page.getByRole('combobox', { name: accessibleName })
  await trigger.click()
  const listboxId = await trigger.getAttribute('aria-controls')
  await page.locator(`#${listboxId}`).getByRole('option', { name: optionLabel }).first().click()
}
