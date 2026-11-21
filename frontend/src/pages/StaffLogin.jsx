import { useRef, useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { AnimatePresence, motion } from 'motion/react'
import { useHospital } from '../services/hospital.js'
import { login } from '../services/auth.js'
import {
  PlusIcon, StethoscopeIcon, ShieldIcon, InfoIcon, EyeIcon, EyeOffIcon,
} from '../components/Icons.jsx'
import { Button, FieldError, Spinner, cx, fadeUp, textLinkClass } from '../components/ui.jsx'

const roles = {
  doctor: { label: 'Doctor', placeholder: 'you@havenhospital.local', home: '/doctor' },
  admin: { label: 'Administrator', placeholder: 'you@havenhospital.local', home: '/admin' },
}

const loginInput =
  'h-13 w-full rounded-xl border-[1.5px] border-line bg-surface px-4 text-ink transition duration-200 placeholder:text-[#8b91a3] ' +
  'hover:border-line-strong focus:border-primary focus:ring-4 focus:ring-primary/16 focus:outline-none focus:placeholder:opacity-55 ' +
  'aria-invalid:border-er aria-invalid:ring-4 aria-invalid:ring-er/12'

const headline = ['One', 'login', 'for', 'the', 'whole', 'care', 'team.']

function RoleCard({ active, onClick, icon: Icon, title, text, delay }) {
  return (
    <motion.button
      type="button"
      onClick={onClick}
      {...fadeUp(delay)}
      whileHover={{ y: -4 }}
      whileTap={{ scale: 0.98 }}
      className={cx(
        'group grid content-start gap-2.5 rounded-2xl border-[1.5px] px-5 py-5.5 text-left text-white backdrop-blur-[2px] transition-[background-color,border-color,box-shadow] duration-300',
        active
          ? 'border-white/45 bg-white/16 shadow-[0_14px_30px_-18px_rgb(0_0_0/0.5)]'
          : 'border-transparent bg-white/8 hover:bg-white/13',
      )}
    >
      <span className="flex items-center gap-2.5 text-[1.1rem] font-extrabold">
        <motion.span
          key={String(active)}
          initial={active ? { scale: 1.4, rotate: -15 } : false}
          animate={{ scale: 1, rotate: 0 }}
          transition={{ type: 'spring', stiffness: 400, damping: 12 }}
          className="text-amber"
        >
          <Icon />
        </motion.span>
        {title}
      </span>
      <span className="text-[0.95rem] leading-relaxed text-sky">{text}</span>
    </motion.button>
  )
}

export default function StaffLogin() {
  const hospital = useHospital()
  const navigate = useNavigate()
  const location = useLocation()
  const panelRef = useRef(null)
  // When redirected here from a protected page, preselect its role and return there after login.
  const [role, setRole] = useState(location.state?.role === 'admin' ? 'admin' : 'doctor')
  const [form, setForm] = useState({ email: '', password: '', remember: false })
  const [errors, setErrors] = useState({})
  const [formError, setFormError] = useState('')
  const [showPw, setShowPw] = useState(false)
  const [capsOn, setCapsOn] = useState(false)
  const [loading, setLoading] = useState(false)
  const [shaking, setShaking] = useState(false)
  const [forgotOpen, setForgotOpen] = useState(false)

  const update = (field) => (e) => {
    const value = e.target.type === 'checkbox' ? e.target.checked : e.target.value
    setForm((f) => ({ ...f, [field]: value }))
    if (errors[field]) setErrors((er) => ({ ...er, [field]: undefined }))
    setFormError('')
  }

  // Arrow keys move between roles, like a native radio group.
  const onRoleKey = (e) => {
    if (['ArrowLeft', 'ArrowRight', 'ArrowUp', 'ArrowDown'].includes(e.key)) {
      e.preventDefault()
      const next = role === 'doctor' ? 'admin' : 'doctor'
      setRole(next)
      document.getElementById(`role-${next}`)?.focus()
    }
  }

  const onSubmit = async (e) => {
    e.preventDefault()
    const errs = {}
    if (!/^\S+@\S+\.\S+$/.test(form.email.trim())) errs.email = 'Enter your hospital email address.'
    if (!form.password) errs.password = 'Enter your password.'
    setErrors(errs)
    if (Object.keys(errs).length) {
      setShaking(true)
      document.getElementById(errs.email ? 'email' : 'password')?.focus()
      return
    }
    setLoading(true)
    try {
      await login({ role, email: form.email.trim(), password: form.password, remember: form.remember })
      const from = location.state?.from
      navigate(from && from.startsWith(roles[role].home) ? from : roles[role].home, { replace: true })
    } catch (err) {
      setFormError(err.message || 'Sign in failed. Try again.')
      setShaking(true)
      setLoading(false)
    }
  }

  // Soft light that follows the pointer on the blue panel.
  const onPanelMove = (e) => {
    const el = panelRef.current
    if (!el) return
    const r = el.getBoundingClientRect()
    el.style.setProperty('--sx', `${e.clientX - r.left}px`)
    el.style.setProperty('--sy', `${e.clientY - r.top}px`)
  }

  return (
    <div className="grid min-h-screen lg:grid-cols-2">
      {/* ---------- Left: brand panel ---------- */}
      <section
        ref={panelRef}
        onPointerMove={onPanelMove}
        className="relative isolate flex min-w-0 flex-col overflow-hidden bg-primary px-6 pt-8 pb-10 text-white [--sx:50%] [--sy:40%] lg:px-14 lg:py-12"
      >
        <span aria-hidden="true" className="pointer-events-none absolute inset-0 -z-10 bg-[radial-gradient(420px_circle_at_var(--sx)_var(--sy),rgb(255_255_255/0.09),transparent_70%)]" />
        <span aria-hidden="true" className="pointer-events-none absolute -right-50 -bottom-70 -z-10 size-130 animate-drift rounded-full bg-primary-light" />
        <motion.span
          aria-hidden="true"
          className="pointer-events-none absolute top-[28%] -left-15 -z-10 size-45 rounded-full bg-primary-light/35"
          animate={{ x: [0, 24, 0], y: [0, 30, 0] }}
          transition={{ duration: 11, repeat: Infinity, ease: 'easeInOut' }}
        />
        {[{ l: '78%', t: '14%', s: 26 }, { l: '62%', t: '58%', s: 16 }, { l: '88%', t: '40%', s: 18 }].map((f, i) => (
          <span
            key={i}
            aria-hidden="true"
            className="pointer-events-none absolute -z-10 animate-float text-white/10"
            style={{ left: f.l, top: f.t, animationDelay: `${i * 1.3}s` }}
          >
            <PlusIcon width={f.s} height={f.s} strokeWidth={3} />
          </span>
        ))}

        <motion.div {...fadeUp(0)}>
          <Link to="/" className="group inline-flex items-center gap-3 text-xl font-extrabold lg:text-2xl">
            <span className="grid size-11 place-items-center rounded-[10px] bg-white text-primary transition duration-500 ease-spring group-hover:rotate-90 group-hover:scale-105">
              <PlusIcon width={22} height={22} strokeWidth={2.4} />
            </span>
            {hospital.name}
          </Link>
        </motion.div>

        <div className="mt-auto pt-10 lg:pt-16">
          <motion.span
            initial={{ opacity: 0, scale: 0.6 }}
            animate={{ opacity: 1, scale: 1 }}
            transition={{ delay: 0.1, type: 'spring', stiffness: 300, damping: 16 }}
            className="inline-block rounded-full bg-amber px-3.5 py-1.75 text-[0.85rem] font-bold text-amber-ink"
          >
            Staff portal
          </motion.span>
          <h1 className="mt-4.5 max-w-115 text-[clamp(2.2rem,3.6vw,3rem)] leading-[1.12] font-extrabold tracking-tight" aria-label={headline.join(' ')}>
            {headline.map((w, i) => (
              <motion.span
                key={i}
                aria-hidden="true"
                className="mr-[0.25em] inline-block"
                initial={{ opacity: 0, y: '0.5em', filter: 'blur(4px)' }}
                animate={{ opacity: 1, y: 0, filter: 'blur(0px)' }}
                transition={{ delay: 0.18 + i * 0.06, duration: 0.7, ease: [0.22, 1, 0.36, 1] }}
              >
                {w}
              </motion.span>
            ))}
          </h1>
          <motion.p {...fadeUp(0.6)} className="mt-5.5 max-w-120 text-[1.15rem] leading-relaxed text-[#dbe6f2]">
            Doctors and administrators sign in here. You&apos;ll land on the dashboard for your role.
          </motion.p>
        </div>

        <div className="mt-auto grid gap-4 pt-8 sm:grid-cols-2 lg:pt-16">
          <RoleCard
            active={role === 'doctor'}
            onClick={() => setRole('doctor')}
            icon={StethoscopeIcon}
            title="Doctors"
            text="Create appointments, see today's patients and record consultation notes."
            delay={0.7}
          />
          <RoleCard
            active={role === 'admin'}
            onClick={() => setRole('admin')}
            icon={ShieldIcon}
            title="Administrators"
            text="Manage doctors, staff accounts, patients and hospital settings."
            delay={0.78}
          />
        </div>
      </section>

      {/* ---------- Right: form ---------- */}
      <section className="grid min-w-0 place-items-center bg-canvas px-6 py-12">
        <form
          onSubmit={onSubmit}
          onAnimationEnd={(e) => e.animationName === 'shake' && setShaking(false)}
          noValidate
          className={cx('grid w-full max-w-105', shaking && 'animate-shake')}
        >
          <motion.h2 {...fadeUp(0.15)} className="text-[2rem] font-extrabold">Staff login</motion.h2>
          <motion.p {...fadeUp(0.22)} className="mt-2.5 text-ink-soft">
            Choose your role, then sign in with your hospital email and password.
          </motion.p>

          {/* Role toggle: the blue pill slides between options */}
          <motion.div
            {...fadeUp(0.3)}
            role="radiogroup"
            aria-label="Role"
            onKeyDown={onRoleKey}
            className="mt-5.5 grid grid-cols-2 rounded-full bg-primary-soft p-1.5"
          >
            {Object.entries(roles).map(([key, r]) => (
              <button
                key={key}
                id={`role-${key}`}
                type="button"
                role="radio"
                aria-checked={role === key}
                tabIndex={role === key ? 0 : -1}
                onClick={() => setRole(key)}
                className={cx(
                  'relative rounded-full px-3 py-2.75 font-bold transition-colors duration-300',
                  role === key ? 'text-white' : 'text-ink-soft hover:text-primary',
                )}
              >
                {role === key && (
                  <motion.span
                    layoutId="role-pill"
                    transition={{ type: 'spring', stiffness: 420, damping: 30 }}
                    className="absolute inset-0 rounded-full bg-primary shadow-[0_6px_14px_-8px_rgb(47_93_138/0.8)]"
                  />
                )}
                <span className="relative">{r.label}</span>
              </button>
            ))}
          </motion.div>

          <motion.div {...fadeUp(0.38)} className="mt-6 grid gap-2">
            <label htmlFor="email" className="text-[0.95rem] font-bold">Hospital email</label>
            <input
              id="email"
              name="email"
              type="email"
              autoComplete="username"
              placeholder={roles[role].placeholder}
              value={form.email}
              onChange={update('email')}
              aria-invalid={!!errors.email}
              aria-describedby={errors.email ? 'email-error' : undefined}
              className={loginInput}
            />
            <FieldError id="email-error">{errors.email}</FieldError>
          </motion.div>

          <motion.div {...fadeUp(0.44)} className="mt-6 grid gap-2">
            <label htmlFor="password" className="text-[0.95rem] font-bold">Password</label>
            <div className="relative">
              <input
                id="password"
                name="password"
                type={showPw ? 'text' : 'password'}
                autoComplete="current-password"
                placeholder="Enter your password"
                value={form.password}
                onChange={update('password')}
                onKeyUp={(e) => setCapsOn(e.getModifierState?.('CapsLock'))}
                onBlur={() => setCapsOn(false)}
                aria-invalid={!!errors.password}
                aria-describedby={errors.password ? 'password-error' : undefined}
                className={cx(loginInput, 'pr-13')}
              />
              <button
                type="button"
                onClick={() => setShowPw((s) => !s)}
                aria-label={showPw ? 'Hide password' : 'Show password'}
                aria-pressed={showPw}
                className="absolute top-1/2 right-2 grid size-9.5 -translate-y-1/2 place-items-center rounded-lg text-muted transition-colors hover:bg-primary-soft hover:text-primary"
              >
                <AnimatePresence mode="wait" initial={false}>
                  <motion.span
                    key={String(showPw)}
                    initial={{ opacity: 0, rotate: -90, scale: 0.5 }}
                    animate={{ opacity: 1, rotate: 0, scale: 1 }}
                    exit={{ opacity: 0, rotate: 90, scale: 0.5 }}
                    transition={{ duration: 0.15 }}
                  >
                    {showPw ? <EyeOffIcon /> : <EyeIcon />}
                  </motion.span>
                </AnimatePresence>
              </button>
            </div>
            {capsOn && (
              <span className="animate-in text-[0.88rem] font-semibold text-peach-ink fade-in slide-in-from-top-1">Caps Lock is on</span>
            )}
            <FieldError id="password-error">{errors.password}</FieldError>
          </motion.div>

          <motion.div {...fadeUp(0.5)} className="mt-5 flex flex-wrap items-center justify-between gap-3">
            <label className="group inline-flex cursor-pointer items-center gap-2.5 text-ink-soft select-none">
              <input type="checkbox" checked={form.remember} onChange={update('remember')} className="peer sr-only" />
              <span
                aria-hidden="true"
                className={cx(
                  'relative size-4.5 rounded border-[1.5px] transition-colors duration-200 group-hover:border-primary',
                  'peer-focus-visible:outline-3 peer-focus-visible:outline-offset-2 peer-focus-visible:outline-primary/45',
                  form.remember ? 'border-primary bg-primary' : 'border-[#7d8496] bg-white',
                )}
              >
                <svg
                  viewBox="0 0 24 24"
                  className="absolute inset-0 m-auto size-3.5 text-white"
                  fill="none"
                  stroke="currentColor"
                  strokeWidth="3.5"
                  strokeLinecap="round"
                  strokeLinejoin="round"
                >
                  <motion.path
                    d="M5 12l5 5L20 7"
                    initial={false}
                    animate={{ pathLength: form.remember ? 1 : 0 }}
                    transition={{ duration: 0.25 }}
                  />
                </svg>
              </span>
              Keep me signed in
            </label>
            <button type="button" className={textLinkClass} aria-expanded={forgotOpen} onClick={() => setForgotOpen((o) => !o)}>
              Forgot password?
            </button>
          </motion.div>

          <AnimatePresence initial={false}>
            {forgotOpen && (
              <motion.p
                initial={{ height: 0, opacity: 0, marginTop: 0 }}
                animate={{ height: 'auto', opacity: 1, marginTop: 10 }}
                exit={{ height: 0, opacity: 0, marginTop: 0 }}
                className="overflow-hidden text-[0.9rem] text-ink-soft"
              >
                Ask your hospital administrator to reset it, or call reception on {hospital.phone}.
              </motion.p>
            )}
          </AnimatePresence>

          {formError && (
            <p role="alert" className="mt-4 animate-in text-[0.88rem] font-semibold text-er fade-in slide-in-from-top-1">
              {formError}
            </p>
          )}

          <motion.div {...fadeUp(0.56)} className="mt-6">
            <Button type="submit" withRipple disabled={loading} className="h-13 w-full text-[1.05rem] disabled:cursor-progress">
              {/* Plain label: it must always match the selected role, even before animations run. */}
              <span className="inline-flex items-center gap-2.5" aria-live="polite">
                {loading ? (
                  <>
                    <Spinner />
                    Signing in…
                  </>
                ) : (
                  `Sign in as ${role === 'doctor' ? 'doctor' : 'administrator'}`
                )}
              </span>
            </Button>
          </motion.div>

          <motion.div {...fadeUp(0.64)} className="mt-5 flex items-start gap-3 rounded-[14px] bg-peach px-4.5 py-4 text-[0.9rem] leading-normal text-[#5a3a1e]">
            <InfoIcon className="mt-0.5 shrink-0 text-peach-ink" />
            <p>
              <strong className="text-[#3d2410]">Are you a patient?</strong> You don&apos;t need an account. See{' '}
              <Link to="/" className={cx(textLinkClass, 'text-peach-ink!')}>visiting information</Link>{' '}
              or call {hospital.phone}.
            </p>
          </motion.div>

          <motion.div {...fadeUp(0.7)} className="mt-7 justify-self-center">
            <Link to="/" className={textLinkClass}>Back to homepage</Link>
          </motion.div>
        </form>
      </section>
    </div>
  )
}
