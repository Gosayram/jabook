"use client"

import {
  ChevronLeft,
  Palette,
  Moon,
  Sliders,
  Download,
  Globe,
  Bell,
  Info,
  ChevronRight,
  Check,
} from "lucide-react"

export function SettingsScreen({ flavor }: { flavor: "beta" | "prod" }) {
  return (
    <div className="flex h-full flex-col bg-background pt-11 text-foreground">
      <header className="flex items-center gap-3 px-5 pb-3 pt-2">
        <button className="grid h-9 w-9 place-items-center rounded-full text-on-surface-variant" aria-label="Назад">
          <ChevronLeft className="h-5 w-5" />
        </button>
        <h1 className="text-[19px] font-bold tracking-tight">Настройки</h1>
      </header>

      <div className="flex-1 px-5 pb-6">
        {/* account header */}
        <div className="mb-5 flex items-center gap-3 rounded-3xl bg-surface-variant p-4">
          <div className="grid h-12 w-12 place-items-center rounded-full bg-primary text-on-primary text-lg font-bold">
            A
          </div>
          <div>
            <p className="text-[15px] font-semibold">Аккаунт RuTracker</p>
            <p className="text-[12px] text-on-surface-variant">user@jabook.app</p>
          </div>
        </div>

        <Section title="Оформление">
          <Row icon={<Palette className="h-5 w-5" />} title="Тема акцента" value={flavor === "beta" ? "Neon Green" : "Royal Gold"} />
          <ThemeModeRow />
          <Row icon={<Moon className="h-5 w-5" />} title="AMOLED-чёрный" toggle />
        </Section>

        <Section title="Воспроизведение">
          <Row icon={<Sliders className="h-5 w-5" />} title="Эквалайзер" value="5-полосный" />
          <Row icon={<Sliders className="h-5 w-5" />} title="Пропуск тишины" toggle on />
          <Row icon={<Sliders className="h-5 w-5" />} title="Нормализация громкости" toggle />
        </Section>

        <Section title="Загрузки и язык">
          <Row icon={<Download className="h-5 w-5" />} title="Папка загрузок" value="Внутренняя" />
          <Row icon={<Globe className="h-5 w-5" />} title="Язык" value="Русский" />
          <Row icon={<Bell className="h-5 w-5" />} title="Уведомления" toggle on />
        </Section>

        <Section title="О приложении">
          <Row icon={<Info className="h-5 w-5" />} title="Версия" value="1.0.0 (beta)" />
        </Section>
      </div>
    </div>
  )
}

function Section({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <div className="mb-5">
      <p className="mb-2 px-1 text-[12px] font-semibold uppercase tracking-wide text-on-surface-variant">{title}</p>
      <div className="overflow-hidden rounded-3xl bg-surface-variant/50">{children}</div>
    </div>
  )
}

function Row({
  icon,
  title,
  value,
  toggle,
  on,
}: {
  icon: React.ReactNode
  title: string
  value?: string
  toggle?: boolean
  on?: boolean
}) {
  return (
    <div className="flex items-center gap-3 border-b border-outline/10 px-4 py-3 last:border-0">
      <span className="text-on-surface-variant">{icon}</span>
      <span className="flex-1 text-[14px]">{title}</span>
      {value && <span className="text-[13px] text-on-surface-variant">{value}</span>}
      {toggle ? (
        <span className={`flex h-6 w-11 items-center rounded-full p-0.5 transition ${on ? "bg-primary" : "bg-on-surface-variant/30"}`}>
          <span className={`h-5 w-5 rounded-full bg-background shadow transition ${on ? "translate-x-5" : ""}`} />
        </span>
      ) : (
        !value && <ChevronRight className="h-4 w-4 text-on-surface-variant" />
      )}
    </div>
  )
}

function ThemeModeRow() {
  const modes = ["Светлая", "Тёмная", "Система"]
  return (
    <div className="flex items-center gap-3 border-b border-outline/10 px-4 py-3">
      <span className="text-on-surface-variant">
        <Moon className="h-5 w-5" />
      </span>
      <span className="flex-1 text-[14px]">Режим</span>
      <div className="flex gap-1 rounded-full bg-background/60 p-0.5">
        {modes.map((m, i) => (
          <span
            key={m}
            className={`flex items-center gap-1 rounded-full px-2.5 py-1 text-[11px] ${
              i === 1 ? "bg-primary text-on-primary" : "text-on-surface-variant"
            }`}
          >
            {i === 1 && <Check className="h-3 w-3" />}
            {m}
          </span>
        ))}
      </div>
    </div>
  )
}
