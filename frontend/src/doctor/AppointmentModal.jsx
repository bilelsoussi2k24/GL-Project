import { useState } from 'react'
import Modal from '../components/Modal.jsx'
import { Alert, Button, Field, cx } from '../components/ui.jsx'
import { useDoctorData } from './DoctorData.jsx'
import { fromISODate, todayISO, toMinutes } from '../utils/date.js'

const empty = { name: '', hhId: '', phone: '', reason: '', date: '', time: '' }

function toForm(appt) {
  if (!appt) return { ...empty, date: todayISO() }
  return { ...appt.patient, reason: appt.reason, date: appt.date, time: appt.time }
}

// Create (no `appointment`) or edit an appointment.
// Render it with a `key` so the form resets each time it opens.
export default function AppointmentModal({ open, onClose, appointment, onSaved }) {
  const { appointments, profile, addAppointment, updateAppointment } = useDoctorData()
  const [form, setForm] = useState(() => toForm(appointment))
  const [errors, setErrors] = useState({})
  const isEdit = !!appointment

  const set = (field) => (e) => {
    setForm((f) => ({ ...f, [field]: e.target.value }))
    setErrors((er) => ({ ...er, [field]: undefined, conflict: undefined }))
  }

  // Soft warnings: outside working days or hours (still allowed).
  const warnings = []
  if (form.date) {
    const weekday = fromISODate(form.date).getDay()
    if (!profile.workDays.includes(weekday)) warnings.push('This day is outside your working days.')
  }
  if (form.time) {
    const t = toMinutes(form.time)
    if (t < toMinutes(profile.firstAppointment) || t > toMinutes(profile.lastAppointment)) {
      warnings.push(`This time is outside your hours (${profile.firstAppointment}–${profile.lastAppointment}).`)
    }
  }

  const submit = (e) => {
    e.preventDefault()
    const errs = {}
    if (!form.name.trim()) errs.name = 'Enter the patient name.'
    if (form.hhId && !/^HH-\d{6}$/i.test(form.hhId.trim())) errs.hhId = 'Use the format HH-123456.'
    if (!form.reason.trim()) errs.reason = 'Enter the reason for the visit.'
    if (!form.date) errs.date = 'Pick a date.'
    if (!form.time) errs.time = 'Pick a time.'
    if (!errs.date && !errs.time) {
      const clash = appointments.find(
        (a) => a.id !== appointment?.id && a.status !== 'cancelled' && a.date === form.date && a.time === form.time,
      )
      if (clash) errs.conflict = `You already have ${clash.patient.name} at ${clash.time} that day.`
    }
    setErrors(errs)
    if (Object.keys(errs).length) return

    const data = {
      patient: { name: form.name.trim(), hhId: form.hhId.trim().toUpperCase(), phone: form.phone.trim() },
      reason: form.reason.trim(),
      date: form.date,
      time: form.time,
    }
    if (isEdit) {
      updateAppointment(appointment.id, data)
      onSaved?.(`Updated ${data.patient.name}'s appointment`)
    } else {
      addAppointment({ ...data, duration: profile.visitLength })
      onSaved?.(`Booked ${data.patient.name} for ${data.date} at ${data.time}`)
    }
    onClose()
  }

  const field = (key, label, props = {}) => (
    <Field
      id={`appt-${key}`}
      label={label}
      value={form[key]}
      onChange={set(key)}
      error={errors[key]}
      className={cx(props.wide && 'sm:col-span-2')}
      {...props.input}
    />
  )

  return (
    <Modal
      open={open}
      onClose={onClose}
      title={isEdit ? 'Edit appointment' : 'New appointment'}
      footer={
        <>
          <Button variant="ghost" onClick={onClose}>Cancel</Button>
          <Button type="submit" form="appt-form">{isEdit ? 'Save changes' : 'Book appointment'}</Button>
        </>
      }
    >
      <form id="appt-form" onSubmit={submit} noValidate className="grid gap-4 sm:grid-cols-2">
        {field('name', 'Patient name', { wide: true, input: { 'data-autofocus': true, placeholder: 'First and last name', autoComplete: 'off' } })}
        {field('hhId', 'Patient ID', { input: { placeholder: 'HH-000000' } })}
        {field('phone', 'Phone', { input: { placeholder: 'Phone number', inputMode: 'tel' } })}
        {field('reason', 'Reason for visit', { wide: true, input: { placeholder: 'e.g. Follow-up visit' } })}
        {field('date', 'Date', { input: { type: 'date' } })}
        {field('time', 'Time', { input: { type: 'time', step: 300 } })}

        {errors.conflict && <Alert error className="sm:col-span-2">{errors.conflict}</Alert>}
        {warnings.map((w) => (
          <Alert key={w} className="sm:col-span-2">{w}</Alert>
        ))}
      </form>
    </Modal>
  )
}
