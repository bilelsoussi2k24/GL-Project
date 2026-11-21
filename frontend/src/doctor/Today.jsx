import { useEffect, useMemo, useState } from 'react'
import { Link, useNavigate, useOutletContext } from 'react-router-dom'
import { AnimatePresence, motion } from 'motion/react'
import { useDoctorData, byDateTime } from './DoctorData.jsx'
import ConsultationModal from './ConsultationModal.jsx'
import { useToast } from '../components/Toast.jsx'
import useCountUp from '../hooks/useCountUp.js'
import {
  addDays, formatDayMonth, formatLong, fromISODate, fromMinutes, greeting, startOfWeek,
  toISODate, todayISO, toMinutes, weekdayShort,
} from '../utils/date.js'
import { PlusIcon, CalendarIcon, RotateIcon, UserIcon, CheckIcon } from '../components/Icons.jsx'
import { Badge, Button, PageHeader, Panel, PillButton, QuickItem, cx, fadeUp, textLinkClass } from '../components/ui.jsx'

function useNow(intervalMs = 30_000) {
  const [now, setNow] = useState(() => new Date())
  useEffect(() => {
    const id = setInterval(() => setNow(new Date()), intervalMs)
    return () => clearInterval(id)
  }, [intervalMs])
  return now
}

const visitsLabel = (n) => (n === 0 ? 'Free' : n === 1 ? '1 visit' : `${n} visits`)

const statCard =
  'relative grid min-h-32 content-start gap-2 overflow-hidden rounded-[18px] border border-line bg-surface p-5.5 transition duration-300 hover:-translate-y-1 hover:shadow-lift'

export default function Today() {
  const { appointments, profile, updateAppointment, consultationsFor } = useDoctorData()
  const { openAppointment } = useOutletContext()
  const toast = useToast()
  const navigate = useNavigate()
  const now = useNow()
  const today = todayISO()
  const [selected, setSelected] = useState(today)
  const [justSeen, setJustSeen] = useState(null)
  const [consult, setConsult] = useState({ open: false, appointment: null, key: 0 })
  const openConsultation = (appointment) =>
    setConsult((c) => ({ open: true, appointment, key: c.key + 1 }))
  const closeConsultation = () => setConsult((c) => ({ ...c, open: false }))
  const existingNote = consult.appointment
    ? consultationsFor(consult.appointment.patientId).find(
        (c) => c.appointmentId === consult.appointment.id,
      )
    : null

  const active = useMemo(() => appointments.filter((a) => a.status !== 'cancelled'), [appointments])
  const todays = active.filter((a) => a.date === today).sort(byDateTime)
  const seen = todays.filter((a) => a.status === 'completed').length
  const left = todays.filter((a) => a.status === 'scheduled')
  const next = left[0]

  const countToday = useCountUp(todays.length)
  const countSeen = useCountUp(seen)

  const week = useMemo(() => {
    const start = startOfWeek(fromISODate(today))
    return Array.from({ length: 7 }, (_, i) => {
      const d = addDays(start, i)
      const iso = toISODate(d)
      return { iso, d, count: active.filter((a) => a.date === iso).length }
    })
  }, [active, today])

  // Hour rows from the profile's working hours, e.g. 08:30–16:00 → 08:00 … 16:00.
  const hours = useMemo(() => {
    const from = Math.floor(toMinutes(profile.firstAppointment) / 60)
    const to = Math.floor(toMinutes(profile.lastAppointment) / 60)
    return Array.from({ length: Math.max(1, to - from + 1) }, (_, i) => from + i)
  }, [profile.firstAppointment, profile.lastAppointment])

  const dayAppts = active.filter((a) => a.date === selected).sort(byDateTime)
  const nowMins = now.getHours() * 60 + now.getMinutes()

  const markSeen = async (a) => {
    try {
      await updateAppointment(a.id, { status: 'completed' })
      setJustSeen(a.id)
      toast(`Marked ${a.patient.name} as seen`, {
        action: {
          label: 'Add notes',
          onClick: () => openConsultation({ ...a, status: 'completed' }),
        },
      })
    } catch (err) {
      toast(err.message, { tone: 'error' })
    }
  }

  // "Dr. Okafor" style greeting once the doctor has entered a name in Profile.
  const lastName = profile.name.replace(/^Dr\.?\s*/i, '').trim().split(/\s+/).slice(-1)[0]

  return (
    <div className="grid max-w-280 gap-6">
      <PageHeader
        title={lastName ? `${greeting(now)}, Dr. ${lastName}` : greeting(now)}
        sub={`${formatLong(now)} · ${
          todays.length === 0 ? 'No appointments today.' : left.length === 0 ? 'No patients left today.' : `You have ${left.length} patient${left.length === 1 ? '' : 's'} left today.`
        }`}
      >
        <Button withRipple onClick={() => openAppointment()}>
          <PlusIcon />
          New appointment
        </Button>
      </PageHeader>

      <section className="grid gap-4.5 sm:grid-cols-2 lg:grid-cols-3">
        <motion.div {...fadeUp(0.12)} className={statCard}>
          <span className="text-ink-soft">Appointments today</span>
          <span className="text-[2rem] font-extrabold tabular-nums">{countToday}</span>
        </motion.div>
        <motion.div {...fadeUp(0.18)} className={statCard}>
          <span className="text-ink-soft">Seen so far</span>
          <span className="text-[2rem] font-extrabold tabular-nums">{countSeen}</span>
          {todays.length > 0 && (
            <span aria-hidden="true" className="absolute inset-x-5.5 bottom-5 h-1.5 overflow-hidden rounded-full bg-primary-soft">
              <motion.span
                className="block h-full rounded-full bg-ok-mid"
                initial={{ width: 0 }}
                animate={{ width: `${(seen / todays.length) * 100}%` }}
                transition={{ type: 'spring', stiffness: 80, damping: 18, delay: 0.3 }}
              />
            </span>
          )}
        </motion.div>
        <motion.div
          {...fadeUp(0.24)}
          className="group relative grid min-h-32 content-start gap-2 overflow-hidden rounded-[18px] bg-primary p-5.5 text-white transition duration-300 hover:-translate-y-1 hover:shadow-lift sm:col-span-2 lg:col-span-1"
        >
          <span aria-hidden="true" className="absolute -right-10 -bottom-15 size-40 rounded-full bg-white/7 transition-transform duration-700 group-hover:scale-130" />
          <span className="text-sky">Next patient</span>
          <AnimatePresence mode="wait">
            <motion.div
              key={next?.id ?? 'done'}
              initial={{ opacity: 0, x: 24 }}
              animate={{ opacity: 1, x: 0 }}
              exit={{ opacity: 0, x: -24 }}
              transition={{ duration: 0.3 }}
              className="grid gap-2"
            >
              <strong className="text-[1.6rem] font-extrabold">{next ? next.patient.name : todays.length ? 'All done' : 'No patients'}</strong>
              <span className="text-[#e2ebf5]">{next ? `${next.time} · ${next.reason}` : todays.length ? 'No more patients today.' : 'Nothing booked for today.'}</span>
            </motion.div>
          </AnimatePresence>
        </motion.div>
      </section>

      <div className="grid items-start gap-5 xl:grid-cols-[minmax(0,1fr)_350px]">
        <Panel delay={0.3}>
          <div className="flex items-start justify-between gap-3">
            <div>
              <h2 className="text-xl font-extrabold">My schedule</h2>
              <p className="mt-1 text-[0.92rem] text-ink-soft">
                {formatDayMonth(fromISODate(selected))}
                {selected === today && ' · Today'}
              </p>
            </div>
            <Link to="/doctor/appointments" className={textLinkClass}>All appointments</Link>
          </div>

          {/* Week strip: the blue tile glides to the selected day */}
          <div className="mt-5 mb-4.5 grid grid-cols-7 gap-1 sm:gap-2" role="tablist" aria-label="Choose a day">
            {week.map(({ iso, d, count }, i) => {
              const isSel = selected === iso
              return (
                <motion.button
                  key={iso}
                  type="button"
                  role="tab"
                  aria-selected={isSel}
                  onClick={() => setSelected(iso)}
                  initial={{ opacity: 0, y: 12 }}
                  animate={{ opacity: 1, y: 0 }}
                  transition={{ delay: 0.35 + i * 0.04 }}
                  whileHover={isSel ? undefined : { y: -3 }}
                  whileTap={{ scale: 0.95 }}
                  className={cx(
                    'relative grid justify-items-center gap-0.5 rounded-xl border-[1.5px] px-0.5 py-2 transition-colors duration-300 sm:px-1 sm:py-3',
                    isSel ? 'border-primary text-white' : 'border-line bg-surface text-ink hover:border-primary/40',
                  )}
                >
                  {isSel && (
                    <motion.span
                      layoutId="week-selected"
                      transition={{ type: 'spring', stiffness: 420, damping: 32 }}
                      className="absolute -inset-[1.5px] rounded-xl bg-primary shadow-[0_10px_18px_-12px_rgb(47_93_138/0.9)]"
                    />
                  )}
                  {iso === today && !isSel && <span className="absolute top-1.75 right-1.75 size-1.5 rounded-full bg-orange" />}
                  <span className="relative text-[0.85rem]">{weekdayShort(d)}</span>
                  <span className="relative text-[1.35rem] font-extrabold">{d.getDate()}</span>
                  <span className={cx('relative text-[0.65rem] sm:text-xs', isSel ? 'text-[#e2ebf5]' : 'text-muted')}>{visitsLabel(count)}</span>
                </motion.button>
              )
            })}
          </div>

          <ol key={selected}>
            {hours.map((h, i) => {
              const inHour = dayAppts.filter((a) => Math.floor(toMinutes(a.time) / 60) === h)
              const showNow = selected === today && Math.floor(nowMins / 60) === h
              return (
                <motion.li
                  key={h}
                  initial={{ opacity: 0, y: 10 }}
                  animate={{ opacity: 1, y: 0 }}
                  transition={{ delay: i * 0.04, duration: 0.35 }}
                  className="grid min-h-14.25 grid-cols-[56px_minmax(0,1fr)] border-t border-line sm:grid-cols-[78px_minmax(0,1fr)]"
                >
                  <span className="pt-2.5 text-[0.88rem] font-bold">{fromMinutes(h * 60)}</span>
                  <div className="relative grid gap-2 py-2">
                    {showNow && (
                      <span
                        className="pointer-events-none absolute right-0 -left-14 z-[2] h-0.5 bg-er sm:-left-19.5"
                        style={{ top: `${((nowMins % 60) / 60) * 100}%` }}
                        aria-label={`Current time ${fromMinutes(nowMins)}`}
                      >
                        <span className="absolute -top-1 left-12 size-2.5 animate-pulse-ring rounded-full bg-er text-er sm:left-17.5" />
                      </span>
                    )}
                    {inHour.length === 0 ? (
                      <span className="pt-0.5 text-[0.85rem] text-muted">Free</span>
                    ) : (
                      inHour.map((a) => (
                        <motion.div
                          key={a.id}
                          layout
                          whileHover={{ x: 4 }}
                          className={cx(
                            'relative grid grid-cols-[minmax(0,1fr)_auto] items-center gap-3 overflow-hidden rounded-xl border px-3.5 py-2.5 transition-[box-shadow,background-color,border-color] duration-300 hover:shadow-lift sm:grid-cols-[106px_minmax(0,1fr)_auto]',
                            a.status === 'completed' && 'border-line bg-[#f4f7fa]',
                            a.status === 'missed' && 'border-[#f6dcc4] bg-[#fff7f0]',
                            a.status === 'scheduled' && 'border-[#dbe5f0] bg-[#eef3f9]',
                          )}
                        >
                          {justSeen === a.id && a.status === 'completed' && (
                            // Green flash when the visit is marked as seen
                            <motion.span
                              aria-hidden="true"
                              initial={{ opacity: 1 }}
                              animate={{ opacity: 0 }}
                              transition={{ duration: 1 }}
                              className="pointer-events-none absolute inset-0 bg-ok-soft"
                            />
                          )}
                          <span className="relative col-span-2 flex gap-1.5 leading-snug sm:col-span-1 sm:grid sm:gap-0">
                            <strong className="text-primary">{a.time}</strong>
                            <small className="text-[0.82rem] text-ink-soft">to {fromMinutes(toMinutes(a.time) + (a.duration ?? profile.visitLength))}</small>
                          </span>
                          <span className="relative grid min-w-0 leading-snug">
                            <strong className={cx('truncate', a.status === 'completed' && 'text-ink-soft')}>{a.patient.name}</strong>
                            <small className="truncate text-[0.82rem] text-ink-soft">{a.reason}</small>
                          </span>
                          <AnimatePresence mode="wait" initial={false}>
                            {a.status === 'completed' && (
                              <motion.span
                                key="seen"
                                initial={{ scale: 0.3, opacity: 0 }}
                                animate={{ scale: 1, opacity: 1 }}
                                transition={{ type: 'spring', stiffness: 500, damping: 15 }}
                                className="inline-flex items-center gap-2"
                              >
                                <Badge tone="done"><CheckIcon width={14} height={14} />Seen</Badge>
                                <PillButton onClick={() => openConsultation(a)}>
                                  {consultationsFor(a.patientId).some((c) => c.appointmentId === a.id)
                                    ? 'Consultation'
                                    : 'Add notes'}
                                </PillButton>
                              </motion.span>
                            )}
                            {a.status === 'missed' && <Badge key="missed" tone="missed">Missed</Badge>}
                            {a.status === 'scheduled' && (
                              <motion.span key="btn" exit={{ scale: 0.8, opacity: 0 }}>
                                <PillButton onClick={() => markSeen(a)}>Mark as seen</PillButton>
                              </motion.span>
                            )}
                          </AnimatePresence>
                        </motion.div>
                      ))
                    )}
                  </div>
                </motion.li>
              )
            })}
          </ol>
        </Panel>

        <Panel delay={0.36} className="grid gap-3">
          <h2 className="mb-1.5 text-xl font-extrabold">Quick actions</h2>
          <QuickItem icon={CalendarIcon} onClick={() => openAppointment()}>Book a patient</QuickItem>
          <QuickItem icon={RotateIcon} onClick={() => navigate('/doctor/appointments')}>Reschedule a visit</QuickItem>
          <QuickItem icon={UserIcon} onClick={() => navigate('/doctor/profile#hours')}>Update my working hours</QuickItem>
        </Panel>
      </div>

      {consult.key !== 0 && (
        <ConsultationModal
          key={consult.key}
          open={consult.open}
          appointment={consult.appointment}
          consultation={existingNote}
          onClose={closeConsultation}
          onSaved={(msg) => toast(msg)}
        />
      )}
    </div>
  )
}
