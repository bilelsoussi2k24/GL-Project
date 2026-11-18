import { useState } from 'react'
import { useOutletContext } from 'react-router-dom'
import { AnimatePresence, motion } from 'motion/react'
import { useDoctorData } from './DoctorData.jsx'
import Modal from '../components/Modal.jsx'
import Highlight from '../components/Highlight.jsx'
import { useToast } from '../components/Toast.jsx'
import { initials } from '../utils/date.js'
import { PlusIcon, PencilIcon, XCircleIcon, SearchIcon, CalendarIcon } from '../components/Icons.jsx'
import {
  Avatar, Badge, Button, EmptyState, PageHeader, Panel, PillButton, SearchBox, cx, rowMotion, tbl, textLinkClass,
} from '../components/ui.jsx'

const statusLabel = { scheduled: 'Scheduled', completed: 'Completed', missed: 'Missed', cancelled: 'Cancelled' }

export default function Appointments() {
  const { appointments, updateAppointment } = useDoctorData()
  const { openAppointment } = useOutletContext()
  const toast = useToast()
  const [query, setQuery] = useState('')
  // Kept after closing so the dialog text stays during its exit animation.
  const [confirm, setConfirm] = useState({ open: false, appt: null })

  const q = query.trim()
  const rows = appointments.filter((a) => {
    if (!q) return true
    const hay = [a.patient.name, a.patient.hhId, a.patient.phone, a.reason, a.date, a.time, statusLabel[a.status]]
      .join(' ')
      .toLowerCase()
    return hay.includes(q.toLowerCase())
  })

  const closeConfirm = () => setConfirm((c) => ({ ...c, open: false }))

  const confirmCancel = () => {
    const a = confirm.appt
    const previous = a.status
    updateAppointment(a.id, { status: 'cancelled' })
    closeConfirm()
    toast(`Cancelled ${a.patient.name}'s appointment`, {
      action: { label: 'Undo', onClick: () => updateAppointment(a.id, { status: previous }) },
    })
  }

  return (
    <div className="grid max-w-280 gap-6">
      <PageHeader
        title="My appointments"
        sub={`${q ? `${rows.length} of ` : ''}${appointments.length} ${appointments.length === 1 ? 'appointment' : 'appointments'}${q ? '' : ' in total'}`}
      >
        <SearchBox value={query} onChange={setQuery} placeholder="Search patient or date" label="Search appointments" />
        <Button withRipple className="max-sm:flex-1" onClick={() => openAppointment()}>
          <PlusIcon />
          New appointment
        </Button>
      </PageHeader>

      <Panel delay={0.18} className="overflow-hidden p-0!">
        {rows.length > 0 && (
        <table className={tbl.table}>
          <thead className={tbl.thead}>
            <tr>
              <th className={tbl.th}>Patient</th>
              <th className={tbl.th}>Reason</th>
              <th className={tbl.th}>Date and time</th>
              <th className={tbl.th}>Status</th>
              <th className={cx(tbl.th, 'text-right')}>Actions</th>
            </tr>
          </thead>
          <tbody className={tbl.tbody}>
            <AnimatePresence initial={true}>
              {rows.map((a, i) => {
                const cancelled = a.status === 'cancelled'
                return (
                  <motion.tr key={a.id} {...rowMotion(i)} className={cx(tbl.tr, 'group')}>
                    <td className={cx(tbl.td, cancelled && 'opacity-55')}>
                      <span className="flex items-center gap-3">
                        <Avatar className="group-hover:scale-108 group-hover:bg-[#d4e2f0]">{initials(a.patient.name)}</Avatar>
                        <span className="grid leading-snug">
                          <strong><Highlight text={a.patient.name} query={q} /></strong>
                          <small className="text-[0.85rem] text-ink-soft">
                            <Highlight text={[a.patient.hhId, a.patient.phone].filter(Boolean).join(' · ')} query={q} />
                          </small>
                        </span>
                      </span>
                    </td>
                    <td className={cx(tbl.td, tbl.tdSub, cancelled && 'line-through opacity-55')}>
                      <Highlight text={a.reason} query={q} />
                    </td>
                    <td className={cx(tbl.td, tbl.tdSub, 'whitespace-nowrap', cancelled && 'opacity-55')}>
                      <Highlight text={a.date} query={q} /> · <Highlight text={a.time} query={q} />
                    </td>
                    <td className={cx(tbl.td, tbl.tdSub)}>
                      <AnimatePresence mode="wait" initial={false}>
                        <motion.span
                          key={a.status}
                          className="inline-block"
                          initial={{ scale: 0.5, opacity: 0 }}
                          animate={{ scale: 1, opacity: 1 }}
                          exit={{ scale: 0.5, opacity: 0 }}
                          transition={{ type: 'spring', stiffness: 500, damping: 20 }}
                        >
                          <Badge tone={a.status}>{statusLabel[a.status]}</Badge>
                        </motion.span>
                      </AnimatePresence>
                    </td>
                    <td className={cx(tbl.td, tbl.actions)}>
                      <span className="inline-flex gap-2">
                        <PillButton onClick={() => openAppointment(a)}>
                          <PencilIcon />Edit
                        </PillButton>
                        <PillButton
                          danger
                          onClick={() => setConfirm({ open: true, appt: a })}
                          disabled={cancelled || a.status === 'completed'}
                        >
                          <XCircleIcon />Cancel
                        </PillButton>
                      </span>
                    </td>
                  </motion.tr>
                )
              })}
            </AnimatePresence>
          </tbody>
        </table>
        )}

        {rows.length === 0 && (
          <EmptyState icon={q ? SearchIcon : CalendarIcon}>
            {q ? (
              <>
                <p>No appointments match “{q}”.</p>
                <button type="button" className={textLinkClass} onClick={() => setQuery('')}>Clear search</button>
              </>
            ) : (
              <>
                <p>No appointments yet.</p>
                <button type="button" className={textLinkClass} onClick={() => openAppointment()}>Book the first one</button>
              </>
            )}
          </EmptyState>
        )}
      </Panel>

      <Modal
        open={confirm.open}
        onClose={closeConfirm}
        title="Cancel this appointment?"
        size="sm"
        footer={
          <>
            <Button variant="ghost" onClick={closeConfirm} data-autofocus>Keep it</Button>
            <Button variant="danger" onClick={confirmCancel}>Cancel appointment</Button>
          </>
        }
      >
        {confirm.appt && (
          <p className="leading-relaxed text-ink-soft">
            <strong className="text-ink">{confirm.appt.patient.name}</strong> · {confirm.appt.reason}
            <br />
            {confirm.appt.date} at {confirm.appt.time}. We&apos;ll mark it as cancelled. You can undo this right after.
          </p>
        )}
      </Modal>
    </div>
  )
}
