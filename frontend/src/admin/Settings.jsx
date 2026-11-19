import { useEffect, useState } from 'react'
import { AnimatePresence, motion } from 'motion/react'
import { useAdminData } from './AdminData.jsx'
import { useToast } from '../components/Toast.jsx'
import ChangePassword from '../components/ChangePassword.jsx'
import { loadHospital, saveHospital } from '../services/hospital.js'
import { toMinutes } from '../utils/date.js'
import { Button, Field, PageHeader, Panel, SaveButton, UnsavedBar } from '../components/ui.jsx'

const toForm = (h, account) => ({
  name: h.name,
  phone: h.phone,
  erNumber: h.erNumber,
  email: h.email,
  street: h.street,
  cityState: h.cityState,
  opens: h.visiting.start ?? '',
  closes: h.visiting.end ?? '',
  maxVisitors: h.visiting.maxVisitors,
  adminName: account.name,
  adminEmail: account.email,
})

export default function Settings() {
  const { account, setAccount } = useAdminData()
  const toast = useToast()
  const [saved, setSaved] = useState(() => toForm(loadHospital(), account))
  const [form, setForm] = useState(saved)
  const [errors, setErrors] = useState({})
  const [saving, setSaving] = useState(false)
  const [savedFlash, setSavedFlash] = useState(false)
  const [pwOpen, setPwOpen] = useState(false)

  const dirty = JSON.stringify(form) !== JSON.stringify(saved)

  useEffect(() => {
    if (!dirty) return
    const onBeforeUnload = (e) => e.preventDefault()
    window.addEventListener('beforeunload', onBeforeUnload)
    return () => window.removeEventListener('beforeunload', onBeforeUnload)
  }, [dirty])

  const set = (k) => (e) => {
    setForm((f) => ({ ...f, [k]: e.target.value }))
    setErrors((er) => ({ ...er, [k]: undefined }))
  }

  const save = async () => {
    const errs = {}
    if (!form.name.trim()) errs.name = 'Enter the hospital name.'
    if (form.email && !form.email.startsWith('[') && !/^\S+@\S+\.\S+$/.test(form.email)) errs.email = 'Enter a valid email.'
    if (form.adminEmail && !/^\S+@\S+\.\S+$/.test(form.adminEmail)) errs.adminEmail = 'Enter a valid email.'
    if (form.opens && form.closes && toMinutes(form.closes) <= toMinutes(form.opens)) {
      errs.closes = 'Must be after opening time.'
    }
    if ((form.opens && !form.closes) || (!form.opens && form.closes)) errs.closes = 'Set both times, or neither.'
    const visitors = Number(form.maxVisitors)
    if (!Number.isInteger(visitors) || visitors < 1 || visitors > 10) errs.maxVisitors = 'Use a number from 1 to 10.'
    setErrors(errs)
    if (Object.keys(errs).length) {
      toast('Check the highlighted fields', { tone: 'error' })
      return
    }

    setSaving(true)
    try {
      const current = loadHospital()
      await saveHospital({
        ...current,
        name: form.name.trim(),
        phone: form.phone.trim(),
        erNumber: form.erNumber.trim(),
        email: form.email.trim(),
        street: form.street.trim(),
        cityState: form.cityState.trim(),
        visiting: { ...current.visiting, start: form.opens || null, end: form.closes || null, maxVisitors: visitors },
      })
      await setAccount({ name: form.adminName.trim(), email: form.adminEmail.trim() })
    } catch (err) {
      setSaving(false)
      toast(err.message, { tone: 'error' })
      return
    }
    const clean = { ...form, maxVisitors: visitors }
    setForm(clean)
    setSaved(clean)
    setSaving(false)
    setSavedFlash(true)
    setTimeout(() => setSavedFlash(false), 1800)
    toast('Settings saved. The public page is updated.', {
      action: { label: 'View page', onClick: () => window.open('/', '_blank', 'noopener') },
    })
  }

  const field = (k, label, input = {}) => (
    <Field id={`st-${k}`} label={label} value={form[k]} onChange={set(k)} error={errors[k]} {...input} />
  )

  return (
    <div className="grid max-w-280 gap-6">
      <PageHeader title="Settings" sub="Hospital details, notifications and your account.">
        <SaveButton dirty={dirty} saving={saving} saved={savedFlash} onClick={save} />
      </PageHeader>

      <UnsavedBar show={dirty && !saving} onDiscard={() => { setForm(saved); setErrors({}) }} />

      <Panel delay={0.16}>
        <h2 className="text-xl font-extrabold">Hospital information</h2>
        <p className="mt-3.5 mb-4 text-[0.92rem] text-ink-soft">Shown on the public homepage and the Visit us page.</p>
        <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
          {field('name', 'Hospital name')}
          {field('phone', 'Reception phone', { inputMode: 'tel' })}
          {field('erNumber', 'Emergency number', { inputMode: 'tel' })}
          {field('email', 'Email', { type: 'email' })}
          {field('street', 'Street address')}
          {field('cityState', 'City and state')}
        </div>
      </Panel>

      <Panel delay={0.22}>
        <h2 className="mb-4.5 text-xl font-extrabold">Visiting hours</h2>
        <div className="grid gap-4 md:grid-cols-3">
          {field('opens', 'Opens at', { type: 'time', step: 300 })}
          {field('closes', 'Closes at', { type: 'time', step: 300 })}
          {field('maxVisitors', 'Visitors per patient', { type: 'number', min: 1, max: 10, inputMode: 'numeric' })}
        </div>
      </Panel>

      <Panel delay={0.28}>
        <h2 className="mb-4.5 text-xl font-extrabold">Your account</h2>
        <div className="grid gap-4 md:grid-cols-2">
          {field('adminName', 'Full name', { autoComplete: 'name' })}
          {field('adminEmail', 'Email', { type: 'email', autoComplete: 'email' })}
        </div>
        <AnimatePresence initial={false}>
          {pwOpen && (
            <motion.div
              initial={{ height: 0, opacity: 0 }}
              animate={{ height: 'auto', opacity: 1 }}
              exit={{ height: 0, opacity: 0 }}
              transition={{ duration: 0.35, ease: [0.22, 1, 0.36, 1] }}
              className="overflow-hidden"
            >
              <div className="pt-4.5">
                <ChangePassword
                  onDone={() => {
                    setPwOpen(false)
                    toast('Password updated')
                  }}
                />
              </div>
            </motion.div>
          )}
        </AnimatePresence>
        <Button variant="ghost" className="mt-4.5" onClick={() => setPwOpen((o) => !o)} aria-expanded={pwOpen}>
          {pwOpen ? 'Cancel' : 'Change password'}
        </Button>
      </Panel>
    </div>
  )
}
