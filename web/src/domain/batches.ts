// A batch's status is worked out from its dates (docs/data-model.md, "batches").
import type { Batch, Day } from '../data/types'
import { DAYS } from '../data/types'
import type { DateStr } from './dates'

export type BatchStatus = 'RUNNING' | 'UPCOMING' | 'COMPLETED' | 'CANCELLED'

export function batchStatus(b: Batch, today: DateStr): BatchStatus {
  if (b.cancelled) return 'CANCELLED'
  if (b.startDate && today < b.startDate) return 'UPCOMING'
  if (b.endDate && today > b.endDate) return 'COMPLETED'
  return 'RUNNING'
}

export const STATUS_LABELS: Record<BatchStatus, string> = {
  RUNNING: 'Running', UPCOMING: 'Upcoming', COMPLETED: 'Completed', CANCELLED: 'Cancelled',
}

/** "Sat, Mon, Wed" in week order. */
export function daysLabel(days: Day[]): string {
  return DAYS.filter(d => days.includes(d)).map(d => d.charAt(0) + d.slice(1).toLowerCase()).join(', ')
}
