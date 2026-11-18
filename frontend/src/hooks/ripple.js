// Ripple effect: call from onPointerDown on an element with `relative overflow-hidden`.
export function ripple(e) {
  const el = e.currentTarget
  const rect = el.getBoundingClientRect()
  const size = Math.max(rect.width, rect.height) * 2
  const span = document.createElement('span')
  span.className = 'pointer-events-none absolute rounded-full bg-white/35 animate-ripple'
  span.style.width = span.style.height = `${size}px`
  span.style.left = `${e.clientX - rect.left - size / 2}px`
  span.style.top = `${e.clientY - rect.top - size / 2}px`
  el.appendChild(span)
  span.addEventListener('animationend', () => span.remove())
}
