import { useState } from 'react'
import { useOutletContext } from 'react-router-dom'
import { AnimatePresence, motion } from 'motion/react'
import { useAdminData, statusLabel } from './AdminData.jsx'
import Modal from '../components/Modal.jsx'
import Highlight from '../components/Highlight.jsx'
import { useToast } from '../components/Toast.jsx'
import { initials } from '../utils/date.js'
import { PlusIcon, PencilIcon, TrashIcon, SearchIcon, StethoscopeIcon } from '../components/Icons.jsx'
import {
  Avatar, Button, EmptyState, PageHeader, Panel, PillButton, SearchBox, badgeClass, cx, rowMotion, tbl, textLinkClass,
} from '../components/ui.jsx'

export default function Doctors() {
  const { doctors, updateDoctor, removeDoctor } = useAdminData()
  const { openDoctor } = useOutletContext()
  const toast = useToast()
  const [query, setQuery] = useState('')
  // Kept after closing so the dialog text stays during its exit animation.
  const [confirm, setConfirm] = useState({ open: false, doctor: null })

  const q = query.trim()
  const rows = doctors.filter(
    (d) => !q || [d.name, d.specialty, d.email, d.phone, d.staffId].join(' ').toLowerCase().includes(q.toLowerCase()),
  )

  const toggleStatus = (d) => {
    const next = d.status === 'active' ? 'leave' : 'active'
    updateDoctor(d.id, { status: next })
    toast(`${d.name} is now ${statusLabel[next].toLowerCase()}`, {
      action: { label: 'Undo', onClick: () => updateDoctor(d.id, { status: d.status }) },
    })
  }

  const closeConfirm = () => setConfirm((c) => ({ ...c, open: false }))

  const confirmDelete = () => {
    const d = confirm.doctor
    const undo = removeDoctor(d.id)
    closeConfirm()
    toast(`Deleted ${d.name}`, { action: { label: 'Undo', onClick: undo }, duration: 6000 })
  }

  return (
    <div className="grid max-w-280 gap-6">
      <PageHeader title="Doctors" sub={q ? `${rows.length} of ${doctors.length} ${doctors.length === 1 ? 'doctor' : 'doctors'}` : `${doctors.length} ${doctors.length === 1 ? 'doctor' : 'doctors'} on staff`}>
        <SearchBox wide value={query} onChange={setQuery} placeholder="Search by name or specialty" label="Search doctors" />
        <Button withRipple className="max-sm:flex-1" onClick={() => openDoctor()}>
          <PlusIcon />
          Add doctor
        </Button>
      </PageHeader>

      <Panel delay={0.18} className="overflow-hidden p-0!">
        {rows.length > 0 && (
        <table className={tbl.table}>
          <thead className={tbl.thead}>
            <tr>
              <th className={tbl.th}>Doctor</th>
              <th className={tbl.th}>Specialty</th>
              <th className={tbl.th}>Phone</th>
              <th className={tbl.th}>Status</th>
              <th className={cx(tbl.th, 'text-right')}>Actions</th>
            </tr>
          </thead>
          <tbody className={tbl.tbody}>
            <AnimatePresence initial={true}>
              {rows.map((d, i) => (
                <motion.tr key={d.id} {...rowMotion(i)} className={cx(tbl.tr, 'group')}>
                  <td className={tbl.td}>
                    <span className="flex items-center gap-3">
                      <Avatar className="group-hover:scale-108 group-hover:bg-[#d4e2f0]">{initials(d.name)}</Avatar>
                      <span className="grid leading-snug">
                        <strong><Highlight text={d.name} query={q} /></strong>
                        <small className="text-[0.85rem] text-ink-soft"><Highlight text={d.email} query={q} /></small>
                      </span>
                    </span>
                  </td>
                  <td className={cx(tbl.td, tbl.tdSub)}><Highlight text={d.specialty} query={q} /></td>
                  <td className={cx(tbl.td, tbl.tdSub, 'whitespace-nowrap')}><Highlight text={d.phone || '—'} query={q} /></td>
                  <td className={cx(tbl.td, tbl.tdSub)}>
                    {/* Click the badge to switch between Active and On leave */}
                    <motion.button
                      type="button"
                      layout
                      onClick={() => toggleStatus(d)}
                      title={`Click to mark as ${d.status === 'active' ? 'on leave' : 'active'}`}
                      whileHover={{ scale: 1.06 }}
                      whileTap={{ scale: 0.9 }}
                      className={badgeClass(d.status, 'transition-colors duration-300 hover:ring-3 hover:ring-current/20')}
                    >
                      <AnimatePresence mode="wait" initial={false}>
                        <motion.span key={d.status} initial={{ y: 8, opacity: 0 }} animate={{ y: 0, opacity: 1 }} exit={{ y: -8, opacity: 0 }} transition={{ duration: 0.15 }}>
                          {statusLabel[d.status]}
                        </motion.span>
                      </AnimatePresence>
                    </motion.button>
                  </td>
                  <td className={cx(tbl.td, tbl.actions)}>
                    <span className="inline-flex gap-2">
                      <PillButton onClick={() => openDoctor(d)}>
                        <PencilIcon />Edit
                      </PillButton>
                      <PillButton danger onClick={() => setConfirm({ open: true, doctor: d })}>
                        <TrashIcon />Delete
                      </PillButton>
                    </span>
                  </td>
                </motion.tr>
              ))}
            </AnimatePresence>
          </tbody>
        </table>
        )}

        {rows.length === 0 && (
          <EmptyState icon={q ? SearchIcon : StethoscopeIcon}>
            {q ? (
              <>
                <p>No doctors match “{q}”.</p>
                <button type="button" className={textLinkClass} onClick={() => setQuery('')}>Clear search</button>
              </>
            ) : (
              <>
                <p>No doctors yet.</p>
                <button type="button" className={textLinkClass} onClick={() => openDoctor()}>Add the first one</button>
              </>
            )}
          </EmptyState>
        )}
      </Panel>

      <Modal
        open={confirm.open}
        onClose={closeConfirm}
        title="Delete this doctor?"
        size="sm"
        footer={
          <>
            <Button variant="ghost" onClick={closeConfirm} data-autofocus>Keep</Button>
            <Button variant="danger" onClick={confirmDelete}>Delete doctor</Button>
          </>
        }
      >
        {confirm.doctor && (
          <p className="leading-relaxed text-ink-soft">
            <strong className="text-ink">{confirm.doctor.name}</strong> · {confirm.doctor.specialty}
            <br />
            Their staff account ({confirm.doctor.staffId}) will be removed. You can undo this right after.
          </p>
        )}
      </Modal>
    </div>
  )
}
