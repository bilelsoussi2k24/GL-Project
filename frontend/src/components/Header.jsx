import { useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { motion, useMotionValueEvent, useScroll, useSpring } from 'motion/react'
import { PlusIcon } from './Icons.jsx'
import { useHospital } from '../services/hospital.js'
import { cx, buttonClass } from './ui.jsx'

export default function Header() {
  const hospital = useHospital()
  const { scrollY, scrollYProgress } = useScroll()
  const progress = useSpring(scrollYProgress, { stiffness: 140, damping: 30, restDelta: 0.001 })
  const [scrolled, setScrolled] = useState(false)
  const [hidden, setHidden] = useState(false)

  const lastY = useRef(0)

  // Shrinks after scrolling a bit; slides away when scrolling down, comes back when scrolling up.
  useMotionValueEvent(scrollY, 'change', (y) => {
    const delta = y - lastY.current
    lastY.current = y
    setScrolled(y > 8)
    if (y < 160) setHidden(false)
    else if (Math.abs(delta) > 4) setHidden(delta > 0)
  })

  return (
    <motion.header
      animate={{ y: hidden ? '-100%' : '0%' }}
      transition={{ duration: 0.35, ease: [0.22, 1, 0.36, 1] }}
      className={cx(
        'sticky top-0 z-50 border-b border-line backdrop-blur-md transition-[background-color,box-shadow] duration-300',
        scrolled ? 'bg-white/85 shadow-[0_6px_24px_-14px_rgb(30_50_90/0.35)]' : 'bg-canvas/80',
      )}
    >
      <div
        className={cx(
          'mx-auto flex w-[min(1200px,100%-32px)] items-center justify-between transition-[height] duration-300',
          scrolled ? 'h-16.5' : 'h-20.5',
        )}
      >
        <Link to="/" className="group inline-flex items-center gap-3 text-xl font-extrabold sm:text-2xl" aria-label={`${hospital.name} home`}>
          <motion.span
            initial={{ scale: 0, rotate: -180 }}
            animate={{ scale: 1, rotate: 0 }}
            transition={{ type: 'spring', stiffness: 260, damping: 18 }}
            className="grid size-10 place-items-center rounded-[10px] bg-primary text-white transition duration-500 ease-spring group-hover:rotate-90 group-hover:bg-primary-dark sm:size-11"
          >
            <PlusIcon width={22} height={22} strokeWidth={2.4} />
          </motion.span>
          <motion.span initial={{ opacity: 0, x: -10 }} animate={{ opacity: 1, x: 0 }} transition={{ delay: 0.15 }}>
            {hospital.name}
          </motion.span>
        </Link>
        <Link to="/login" className={buttonClass('outline')}>
          Staff login
        </Link>
      </div>
      {/* Reading progress bar */}
      <motion.div
        aria-hidden="true"
        style={{ scaleX: progress }}
        className="absolute inset-x-0 bottom-0 h-[3px] origin-left bg-gradient-to-r from-primary via-orange to-er"
      />
    </motion.header>
  )
}
