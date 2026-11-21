import { useCallback, useEffect, useState } from 'react'
import { AnimatePresence, motion } from 'motion/react'
import { api } from '../services/api.js'
import { useAdminData } from './AdminData.jsx'
import { useToast } from '../components/Toast.jsx'
import Modal from '../components/Modal.jsx'
import Highlight from '../components/Highlight.jsx'
import { departments } from '../config/hospital.js'
import { UserIcon, SearchIcon, KeyIcon, CheckIcon, XCircleIcon } from '../components/Icons.jsx'
import {
  Alert, Button, EmptyState, Field, PageHeader, Panel, PillButton, SearchBox, badgeClass, cx, rowMotion, tbl,
  textLinkClass, inputClass, labelClass,
} from '../components/ui.jsx'

const blank = { fullName: '', email: '', role: 'ADMIN', specialty: departments[0].name, phone: '', password: '' }

function CreateAccountModal({ open, onClose, onCreated }) {
  const [form, setForm] = useState(blank)
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
    if (form.fullName.trim().length < 2) errs.fullName = 'Enter the full name.'
    if (!/^\S+@\S+\.\S+$/.test(form.email.trim())) errs.email = 'Enter a valid email.'
    if (form.password.length < 8 || !/[A-Za-z]/.test(form.password) || !/\d/.test(form.password)) {
      errs.password = 'Use at least 8 characters with a letter and a number.'
    }
    setErrors(errs)
    if (Object.keys(errs).length) return

    setSaving(true)
    setFormError('')
    try {
      const body = {
        fullName: form.fullName.trim(),
        email: form.email.trim().toLowerCase(),
        role: form.role,
        phone: form.phone.trim() || null,
        password: form.password,
      }
      if (form.role === 'DOCTOR') body.specialty = form.specialty
      const created = await api('/admin/users', { method: 'POST', body })
      onCreated(created)
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
      title="Create an account"
      footer={
        <>
          <Button variant="ghost" onClick={onClose}>Cancel</Button>
          <Button type="submit" form="account-form" disabled={saving}>
            {saving ? 'Creating…' : 'Create account'}
          </Button>
        </>
      }
    >
      <form id="account-form" onSubmit={submit} noValidate className="grid gap-4 sm:grid-cols-2">
        <Field id="ac-name" label="Full name" data-autofocus value={form.fullName} onChange={set('fullName')} error={errors.fullName} className="sm:col-span-2" />
        <Field id="ac-email" label="Email" type="email" value={form.email} onChange={set('email')} error={errors.email} />
        <Field id="ac-phone" label="Phone" value={form.phone} onChange={set('phone')} inputMode="tel" />
        <div className="grid content-start gap-2">
          <label htmlFor="ac-role" className={labelClass}>Role</label>
          <select id="ac-role" value={form.role} onChange={set('role')} className={inputClass}>
            <option value="ADMIN">Administrator</option>
            <option value="DOCTOR">Doctor</option>
          </select>
        </div>
        {form.role === 'DOCTOR' && (
          <div className="grid content-start gap-2">
            <label htmlFor="ac-specialty" className={labelClass}>Specialty</label>
            <select id="ac-specialty" value={form.specialty} onChange={set('specialty')} className={inputClass}>
              {departments.map((d) => <option key={d.name}>{d.name}</option>)}
            </select>
          </div>
        )}
        <Field
          id="ac-password"
          label="Initial password"
          type="password"
          value={form.password}
          onChange={set('password')}
          error={errors.password}
          autoComplete="new-password"
          className="sm:col-span-2"
        />
        {form.role === 'DOCTOR' && (
          <p className="text-[0.88rem] text-ink-soft sm:col-span-2">
            A doctor account also gets a profile with a staff ID, working hours and patients.
          </p>
        )}
        {formError && <Alert error className="sm:col-span-2">{formError}</Alert>}
      </form>
    </Modal>
  )
}

function ResetPasswordModal({ user, onClose, onDone }) {
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [saving, setSaving] = useState(false)

  const submit = async (e) => {
    e.preventDefault()
    if (password.length < 8 || !/[A-Za-z]/.test(password) || !/\d/.test(password)) {
      setError('Use at least 8 characters with a letter and a number.')
      return
    }
    setSaving(true)
    try {
      await api(`/admin/users/${user.id}/password`, { method: 'POST', body: { password } })
      onDone(user)
      onClose()
    } catch (err) {
      setSaving(false)
      setError(err.message)
    }
  }

  return (
    <Modal
      open={!!user}
      onClose={onClose}
      title="Reset password"
      size="sm"
      footer={
        <>
          <Button variant="ghost" onClick={onClose}>Cancel</Button>
          <Button type="submit" form="reset-form" disabled={saving}>
            {saving ? 'Saving…' : 'Set password'}
          </Button>
        </>
      }
    >
      {user && (
        <form id="reset-form" onSubmit={submit} noValidate className="grid gap-4">
          <p className="text-[0.92rem] leading-relaxed text-ink-soft">
            Set a new password for <strong className="text-ink">{user.fullName || user.email}</strong>.
            All their open sessions are signed out.
          </p>
          <Field
            id="reset-password"
            label="New password"
            type="password"
            value={password}
            onChange={(e) => { setPassword(e.target.value); setError('') }}
            error={error}
            data-autofocus
            autoComplete="new-password"
          />
        </form>
      )}
    </Modal>
  )
}

export default function Users() {
  const { account } = useAdminData()
  const toast = useToast()
  const [users, setUsers] = useState([])
  const [query, setQuery] = useState('')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [creating, setCreating] = useState(false)
  const [resetting, setResetting] = useState(null)

  const load = useCallback(async () => {
    try {
      setUsers(await api('/admin/users'))
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
  const rows = users.filter(
    (u) => !q || [u.fullName, u.email, u.role, u.staffId].join(' ').toLowerCase().includes(q.toLowerCase()),
  )

  const toggleActive = async (user) => {
    try {
      await api(`/admin/users/${user.id}/active`, { method: 'PATCH', body: { active: !user.active } })
      await load()
      toast(`${user.email} ${user.active ? 'deactivated' : 'activated'}`)
    } catch (err) {
      toast(err.message, { tone: 'error' })
    }
  }

  return (
    <div className="grid max-w-280 gap-6">
      <PageHeader
        title="Accounts"
        sub={`${users.length} ${users.length === 1 ? 'account' : 'accounts'} in the hospital`}
      >
        <SearchBox value={query} onChange={setQuery} placeholder="Search accounts" label="Search accounts" />
        <Button withRipple className="max-sm:flex-1" onClick={() => setCreating(true)}>
          <UserIcon />
          Create account
        </Button>
      </PageHeader>

      <Panel delay={0.18} className="overflow-hidden p-0!">
        {loading && <p className="px-6 py-10 text-center text-ink-soft">Loading accounts…</p>}
        {!loading && error && <p role="alert" className="px-6 py-10 text-center font-semibold text-er">{error}</p>}

        {!loading && !error && rows.length > 0 && (
          <table className={tbl.table}>
            <thead className={tbl.thead}>
              <tr>
                <th className={tbl.th}>Account</th>
                <th className={tbl.th}>Role</th>
                <th className={tbl.th}>Status</th>
                <th className={cx(tbl.th, 'text-right')}>Actions</th>
              </tr>
            </thead>
            <tbody className={tbl.tbody}>
              <AnimatePresence initial={true}>
                {rows.map((u, i) => (
                  <motion.tr key={u.id} {...rowMotion(i)} className={tbl.tr}>
                    <td className={tbl.td}>
                      <span className="grid leading-snug">
                        <strong><Highlight text={u.fullName || u.email} query={q} /></strong>
                        <small className="text-[0.85rem] text-ink-soft">
                          <Highlight text={[u.email, u.staffId].filter(Boolean).join(' · ')} query={q} />
                        </small>
                      </span>
                    </td>
                    <td className={cx(tbl.td, tbl.tdSub)}>{u.role === 'ADMIN' ? 'Administrator' : 'Doctor'}</td>
                    <td className={cx(tbl.td, tbl.tdSub)}>
                      <span className={badgeClass(u.active ? 'active' : 'inactive')}>
                        {u.active ? 'Active' : 'Deactivated'}
                      </span>
                    </td>
                    <td className={cx(tbl.td, tbl.actions)}>
                      <span className="inline-flex flex-wrap gap-2">
                        <PillButton onClick={() => setResetting(u)}>
                          <KeyIcon />Reset password
                        </PillButton>
                        <PillButton
                          danger={u.active}
                          disabled={u.email === account.email}
                          title={u.email === account.email ? 'You cannot deactivate your own account' : undefined}
                          onClick={() => toggleActive(u)}
                        >
                          {u.active ? <XCircleIcon /> : <CheckIcon />}
                          {u.active ? 'Deactivate' : 'Activate'}
                        </PillButton>
                      </span>
                    </td>
                  </motion.tr>
                ))}
              </AnimatePresence>
            </tbody>
          </table>
        )}

        {!loading && !error && rows.length === 0 && (
          <EmptyState icon={q ? SearchIcon : UserIcon}>
            <p>{q ? `No accounts match “${q}”.` : 'No accounts yet.'}</p>
            {q && <button type="button" className={textLinkClass} onClick={() => setQuery('')}>Clear search</button>}
          </EmptyState>
        )}
      </Panel>

      {creating && (
        <CreateAccountModal
          open={creating}
          onClose={() => setCreating(false)}
          onCreated={(created) => {
            load()
            toast(`Account created for ${created.email}`)
          }}
        />
      )}

      {resetting && (
        <ResetPasswordModal
          key={resetting.id}
          user={resetting}
          onClose={() => setResetting(null)}
          onDone={(user) => toast(`Password reset for ${user.email}`)}
        />
      )}
    </div>
  )
}
