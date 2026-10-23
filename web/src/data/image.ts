// Pictures are stored as small data URLs in Firestore (no Cloud Storage), so
// they are resized and compressed in the browser first.

function loadImage(file: Blob): Promise<HTMLImageElement> {
  return new Promise((resolve, reject) => {
    const url = URL.createObjectURL(file)
    const img = new Image()
    img.onload = () => {
      URL.revokeObjectURL(url)
      resolve(img)
    }
    img.onerror = () => {
      URL.revokeObjectURL(url)
      reject(new Error('That file is not a picture the browser can read.'))
    }
    img.src = url
  })
}

/** Bytes a data URL decodes to. */
export const dataUrlBytes = (url: string) => Math.floor((url.length - url.indexOf(',') - 1) * 3 / 4)

/**
 * A passport-style photo: centre-cropped to 4:5, about 240 × 300 px, JPEG,
 * at most [maxBytes] (60 KB by default).
 */
export async function photoFromFile(file: Blob, maxBytes = 60_000): Promise<string> {
  const img = await loadImage(file)
  const targetRatio = 4 / 5
  let sw = img.naturalWidth
  let sh = img.naturalHeight
  if (sw / sh > targetRatio) sw = Math.round(sh * targetRatio)
  else sh = Math.round(sw / targetRatio)
  const sx = Math.round((img.naturalWidth - sw) / 2)
  const sy = Math.round((img.naturalHeight - sh) / 2)
  return encode(img, [sx, sy, sw, sh], 240, 300, maxBytes, 'image/jpeg')
}

/** A logo kept whole (no crop), at most 400 px on its long side and [maxBytes] (100 KB). */
export async function logoFromFile(file: Blob, maxBytes = 100_000): Promise<string> {
  const img = await loadImage(file)
  const scale = Math.min(1, 400 / Math.max(img.naturalWidth, img.naturalHeight))
  const w = Math.max(1, Math.round(img.naturalWidth * scale))
  const h = Math.max(1, Math.round(img.naturalHeight * scale))
  // PNG keeps transparency; fall back to JPEG when that is too big.
  const png = await encode(img, [0, 0, img.naturalWidth, img.naturalHeight], w, h, Number.MAX_SAFE_INTEGER, 'image/png')
  if (dataUrlBytes(png) <= maxBytes) return png
  return encode(img, [0, 0, img.naturalWidth, img.naturalHeight], w, h, maxBytes, 'image/jpeg', '#ffffff')
}

async function encode(
  img: HTMLImageElement, [sx, sy, sw, sh]: number[], width: number, height: number, maxBytes: number,
  type: 'image/jpeg' | 'image/png', background?: string,
): Promise<string> {
  let w = width
  let h = height
  for (let attempt = 0; attempt < 6; attempt++) {
    const canvas = document.createElement('canvas')
    canvas.width = w
    canvas.height = h
    const ctx = canvas.getContext('2d')!
    if (background || type === 'image/jpeg') {
      ctx.fillStyle = background ?? '#ffffff'
      ctx.fillRect(0, 0, w, h)
    }
    ctx.imageSmoothingQuality = 'high'
    ctx.drawImage(img, sx, sy, sw, sh, 0, 0, w, h)
    if (type === 'image/png') return canvas.toDataURL('image/png')
    for (const q of [0.86, 0.78, 0.7, 0.6, 0.5]) {
      const url = canvas.toDataURL('image/jpeg', q)
      if (dataUrlBytes(url) <= maxBytes) return url
    }
    w = Math.round(w * 0.8)
    h = Math.round(h * 0.8)
  }
  throw new Error('The picture could not be made small enough.')
}
