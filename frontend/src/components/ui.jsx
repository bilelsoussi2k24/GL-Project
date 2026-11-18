/* eslint-disable react-refresh/only-export-components */
import { useEffect, useRef } from 'react'
import { AnimatePresence, motion } from 'motion/react'
import { ripple } from '../hooks/ripple.js'
import { AlertIcon, CheckIcon, ChevronRightIcon, SearchIcon, XIcon } from './Icons.jsx'

// Small Tailwind building blocks shared by every page.

export const cx = (...classes) => classes.filter(Boolean).join(' ')

export const EASE = [0.22, 1, 0.36, 1]

// Spread on a motion element for a fade-and-rise entrance: <motion.h1 {...fadeUp(0.1)}>
export const fadeUp = (delay = 0, y = 18) => ({
  initial: { opacity: 0, y },
  animate: { opacity: 1, y: 0 },
  transition: { duration: 0.6, ease: EASE, delay },
})

// Same, but plays when the element scrolls into view.
export const revealUp = (delay = 0, y = 24) => ({
  initial: { opacity: 0, y },
  whileInView: { opacity: 1, y: 0 },
  viewport: { once: true, margin: '0px 0px -60px 0px' },
  transition: { duration: 0.6, ease: EASE, delay },
})

/* ---------- Buttons ---------- */

const btnBase =
  'relative inline-flex items-center justify-center gap-2.5 overflow-hidden rounded-full font-bold transition duration-200 ' +
  'active:scale-[0.97] disabled:cursor-not-allowed disabled:opacity-75 disabled:active:scale-100'

const btnVariants = {
  primary:
    'bg-primary px-5.5 py-3 text-white shadow-btn hover:-translate-y-0.5 hover:bg-primary-dark hover:shadow-btn-hover disabled:hover:translate-y-0',
  outline: 'border-[1.5px] border-primary px-4.5 py-[11px] font-medium text-primary hover:bg-primary hover:text-white hover:shadow-lift',
  ghost: 'border-[1.5px] border-line bg-surface px-5.5 py-[11px] text-ink hover:border-primary/40 hover:text-primary',
  danger: 'bg-er px-5.5 py-3 text-white hover:-translate-y-px hover:bg-er-dark',
  success: 'bg-[#2f8a57] px-5.5 py-3 text-white shadow-btn',
}

export const buttonClass = (variant = 'primary', className) => cx(btnBase, btnVariants[variant], className)

export function Button({ variant = 'primary', className, withRipple, onPointerDown, ...props }) {
  return (
    <button
      type="button"
      className={buttonClass(variant, className)}
      onPointerDown={(e) => {
        if (withRipple) ripple(e)
        onPointerDown?.(e)
      }}
      {...props}
    />
  )
}

const pillBase =
  'inline-flex items-center gap-1.5 whitespace-nowrap rounded-full border-[1.5px] bg-surface px-3.5 py-2 text-[0.9rem] font-bold transition duration-200 ' +
  'enabled:hover:-translate-y-px enabled:active:scale-95 disabled:cursor-not-allowed disabled:opacity-40'

export function PillButton({ danger, className, ...props }) {
  return (
    <button
      type="button"
      className={cx(
        pillBase,
        danger
          ? 'border-[#f1cfc5] text-er enabled:hover:border-er enabled:hover:bg-er-soft'
          : 'border-line text-primary enabled:hover:border-primary/45 enabled:hover:bg-primary-soft',
        className,
      )}
      {...props}
    />
  )
}

export function IconButton({ className, ...props }) {
  return (
    <button
      type="button"
      className={cx(
        'grid size-9 place-items-center rounded-[10px] text-muted transition duration-300 hover:rotate-90 hover:bg-primary-soft hover:text-primary',
        className,
      )}
      {...props}
    />
  )
}

export const textLinkClass =
  'cursor-pointer font-bold text-primary underline decoration-[1.5px] underline-offset-[3px] transition-all hover:text-primary-dark hover:underline-offset-[5px]'

export function Spinner({ className }) {
  return (
    <span
      aria-hidden="true"
      className={cx('size-4 animate-spin rounded-full border-[2.5px] border-white/35 border-t-white', className)}
    />
  )
}

/* ---------- Avatars and badges ---------- */

export function Avatar({ children, tone = 'blue', size = 'md', className }) {
  return (
    <span
      className={cx(
        'grid shrink-0 place-items-center rounded-full font-extrabold transition duration-300',
        tone === 'peach' ? 'bg-amber text-amber-ink' : 'bg-primary-soft text-[#2a4766]',
        size === 'xl' ? 'size-18 text-2xl' : 'size-10.5 text-[0.9rem]',
        className,
      )}
    >
      {children}
    </span>
  )
}

const badgeTones = {
  completed: 'bg-ok-soft text-ok',
  done: 'bg-ok-soft text-ok',
  active: 'bg-ok-soft text-ok',
  scheduled: 'bg-primary-soft text-[#2a4766]',
  missed: 'bg-peach text-peach-ink',
  leave: 'bg-peach text-peach-ink',
  cancelled: 'bg-[#eef0f3] text-muted',
}

export const badgeClass = (tone, className) =>
  cx(
    'inline-flex items-center gap-1.5 whitespace-nowrap rounded-full px-3 py-1 text-[0.82rem] font-bold',
    badgeTones[tone],
    className,
  )

export function Badge({ tone, className, children }) {
  return <span className={badgeClass(tone, className)}>{children}</span>
}

/* ---------- Form fields ---------- */

export const inputClass =
  'h-11.5 w-full rounded-[10px] border-[1.5px] border-line bg-field px-3.5 text-ink transition duration-200 ' +
  'hover:border-line-strong focus:border-primary focus:bg-white focus:ring-4 focus:ring-primary/15 focus:outline-none ' +
  'aria-invalid:border-er aria-invalid:ring-4 aria-invalid:ring-er/12'

export const labelClass = 'text-[0.92rem] font-bold text-ink'

export function FieldError({ children, id }) {
  if (!children) return null
  return (
    <span
      id={id}
      className="flex animate-in items-center gap-1.5 text-[0.85rem] font-semibold text-er fade-in slide-in-from-top-1"
    >
      <AlertIcon />
      {children}
    </span>
  )
}

// Label + input + error. Extra props go to the <input>.
export function Field({ id, label, error, className, ...input }) {
  return (
    <div className={cx('grid content-start gap-2', className)}>
      <label htmlFor={id} className={labelClass}>{label}</label>
      <input id={id} className={inputClass} aria-invalid={!!error} {...input} />
      <FieldError>{error}</FieldError>
    </div>
  )
}

export function Alert({ error, children, className }) {
  return (
    <p
      role={error ? 'alert' : undefined}
      className={cx(
        'flex animate-in items-start gap-2 rounded-[10px] px-3.5 py-2.5 text-[0.9rem] font-semibold fade-in slide-in-from-top-1',
        error ? 'bg-er-soft text-er' : 'bg-peach text-peach-ink',
        className,
      )}
    >
      <AlertIcon className="mt-[3px] shrink-0" />
      {children}
    </p>
  )
}

/* ---------- Save flow (Profile, Settings) ---------- */

// Pulses while there are unsaved changes, spins while saving, turns green when saved.
export function SaveButton({ dirty, saving, saved, onClick }) {
  return (
    <motion.button
      type="button"
      onClick={onClick}
      disabled={saving}
      animate={
        dirty && !saving
          ? { boxShadow: ['0 0 0 0px rgb(47 93 138 / 0.25)', '0 0 0 7px rgb(47 93 138 / 0)'] }
          : { boxShadow: '0 6px 16px -8px rgb(47 93 138 / 0.7)' }
      }
      transition={dirty && !saving ? { duration: 1.4, repeat: Infinity } : { duration: 0.2 }}
      whileTap={{ scale: 0.96 }}
      className={buttonClass(saved ? 'success' : 'primary', 'max-sm:flex-1')}
    >
      <AnimatePresence mode="wait" initial={false}>
        <motion.span
          key={saving ? 'saving' : saved ? 'saved' : 'idle'}
          initial={{ opacity: 0, y: 8 }}
          animate={{ opacity: 1, y: 0 }}
          exit={{ opacity: 0, y: -8 }}
          transition={{ duration: 0.15 }}
          className="inline-flex items-center gap-2.5"
        >
          {saving ? <Spinner /> : <CheckIcon />}
          {saving ? 'Saving…' : saved ? 'Saved' : 'Save changes'}
        </motion.span>
      </AnimatePresence>
    </motion.button>
  )
}

export function UnsavedBar({ show, onDiscard }) {
  return (
    <AnimatePresence initial={false}>
      {show && (
        <motion.p
          role="status"
          initial={{ opacity: 0, height: 0, marginTop: -24 }}
          animate={{ opacity: 1, height: 'auto', marginTop: -8 }}
          exit={{ opacity: 0, height: 0, marginTop: -24 }}
          transition={{ duration: 0.3, ease: EASE }}
          className="overflow-hidden"
        >
          <span className="flex items-center gap-3 rounded-xl bg-peach px-4 py-2.5 font-semibold text-peach-ink">
            <span className="size-2 animate-pulse-ring rounded-full bg-orange text-orange" />
            You have unsaved changes.
            <button type="button" onClick={onDiscard} className={cx(textLinkClass, 'text-peach-ink!')}>
              Discard
            </button>
          </span>
        </motion.p>
      )}
    </AnimatePresence>
  )
}

/* ---------- Data tables (become stacked cards below md) ---------- */

export const tbl = {
  table: 'w-full border-collapse max-md:block',
  thead: 'max-md:hidden',
  th: 'border-b border-line px-6 py-4 text-left text-[0.85rem] font-bold text-ink',
  tbody: 'max-md:block',
  tr: 'border-t border-line transition-colors first:border-t-0 hover:bg-[#f8fafc] max-md:block max-md:px-4 max-md:py-3.5',
  td: 'px-6 py-3.5 max-md:block max-md:px-0 max-md:py-1',
  // Secondary cells line up under the name on phones
  tdSub: 'max-md:pl-13.5 max-md:text-ink-soft',
  actions: 'text-right max-md:pt-2.5 max-md:pl-13.5 max-md:text-left',
}

// Motion props for table rows: stagger in, fade out when filtered away.
export const rowMotion = (i) => ({
  layout: 'position',
  initial: { opacity: 0, y: 14 },
  animate: { opacity: 1, y: 0, transition: { delay: 0.2 + i * 0.05, duration: 0.4, ease: EASE } },
  exit: { opacity: 0, x: -20, transition: { duration: 0.2 } },
})

export function EmptyState({ icon: Icon = SearchIcon, children }) {
  return (
    <motion.div
      initial={{ opacity: 0, scale: 0.96 }}
      animate={{ opacity: 1, scale: 1 }}
      className="grid justify-items-center gap-2 px-4 py-12 text-center text-ink-soft"
    >
      <motion.span
        animate={{ rotate: [0, -12, 12, 0] }}
        transition={{ duration: 0.6, delay: 0.1 }}
        className="mb-1 grid size-14 place-items-center rounded-2xl bg-primary-soft text-primary"
      >
        <Icon width={26} height={26} />
      </motion.span>
      {children}
    </motion.div>
  )
}

/* ---------- Layout pieces ---------- */

export const quickItemClass =
  'group flex w-full items-center gap-3 rounded-xl bg-[#f1f5f9] px-4 py-3.5 text-left font-bold text-ink transition duration-200 hover:bg-primary-soft hover:text-primary'

// Row button for "Quick actions" panels. Pass `as="a"` with href for a link.
export function QuickItem({ icon: Icon, children, as = 'button', ...props }) {
  const Comp = as === 'a' ? motion.a : motion.button
  return (
    <Comp whileTap={{ scale: 0.98 }} className={quickItemClass} {...(as === 'button' ? { type: 'button' } : {})} {...props}>
      <Icon className="shrink-0 text-primary transition-transform duration-300 group-hover:scale-115" />
      {children}
      <ChevronRightIcon className="ml-auto shrink-0 transition-transform duration-300 ease-spring group-hover:translate-x-1" />
    </Comp>
  )
}

export function Panel({ className, delay = 0, as = 'section', ...props }) {
  const Comp = motion[as]
  return (
    <Comp
      {...fadeUp(delay)}
      className={cx('rounded-[18px] border border-line bg-surface p-6', className)}
      {...props}
    />
  )
}

export function PageHeader({ title, sub, children }) {
  return (
    <header className="flex flex-wrap items-end justify-between gap-4">
      <div>
        <motion.h1 {...fadeUp(0)} className="text-[1.6rem] font-extrabold tracking-tight md:text-[2rem]">
          {title}
        </motion.h1>
        <motion.p {...fadeUp(0.06)} className="mt-1.5 text-ink-soft">
          {sub}
        </motion.p>
      </div>
      {children && (
        <motion.div {...fadeUp(0.12)} className="flex w-full flex-wrap items-center gap-3 sm:w-auto">
          {children}
        </motion.div>
      )}
    </header>
  )
}

// Pill search box. Press "/" anywhere on the page to focus it, Escape to clear.
export function SearchBox({ value, onChange, placeholder, label, wide }) {
  const ref = useRef(null)
  useEffect(() => {
    const onKey = (e) => {
      const typing = ['INPUT', 'TEXTAREA', 'SELECT'].includes(document.activeElement?.tagName)
      if (e.key === '/' && !typing) {
        e.preventDefault()
        ref.current?.focus()
      }
    }
    document.addEventListener('keydown', onKey)
    return () => document.removeEventListener('keydown', onKey)
  }, [])

  return (
    <label
      className={cx(
        'flex h-11.5 w-full cursor-text items-center gap-2.5 rounded-full border-[1.5px] border-line bg-surface pr-3 pl-4 text-ink-soft',
        'transition-all duration-300 focus-within:border-primary focus-within:ring-4 focus-within:ring-primary/15',
        wide ? 'sm:w-77.5 sm:focus-within:w-87.5' : 'sm:w-69 sm:focus-within:w-80',
      )}
    >
      <SearchIcon />
      <span className="sr-only">{label}</span>
      <input
        ref={ref}
        type="search"
        placeholder={placeholder}
        value={value}
        onChange={(e) => onChange(e.target.value)}
        onKeyDown={(e) => e.key === 'Escape' && onChange('')}
        className="min-w-0 flex-1 bg-transparent text-ink outline-none placeholder:text-muted"
      />
      {value ? (
        <button
          type="button"
          onClick={() => onChange('')}
          aria-label="Clear search"
          className="grid size-6 animate-in place-items-center rounded-full bg-[#eef1f5] text-ink-soft zoom-in-50"
        >
          <XIcon width={14} height={14} />
        </button>
      ) : (
        <kbd aria-hidden="true" className="hidden rounded-md border border-line px-1.75 text-[0.8rem] text-muted sm:inline">
          /
        </kbd>
      )}
    </label>
  )
}
