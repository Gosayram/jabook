export type Book = {
  id: string
  title: string
  author: string
  cover: string
  progress: number // 0..1
  chapters: number
  currentChapter: number
  duration: string
  favorite?: boolean
  status?: "listening" | "completed" | "new"
}

export const books: Book[] = [
  {
    id: "1",
    title: "Северное сияние",
    author: "Филип Пулман",
    cover: "/covers/cover1.png",
    progress: 0.42,
    chapters: 24,
    currentChapter: 10,
    duration: "11 ч 20 мин",
    favorite: true,
    status: "listening",
  },
  {
    id: "2",
    title: "Война и мир",
    author: "Лев Толстой",
    cover: "/covers/cover2.png",
    progress: 0.08,
    chapters: 60,
    currentChapter: 5,
    duration: "61 ч 04 мин",
    status: "listening",
  },
  {
    id: "3",
    title: "Гиперион",
    author: "Дэн Симмонс",
    cover: "/covers/cover3.png",
    progress: 1,
    chapters: 18,
    currentChapter: 18,
    duration: "20 ч 41 мин",
    favorite: true,
    status: "completed",
  },
  {
    id: "4",
    title: "Тёмный город",
    author: "Реймонд Чандлер",
    cover: "/covers/cover4.png",
    progress: 0,
    chapters: 14,
    currentChapter: 1,
    duration: "8 ч 12 мин",
    status: "new",
  },
]

export const nowPlaying = books[0]

export const chapterList = Array.from({ length: 24 }).map((_, i) => ({
  index: i + 1,
  title:
    [
      "Графин с токайским",
      "Идея о Севере",
      "Тени Иордан-колледжа",
      "Алетиометр",
      "Приём у магистра",
      "Бал-маскарад",
      "Джон Фаа",
      "Разочарование",
      "Шпион из Лондона",
      "Заколдованный медведь",
    ][i] ?? `Глава ${i + 1}`,
  duration: ["28:14", "31:02", "24:48", "41:10", "19:33", "36:27", "22:05", "29:51", "33:40", "27:12"][i % 10],
}))
