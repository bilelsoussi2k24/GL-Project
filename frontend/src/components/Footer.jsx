import { Link } from 'react-router-dom'
import { motion } from 'motion/react'
import { useHospital } from '../services/hospital.js'
import { revealUp } from './ui.jsx'

const linkClass =
  'relative text-[#d8dbe6] transition-colors hover:text-white after:absolute after:inset-x-0 after:-bottom-1 after:h-0.5 ' +
  'after:origin-right after:scale-x-0 after:bg-current after:transition-transform after:duration-300 hover:after:origin-left hover:after:scale-x-100'

export default function Footer() {
  const hospital = useHospital()
  return (
    <footer className="bg-navy text-white">
      <motion.div
        {...revealUp(0, 12)}
        className="mx-auto flex min-h-23 w-[min(1200px,100%-32px)] flex-wrap items-center justify-between gap-4 py-4"
      >
        <span className="text-xl font-extrabold">{hospital.name}</span>
        <nav className="flex gap-6" aria-label="Footer">
          <Link to="/" className={linkClass}>Homepage</Link>
          <Link to="/login" className={linkClass}>Staff login</Link>
        </nav>
      </motion.div>
    </footer>
  )
}
