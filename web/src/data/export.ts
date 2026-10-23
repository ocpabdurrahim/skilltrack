// CSV exports for spreadsheets.

function csvEscape(value: string): string {
  return /[",\r\n]/.test(value) ? `"${value.replace(/"/g, '""')}"` : value
}

export function plainAmount(minor: number): string {
  const sign = minor < 0 ? '-' : ''
  const a = Math.abs(minor)
  return `${sign}${Math.floor(a / 100)}.${String(a % 100).padStart(2, '0')}`
}

export function buildCsv(header: string[], rows: (string | number)[][]): string {
  const lines = [header, ...rows].map(r => r.map(v => csvEscape(String(v))).join(','))
  // The byte-order mark makes Excel read the file as UTF-8, so Bangla names survive.
  return '\uFEFF' + lines.join('\r\n') + '\r\n'
}

export function download(fileName: string, text: string, type = 'text/csv;charset=utf-8') {
  const url = URL.createObjectURL(new Blob([text], { type }))
  const a = document.createElement('a')
  a.href = url
  a.download = fileName
  document.body.appendChild(a)
  a.click()
  a.remove()
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}
