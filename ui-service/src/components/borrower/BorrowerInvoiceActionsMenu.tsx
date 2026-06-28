import { useEffect, useRef, useState } from 'react'
import { createPortal } from 'react-dom'

export type InvoiceActionItem = {
  id: string
  label: string
  onClick: () => void
  disabled?: boolean
}

type Props = {
  items: InvoiceActionItem[]
  busy?: boolean
}

const MENU_WIDTH = 168

export function BorrowerInvoiceActionsMenu({ items, busy }: Props) {
  const [open, setOpen] = useState(false)
  const [menuPos, setMenuPos] = useState<{ top: number; left: number } | null>(null)
  const rootRef = useRef<HTMLDivElement>(null)
  const menuRef = useRef<HTMLDivElement>(null)

  const closeMenu = () => {
    setOpen(false)
    setMenuPos(null)
  }

  const toggleMenu = () => {
    if (open) {
      closeMenu()
      return
    }
    const btn = rootRef.current?.querySelector('button')
    if (btn) {
      const rect = btn.getBoundingClientRect()
      const left = Math.max(8, Math.min(rect.right - MENU_WIDTH, window.innerWidth - MENU_WIDTH - 8))
      setMenuPos({ top: rect.bottom + 4, left })
    }
    setOpen(true)
  }

  useEffect(() => {
    if (!open) return
    const onDoc = (e: MouseEvent) => {
      const target = e.target as Node
      if (rootRef.current?.contains(target) || menuRef.current?.contains(target)) return
      closeMenu()
    }
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') closeMenu()
    }
    document.addEventListener('mousedown', onDoc)
    document.addEventListener('keydown', onKey)
    return () => {
      document.removeEventListener('mousedown', onDoc)
      document.removeEventListener('keydown', onKey)
    }
  }, [open])

  if (items.length === 0) {
    return <span className="text-xs text-slate-400">—</span>
  }

  return (
    <>
      <div ref={rootRef} className="inline-flex">
        <button
          type="button"
          disabled={busy}
          onClick={toggleMenu}
          className="inline-flex items-center gap-1 rounded-md border border-slate-300 bg-white px-2.5 py-1 text-xs font-medium text-slate-700 hover:bg-slate-50 disabled:opacity-50"
          aria-expanded={open}
          aria-haspopup="menu"
        >
          Actions
          <svg className="h-3.5 w-3.5 text-slate-500" viewBox="0 0 20 20" fill="currentColor" aria-hidden>
            <path
              fillRule="evenodd"
              d="M5.23 7.21a.75.75 0 011.06.02L10 10.94l3.71-3.71a.75.75 0 111.06 1.06l-4.24 4.25a.75.75 0 01-1.06 0L5.21 8.29a.75.75 0 01.02-1.08z"
              clipRule="evenodd"
            />
          </svg>
        </button>
      </div>
      {open && menuPos
        ? createPortal(
            <div
              ref={menuRef}
              role="menu"
              className="fixed z-[200] rounded-lg border border-slate-200 bg-white py-1 shadow-xl"
              style={{ top: menuPos.top, left: menuPos.left, width: MENU_WIDTH }}
            >
              {items.map((item) => (
                <button
                  key={item.id}
                  type="button"
                  role="menuitem"
                  disabled={item.disabled || busy}
                  onClick={() => {
                    closeMenu()
                    item.onClick()
                  }}
                  className="block w-full px-3 py-2 text-left text-xs text-slate-700 hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-50"
                >
                  {item.label}
                </button>
              ))}
            </div>,
            document.body,
          )
        : null}
    </>
  )
}
