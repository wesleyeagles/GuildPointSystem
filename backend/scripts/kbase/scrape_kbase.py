"""Scrape cerberus-games.com/kbase/items (FORCE&STEEL, English) into one JSON file.

Usage: python -I scrape_kbase.py <out.json>
"""
import html
import http.cookiejar
import json
import re
import sys
import time
import urllib.request
from concurrent.futures import ThreadPoolExecutor

BASE = "https://cerberus-games.com"
TYPES = ["ih", "iu", "il", "is", "ig", "id", "ik", "ia", "ii", "iw"]
UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64)"

jar = http.cookiejar.CookieJar()
opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(jar))


def get(path, tries=5):
    for i in range(tries):
        try:
            req = urllib.request.Request(BASE + path, headers={"User-Agent": UA})
            with opener.open(req, timeout=30) as r:
                return r.read().decode("utf-8", "replace")
        except Exception as e:  # noqa: BLE001
            if i == tries - 1:
                raise
            time.sleep(2 + i * 3)


def setup():
    page = get("/misc/language")
    m = re.search(r"/misc/language\?(language_id=1&amp;[^\"]*)", page)
    if not m:
        raise SystemExit("language link not found")
    get("/misc/language?" + m.group(1).replace("&amp;", "&"))
    get("/kbase/server?srv=1")


def text(s):
    return re.sub(r"\s+", " ", html.unescape(re.sub(r"<[^>]+>", " ", s))).strip()


CARD = re.compile(r'<div class="KBaseItemCard">(.*?)(?=<div class="KBaseItemCard">|<div class="KBasePagination|$)', re.S)


def parse_card(c, t):
    item = {"t": t}
    m = re.search(r"details\?code=([A-Za-z0-9_]+)", c)
    if not m:
        return None
    item["c"] = m.group(1).lower()
    m = re.search(r'class="KBaseItemName">\s*<a[^>]*>(.*?)</a>', c, re.S)
    item["n"] = text(m.group(1)) if m else ""
    m = re.search(r"KBaseIconSprite grade(\d+)", c)
    item["g"] = int(m.group(1)) if m else 0
    m = re.search(r'spr_icon (spr_\w+)"\s*style="background-position:\s*(-?\d+)px\s+(-?\d+)px', c)
    if m:
        item["spr"] = m.group(1)
        item["pos"] = [int(m.group(2)), int(m.group(3))]
    m = re.search(r'KBaseItemRace (r\d+)', c)
    if m:
        item["r"] = m.group(1)[1:]
    m = re.search(r'class="KBaseItemLevel">(.*?)</div>', c, re.S)
    if m:
        lv = re.search(r"(\d+)", text(m.group(1)))
        item["l"] = int(lv.group(1)) if lv else 0
    m = re.search(r'class="KBaseItemUpgrade">(.*?)</div>', c, re.S)
    if m:
        item["up"] = text(m.group(1))
    m = re.search(r'class="KBaseItemPt">(.*?)</div>', c, re.S)
    if m:
        item["pt"] = text(m.group(1))
    m = re.search(r'class="KBaseItemParams">(.*?)</div>\s*(?:<div class="KBaseItemAbilities"|$)', c, re.S)
    if m:
        item["p"] = [x for x in (text(d) for d in re.findall(r"<div>(.*?)</div>", m.group(1), re.S)) if x]
    m = re.search(r'class="KBaseItemAbilities">(.*?)</ul>', c, re.S)
    if m:
        item["a"] = [x for x in (text(li) for li in re.findall(r"<li>(.*?)</li>", m.group(1), re.S)) if x and x != "---"]
    return item


def last_page(page):
    nums = [int(x) for x in re.findall(r'class="pageBtn[^"]*">\s*(\d+)', page)]
    return max(nums) if nums else 1


def scrape_page(t, p):
    page = get(f"/kbase/items/?type={t}&page={p}")
    return [x for x in (parse_card(c, t) for c in CARD.findall(page)) if x]


def main(out):
    setup()
    first = {t: get(f"/kbase/items/?type={t}&page=1") for t in TYPES}
    if "FORCE&amp;STEEL</option>" not in first["iw"] or 'value="1" selected' not in first["iw"]:
        raise SystemExit("server is not FORCE&STEEL")
    jobs = []
    for t in TYPES:
        n = last_page(first[t])
        jobs += [(t, p) for p in range(1, n + 1)]
    print(f"{len(jobs)} pages", flush=True)
    items, done = {}, 0
    with ThreadPoolExecutor(4) as ex:
        for res in ex.map(lambda j: scrape_page(*j), jobs):
            for it in res:
                items.setdefault(it["c"], it)
            done += 1
            if done % 200 == 0:
                print(f"{done}/{len(jobs)} pages, {len(items)} items", flush=True)
    data = {
        "source": BASE + "/kbase/items/",
        "server": "CERBERUS - FORCE&STEEL",
        "scrapedAt": time.strftime("%Y-%m-%d"),
        "items": sorted(items.values(), key=lambda x: (x["t"], x["c"])),
    }
    with open(out, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, separators=(",", ":"))
    print(f"done: {len(items)} items -> {out}")


if __name__ == "__main__":
    main(sys.argv[1])
