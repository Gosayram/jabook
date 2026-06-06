import type { Metadata } from "next"
import { Inter } from "next/font/google"
import "./globals.css"

const inter = Inter({ subsets: ["latin", "cyrillic"], variable: "--font-inter" })

export const metadata: Metadata = {
  title: "JaBook — Design Visualization",
  description: "Interactive web visualization of the JaBook Android audiobook app screens.",
}

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="ru" className={`${inter.variable} bg-[#0c0c0c]`}>
      <body className="bg-[#0c0c0c] text-neutral-200 antialiased">{children}</body>
    </html>
  )
}
