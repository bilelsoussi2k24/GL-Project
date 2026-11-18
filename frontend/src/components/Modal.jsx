import { useEffect, useRef } from 'react'
import { createPortal } from 'react-dom'
import { AnimatePresence, motion } from 'motion/react'
import { XIcon } from './Icons.jsx'
import { IconButton, cx } from './ui.jsx'

// Accessible dialog with enter/exit animations. Escape and backdrop click close it,
// focus moves inside on open, Tab stays inside, and focus returns to the opener on close.
// Keep the component mounted and toggle `open` so the exit animation can play.
export default function Modal({ open, onClose, title, children, footer, size = 'md' }) {
  return createPortal(
    <AnimatePresence>
      {open && (
        <Dialog onClose={onClose} title={title} footer={footer} size={size}>
          {children}
        </Dialog>
      )}
    </AnimatePresence>,
    document.body,
  )
}

function Dialog({ onClose, title, children, footer, size }) {
  const panelRef = useRef(null)
  const closeRef = useRef(onClose)
  useEffect(() => {
    closeRef.current = onClose
  })

  useEffect(() => {
    const opener = document.activeElement
    const panel = panelRef.current
    const focusables = () =>
      [...panel.querySelectorAll('button, [href], input, select, textarea, [tabindex]:not([tabindex="-1"])')].filter(
        (el) => !el.disabled,
      )
    ;(panel.querySelector('[data-autofocus]') ?? focusables()[1] ?? panel).focus()

    const onKey = (e) => {
      if (e.key === 'Escape') closeRef.current()
      if (e.key === 'Tab') {
        const els = focusables()
        if (!els.length) return
        const first = els[0]
        const last = els[els.length - 1]
        if (e.shiftKey && document.activeElement === first) {
          e.preventDefault()
          last.focus()
        } else if (!e.shiftKey && document.activeElement === last) {
          e.preventDefault()
          first.focus()
        }
      }
    }
    document.addEventListener('keydown', onKey)
    const overflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    return () => {
      document.removeEventListener('keydown', onKey)
      document.body.style.overflow = overflow
      opener?.focus?.()
    }
  }, [])

  return (
    <motion.div
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      exit={{ opacity: 0 }}
      transition={{ duration: 0.2 }}
      className="fixed inset-0 z-[200] grid place-items-center bg-[#1e2234]/45 p-4 backdrop-blur-[3px]"
      onMouseDown={(e) => e.target === e.currentTarget && onClose()}
    >
      <motion.div
        ref={panelRef}
        initial={{ opacity: 0, y: 24, scale: 0.94 }}
        animate={{ opacity: 1, y: 0, scale: 1 }}
        exit={{ opacity: 0, y: 12, scale: 0.97 }}
        transition={{ type: 'spring', stiffness: 380, damping: 30 }}
        className={cx(
          'max-h-[calc(100vh-32px)] w-full overflow-auto rounded-[20px] bg-surface shadow-pop focus:outline-none',
          size === 'sm' ? 'max-w-110' : 'max-w-140',
        )}
        role="dialog"
        aria-modal="true"
        aria-labelledby="modal-title"
        tabIndex={-1}
      >
        <div className="flex items-center justify-between gap-3 px-6 pt-5.5">
          <h2 id="modal-title" className="text-[1.35rem] font-extrabold">{title}</h2>
          <IconButton onClick={onClose} aria-label="Close">
            <XIcon />
          </IconButton>
        </div>
        <div className="px-6 py-4.5">{children}</div>
        {footer && <div className="flex justify-end gap-2.5 px-6 pb-5.5">{footer}</div>}
      </motion.div>
    </motion.div>
  )
}
