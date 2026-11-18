import { useEffect, useRef, useState } from 'react'
import {
  AnimatePresence, motion, useMotionValue, useScroll, useSpring, useTransform,
} from 'motion/react'
import { departments } from '../config/hospital.js'
import { useHospital, visitingLabel } from '../services/hospital.js'
import { ripple } from '../hooks/ripple.js'
import {
  PlusIcon, PlusSquareIcon, ClockIcon, PhoneIcon, CarIcon, NavIcon, CopyIcon, CheckIcon,
} from '../components/Icons.jsx'
import { EASE, cx, revealUp } from '../components/ui.jsx'

const steps = [
  { title: 'Call or visit reception.', text: 'Bring an ID and your hospital card if you have one.' },
  { title: 'A doctor schedules you.', text: 'They choose the right department and time.' },
  { title: 'You get a confirmation.', text: 'By SMS or a call from our team.' },
]

const container = 'mx-auto w-[min(1200px,100%-32px)]'
const spring = { type: 'spring', stiffness: 260, damping: 22 }

function toMinutes(hhmm) {
  if (!hhmm) return null
  const [h, m] = hhmm.split(':').map(Number)
  return h * 60 + m
}

// true/false while visiting hours are set, null otherwise. Re-checks every minute.
function useVisitingOpen(startHHMM, endHHMM) {
  const [now, setNow] = useState(() => new Date())
  useEffect(() => {
    const id = setInterval(() => setNow(new Date()), 60_000)
    return () => clearInterval(id)
  }, [])
  const start = toMinutes(startHHMM)
  const end = toMinutes(endHHMM)
  if (start == null || end == null) return null
  const mins = now.getHours() * 60 + now.getMinutes()
  return mins >= start && mins < end
}

/* ---------- Decorative hero background ---------- */

const floaters = [
  { left: '6%', top: '18%', size: 22, delay: 0, slow: false },
  { left: '88%', top: '12%', size: 30, delay: 1.2, slow: true },
  { left: '72%', top: '46%', size: 16, delay: 0.6, slow: false },
  { left: '44%', top: '8%', size: 14, delay: 2, slow: true },
  { left: '94%', top: '70%', size: 20, delay: 0.3, slow: false },
]

function HeroBackground() {
  return (
    <div aria-hidden="true" className="pointer-events-none absolute inset-0 -z-10 overflow-hidden">
      {/* Dot grid */}
      <div className="absolute inset-0 bg-[radial-gradient(#2f5d8a1a_1px,transparent_1px)] [background-size:22px_22px] [mask-image:linear-gradient(to_bottom,black,transparent_70%)]" />
      {/* Drifting color blobs */}
      <motion.div
        className="absolute -top-40 -right-32 size-[520px] rounded-full bg-primary-soft blur-3xl"
        animate={{ x: [0, -40, 0], y: [0, 30, 0], scale: [1, 1.08, 1] }}
        transition={{ duration: 16, repeat: Infinity, ease: 'easeInOut' }}
      />
      <motion.div
        className="absolute top-32 -left-40 size-[420px] rounded-full bg-peach/70 blur-3xl"
        animate={{ x: [0, 50, 0], y: [0, -20, 0] }}
        transition={{ duration: 18, repeat: Infinity, ease: 'easeInOut' }}
      />
      {/* Floating medical crosses */}
      {floaters.map((f, i) => (
        <motion.span
          key={i}
          className={cx('absolute text-primary/15', f.slow ? 'animate-float-slow' : 'animate-float')}
          style={{ left: f.left, top: f.top, animationDelay: `${f.delay}s` }}
          initial={{ opacity: 0, scale: 0 }}
          animate={{ opacity: 1, scale: 1 }}
          transition={{ delay: 0.8 + f.delay / 2, ...spring }}
        >
          <PlusIcon width={f.size} height={f.size} strokeWidth={3} />
        </motion.span>
      ))}
    </div>
  )
}

/* ---------- Map with 3D tilt, parallax, route and moving ambulance ---------- */

// Route legs follow the road centers (roads are 14px wide at 17% and 48%, the lower road at 80%).
const dashV = 'w-1 bg-[repeating-linear-gradient(to_bottom,#e36b4f_0_8px,transparent_8px_14px)]'
const dashH = 'h-1 bg-[repeating-linear-gradient(to_right,#e36b4f_0_8px,transparent_8px_14px)]'
const routeLegs = [
  // Up the left road from the bottom edge
  { axis: 'scaleY', className: cx(dashV, 'bottom-0 left-[calc(17%+5px)] top-[calc(80%+5px)] origin-bottom') },
  // Along the lower road to the middle road
  { axis: 'scaleX', className: cx(dashH, 'left-[calc(17%+5px)] top-[calc(80%+5px)] w-[calc(31%+4px)] origin-left') },
  // Up the middle road to the pin
  { axis: 'scaleY', className: cx(dashV, 'left-[calc(48%+5px)] top-[calc(36%+44px)] bottom-[calc(20%-9px)] origin-bottom') },
]

function MapCard({ hospital, directionsUrl }) {
  const mx = useMotionValue(0)
  const my = useMotionValue(0)
  const sx = useSpring(mx, { stiffness: 120, damping: 18 })
  const sy = useSpring(my, { stiffness: 120, damping: 18 })
  const rotateX = useTransform(sy, (v) => v * -6)
  const rotateY = useTransform(sx, (v) => v * 8)
  const far = { x: useTransform(sx, (v) => v * -10), y: useTransform(sy, (v) => v * -10) }
  const mid = { x: useTransform(sx, (v) => v * -22), y: useTransform(sy, (v) => v * -22) }
  const near = { x: useTransform(sx, (v) => v * 14), y: useTransform(sy, (v) => v * 14) }

  const onMove = (e) => {
    const r = e.currentTarget.getBoundingClientRect()
    mx.set((e.clientX - r.left) / r.width - 0.5)
    my.set((e.clientY - r.top) / r.height - 0.5)
  }
  const onLeave = () => {
    mx.set(0)
    my.set(0)
  }

  return (
    <motion.article
      initial={{ opacity: 0, y: 30 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.8, ease: EASE, delay: 0.5 }}
      style={{ rotateX, rotateY, transformPerspective: 1200 }}
      onPointerMove={onMove}
      onPointerLeave={onLeave}
      className="flex flex-col overflow-hidden rounded-[18px] border border-line bg-surface transition-shadow duration-300 hover:shadow-lift"
    >
      <div
        role="img"
        aria-label={`Map showing ${hospital.name}`}
        className="relative min-h-75 flex-1 cursor-crosshair overflow-hidden bg-map sm:min-h-95"
      >
        {/* Roads */}
        <motion.div style={far} className="absolute -inset-5">
          <span className="absolute inset-y-0 left-[17%] w-3.5 bg-white" />
          <span className="absolute inset-y-0 left-[48%] w-3.5 bg-white" />
          <span className="absolute inset-x-0 top-[45%] h-6.5 bg-white" />
          <span className="absolute inset-x-0 top-[80%] h-3.5 bg-white" />
          {/* Dashed route that draws itself along the roads to the pin, one leg after another */}
          {routeLegs.map((leg, i) => (
            <motion.span
              key={i}
              aria-hidden="true"
              className={cx('absolute rounded-full opacity-85', leg.className)}
              initial={{ [leg.axis]: 0 }}
              animate={{ [leg.axis]: 1 }}
              transition={{ delay: 1.7 + i * 0.45, duration: 0.45, ease: 'easeInOut' }}
            />
          ))}
          {/* Ambulance driving along the main road */}
          <motion.span
            className="absolute top-[45%] mt-1.5 grid h-3.5 w-6 place-items-center rounded-[4px] bg-white shadow ring-1 ring-er/40"
            animate={{ left: ['-6%', '104%'] }}
            transition={{ duration: 9, repeat: Infinity, ease: 'linear', repeatDelay: 1.5 }}
          >
            <span className="h-2 w-0.5 bg-er" />
            <span className="absolute h-0.5 w-2 bg-er" />
            <motion.span
              className="absolute -top-1 left-1 size-1.5 rounded-full bg-[#3b82f6]"
              animate={{ opacity: [1, 0.2, 1] }}
              transition={{ duration: 0.6, repeat: Infinity }}
            />
          </motion.span>
        </motion.div>

        {/* Park and buildings */}
        <motion.div style={mid} className="absolute -inset-5">
          <span className="absolute top-[14%] left-[9%] size-22.5 animate-breathe rounded-full bg-park" />
          <span className="absolute top-[14%] left-[66%] h-20 w-25 rounded-lg bg-block transition-colors duration-300 sm:w-40" />
          <span className="absolute top-[56%] left-[30%] h-16 w-30 rounded-lg bg-block" />
        </motion.div>


        {/* Pin */}
        <motion.div style={near} className="absolute -inset-5">
          <div className="absolute top-[36%] left-[calc(48%+7px)] -translate-x-1/2">
            <motion.span
              initial={{ opacity: 0, y: 10, scale: 0.8 }}
              animate={{ opacity: 1, y: 0, scale: 1 }}
              transition={{ delay: 1.5, ...spring }}
              className="absolute -top-11 left-1/2 -translate-x-1/2 rounded-lg bg-ink px-3 py-1.5 text-[0.9rem] font-bold whitespace-nowrap text-white after:absolute after:-bottom-1 after:left-1/2 after:size-2.5 after:-translate-x-1/2 after:rotate-45 after:rounded-[2px] after:bg-inherit"
            >
              {hospital.name}
            </motion.span>
            {[0, 0.7].map((d) => (
              <motion.span
                key={d}
                className="absolute top-10 left-1/2 -ml-3 h-2.5 w-6 rounded-[50%] bg-pin/45"
                initial={{ opacity: 0, scale: 0.6 }}
                animate={{ opacity: [0.9, 0], scale: [0.6, 3.4] }}
                transition={{ duration: 2, repeat: Infinity, delay: 1.6 + d, ease: 'easeOut' }}
              />
            ))}
            <motion.svg
              viewBox="0 0 24 32"
              aria-hidden="true"
              className="relative h-12 w-9 fill-pin drop-shadow-[0_6px_6px_rgb(150_50_30/0.3)]"
              style={{ originY: 1 }}
              initial={{ y: -140, opacity: 0 }}
              animate={{ y: [-140, 0, -14, 0], opacity: 1, scaleY: [1, 0.86, 1.04, 1] }}
              transition={{ delay: 0.9, duration: 1, times: [0, 0.55, 0.75, 1], ease: 'easeOut' }}
            >
              <path d="M12 0C5.4 0 0 5.2 0 11.7 0 20.3 12 32 12 32s12-11.7 12-20.3C24 5.2 18.6 0 12 0z" />
              <circle cx="12" cy="11.5" r="4.5" fill="#fff" />
            </motion.svg>
          </div>
        </motion.div>

        <span className="absolute bottom-3.5 left-3.5 rounded-md bg-white px-2.5 py-1 text-[0.8rem] text-ink-soft">
          [Map: embed Google Maps here]
        </span>
      </div>

      <div className="flex flex-wrap items-center justify-between gap-4 px-6 pt-5 pb-7">
        <address className="grid gap-1 text-ink-soft not-italic">
          <strong className="text-[1.1rem] text-ink">{hospital.street}</strong>
          <span>{hospital.cityState}</span>
        </address>
        <MagneticButton href={directionsUrl}>
          <NavIcon className="transition-transform duration-300 ease-spring group-hover:translate-x-0.75 group-hover:-translate-y-0.75 group-hover:rotate-12" />
          Get directions
        </MagneticButton>
      </div>
    </motion.article>
  )
}

// A link that leans toward the cursor while hovered.
function MagneticButton({ href, children }) {
  const x = useSpring(0, { stiffness: 300, damping: 15 })
  const y = useSpring(0, { stiffness: 300, damping: 15 })
  return (
    <motion.a
      href={href}
      target="_blank"
      rel="noreferrer"
      style={{ x, y }}
      onPointerMove={(e) => {
        const r = e.currentTarget.getBoundingClientRect()
        x.set((e.clientX - r.left - r.width / 2) * 0.25)
        y.set((e.clientY - r.top - r.height / 2) * 0.35)
      }}
      onPointerLeave={() => {
        x.set(0)
        y.set(0)
      }}
      onPointerDown={ripple}
      whileTap={{ scale: 0.95 }}
      className="group relative inline-flex w-full items-center justify-center gap-2.5 overflow-hidden rounded-full bg-primary px-5.5 py-3 font-bold text-white shadow-btn transition-[background-color,box-shadow] hover:bg-primary-dark hover:shadow-btn-hover sm:w-auto"
    >
      {children}
    </motion.a>
  )
}

/* ---------- Info cards ---------- */

const cardIn = (i) => ({
  initial: { opacity: 0, x: 40 },
  animate: { opacity: 1, x: 0 },
  transition: { delay: 0.6 + i * 0.1, ...spring },
})

const infoCard =
  'group grid gap-2.5 rounded-[18px] border border-line bg-surface px-5.5 py-5 transition duration-300 hover:-translate-y-1 hover:border-primary/35 hover:shadow-lift'
const iconWrap = 'grid place-items-center text-primary transition-transform duration-400 ease-spring group-hover:scale-115 group-hover:-rotate-8'

function CopyButton({ value, label, copied, onCopy }) {
  return (
    <button
      type="button"
      onClick={() => onCopy(value, label)}
      className="group/copy -ml-1.5 inline-flex items-center gap-2 rounded-md px-1.5 py-0.5 text-ink-soft transition-colors hover:bg-primary-soft hover:text-primary focus-visible:bg-primary-soft"
    >
      {value}
      <AnimatePresence mode="wait" initial={false}>
        <motion.span
          key={copied ? 'ok' : 'copy'}
          initial={{ scale: 0, rotate: -45 }}
          animate={{ scale: 1, rotate: 0 }}
          exit={{ scale: 0 }}
          className={cx('transition-opacity', copied ? 'text-ok' : 'opacity-0 group-hover/copy:opacity-100 group-focus-visible/copy:opacity-100')}
        >
          {copied ? <CheckIcon width={14} height={14} /> : <CopyIcon />}
        </motion.span>
      </AnimatePresence>
    </button>
  )
}

/* ---------- Page ---------- */

export default function VisitUs() {
  const hospital = useHospital()
  const toastTimer = useRef(null)
  const stepsRef = useRef(null)
  const [toast, setToast] = useState(null)
  const [copied, setCopied] = useState(null)
  const [activeDept, setActiveDept] = useState(null)
  const visitingOpen = useVisitingOpen(hospital.visiting.start, hospital.visiting.end)

  // Steps connector fills as the steps scroll through the viewport.
  const { scrollYProgress } = useScroll({ target: stepsRef, offset: ['start 85%', 'end 55%'] })
  const lineScale = useSpring(scrollYProgress, { stiffness: 120, damping: 25 })

  useEffect(() => () => clearTimeout(toastTimer.current), [])

  const showToast = (msg) => {
    clearTimeout(toastTimer.current)
    setToast({ msg, key: Date.now() })
    toastTimer.current = setTimeout(() => {
      setToast(null)
      setCopied(null)
    }, 2200)
  }

  const copy = async (value, label) => {
    try {
      await navigator.clipboard.writeText(value)
      setCopied(label)
      showToast(`${label} copied to clipboard`)
    } catch {
      showToast(`Could not copy the ${label.toLowerCase()}`)
    }
  }

  const directionsUrl = `https://www.google.com/maps/search/?api=1&query=${encodeURIComponent(
    `${hospital.name} ${hospital.street} ${hospital.cityState}`,
  )}`

  const selected = departments.find((d) => d.name === activeDept)
  const [firstWord, ...nameWords] = hospital.name.split(' ')

  return (
    <div>
      {/* ---------- Hero ---------- */}
      <section className="relative isolate border-b border-line py-9 sm:py-14">
        <HeroBackground />
        <div className={container}>
          <motion.span
            initial={{ opacity: 0, y: -10, scale: 0.9 }}
            animate={{ opacity: 1, y: 0, scale: 1 }}
            transition={spring}
            className="inline-flex items-center gap-2 rounded-full bg-peach px-3.5 py-2 text-[0.85rem] font-bold text-peach-ink"
          >
            <span className="relative flex size-2">
              <span className="absolute inline-flex size-full animate-ping rounded-full bg-orange opacity-70" />
              <span className="relative inline-flex size-2 rounded-full bg-orange" />
            </span>
            Patients and visitors
          </motion.span>

          <h1
            className="mt-3.5 text-[clamp(2.2rem,5vw,3.6rem)] leading-[1.1] font-extrabold tracking-tight"
            aria-label={`Visit ${hospital.name}`}
          >
            {['Visit', firstWord, ...nameWords].map((w, i) => (
              <motion.span
                key={`${w}-${i}`}
                aria-hidden="true"
                className={cx(
                  'mr-[0.25em] inline-block',
                  i > 0 && 'animate-gradient bg-gradient-to-r from-ink via-primary to-ink bg-[length:200%_auto] bg-clip-text text-transparent',
                )}
                initial={{ opacity: 0, y: '0.6em', rotateX: -70, filter: 'blur(6px)' }}
                animate={{ opacity: 1, y: 0, rotateX: 0, filter: 'blur(0px)' }}
                transition={{ delay: 0.12 + i * 0.1, type: 'spring', stiffness: 200, damping: 18 }}
                style={{ transformPerspective: 600 }}
              >
                {w}
              </motion.span>
            ))}
          </h1>

          <motion.p
            initial={{ opacity: 0, y: 14 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ delay: 0.45, duration: 0.6, ease: EASE }}
            className="mt-4.5 max-w-160 text-[1.2rem] leading-relaxed text-ink-soft"
          >
            Everything you need before you come in: where we are, when to visit and who to call. No account needed.
          </motion.p>

          <div className="mt-12 grid items-stretch gap-6 lg:grid-cols-[minmax(0,1fr)_320px]">
            <MapCard hospital={hospital} directionsUrl={directionsUrl} />

            <aside className="grid content-start gap-4">
              {/* Emergency */}
              <motion.a
                {...cardIn(0)}
                href={`tel:${hospital.erNumber}`}
                onPointerDown={ripple}
                whileHover={{ y: -4 }}
                whileTap={{ scale: 0.98 }}
                className="group relative grid animate-er-glow gap-2.5 overflow-hidden rounded-[18px] bg-er px-5.5 py-5 text-white transition-colors hover:bg-er-dark"
              >
                <span aria-hidden="true" className="absolute inset-y-0 left-0 w-1/3 animate-shimmer bg-gradient-to-r from-transparent via-white/20 to-transparent" />
                <span className="relative flex items-center gap-2.5 text-[1.2rem] font-extrabold">
                  <PlusSquareIcon className="animate-heartbeat" width={20} height={20} />
                  Emergency, open 24/7
                </span>
                <span className="relative font-bold">Call {hospital.erNumber}</span>
              </motion.a>

              {/* Visiting hours */}
              <motion.div {...cardIn(1)} className={infoCard}>
                <span className="flex items-center gap-2.5 text-[1.05rem] font-bold">
                  <span className={iconWrap}>
                    <ClockIcon className="[&_.clock-hand]:origin-center [&_.clock-hand]:transition-transform [&_.clock-hand]:duration-700 group-hover:[&_.clock-hand]:rotate-360" />
                  </span>
                  Visiting hours
                  {visitingOpen != null && (
                    <motion.span
                      initial={{ scale: 0 }}
                      animate={{ scale: 1 }}
                      transition={{ delay: 1.1, ...spring }}
                      className={cx(
                        'ml-auto inline-flex items-center gap-1.5 rounded-full px-2.5 py-0.5 text-xs font-bold',
                        visitingOpen ? 'bg-ok-soft text-ok' : 'bg-[#f1f2f5] text-muted',
                      )}
                    >
                      {visitingOpen && <span className="size-1.5 animate-pulse-ring rounded-full bg-ok text-ok" />}
                      {visitingOpen ? 'Open now' : 'Closed'}
                    </motion.span>
                  )}
                </span>
                <span className="leading-normal text-ink-soft">
                  {visitingLabel(hospital)}
                  <br />
                  Up to {hospital.visiting.maxVisitors} visitors per patient
                </span>
              </motion.div>

              {/* Reception */}
              <motion.div {...cardIn(2)} className={infoCard}>
                <span className="flex items-center gap-2.5 text-[1.05rem] font-bold">
                  <span className={cx(iconWrap, 'group-hover:animate-[heartbeat_0.8s_ease-in-out]')}><PhoneIcon /></span>
                  Reception
                </span>
                <span className="grid justify-items-start gap-0.5">
                  <CopyButton value={hospital.phone} label="Phone number" copied={copied === 'Phone number'} onCopy={copy} />
                  <CopyButton value={hospital.email} label="Email" copied={copied === 'Email'} onCopy={copy} />
                </span>
              </motion.div>

              {/* Parking */}
              <motion.div {...cardIn(3)} className={infoCard}>
                <span className="flex items-center gap-2.5 text-[1.05rem] font-bold">
                  <span className="grid place-items-center text-primary transition-transform duration-500 group-hover:translate-x-1.5">
                    <CarIcon />
                  </span>
                  Parking
                </span>
                <span className="text-ink-soft">{hospital.parking}</span>
              </motion.div>
            </aside>
          </div>
        </div>
      </section>

      {/* ---------- Appointments + departments ---------- */}
      <section className="bg-surface pt-16 pb-18">
        <div className={cx(container, 'grid gap-10 md:grid-cols-2 md:gap-12')}>
          <div>
            <SectionTitle>How to get an appointment</SectionTitle>
            <ol ref={stepsRef} className="relative grid gap-4">
              {/* Track + animated fill behind the numbers */}
              <span aria-hidden="true" className="absolute top-4 bottom-4 left-4 w-0.5 -translate-x-1/2 rounded bg-peach" />
              <motion.span
                aria-hidden="true"
                style={{ scaleY: lineScale }}
                className="absolute top-4 bottom-4 left-4 w-0.5 origin-top -translate-x-1/2 rounded bg-orange"
              />
              {steps.map((s, i) => (
                <motion.li key={s.title} {...revealUp(0.1 + i * 0.12)} className="group relative flex items-start gap-3.5 leading-relaxed text-ink-soft">
                  <motion.span
                    initial={{ scale: 0 }}
                    whileInView={{ scale: 1 }}
                    viewport={{ once: true }}
                    transition={{ delay: 0.2 + i * 0.15, type: 'spring', stiffness: 400, damping: 14 }}
                    className="relative grid size-8.5 shrink-0 place-items-center rounded-full bg-peach font-bold text-peach-ink ring-4 ring-surface transition duration-300 ease-spring group-hover:scale-115 group-hover:bg-orange group-hover:text-white"
                  >
                    {i + 1}
                  </motion.span>
                  <p className="pt-1 transition-transform duration-300 group-hover:translate-x-1">
                    <strong className="text-ink">{s.title}</strong> {s.text}
                  </p>
                </motion.li>
              ))}
            </ol>
          </div>

          <div>
            <SectionTitle>Our departments</SectionTitle>
            <div className="flex flex-wrap gap-2.5" role="group" aria-label="Departments">
              {departments.map((d, i) => {
                const active = activeDept === d.name
                const er = d.name === 'Emergency'
                return (
                  <motion.button
                    key={d.name}
                    type="button"
                    aria-pressed={active}
                    onClick={() => setActiveDept(active ? null : d.name)}
                    initial={{ opacity: 0, scale: 0.6, y: 10 }}
                    whileInView={{ opacity: 1, scale: 1, y: 0 }}
                    viewport={{ once: true }}
                    transition={{ delay: 0.05 + i * 0.05, type: 'spring', stiffness: 380, damping: 20 }}
                    whileHover={{ y: -3 }}
                    whileTap={{ scale: 0.94 }}
                    className={cx(
                      'relative rounded-full bg-primary-soft px-4 py-2.25 font-bold transition-colors duration-300',
                      active ? 'text-white' : 'text-primary-ink hover:shadow-[0_8px_16px_-10px_rgb(31_58_90/0.6)]',
                    )}
                  >
                    {active && (
                      <motion.span
                        layoutId="dept-highlight"
                        transition={{ type: 'spring', stiffness: 420, damping: 32 }}
                        className={cx('absolute inset-0 rounded-full', er ? 'bg-er' : 'bg-primary')}
                      />
                    )}
                    <span className="relative">{d.name}</span>
                  </motion.button>
                )
              })}
            </div>
            <div className="mt-4.5 min-h-13" aria-live="polite">
              <AnimatePresence mode="wait">
                <motion.p
                  key={activeDept ?? 'none'}
                  initial={{ opacity: 0, y: 8 }}
                  animate={{ opacity: 1, y: 0 }}
                  exit={{ opacity: 0, y: -8 }}
                  transition={{ duration: 0.25 }}
                  className="leading-relaxed text-ink-soft"
                >
                  {selected ? (
                    <>
                      <strong className="text-ink">{selected.name}:</strong> {selected.info} Ask at reception and we&apos;ll
                      point you to its wing.
                    </>
                  ) : (
                    <>Each department sits in a marked wing. Ask at reception and we&apos;ll point you the right way.</>
                  )}
                </motion.p>
              </AnimatePresence>
            </div>
          </div>
        </div>
      </section>

      <div className="pointer-events-none fixed inset-x-0 bottom-7 z-[100] flex justify-center" role="status" aria-live="polite">
        <AnimatePresence>
          {toast && (
            <motion.div
              key={toast.key}
              initial={{ opacity: 0, y: 50, scale: 0.9 }}
              animate={{ opacity: 1, y: 0, scale: 1 }}
              exit={{ opacity: 0, y: 30 }}
              transition={{ type: 'spring', stiffness: 380, damping: 26 }}
              className="inline-flex items-center gap-2 rounded-xl bg-ink px-4.5 py-3 font-semibold text-white shadow-lift"
            >
              <CheckIcon width={16} height={16} className="text-[#7fd6a2]" />
              {toast.msg}
            </motion.div>
          )}
        </AnimatePresence>
      </div>
    </div>
  )
}

// Heading with an underline that draws in when it scrolls into view.
function SectionTitle({ children }) {
  return (
    <motion.h2 {...revealUp(0)} className="relative mb-5 inline-block text-[1.9rem] font-extrabold">
      {children}
      <motion.span
        aria-hidden="true"
        initial={{ scaleX: 0 }}
        whileInView={{ scaleX: 1 }}
        viewport={{ once: true }}
        transition={{ delay: 0.35, duration: 0.7, ease: EASE }}
        className="absolute -bottom-1 left-0 h-1 w-14 origin-left rounded-full bg-gradient-to-r from-orange to-amber"
      />
    </motion.h2>
  )
}
