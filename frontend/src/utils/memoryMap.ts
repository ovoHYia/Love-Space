import * as L from 'leaflet'
import 'leaflet/dist/leaflet.css'

export const MEMORY_MAP_CENTER: L.LatLngExpression = [35.86, 104.19]

export function createMemoryTileLayer() {
  return L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
    maxZoom: 19,
    attribution: '&copy; OpenStreetMap contributors',
  })
}

export function createMemoryMarkerIcon() {
  return L.divIcon({
    className: 'memory-map-marker-wrap',
    html: '<span class="memory-map-marker">♥</span>',
    iconSize: [34, 34],
    iconAnchor: [17, 30],
  })
}
