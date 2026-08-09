export function safeGitHubInstallationUrl(value: string): string | null {
  try {
    const parsed = new URL(value)
    const state = parsed.searchParams.get('state')
    const parameters = [...parsed.searchParams.entries()]
    const onlyState = parameters.length === 1 && parameters[0]?.[0] === 'state'
    return parsed.origin === 'https://github.com' &&
      parsed.username === '' &&
      parsed.password === '' &&
      /^\/apps\/[A-Za-z0-9-]{1,100}\/installations\/new$/.test(parsed.pathname) &&
      state !== null &&
      /^[A-Za-z0-9_-]{43}$/.test(state) &&
      onlyState &&
      parsed.hash === ''
      ? parsed.toString()
      : null
  } catch {
    return null
  }
}

export function safeGitHubInstallationManageUrl(value: string): string | null {
  try {
    const parsed = new URL(value)
    return parsed.origin === 'https://github.com' &&
      parsed.username === '' &&
      parsed.password === '' &&
      /^\/settings\/installations\/[1-9][0-9]*$/.test(parsed.pathname) &&
      parsed.search === '' &&
      parsed.hash === ''
      ? parsed.toString()
      : null
  } catch {
    return null
  }
}
