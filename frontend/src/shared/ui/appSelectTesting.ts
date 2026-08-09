import type { VueWrapper } from '@vue/test-utils'

/*
 * `AppSelect`는 native `<select>`가 아니라 combobox + listbox이므로
 * `setValue`로 값을 바꿀 수 없다. test가 사용자와 같은 순서(열기 → 고르기)를 밟도록
 * 이 helper만 쓰고 내부 class 이름에는 의존하지 않는다.
 */

/* 어떤 화면 component를 mount했는지와 무관하게 쓰이므로 instance type은 좁히지 않는다. */
type AnyWrapper = Pick<VueWrapper, 'find' | 'findAll' | 'get'>

function comboboxes(wrapper: AnyWrapper) {
  return wrapper.findAll('[role="combobox"]')
}

/** `aria-label` 또는 `aria-labelledby`가 가리키는 문구로 접근 가능한 이름을 만든다. */
function accessibleNameOf(wrapper: AnyWrapper, combobox: ReturnType<typeof comboboxes>[number]) {
  const label = combobox.attributes('aria-label')
  if (label !== undefined) return label.trim()
  const labelledBy = combobox.attributes('aria-labelledby')
  if (labelledBy === undefined) return ''
  return labelledBy
    .split(/\s+/)
    .map((id) => wrapper.find(`#${id}`).exists() && wrapper.get(`#${id}`).text().trim())
    .filter((text): text is string => typeof text === 'string')
    .join(' ')
    .trim()
}

export function findAppSelect(wrapper: AnyWrapper, accessibleName: string) {
  const all = comboboxes(wrapper)
  const match = all.find((combobox) => accessibleNameOf(wrapper, combobox) === accessibleName)
  if (match === undefined) {
    const available = all
      .map((combobox) => accessibleNameOf(wrapper, combobox) || '(이름 없음)')
      .join(', ')
    throw new Error(`이름이 "${accessibleName}"인 선택 control이 없습니다. 현재: ${available}`)
  }
  return match
}

/** aria-label로 선택 control을 찾아 보이는 문구가 `optionLabel`인 항목을 고른다. */
export async function selectAppOption(
  wrapper: AnyWrapper,
  accessibleName: string,
  optionLabel: string,
): Promise<void> {
  await chooseFrom(wrapper, findAppSelect(wrapper, accessibleName), optionLabel, accessibleName)
}

/** 위치로 선택 control을 지정해야 하는 화면에서 index 기반으로 항목을 고른다. */
export async function selectAppOptionAt(
  wrapper: AnyWrapper,
  index: number,
  optionLabel: string,
): Promise<void> {
  const trigger = comboboxes(wrapper)[index]
  if (trigger === undefined) throw new Error(`${index}번째 선택 control이 없습니다.`)
  await chooseFrom(wrapper, trigger, optionLabel, `${index}번째 선택 control`)
}

async function chooseFrom(
  wrapper: AnyWrapper,
  trigger: ReturnType<typeof comboboxes>[number],
  optionLabel: string,
  described: string,
): Promise<void> {
  // 이미 열려 있으면 다시 누르지 않는다. click은 열기가 아니라 toggle이다.
  if (trigger.attributes('aria-expanded') !== 'true') await trigger.trigger('click')
  const listboxId = trigger.attributes('aria-controls')
  const options = wrapper.findAll(`#${listboxId} [role="option"]`)
  const option = options.find((candidate) => candidate.text().includes(optionLabel))
  if (option === undefined) {
    const available = options.map((candidate) => candidate.text()).join(', ')
    throw new Error(`"${described}"에 "${optionLabel}" 항목이 없습니다. 현재: ${available}`)
  }
  await option.trigger('click')
}

/** 선택 control이 지금 보여 주는 값의 문구. */
export function appSelectLabel(wrapper: AnyWrapper, accessibleName: string): string {
  return findAppSelect(wrapper, accessibleName).text()
}

/** 선택 control을 열고 항목 문구 목록을 돌려준다. 목록은 열린 상태로 남는다. */
export async function openAppSelectOptions(
  wrapper: AnyWrapper,
  accessibleName: string,
): Promise<string[]> {
  const trigger = findAppSelect(wrapper, accessibleName)
  if (trigger.attributes('aria-expanded') !== 'true') await trigger.trigger('click')
  return wrapper
    .findAll(`#${trigger.attributes('aria-controls')} [role="option"]`)
    .map((option) => option.text())
}
