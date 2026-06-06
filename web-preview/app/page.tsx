"use client"

import { useState } from "react"
import { PhoneFrame } from "@/components/phone-frame"
import { LibraryScreen } from "@/components/screens/library-screen"
import { PlayerScreen } from "@/components/screens/player-screen"
import { SearchScreen } from "@/components/screens/search-screen"
import { SettingsScreen } from "@/components/screens/settings-screen"

type Flavor = "beta" | "prod"

export default function Page() {
  const [flavor, setFlavor] = useState<Flavor>("beta")
  const [amoled, setAmoled] = useState(false)

  return (
    <main className="min-h-screen bg-[#0c0c0c] text-neutral-200">
      {/* header / controls */}
      <div className="sticky top-0 z-50 border-b border-neutral-800 bg-[#0c0c0c]/90 backdrop-blur">
        <div className="mx-auto flex max-w-7xl flex-col gap-4 px-6 py-5 sm:flex-row sm:items-center sm:justify-between">
          <div>
            <h1 className="text-xl font-bold tracking-tight text-white">JaBook — Визуализация дизайна</h1>
            <p className="mt-0.5 text-sm text-neutral-400">
              Веб-превью экранов Android-приложения. Compose · Material 3
            </p>
          </div>
          <div className="flex flex-wrap items-center gap-3">
            <div className="flex rounded-full border border-neutral-800 bg-neutral-900 p-1">
              {(["beta", "prod"] as Flavor[]).map((f) => (
                <button
                  key={f}
                  onClick={() => setFlavor(f)}
                  className={`rounded-full px-4 py-1.5 text-sm font-medium transition ${
                    flavor === f ? "bg-white text-black" : "text-neutral-400 hover:text-white"
                  }`}
                >
                  {f === "beta" ? "Beta · Neon" : "Prod · Gold"}
                </button>
              ))}
            </div>
            <button
              onClick={() => setAmoled((a) => !a)}
              className={`rounded-full border px-4 py-2 text-sm font-medium transition ${
                amoled
                  ? "border-white bg-white text-black"
                  : "border-neutral-800 bg-neutral-900 text-neutral-300 hover:text-white"
              }`}
            >
              AMOLED {amoled ? "вкл" : "выкл"}
            </button>
          </div>
        </div>
      </div>

      {/* screens rail */}
      <div className="mx-auto max-w-[1600px] overflow-x-auto px-6 py-10">
        <div className="flex justify-start gap-8 lg:justify-center">
          <PhoneFrame label="Библиотека" flavor={flavor} amoled={amoled}>
            <LibraryScreen />
          </PhoneFrame>
          <PhoneFrame label="Плеер" flavor={flavor} amoled={amoled}>
            <PlayerScreen />
          </PhoneFrame>
          <PhoneFrame label="Поиск · RuTracker" flavor={flavor} amoled={amoled}>
            <SearchScreen />
          </PhoneFrame>
          <PhoneFrame label="Настройки" flavor={flavor} amoled={amoled}>
            <SettingsScreen flavor={flavor} />
          </PhoneFrame>
        </div>
      </div>

      <footer className="border-t border-neutral-800 px-6 py-6 text-center text-xs text-neutral-500">
        Цвета и компоненты воспроизведены из <code className="text-neutral-400">ui/theme/Color.kt</code> и{" "}
        <code className="text-neutral-400">compose/feature/*</code>. Это интерактивный макет, не сборка APK.
      </footer>
    </main>
  )
}
