import { useState } from 'react'
import { Link, useNavigate, useOutletContext } from 'react-router-dom'
import { AnimatePresence, motion } from 'motion/react'
import { useAdminData, statusLabel } from './AdminData.jsx'
import { useToast } from '../components/Toast.jsx'
import useCountUp from '../hooks/useCountUp.js'
import { formatLong, greeting, initials } from '../utils/date.js'
import { StethoscopeIcon, CheckIcon, GearIcon, MapPinIcon, UserIcon, CalendarIcon } from '../components/Icons.jsx'
import { Avatar, Badge, EmptyState, PageHeader, Panel, QuickItem, cx, fadeUp, textLinkClass } from '../components/ui.jsx'

const tones = {
  blue: 'text-primary',
  green: 'text-[#2f8a57]',
  peach: 'text-peach-ink',
}

function Stat({ icon: Icon, value, label, tone, delay }) {
  const n = useCountUp(value)
  return (
    <motion.div
      {...fadeUp(delay)}
      className="group grid min-h-42.5 content-start gap-1.5 rounded-[18px] border border-line bg-surface p-5.5 transition duration-300 hover:-translate-y-1 hover:shadow-lift"
    >
      <motion.span
        initial={{ scale: 0, rotate: -45 }}
        animate={{ scale: 1, rotate: 0 }}
        transition={{ delay: delay + 0.15, type: 'spring', stiffness: 300, damping: 14 }}
        className={cx(
          'mb-3.5 grid size-11 place-items-center rounded-xl bg-primary-soft transition-transform duration-500 ease-spring group-hover:scale-110 group-hover:-rotate-8',
          tones[tone],
        )}
      >
        <Icon />
      </motion.span>
      <span className="text-[2rem] leading-none font-extrabold tabular-nums">{n}</span>
      <span className="text-ink-soft">{label}</span>
    </motion.div>
  )
}

const activityIcon = { appointment: CalendarIcon, consultation: StethoscopeIcon, account: UserIcon }

function whenLabel(iso) {
  if (!iso) return ''
  const date = new Date(iso)
  if (Number.isNaN(date.getTime())) return iso
  return date.toLocaleString('en-GB', { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' })
}

export default function Overview() {
  const { doctors, stats, activity, account, updateDoctor } = useAdminData()
  const { openDoctor } = useOutletContext()
  const toast = useToast()
  const navigate = useNavigate()
  const [now] = useState(() => new Date())

  const onLeave = doctors.filter((d) => d.status === 'leave')
  const inactive = doctors.filter((d) => d.status === 'inactive')

  const markBack = async (d) => {
    try {
      await updateDoctor(d.id, { status: 'active' })
      toast(`${d.name} is back to active`)
    } catch (err) {
      toast(err.message, { tone: 'error' })
    }
  }

  return (
    <div className="grid max-w-280 gap-6">
      <PageHeader
        title={`${greeting(now)}, ${account.name.trim().split(' ')[0] || 'admin'}`}
        sub={`${formatLong(now)} · ${doctors.length ? "Here's your hospital today." : 'Add your first doctor to get started.'}`}
      />

      <section className="grid gap-4.5 sm:grid-cols-2 xl:grid-cols-4">
        <Stat icon={StethoscopeIcon} value={stats?.doctors ?? 0} label="Doctors on staff" tone="blue" delay={0.1} />
        <Stat icon={UserIcon} value={stats?.patients ?? 0} label="Patients registered" tone="green" delay={0.15} />
        <Stat icon={CalendarIcon} value={stats?.appointments ?? 0} label="Appointments booked" tone="peach" delay={0.2} />
        <Stat icon={CheckIcon} value={stats?.consultations ?? 0} label="Consultations recorded" tone="blue" delay={0.25} />
      </section>

      <div className="grid items-start gap-5 xl:grid-cols-[minmax(0,1fr)_360px]">
        <div className="grid gap-5">
          <Panel delay={0.3}>
            <div className="flex items-start justify-between gap-3">
              <h2 className="text-xl font-extrabold">Doctors</h2>
              <Link to="/admin/doctors" className={textLinkClass}>Manage doctors</Link>
            </div>
            <p className="mt-1 text-[0.9rem] text-ink-soft">
              {stats?.activeDoctors ?? 0} active · {stats?.doctorsOnLeave ?? 0} on leave
              {inactive.length > 0 ? ` · ${inactive.length} deactivated` : ''}
            </p>
            <ul className="mt-2.5">
              <AnimatePresence initial={true}>
                {doctors.map((d, i) => (
                  <motion.li
                    key={d.id}
                    layout="position"
                    initial={{ opacity: 0, x: -12 }}
                    animate={{ opacity: 1, x: 0, transition: { delay: 0.35 + i * 0.05 } }}
                    exit={{ opacity: 0 }}
                    className="border-b border-line"
                  >
                    <button
                      type="button"
                      onClick={() => openDoctor(d)}
                      aria-label={`Edit ${d.name}`}
                      className="group flex w-full items-center gap-3 rounded-xl px-1.5 py-2.5 text-left transition-all duration-300 hover:bg-field hover:pl-3"
                    >
                      <Avatar className="group-hover:scale-108">{initials(d.name)}</Avatar>
                      <span className="grid flex-1 leading-snug">
                        <strong>{d.name}</strong>
                        <small className="text-[0.85rem] text-ink-soft">{d.specialty}</small>
                      </span>
                      <AnimatePresence mode="wait" initial={false}>
                        <motion.span key={d.status} initial={{ scale: 0.5, opacity: 0 }} animate={{ scale: 1, opacity: 1 }} exit={{ scale: 0.5, opacity: 0 }}>
                          <Badge tone={d.status}>{statusLabel[d.status] ?? d.status}</Badge>
                        </motion.span>
                      </AnimatePresence>
                    </button>
                  </motion.li>
                ))}
              </AnimatePresence>
            </ul>
            {doctors.length === 0 && (
              <EmptyState icon={StethoscopeIcon}>
                <p>No doctors yet.</p>
                <button type="button" className={textLinkClass} onClick={() => openDoctor()}>Add the first one</button>
              </EmptyState>
            )}
          </Panel>

          <Panel delay={0.36}>
            <h2 className="text-xl font-extrabold">Recent activity</h2>
            <p className="mt-1 text-[0.9rem] text-ink-soft">Appointments, consultations and new accounts.</p>
            {activity.length === 0 ? (
              <p className="mt-3.5 text-ink-soft">Nothing has happened yet.</p>
            ) : (
              <ul className="mt-3.5 grid gap-3">
                {activity.map((event, i) => {
                  const Icon = activityIcon[event.type] ?? CalendarIcon
                  return (
                    <motion.li
                      key={`${event.type}-${event.at}-${i}`}
                      initial={{ opacity: 0, y: 8 }}
                      animate={{ opacity: 1, y: 0 }}
                      transition={{ delay: 0.4 + i * 0.04 }}
                      className="flex items-start gap-3 border-b border-line pb-3 last:border-0 last:pb-0"
                    >
                      <span className="mt-0.5 grid size-9 shrink-0 place-items-center rounded-xl bg-primary-soft text-primary">
                        <Icon width={18} height={18} />
                      </span>
                      <span className="grid gap-0.5 leading-snug">
                        <strong className="text-[0.95rem]">{event.title}</strong>
                        <small className="text-[0.85rem] text-ink-soft">{event.detail}</small>
                        <small className="text-[0.8rem] text-muted">{whenLabel(event.at)}</small>
                      </span>
                    </motion.li>
                  )
                })}
              </ul>
            )}
          </Panel>
        </div>

        <div className="grid gap-5">
          <Panel delay={0.42} className="grid gap-3">
            <h2 className="mb-1.5 text-xl font-extrabold">Quick actions</h2>
            <QuickItem icon={StethoscopeIcon} onClick={() => openDoctor()}>Add a doctor</QuickItem>
            <QuickItem icon={UserIcon} onClick={() => navigate('/admin/patients')}>Register a patient</QuickItem>
            <QuickItem icon={CalendarIcon} onClick={() => navigate('/admin/users')}>Manage accounts</QuickItem>
            <QuickItem icon={GearIcon} onClick={() => navigate('/admin/settings')}>Hospital settings</QuickItem>
            <QuickItem icon={MapPinIcon} as="a" href="/" target="_blank" rel="noreferrer">View public Visit us page</QuickItem>
          </Panel>

          <Panel delay={0.48}>
            <h2 className="text-xl font-extrabold">Needs attention</h2>
            <AnimatePresence mode="popLayout" initial={false}>
              {onLeave.length === 0 ? (
                <motion.p
                  key="clear"
                  initial={{ opacity: 0, scale: 0.9 }}
                  animate={{ opacity: 1, scale: 1 }}
                  className="mt-3.5 flex items-center gap-2 font-semibold text-ok"
                >
                  <motion.span initial={{ rotate: -90, scale: 0 }} animate={{ rotate: 0, scale: 1 }} transition={{ type: 'spring', stiffness: 400, damping: 12, delay: 0.1 }}>
                    <CheckIcon width={16} height={16} />
                  </motion.span>
                  Nothing needs your attention.
                </motion.p>
              ) : (
                <motion.ul key="list" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }} className="mt-3.5 grid gap-3.5">
                  <AnimatePresence initial={false}>
                    {onLeave.map((d) => (
                      <motion.li
                        key={d.id}
                        layout
                        initial={{ opacity: 0, x: 20 }}
                        animate={{ opacity: 1, x: 0 }}
                        exit={{ opacity: 0, x: 20 }}
                        className="flex items-start gap-3"
                      >
                        <span className="mt-2 size-2.25 shrink-0 animate-pulse-ring rounded-full bg-orange text-orange" />
                        <p className="leading-relaxed text-ink-soft">
                          <strong className="text-ink">{d.name} is on leave.</strong> Update the status in Doctors when they&apos;re back.
                          <button type="button" onClick={() => markBack(d)} className={cx(textLinkClass, 'mt-1 block text-[0.9rem]')}>
                            Mark as back
                          </button>
                        </p>
                      </motion.li>
                    ))}
                  </AnimatePresence>
                </motion.ul>
              )}
            </AnimatePresence>
          </Panel>

          <Panel delay={0.54}>
            <h2 className="text-xl font-extrabold">Today</h2>
            <dl className="mt-3 grid gap-2.5 text-[0.95rem]">
              <div className="flex items-baseline justify-between gap-3">
                <dt className="text-ink-soft">Appointments today</dt>
                <dd className="font-extrabold tabular-nums">{stats?.appointmentsToday ?? 0}</dd>
              </div>
              <div className="flex items-baseline justify-between gap-3">
                <dt className="text-ink-soft">Upcoming scheduled</dt>
                <dd className="font-extrabold tabular-nums">{stats?.upcomingAppointments ?? 0}</dd>
              </div>
              <div className="flex items-baseline justify-between gap-3">
                <dt className="text-ink-soft">Completed visits</dt>
                <dd className="font-extrabold tabular-nums">{stats?.completedAppointments ?? 0}</dd>
              </div>
            </dl>
          </Panel>
        </div>
      </div>
    </div>
  )
}
