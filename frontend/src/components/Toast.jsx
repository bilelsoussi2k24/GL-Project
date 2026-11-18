import { createContext, useCallback, useContext, useEffect, useRef, useState } from 'react'
import { AnimatePresence, motion } from 'motion/react'
import { CheckIcon } from './Icons.jsx'
import { cx } from './ui.jsx'

const ToastContext = createContext(() => {})

// Usage: const toast = useToast(); toast('Saved', { action: { label: 'Undo', onClick } })
export function ToastProvider({ children }) {
  const [toast, setToast] = useState(null)
  const timer = useRef(null)

  const show = useCallback((message, opts = {}) => {
    clearTimeout(timer.current)
    setToast({ message, action: opts.action, tone: opts.tone ?? 'default', key: Date.now() })
    timer.current = setTimeout(() => setToast(null), opts.duration ?? 4000)
  }, [])

  useEffect(() => () => clearTimeout(timer.current), [])

  return (
    <ToastContext.Provider value={show}>
      {children}
      <div className="pointer-events-none fixed inset-x-0 bottom-7 z-[300] flex justify-center px-4" role="status" aria-live="polite">
        <AnimatePresence mode="popLayout">
          {toast && (
            <motion.div
              key={toast.key}
              layout
              initial={{ opacity: 0, y: 60, scale: 0.9 }}
              animate={{ opacity: 1, y: 0, scale: 1 }}
              exit={{ opacity: 0, y: 30, scale: 0.95 }}
              transition={{ type: 'spring', stiffness: 380, damping: 28 }}
              className="pointer-events-auto inline-flex max-w-full items-center gap-2.5 rounded-[14px] bg-ink py-3 pr-3.5 pl-4.5 font-semibold text-white shadow-[0_16px_32px_-14px_rgb(20_30_60/0.6)]"
            >
              <motion.span
                initial={{ scale: 0, rotate: -90 }}
                animate={{ scale: 1, rotate: 0 }}
                transition={{ delay: 0.1, type: 'spring', stiffness: 400, damping: 15 }}
                className={cx('shrink-0', toast.tone === 'error' ? 'text-amber' : 'text-[#7fd6a2]')}
              >
                <CheckIcon width={16} height={16} />
              </motion.span>
              <span>{toast.message}</span>
              {toast.action && (
                <button
                  type="button"
                  onClick={() => {
                    toast.action.onClick()
                    clearTimeout(timer.current)
                    setToast(null)
                  }}
                  className="ml-1.5 rounded-lg bg-white/12 px-3 py-1.5 font-bold transition hover:bg-white/22"
                >
                  {toast.action.label}
                </button>
              )}
            </motion.div>
          )}
        </AnimatePresence>
      </div>
    </ToastContext.Provider>
  )
}

// eslint-disable-next-line react-refresh/only-export-components
export const useToast = () => useContext(ToastContext)
