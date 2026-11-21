import { useCallback, useEffect, useState } from 'react'
import { AnimatePresence, motion } from 'motion/react'
import { api } from '../services/api.js'
import { useAdminData } from './AdminData.jsx'
import { useToast } from '../components/Toast.jsx'
import Modal from '../components/Modal.jsx'
import Highlight from '../components/Highlight.jsx'
import { UserIcon, SearchIcon, PlusIcon, PencilIcon, StethoscopeIcon } from '../components/Icons.jsx'
import {
  Alert, Button, EmptyState, Field, PageHeader, Panel, PillButton, SearchBox, cx, inputClass, labelClass, rowMotion,
  tbl, textLinkClass,
} from '../components/ui.jsx'

const blank = { firstName: '', lastName: '', birthDate: '', gender: 'Female', phone: '', address: '', doctorId: '' }

function PatientModal({ open, patient, doctors, onClose, onSaved }) {
  const [form, setForm] = useState(
    () =>
      patient
        ? {
            firstName: patient.firstName,
            lastName: patient.lastName,
            birthDate: patient.birthDate ?? '',
            gender: patient.gender ?? '',
            phone: patient.phone ?? '',
            address: patient.address ?? '',
            doctorId: '',
          }
        : blank,
  )
  const [errors, setErrors] = useState({})
  const [formError, setFormError] = useState('')
  const [saving, setSaving] = useState(false)

  const set = (k) => (e) => {
    setForm((f) => ({ ...f, [k]: e.target.value }))
    setErrors((er) => ({ ...er, [k]: undefined }))
    setFormError('')
  }

  const submit = async (e) => {
    e.preventDefault()
    const errs = {}
    if (form.firstName.trim().length < 2) errs.firstName = 'Enter the first name.'
    if (form.lastName.trim().length < 2) errs.lastName = 'Enter the last name.'
    if (!form.birthDate) errs.birthDate = 'Pick the birth date.'
    setErrors(errs)
    if (Object.keys(errs).length) return

    setSaving(true)
    setFormError('')
    const body = {
      firstName: form.firstName.trim(),
      lastName: form.lastName.trim(),
      birthDate: form.birthDate,
      gender: form.gender || null,
      phone: form.phone.trim() || null,
      address: form.address.trim() || null,
    }
    try {
      if (patient) {
        await api(`/admin/patients/${patient.id}`, { method: 'PUT', body })
        if (form.doctorId) {
          await api(`/admin/patients/${patient.id}/doctor`, {
            method: 'PUT',
            body: { doctorId: Number(form.doctorId) },
          })
        }
      } else {
        if (form.doctorId) body.doctorId = Number(form.doctorId)
        await api('/admin/patients', { method: 'POST', body })
      }
      onSaved(patient ? `Updated ${body.firstName} ${body.lastName}` : `${body.firstName} ${body.lastName} registered`)
      onClose()
    } catch (err) {
      setSaving(false)
      setFormError(err.message)
    }
  }

  const usableDoctors = doctors.filter((d) => d.status !== 'inactive')

  return (
    <Modal
      open={open}
      onClose={onClose}
      title={patient ? 'Edit patient' : 'Register a patient'}
      footer={
        <>
          <Button variant="ghost" onClick={onClose}>Cancel</Button>
          <Button type="submit" form="patient-form" disabled={saving}>
            {saving ? 'Saving…' : patient ? 'Save changes' : 'Register patient'}
          </Button>
        </>
      }
    >
      <form id="patient-form" onSubmit={submit} noValidate className="grid gap-4 sm:grid-cols-2">
        <Field id="pt-first" label="First name" data-autofocus value={form.firstName} onChange={set('firstName')} error={errors.firstName} />
        <Field id="pt-last" label="Last name" value={form.lastName} onChange={set('lastName')} error={errors.lastName} />
        <Field id="pt-birth" label="Birth date" type="date" value={form.birthDate} onChange={set('birthDate')} error={errors.birthDate} />
        <div className="grid content-start gap-2">
          <label htmlFor="pt-gender" className={labelClass}>Gender</label>
          <select id="pt-gender" value={form.gender} onChange={set('gender')} className={inputClass}>
            <option value="">Not specified</option>
            <option>Female</option>
            <option>Male</option>
          </select>
        </div>
        <Field id="pt-phone" label="Phone" value={form.phone} onChange={set('phone')} inputMode="tel" />
        <Field id="pt-address" label="Address" value={form.address} onChange={set('address')} />

        <div className="grid content-start gap-2 sm:col-span-2">
          <label htmlFor="pt-doctor" className={labelClass}>
            {patient ? 'Also assign to a doctor (optional)' : 'Assign to a doctor (optional)'}
          </label>
          <select id="pt-doctor" value={form.doctorId} onChange={set('doctorId')} className={inputClass}>
            <option value="">{patient ? 'Keep the current assignment' : 'Not assigned yet'}</option>
            {usableDoctors.map((d) => (
              <option key={d.id} value={d.id}>{d.name} · {d.specialty}</option>
            ))}
          </select>
        </div>

        {formError && <Alert error className="sm:col-span-2">{formError}</Alert>}
      </form>
    </Modal>
  )
}

export default function Patients() {
  const { doctors } = useAdminData()
  const toast = useToast()
  const [patients, setPatients] = useState([])
  const [query, setQuery] = useState('')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [editing, setEditing] = useState(null)
  const [creating, setCreating] = useState(false)

  const load = useCallback(async () => {
    try {
      setPatients(await api('/admin/patients'))
      setError('')
    } catch (err) {
      setError(err.message)
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    load()
  }, [load])

  const q = query.trim()
  const rows = patients.filter(
    (p) => !q || [p.name, p.hhId, p.phone, p.address, p.doctors].join(' ').toLowerCase().includes(q.toLowerCase()),
  )

  return (
    <div className="grid max-w-280 gap-6">
      <PageHeader
        title="Patients"
        sub={`${patients.length} ${patients.length === 1 ? 'patient' : 'patients'} registered`}
      >
        <SearchBox value={query} onChange={setQuery} placeholder="Search patients" label="Search patients" />
        <Button withRipple className="max-sm:flex-1" onClick={() => setCreating(true)}>
          <PlusIcon />
          Register patient
        </Button>
      </PageHeader>

      <Panel delay={0.18} className="overflow-hidden p-0!">
        {loading && <p className="px-6 py-10 text-center text-ink-soft">Loading patients…</p>}
        {!loading && error && <p role="alert" className="px-6 py-10 text-center font-semibold text-er">{error}</p>}

        {!loading && !error && rows.length > 0 && (
          <table className={tbl.table}>
            <thead className={tbl.thead}>
              <tr>
                <th className={tbl.th}>Patient</th>
                <th className={tbl.th}>Date of birth</th>
                <th className={tbl.th}>Doctors</th>
                <th className={tbl.th}>Visits</th>
                <th className={cx(tbl.th, 'text-right')}>Actions</th>
              </tr>
            </thead>
            <tbody className={tbl.tbody}>
              <AnimatePresence initial={true}>
                {rows.map((p, i) => (
                  <motion.tr key={p.id} {...rowMotion(i)} className={tbl.tr}>
                    <td className={tbl.td}>
                      <span className="grid leading-snug">
                        <strong><Highlight text={p.name} query={q} /></strong>
                        <small className="text-[0.85rem] text-ink-soft">
                          <Highlight text={[p.hhId, p.phone].filter(Boolean).join(' · ')} query={q} />
                        </small>
                      </span>
                    </td>
                    <td className={cx(tbl.td, tbl.tdSub, 'whitespace-nowrap')}>{p.birthDate || '—'}</td>
                    <td className={cx(tbl.td, tbl.tdSub)}>
                      <Highlight text={p.doctors || 'Not assigned'} query={q} />
                    </td>
                    <td className={cx(tbl.td, tbl.tdSub, 'whitespace-nowrap')}>
                      {p.appointments} appointment{p.appointments === 1 ? '' : 's'} · {p.consultations}{' '}
                      consultation{p.consultations === 1 ? '' : 's'}
                    </td>
                    <td className={cx(tbl.td, tbl.actions)}>
                      <PillButton onClick={() => setEditing(p)}>
                        <PencilIcon />Edit
                      </PillButton>
                    </td>
                  </motion.tr>
                ))}
              </AnimatePresence>
            </tbody>
          </table>
        )}

        {!loading && !error && rows.length === 0 && (
          <EmptyState icon={q ? SearchIcon : UserIcon}>
            <p>{q ? `No patients match “${q}”.` : 'No patients yet.'}</p>
            {q ? (
              <button type="button" className={textLinkClass} onClick={() => setQuery('')}>Clear search</button>
            ) : (
              <button type="button" className={textLinkClass} onClick={() => setCreating(true)}>Register the first patient</button>
            )}
          </EmptyState>
        )}
      </Panel>

      <p className="flex items-center gap-2 text-[0.9rem] text-ink-soft">
        <StethoscopeIcon width={16} height={16} />
        A doctor only sees patients assigned to them. Assign a patient when registering them or when
        editing an existing record.
      </p>

      {creating && (
        <PatientModal
          key="new"
          open={creating}
          patient={null}
          doctors={doctors}
          onClose={() => setCreating(false)}
          onSaved={(msg) => { load(); toast(msg) }}
        />
      )}
      {editing && (
        <PatientModal
          key={editing.id}
          open
          patient={editing}
          doctors={doctors}
          onClose={() => setEditing(null)}
          onSaved={(msg) => { load(); toast(msg) }}
        />
      )}
    </div>
  )
}
