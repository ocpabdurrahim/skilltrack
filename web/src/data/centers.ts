// Finding the centers a person works at, and creating a new one
// (docs/data-model.md, "Centers, staff and roles").
import {
  collectionGroup, doc, getDoc, getDocs, query, serverTimestamp, where, writeBatch, type Firestore,
} from 'firebase/firestore'
import { newId } from './ids'
import { parseCenter, parsers } from './parse'
import type { Center, Role } from './types'

export interface CenterChoice {
  id: string
  name: string
  tagline: string
  role: Role
}

/** Centers where this email is active staff, by name. */
export async function findCenters(db: Firestore, email: string): Promise<CenterChoice[]> {
  const snap = await getDocs(query(collectionGroup(db, 'members'), where('email', '==', email)))
  const out: CenterChoice[] = []
  for (const d of snap.docs) {
    const data = d.data()
    if (data.deleted === true) continue
    const member = parsers.members(d.id, data)
    const centerRef = d.ref.parent.parent
    if (!member.active || !centerRef) continue
    try {
      const c = await getDoc(doc(db, 'centers', centerRef.id))
      if (!c.exists()) continue
      const center = parseCenter(c.id, c.data())
      out.push({ id: c.id, name: center.name, tagline: center.tagline, role: member.role })
    } catch {
      // Not readable right now (e.g. access just changed): leave it out.
    }
  }
  return out.sort((a, b) => a.name.localeCompare(b.name))
}

export type NewCenter = Pick<Center, 'name' | 'tagline' | 'address' | 'phone' | 'email'>

/** One batch: the center and its owner's staff record. Returns the new center's id. */
export async function createCenter(
  db: Firestore, user: { uid: string; email: string; name: string }, fields: NewCenter,
): Promise<string> {
  const id = newId()
  const now = Date.now()
  const center: Omit<Center, 'id'> = {
    ...fields, website: '', logo: '', signatory: 'Director', currencyCode: 'BDT', currencySymbol: '৳',
    grouping: 'SOUTH_ASIAN', studentPrefix: 'ST', receiptPrefix: 'MR', certificatePrefix: 'CERT',
    ownerUid: user.uid, ownerEmail: user.email, createdAt: now,
  }
  const batch = writeBatch(db)
  batch.set(doc(db, 'centers', id), { ...center, updatedAt: serverTimestamp() })
  batch.set(doc(db, 'centers', id, 'members', user.email), {
    email: user.email, name: user.name, role: 'OWNER', active: true, instructorId: null, addedBy: user.email,
    createdAt: now, deleted: false, updatedAt: serverTimestamp(),
  })
  await batch.commit()
  return id
}

const LAST_KEY = 'skilltrack.lastCenter'

export function lastCenter(email: string): string | null {
  try {
    return localStorage.getItem(`${LAST_KEY}.${email}`)
  } catch {
    return null
  }
}

export function rememberCenter(email: string, centerId: string | null) {
  try {
    if (centerId) localStorage.setItem(`${LAST_KEY}.${email}`, centerId)
    else localStorage.removeItem(`${LAST_KEY}.${email}`)
  } catch {
    // Storage blocked: the choice lasts for this visit.
  }
}
