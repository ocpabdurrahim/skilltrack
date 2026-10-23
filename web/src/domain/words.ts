// Amounts in words for money receipts: "One Lakh Twenty Thousand Five Hundred Only".
import type { Grouping } from './money'

const ONES = ['', 'One', 'Two', 'Three', 'Four', 'Five', 'Six', 'Seven', 'Eight', 'Nine', 'Ten', 'Eleven', 'Twelve',
  'Thirteen', 'Fourteen', 'Fifteen', 'Sixteen', 'Seventeen', 'Eighteen', 'Nineteen']
const TENS = ['', '', 'Twenty', 'Thirty', 'Forty', 'Fifty', 'Sixty', 'Seventy', 'Eighty', 'Ninety']

function belowHundred(n: number): string {
  if (n < 20) return ONES[n]
  return TENS[Math.floor(n / 10)] + (n % 10 ? ` ${ONES[n % 10]}` : '')
}

function belowThousand(n: number): string {
  const h = Math.floor(n / 100)
  const rest = n % 100
  return [h ? `${ONES[h]} Hundred` : '', rest ? belowHundred(rest) : ''].filter(Boolean).join(' ')
}

/** A whole number in words; 0 is "Zero". */
export function numberInWords(n: number, grouping: Grouping = 'SOUTH_ASIAN'): string {
  n = Math.floor(Math.abs(n))
  if (n === 0) return 'Zero'
  const parts: string[] = []
  if (grouping === 'SOUTH_ASIAN') {
    const crore = Math.floor(n / 1e7)
    const lakh = Math.floor((n % 1e7) / 1e5)
    const thousand = Math.floor((n % 1e5) / 1e3)
    const rest = n % 1e3
    if (crore) parts.push(`${numberInWords(crore, grouping)} Crore`)
    if (lakh) parts.push(`${belowHundred(lakh)} Lakh`)
    if (thousand) parts.push(`${belowHundred(thousand)} Thousand`)
    if (rest) parts.push(belowThousand(rest))
  } else {
    const units: [number, string][] = [[1e9, 'Billion'], [1e6, 'Million'], [1e3, 'Thousand']]
    let left = n
    for (const [size, word] of units) {
      const q = Math.floor(left / size)
      if (q) parts.push(`${belowThousand(q)} ${word}`)
      left %= size
    }
    if (left) parts.push(belowThousand(left))
  }
  return parts.join(' ')
}

/** 125050 -> "One Thousand Two Hundred Fifty and 50/100 Only" (with an optional currency word first). */
export function amountInWords(minor: number, grouping: Grouping = 'SOUTH_ASIAN', currencyWord = ''): string {
  const whole = Math.floor(Math.abs(minor) / 100)
  const fraction = Math.abs(minor) % 100
  const words = numberInWords(whole, grouping) + (fraction ? ` and ${String(fraction).padStart(2, '0')}/100` : '')
  return `${currencyWord ? currencyWord + ' ' : ''}${words} Only`
}

const CURRENCY_WORDS: Record<string, string> = {
  BDT: 'Taka', INR: 'Rupees', PKR: 'Rupees', NPR: 'Rupees', LKR: 'Rupees', USD: 'Dollars', EUR: 'Euros', GBP: 'Pounds',
}

export const currencyWord = (code: string) => CURRENCY_WORDS[code] ?? code
