import { createId } from './assistantStorage'

const DEVICE_KEY = 'kod:public-assistant:device-id:v1'

export function getAssistantDeviceId() {
  try {
    const existing = localStorage.getItem(DEVICE_KEY)
    if (existing && existing.length <= 64) return existing
    const next = createId('device').slice(0, 64)
    localStorage.setItem(DEVICE_KEY, next)
    return next
  } catch {
    return createId('device').slice(0, 64)
  }
}
