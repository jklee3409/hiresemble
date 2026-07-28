export function focusFirstInvalidControl(root: ParentNode = document): void {
  const explicitInvalid = root.querySelector<HTMLElement>(
    '[aria-invalid="true"]:not(fieldset):not([disabled])',
  )
  if (explicitInvalid !== null) {
    explicitInvalid.focus()
    return
  }

  const error = root.querySelector<HTMLElement>('.field-error, .inline-error')
  const field = error?.closest('label, fieldset')
  field
    ?.querySelector<HTMLElement>(
      'input:not([disabled]), select:not([disabled]), textarea:not([disabled])',
    )
    ?.focus()
}
