import { useState } from 'react'
import { AnimatePresence, motion } from 'motion/react'
import Modal from '../components/Modal.jsx'
import { Alert, Avatar, Button, Field, cx, inputClass, labelClass } from '../components/ui.jsx'
import { useAdminData } from './AdminData.jsx'
import { departments } from '../config/hospital.js'
import { initials } from '../utils/date.js'

const DOMAIN = 'havenhospital.local'

// "Jane Smith" → "j.smith@havenhospital.local"
function suggestEmail(first, last) {
  const clean = (s) => s.normalize('NFD').replace(/[^a-z]/gi, '').toLowerCase()
  const f = clean(first)
  const l = clean(last)
  if (!f || !l) return ''
  return `${f[0]}.${l}@${DOMAIN}`
}

function Select({ id, label, value, onChange, children }) {
  return (
    <div className="grid content-start gap-2">
      <label htmlFor={id} className={labelClass}>{label}</label>
      <select id={id} value={value} onChange={onChange} className={inputClass}>{children}</select>
    </div>
  )
}

// Add (no `doctor`) or edit a doctor. The backend creates the login account and
// the doctor profile together. Render with a `key` so the form resets each time.
export default function DoctorModal({ open, doctor, onClose, onSaved }) {
  const { addDoctor, updateDoctor } = useAdminData()
  const isEdit = !!doctor
  const [form, setForm] = useState(() =>
    doctor
      ? {
          firstName: doctor.firstName ?? '',
          lastName: doctor.lastName ?? '',
          specialty: doctor.specialty ?? departments[0].name,
          phone: doctor.phone ?? '',
          office: doctor.office ?? '',
          email: doctor.email ?? '',
          status: doctor.status ?? 'active',
          password: '',
        }
      : {
          firstName: '',
          lastName: '',
          specialty: departments[0].name,
          phone: '',
          office: '',
          email: '',
          status: 'active',
          password: '',
        },
  )
  // Keep suggesting the email from the name until the admin types their own.
  const [emailTouched, setEmailTouched] = useState(isEdit)
  const [errors, setErrors] = useState({})
  const [formError, setFormError] = useState('')
  const [saving, setSaving] = useState(false)

  const set = (k) => (e) => {
    const v = e.target.value
    setForm((f) => {
      const next = { ...f, [k]: v }
      if ((k === 'firstName' || k === 'lastName') && !emailTouched) {
        next.email = suggestEmail(next.firstName, next.lastName)
      }
      return next
    })
    if (k === 'email') setEmailTouched(true)
    setErrors((er) => ({ ...er, [k]: undefined }))
    setFormError('')
  }

  const submit = async (e) => {
    e.preventDefault()
    const errs = {}
    if (form.firstName.trim().length < 2) errs.firstName = 'Enter the first name.'
    if (form.lastName.trim().length < 2) errs.lastName = 'Enter the last name.'
    const email = form.email.trim().toLowerCase()
    if (!/^\S+@\S+\.\S+$/.test(email)) errs.email = 'Enter a valid email.'
    if (form.phone && form.phone.replace(/\D/g, '').length < 7) errs.phone = 'Enter a valid phone number.'
    if (!isEdit && (form.password.length < 8 || !/[A-Za-z]/.test(form.password) || !/\d/.test(form.password))) {
      errs.password = 'Use at least 8 characters with a letter and a number.'
    }
    setErrors(errs)
    if (Object.keys(errs).length) return

    setSaving(true)
    setFormError('')
    try {
      const payload = {
        firstName: form.firstName.trim(),
        lastName: form.lastName.trim(),
        specialty: form.specialty,
        phone: form.phone.trim(),
        office: form.office.trim(),
        email,
        status: form.status,
      }
      if (isEdit) {
        await updateDoctor(doctor.id, payload)
        onSaved?.(`Updated Dr. ${payload.firstName} ${payload.lastName}`)
      } else {
        await addDoctor({ ...payload, password: form.password })
        onSaved?.(`Added Dr. ${payload.firstName} ${payload.lastName}`)
      }
      onClose()
    } catch (err) {
      setSaving(false)
      setFormError(err.message)
    }
  }

  const fullName = `Dr. ${form.firstName} ${form.lastName}`.replace(/\s+/g, ' ').trim()

  return (
    <Modal
      open={open}
      onClose={onClose}
      title={isEdit ? 'Edit doctor' : 'Add a doctor'}
      footer={
        <>
          <Button variant="ghost" onClick={onClose}>Cancel</Button>
          <Button type="submit" form="doctor-form" disabled={saving}>
            {saving ? 'Saving…' : isEdit ? 'Save changes' : 'Add doctor'}
          </Button>
        </>
      }
    >
      <div className="mb-4.5 flex items-center gap-3 rounded-[14px] bg-field px-3.5 py-3">
        <Avatar>
          <AnimatePresence mode="wait" initial={false}>
            <motion.span
              key={initials(fullName) || '?'}
              initial={{ scale: 0.4, opacity: 0 }}
              animate={{ scale: 1, opacity: 1 }}
              exit={{ scale: 0.4, opacity: 0 }}
              transition={{ duration: 0.15 }}
            >
              {initials(fullName) || '?'}
            </motion.span>
          </AnimatePresence>
        </Avatar>
        <span className="grid leading-snug">
          <strong>{form.firstName || form.lastName ? fullName : 'New doctor'}</strong>
          <small className="text-[0.85rem] text-ink-soft">
            {form.specialty}
            {doctor ? ` · ${doctor.staffId}` : ' · Staff ID is assigned automatically'}
          </small>
        </span>
        <span
          className={cx(
            'ml-auto size-2.5 shrink-0 rounded-full transition-colors duration-300',
            form.status === 'active' ? 'bg-ok-mid' : form.status === 'leave' ? 'bg-orange' : 'bg-muted',
          )}
          title={form.status}
        />
      </div>

      <form id="doctor-form" onSubmit={submit} noValidate className="grid gap-4 sm:grid-cols-2">
        <Field
          id="doc-first"
          label="First name"
          data-autofocus
          value={form.firstName}
          onChange={set('firstName')}
          placeholder="Jane"
          autoComplete="off"
          error={errors.firstName}
        />
        <Field
          id="doc-last"
          label="Last name"
          value={form.lastName}
          onChange={set('lastName')}
          placeholder="Smith"
          autoComplete="off"
          error={errors.lastName}
        />
        <Select id="doc-specialty" label="Specialty" value={form.specialty} onChange={set('specialty')}>
          {departments.map((d) => <option key={d.name}>{d.name}</option>)}
        </Select>
        <Select id="doc-status" label="Status" value={form.status} onChange={set('status')}>
          <option value="active">Active</option>
          <option value="leave">On leave</option>
          <option value="inactive">Deactivated</option>
        </Select>
        <Field id="doc-phone" label="Phone" value={form.phone} onChange={set('phone')} placeholder="Phone number" inputMode="tel" error={errors.phone} />
        <Field id="doc-office" label="Office" value={form.office} onChange={set('office')} placeholder="e.g. Room 204" />
        <Field
          id="doc-email"
          label="Email (login)"
          type="email"
          value={form.email}
          onChange={set('email')}
          placeholder={`name@${DOMAIN}`}
          error={errors.email}
          className="sm:col-span-2"
        />
        {!isEdit && (
          <Field
            id="doc-password"
            label="Initial password"
            type="password"
            value={form.password}
            onChange={set('password')}
            placeholder="At least 8 characters, with a number"
            autoComplete="new-password"
            error={errors.password}
            className="sm:col-span-2"
          />
        )}

        {formError && <Alert error className="sm:col-span-2">{formError}</Alert>}
      </form>
    </Modal>
  )
}
