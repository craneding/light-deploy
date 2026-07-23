const PREFIX = 'light_deploy_'

function getKey(key: string): string {
  return `${PREFIX}${key}`
}

export function getItem<T = string>(key: string): T | null {
  const value = localStorage.getItem(getKey(key))
  if (value === null) return null
  try {
    return JSON.parse(value) as T
  } catch {
    return value as unknown as T
  }
}

export function setItem(key: string, value: unknown): void {
  const stored = typeof value === 'string' ? value : JSON.stringify(value)
  localStorage.setItem(getKey(key), stored)
}

export function removeItem(key: string): void {
  localStorage.removeItem(getKey(key))
}

export function clear(): void {
  const keysToRemove: string[] = []
  for (let i = 0; i < localStorage.length; i++) {
    const key = localStorage.key(i)
    if (key && key.startsWith(PREFIX)) {
      keysToRemove.push(key)
    }
  }
  keysToRemove.forEach((key) => localStorage.removeItem(key))
}
