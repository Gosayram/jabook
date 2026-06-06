"use client"

import { Search, LayoutGrid, Menu, Heart, Play, Library, Settings } from "lucide-react"
import Image from "next/image"
import { books, nowPlaying } from "@/lib/data"

const filters = ["Все", "Слушаю", "Избранное", "Завершено"]

export function LibraryScreen() {
  return (
    <div className="flex h-full flex-col bg-background pt-11 text-foreground">
      {/* top app bar */}
      <header className="flex items-center justify-between px-5 pb-3 pt-2">
        <button className="grid h-9 w-9 place-items-center rounded-full text-on-surface-variant" aria-label="Меню">
          <Menu className="h-5 w-5" />
        </button>
        <h1 className="text-[19px] font-bold tracking-tight">Моя библиотека</h1>
        <div className="flex items-center gap-1">
          <button className="grid h-9 w-9 place-items-center rounded-full text-on-surface-variant" aria-label="Поиск">
            <Search className="h-5 w-5" />
          </button>
          <button className="grid h-9 w-9 place-items-center rounded-full text-on-surface-variant" aria-label="Вид">
            <LayoutGrid className="h-5 w-5" />
          </button>
        </div>
      </header>

      {/* filter chips */}
      <div className="no-scrollbar flex gap-2 overflow-x-auto px-5 pb-4">
        {filters.map((f, i) => (
          <button
            key={f}
            className={`shrink-0 rounded-full px-4 py-1.5 text-[13px] font-medium transition ${
              i === 0
                ? "bg-primary text-on-primary"
                : "bg-surface-variant text-on-surface-variant"
            }`}
          >
            {f}
          </button>
        ))}
      </div>

      {/* hero - continue listening */}
      <div className="px-5 pb-5">
        <p className="mb-2 text-[13px] font-semibold text-on-surface-variant">Продолжить</p>
        <div className="relative overflow-hidden rounded-3xl bg-surface-variant p-3">
          <div className="flex gap-3">
            <div className="relative h-24 w-[68px] shrink-0 overflow-hidden rounded-xl">
              <Image src={nowPlaying.cover || "/placeholder.svg"} alt="" fill className="object-cover" />
            </div>
            <div className="flex min-w-0 flex-1 flex-col justify-between py-0.5">
              <div>
                <p className="truncate text-[15px] font-semibold">{nowPlaying.title}</p>
                <p className="truncate text-[12px] text-on-surface-variant">{nowPlaying.author}</p>
                <p className="mt-1 text-[11px] text-on-surface-variant">
                  Глава {nowPlaying.currentChapter} из {nowPlaying.chapters}
                </p>
              </div>
              <div className="flex items-center gap-3">
                <div className="h-1 flex-1 overflow-hidden rounded-full bg-background/60">
                  <div className="h-full rounded-full bg-primary" style={{ width: `${nowPlaying.progress * 100}%` }} />
                </div>
                <button className="grid h-10 w-10 place-items-center rounded-full bg-primary text-on-primary shadow-lg">
                  <Play className="h-5 w-5 fill-current" />
                </button>
              </div>
            </div>
          </div>
        </div>
      </div>

      {/* grid */}
      <div className="flex-1 px-5">
        <p className="mb-3 text-[13px] font-semibold text-on-surface-variant">Все книги</p>
        <div className="grid grid-cols-2 gap-4 pb-4">
          {books.map((b) => (
            <article key={b.id} className="flex flex-col gap-2">
              <div className="relative aspect-[2/3] overflow-hidden rounded-2xl bg-surface-variant">
                <Image src={b.cover || "/placeholder.svg"} alt={b.title} fill className="object-cover" />
                <button
                  aria-label="В избранное"
                  className="absolute right-2 top-2 grid h-7 w-7 place-items-center rounded-full bg-black/45 backdrop-blur"
                >
                  <Heart className={`h-4 w-4 ${b.favorite ? "fill-primary text-primary" : "text-white"}`} />
                </button>
                {b.progress > 0 && (
                  <div className="absolute inset-x-0 bottom-0 h-1 bg-black/40">
                    <div className="h-full bg-primary" style={{ width: `${b.progress * 100}%` }} />
                  </div>
                )}
              </div>
              <div className="px-0.5">
                <p className="line-clamp-2 text-[13px] font-semibold leading-tight">{b.title}</p>
                <p className="mt-0.5 truncate text-[11px] text-on-surface-variant">{b.author}</p>
              </div>
            </article>
          ))}
        </div>
      </div>

      {/* mini player */}
      <div className="sticky bottom-[60px] mx-3 flex items-center gap-3 rounded-2xl bg-surface-variant px-3 py-2.5 shadow-lg">
        <div className="relative h-10 w-10 shrink-0 overflow-hidden rounded-lg">
          <Image src={nowPlaying.cover || "/placeholder.svg"} alt="" fill className="object-cover" />
        </div>
        <div className="min-w-0 flex-1">
          <p className="truncate text-[12px] font-semibold">{nowPlaying.title}</p>
          <p className="truncate text-[10px] text-on-surface-variant">{nowPlaying.author}</p>
        </div>
        <button className="grid h-9 w-9 place-items-center rounded-full bg-primary text-on-primary">
          <Play className="h-4 w-4 fill-current" />
        </button>
        <div className="absolute inset-x-3 bottom-0 h-0.5 overflow-hidden rounded-full bg-background/50">
          <div className="h-full bg-primary" style={{ width: `${nowPlaying.progress * 100}%` }} />
        </div>
      </div>

      {/* bottom nav */}
      <nav className="flex items-center justify-around border-t border-outline/15 bg-surface px-2 pb-5 pt-2">
        <NavItem icon={<Library className="h-5 w-5" />} label="Библиотека" active />
        <NavItem icon={<Settings className="h-5 w-5" />} label="Настройки" />
      </nav>
    </div>
  )
}

function NavItem({ icon, label, active }: { icon: React.ReactNode; label: string; active?: boolean }) {
  return (
    <button className={`flex flex-1 flex-col items-center gap-1 py-1 ${active ? "text-primary" : "text-on-surface-variant"}`}>
      <span className={active ? "rounded-full bg-primary/15 px-5 py-1" : "px-5 py-1"}>{icon}</span>
      <span className="text-[11px] font-medium">{label}</span>
    </button>
  )
}
