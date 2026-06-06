"use client"

import type { ReactNode } from "react"

export function PhoneFrame({
  children,
  label,
  flavor,
  amoled,
}: {
  children: ReactNode
  label: string
  flavor: "beta" | "prod"
  amoled: boolean
}) {
  return (
    <div className="flex shrink-0 flex-col items-center gap-3">
      <div className="flex items-center gap-2 text-xs font-medium text-neutral-400">
        <span className="inline-block h-1.5 w-1.5 rounded-full bg-neutral-600" />
        {label}
      </div>
      <div
        data-flavor={flavor}
        data-amoled={amoled}
        className="relative h-[760px] w-[372px] rounded-[2.75rem] border border-neutral-800 bg-neutral-950 p-2.5 shadow-2xl shadow-black/60"
      >
        {/* side buttons */}
        <div className="absolute -left-[3px] top-28 h-12 w-[3px] rounded-l bg-neutral-800" />
        <div className="absolute -left-[3px] top-44 h-12 w-[3px] rounded-l bg-neutral-800" />
        <div className="absolute -right-[3px] top-36 h-16 w-[3px] rounded-r bg-neutral-800" />
        <div className="relative h-full w-full overflow-hidden rounded-[2.1rem] bg-background">
          {/* dynamic island / status bar */}
          <div className="pointer-events-none absolute inset-x-0 top-0 z-30 flex h-11 items-center justify-between px-6 pt-1 text-[11px] font-semibold text-foreground">
            <span>9:41</span>
            <div className="absolute left-1/2 top-2 h-5 w-24 -translate-x-1/2 rounded-full bg-black" />
            <div className="flex items-center gap-1.5">
              <SignalIcon />
              <WifiIcon />
              <BatteryIcon />
            </div>
          </div>
          <div className="no-scrollbar h-full w-full overflow-y-auto">{children}</div>
        </div>
      </div>
    </div>
  )
}

function SignalIcon() {
  return (
    <svg width="16" height="11" viewBox="0 0 16 11" fill="currentColor" aria-hidden>
      <rect x="0" y="7" width="3" height="4" rx="1" />
      <rect x="4.5" y="5" width="3" height="6" rx="1" />
      <rect x="9" y="2.5" width="3" height="8.5" rx="1" />
      <rect x="13.5" y="0" width="3" height="11" rx="1" />
    </svg>
  )
}
function WifiIcon() {
  return (
    <svg width="16" height="12" viewBox="0 0 16 12" fill="currentColor" aria-hidden>
      <path d="M8 11.5 5.8 8.8a3.4 3.4 0 0 1 4.4 0L8 11.5Z" />
      <path d="M3.2 5.6 4.6 7.3a5.3 5.3 0 0 1 6.8 0l1.4-1.7a7.6 7.6 0 0 0-9.6 0Z" opacity=".9" />
      <path d="M1 2.9l1.3 1.7a9.8 9.8 0 0 1 11.4 0L15 2.9a12 12 0 0 0-14 0Z" opacity=".8" />
    </svg>
  )
}
function BatteryIcon() {
  return (
    <svg width="26" height="13" viewBox="0 0 26 13" fill="none" aria-hidden>
      <rect x="0.5" y="0.5" width="22" height="12" rx="3.5" stroke="currentColor" opacity=".5" />
      <rect x="2" y="2" width="17" height="9" rx="2" fill="currentColor" />
      <rect x="24" y="4" width="2" height="5" rx="1" fill="currentColor" opacity=".5" />
    </svg>
  )
}
