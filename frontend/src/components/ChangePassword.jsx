import { useState } from 'react'
import { motion } from 'motion/react'
import { EyeIcon, EyeOffIcon } from './Icons.jsx'
import { Button, Field, PillButton, Spinner, cx } from './ui.jsx'

function strength(pw) {
  let s = 0
  if (pw.length >= 8) s++
  if (pw.length >= 12) s++
  if (/[A-Z]/.test(pw) && /[a-z]/.test(pw)) s++
  if (/\d/.test(pw)) s++
  if (/[^A-Za-z0-9]/.test(pw)) s++
  return Math.min(4, s)
}
const meter = [
  { label: 'Too weak', width: '10%', color: 'bg-er' },
  { label: 'Weak', width: '30%', color: 'bg-er' },
  { label: 'Fair', width: '55%', color: 'bg-[#e0a02f]' },
  { label: 'Good', width: '80%', color: 'bg-ok-mid' },
  { label: 'Strong', width: '100%', color: 'bg-[#2f8a57]' },
]

// Inline change-password form with a strength meter. Calls onDone() after the (mock) request.
export default function ChangePassword({ onDone }) {
  const [pw, setPw] = useState({ current: '', next: '', confirm: '' })
  const [show, setShow] = useState(false)
  const [errors, setErrors] = useState({})
  const [busy, setBusy] = useState(false)
  const score = strength(pw.next)

  const set = (k) => (e) => {
    setPw((p) => ({ ...p, [k]: e.target.value }))
    setErrors((er) => ({ ...er, [k]: undefined }))
  }

  const submit = async (e) => {
    e.preventDefault()
    const errs = {}
    if (!pw.current) errs.current = 'Enter your current password.'
    if (pw.next.length < 8) errs.next = 'Use at least 8 characters.'
    else if (pw.next === pw.current) errs.next = 'Choose a different password.'
    if (pw.confirm !== pw.next) errs.confirm = 'The passwords do not match.'
    setErrors(errs)
    if (Object.keys(errs).length) return
    setBusy(true)
    await new Promise((r) => setTimeout(r, 700)) // mock request
    onDone()
  }

  const type = show ? 'text' : 'password'

  return (
    <form onSubmit={submit} noValidate className="grid gap-4 px-1 pb-5">
      <div className="grid gap-4 md:grid-cols-3">
        <Field id="pw-current" label="Current password" type={type} autoComplete="current-password" value={pw.current} onChange={set('current')} error={errors.current} />
        <div>
          <Field id="pw-next" label="New password" type={type} autoComplete="new-password" value={pw.next} onChange={set('next')} error={errors.next} />
          {pw.next && (
            <span className="mt-2 flex items-center gap-2.5 text-[0.82rem] font-bold text-ink-soft" aria-live="polite">
              <span className="h-1.5 flex-1 overflow-hidden rounded-full bg-[#eef1f5]">
                <motion.span
                  className={cx('block h-full rounded-full', meter[score].color)}
                  animate={{ width: meter[score].width }}
                  transition={{ type: 'spring', stiffness: 200, damping: 24 }}
                />
              </span>
              {meter[score].label}
            </span>
          )}
        </div>
        <Field id="pw-confirm" label="Confirm new password" type={type} autoComplete="new-password" value={pw.confirm} onChange={set('confirm')} error={errors.confirm} />
      </div>
      <div className="flex justify-end gap-2.5">
        <PillButton onClick={() => setShow((s) => !s)} aria-pressed={show}>
          {show ? <EyeOffIcon width={14} height={14} /> : <EyeIcon width={14} height={14} />}
          {show ? 'Hide' : 'Show'} passwords
        </PillButton>
        <Button type="submit" disabled={busy}>
          {busy && <Spinner />}
          {busy ? 'Updating…' : 'Update password'}
        </Button>
      </div>
    </form>
  )
}
