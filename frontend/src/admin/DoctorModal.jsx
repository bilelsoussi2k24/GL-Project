import { useState } from 'react'
import { AnimatePresence, motion } from 'motion/react'
import Modal from '../components/Modal.jsx'
import { Avatar, Button, Field, cx, inputClass, labelClass } from '../components/ui.jsx'
import { useAdminData } from './AdminData.jsx'
import { departments } from '../config/hospital.js'
import { initials } from '../utils/date.js'

const DOMAIN = 'havenhospital.ng'

// "Dr. Jane Smith" → "j.smith@havenhospital.ng"
function suggestEmail(name) {
  const parts = name.replace(/^Dr\.?\s*/i, '').trim().toLowerCase().split(/\s+/).filter(Boolean)
  if (parts.length < 2) return ''
  const clean = (s) => s.normalize('NFD').replace(/[^a-z]/g, '')
  return `${clean(parts[0])[0] ?? ''}.${clean(parts[parts.length - 1])}@${DOMAIN}`
}

const withTitle = (name) => (/^Dr\.?\s/i.test(name) ? name.replace(/^Dr\.?\s*/i, 'Dr. ') : `Dr. ${name}`)

function Select({ id, label, value, onChange, children }) {
  return (
    <div className="grid content-start gap-2">
      <label htmlFor={id} className={labelClass}>{label}</label>
      <select id={id} value={value} onChange={onChange} className={inputClass}>{children}</select>
    </div>
  )
}

// Add (no `doctor`) or edit a doctor. Render with a `key` so the form resets each time.
export default function DoctorModal({ open, doctor, onClose, onSaved }) {
  const { doctors, addDoctor, updateDoctor } = useAdminData()
  const isEdit = !!doctor
  const [form, setForm] = useState(
    () => doctor ?? { name: '', specialty: departments[0].name, phone: '', email: '', status: 'active' },
  )
  // Keep suggesting the email from the name until the admin types their own.
  const [emailTouched, setEmailTouched] = useState(isEdit)
  const [errors, setErrors] = useState({})

  const set = (k) => (e) => {
    const v = e.target.value
    setForm((f) => {
      const next = { ...f, [k]: v }
      if (k === 'name' && !emailTouched) next.email = suggestEmail(v)
      return next
    })
    if (k === 'email') setEmailTouched(true)
    setErrors((er) => ({ ...er, [k]: undefined }))
  }

  const submit = (e) => {
    e.preventDefault()
    const errs = {}
    const name = form.name.trim()
    const email = form.email.trim().toLowerCase()
    if (name.replace(/^Dr\.?\s*/i, '').length < 2) errs.name = 'Enter the doctor’s full name.'
    if (!/^\S+@\S+\.\S+$/.test(email)) errs.email = 'Enter a valid email.'
    else if (doctors.some((d) => d.id !== doctor?.id && d.email.toLowerCase() === email)) {
      errs.email = 'Another doctor already uses this email.'
    }
    if (form.phone && form.phone.replace(/\D/g, '').length < 7) errs.phone = 'Enter a valid phone number.'
    setErrors(errs)
    if (Object.keys(errs).length) return

    const data = { ...form, name: withTitle(name), email, phone: form.phone.trim() }
    if (isEdit) {
      updateDoctor(doctor.id, data)
      onSaved(`Updated ${data.name}`)
    } else {
      addDoctor(data)
      onSaved(`Added ${data.name}`)
    }
    onClose()
  }

  const ini = initials(form.name)

  return (
    <Modal
      open={open}
      onClose={onClose}
      title={isEdit ? 'Edit doctor' : 'Add a doctor'}
      footer={
        <>
          <Button variant="ghost" onClick={onClose}>Cancel</Button>
          <Button type="submit" form="doctor-form">{isEdit ? 'Save changes' : 'Add doctor'}</Button>
        </>
      }
    >
      {/* Live preview card */}
      <div className="mb-4.5 flex items-center gap-3 rounded-[14px] bg-field px-3.5 py-3">
        <Avatar>
          <AnimatePresence mode="wait" initial={false}>
            <motion.span
              key={ini || '?'}
              initial={{ scale: 0.4, opacity: 0 }}
              animate={{ scale: 1, opacity: 1 }}
              exit={{ scale: 0.4, opacity: 0 }}
              transition={{ duration: 0.15 }}
            >
              {ini || '?'}
            </motion.span>
          </AnimatePresence>
        </Avatar>
        <span className="grid leading-snug">
          <strong>{form.name.trim() ? withTitle(form.name.trim()) : 'New doctor'}</strong>
          <small className="text-[0.85rem] text-ink-soft">
            {form.specialty}
            {doctor ? ` · ${doctor.staffId}` : ' · Staff ID is assigned automatically'}
          </small>
        </span>
        <span
          className={cx(
            'ml-auto size-2.5 shrink-0 rounded-full transition-colors duration-300',
            form.status === 'active' ? 'bg-ok-mid' : 'bg-orange',
          )}
          title={form.status === 'active' ? 'Active' : 'On leave'}
        />
      </div>

      <form id="doctor-form" onSubmit={submit} noValidate className="grid gap-4 sm:grid-cols-2">
        <Field
          id="doc-name"
          label="Full name"
          data-autofocus
          value={form.name}
          onChange={set('name')}
          placeholder="First and last name"
          autoComplete="off"
          error={errors.name}
          className="sm:col-span-2"
        />
        <Select id="doc-specialty" label="Specialty" value={form.specialty} onChange={set('specialty')}>
          {departments.map((d) => <option key={d.name}>{d.name}</option>)}
        </Select>
        <Select id="doc-status" label="Status" value={form.status} onChange={set('status')}>
          <option value="active">Active</option>
          <option value="leave">On leave</option>
        </Select>
        <Field id="doc-phone" label="Phone" value={form.phone} onChange={set('phone')} placeholder="Phone number" inputMode="tel" error={errors.phone} />
        <Field id="doc-email" label="Email" type="email" value={form.email} onChange={set('email')} placeholder={`name@${DOMAIN}`} error={errors.email} />
      </form>
    </Modal>
  )
}
