// The web app's copy of one center, kept in step with Firestore
// (docs/data-model.md, "Reading").
//
// Start-up reads whatever the browser already cached (instant), then listens
// only for documents changed since the newest cached one. Deletes are
// tombstones ({deleted: true}) so they arrive through the same listener.
// Writes update memory at once and reach Firestore in the background (queued
// while offline). Photos are not kept here; see loadPhoto().
import {
  collection, doc, getDoc, getDocFromCache, getDocsFromCache, onSnapshot, query, serverTimestamp, setDoc, Timestamp,
  where, writeBatch, type DocumentData, type DocumentSnapshot, type Firestore, type Unsubscribe,
} from 'firebase/firestore'
import { useEffect, useState, useSyncExternalStore } from 'react'
import { canWrite } from '../domain/roles'
import { parseCenter, parsers } from './parse'
import {
  COLLECTIONS, DEFAULT_EXPENSE_HEADS, MONEY_COLLECTIONS,
  type Batch, type Center, type CollectionName, type Collections, type Course, type Enrollment, type ExpenseHead,
  type Instructor, type Member, type Role, type Student, type WritableName,
} from './types'

type Lists = { [K in CollectionName]: Collections[K][] }

export interface CenterData extends Lists {
  centerId: string
  /** False until the first data (cached or from the server) is in. */
  ready: boolean
  /** True once the server has answered for every collection this role reads. */
  synced: boolean
  /** Writes made here that the server has not confirmed yet. */
  pending: number
  error: string | null
  /** This person is no longer (active) staff here. */
  revoked: boolean
  center: Center | null
  me: Member | null
  role: Role
  email: string
  idx: {
    students: Map<string, Student>
    courses: Map<string, Course>
    batches: Map<string, Batch>
    enrollments: Map<string, Enrollment>
    instructors: Map<string, Instructor>
    expenseHeads: Map<string, ExpenseHead>
    members: Map<string, Member>
  }
}

type Maps = { [K in CollectionName]: Map<string, Collections[K]> }

const emptyMaps = (): Maps => Object.fromEntries(COLLECTIONS.map(n => [n, new Map()])) as unknown as Maps
const emptySets = (): Record<CollectionName, Set<string>> =>
  Object.fromEntries(COLLECTIONS.map(n => [n, new Set<string>()])) as Record<CollectionName, Set<string>>

const byName = (a: { name: string }, b: { name: string }) => a.name.localeCompare(b.name)
const desc = <T extends { date: string; createdAt: number }>(a: T, b: T) => b.date.localeCompare(a.date) || b.createdAt - a.createdAt

function updatedMillis(d: DocumentSnapshot): number {
  const value = d.get('updatedAt')
  return value instanceof Timestamp ? value.toMillis() : 0
}

const isPermissionError = (err: unknown) => (err as { code?: string })?.code === 'permission-denied'

class CenterStore {
  private db: Firestore | null = null
  private centerId = ''
  private email = ''
  private initialRole: Role = 'TEACHER'
  private baseUnsubs: Unsubscribe[] = []
  private collectionUnsubs = new Map<CollectionName, Unsubscribe>()
  private generation = 0
  private maps = emptyMaps()
  /** Every id seen, deleted or not: a collection with history is never re-seeded. */
  private seen = emptySets()
  private serverLoaded = new Set<CollectionName>()
  private center: Center | null = null
  private ready = false
  private pending = 0
  private error: string | null = null
  private revoked = false
  private listeners = new Set<() => void>()
  private cached: CenterData | null = null
  private photos = new Map<string, string | null>()
  private photoListeners = new Set<() => void>()

  subscribe = (listener: () => void) => {
    this.listeners.add(listener)
    return () => this.listeners.delete(listener)
  }

  getSnapshot = (): CenterData => {
    if (!this.cached) {
      const m = this.maps
      const list = <K extends CollectionName>(name: K) => [...m[name].values()] as Collections[K][]
      const me = m.members.get(this.email) ?? null
      const readable = this.readable()
      this.cached = {
        centerId: this.centerId,
        ready: this.ready,
        synced: readable.every(n => this.serverLoaded.has(n)) && this.center !== null,
        pending: this.pending,
        error: this.error,
        revoked: this.revoked,
        center: this.center,
        me,
        role: this.role(),
        email: this.email,
        members: list('members').sort((a, b) => roleRank(a.role) - roleRank(b.role) || byName(a, b)),
        courses: list('courses').sort((a, b) => Number(a.archived) - Number(b.archived) || byName(a, b)),
        instructors: list('instructors').sort((a, b) => Number(a.archived) - Number(b.archived) || byName(a, b)),
        batches: list('batches').sort((a, b) => b.startDate.localeCompare(a.startDate) || byName(a, b)),
        students: list('students').sort((a, b) => b.regNo.localeCompare(a.regNo) || byName(a, b)),
        enrollments: list('enrollments').sort((a, b) => a.rollNo - b.rollNo || a.date.localeCompare(b.date)),
        payments: list('payments').sort(desc),
        attendance: list('attendance').sort((a, b) => b.date.localeCompare(a.date)),
        exams: list('exams').sort((a, b) => a.date.localeCompare(b.date) || byName(a, b)),
        certificates: list('certificates').sort((a, b) => b.issueDate.localeCompare(a.issueDate) || b.certNo.localeCompare(a.certNo)),
        enquiries: list('enquiries').sort((a, b) => b.date.localeCompare(a.date) || b.createdAt - a.createdAt),
        expenseHeads: list('expenseHeads').sort((a, b) => Number(a.archived) - Number(b.archived) || byName(a, b)),
        expenses: list('expenses').sort(desc),
        instructorPayments: list('instructorPayments').sort(desc),
        idx: {
          students: m.students, courses: m.courses, batches: m.batches, enrollments: m.enrollments,
          instructors: m.instructors, expenseHeads: m.expenseHeads, members: m.members,
        },
      }
    }
    return this.cached
  }

  private changed() {
    this.cached = null
    this.listeners.forEach(l => l())
  }

  /** The role from the live staff record, or the one found at sign-in until it loads. */
  private role(): Role {
    return this.maps.members.get(this.email)?.role ?? this.initialRole
  }

  private readable(): CollectionName[] {
    return this.role() === 'TEACHER' ? COLLECTIONS.filter(n => !MONEY_COLLECTIONS.includes(n)) : COLLECTIONS
  }

  start(db: Firestore, centerId: string, email: string, role: Role) {
    this.stop()
    this.db = db
    this.centerId = centerId
    this.email = email
    this.initialRole = role
    const gen = ++this.generation
    const centerRef = doc(db, 'centers', centerId)
    getDocFromCache(centerRef)
      .then(snap => {
        if (gen === this.generation && snap.exists() && !this.center) {
          this.center = parseCenter(snap.id, snap.data())
          this.changed()
        }
      })
      .catch(() => { /* not cached yet */ })
    this.baseUnsubs.push(onSnapshot(centerRef, snap => {
      const data = snap.data({ serverTimestamps: 'estimate' })
      if (data) this.center = parseCenter(snap.id, data)
      this.changed()
    }, err => this.fail(err)))
    this.syncListeners()
  }

  stop() {
    this.generation++
    this.baseUnsubs.forEach(u => u())
    this.baseUnsubs = []
    this.collectionUnsubs.forEach(u => u())
    this.collectionUnsubs.clear()
    this.maps = emptyMaps()
    this.seen = emptySets()
    this.serverLoaded.clear()
    this.center = null
    this.ready = false
    this.error = null
    this.revoked = false
    this.pending = 0
    this.photos.clear()
    this.db = null
    this.centerId = ''
    this.changed()
  }

  /** Listens to what the current role may read, and drops what it may not. */
  private syncListeners() {
    const readable = new Set(this.readable())
    for (const name of COLLECTIONS) {
      const listening = this.collectionUnsubs.has(name)
      if (readable.has(name) && !listening) {
        this.collectionUnsubs.set(name, () => {}) // placeholder until listen() replaces it
        void this.listen(name, this.generation)
      } else if (!readable.has(name) && listening) {
        this.collectionUnsubs.get(name)!()
        this.collectionUnsubs.delete(name)
        this.maps[name].clear()
        this.seen[name].clear()
        this.serverLoaded.delete(name)
      }
    }
  }

  private async listen(name: CollectionName, gen: number) {
    const col = collection(this.db!, 'centers', this.centerId, name)
    let newest = 0
    try {
      const cached = await getDocsFromCache(col)
      if (gen !== this.generation || !this.collectionUnsubs.has(name)) return
      cached.forEach(d => {
        this.apply(name, d)
        newest = Math.max(newest, updatedMillis(d))
      })
    } catch {
      // Nothing cached yet: the listener below downloads everything.
    }
    if (gen !== this.generation || !this.collectionUnsubs.has(name)) return
    if (name === 'members') this.ready = true
    this.changed()
    const changes = query(col, where('updatedAt', '>=', Timestamp.fromMillis(newest)))
    const unsub = onSnapshot(changes, { includeMetadataChanges: true }, snap => {
      snap.docChanges().forEach(change => this.apply(name, change.doc))
      if (!snap.metadata.fromCache && !this.serverLoaded.has(name)) {
        this.serverLoaded.add(name)
        this.afterServerLoad(name)
      }
      if (name === 'members') this.afterMembers()
      this.ready = true
      this.changed()
    }, err => this.fail(err))
    this.collectionUnsubs.set(name, unsub)
  }

  private apply(name: CollectionName, d: DocumentSnapshot) {
    const data = d.data({ serverTimestamps: 'estimate' })
    if (!data) return
    this.seen[name].add(d.id)
    if (data.deleted === true) this.maps[name].delete(d.id)
    else (this.maps[name] as Map<string, unknown>).set(d.id, parsers[name](d.id, data))
  }

  private afterMembers() {
    const me = this.maps.members.get(this.email)
    if (this.serverLoaded.has('members') && (!me || !me.active)) this.revoked = true
    // A changed role may read more or less than before.
    this.syncListeners()
  }

  private afterServerLoad(name: CollectionName) {
    // A new center gets the starter expense heads, from whoever first may write them.
    if (name === 'expenseHeads' && this.seen.expenseHeads.size === 0 && canWrite(this.role(), 'expenseHeads')) {
      this.putMany(DEFAULT_EXPENSE_HEADS.map(h => ['expenseHeads', h] as const))
    }
  }

  private fail(err: unknown) {
    if (isPermissionError(err)) this.revoked = true
    this.error = err instanceof Error ? err.message : String(err)
    this.changed()
  }

  private ref(name: WritableName, id: string) {
    if (!this.db || !this.centerId) throw new Error('No center is open')
    return doc(this.db, 'centers', this.centerId, name, id)
  }

  private track(write: Promise<unknown>) {
    this.pending++
    this.changed()
    write
      .catch(err => this.fail(err))
      .finally(() => {
        this.pending--
        this.changed()
      })
  }

  // ---- Writes -------------------------------------------------------------

  put<K extends CollectionName>(name: K, item: Collections[K]) {
    this.putMany([[name, item]])
  }

  putMany(items: ReadonlyArray<readonly [CollectionName, Collections[CollectionName]]>) {
    if (items.length === 0 || !this.db) return
    for (const [name, item] of items) {
      ;(this.maps[name] as Map<string, unknown>).set(item.id, item)
      this.seen[name].add(item.id)
    }
    this.changed()
    // Firestore batches hold at most 500 writes.
    for (let i = 0; i < items.length; i += 450) {
      const batch = writeBatch(this.db)
      for (const [name, item] of items.slice(i, i + 450)) {
        const { id, ...fields } = item
        batch.set(this.ref(name, id), { ...fields, deleted: false, updatedAt: serverTimestamp() })
      }
      this.track(batch.commit())
    }
  }

  remove(name: CollectionName, id: string) {
    this.maps[name].delete(id)
    this.changed()
    this.track(setDoc(this.ref(name, id), { deleted: true, updatedAt: serverTimestamp() }, { merge: true }))
  }

  setCenter(center: Center) {
    if (!this.db) return
    this.center = center
    this.changed()
    const { id, ...fields } = center
    this.track(setDoc(doc(this.db, 'centers', id), { ...fields, updatedAt: serverTimestamp() }))
  }

  // ---- Photos (fetched one at a time, never in bulk) ------------------------

  subscribePhotos = (listener: () => void) => {
    this.photoListeners.add(listener)
    return () => this.photoListeners.delete(listener)
  }

  /** The cached photo: a data URL, null for none, undefined while unknown. */
  photo(studentId: string): string | null | undefined {
    return this.photos.get(studentId)
  }

  private setPhotoLocal(studentId: string, data: string | null) {
    this.photos.set(studentId, data)
    this.photoListeners.forEach(l => l())
  }

  /**
   * Cache first (instant, offline). The server is asked only when the photo is
   * not cached, or with [fresh] (a student's own page) for anything newer.
   */
  async loadPhoto(studentId: string, fresh = false): Promise<string | null> {
    if (!this.db || !this.centerId) return null
    const known = this.photos.get(studentId)
    if (known !== undefined && !fresh) return known
    const ref = this.ref('photos', studentId)
    const read = (d: DocumentData | undefined) => (d && d.deleted !== true && typeof d.data === 'string' ? d.data : null)
    if (known === undefined) {
      try {
        const cached = await getDocFromCache(ref)
        this.setPhotoLocal(studentId, read(cached.data()))
        if (!fresh) return this.photos.get(studentId) ?? null
      } catch {
        // Not cached.
      }
    }
    try {
      const fresh = await getDoc(ref)
      this.setPhotoLocal(studentId, read(fresh.data()))
    } catch {
      if (!this.photos.has(studentId)) this.setPhotoLocal(studentId, null)
    }
    return this.photos.get(studentId) ?? null
  }

  setPhoto(studentId: string, data: string | null) {
    this.setPhotoLocal(studentId, data)
    const ref = this.ref('photos', studentId)
    this.track(data
      ? setDoc(ref, { data, deleted: false, updatedAt: serverTimestamp() })
      : setDoc(ref, { deleted: true, updatedAt: serverTimestamp() }, { merge: true }))
  }
}

const roleRank = (r: Role) => ['OWNER', 'ADMIN', 'OFFICE', 'TEACHER'].indexOf(r)

export const store = new CenterStore()

export function useData(): CenterData {
  return useSyncExternalStore(store.subscribe, store.getSnapshot)
}

/** A student's photo, loaded on demand: undefined while loading, null when there is none. */
export function usePhoto(studentId: string | null | undefined, fresh = false): string | null | undefined {
  const [, force] = useState(0)
  useEffect(() => {
    const off = store.subscribePhotos(() => force(n => n + 1))
    return () => { off() }
  }, [])
  useEffect(() => {
    if (studentId) void store.loadPhoto(studentId, fresh)
  }, [studentId, fresh])
  return studentId ? store.photo(studentId) : null
}
