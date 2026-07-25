export class DeviceLocationError extends Error {
  constructor(message: string) {
    super(message)
    this.name = 'DeviceLocationError'
  }
}

export function geolocationRequirementMessage() {
  if (!isTrustedLocationContext()) {
    return '当前页面使用 HTTP，手机浏览器即使已授权也会拒绝定位。请改用 HTTPS 访问；仍可点击地图手动选点。'
  }
  if (!navigator.geolocation) {
    return '当前浏览器不支持网页定位，仍可点击地图手动选点。'
  }
  return ''
}

export async function locateDevice() {
  const requirement = geolocationRequirementMessage()
  if (requirement) throw new DeviceLocationError(requirement)

  try {
    return await requestPosition({ enableHighAccuracy: true, timeout: 12000, maximumAge: 60000 })
  } catch (cause) {
    const error = cause as GeolocationPositionError
    if (error.code === 1) throw locationError(error)
    try {
      return await requestPosition({ enableHighAccuracy: false, timeout: 20000, maximumAge: 300000 })
    } catch (fallbackCause) {
      throw locationError(fallbackCause as GeolocationPositionError)
    }
  }
}

function requestPosition(options: PositionOptions) {
  return new Promise<GeolocationPosition>((resolve, reject) => {
    navigator.geolocation.getCurrentPosition(resolve, reject, options)
  })
}

function locationError(error: GeolocationPositionError) {
  if (error.code === 1) {
    return new DeviceLocationError('浏览器拒绝定位。请确认系统定位服务已开启、该浏览器的位置权限为“允许”；微信等内置浏览器可改用系统浏览器重试。')
  }
  if (error.code === 2) {
    return new DeviceLocationError('暂时无法取得位置。请到室外或窗边、开启 Wi-Fi/移动网络后重试，也可以直接点击地图选点。')
  }
  if (error.code === 3) {
    return new DeviceLocationError('定位等待超时。已尝试高精度和网络定位，请稍后重试或直接点击地图选点。')
  }
  return new DeviceLocationError('定位失败，请稍后重试或直接点击地图选点。')
}

function isTrustedLocationContext() {
  if (window.isSecureContext) return true
  return ['localhost', '127.0.0.1', '::1'].includes(window.location.hostname)
}
