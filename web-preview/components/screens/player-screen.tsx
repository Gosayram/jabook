"use client"

import { useState } from "react"
import Image from "next/image"
import {
  ChevronDown,
  BarChart3,
  Heart,
  SkipBack,
  SkipForward,
  Play,
  Disc3,
  Gauge,
  Sliders,
  Moon,
  ListMusic,
  Bookmark,
} from "lucide-react"
import { nowPlaying } from "@/lib/data"

export function PlayerScreen() {
  const [vinyl, setVinyl] = useState(false)

  return (
    <div className="relative flex h-full flex-col bg-background pt-11 text-foreground">
      {/* blurred cover backdrop */}
      <div className="pointer-events-none absolute inset-0">
        <Image src={nowPlaying.cover || "/placeholder.svg"} alt="" fill className="scale-125 object-cover opacity-40 blur-2xl" />
        <div className="absolute inset-0 bg-[var(--scrim)]" />
      </div>

      <div className="relative flex h-full flex-col px-6">
        {/* top bar */}
        <header className="flex items-center justify-between py-2">
          <button className="grid h-9 w-9 place-items-center rounded-full bg-surface-variant/60 backdrop-blur" aria-label="Свернуть">
            <ChevronDown className="h-5 w-5" />
          </button>
          <div className="text-center">
            <p className="text-[10px] uppercase tracking-widest text-on-surface-variant">Сейчас играет</p>
          </div>
          <button className="grid h-9 w-9 place-items-center rounded-full bg-surface-variant/60 backdrop-blur" aria-label="Статистика">
            <BarChart3 className="h-5 w-5" />
          </button>
        </header>

        {/* artwork */}
        <div className="flex flex-1 items-center justify-center py-4">
          <button onClick={() => setVinyl((v) => !v)} className="relative" aria-label="Переключить винил">
            {vinyl ? (
              <div className="relative grid h-64 w-64 place-items-center">
                <div className="vinyl-spin absolute inset-0 rounded-full bg-[conic-gradient(from_0deg,#111,#2a2a2a,#111,#2a2a2a,#111)] shadow-2xl">
                  <div className="absolute inset-3 rounded-full border border-white/5" />
                  <div className="absolute inset-7 rounded-full border border-white/5" />
                  <div className="absolute inset-12 rounded-full border border-white/5" />
                </div>
                <div className="relative h-28 w-28 overflow-hidden rounded-full ring-4 ring-black/40">
                  <Image src={nowPlaying.cover || "/placeholder.svg"} alt={nowPlaying.title} fill className="object-cover" />
                  <div className="absolute left-1/2 top-1/2 h-3 w-3 -translate-x-1/2 -translate-y-1/2 rounded-full bg-background ring-2 ring-white/30" />
                </div>
              </div>
            ) : (
              <div
                className="relative h-64 w-64 overflow-hidden rounded-[1.75rem] shadow-2xl"
                style={{ boxShadow: "0 25px 60px -15px var(--primary-glow)" }}
              >
                <Image src={nowPlaying.cover || "/placeholder.svg"} alt={nowPlaying.title} fill className="object-cover" />
              </div>
            )}
          </button>
        </div>

        {/* title + chapter chip */}
        <div className="flex items-start justify-between gap-3">
          <div className="min-w-0">
            <h2 className="truncate text-[22px] font-bold leading-tight text-balance">{nowPlaying.title}</h2>
            <p className="mt-0.5 truncate text-[13px] text-on-surface-variant">{nowPlaying.author}</p>
          </div>
          <button className="grid h-10 w-10 shrink-0 place-items-center rounded-full bg-surface-variant/70" aria-label="В избранное">
            <Heart className="h-5 w-5 fill-primary text-primary" />
          </button>
        </div>
        <span className="mt-2 w-fit rounded-full bg-surface-variant/70 px-3 py-1 text-[11px] font-medium text-on-surface-variant">
          Глава {nowPlaying.currentChapter} / {nowPlaying.chapters}
        </span>

        {/* squiggly slider */}
        <div className="mt-5">
          <div className="flex h-6 items-center">
            <div className="relative flex h-6 flex-1 items-center overflow-hidden">
              <div className="squiggly h-4 text-primary" style={{ width: "42%" }} />
              <div className="h-[3px] flex-1 rounded-full bg-on-surface-variant/30" />
              <div className="absolute h-3.5 w-3.5 rounded-full bg-primary shadow" style={{ left: "calc(42% - 7px)" }} />
            </div>
          </div>
          <div className="mt-1 flex justify-between text-[11px] tabular-nums text-on-surface-variant">
            <span>12:45</span>
            <span>-48:10</span>
          </div>
        </div>

        {/* transport controls */}
        <div className="mt-3 flex items-center justify-between">
          <button className="grid h-11 w-11 place-items-center rounded-full text-on-surface-variant" aria-label="Назад">
            <SkipBack className="h-6 w-6" />
          </button>
          <button className="relative grid h-11 w-11 place-items-center rounded-full text-foreground" aria-label="-15с">
            <Rewind15 />
          </button>
          <button
            className="grid h-[68px] w-[68px] place-items-center rounded-full bg-primary text-on-primary"
            style={{ boxShadow: "0 10px 30px -6px var(--primary-glow)" }}
            aria-label="Пауза"
          >
            <Play className="h-8 w-8 fill-current" />
          </button>
          <button className="relative grid h-11 w-11 place-items-center rounded-full text-foreground" aria-label="+30с">
            <Forward30 />
          </button>
          <button className="grid h-11 w-11 place-items-center rounded-full text-on-surface-variant" aria-label="Вперёд">
            <SkipForward className="h-6 w-6" />
          </button>
        </div>

        {/* bottom sheet pill row */}
        <div className="no-scrollbar mb-5 mt-5 flex gap-2 overflow-x-auto">
          <Pill icon={<Gauge className="h-4 w-4" />} label="1.0×" />
          <Pill icon={<Moon className="h-4 w-4" />} label="Таймер" />
          <Pill icon={<Sliders className="h-4 w-4" />} label="Эквалайзер" />
          <Pill icon={<ListMusic className="h-4 w-4" />} label="Главы" />
          <Pill icon={<Bookmark className="h-4 w-4" />} label="Закладки" />
          <Pill icon={<Disc3 className="h-4 w-4" />} label="Винил" onClick={() => setVinyl((v) => !v)} />
        </div>
      </div>
    </div>
  )
}

function Pill({ icon, label, onClick }: { icon: React.ReactNode; label: string; onClick?: () => void }) {
  return (
    <button
      onClick={onClick}
      className="flex shrink-0 items-center gap-1.5 rounded-full bg-surface-variant/70 px-3.5 py-2 text-[12px] font-medium text-foreground backdrop-blur"
    >
      <span className="text-primary">{icon}</span>
      {label}
    </button>
  )
}

function Rewind15() {
  return (
    <span className="relative grid place-items-center">
      <SkipBack className="h-7 w-7" strokeWidth={1.5} />
      <span className="absolute text-[8px] font-bold">15</span>
    </span>
  )
}
function Forward30() {
  return (
    <span className="relative grid place-items-center">
      <SkipForward className="h-7 w-7" strokeWidth={1.5} />
      <span className="absolute text-[8px] font-bold">30</span>
    </span>
  )
}
