import { useRef, useState } from 'react'
import { AnimatePresence, motion } from 'motion/react'
import { api } from '../services/api.js'
import { PlusIcon, XIcon, CheckIcon } from './Icons.jsx'
import { Spinner, cx } from './ui.jsx'

// Public question box for hospital information (opening hours, departments,
// parking). It calls the Java API, which forwards to the Python AI service, so
// the Gemini key stays on the server and never reaches this code.

const suggestions = [
  'When can I visit a patient?',
  'How do I book an appointment?',
  'Where is the cardiology department?',
]

export default function HospitalAssistant() {
  const [open, setOpen] = useState(false)
  const [question, setQuestion] = useState('')
  const [messages, setMessages] = useState([])
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const listRef = useRef(null)

  const ask = async (text) => {
    const message = text.trim()
    if (!message || busy) return
    setError('')
    setBusy(true)
    setMessages((list) => [...list, { from: 'you', text: message }])
    setQuestion('')
    try {
      const data = await api('/assistant/chat', { method: 'POST', auth: false, body: { message } })
      setMessages((list) => [...list, { from: 'assistant', text: data.response }])
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(false)
      requestAnimationFrame(() => {
        listRef.current?.scrollTo({ top: listRef.current.scrollHeight, behavior: 'smooth' })
      })
    }
  }

  return (
    <div className="fixed right-4 bottom-4 z-[90] sm:right-6 sm:bottom-6">
      <AnimatePresence>
        {open && (
          <motion.section
            initial={{ opacity: 0, y: 20, scale: 0.96 }}
            animate={{ opacity: 1, y: 0, scale: 1 }}
            exit={{ opacity: 0, y: 16, scale: 0.97 }}
            transition={{ type: 'spring', stiffness: 380, damping: 30 }}
            aria-label="Hospital information assistant"
            className="mb-3 flex h-[min(460px,70vh)] w-[min(360px,calc(100vw-32px))] flex-col overflow-hidden rounded-[18px] border border-line bg-surface shadow-pop"
          >
            <header className="flex items-center gap-2.5 border-b border-line px-4 py-3">
              <span className="grid size-8 place-items-center rounded-lg bg-primary text-white">
                <PlusIcon width={16} height={16} strokeWidth={2.6} />
              </span>
              <span className="grid leading-tight">
                <strong className="text-[0.98rem]">Haven assistant</strong>
                <small className="text-[0.8rem] text-ink-soft">Hospital information only</small>
              </span>
              <button
                type="button"
                onClick={() => setOpen(false)}
                aria-label="Close assistant"
                className="ml-auto grid size-8 place-items-center rounded-lg text-muted transition hover:bg-primary-soft hover:text-primary"
              >
                <XIcon width={16} height={16} />
              </button>
            </header>

            <div ref={listRef} className="flex-1 space-y-2.5 overflow-y-auto px-4 py-3" aria-live="polite">
              {messages.length === 0 && (
                <div className="grid gap-2">
                  <p className="text-[0.9rem] leading-relaxed text-ink-soft">
                    Ask about visiting hours, departments or how to book. For medical emergencies call the
                    emergency number.
                  </p>
                  {suggestions.map((s) => (
                    <button
                      key={s}
                      type="button"
                      onClick={() => ask(s)}
                      className="rounded-xl bg-field px-3 py-2 text-left text-[0.88rem] font-semibold text-ink transition hover:bg-primary-soft hover:text-primary"
                    >
                      {s}
                    </button>
                  ))}
                </div>
              )}

              {messages.map((m, i) => (
                <div
                  key={i}
                  className={cx(
                    'max-w-[85%] rounded-xl px-3 py-2 text-[0.9rem] leading-relaxed whitespace-pre-line',
                    m.from === 'you'
                      ? 'ml-auto bg-primary text-white'
                      : 'bg-field text-ink',
                  )}
                >
                  {m.text}
                </div>
              ))}

              {busy && (
                <p className="flex items-center gap-2 text-[0.85rem] text-ink-soft">
                  <Spinner className="border-primary-soft border-t-primary" /> Thinking…
                </p>
              )}
              {error && (
                <p role="alert" className="flex items-start gap-2 rounded-xl bg-er-soft px-3 py-2 text-[0.85rem] font-semibold text-er">
                  {error}
                </p>
              )}
            </div>

            <form
              onSubmit={(e) => {
                e.preventDefault()
                ask(question)
              }}
              className="flex items-center gap-2 border-t border-line px-3 py-2.5"
            >
              <label htmlFor="assistant-question" className="sr-only">Your question</label>
              <input
                id="assistant-question"
                value={question}
                onChange={(e) => setQuestion(e.target.value)}
                maxLength={1000}
                placeholder="Ask a question…"
                className="h-10 min-w-0 flex-1 rounded-xl border-[1.5px] border-line bg-field px-3 text-ink outline-none transition focus:border-primary focus:bg-white focus:ring-4 focus:ring-primary/15"
              />
              <button
                type="submit"
                disabled={busy || !question.trim()}
                className="grid size-10 shrink-0 place-items-center rounded-xl bg-primary text-white transition hover:bg-primary-dark disabled:opacity-50"
                aria-label="Send question"
              >
                <CheckIcon width={16} height={16} />
              </button>
            </form>
          </motion.section>
        )}
      </AnimatePresence>

      <motion.button
        type="button"
        onClick={() => setOpen((o) => !o)}
        whileHover={{ y: -3 }}
        whileTap={{ scale: 0.95 }}
        aria-expanded={open}
        className="ml-auto flex h-13 items-center gap-2.5 rounded-full bg-primary px-5 font-bold text-white shadow-btn"
      >
        {open ? <XIcon width={18} height={18} /> : <PlusIcon width={18} height={18} />}
        {open ? 'Close' : 'Ask about visiting'}
      </motion.button>
    </div>
  )
}
