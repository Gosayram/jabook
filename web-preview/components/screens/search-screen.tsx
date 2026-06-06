"use client"

import { Search, X, TrendingUp, Download } from "lucide-react"
import Image from "next/image"
import { books } from "@/lib/data"

const trending = ["Стивен Кинг", "Фантастика 2026", "Гарри Поттер", "Детективы", "Толстой", "Бизнес"]

export function SearchScreen() {
  return (
    <div className="flex h-full flex-col bg-background pt-11 text-foreground">
      <header className="px-5 pb-3 pt-2">
        <div className="flex items-center gap-2 rounded-2xl bg-surface-variant px-4 py-3">
          <Search className="h-5 w-5 text-on-surface-variant" />
          <input
            defaultValue="северное"
            className="flex-1 bg-transparent text-[15px] outline-none placeholder:text-on-surface-variant"
            placeholder="Поиск книг, авторов…"
          />
          <button aria-label="Очистить" className="text-on-surface-variant">
            <X className="h-4 w-4" />
          </button>
        </div>
      </header>

      <div className="px-5 pb-4">
        <p className="mb-2 flex items-center gap-1.5 text-[13px] font-semibold text-on-surface-variant">
          <TrendingUp className="h-4 w-4" /> Популярные запросы
        </p>
        <div className="flex flex-wrap gap-2">
          {trending.map((t) => (
            <span key={t} className="rounded-full bg-surface-variant px-3 py-1.5 text-[12px] text-foreground">
              {t}
            </span>
          ))}
        </div>
      </div>

      <div className="flex-1 px-5">
        <p className="mb-3 text-[13px] font-semibold text-on-surface-variant">Результаты на RuTracker</p>
        <div className="flex flex-col gap-3">
          {books.map((b) => (
            <article key={b.id} className="flex items-center gap-3 rounded-2xl bg-surface-variant/60 p-2.5">
              <div className="relative h-16 w-12 shrink-0 overflow-hidden rounded-lg">
                <Image src={b.cover || "/placeholder.svg"} alt="" fill className="object-cover" />
              </div>
              <div className="min-w-0 flex-1">
                <p className="truncate text-[14px] font-semibold">{b.title}</p>
                <p className="truncate text-[12px] text-on-surface-variant">{b.author}</p>
                <p className="mt-0.5 text-[11px] text-on-surface-variant">{b.duration} · 256 kbps · 312 МБ</p>
              </div>
              <button
                className="grid h-10 w-10 shrink-0 place-items-center rounded-full bg-primary text-on-primary"
                aria-label="Скачать"
              >
                <Download className="h-5 w-5" />
              </button>
            </article>
          ))}
        </div>
      </div>
    </div>
  )
}
