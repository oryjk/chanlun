import type { ThemeMode, TrackingPreferencesData } from '../domain/types.ts'
import type {
  PreferencesStorage,
  ThemePreferencesUseCases,
  TrackingPreferencesUseCases,
} from './ports.ts'

export function createThemePreferences(storage: PreferencesStorage): ThemePreferencesUseCases {
  return {
    read: (): ThemeMode => storage.readThemeMode(),
    write: (mode: ThemeMode) => storage.writeThemeMode(mode),
  }
}

export function createTrackingPreferences(storage: PreferencesStorage): TrackingPreferencesUseCases {
  return {
    read: (userKey: string): TrackingPreferencesData => storage.readTrackingPrefs(userKey),
    write: (userKey: string, prefs: TrackingPreferencesData) => storage.writeTrackingPrefs(userKey, prefs),
  }
}
