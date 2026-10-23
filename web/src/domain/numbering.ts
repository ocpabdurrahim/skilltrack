// Numbers people see: {prefix}-{year}-{nnnn} (docs/data-model.md, "Numbers people see").

/** One more than the highest number already used with this prefix and year. */
export function nextNumber(prefix: string, year: number | string, existing: Iterable<string>): string {
  const head = `${prefix}-${year}-`
  let max = 0
  for (const s of existing) {
    if (!s.startsWith(head)) continue
    const n = Number(s.slice(head.length))
    if (Number.isInteger(n) && n > max) max = n
  }
  return `${head}${String(max + 1).padStart(4, '0')}`
}

export const yearOf = (date: string) => (date && date.length >= 4 ? date.slice(0, 4) : String(new Date().getFullYear()))
