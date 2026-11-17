import type { PreferencesStorage } from '../../application/ports.ts'
import type { ThemeMode, TrackingPreferencesData, TrackingSortBy } from '../../domain/types.ts'

const THEME_MODE_KEY = 'chanlun.theme_mode'
const EMAIL_TOKEN_KEY = 'chanlun.email_bearer'
const TRACKING_PREFS_PREFIX = 'chanlun.tracking_prefs.'

function safeStorage(): Storage | null {
  try {
    return typeof window !== 'undefined' && window.localStorage ? window.localStorage : null
  } catch {
    return null
  }
}

function isValidSortBy(value: unknown): value is TrackingSortBy {
  return value === 'default' || value === 'cumulative' || value === 'day' || value === 'days'
}

export function createLocalPreferences(): PreferencesStorage {
  return {
    readThemeMode(): ThemeMode {
      const storage = safeStorage()
      const value = storage?.getItem(THEME_MODE_KEY)
      return value === 'light' || value === 'dark' || value === 'system' ? value : 'system'
    },
    writeThemeMode(mode: ThemeMode) {
      const storage = safeStorage()
      if (!storage) return
      if (mode === 'light' || mode === 'dark' || mode === 'system') {
        storage.setItem(THEME_MODE_KEY, mode)
      }
    },
    readEmailToken(): string {
      const storage = safeStorage()
      return storage?.getItem(EMAIL_TOKEN_KEY) || ''
    },
    writeEmailToken(token: string) {
      const storage = safeStorage()
      if (!storage) return
      if (token) {
        storage.setItem(EMAIL_TOKEN_KEY, token)
      } else {
        storage.removeItem(EMAIL_TOKEN_KEY)
      }
    },
    clearEmailToken() {
      const storage = safeStorage()
      storage?.removeItem(EMAIL_TOKEN_KEY)
    },
    readTrackingPrefs(userKey: string): TrackingPreferencesData {
      const storage = safeStorage()
      if (!storage || !userKey) {
        return { sortBy: 'default', pinnedSecids: [], customOrder: [] }
      }
      try {
        const raw = storage.getItem(`${TRACKING_PREFS_PREFIX}${userKey}`)
        if (!raw) return { sortBy: 'default', pinnedSecids: [], customOrder: [] }
        const parsed = JSON.parse(raw) as Partial<TrackingPreferencesData> | null
        return {
          sortBy: isValidSortBy(parsed?.sortBy) ? parsed.sortBy : 'default',
          pinnedSecids: Array.isArray(parsed?.pinnedSecids) ? parsed.pinnedSecids.map(String) : [],
          customOrder: Array.isArray(parsed?.customOrder) ? parsed.customOrder.map(String) : [],
        }
      } catch {
        return { sortBy: 'default', pinnedSecids: [], customOrder: [] }
      }
    },
    writeTrackingPrefs(userKey: string, prefs: TrackingPreferencesData) {
      const storage = safeStorage()
      if (!storage || !userKey) return
      try {
        storage.setItem(`${TRACKING_PREFS_PREFIX}${userKey}`, JSON.stringify(prefs))
      } catch {
        // 忽略无痕模式配额限制
      }
    },
  }
}
