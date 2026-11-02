import { useEffect, useId, useRef, useState } from 'react'

export default function TermInfo({ term, meaning, usage }) {
  const [open, setOpen] = useState(false)
  const rootRef = useRef(null)
  const buttonRef = useRef(null)
  const popoverId = useId()

  useEffect(() => {
    if (!open) return

    const handlePointerDown = (event) => {
      if (!rootRef.current?.contains(event.target)) setOpen(false)
    }
    const handleKeyDown = (event) => {
      if (event.key === 'Escape') {
        setOpen(false)
        buttonRef.current?.focus()
      }
    }
    document.addEventListener('pointerdown', handlePointerDown)
    document.addEventListener('keydown', handleKeyDown)
    return () => {
      document.removeEventListener('pointerdown', handlePointerDown)
      document.removeEventListener('keydown', handleKeyDown)
    }
  }, [open])

  const close = () => {
    setOpen(false)
    buttonRef.current?.focus()
  }

  return (
    <span className={`term-info${open ? ' is-open' : ''}`} ref={rootRef}>
      <button
        ref={buttonRef}
        type="button"
        className="term-info-button"
        aria-label={`查看${term}解释`}
        aria-expanded={open}
        aria-controls={open ? popoverId : undefined}
        onClick={() => setOpen((value) => !value)}
      >
        <svg viewBox="0 0 16 16" aria-hidden="true">
          <circle cx="8" cy="8" r="6.25" />
          <path d="M8 7.25v3.5M8 4.75h.01" />
        </svg>
      </button>
      {open && (
        <>
          <button type="button" className="term-info-backdrop" tabIndex={-1} aria-label="关闭解释" onClick={close} />
          <span className="term-info-popover" id={popoverId} role="dialog" aria-label={`${term}解释`}>
            <button type="button" className="term-info-close" aria-label="关闭解释" onClick={close}>×</button>
            <strong>{term}</strong>
            <span className="term-info-section"><b>是什么</b><span>{meaning}</span></span>
            <span className="term-info-section"><b>用来做什么</b><span>{usage}</span></span>
          </span>
        </>
      )}
    </span>
  )
}
