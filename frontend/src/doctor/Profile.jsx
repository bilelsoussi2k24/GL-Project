import { useEffect, useRef, useState } from 'react'
import { useLocation } from 'react-router-dom'
import { AnimatePresence, motion } from 'motion/react'
import { useDoctorData } from './DoctorData.jsx'
import { useToast } from '../components/Toast.jsx'
import ChangePassword from '../components/ChangePassword.jsx'
import { initials, toMinutes } from '../utils/date.js'
import { UserIcon } from '../components/Icons.jsx'
import {
  Avatar, Button, Field, FieldError, PageHeader, Panel, SaveButton, UnsavedBar, cx, inputClass, labelClass,
} from '../components/ui.jsx'

const days = [
  { n: 1, label: 'Mon' }, { n: 2, label: 'Tue' }, { n: 3, label: 'Wed' }, { n: 4, label: 'Thu' },
  { n: 5, label: 'Fri' }, { n: 6, label: 'Sat' }, { n: 0, label: 'Sun' },
]
const lengths = [15, 20, 30, 45, 60]

function timeAgo(ts) {
  if (!ts) return null
  const daysAgo = Math.floor((Date.now() - ts) / 86_400_000)
  if (daysAgo < 1) return 'today'
  if (daysAgo < 30) return `${daysAgo} day${daysAgo === 1 ? '' : 's'} ago`
  const months = Math.floor(daysAgo / 30)
  return `${months} month${months === 1 ? '' : 's'} ago`
}

export default function Profile() {
  const { profile, setProfile, patchProfile } = useDoctorData()
  const toast = useToast()
  const { hash } = useLocation()
  const hoursRef = useRef(null)
  const [form, setForm] = useState(profile)
  const [errors, setErrors] = useState({})
  const [saving, setSaving] = useState(false)
  const [savedFlash, setSavedFlash] = useState(false)
  const [pwOpen, setPwOpen] = useState(false)
  const [flashHours, setFlashHours] = useState(false)

  const dirty = JSON.stringify(form) !== JSON.stringify(profile)

  // Coming from "Update my working hours": scroll to and outline that section.
  useEffect(() => {
    if (hash !== '#hours') return
    hoursRef.current?.scrollIntoView({ behavior: 'smooth', block: 'center' })
    const on = setTimeout(() => setFlashHours(true), 0)
    const off = setTimeout(() => setFlashHours(false), 1600)
    return () => {
      clearTimeout(on)
      clearTimeout(off)
    }
  }, [hash])

  // Warn before closing the tab with unsaved changes.
  useEffect(() => {
    if (!dirty) return
    const onBeforeUnload = (e) => e.preventDefault()
    window.addEventListener('beforeunload', onBeforeUnload)
    return () => window.removeEventListener('beforeunload', onBeforeUnload)
  }, [dirty])

  const set = (k) => (e) => {
    const v = k === 'visitLength' ? Number(e.target.value) : e.target.value
    setForm((f) => ({ ...f, [k]: v }))
    setErrors((er) => ({ ...er, [k]: undefined }))
  }

  const toggleDay = (n) => {
    setForm((f) => ({
      ...f,
      workDays: (f.workDays.includes(n) ? f.workDays.filter((d) => d !== n) : [...f.workDays, n]).sort((a, b) => a - b),
    }))
    setErrors((er) => ({ ...er, workDays: undefined }))
  }

  const save = async () => {
    const errs = {}
    if (!form.name.trim()) errs.name = 'Enter your name.'
    if (form.email && !/^\S+@\S+\.\S+$/.test(form.email)) errs.email = 'Enter a valid email.'
    if (!form.workDays.length) errs.workDays = 'Pick at least one day.'
    if (!form.firstAppointment || !form.lastAppointment) errs.lastAppointment = 'Set both times.'
    else if (toMinutes(form.lastAppointment) <= toMinutes(form.firstAppointment)) {
      errs.lastAppointment = 'Must be after the first appointment.'
    }
    setErrors(errs)
    if (Object.keys(errs).length) {
      toast('Check the highlighted fields', { tone: 'error' })
      return
    }
    setSaving(true)
    try {
      await setProfile(form)
    } catch (err) {
      setSaving(false)
      toast(err.message, { tone: 'error' })
      return
    }
    setSaving(false)
    setSavedFlash(true)
    setTimeout(() => setSavedFlash(false), 1800)
    toast('Profile saved')
  }

  const field = (k, label, props = {}) => (
    <Field id={`pf-${k}`} label={label} value={form[k]} onChange={set(k)} error={errors[k]} {...props} />
  )

  return (
    <div className="grid max-w-280 gap-6">
      <PageHeader title="My profile" sub="Your details and when patients can be booked with you.">
        <SaveButton dirty={dirty} saving={saving} saved={savedFlash} onClick={save} />
      </PageHeader>

      <UnsavedBar show={dirty && !saving} onDiscard={() => { setForm(profile); setErrors({}) }} />

      <Panel delay={0.16}>
        <div className="mb-5 flex items-center gap-4">
          <motion.span
            initial={{ scale: 0, rotate: -30 }}
            animate={{ scale: 1, rotate: 0 }}
            transition={{ type: 'spring', stiffness: 260, damping: 14, delay: 0.25 }}
          >
            <Avatar tone="peach" size="xl">{initials(form.name) || <UserIcon width={30} height={30} />}</Avatar>
          </motion.span>
          <div>
            <h2 className="text-[1.4rem] font-extrabold">{form.name.trim() || 'Your name'}</h2>
            <p className="mt-1 text-[0.92rem] text-ink-soft">{profile.specialty || 'Specialty not set yet'} · Staff ID {profile.staffId || '—'}</p>
          </div>
        </div>
        <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
          {field('name', 'Full name', { autoComplete: 'name' })}
          {field('phone', 'Phone', { inputMode: 'tel', autoComplete: 'tel' })}
          {field('email', 'Email', { type: 'email', autoComplete: 'email' })}
          {field('office', 'Office')}
        </div>
        <p className="mt-4.5 text-[0.88rem] text-ink-soft">Your specialty and staff ID are set by the administrator.</p>
      </Panel>

      <Panel
        ref={hoursRef}
        id="hours"
        delay={0.22}
        className={cx('scroll-mt-6 transition-[box-shadow,border-color] duration-500', flashHours && 'border-primary ring-4 ring-primary/18')}
      >
        <h2 className="mb-4 text-xl font-extrabold">Working hours</h2>
        <p className={labelClass} id="days-label">Days you see patients</p>
        <div className="mt-2.5 mb-4.5 flex flex-wrap gap-2" role="group" aria-labelledby="days-label">
          {days.map(({ n, label }) => {
            const on = form.workDays.includes(n)
            return (
              <motion.button
                key={n}
                type="button"
                aria-pressed={on}
                onClick={() => toggleDay(n)}
                whileHover={{ y: -2 }}
                whileTap={{ scale: 0.9 }}
                animate={on ? { scale: [1, 1.12, 1] } : { scale: 1 }}
                transition={{ duration: 0.3 }}
                className={cx(
                  'h-11 min-w-16 rounded-xl border-[1.5px] px-3.5 font-bold transition-colors duration-300',
                  on ? 'border-primary bg-primary text-white' : 'border-line bg-surface text-ink hover:border-primary',
                )}
              >
                {label}
              </motion.button>
            )
          })}
        </div>
        <FieldError>{errors.workDays}</FieldError>

        <div className="mt-2 grid gap-4 md:grid-cols-3">
          {field('firstAppointment', 'First appointment', { type: 'time', step: 300 })}
          {field('lastAppointment', 'Last appointment', { type: 'time', step: 300 })}
          <div className="grid content-start gap-2">
            <label htmlFor="pf-visitLength" className={labelClass}>Visit length</label>
            <select id="pf-visitLength" value={form.visitLength} onChange={set('visitLength')} className={inputClass}>
              {lengths.map((m) => <option key={m} value={m}>{m} minutes</option>)}
            </select>
          </div>
        </div>
      </Panel>

      <Panel delay={0.28}>
        <h2 className="text-xl font-extrabold">Password</h2>
        <p className="mt-3.5 mb-4 text-[0.92rem] text-ink-soft">{profile.passwordChangedAt ? `Last changed ${timeAgo(profile.passwordChangedAt)}.` : 'Not changed since your account was created.'}</p>
        <AnimatePresence initial={false}>
          {pwOpen && (
            <motion.div
              initial={{ height: 0, opacity: 0 }}
              animate={{ height: 'auto', opacity: 1 }}
              exit={{ height: 0, opacity: 0 }}
              transition={{ duration: 0.35, ease: [0.22, 1, 0.36, 1] }}
              className="overflow-hidden"
            >
              <ChangePassword
                onDone={(result) => {
                  const ts = result?.passwordChangedAt ?? Date.now()
                  patchProfile({ passwordChangedAt: ts })
                  setForm((f) => ({ ...f, passwordChangedAt: ts }))
                  setPwOpen(false)
                  toast('Password updated')
                }}
              />
            </motion.div>
          )}
        </AnimatePresence>
        <Button variant="ghost" onClick={() => setPwOpen((o) => !o)} aria-expanded={pwOpen}>
          {pwOpen ? 'Cancel' : 'Change password'}
        </Button>
      </Panel>
    </div>
  )
}
