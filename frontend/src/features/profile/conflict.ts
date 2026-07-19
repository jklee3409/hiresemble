export function reapplySelectedFields<T extends Record<string, unknown>>(
  latest: T,
  draft: T,
  fields: readonly string[],
): T {
  const result: Record<string, unknown> = { ...latest }
  for (const field of fields) {
    if (Object.prototype.hasOwnProperty.call(draft, field)) {
      result[field] = draft[field]
    }
  }
  return result as T
}

export function isVersionConflict(error: { status: number; code: string }): boolean {
  return error.status === 409 && error.code === 'RESOURCE_VERSION_CONFLICT'
}
