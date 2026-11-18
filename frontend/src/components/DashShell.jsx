import { useState } from 'react'
import { Link, NavLink, useLocation, useNavigate } from 'react-router-dom'
import { AnimatePresence, motion } from 'motion/react'
import { logout } from '../services/auth.js'
import { useHospital } from '../services/hospital.js'
import { PlusIcon, LogoutIcon, MenuIcon, XIcon } from './Icons.jsx'
import { cx } from './ui.jsx'

const linkBase =
  'group relative flex h-11 w-full items-center gap-3 rounded-xl px-3.5 font-bold transition-colors duration-300'

// Sidebar layout shared by the doctor and admin dashboards.
// nav: [{ to, label, icon, end? }]  card: optional node under the brand (e.g. the doctor's card).
export default function DashShell({ nav, home, subtitle, card, label, children }) {
  const navigate = useNavigate()
  const { pathname } = useLocation()
  const { name } = useHospital()
  const [menuOpen, setMenuOpen] = useState(false)
  const closeMenu = () => setMenuOpen(false)
  const [first, ...rest] = name.split(' ')

  return (
    <div className="grid min-h-screen bg-canvas lg:grid-cols-[240px_minmax(0,1fr)]">
      <aside
        aria-label={label}
        className="sticky top-0 z-40 flex h-16 flex-col bg-primary px-4 py-3 text-white lg:h-screen lg:px-4.5 lg:pt-7 lg:pb-6"
      >
        <div className="flex items-center justify-between">
          <Link to={home} onClick={closeMenu} className="group inline-flex items-center gap-2.5 pl-2 text-xl font-extrabold leading-tight">
            <span className="grid size-9 place-items-center rounded-[10px] bg-white text-primary transition duration-500 ease-spring group-hover:rotate-90 lg:size-10">
              <PlusIcon width={22} height={22} strokeWidth={2.4} />
            </span>
            <span className="lg:grid">
              {first}
              {rest.length > 0 && <>{' '}<br className="hidden lg:block" />{rest.join(' ')}</>}
              {subtitle && <small className="mt-0.5 hidden text-[0.8rem] font-semibold text-sky lg:block">{subtitle}</small>}
            </span>
          </Link>
          <button
            type="button"
            onClick={() => setMenuOpen((o) => !o)}
            aria-expanded={menuOpen}
            aria-label={menuOpen ? 'Close menu' : 'Open menu'}
            className="grid size-10 place-items-center rounded-[10px] bg-white/12 lg:hidden"
          >
            <motion.span key={menuOpen ? 'x' : 'm'} initial={{ rotate: -90, opacity: 0 }} animate={{ rotate: 0, opacity: 1 }}>
              {menuOpen ? <XIcon /> : <MenuIcon />}
            </motion.span>
          </button>
        </div>

        {/* Drawer on small screens, column on large screens */}
        <div
          className={cx(
            'fixed top-16 bottom-0 left-0 flex w-[min(290px,85vw)] flex-col overflow-y-auto bg-primary px-4.5 pb-6 transition-transform duration-300 ease-out-expo',
            'lg:static lg:w-auto lg:flex-1 lg:translate-x-0 lg:overflow-visible lg:p-0',
            menuOpen ? 'translate-x-0' : '-translate-x-full',
          )}
        >
          {card && <div onClick={closeMenu}>{card}</div>}

          <nav className="mt-7 grid gap-1">
            {nav.map(({ to, label: text, icon: Icon, end }, i) => (
              <motion.div
                key={to}
                initial={{ opacity: 0, x: -16 }}
                animate={{ opacity: 1, x: 0 }}
                transition={{ delay: 0.1 + i * 0.06 }}
              >
                <NavLink
                  to={to}
                  end={end}
                  onClick={closeMenu}
                  className={({ isActive }) =>
                    cx(linkBase, isActive ? 'text-primary' : 'text-white hover:bg-white/8')
                  }
                >
                  {({ isActive }) => (
                    <>
                      {isActive && (
                        // Shared layoutId: the white pill glides to the active link.
                        <motion.span
                          layoutId={`${home}-nav-pill`}
                          transition={{ type: 'spring', stiffness: 420, damping: 32 }}
                          className="absolute inset-0 rounded-xl bg-white shadow-[0_8px_18px_-10px_rgb(0_0_0/0.45)]"
                        />
                      )}
                      <Icon className="relative transition-transform duration-300 ease-spring group-hover:scale-115" />
                      <span className="relative">{text}</span>
                    </>
                  )}
                </NavLink>
              </motion.div>
            ))}
          </nav>

          <button
            type="button"
            onClick={() => {
              logout()
              navigate('/login')
            }}
            className={cx(linkBase, 'mt-auto text-white hover:bg-white/8')}
          >
            <LogoutIcon className="transition-transform duration-300 group-hover:-translate-x-1" />
            Log out
          </button>
        </div>
      </aside>

      <AnimatePresence>
        {menuOpen && (
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            onClick={closeMenu}
            aria-hidden="true"
            className="fixed inset-x-0 top-16 bottom-0 z-30 bg-[#141e32]/40 lg:hidden"
          />
        )}
      </AnimatePresence>

      <motion.main
        key={pathname}
        initial={{ opacity: 0 }}
        animate={{ opacity: 1 }}
        transition={{ duration: 0.3 }}
        className="min-w-0 px-4 pt-6 pb-12 lg:px-10 lg:pt-10 lg:pb-14"
      >
        {children}
      </motion.main>
    </div>
  )
}
