import * as L from 'leaflet'
import 'leaflet/dist/leaflet.css'

export const MEMORY_MAP_CENTER: L.LatLngExpression = [35.86, 104.19]
const TILE_URL = import.meta.env.VITE_MAP_TILE_URL || 'https://tile.openstreetmap.org/{z}/{x}/{y}.png'
const TILE_ATTRIBUTION = import.meta.env.VITE_MAP_TILE_ATTRIBUTION || '&copy; OpenStreetMap contributors'

export function createMemoryTileLayer(onUnavailable?: () => void) {
  const layer = L.tileLayer(TILE_URL, {
    maxZoom: 19,
    attribution: TILE_ATTRIBUTION,
    referrerPolicy: 'origin',
    updateWhenIdle: true,
  })
  let failures = 0
  let notified = false
  layer.on('tileerror', () => {
    failures += 1
    if (failures >= 3 && !notified) {
      notified = true
      onUnavailable?.()
    }
  })
  layer.on('load', () => { failures = 0 })
  return layer
}

export function createMemoryMarkerIcon() {
  return L.divIcon({
    className: 'memory-map-marker-wrap',
    html: '<span class="memory-map-marker">♥</span>',
    iconSize: [34, 34],
    iconAnchor: [17, 30],
  })
}

export function synchronizeMapSize(map: L.Map, element: HTMLElement) {
  let disposed = false
  const invalidate = () => {
    if (disposed) return
    window.requestAnimationFrame(() => {
      if (!disposed) map.invalidateSize({ pan: false, debounceMoveend: true })
    })
  }
  const timers = [0, 180, 500].map((delay) => window.setTimeout(invalidate, delay))
  const observer = typeof ResizeObserver === 'undefined' ? null : new ResizeObserver(invalidate)
  observer?.observe(element)
  window.addEventListener('resize', invalidate)
  window.addEventListener('orientationchange', invalidate)
  const onVisibilityChange = () => {
    if (document.visibilityState === 'visible') invalidate()
  }
  document.addEventListener('visibilitychange', onVisibilityChange)

  return () => {
    disposed = true
    timers.forEach(window.clearTimeout)
    observer?.disconnect()
    window.removeEventListener('resize', invalidate)
    window.removeEventListener('orientationchange', invalidate)
    document.removeEventListener('visibilitychange', onVisibilityChange)
  }
}
