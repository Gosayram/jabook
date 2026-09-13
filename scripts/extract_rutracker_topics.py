#!/usr/bin/env python3
"""Extract topic HTML from RuTracker via firecrawl browser profile.

Usage: Run after firecrawl browser session is active with --profile rutracker2.
Outputs raw post body HTML for parser fixture creation.
"""
import subprocess
import json
import time
import os

TOPIC_IDS = [
    "6395270",   # Стругацкие - Пикник на обочине (well-filled)
    "5551472",   # Лев Толстой
    "5114888",   # Гарри Поттер
    "4256839",   # Донцова (mass-market)
    "5463270",   # Шерлок Холмс
    "6062633",   # 1984 Оруэлл
    "5724870",   # Маленький принц
    "3898498",   # Калашников (non-fiction)
    "4923015",   # Набоков
    "6658033",   # Recent upload
]

BASE_URL = "https://rutracker.net/forum/viewtopic.php?t="
OUTPUT_DIR = "android/app/src/test/resources/fixtures/rutracker/topics"
os.makedirs(OUTPUT_DIR, exist_ok=True)


def browser_cmd(profile, command):
    result = subprocess.run(
        ["firecrawl", "browser", f"--profile", profile, command],
        capture_output=True, text=True, timeout=30
    )
    return result.stdout.strip()


def extract_topic(profile, topic_id):
    url = f"{BASE_URL}{topic_id}"
    browser_cmd(profile, f"open {url}")
    time.sleep(4)

    js = """'(() => {
        const post = document.querySelector(".post_body");
        if (!post) return JSON.stringify({error: "no_post_body", title: document.title});
        const maintitle = document.querySelector("h1.maintitle a, h1.maintitle");
        const sizeEl = document.querySelector("#tor-size-humn, #tor-size-hf, span#tor-size-humn");
        const magnet = document.querySelector("a[href*=\\"magnet:\\"]");
        const seedEl = document.querySelector("span.seedmed b, span.seed b, b.seedmed");
        const leechEl = document.querySelector("span.leechmed b, span.leech b, b.leechmed");

        // Check metadata completeness
        const postText = post.textContent;
        const hasAuthor = postText.includes("Автор");
        const hasPerformer = postText.includes("Исполнитель");
        const hasGenre = postText.includes("Жанр");
        const hasYear = postText.includes("Год выпуска") || postText.includes("Год");
        const hasDescription = postText.includes("Описание") || postText.includes("Доп. информация");
        const hasCover = post.querySelector("img.postImg") !== null;

        return JSON.stringify({
            topicId: "PLACEHOLDER",
            title: maintitle ? maintitle.textContent.trim().substring(0, 120) : "none",
            size: sizeEl ? sizeEl.textContent.trim() : "none",
            hasMagnet: !!magnet,
            seeders: seedEl ? seedEl.textContent.trim() : "0",
            leechers: leechEl ? leechEl.textContent.trim() : "0",
            completeness: {
                author: hasAuthor,
                performer: hasPerformer,
                genre: hasGenre,
                year: hasYear,
                description: hasDescription,
                cover: hasCover,
            },
            htmlLength: post.outerHTML.length,
        });
    })()'""".replace("PLACEHOLDER", topic_id)

    result = browser_cmd(profile, f"eval {js}")
    return result


def extract_topic_html(profile, topic_id):
    url = f"{BASE_URL}{topic_id}"
    browser_cmd(profile, f"open {url}")
    time.sleep(4)

    js = """'(() => {
        const post = document.querySelector(".post_body");
        if (!post) return "ERROR: no post_body found";
        return post.outerHTML.substring(0, 60000);
    })()'"""

    result = browser_cmd(profile, f"eval {js}")
    return result


if __name__ == "__main__":
    profile = "rutracker2"
    print("=== Phase 1: Survey topic completeness ===")

    results = []
    for tid in TOPIC_IDS:
        time.sleep(2)
        print(f"\n--- Topic {tid} ---")
        try:
            info = extract_topic(profile, tid)
            # Clean the output (firecrawl may add prefixes)
            clean = info.replace("\\n", "\n").strip().strip('"')
            try:
                data = json.loads(clean.encode().decode('unicode_escape'))
                print(json.dumps(data, ensure_ascii=False, indent=2))
                results.append(data)
            except:
                print(f"Raw: {clean[:200]}")
        except Exception as e:
            print(f"Error: {e}")

    # Save survey
    with open(f"{OUTPUT_DIR}/../survey.json", "w") as f:
        json.dump(results, f, ensure_ascii=False, indent=2)

    print("\n\n=== Phase 2: Extract HTML for worst-case fixtures ===")
    # Pick the most interesting topics (poorly filled ones)
    for tid in TOPIC_IDS[:5]:
        time.sleep(2)
        print(f"\n--- Extracting HTML for topic {tid} ---")
        try:
            html = extract_topic_html(profile, tid)
            clean = html.strip().strip('"').encode().decode('unicode_escape')
            outpath = f"{OUTPUT_DIR}/topic_{tid}.html"
            with open(outpath, "w", encoding="utf-8") as f:
                f.write(clean)
            print(f"Saved {len(clean)} bytes to {outpath}")
        except Exception as e:
            print(f"Error: {e}")
