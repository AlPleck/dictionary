const CACHE_NAME = "vocabulary-shell-v2";
const APP_FILES = [
    "./",
    "./index.html",
    "./styles.css",
    "./app.js",
    "./data.js",
    "./ui-translations.js",
    "./ui-translations-ja-zh.js",
    "./decks/english_300.tsv",
    "./decks/english_100_nouns.tsv",
    "./decks/english_100_verbs.tsv",
    "./decks/japanese_100.tsv"
];

self.addEventListener("install", event => {
    event.waitUntil(caches.open(CACHE_NAME).then(cache => cache.addAll(APP_FILES)));
    self.skipWaiting();
});

self.addEventListener("activate", event => {
    event.waitUntil(
        caches.keys()
            .then(keys => Promise.all(keys.filter(key => key.startsWith("vocabulary-shell-") && key !== CACHE_NAME)
                .map(key => caches.delete(key))))
            .then(() => self.clients.claim())
    );
});

self.addEventListener("fetch", event => {
    const request = event.request;
    if (request.method !== "GET" || new URL(request.url).origin !== self.location.origin) return;

    event.respondWith(
        fetch(request).then(async response => {
            if (response.ok) {
                const cache = await caches.open(CACHE_NAME);
                await cache.put(request, response.clone());
            }
            return response;
        }).catch(async () => {
            const cached = await caches.match(request);
            if (cached) return cached;
            if (request.mode === "navigate") return (await caches.match("./index.html")) || Response.error();
            return Response.error();
        })
    );
});
