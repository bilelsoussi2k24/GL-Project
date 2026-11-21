import { useState } from 'react'
import Modal from '../components/Modal.jsx'
import { Alert, Button, Field } from '../components/ui.jsx'
import { useDoctorData } from './DoctorData.jsx'

const textarea =
  'w-full rounded-[10px] border-[1.5px] border-line bg-field px-3.5 py-2.5 text-ink transition ' +
  'hover:border-line-strong focus:border-primary focus:bg-white focus:ring-4 focus:ring-primary/15 focus:outline-none'

// Records or edits the consultation notes of a completed appointment.
// Render it with a `key` so the form resets each time it opens.
export default function ConsultationModal({ open, appointment, consultation, onClose, onSaved }) {
  const { addConsultation, updateConsultation, consultationsFor } = useDoctorData()
  const [form, setForm] = useState({
    diagnosis: consultation?.diagnosis ?? '',
    treatment: consultation?.treatment ?? '',
    notes: consultation?.notes ?? '',
  })
  const [errors, setErrors] = useState({})
  const [formError, setFormError] = useState('')
  const [saving, setSaving] = useState(false)

  const history = appointment ? consultationsFor(appointment.patientId) : []

  const set = (key) => (e) => {
    setForm((f) => ({ ...f, [key]: e.target.value }))
    setErrors((er) => ({ ...er, [key]: undefined }))
    setFormError('')
  }

  const submit = async (e) => {
    e.preventDefault()
    const errs = {}
    if (!form.diagnosis.trim()) errs.diagnosis = 'Enter the diagnosis.'
    setErrors(errs)
    if (Object.keys(errs).length) return

    setSaving(true)
    setFormError('')
    try {
      if (consultation) await updateConsultation(consultation.id, form)
      else await addConsultation({ appointmentId: appointment.id, ...form })
      onSaved?.(consultation ? 'Consultation updated' : 'Consultation saved')
      onClose()
    } catch (err) {
      setSaving(false)
      setFormError(err.message)
    }
  }

  return (
    <Modal
      open={open}
      onClose={onClose}
      title={consultation ? 'Edit consultation' : 'Record consultation'}
      footer={
        <>
          <Button variant="ghost" onClick={onClose}>Close</Button>
          <Button type="submit" form="consultation-form" disabled={saving}>
            {saving ? 'Saving…' : consultation ? 'Save changes' : 'Save consultation'}
          </Button>
        </>
      }
    >
      <p className="mb-4 text-[0.92rem] text-ink-soft">
        {appointment ? (
          <>
            <strong className="text-ink">{appointment.patient.name}</strong> · {appointment.date} at{' '}
            {appointment.time}
          </>
        ) : null}
      </p>

      <form id="consultation-form" onSubmit={submit} noValidate className="grid gap-4">
        <Field
          id="consult-diagnosis"
          label="Diagnosis"
          data-autofocus
          value={form.diagnosis}
          onChange={set('diagnosis')}
          error={errors.diagnosis}
          placeholder="What did you find?"
        />
        <div className="grid gap-2">
          <label htmlFor="consult-treatment" className="text-[0.92rem] font-bold text-ink">Treatment</label>
          <textarea
            id="consult-treatment"
            rows={2}
            value={form.treatment}
            onChange={set('treatment')}
            placeholder="Prescription, procedure or advice"
            className={textarea}
          />
        </div>
        <div className="grid gap-2">
          <label htmlFor="consult-notes" className="text-[0.92rem] font-bold text-ink">Notes</label>
          <textarea
            id="consult-notes"
            rows={3}
            value={form.notes}
            onChange={set('notes')}
            placeholder="Observations, follow-up, anything the next visit needs"
            className={textarea}
          />
        </div>

        {formError && <Alert error>{formError}</Alert>}
      </form>

      {history.length > 0 && (
        <div className="mt-5 border-t border-line pt-4">
          <h3 className="mb-2 text-[0.95rem] font-extrabold">Earlier consultations for this patient</h3>
          <ul className="grid gap-2.5">
            {history.slice(0, 4).map((c) => (
              <li key={c.id} className="rounded-xl bg-field px-3.5 py-2.5 text-[0.9rem]">
                <strong>{c.date || 'Undated'}</strong>
                {c.time ? ` · ${c.time}` : ''} — {c.diagnosis}
                {c.treatment ? <span className="block text-ink-soft">{c.treatment}</span> : null}
              </li>
            ))}
          </ul>
        </div>
      )}
    </Modal>
  )
}
