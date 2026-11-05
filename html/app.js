/* Словарь — web port of the Android app (MainActivity.kt / WordStorage.kt) */

"use strict";

// ---------- Storage (equivalent of WordStorage over SharedPreferences) ----------

const KEY_WORDS = "words";
const KEY_TOPICS = "topics";
const KEY_THEME = "vocabulary_theme";
const KEY_LANGUAGE = "vocabulary_language";
const KEY_ENGLISH_300_SEEDED = "english_300_seeded";
const KEY_RU_ENG_300_SEEDED = "ru_eng_300_seeded";
const KEY_ENGLISH_100_NOUNS_SEEDED = "english_100_nouns_seeded";
const KEY_RU_ENG_100_NOUNS_SEEDED = "ru_eng_100_nouns_seeded";
const KEY_ENGLISH_100_VERBS_SEEDED = "english_100_verbs_seeded";
const KEY_RU_ENG_100_VERBS_SEEDED = "ru_eng_100_verbs_seeded";
const KEY_JAPANESE_100_SEEDED = "japanese_100_seeded";
const KEY_STUDY_EVENTS = "study_events";
const DEFAULT_NEW_CARDS_PER_DAY = 10;
const ENGLISH_300_ASSET = "decks/english_300.tsv";
const ENGLISH_300_TOPIC_NAME = "English 300";
const RU_ENG_300_TOPIC_NAME = "Ru-Eng 300";
const ENGLISH_300_WORD_COUNT = 300;
const ENGLISH_100_NOUNS_ASSET = "decks/english_100_nouns.tsv";
const ENGLISH_100_NOUNS_TOPIC_NAME = "English 100 Nouns";
const RU_ENG_100_NOUNS_TOPIC_NAME = "Ru-Eng 100 Существительные";
const ENGLISH_100_NOUNS_WORD_COUNT = 100;
const ENGLISH_100_VERBS_ASSET = "decks/english_100_verbs.tsv";
const ENGLISH_100_VERBS_TOPIC_NAME = "English 100 Verbs";
const RU_ENG_100_VERBS_TOPIC_NAME = "Ru-Eng 100 Глаголы";
const ENGLISH_100_VERBS_WORD_COUNT = 100;
const JAPANESE_100_ASSET = "decks/japanese_100.tsv";
const JAPANESE_100_TOPIC_NAME = "Japanese 100";
const JAPANESE_100_WORD_COUNT = 100;

function uuid() {
    if (typeof crypto !== "undefined" && crypto.randomUUID) return crypto.randomUUID();
    return "xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx".replace(/[xy]/g, c => {
        const r = Math.random() * 16 | 0;
        return (c === "x" ? r : (r & 0x3 | 0x8)).toString(16);
    });
}

function loadWords() {
    try {
        return JSON.parse(localStorage.getItem(KEY_WORDS) || "[]").map(w => ({
            id: w.id,
            original: w.original,
            translation: w.translation,
            originalLanguageTag: w.originalLanguageTag || "en",
            translationLanguageTag: w.translationLanguageTag || "ru",
            successCount: w.successCount || 0,
            nextReviewAtMillis: w.nextReviewAtMillis || 0,
            introducedAtMillis: w.introducedAtMillis || 0,
            introducedInTopicId: w.introducedInTopicId ?? w.topicId ?? null,
            intervalDays: w.intervalDays ?? (w.successCount > 0 ? 2 ** Math.min(w.successCount - 1, 9) : 0),
            topicId: w.topicIds?.[0] ?? w.topicId ?? null,
            topicIds: Array.isArray(w.topicIds) ? [...new Set(w.topicIds.filter(Boolean))] : (w.topicId ? [w.topicId] : []),
            transcription: w.transcription ?? null,
            example: w.example ?? null,
            exampleTranslation: w.exampleTranslation ?? null,
            quizAttemptCount: w.quizAttemptCount || 0,
            quizCorrectCount: w.quizCorrectCount || 0,
            quizCorrectStreak: w.quizCorrectStreak || 0
        }));
    } catch { return []; }
}

function loadTopics() {
    try {
        return JSON.parse(localStorage.getItem(KEY_TOPICS) || "[]").map(t => ({
            id: t.id,
            name: t.name,
            originalLanguageTag: t.originalLanguageTag || "en",
            translationLanguageTag: t.translationLanguageTag || "ru",
            newCardsPerDay: Number.isInteger(t.newCardsPerDay) ? Math.max(0, t.newCardsPerDay) : DEFAULT_NEW_CARDS_PER_DAY
        }));
    } catch { return []; }
}

function loadStudyEvents() {
    try {
        const events = JSON.parse(localStorage.getItem(KEY_STUDY_EVENTS) || "[]");
        return Array.isArray(events) ? events.filter(e => Number.isFinite(e.timestampMillis) && typeof e.correct === "boolean") : [];
    } catch { return []; }
}

function saveStudyEvents(events) {
    state.studyEvents = events;
    localStorage.setItem(KEY_STUDY_EVENTS, JSON.stringify(events));
}

function inferTopicLanguages(words) {
    const counts = new Map();
    words.forEach(word => {
        const key = `${word.originalLanguageTag || "en"}\t${word.translationLanguageTag || "ru"}`;
        counts.set(key, (counts.get(key) || 0) + 1);
    });
    const mostCommon = [...counts].sort((a, b) => b[1] - a[1])[0]?.[0];
    return mostCommon ? mostCommon.split("\t") : ["en", "ru"];
}

function migrateDeckLanguages() {
    let topics;
    let words;
    try { topics = JSON.parse(localStorage.getItem(KEY_TOPICS) || "[]"); }
    catch { topics = []; }
    try { words = JSON.parse(localStorage.getItem(KEY_WORDS) || "[]"); }
    catch { return; }
    if (!Array.isArray(words)) return;
    if (!Array.isArray(topics)) topics = [];

    let topicsChanged = false;
    try { topicsChanged = !Array.isArray(JSON.parse(localStorage.getItem(KEY_TOPICS) || "[]")); }
    catch { topicsChanged = true; }
    const wordsByTopic = new Map();
    words.forEach(word => {
        const ids = word.topicIds || (word.topicId ? [word.topicId] : []);
        ids.forEach(id => {
            const topicWords = wordsByTopic.get(id) || [];
            topicWords.push(word);
            wordsByTopic.set(id, topicWords);
        });
    });
    const migratedTopics = topics.map(topic => {
        if (topic.originalLanguageTag && topic.translationLanguageTag) return topic;
        const [originalLanguageTag, translationLanguageTag] = inferTopicLanguages(
            wordsByTopic.get(topic.id) || []
        );
        topicsChanged = true;
        return { ...topic, originalLanguageTag, translationLanguageTag };
    });
    const topicsById = new Map(migratedTopics.map(topic => [topic.id, topic]));
    let wordsChanged = false;
    const reconciledWords = words.map(word => {
        const resetUnintroducedSchedule = !word.introducedAtMillis && !word.successCount &&
            !word.intervalDays && word.nextReviewAtMillis > 0;
        const normalizedWord = resetUnintroducedSchedule ? { ...word, nextReviewAtMillis: 0 } : word;
        const ids = [...new Set([...(normalizedWord.topicIds || []), ...(normalizedWord.topicId ? [normalizedWord.topicId] : [])])]
            .filter(id => {
                const topic = topicsById.get(id);
                return topic && (normalizedWord.originalLanguageTag || "en") === topic.originalLanguageTag &&
                    (normalizedWord.translationLanguageTag || "ru") === topic.translationLanguageTag;
            });
        const legacyIntroTopicId = normalizedWord.introducedInTopicId ?? normalizedWord.topicId;
        const introTopicId = ids.includes(legacyIntroTopicId) ? legacyIntroTopicId : (ids[0] || null);
        if (resetUnintroducedSchedule || ids.length !== (word.topicIds || (word.topicId ? [word.topicId] : [])).length ||
            word.topicId !== ids[0] || word.introducedInTopicId !== introTopicId) wordsChanged = true;
        return { ...normalizedWord, topicIds: ids, topicId: ids[0] || null, introducedInTopicId: introTopicId };
    });
    const pairs = [...new Set(reconciledWords.filter(word => !word.topicIds.length)
        .map(word => `${word.originalLanguageTag || "en"}\t${word.translationLanguageTag || "ru"}`))];
    pairs.forEach(pair => {
        const [originalLanguageTag, translationLanguageTag] = pair.split("\t");
        let deck = migratedTopics.find(topic => topic.originalLanguageTag === originalLanguageTag &&
            topic.translationLanguageTag === translationLanguageTag);
        if (!deck) {
            deck = { id: uuid(), name: `Мои слова (${originalLanguageTag.toUpperCase()} → ${translationLanguageTag.toUpperCase()})`,
                originalLanguageTag, translationLanguageTag,
                newCardsPerDay: DEFAULT_NEW_CARDS_PER_DAY };
            migratedTopics.push(deck);
            topicsChanged = true;
        }
        reconciledWords.forEach((word, index) => {
            if (!word.topicIds.length && word.originalLanguageTag === originalLanguageTag &&
                word.translationLanguageTag === translationLanguageTag) {
                reconciledWords[index] = { ...word, topicId: deck.id, topicIds: [deck.id], introducedInTopicId: deck.id };
                wordsChanged = true;
            }
        });
    });
    if (topicsChanged) saveTopicsToStorage(migratedTopics);
    if (wordsChanged) saveWordsToStorage(reconciledWords);
}

function saveWordsToStorage(words) {
    localStorage.setItem(KEY_WORDS, JSON.stringify(words.map(word => ({
        ...word,
        topicIds: [...new Set([...(word.topicIds || []), ...(word.topicId ? [word.topicId] : [])])],
        topicId: [...new Set([...(word.topicIds || []), ...(word.topicId ? [word.topicId] : [])])][0] || null
    }))));
}

function belongsToTopic(word, topicId) {
    return topicId != null && ((word.topicIds || []).includes(topicId) || word.topicId === topicId);
}

function saveTopicsToStorage(topics) {
    localStorage.setItem(KEY_TOPICS, JSON.stringify(topics));
}

async function loadDeckTsv(assetPath, fallbackData) {
    try {
        const response = await fetch(assetPath);
        if (response.ok) return await response.text();
    } catch { /* file:// or missing server — use embedded copy */ }
    return fallbackData || "";
}

async function seedDeckIfNeeded(assetPath, fallbackData, topicName, expectedWordCount, seededKey,
    originalLanguageTag = "en", translationLanguageTag = "ru", reverseDirection = false) {
    if (localStorage.getItem(seededKey) === "true") return;
    const tsv = await loadDeckTsv(assetPath, fallbackData);
    const entries = tsv.split("\n")
        .map(line => {
            const parts = line.split("\t");
            const first = (parts[0] || "").trim();
            const second = (parts[1] || "").trim();
            const original = reverseDirection ? second : first;
            const translation = reverseDirection ? first : second;
            const transcription = !reverseDirection ? ((parts[2] || "").trim() || null) : null;
            return original && translation ? [original, translation, transcription] : null;
        })
        .filter(Boolean);
    if (entries.length !== expectedWordCount) return;

    const topics = loadTopics();
    let topic = topics.find(t =>
        t.name.toLowerCase() === topicName.toLowerCase() &&
        t.originalLanguageTag === originalLanguageTag && t.translationLanguageTag === translationLanguageTag
    );
    if (!topic) {
        topic = {
            id: uuid(),
            name: topicName,
            originalLanguageTag,
            translationLanguageTag
        };
        topics.push(topic);
    }
    saveTopicsToStorage(topics);

    const transcriptions = {};
    entries.forEach(([original, , transcription]) => {
        if (transcription) transcriptions[original.trim().toLowerCase()] = transcription;
    });
    let repaired = false;
    const words = loadWords().map(w => {
        if (belongsToTopic(w, topic.id) && !w.transcription) {
            const t = transcriptions[w.original.trim().toLowerCase()];
            if (t) { repaired = true; return { ...w, transcription: t }; }
        }
        return w;
    });
    const existing = new Set(
        words.filter(w => belongsToTopic(w, topic.id))
            .map(w => seededWordKey(w.original, w.translation, reverseDirection))
    );
    let added = false;
    entries.forEach(([original, translation, transcription]) => {
        const key = seededWordKey(original, translation, reverseDirection);
        if (!existing.has(key)) {
            existing.add(key);
            added = true;
            words.push({
                id: uuid(),
                original,
                translation,
                originalLanguageTag,
                translationLanguageTag,
                successCount: 0,
                nextReviewAtMillis: 0,
                topicId: topic.id,
                topicIds: [topic.id],
                transcription
            });
        }
    });
    if (repaired || added || localStorage.getItem(seededKey) !== "true") {
        saveWordsToStorage(words);
    }
    localStorage.setItem(seededKey, "true");
}

function seededWordKey(original, translation, reverseDirection) {
    const originalKey = original.trim().toLowerCase();
    return reverseDirection ? `${originalKey}\u0000${translation.trim().toLowerCase()}` : originalKey;
}

async function seedEnglish300IfNeeded() {
    return seedDeckIfNeeded(
        ENGLISH_300_ASSET,
        typeof ENGLISH_300_TSV !== "undefined" ? ENGLISH_300_TSV : "",
        ENGLISH_300_TOPIC_NAME,
        ENGLISH_300_WORD_COUNT,
        KEY_ENGLISH_300_SEEDED
    );
}

async function seedRuEng300IfNeeded() {
    return seedDeckIfNeeded(
        ENGLISH_300_ASSET,
        typeof ENGLISH_300_TSV !== "undefined" ? ENGLISH_300_TSV : "",
        RU_ENG_300_TOPIC_NAME,
        ENGLISH_300_WORD_COUNT,
        KEY_RU_ENG_300_SEEDED,
        "ru",
        "en",
        true
    );
}

async function seedEnglish100NounsIfNeeded() {
    return seedDeckIfNeeded(
        ENGLISH_100_NOUNS_ASSET,
        typeof ENGLISH_100_NOUNS_TSV !== "undefined" ? ENGLISH_100_NOUNS_TSV : "",
        ENGLISH_100_NOUNS_TOPIC_NAME,
        ENGLISH_100_NOUNS_WORD_COUNT,
        KEY_ENGLISH_100_NOUNS_SEEDED
    );
}

async function seedRuEng100NounsIfNeeded() {
    return seedDeckIfNeeded(
        ENGLISH_100_NOUNS_ASSET,
        typeof ENGLISH_100_NOUNS_TSV !== "undefined" ? ENGLISH_100_NOUNS_TSV : "",
        RU_ENG_100_NOUNS_TOPIC_NAME,
        ENGLISH_100_NOUNS_WORD_COUNT,
        KEY_RU_ENG_100_NOUNS_SEEDED,
        "ru",
        "en",
        true
    );
}

async function seedEnglish100VerbsIfNeeded() {
    return seedDeckIfNeeded(
        ENGLISH_100_VERBS_ASSET,
        typeof ENGLISH_100_VERBS_TSV !== "undefined" ? ENGLISH_100_VERBS_TSV : "",
        ENGLISH_100_VERBS_TOPIC_NAME,
        ENGLISH_100_VERBS_WORD_COUNT,
        KEY_ENGLISH_100_VERBS_SEEDED
    );
}

async function seedRuEng100VerbsIfNeeded() {
    return seedDeckIfNeeded(
        ENGLISH_100_VERBS_ASSET,
        typeof ENGLISH_100_VERBS_TSV !== "undefined" ? ENGLISH_100_VERBS_TSV : "",
        RU_ENG_100_VERBS_TOPIC_NAME,
        ENGLISH_100_VERBS_WORD_COUNT,
        KEY_RU_ENG_100_VERBS_SEEDED,
        "ru",
        "en",
        true
    );
}

async function seedJapanese100IfNeeded() {
    return seedDeckIfNeeded(
        JAPANESE_100_ASSET,
        typeof JAPANESE_100_TSV !== "undefined" ? JAPANESE_100_TSV : "",
        JAPANESE_100_TOPIC_NAME,
        JAPANESE_100_WORD_COUNT,
        KEY_JAPANESE_100_SEEDED,
        "ja"
    );
}

async function seedDecksIfNeeded() {
    await seedEnglish300IfNeeded();
    await seedRuEng300IfNeeded();
    await seedEnglish100NounsIfNeeded();
    await seedRuEng100NounsIfNeeded();
    await seedEnglish100VerbsIfNeeded();
    await seedRuEng100VerbsIfNeeded();
    await seedJapanese100IfNeeded();
}

// ---------- State ----------

const Screen = {
    HOME: "HOME", STUDY: "STUDY", WORDS: "WORDS", TOPICS: "TOPICS",
    TOPIC_WORDS: "TOPIC_WORDS", DUE_WORDS: "DUE_WORDS", QUIZ: "QUIZ", QUIZ_RESULTS: "QUIZ_RESULTS", STATS: "STATS"
};

const inputLanguages = [
    { tag: "ru", label: "Русский" },
    { tag: "en", label: "English" },
    { tag: "es", label: "Español" },
    { tag: "fr", label: "Français" },
    { tag: "de", label: "Deutsch" },
    { tag: "ja", label: "日本語" },
    { tag: "zh", label: "中文" }
];

function languageLabel(tag) {
    return inputLanguages.find(language => language.tag === tag)?.label || tag;
}

function localDayStart(timestamp) {
    const date = new Date(timestamp);
    date.setHours(0, 0, 0, 0);
    return date.getTime();
}

function isNextLocalDay(previous, next) {
    const following = new Date(previous);
    following.setDate(following.getDate() + 1);
    return localDayStart(following.getTime()) === next;
}

function sameLocalDay(first, second) {
    return localDayStart(first) === localDayStart(second);
}

function isUnseen(word) {
    return !word.introducedAtMillis && !word.successCount && !word.nextReviewAtMillis;
}

function resetStudyProgress(word) {
    return { ...word, successCount: 0, nextReviewAtMillis: 0, introducedAtMillis: 0, intervalDays: 0, introducedInTopicId: null };
}

function dueCardsCount(words, now = Date.now()) {
    const today = localDayStart(now);
    return words.filter(word => !isUnseen(word) && localDayStart(word.nextReviewAtMillis) <= today).length;
}

function dueCards(words, now = Date.now()) {
    const today = localDayStart(now);
    return words.filter(word => !isUnseen(word) && localDayStart(word.nextReviewAtMillis) <= today)
        .sort((a, b) => a.nextReviewAtMillis - b.nextReviewAtMillis);
}

function availableNewCardsCount(words, topicId, dailyLimit, now = Date.now(), scopeToTopic = true) {
    const scoped = scopeToTopic ? words.filter(word => belongsToTopic(word, topicId)) : words;
    const introducedToday = scoped.filter(word => word.introducedAtMillis && sameLocalDay(word.introducedAtMillis, now) &&
        (word.introducedInTopicId === topicId || (word.introducedInTopicId == null && word.topicId === topicId))).length;
    const unseen = scoped.filter(isUnseen).length;
    return Math.min(unseen, Math.max(0, Math.max(0, dailyLimit) - introducedToday));
}

function availableGlobalNewCardsCount(words, topicLimits, now = Date.now()) {
    const remaining = new Map([...topicLimits].map(([topicId, limit]) => {
        const introducedToday = words.filter(word => word.introducedAtMillis && sameLocalDay(word.introducedAtMillis, now) &&
            (word.introducedInTopicId === topicId || (word.introducedInTopicId == null && word.topicId === topicId))).length;
        return [topicId, Math.max(0, Math.max(0, limit) - introducedToday)];
    }));
    let available = 0;
    words.filter(isUnseen).forEach(word => {
        const membership = [...new Set([...(word.topicIds || []), ...(word.topicId ? [word.topicId] : [])])]
            .find(topicId => (remaining.get(topicId) || 0) > 0);
        if (membership != null) {
            remaining.set(membership, remaining.get(membership) - 1);
            available++;
        }
    });
    return available;
}

function introductionTopicIdFor(word, words, topicId, scopeToTopic, topicLimits, now = Date.now()) {
    if (scopeToTopic) return topicId;
    return [...new Set([...(word.topicIds || []), ...(word.topicId ? [word.topicId] : [])])]
        .find(membership => availableNewCardsCount(
            words, membership, topicLimits.get(membership) ?? DEFAULT_NEW_CARDS_PER_DAY, now
        ) > 0) ?? null;
}

function nextStudyWordId(words, topicId, dailyLimit, now = Date.now(), scopeToTopic = true, topicLimits = new Map()) {
    const scoped = scopeToTopic ? words.filter(word => belongsToTopic(word, topicId)) : words;
    const dueReview = scoped.filter(word => !isUnseen(word) && localDayStart(word.nextReviewAtMillis) <= localDayStart(now) &&
        !(word.introducedAtMillis && word.intervalDays === 0));
    dueReview.sort((a, b) => a.nextReviewAtMillis - b.nextReviewAtMillis);
    if (dueReview.length) return dueReview[0].id;
    const overdueLearning = scoped.filter(word => !isUnseen(word) &&
        localDayStart(word.nextReviewAtMillis) <= localDayStart(now) && word.introducedAtMillis &&
        word.intervalDays === 0 && !sameLocalDay(word.introducedAtMillis, now));
    overdueLearning.sort((a, b) => a.introducedAtMillis - b.introducedAtMillis);
    if (overdueLearning.length) return overdueLearning[0].id;
    if (scopeToTopic) {
        if (availableNewCardsCount(words, topicId, dailyLimit, now, true) > 0) {
            const fresh = scoped.find(isUnseen);
            if (fresh) return fresh.id;
        }
    } else {
        const fresh = scoped.find(word => isUnseen(word) && (word.topicIds || [word.topicId]).filter(Boolean).some(id =>
            availableNewCardsCount(words, id, topicLimits.get(id) ?? DEFAULT_NEW_CARDS_PER_DAY, now) > 0
        ));
        if (fresh) return fresh.id;
    }
    return scoped.filter(word => !isUnseen(word) && localDayStart(word.nextReviewAtMillis) <= localDayStart(now) &&
        word.introducedAtMillis && word.intervalDays === 0)
        .sort((a, b) => a.introducedAtMillis - b.introducedAtMillis)[0]?.id ?? null;
}

function studyDayStats(events, now = Date.now()) {
    const start = localDayStart(now);
    const end = new Date(start);
    end.setDate(end.getDate() + 1);
    const today = events.filter(event => event.timestampMillis >= start && event.timestampMillis < end.getTime());
    return { answered: today.length, correct: today.filter(event => event.correct).length };
}

function defaultStatsDay(year) {
    if (year === new Date().getFullYear()) return localDayStart(Date.now());
    const days = state.studyEvents.map(event => localDayStart(event.timestampMillis))
        .filter(day => new Date(day).getFullYear() === year)
        .sort((a, b) => a - b);
    return days[days.length - 1] || localDayStart(new Date(year, 0, 1).getTime());
}

function deckNewLimits() {
    return new Map([
        ...state.topics.map(topic => [topic.id, topic.newCardsPerDay]),
        [null, DEFAULT_NEW_CARDS_PER_DAY]
    ]);
}

const state = {
    words: [],
    topics: [],
    screen: Screen.HOME,
    currentWordId: null,
    answerShown: false,
    editor: null,          // WordDraft
    topicEditor: null,     // TopicDraft
    topicDeleteTarget: null,
    topicRenameTarget: null,
    selectedTopicId: null,
    studyTopicId: null,
    isTopicStudy: false,
    deleteTarget: null,
    selectionMode: false,
    selectedWordIds: new Set(),
    bulkDeleteTargets: null,
    openDropdown: null,    // id of currently open dropdown
    languageSearch: "",
    undo: null,
    undoTimer: null,
    language: "ru",
    quizQuestions: [],
    quizIndex: 0,
    quizOptions: [],
    quizOptionPool: [],
    quizSelectedOption: null,
    quizAnswerSubmitted: false,
    quizAnswerCorrect: false,
    quizScore: 0,
    quizMistakes: [],
    studyEvents: [],
    quizTitle: "Все слова",
    quizReturnScreen: Screen.HOME,
    calendarYear: new Date().getFullYear(),
    calendarSelectedDay: localDayStart(Date.now()),
    deckDrag: null
};

function tr(source, values = {}) {
    const translated = UI_TRANSLATIONS?.[source]?.[state.language] || source;
    return translated.replace(/\{(\w+)\}/g, (_, key) => String(values[key] ?? `{${key}}`));
}

function saveWords(updated) {
    const topics = new Map(state.topics.map(topic => [topic.id, topic]));
    const hasMismatch = updated.some(word => {
        const ids = [...new Set([...(word.topicIds || []), ...(word.topicId ? [word.topicId] : [])])];
        return ids.length === 0 || ids.some(id => {
            const topic = topics.get(id);
            return !topic || word.originalLanguageTag !== topic.originalLanguageTag ||
                word.translationLanguageTag !== topic.translationLanguageTag;
        });
    });
    if (hasMismatch) return false;
    state.words = updated;
    saveWordsToStorage(updated);
    return true;
}

function saveTopics(updated) {
    const currentTopics = new Map(state.topics.map(topic => [topic.id, topic]));
    const changesLanguagePair = updated.some(topic => {
        const previous = currentTopics.get(topic.id);
        return previous && (
            previous.originalLanguageTag !== topic.originalLanguageTag ||
            previous.translationLanguageTag !== topic.translationLanguageTag
        );
    });
    if (changesLanguagePair) return false;
    state.topics = updated;
    saveTopicsToStorage(updated);
    return true;
}

function reorderTopics(topics, sourceId, targetId) {
    const sourceIndex = topics.findIndex(topic => topic.id === sourceId);
    const targetIndex = topics.findIndex(topic => topic.id === targetId);
    if (sourceIndex < 0 || targetIndex < 0 || sourceIndex === targetIndex) return topics;
    const reordered = [...topics];
    const [movedTopic] = reordered.splice(sourceIndex, 1);
    reordered.splice(targetIndex, 0, movedTopic);
    return reordered;
}

function offerUndo(message, action) {
    clearTimeout(state.undoTimer);
    state.undo = { message, action };
    state.undoTimer = setTimeout(() => {
        state.undo = null;
        render();
    }, 6000);
}

function undoLastAction() {
    if (!state.undo) return;
    clearTimeout(state.undoTimer);
    const action = state.undo.action;
    state.undo = null;
    action();
    render();
}

function normalizeQuizAnswer(value) {
    return String(value).toLowerCase()
        .replace(/[\p{P}\p{S}]/gu, " ")
        .trim()
        .replace(/\s+/g, " ");
}

function isQuizAnswerCorrect(answer, acceptedTranslations) {
    const normalizedAnswer = normalizeQuizAnswer(answer);
    if (!normalizedAnswer) return false;
    return normalizedAnswer === normalizeQuizAnswer(acceptedTranslations) ||
        acceptedTranslations.split(";")
        .map(normalizeQuizAnswer)
        .some(value => value === normalizedAnswer);
}

function quizPromptKey(word) {
    return [word.originalLanguageTag || "en", word.translationLanguageTag || "ru", normalizeQuizAnswer(word.original)]
        .map(value => value.toLowerCase())
        .join("\u0000");
}

function sameQuizPrompt(first, second) {
    return quizPromptKey(first) === quizPromptKey(second);
}

function translationsOverlap(first, second) {
    return isQuizAnswerCorrect(first, second) || isQuizAnswerCorrect(second, first);
}

function quizDistractorTranslations(question, candidateWords, knownWords) {
    const acceptedTranslations = knownWords.filter(word => sameQuizPrompt(word, question)).map(word => word.translation);
    const distinct = new Map();
    candidateWords.forEach(word => {
        const samePair = (word.originalLanguageTag || "en").toLowerCase() === (question.originalLanguageTag || "en").toLowerCase() &&
            (word.translationLanguageTag || "ru").toLowerCase() === (question.translationLanguageTag || "ru").toLowerCase();
        if (!samePair || sameQuizPrompt(word, question)) return;
        const key = normalizeQuizAnswer(word.translation);
        if (key && !distinct.has(key) && !acceptedTranslations.some(answer => translationsOverlap(word.translation, answer))) {
            distinct.set(key, word.translation);
        }
    });
    return [...distinct.values()];
}

function shuffle(items) {
    const pool = [...items];
    for (let i = pool.length - 1; i > 0; i--) {
        const j = Math.floor(Math.random() * (i + 1));
        [pool[i], pool[j]] = [pool[j], pool[i]];
    }
    return pool;
}

function buildQuizOptions(correctTranslation, candidates, optionCount = 4) {
    const correctForms = new Set([
        normalizeQuizAnswer(correctTranslation),
        ...correctTranslation.split(";").map(normalizeQuizAnswer)
    ]);
    const uniqueDistractors = new Map();
    candidates.forEach(value => {
        const normalized = normalizeQuizAnswer(value);
        if (normalized && !correctForms.has(normalized) && !uniqueDistractors.has(normalized)) {
            uniqueDistractors.set(normalized, value);
        }
    });
    const distractors = [...uniqueDistractors.values()];
    return shuffle([...shuffle(distractors).slice(0, Math.max(0, optionCount - 1)), correctTranslation]);
}

function prepareQuizOptions() {
    const question = state.quizQuestions[state.quizIndex];
    if (!question) {
        state.quizOptions = [];
        state.quizSelectedOption = null;
        return;
    }
    const candidateWords = state.quizOptionPool.map(id => state.words.find(word => word.id === id)).filter(Boolean);
    const distractors = quizDistractorTranslations(question, candidateWords, state.words);
    state.quizOptions = buildQuizOptions(question.translation, distractors);
    state.quizSelectedOption = null;
}

function startQuiz(questionPool, returnScreen, title, optionPool = questionPool) {
    state.quizQuestions = shuffle([...new Map(questionPool.map(word => [quizPromptKey(word), word])).values()])
        .filter(question => quizDistractorTranslations(question, optionPool, state.words).length > 0)
        .slice(0, 10);
    state.quizIndex = 0;
    state.quizOptionPool = [...new Set(optionPool.map(word => word.id))];
    state.quizAnswerSubmitted = false;
    state.quizAnswerCorrect = false;
    state.quizScore = 0;
    state.quizMistakes = [];
    state.quizTitle = title;
    state.quizReturnScreen = returnScreen;
    prepareQuizOptions();
    state.screen = Screen.QUIZ;
    render();
}

function studyWords() {
    return state.isTopicStudy ? state.words.filter(w => belongsToTopic(w, state.studyTopicId)) : state.words;
}

function openStudy(topicId = null, fromTopic = false) {
    state.studyTopicId = topicId;
    state.isTopicStudy = fromTopic;
    const now = Date.now();
    const updated = selectNextStudyCard(state.words, now);
    if (updated !== state.words) saveWords(updated);
    state.answerShown = false;
    state.screen = Screen.STUDY;
    render();
}

function selectNextStudyCard(updatedWords, now = Date.now()) {
    const limit = state.isTopicStudy
        ? state.topics.find(topic => topic.id === state.studyTopicId)?.newCardsPerDay ?? DEFAULT_NEW_CARDS_PER_DAY
        : DEFAULT_NEW_CARDS_PER_DAY;
    const selectedId = nextStudyWordId(
        updatedWords, state.studyTopicId, limit, now, state.isTopicStudy, deckNewLimits()
    );
    const selectedWord = updatedWords.find(word => word.id === selectedId);
    if (selectedWord && isUnseen(selectedWord)) {
        const introTopicId = introductionTopicIdFor(
            selectedWord, updatedWords, state.studyTopicId, state.isTopicStudy, deckNewLimits(), now
        );
        updatedWords = updatedWords.map(word => word.id === selectedId
            ? { ...word, introducedAtMillis: now, introducedInTopicId: introTopicId, nextReviewAtMillis: now }
            : word);
    }
    state.currentWordId = selectedId;
    return updatedWords;
}

function answerStudyCard(correct) {
    const word = state.words.find(item => item.id === state.currentWordId);
    if (!word) return;
    const now = Date.now();
    const nextInterval = correct
        ? word.successCount === 0 ? 0 : word.intervalDays === 0 ? 1 : Math.min(365, word.intervalDays * 2)
        : 0;
    const nextReview = new Date(localDayStart(now));
    nextReview.setDate(nextReview.getDate() + nextInterval);
    const updated = state.words.map(item => item.id !== word.id ? item : {
        ...item,
        successCount: correct ? word.successCount + 1 : 0,
        intervalDays: nextInterval,
        nextReviewAtMillis: nextInterval === 0 ? now : nextReview.getTime()
    });
    saveStudyEvents([...state.studyEvents, { timestampMillis: now, correct }]);
    saveWords(selectNextStudyCard(updated, now));
    state.answerShown = false;
    render();
}

function goBack() {
    if ((state.screen === Screen.WORDS || state.screen === Screen.TOPIC_WORDS) && state.selectionMode) {
        state.selectionMode = false;
        state.selectedWordIds = new Set();
    } else if (state.screen === Screen.QUIZ || state.screen === Screen.QUIZ_RESULTS) {
        state.screen = state.quizReturnScreen;
    } else if (state.screen === Screen.TOPIC_WORDS) {
        state.screen = Screen.TOPICS;
    } else {
        state.screen = Screen.HOME;
    }
    render();
}

// ---------- Helpers ----------

function wordCountLabel(count) {
    if (state.language === "ru") {
        const m100 = count % 100, m10 = count % 10;
        if (m100 >= 11 && m100 <= 14) return tr("слов");
        if (m10 === 1) return tr("слово");
        if (m10 >= 2 && m10 <= 4) return tr("слова");
        return tr("слов");
    }
    if (state.language === "en") return tr(count === 1 ? "слово" : "слов");
    if (state.language === "es") return tr(count === 1 ? "слово" : "слов");
    if (state.language === "fr") return tr(count === 1 ? "слово" : "слов");
    if (state.language === "ja" || state.language === "zh") return tr(count === 1 ? "слово" : "слов");
    return tr(count === 1 ? "слово" : "слов");
}

function formatDate(timestamp) {
    const locales = {
        ru: "ru-RU", en: "en-US", es: "es-ES", fr: "fr-FR", de: "de-DE",
        ja: "ja-JP", zh: "zh-CN"
    };
    return new Intl.DateTimeFormat(locales[state.language] || "ru-RU", { day: "numeric", month: "long" }).format(timestamp);
}

function esc(s) {
    return String(s).replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;")
        .replace(/"/g, "&quot;").replace(/'/g, "&#39;");
}

function h(html) {
    const t = document.createElement("template");
    t.innerHTML = html.trim();
    return t.content;
}

// ---------- Screens ----------

function renderHeader(title) {
    if (state.screen === Screen.HOME) return "";
    return `<div class="header">
        <div class="header-main">
            <button class="back-btn" data-action="back">←</button>
            <h2>${esc(title)}</h2>
        </div>
    </div>`;
}

function renderThemeSelector() {
    const themeMode = document.documentElement.dataset.themeMode || "system";
    return `<div class="theme-selector" aria-label="Режим оформления">
        <button class="theme-choice ${themeMode === "light" ? "active" : ""}" data-action="set-theme" data-id="light" aria-pressed="${themeMode === "light"}">${esc(tr("Светлая"))}</button>
        <button class="theme-choice ${themeMode === "dark" ? "active" : ""}" data-action="set-theme" data-id="dark" aria-pressed="${themeMode === "dark"}">${esc(tr("Тёмная"))}</button>
        <button class="theme-choice ${themeMode === "system" ? "active" : ""}" data-action="set-theme" data-id="system" aria-pressed="${themeMode === "system"}">${esc(tr("Система"))}</button>
    </div>`;
}

function renderLanguageSelector() {
    const selected = inputLanguages.find(language => language.tag === state.language) || inputLanguages[0];
    return `<div class="language-setting">
        <div class="dropdown">
            <button class="picker-btn" data-action="dropdown" data-id="app-language" aria-haspopup="dialog">${esc(selected.label)} ▾</button>
        </div>
    </div>`;
}

function applyTheme(theme) {
    const prefersDark = typeof window.matchMedia === "function" &&
        window.matchMedia("(prefers-color-scheme: dark)").matches;
    const effectiveTheme = theme === "system" ? (prefersDark ? "dark" : "light") : theme;
    document.documentElement.dataset.themeMode = theme;
    document.documentElement.dataset.theme = effectiveTheme;
    const themeColorMeta = document.querySelector('meta[name="theme-color"]');
    if (themeColorMeta) themeColorMeta.content = effectiveTheme === "dark" ? "#141c18" : "#f6f4ed";
}

function screenTitle() {
    switch (state.screen) {
        case Screen.STUDY:
            return state.isTopicStudy
                ? (state.topics.find(t => t.id === state.studyTopicId)?.name || tr("Без колоды"))
                : tr("Учить");
        case Screen.QUIZ: return tr(state.quizTitle);
        case Screen.QUIZ_RESULTS: return tr("Результаты");
        case Screen.STATS: return tr("Статистика");
        case Screen.WORDS: return tr("Слова");
        case Screen.TOPICS: return tr("Колоды");
        case Screen.TOPIC_WORDS:
            return state.topics.find(t => t.id === state.selectedTopicId)?.name || tr("Без колоды");
        case Screen.DUE_WORDS: return tr("Слова к повторению");
        default: return tr("Словарь");
    }
}

function homeActionIcon(kind) {
    const paths = {
        study: "M12 6.5C9.7 4.8 6.6 4.2 3 4.5V18c3.6-.3 6.7.3 9 2 2.3-1.7 5.4-2.3 9-2V4.5c-3.6-.3-6.7.3-9 2ZM12 6.5V20M5.5 8c1.7 0 3.2.3 4.5 1M14 9c1.3-.7 2.8-1 4.5-1",
        decks: "M4 7v11c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2V7c0-1.1-.9-2-2-2H6c-1.1 0-2 .9-2 2ZM8 5V4c0-1.1.9-2 2-2h9c1.1 0 2 .9 2 2v10M4 9h16M8 14h8M8 17h5",
        quiz: "M12 2a10 10 0 1 0 0 20 10 10 0 1 0 0-20ZM12 6a6 6 0 1 0 0 12 6 6 0 1 0 0-12ZM9.5 12l1.7 1.7 3.4-3.5",
        stats: "M4 19V5M4 19h17M8 16v-4M12 16V7M16 16v-7M20 16V4"
    };
    return `<span class="home-action-icon" aria-hidden="true"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="${paths[kind]}"/></svg></span>`;
}

function renderStats() {
    const year = state.calendarYear;
    const firstDay = new Date(year, 0, 1);
    const gridStart = new Date(firstDay);
    gridStart.setDate(firstDay.getDate() - (firstDay.getDay() + 6) % 7);
    const eventsByDay = new Map();
    state.studyEvents.forEach(event => {
        const day = localDayStart(event.timestampMillis);
        eventsByDay.set(day, (eventsByDay.get(day) || 0) + 1);
    });
    const dueByDay = new Map();
    state.words.filter(word => !isUnseen(word)).forEach(word => {
        const day = localDayStart(word.nextReviewAtMillis);
        if (new Date(day).getFullYear() === year && day > localDayStart(Date.now())) {
            dueByDay.set(day, (dueByDay.get(day) || 0) + 1);
        }
    });
    const cells = [];
    const monthLabels = [];
    let previousMonth = -1;
    for (let index = 0; index < 53 * 7; index++) {
        const date = new Date(gridStart);
        date.setDate(gridStart.getDate() + index);
        const day = localDayStart(date.getTime());
        const inYear = date.getFullYear() === year;
        const count = eventsByDay.get(day) || 0;
        const due = dueByDay.get(day) || 0;
        const level = count === 0 ? 0 : count < 3 ? 1 : count < 6 ? 2 : count < 10 ? 3 : 4;
        if (inYear && date.getDate() <= 7 && date.getMonth() !== previousMonth) {
            monthLabels.push(`<span style="grid-column:${Math.floor(index / 7) + 1}">${esc(new Intl.DateTimeFormat(state.language, { month: "short" }).format(date))}</span>`);
            previousMonth = date.getMonth();
        }
        const dateLabel = new Intl.DateTimeFormat(state.language, { dateStyle: "medium" }).format(date);
        const selected = day === state.calendarSelectedDay;
        cells.push(`<button type="button" class="heat-cell heat-${inYear && due && !count ? "future" : level}${selected ? " selected" : ""}" data-action="stats-day-select" data-day="${day}" aria-label="${esc(`${dateLabel}: ${count}`)}" title="${esc(`${dateLabel}: ${count} · ${due}`)}" ${inYear ? "" : "disabled"}></button>`);
    }
    const todayStart = localDayStart(Date.now());
    const activeDays = [...eventsByDay.keys()].filter(day => day <= todayStart).sort((a, b) => a - b);
    let currentStreak = 0, longestStreak = 0, streak = 0, previousDay = null;
    activeDays.forEach(day => {
        streak = previousDay != null && isNextLocalDay(previousDay, day) ? streak + 1 : 1;
        longestStreak = Math.max(longestStreak, streak);
        previousDay = day;
    });
    const yesterday = new Date(todayStart);
    yesterday.setDate(yesterday.getDate() - 1);
    if (activeDays.length && [todayStart, localDayStart(yesterday.getTime())].includes(activeDays[activeDays.length - 1])) currentStreak = streak;
    const inYearCount = [...eventsByDay].filter(([day]) => new Date(day).getFullYear() === year).reduce((sum, [, count]) => sum + count, 0);
    const yearActiveDays = [...eventsByDay.keys()].filter(day => new Date(day).getFullYear() === year && day <= todayStart).length;
    const averagePerActiveDay = yearActiveDays ? Math.round(inYearCount / yearActiveDays) : 0;
    const selectedDayEvents = state.studyEvents.filter(event => localDayStart(event.timestampMillis) === state.calendarSelectedDay);
    const selectedDayCorrect = selectedDayEvents.filter(event => event.correct).length;
    const selectedDayRetention = selectedDayEvents.length ? Math.round(selectedDayCorrect * 100 / selectedDayEvents.length) : 0;
    const selectedDateLabel = new Intl.DateTimeFormat(state.language, { dateStyle: "full" }).format(new Date(state.calendarSelectedDay));
    const todayStats = studyDayStats(state.studyEvents);
    const unseen = state.words.filter(isUnseen).length;
    const mastered = state.words.filter(word => !isUnseen(word) && word.intervalDays >= 21).length;
    const learning = state.words.length - unseen - mastered;
    const total = state.words.length;
    const pieTotal = total || 1;
    const blue = Math.round(unseen * 100 / pieTotal);
    const lime = Math.round(learning * 100 / pieTotal);
    return `<div class="screen stats-screen">
        <section class="stats-overview">
            <div class="stats-metric"><span>${esc(tr("Сегодня изучено {count} карточек", { count: todayStats.answered }))}</span><strong>${esc(tr("Удержание: {percent}%", { percent: todayStats.answered ? Math.round(todayStats.correct * 100 / todayStats.answered) : 0 }))}</strong></div>
            <div class="stats-metric"><span>${esc(tr("Текущая серия: {count} дн.", { count: currentStreak }))}</span><strong>${esc(tr("Самая длинная серия: {count} дн.", { count: longestStreak }))}</strong></div>
        </section>
        <section class="card stats-card">
            <div class="stats-heading"><h2>${esc(tr("Календарь повторений"))}</h2>
                <div class="calendar-year-nav"><button data-action="stats-year-prev" aria-label="‹">‹</button><strong>${year}</strong><button data-action="stats-year-next" ${year >= new Date().getFullYear() ? "disabled" : ""} aria-label="›">›</button></div>
            </div>
            <div class="calendar-scroll"><div class="calendar-content">
                <div class="heatmap-months">${monthLabels.join("")}</div>
                <div class="heatmap-grid">${cells.join("")}</div>
            </div></div>
            <div class="heatmap-legend"><span>${esc(tr("Меньше"))}</span>${[0, 1, 2, 3, 4].map(level => `<i class="heat-cell heat-${level}"></i>`).join("")}<span>${esc(tr("Больше"))}</span></div>
            <div class="calendar-summary"><span>${esc(tr("Повторов за год: {count}", { count: inYearCount }))}</span><span>${esc(tr("Учебных дней: {count}", { count: yearActiveDays }))}</span><span>${esc(tr("Среднее за активный день: {count}", { count: averagePerActiveDay }))}</span></div>
            <div class="selected-day-summary"><strong>${esc(selectedDateLabel)}</strong><span>${esc(tr("Повторов за день: {count}", { count: selectedDayEvents.length }))}</span><span>${esc(tr("Удержание: {percent}%", { percent: selectedDayRetention }))}</span></div>
        </section>
        <section class="card stats-card">
            <h2>${esc(tr("Карточки по состоянию"))}</h2>
            <div class="pie-layout">
                <div class="stats-pie${total ? "" : " empty"}" style="--blue:${blue}%;--lime:${lime}%" role="img" aria-label="${esc(tr("Неизученные {new}, изучаемые {learning}, запомненные {mastered}", { new: unseen, learning, mastered }))}"></div>
                <div class="pie-legend">
                    <span><i class="legend-dot unseen"></i>${esc(tr("Неизученные"))}<b>${unseen} · ${Math.round(unseen * 100 / pieTotal)}%</b></span>
                    <span><i class="legend-dot learning"></i>${esc(tr("Изучаемые"))}<b>${learning} · ${Math.round(learning * 100 / pieTotal)}%</b></span>
                    <span><i class="legend-dot mastered"></i>${esc(tr("Запомненные"))}<b>${mastered} · ${Math.round(mastered * 100 / pieTotal)}%</b></span>
                </div>
            </div>
            <p class="stats-total">${esc(tr("Всего карточек: {count}", { count: total }))}</p>
            ${total ? `<p class="stats-retention">${esc(tr("Запомненной считается карточка с интервалом не менее 21 дня."))}</p>` : `<p class="stats-retention">${esc(tr("Добавьте слова, чтобы увидеть распределение карточек."))}</p>`}
        </section>
    </div>`;
}

function renderHome() {
    const n = state.words.length;
    const decks = state.topics.length;
    const now = Date.now();
    const dueToday = dueCardsCount(state.words, now);
    const newToday = availableGlobalNewCardsCount(state.words, deckNewLimits(), now);
    return `<div class="screen center home-screen">
        <div class="home-controls">
            ${renderLanguageSelector()}
            ${renderThemeSelector()}
        </div>
        <section class="home-hero">
            <div class="home-brand">
                <span class="home-mark">Aa</span>
            </div>
            <h1>${esc(tr("Словарь"))}</h1>
            ${n === 0 ? `<p class="subtitle">${esc(tr("Добавьте слова и учите их в своём темпе"))}</p>` : ""}
            <div class="home-stats">
                <div class="home-stat"><strong>${n}</strong><span>${esc(tr("СЛОВ"))}</span></div>
                <div class="home-stat"><strong>${decks}</strong><span>${esc(tr("КОЛОД"))}</span></div>
            </div>
        </section>
        <div class="home-actions">
            <div class="study-action-card">
                <button class="study-action-main" data-action="study">
                    ${homeActionIcon("study")}<span class="home-action-text"><span class="home-action-label">${esc(tr("Учить слова"))}</span><span class="home-action-count">${esc(tr("{due} повторить · {new} новых", { due: dueToday, new: newToday }))}</span></span>
                </button>
                ${dueToday > 0 ? `<button class="study-action-repeat" data-action="show-due-words">${esc(tr("Повторить"))}</button>` : ""}
            </div>
            <button class="btn tonal home-action" data-action="decks">
                ${homeActionIcon("decks")}<span class="home-action-label">${esc(tr("Колоды"))}</span>
            </button>
            <button class="btn outlined home-action" data-action="quiz-all" ${n === 0 ? "disabled" : ""}>
                ${homeActionIcon("quiz")}<span class="home-action-label">${esc(tr("Проверить знания"))}</span>
            </button>
            <button class="btn tonal home-action" data-action="stats">
                ${homeActionIcon("stats")}<span class="home-action-label">${esc(tr("Статистика"))}</span>
            </button>
        </div>
    </div>`;
}

function renderDueWords() {
    const words = dueCards(state.words);
    if (!words.length) {
        return `<div class="screen center"><div class="empty"><h3>${esc(tr("На сегодня все!"))}</h3><p>${esc(tr("Сейчас нет карточек для повторения."))}</p></div></div>`;
    }
    const cards = words.map(word => {
        const decks = (word.topicIds || [word.topicId]).filter(Boolean)
            .map(id => state.topics.find(topic => topic.id === id)?.name).filter(Boolean);
        return `<article class="card due-word-card">
            <div class="row space-between"><div class="title-medium">${esc(word.original)}${word.transcription ? ` <span class="body-medium">[${esc(word.transcription)}]</span>` : ""}</div>${renderSpeakButton(word)}</div>
            <div class="word-translation-audio"><div class="body-medium due-word-translation">${esc(word.translation)}</div>${renderSpeakButton(word, "translation")}</div>
            ${decks.length ? `<div class="due-word-decks">${esc(decks.join(" · "))}</div>` : ""}
            <span class="due-word-status">${esc(tr(localDayStart(word.nextReviewAtMillis) < localDayStart(Date.now()) ? "Просрочено" : "Сегодня"))}</span>
        </article>`;
    }).join("");
    return `<div class="screen due-words-screen has-fixed-action">
        <div class="due-words-intro">${esc(tr("Эти карточки нужно повторить сегодня: {count}", { count: words.length }))}</div>
        ${cards}
        <div class="spacer16"></div>
    </div>`;
}

function renderTopics() {
    const countsByTopic = new Map();
    state.words.forEach(word => (word.topicIds || [word.topicId]).filter(Boolean).forEach(id => {
        countsByTopic.set(id, (countsByTopic.get(id) || 0) + 1);
    }));
    const unassignedCount = 0;
    const rows = [...state.topics];
    if (unassignedCount > 0 && state.topics.length > 0) rows.push(null);

    let body = "";
    if (state.topics.length === 0) {
        body += `<div class="empty"><h3>${esc(tr("Нет колод"))}</h3><p>${esc(tr(unassignedCount > 0
            ? "Создайте колоду — существующие слова будут добавлены в неё."
            : "Создайте колоду, чтобы добавлять и учить слова по отдельности."))}</p></div>
            <div class="spacer20"></div>`;
    }

    rows.forEach(topic => {
        const topicId = topic ? topic.id : null;
        const count = countsByTopic.get(topicId) || 0;
        const deckWords = topicId ? state.words.filter(word => belongsToTopic(word, topicId)) : [];
        const dueCount = dueCardsCount(deckWords);
        const newCount = availableNewCardsCount(
            deckWords, topicId, topic?.newCardsPerDay ?? DEFAULT_NEW_CARDS_PER_DAY
        );
        body += `<div class="card deck-card" data-topic-card="${esc(topicId ?? "")}">
            <div class="row space-between deck-card-heading">
                <div class="deck-card-title">
                    <span class="deck-card-mark" aria-hidden="true">${esc((topic?.name || "—").trim().charAt(0).toLocaleUpperCase())}</span>
                    <div class="deck-card-name"><div class="title-medium">${esc(topic ? topic.name : tr("Без колоды"))}</div>
                    ${topic ? `<span class="deck-language-direction">${esc(languageLabel(topic.originalLanguageTag))} → ${esc(languageLabel(topic.translationLanguageTag))}</span>` : ""}
                    </div>
                </div>
                <div class="row deck-card-tools">
                    <span class="body-medium">${count} ${wordCountLabel(count)}</span>
                    ${topic ? `<button type="button" class="deck-drag-handle" data-drag-handle aria-label="${esc(tr("Перетащить колоду"))}" title="${esc(tr("Перетащить колоду"))}">↕</button>` : ""}
                </div>
            </div>
            <div class="deck-learning-counts">
                <span class="deck-learning-stat due">${esc(tr("Повторить сегодня: {count}", { count: dueCount }))}</span>
                <span class="deck-learning-stat fresh">${esc(tr("Новых сегодня: {count}", { count: newCount }))}</span>
            </div>
            <div class="row mt14">
                <button class="deck-btn primary grow" data-action="open-topic" data-id="${esc(topicId ?? "")}">${esc(tr("Открыть колоду"))}</button>
                <button class="deck-btn secondary grow" data-action="study-topic" data-id="${esc(topicId ?? "")}">${esc(tr("Учить слова"))}</button>
            </div>
            ${topic ? `<div class="row mt8">
                <button class="deck-btn tertiary grow" data-action="edit-topic" data-id="${esc(topic.id)}">${esc(tr("Настройки колоды"))}</button>
                <button class="deck-btn error grow" data-action="delete-topic" data-id="${esc(topic.id)}">${esc(tr("Удалить колоду"))}</button>
            </div>` : ""}
            <button class="deck-btn tertiary solid-deck-check" data-action="quiz-topic" data-id="${esc(topicId ?? "")}" ${count === 0 ? "disabled" : ""}>${esc(tr("Проверить знания"))}</button>
        </div>`;
    });

    body += `<div class="spacer16"></div>`;
    return `<div class="screen has-fixed-action">${body}</div>`;
}

function renderStudy() {
    const pool = studyWords();
    const currentWord = pool.find(w => w.id === state.currentWordId) || null;
    let body;

    if (pool.length === 0) {
        body = `<div class="empty"><h3>${esc(tr("Пока нет слов"))}</h3><p>${esc(tr("Добавьте первое слово, чтобы начать учиться."))}</p></div>
            <div class="spacer20"></div>
            <button class="btn" style="border-radius:16px;height:auto;padding:12px" data-action="open-words">${esc(tr("Добавить слово"))}</button>`;
    } else if (!currentWord) {
        const next = pool.filter(w => !isUnseen(w)).reduce((m, w) => Math.min(m, w.nextReviewAtMillis), Infinity);
        body = `<div class="empty"><h3>${esc(tr("На сегодня все!"))}</h3><p>${isFinite(next)
            ? esc(tr("Следующее повторение {date}", { date: formatDate(next) }))
            : esc(tr("Новые слова скоро появятся здесь."))}</p></div>`;
    } else {
        body = `<div class="study-card">
            <div class="word-audio-heading"><h2>${esc(currentWord.original)}${currentWord.transcription ? ` <span class="body-medium" style="font-size:18px;font-weight:400">[${esc(currentWord.transcription)}]</span>` : ""}</h2>${renderSpeakButton(currentWord)}</div>
            ${state.answerShown ? `<div class="study-translation"><div class="translation">${esc(currentWord.translation)}</div>${renderSpeakButton(currentWord, "translation")}</div>` : ""}
            ${state.answerShown && currentWord.example ? `<p class="word-example">${esc(currentWord.example)}</p>` : ""}
            ${state.answerShown && currentWord.exampleTranslation ? `<p class="word-example-translation">${esc(currentWord.exampleTranslation)}</p>` : ""}
        </div>
        ${!state.answerShown ? `<div class="spacer24"></div>
            <button class="btn" style="height:56px" data-action="show-answer">${esc(tr("Показать ответ"))}</button>` : ""}`;
    }
    return `<div class="screen center">${body}</div>`;
}

function renderSpeakButton(word, side = "word") {
    if (typeof window === "undefined" || !window.speechSynthesis || typeof SpeechSynthesisUtterance === "undefined") return "";
    const label = side === "translation" ? "Озвучить перевод" : "Озвучить слово";
    return `<button type="button" class="speak-word-btn" data-action="speak-word" data-id="${esc(word.id)}" data-side="${side}"
        title="${esc(tr(label))}" aria-label="${esc(tr(label))}">🔊</button>`;
}

function speakWord(word, side = "word") {
    const synthesis = window.speechSynthesis;
    const localeByLanguage = {
        ru: "ru-RU", en: "en-US", es: "es-ES", fr: "fr-FR",
        de: "de-DE", ja: "ja-JP", zh: "zh-CN"
    };
    const isTranslation = side === "translation";
    const languageTag = (isTranslation ? word.translationLanguageTag : word.originalLanguageTag) || "en";
    const spokenText = !isTranslation && languageTag.split("-")[0].toLowerCase() === "ja" && word.transcription
        ? word.transcription
        : isTranslation ? word.translation : word.original;
    const utterance = new SpeechSynthesisUtterance(spokenText);
    utterance.lang = localeByLanguage[languageTag] || languageTag || "en-US";
    utterance.rate = 0.95;
    utterance.pitch = 1;
    utterance.volume = 1;
    const languagePrefix = utterance.lang.toLowerCase().split("-")[0];
    const voices = synthesis.getVoices();
    const matchingVoices = voices.filter(voice => {
        const voiceLanguage = voice.lang.toLowerCase();
        return voiceLanguage === languagePrefix || voiceLanguage.startsWith(`${languagePrefix}-`);
    });
    const qualityHint = /natural|neural|enhanced|premium|online|google|microsoft|siri/i;
    const exactLocale = utterance.lang.toLowerCase();
    matchingVoices.sort((a, b) => {
        const score = voice =>
            (voice.lang.toLowerCase() === exactLocale ? 100 : 0)
            + (qualityHint.test(voice.name) ? 50 : 0)
            + (voice.default ? 1 : 0);
        return score(b) - score(a);
    });
    utterance.voice = matchingVoices[0] || null;
    synthesis.cancel();
    synthesis.speak(utterance);
}

function renderQuiz() {
    const word = state.quizQuestions[state.quizIndex];
    if (!word) {
        const hasWords = state.quizOptionPool.length > 0;
        return `<div class="screen center"><div class="empty"><h3>${esc(tr(hasWords ? "Недостаточно вариантов" : "Нет слов для проверки"))}</h3><p>${esc(tr(hasWords ? "Добавьте слово с другим переводом в эту языковую пару." : "Добавьте слова в словарь или выберите непустую колоду."))}</p><button class="btn tonal" data-action="quiz-done">${esc(tr("Готово"))}</button></div></div>`;
    }

    const number = state.quizIndex + 1;
    const count = state.quizQuestions.length;
    const progress = Math.round(number * 100 / count);
    return `<div class="screen center quiz-screen">
        <section class="quiz-content">
            <span class="eyebrow">${esc(tr(state.quizTitle))}</span>
            <p class="quiz-counter">${esc(tr("Вопрос {current} из {total}", { current: number, total: count }))}</p>
            <div class="progress-track"><span style="width:${progress}%"></span></div>
            <div class="quiz-card">
                <p class="body-medium">${esc(tr("Выберите правильный перевод"))}</p>
                <div class="word-audio-heading"><h2>${esc(word.original)}${word.transcription ? ` <span>[${esc(word.transcription)}]</span>` : ""}</h2>${renderSpeakButton(word)}</div>
            </div>
            <div class="quiz-options">
                ${state.quizOptions.map(option => {
                    const selected = option === state.quizSelectedOption;
                    const correct = state.quizAnswerSubmitted && isQuizAnswerCorrect(option, word.translation);
                    const incorrect = state.quizAnswerSubmitted && selected && !state.quizAnswerCorrect;
                    return `<button class="quiz-option ${selected ? "selected" : ""} ${correct ? "correct" : ""} ${incorrect ? "incorrect" : ""}"
                        data-action="select-quiz-option" data-id="${esc(option)}" ${state.quizAnswerSubmitted ? "disabled" : ""}>
                        <span>${esc(option)}</span>${selected || correct ? "<strong>✓</strong>" : ""}
                    </button>`;
                }).join("")}
            </div>
            ${state.quizAnswerSubmitted
                ? `${!state.quizAnswerCorrect
                    ? `<div class="quiz-feedback incorrect"><p>${esc(tr("Правильный ответ: {translation}", { translation: word.translation }))}</p>${renderSpeakButton(word, "translation")}</div>`
                    : ""}
                ${state.quizAnswerCorrect ? `<div class="quiz-feedback correct"><p>${esc(word.translation)}</p>${renderSpeakButton(word, "translation")}</div>` : ""}
                ${word.example ? `<p class="word-example">${esc(word.example)}</p>` : ""}
                ${word.exampleTranslation ? `<p class="word-example-translation">${esc(word.exampleTranslation)}</p>` : ""}
                <button class="btn" data-action="next-quiz">${esc(tr(number === count ? "Показать результат" : "Следующее слово"))}</button>`
                : `<button class="btn" data-action="submit-quiz" ${state.quizSelectedOption == null ? "disabled" : ""}>${esc(tr("Проверить ответ"))}</button>`}
        </section>
    </div>`;
}

function renderQuizResults() {
    const count = state.quizQuestions.length;
    const accuracy = count ? Math.round(state.quizScore * 100 / count) : 0;
    let mistakes = "";
    if (state.quizMistakes.length === 0) {
        mistakes = `<div class="empty"><h3>${esc(tr("Все ответы верны!"))}</h3></div>`;
    } else {
        mistakes = `<h3 class="mistake-heading">${esc(tr("Нужно повторить"))}</h3>` + state.quizMistakes.map(({ word, answer }) => `
            <div class="card mistake-card">
                <div class="word-title-row"><strong>${esc(word.original)}</strong>${renderSpeakButton(word)}</div>
                <span class="wrong-answer">${esc(tr("Ваш ответ: {answer}", { answer: answer || "—" }))}</span>
                <div class="word-translation-audio"><span class="body-medium">${esc(tr("Перевод: {translation}", { translation: word.translation }))}</span>${renderSpeakButton(word, "translation")}</div>
            </div>`).join("");
    }
    return `<div class="screen quiz-results">
        <section class="result-summary">
            <span class="eyebrow">${esc(tr("РЕЗУЛЬТАТ"))}</span>
            <h2>${esc(tr("{correct} из {total}", { correct: state.quizScore, total: count }))}</h2>
            <p>${esc(tr("Точность: {accuracy}%", { accuracy }))}</p>
        </section>
        ${mistakes}
        ${state.quizMistakes.length ? `<button class="btn" data-action="retry-mistakes">${esc(tr("Проверить ошибки ещё раз"))}</button>` : ""}
        <button class="btn tonal" data-action="quiz-done">${esc(tr("Готово"))}</button>
        <div class="spacer16"></div>
    </div>`;
}

function renderWords(list) {
    if (list.length === 0) {
        return `<div class="screen center has-fixed-action"><div class="empty"><h3>${esc(tr("Слов нет"))}</h3><p>${esc(tr("Ваш словарь пока пуст."))}</p></div></div>
            <div class="word-add-dock"><button class="btn" data-action="add-word">${esc(tr("Добавить слово"))}</button></div>`;
    }

    let body = `<div class="row space-between">
        <span class="body-medium">${state.selectionMode
            ? tr("Выбрано: {count}", { count: state.selectedWordIds.size })
            : tr("Всего слов: {count}", { count: list.length })}</span>
        ${state.selectionMode
            ? `<button class="text-btn" data-action="toggle-select-all">${esc(tr(state.selectedWordIds.size === list.length ? "Снять выбор" : "Выбрать все"))}</button>`
            : `<button class="text-btn" data-action="select-mode">${esc(tr("Выбрать"))}</button>`}
    </div>
    <div class="spacer8"></div>
    `;

    list.forEach(word => {
        const selected = state.selectedWordIds.has(word.id);
        body += `<div class="card word-card ${state.selectionMode ? "clickable" : ""} ${selected ? "selected" : ""}"
            ${state.selectionMode ? `data-action="toggle-word" data-id="${esc(word.id)}"` : ""}>
            <div class="row">
                <div class="grow word-card-content">
                    <div class="word-title-row"><div class="title-medium word-card-term">${esc(word.original)}${word.transcription ? ` <span class="body-medium">[${esc(word.transcription)}]</span>` : ""}</div>${renderSpeakButton(word)}</div>
                    <div class="word-translation-audio"><div class="word-card-translation">${esc(word.translation)}</div>${renderSpeakButton(word, "translation")}</div>
                    ${word.example?.trim() || word.exampleTranslation?.trim() ? `<div class="word-example-box">
                        ${word.example?.trim() ? `<p class="word-card-example">${esc(word.example)}</p>` : ""}
                        ${word.exampleTranslation?.trim() ? `<p class="word-card-example-translation">${esc(word.exampleTranslation)}</p>` : ""}
                    </div>` : ""}
                    ${word.quizCorrectStreak >= 3
                        ? `<div class="word-mastery word-progress-badge">${esc(tr("Освоено ✓"))}</div>`
                        : word.quizAttemptCount > 0
                            ? `<div class="word-quiz-progress word-progress-badge">${esc(tr("В проверках: {correct}/{attempts} · подряд {streak}", {
                                correct: word.quizCorrectCount, attempts: word.quizAttemptCount, streak: word.quizCorrectStreak
                            }))}</div>`
                            : ""}
                </div>
                ${state.selectionMode ? `<input type="checkbox" class="checkbox" ${selected ? "checked" : ""} data-id="${esc(word.id)}" data-checkbox>` : ""}
            </div>
            ${!state.selectionMode ? `<div class="row end mt8">
                <button class="btn tonal" style="width:auto;height:auto;padding:10px 20px;border-radius:14px;font-size:14px" data-action="edit-word" data-id="${esc(word.id)}">${esc(tr("Изменить"))}</button>
                <button class="btn outlined danger-text" style="width:auto;height:auto;padding:10px 20px;border-radius:14px;font-size:14px" data-action="delete-word" data-id="${esc(word.id)}">${esc(tr("Удалить"))}</button>
            </div>` : ""}
        </div>`;
    });

    body += `<div class="spacer12"></div><div class="spacer16"></div>`;
    const dock = `<div class="word-add-dock${state.selectionMode ? " has-selection-actions" : ""}">
        ${state.selectionMode
            ? `<button class="btn error" data-action="delete-selected" ${state.selectedWordIds.size === 0 ? "disabled" : ""}>${esc(tr("Удалить выбранные ({count})", { count: state.selectedWordIds.size }))}</button>`
            : `<button class="btn" data-action="add-word">${esc(tr("Добавить слово"))}</button>`}
    </div>`;
    return `<div class="screen has-fixed-action${state.selectionMode ? " has-selection-actions" : ""}">${body}</div>${dock}`;
}

// ---------- Dialogs ----------

function renderLanguagePicker(label, selectedTag, pickerId) {
    const lang = inputLanguages.find(l => l.tag === selectedTag) || inputLanguages[0];
    return `<div class="picker-row">
        <span class="label">${esc(tr(label))}</span>
        <div class="dropdown">
            <button class="picker-btn" data-action="dropdown" data-id="${pickerId}" aria-haspopup="dialog">${esc(lang.label)}&nbsp;&nbsp;▾</button>
        </div>
    </div>`;
}

function renderLanguageChooser(pickerId) {
    const selectedTag = pickerId === "app-language" ? state.language
        : pickerId === "deck-original-lang" ? state.topicEditor?.originalLanguageTag
            : pickerId === "deck-translation-lang" ? state.topicEditor?.translationLanguageTag
                : pickerId === "orig-lang" ? state.editor?.originalLanguageTag
                    : state.editor?.translationLanguageTag;
    return `<div class="dialog-backdrop language-dialog-backdrop" data-action="dismiss-language-picker">
        <section class="dialog language-dialog" data-dialog role="dialog" aria-modal="true" aria-labelledby="language-dialog-title">
            <div class="language-dialog-header">
                <h3 id="language-dialog-title">${esc(tr("Выберите язык"))}</h3>
                <button class="language-close" type="button" data-action="dismiss-language-picker" aria-label="${esc(tr("Отмена"))}">×</button>
            </div>
            <input class="field language-search" type="search" value="${esc(state.languageSearch)}" placeholder="${esc(tr("Поиск языка"))}" data-language-search autocomplete="off">
            <div class="language-options" role="listbox" aria-label="${esc(tr("Выберите язык"))}">
                ${inputLanguages.map(language => `<button class="language-option" type="button" data-language-option data-action="pick-language" data-picker="${pickerId}" data-tag="${language.tag}" role="option" aria-selected="${language.tag === selectedTag}">
                    <span>${esc(language.label)}</span><span class="language-option-code">${esc(language.tag.toUpperCase())}</span>${language.tag === selectedTag ? `<span class="language-option-check" aria-hidden="true">✓</span>` : ""}
                </button>`).join("")}
                <p class="language-empty" hidden>${esc(tr("Язык не найден"))}</p>
            </div>
            <div class="language-dialog-footer"><button class="btn outlined" type="button" data-action="dismiss-language-picker">${esc(tr("Отмена"))}</button></div>
        </section>
    </div>`;
}

function renderTopicPicker(topics, selectedTopicIds) {
    return `<div class="picker-row topic-picker-row">
        <span class="label">${esc(tr("Колоды (выберите одну или несколько)"))}</span>
        <div class="topic-membership-list">${topics.length ? topics.map(topic =>
            `<button type="button" class="picker-btn topic-membership ${selectedTopicIds.includes(topic.id) ? "selected" : ""}" data-action="pick-topic" data-id="${esc(topic.id)}">${selectedTopicIds.includes(topic.id) ? "☑" : "□"} ${esc(topic.name)}</button>`
        ).join("") : `<span class="hint">${esc(tr("Сначала создайте колоду"))}</span>`}</div>
    </div>`;
}

function renderWordEditor() {
    const d = state.editor;
    const selectedTopicIds = d.topicIds || (d.topicId ? [d.topicId] : []);
    const deck = state.topics.find(topic => selectedTopicIds.includes(topic.id));
    const placeholders = {
        ru: "Например: привет", en: "For example: hello", es: "Por ejemplo: hola",
        fr: "Par exemple : bonjour", de: "Zum Beispiel: hallo"
    };
    return `<div class="dialog-backdrop" data-action="dismiss-editor">
        <div class="dialog" data-dialog>
            <h3>${esc(tr(d.id == null ? "Новое слово" : "Редактировать слово"))}</h3>
            ${deck
                ? `<p class="hint">${esc(tr("Язык слова"))}: ${esc(languageLabel(deck.originalLanguageTag))}</p>`
                : renderLanguagePicker("Язык слова", d.originalLanguageTag, "orig-lang")}
            <textarea class="field" rows="2" placeholder="${esc(tr("Слово"))}" data-field="original" lang="${esc(d.originalLanguageTag)}">${esc(d.original)}</textarea>
            ${d.originalLanguageTag === "en" ? `<input class="field" type="text" placeholder="${esc(tr("Транскрипция (необязательно)"))}" data-field="transcription" value="${esc(d.transcription)}" autocapitalize="off">` : ""}
            <div class="spacer8"></div>
            ${deck
                ? `<p class="hint">${esc(tr("Язык перевода"))}: ${esc(languageLabel(deck.translationLanguageTag))}</p>`
                : renderLanguagePicker("Язык перевода", d.translationLanguageTag, "trans-lang")}
            <textarea class="field" rows="2" placeholder="${esc(tr(placeholders[d.translationLanguageTag] || "Перевод"))}" data-field="translation" lang="${esc(d.translationLanguageTag)}">${esc(d.translation)}</textarea>
            <div class="spacer8"></div>
            <textarea class="field" rows="2" placeholder="${esc(tr("Пример употребления (необязательно)"))}" data-field="example" lang="${esc(d.originalLanguageTag)}">${esc(d.example)}</textarea>
            <textarea class="field" rows="2" placeholder="${esc(tr("Перевод примера (необязательно)"))}" data-field="example-translation" lang="${esc(d.translationLanguageTag)}">${esc(d.exampleTranslation)}</textarea>
            <div class="spacer8"></div>
            ${renderTopicPicker(state.topics.filter(topic =>
                topic.originalLanguageTag === d.originalLanguageTag &&
                topic.translationLanguageTag === d.translationLanguageTag
            ), selectedTopicIds)}
            <div class="dialog-actions">
                <button class="text-btn" data-action="dismiss-editor">${esc(tr("Отмена"))}</button>
                <button class="text-btn" data-action="save-word" ${!(d.original.trim() && d.translation.trim() && selectedTopicIds.length) ? "disabled" : ""}>${esc(tr("Сохранить"))}</button>
            </div>
        </div>
    </div>`;
}

function renderTopicEditor() {
    const d = state.topicEditor;
    const isNew = d.id == null;
    const title = isNew ? "Новая колода" : "Настройки колоды";
    const mark = (d.name || "＋").trim().charAt(0).toLocaleUpperCase();
    const languageSettings = isNew
        ? `<section class="topic-create-languages">
                ${renderLanguagePicker("Язык слова", d.originalLanguageTag, "deck-original-lang")}
                ${renderLanguagePicker("Язык перевода", d.translationLanguageTag, "deck-translation-lang")}
            </section>`
        : `<section class="topic-language-summary" aria-label="${esc(tr("Языковая пара"))}">
                <div class="topic-language-item"><span>${esc(tr("Язык слова"))}</span><strong>${esc(languageLabel(d.originalLanguageTag))}</strong></div>
                <span class="topic-language-separator" aria-hidden="true">→</span>
                <div class="topic-language-item"><span>${esc(tr("Язык перевода"))}</span><strong>${esc(languageLabel(d.translationLanguageTag))}</strong></div>
            </section>`;
    return `<div class="dialog-backdrop" data-action="dismiss-topic-editor">
            <section class="dialog topic-settings-dialog ${isNew ? "topic-create-dialog" : ""}" data-dialog role="dialog" aria-modal="true" aria-labelledby="topic-settings-title">
                <header class="topic-settings-header">
                    <div class="topic-settings-mark" aria-hidden="true">${esc(mark)}</div>
                    <div class="topic-settings-heading">
                        <span class="eyebrow">${esc(tr("Колода"))}</span>
                        <h3 id="topic-settings-title">${esc(tr(title))}</h3>
                    </div>
                    <button class="language-close" type="button" data-action="dismiss-topic-editor" aria-label="${esc(tr("Отмена"))}">×</button>
                </header>
                <div class="topic-settings-content">
                    ${isNew ? "" : `<label class="field-label" for="topic-settings-name">${esc(tr("Название колоды"))}</label>`}
                    <input id="topic-settings-name" class="field" type="text" placeholder="${esc(tr("Например, Путешествия"))}" data-field="topic-name" value="${esc(d.name)}">
                    ${languageSettings}
                    <section class="daily-limit-card">
                        <div class="daily-limit-copy">
                            <label for="topic-settings-limit">${esc(tr("Количество новых слов в день"))}</label>
                            <p>${esc(tr("Лимит применяется к новым словам этой колоды."))}</p>
                        </div>
                        <input id="topic-settings-limit" class="field daily-limit-input" type="number" min="0" step="1" data-field="new-cards-limit" value="${esc(d.newCardsPerDay)}" inputmode="numeric">
                    </section>
                </div>
                <footer class="topic-settings-footer">
                    <button class="btn outlined" data-action="dismiss-topic-editor">${esc(tr("Отмена"))}</button>
                    <button class="btn" data-action="save-topic" ${!d.name.trim() ? "disabled" : ""}>${esc(tr("Сохранить"))}</button>
                </footer>
            </section>
        </div>`;
}

function confirmDialog(title, text, confirmAction, confirmLabel = "Удалить") {
    return `<div class="dialog-backdrop" data-action="dismiss-confirm">
        <div class="dialog" data-dialog>
            <h3>${esc(tr(title))}</h3>
            ${text ? `<p>${esc(text)}</p>` : ""}
            <div class="dialog-actions">
                <button class="text-btn" data-action="dismiss-confirm">${esc(tr("Отмена"))}</button>
                <button class="text-btn ${confirmLabel === "Удалить" ? "danger" : ""}" data-action="${confirmAction}">${esc(tr(confirmLabel))}</button>
            </div>
        </div>
    </div>`;
}

// ---------- Render ----------

function render() {
    let body;
    switch (state.screen) {
        case Screen.HOME: body = renderHome(); break;
        case Screen.TOPICS: body = renderTopics(); break;
        case Screen.STUDY: body = renderStudy(); break;
        case Screen.WORDS: body = renderWords(state.words); break;
        case Screen.DUE_WORDS: body = renderDueWords(); break;
        case Screen.TOPIC_WORDS:
            body = renderWords(state.words.filter(w => belongsToTopic(w, state.selectedTopicId)));
            break;
        case Screen.QUIZ: body = renderQuiz(); break;
        case Screen.QUIZ_RESULTS: body = renderQuizResults(); break;
        case Screen.STATS: body = renderStats(); break;
    }

    // Bottom bar: study answer buttons
    const pool = studyWords();
    const currentWord = pool.find(w => w.id === state.currentWordId) || null;
    let bottomBar = "";
    if (state.screen === Screen.STUDY && state.answerShown && currentWord) {
        bottomBar = `<div class="bottom-bar">
            <button class="btn outlined" data-action="answer-again">${esc(tr("Заново"))}</button>
            <button class="btn" data-action="answer-good">${esc(tr("Хорошо"))}</button>
        </div>`;
    }
    if (state.screen === Screen.DUE_WORDS && dueCards(state.words).length) {
        bottomBar = `<div class="bottom-bar fixed-bottom-bar"><button class="btn" data-action="study-due">${esc(tr("Начать повторение"))}</button></div>`;
    }

    let dialog = "";
    if (state.editor) dialog += renderWordEditor();
    else if (state.topicEditor) dialog += renderTopicEditor();
    if (state.openDropdown) dialog += renderLanguageChooser(state.openDropdown);
    if (state.deleteTarget) {
        dialog += confirmDialog("Удалить слово?", "", "confirm-delete-word");
    } else if (state.bulkDeleteTargets) {
        const n = state.bulkDeleteTargets.length;
        dialog += confirmDialog("Удалить выбранные слова?",
            tr("Вы точно хотите удалить {count} {words}?", { count: n, words: wordCountLabel(n) }), "confirm-bulk-delete");
    } else if (state.topicDeleteTarget) {
        const onlyHere = state.words.filter(word => belongsToTopic(word, state.topicDeleteTarget.id) &&
            !(word.topicIds || [word.topicId]).some(id => id && id !== state.topicDeleteTarget.id)).length;
        dialog += confirmDialog("Удалить колоду?",
            tr("Слова, которые есть только в этой колоде, тоже будут удалены: {count}.", { count: onlyHere }),
            "confirm-delete-topic");
    } else if (state.topicRenameTarget) {
        const d = state.topicRenameTarget;
        const oldName = state.topics.find(t => t.id === d.id)?.name || "";
        dialog += confirmDialog("Изменить имя колоды?",
            tr("Колода «{old}» будет переименована в «{new}».", { old: oldName, new: d.name.trim() }),
            "confirm-rename-topic", "Сохранить");
    }
    const undoBar = state.undo
        ? `<div class="undo-bar${state.screen === Screen.STUDY && state.answerShown ? " above-study-actions" : ""}${state.screen === Screen.WORDS || state.screen === Screen.TOPIC_WORDS ? " above-word-actions" : ""}${state.screen === Screen.TOPICS ? " above-deck-actions" : ""}${state.screen === Screen.DUE_WORDS ? " above-due-action" : ""}">
            <span>${esc(tr(state.undo.message))}</span>
            <button class="text-btn" data-action="undo">${esc(tr("Отменить"))}</button>
        </div>`
        : "";

    const app = document.getElementById("app");
    document.querySelector("body > .word-add-dock")?.remove();
    const deckAddDock = document.getElementById("deck-add-dock");
    if (deckAddDock) {
        deckAddDock.hidden = state.screen !== Screen.TOPICS;
        deckAddDock.querySelector("button").textContent = `＋  ${tr("Добавить колоду")}`;
    }
    app.innerHTML = "";
    app.appendChild(h(renderHeader(screenTitle()) + body + bottomBar + dialog + undoBar));
    const wordAddDock = app.querySelector(".word-add-dock");
    if (wordAddDock) document.body.appendChild(wordAddDock);
    bindFields();
    if (state.openDropdown) app.querySelector("[data-language-search]")?.focus({ preventScroll: true });
}

// Keep textarea values in the draft without re-rendering on every keystroke
function bindFields() {
    document.querySelectorAll("[data-language-search]").forEach(el => {
        el.addEventListener("input", () => {
            state.languageSearch = el.value;
            const query = el.value.trim().toLocaleLowerCase();
            let matches = 0;
            document.querySelectorAll("[data-language-option]").forEach(option => {
                const visible = `${option.textContent} ${option.dataset.tag}`.toLocaleLowerCase().includes(query);
                option.hidden = !visible;
                if (visible) matches++;
            });
            const emptyState = document.querySelector(".language-empty");
            if (emptyState) emptyState.hidden = matches > 0;
        });
    });
    document.querySelectorAll("[data-field]").forEach(el => {
        el.addEventListener("input", () => {
            const field = el.dataset.field;
            if (field === "original" && state.editor) state.editor.original = el.value;
            if (field === "translation" && state.editor) state.editor.translation = el.value;
            if (field === "transcription" && state.editor) state.editor.transcription = el.value;
            if (field === "example" && state.editor) state.editor.example = el.value;
            if (field === "example-translation" && state.editor) state.editor.exampleTranslation = el.value;
            if (field === "topic-name" && state.topicEditor) state.topicEditor.name = el.value;
            if (field === "new-cards-limit" && state.topicEditor) {
                state.topicEditor.newCardsPerDay = Math.max(0, parseInt(el.value || "0", 10) || 0);
            }
            // refresh save button enabled state
            const saveBtn = document.querySelector('[data-action="save-word"], [data-action="save-topic"]');
            if (saveBtn) {
                if (state.editor) saveBtn.disabled = !(state.editor.original.trim() && state.editor.translation.trim());
                if (state.topicEditor) saveBtn.disabled = !state.topicEditor.name.trim();
            }
        });
    });
}

// ---------- Actions ----------

function nextDueWordId(pool, excludeId = null, now = Date.now()) {
    const due = pool.filter(w => w.nextReviewAtMillis <= now && w.id !== excludeId);
    due.sort((a, b) => a.nextReviewAtMillis - b.nextReviewAtMillis);
    return due.length ? due[0].id : null;
}

const actions = {
    "back": () => goBack(),
    "set-theme": mode => {
        if (mode !== "light" && mode !== "dark" && mode !== "system") return;
        applyTheme(mode);
        localStorage.setItem(KEY_THEME, mode);
        render();
    },
    "study": () => openStudy(),
    "show-due-words": () => { state.screen = Screen.DUE_WORDS; render(); },
    "study-due": () => openStudy(),
    "stats": () => { state.screen = Screen.STATS; state.calendarYear = new Date().getFullYear(); state.calendarSelectedDay = defaultStatsDay(state.calendarYear); render(); },
    "stats-year-prev": () => { state.calendarYear--; state.calendarSelectedDay = defaultStatsDay(state.calendarYear); render(); },
    "stats-year-next": () => { state.calendarYear = Math.min(new Date().getFullYear(), state.calendarYear + 1); state.calendarSelectedDay = defaultStatsDay(state.calendarYear); render(); },
    "stats-day-select": (_, el) => { state.calendarSelectedDay = Number(el.dataset.day); render(); },
    "decks": () => { state.screen = Screen.TOPICS; render(); },
    "quiz-all": () => startQuiz(state.words, Screen.HOME, "Все слова"),
    "open-topic": id => { state.selectedTopicId = id || null; state.screen = Screen.TOPIC_WORDS; render(); },
    "study-topic": id => { state.selectedTopicId = id || null; openStudy(id || null, true); },
    "quiz-topic": id => {
        const topicId = id || null;
        const topicWords = state.words.filter(w => belongsToTopic(w, topicId));
        const title = state.topics.find(t => t.id === topicId)?.name || "Без колоды";
        startQuiz(topicWords, Screen.TOPICS, title, topicWords);
    },
    "reorder-topic": (sourceId, targetId) => {
        const reordered = reorderTopics(state.topics, sourceId, targetId);
        if (reordered === state.topics) return;
        saveTopics(reordered);
        render();
    },
    "open-words": () => {
        state.screen = state.isTopicStudy ? Screen.TOPIC_WORDS : Screen.TOPICS;
        if (state.isTopicStudy) state.selectedTopicId = state.studyTopicId;
        render();
    },
    "show-answer": () => { state.answerShown = true; render(); },
    "speak-word": (id, el) => {
        const word = state.words.find(item => item.id === id);
        if (word) speakWord(word, el.dataset.side);
    },
    "select-quiz-option": option => {
        if (state.quizAnswerSubmitted) return;
        state.quizSelectedOption = option;
        render();
    },
    "submit-quiz": () => {
        const word = state.quizQuestions[state.quizIndex];
        const answer = state.quizSelectedOption;
        if (!word || state.quizAnswerSubmitted || !answer?.trim()) return;
        const acceptedTranslations = state.words
            .filter(item => sameQuizPrompt(item, word))
            .map(item => item.translation);
        const correct = acceptedTranslations.some(translation => isQuizAnswerCorrect(answer, translation));
        state.quizAnswerCorrect = correct;
        state.quizAnswerSubmitted = true;
        if (correct) state.quizScore++;
        else state.quizMistakes = [...state.quizMistakes, { word, answer }];
        saveWords(state.words.map(w => w.id === word.id ? {
            ...w,
            quizAttemptCount: w.quizAttemptCount + 1,
            quizCorrectCount: w.quizCorrectCount + (correct ? 1 : 0),
            quizCorrectStreak: correct ? w.quizCorrectStreak + 1 : 0
        } : w));
        render();
    },
    "next-quiz": () => {
        if (state.quizIndex + 1 >= state.quizQuestions.length) {
            state.screen = Screen.QUIZ_RESULTS;
        } else {
            state.quizIndex++;
            state.quizAnswerSubmitted = false;
            state.quizAnswerCorrect = false;
            prepareQuizOptions();
        }
        render();
    },
    "retry-mistakes": () => {
        const returnScreen = state.quizReturnScreen;
        startQuiz(state.quizMistakes.map(mistake => mistake.word), returnScreen, "Повтор ошибок", state.words);
    },
    "quiz-done": () => {
        state.screen = state.quizReturnScreen;
        render();
    },
    "answer-again": () => {
        answerStudyCard(false);
    },
    "answer-good": () => {
        answerStudyCard(true);
    },
    "add-word": () => {
        const topic = state.screen === Screen.TOPIC_WORDS
            ? state.topics.find(item => item.id === state.selectedTopicId)
            : null;
        state.editor = {
            id: null, original: "", translation: "",
            originalLanguageTag: topic?.originalLanguageTag || "en",
            translationLanguageTag: topic?.translationLanguageTag || "ru",
            topicId: state.screen === Screen.TOPIC_WORDS ? state.selectedTopicId : null,
            topicIds: state.screen === Screen.TOPIC_WORDS ? [state.selectedTopicId] : [],
            transcription: "",
            example: "",
            exampleTranslation: ""
        };
        render();
    },
    "edit-word": id => {
        const w = state.words.find(x => x.id === id);
        if (!w) return;
        state.editor = {
            id: w.id, original: w.original, translation: w.translation,
            originalLanguageTag: w.originalLanguageTag, translationLanguageTag: w.translationLanguageTag,
            topicId: w.topicId, topicIds: [...(w.topicIds || [w.topicId]).filter(Boolean)], transcription: w.transcription || "",
            example: w.example || "", exampleTranslation: w.exampleTranslation || ""
        };
        render();
    },
    "delete-word": id => { state.deleteTarget = state.words.find(x => x.id === id) || null; render(); },
    "confirm-delete-word": () => {
        const previousWords = state.words;
        saveWords(state.words.filter(w => w.id !== state.deleteTarget.id));
        state.deleteTarget = null;
        offerUndo("Слово удалено", () => saveWords(previousWords));
        render();
    },
    "select-mode": () => { state.selectionMode = true; render(); },
    "toggle-word": id => {
        const s = new Set(state.selectedWordIds);
        s.has(id) ? s.delete(id) : s.add(id);
        state.selectedWordIds = s;
        render();
    },
    "toggle-select-all": () => {
        const list = state.screen === Screen.TOPIC_WORDS
            ? state.words.filter(w => belongsToTopic(w, state.selectedTopicId))
            : state.words;
        state.selectedWordIds = state.selectedWordIds.size === list.length
            ? new Set()
            : new Set(list.map(w => w.id));
        render();
    },
    "delete-selected": () => {
        const list = state.screen === Screen.TOPIC_WORDS
            ? state.words.filter(w => belongsToTopic(w, state.selectedTopicId))
            : state.words;
        state.bulkDeleteTargets = list.filter(w => state.selectedWordIds.has(w.id));
        render();
    },
    "confirm-bulk-delete": () => {
        const ids = new Set(state.bulkDeleteTargets.map(w => w.id));
        const previousWords = state.words;
        saveWords(state.words.filter(w => !ids.has(w.id)));
        state.bulkDeleteTargets = null;
        state.selectionMode = false;
        state.selectedWordIds = new Set();
        offerUndo("Слова удалены", () => saveWords(previousWords));
        render();
    },
    "add-topic": () => {
        state.topicEditor = {
            id: null,
            name: "",
            originalLanguageTag: "en",
            translationLanguageTag: "ru",
            newCardsPerDay: DEFAULT_NEW_CARDS_PER_DAY
        };
        render();
    },
    "edit-topic": id => {
        const t = state.topics.find(x => x.id === id);
        if (!t) return;
        state.topicEditor = {
            id: t.id,
            name: t.name,
            originalLanguageTag: t.originalLanguageTag,
            translationLanguageTag: t.translationLanguageTag,
            newCardsPerDay: t.newCardsPerDay
        };
        render();
    },
    "delete-topic": id => {
        state.topicDeleteTarget = state.topics.find(x => x.id === id) || null;
        render();
    },
    "confirm-delete-topic": () => {
        const t = state.topicDeleteTarget;
        const previousTopics = state.topics;
        const previousWords = state.words;
        const previousScreen = state.screen;
        const previousSelectedTopicId = state.selectedTopicId;
        saveTopics(state.topics.filter(x => x.id !== t.id));
        saveWords(state.words.map(w => {
            if (!belongsToTopic(w, t.id)) return w;
            const topicIds = (w.topicIds || [w.topicId]).filter(id => id && id !== t.id);
            return topicIds.length ? {
                ...w,
                topicIds,
                topicId: topicIds[0],
                introducedInTopicId: w.introducedInTopicId && topicIds.includes(w.introducedInTopicId)
                    ? w.introducedInTopicId : topicIds[0]
            } : null;
        }).filter(Boolean));
        if (state.selectedTopicId === t.id) {
            state.selectedTopicId = null;
            state.screen = Screen.TOPICS;
        }
        state.topicDeleteTarget = null;
        offerUndo("Колода удалена", () => {
            saveTopics(previousTopics);
            saveWords(previousWords);
            state.screen = previousScreen;
            state.selectedTopicId = previousSelectedTopicId;
        });
        render();
    },
    "save-word": () => {
        const d = state.editor;
        if (!d || !d.original.trim() || !d.translation.trim()) return;
        const validTopics = [...new Set(d.topicIds || (d.topicId ? [d.topicId] : []))]
            .filter(id => state.topics.some(topic => topic.id === id));
        if (!validTopics.length || validTopics.some(id => {
            const deck = state.topics.find(topic => topic.id === id);
            return deck.originalLanguageTag !== d.originalLanguageTag || deck.translationLanguageTag !== d.translationLanguageTag;
        })) return;
        const validTopic = validTopics[0];
        const transcription = d.originalLanguageTag === "en" ? d.transcription.trim() || null : null;
        let updated;
        if (d.id == null) {
            updated = [...state.words, {
                id: uuid(),
                original: d.original.trim(),
                translation: d.translation.trim(),
                originalLanguageTag: d.originalLanguageTag,
                translationLanguageTag: d.translationLanguageTag,
                successCount: 0,
                nextReviewAtMillis: 0,
                topicId: validTopic,
                topicIds: validTopics,
                transcription,
                example: d.example.trim() || null,
                exampleTranslation: d.exampleTranslation.trim() || null,
                quizAttemptCount: 0,
                quizCorrectCount: 0,
                quizCorrectStreak: 0,
                introducedAtMillis: 0,
                introducedInTopicId: null,
                intervalDays: 0
            }];
        } else {
            updated = state.words.map(w => {
                if (w.id !== d.id) return w;
                const original = d.original.trim();
                const translation = d.translation.trim();
                const contentChanged = w.original !== original ||
                    w.translation !== translation ||
                    w.originalLanguageTag !== d.originalLanguageTag ||
                    w.translationLanguageTag !== d.translationLanguageTag;
                const baseWord = contentChanged ? resetStudyProgress(w) : w;
                const introDeck = baseWord.introducedInTopicId && validTopics.includes(baseWord.introducedInTopicId)
                    ? baseWord.introducedInTopicId : validTopic;
                return {
                    ...baseWord,
                    original,
                    translation,
                    originalLanguageTag: d.originalLanguageTag,
                    translationLanguageTag: d.translationLanguageTag,
                    topicId: validTopic,
                    topicIds: validTopics,
                    introducedInTopicId: introDeck,
                    transcription,
                    example: d.example.trim() || null,
                    exampleTranslation: d.exampleTranslation.trim() || null,
                    quizAttemptCount: contentChanged ? 0 : w.quizAttemptCount,
                    quizCorrectCount: contentChanged ? 0 : w.quizCorrectCount,
                    quizCorrectStreak: contentChanged ? 0 : w.quizCorrectStreak
                };
            });
        }
        saveWords(updated);
        state.editor = null;
        render();
    },
    "save-topic": () => {
        const d = state.topicEditor;
        if (!d || !d.name.trim()) return;
        if (d.id == null) {
            const name = d.name.trim();
            const newTopic = {
                id: uuid(),
                name,
                originalLanguageTag: d.originalLanguageTag,
                translationLanguageTag: d.translationLanguageTag,
                newCardsPerDay: d.newCardsPerDay
            };
            saveTopics([...state.topics, newTopic]);
            state.topicEditor = null;
            render();
        } else {
            if (state.topics.find(topic => topic.id === d.id)?.name === d.name.trim()) {
                saveTopics(state.topics.map(topic => topic.id === d.id ? {
                    ...topic, newCardsPerDay: d.newCardsPerDay
                } : topic));
                state.topicEditor = null;
                render();
            } else {
                state.topicRenameTarget = d;
                render();
            }
        }
    },
    "confirm-rename-topic": () => {
        const d = state.topicRenameTarget;
        if (!d) return;
        const name = d.name.trim();
        saveTopics(state.topics.map(t => t.id === d.id ? {
            ...t, name, newCardsPerDay: d.newCardsPerDay
        } : t));
        state.topicRenameTarget = null;
        state.topicEditor = null;
        render();
    },
    "dropdown": id => {
        state.openDropdown = state.openDropdown === id ? null : id;
        state.languageSearch = "";
        render();
    },
    "dismiss-language-picker": () => {
        state.openDropdown = null;
        state.languageSearch = "";
        render();
    },
    "pick-language": (tag, el) => {
        const picker = el.dataset.picker;
        if (picker === "app-language") {
            state.language = tag;
            document.documentElement.lang = tag;
            localStorage.setItem(KEY_LANGUAGE, tag);
        } else if ((picker === "orig-lang" || picker === "trans-lang") && state.editor) {
            if (picker === "orig-lang") state.editor.originalLanguageTag = tag;
            else state.editor.translationLanguageTag = tag;
            state.editor.topicIds = [];
            state.editor.topicId = null;
        }
        if (picker === "deck-original-lang" && state.topicEditor) state.topicEditor.originalLanguageTag = tag;
        if (picker === "deck-translation-lang" && state.topicEditor) state.topicEditor.translationLanguageTag = tag;
        state.openDropdown = null;
        state.languageSearch = "";
        render();
    },
    "pick-topic": id => {
        if (state.editor && id) {
            const ids = state.editor.topicIds || (state.editor.topicId ? [state.editor.topicId] : []);
            state.editor.topicIds = ids.includes(id) ? ids.filter(item => item !== id) : [...ids, id];
            state.editor.topicId = state.editor.topicIds[0] || null;
        }
        render();
    },
    "dismiss-editor": () => { state.editor = null; state.openDropdown = null; render(); },
    "dismiss-topic-editor": () => { state.topicEditor = null; state.openDropdown = null; render(); },
    "dismiss-confirm": () => {
        state.deleteTarget = null;
        state.bulkDeleteTargets = null;
        state.topicDeleteTarget = null;
        state.topicRenameTarget = null;
        render();
    },
    "undo": () => {
        undoLastAction();
    }
};

// ---------- Event delegation ----------

function closestDeckCard(y, targets) {
    let closestId = null;
    let closestDistance = Infinity;
    for (const target of targets) {
        const distance = Math.abs(y - target.centerY);
        if (distance < closestDistance) {
            closestId = target.id;
            closestDistance = distance;
        }
    }
    return closestId;
}

function clearDeckDragVisuals(drag) {
    drag.card.classList.remove("dragging");
    drag.targetCard?.classList.remove("drag-over");
    drag.card.style.transform = "";
    drag.card.style.zIndex = "";
}

document.addEventListener("pointerdown", event => {
    const handle = event.target.closest("[data-drag-handle]");
    const card = handle?.closest("[data-topic-card]");
    if (!handle || !card || !card.dataset.topicCard) return;
    if (event.pointerType === "mouse" && event.button !== 0) return;

    const cardRect = card.getBoundingClientRect();
    const drag = {
        id: card.dataset.topicCard,
        card,
        handle,
        pointerId: event.pointerId,
        startY: event.clientY,
        startCardCenterY: cardRect.top + cardRect.height / 2,
        started: false,
        targetId: null,
        targetCard: null,
        targets: [],
        timer: null
    };
    state.deckDrag = drag;
    event.preventDefault();
    handle.setPointerCapture?.(event.pointerId);
    drag.timer = setTimeout(() => {
        if (state.deckDrag !== drag) return;
        drag.started = true;
        card.classList.add("dragging");
        drag.targets = [...document.querySelectorAll("[data-topic-card]")]
            .filter(target => target.dataset.topicCard && target.dataset.topicCard !== drag.id)
            .map(target => {
                const rect = target.getBoundingClientRect();
                return { id: target.dataset.topicCard, card: target, centerY: rect.top + rect.height / 2 };
            });
    }, 350);
});

document.addEventListener("pointermove", event => {
    const drag = state.deckDrag;
    if (!drag || drag.pointerId !== event.pointerId) return;
    if (!drag.started) {
        if (Math.abs(event.clientY - drag.startY) > 10) {
            clearTimeout(drag.timer);
            state.deckDrag = null;
        }
        return;
    }
    event.preventDefault();
    const deltaY = event.clientY - drag.startY;
    drag.card.style.transform = `translateY(${deltaY}px)`;
    const targetId = Math.abs(deltaY) >= 12 ? closestDeckCard(drag.startCardCenterY + deltaY, drag.targets) : null;
    if (targetId !== drag.targetId) {
        drag.targetCard?.classList.remove("drag-over");
        drag.targetCard = drag.targets.find(target => target.id === targetId)?.card ?? null;
        drag.targetCard?.classList.add("drag-over");
        drag.targetId = targetId;
    }
});

function finishDeckDrag(event) {
    const drag = state.deckDrag;
    if (!drag || (event.pointerId != null && drag.pointerId !== event.pointerId)) return;
    clearTimeout(drag.timer);
    state.deckDrag = null;
    clearDeckDragVisuals(drag);
    if (drag.started && drag.targetId) actions["reorder-topic"](drag.id, drag.targetId);
}

function cancelDeckDrag(event) {
    const drag = state.deckDrag;
    if (!drag || (event.pointerId != null && drag.pointerId !== event.pointerId)) return;
    clearTimeout(drag.timer);
    state.deckDrag = null;
    clearDeckDragVisuals(drag);
}

document.addEventListener("pointerup", finishDeckDrag);
document.addEventListener("pointercancel", cancelDeckDrag);

document.addEventListener("click", e => {
    const checkbox = e.target.closest("[data-checkbox]");
    if (checkbox) {
        actions["toggle-word"](checkbox.dataset.id);
        return;
    }
    const el = e.target.closest("[data-action]");
    if (!el) {
        if (state.openDropdown) {
            state.openDropdown = null;
            state.languageSearch = "";
            render();
        }
        return;
    }
    const action = el.dataset.action;
    // backdrop click only dismisses when clicking the backdrop itself
    if (el.classList.contains("dialog-backdrop") && e.target !== el) return;
    if (e.target.closest("[data-dialog]") && (action === "dismiss-editor" || action === "dismiss-topic-editor" || action === "dismiss-confirm" || action === "dismiss-language-picker")
        && !e.target.closest("button")) return;
    const fn = actions[action];
    if (fn) {
        e.stopPropagation();
        fn(el.dataset.id !== undefined ? el.dataset.id : el.dataset.tag, el);
    }
});

document.addEventListener("keydown", e => {
    if (e.key === "Escape" && state.openDropdown) {
        state.openDropdown = null;
        state.languageSearch = "";
        render();
    }
});

// Browser back button ≈ Android BackHandler
window.addEventListener("popstate", () => goBack());

// ---------- Init ----------

const storedTheme = localStorage.getItem(KEY_THEME);
const storedLanguage = localStorage.getItem(KEY_LANGUAGE);
state.language = inputLanguages.some(language => language.tag === storedLanguage) ? storedLanguage : "ru";
document.documentElement.lang = state.language;
applyTheme(["light", "dark", "system"].includes(storedTheme) ? storedTheme : "system");

if (typeof window.matchMedia === "function") {
    const systemThemeQuery = window.matchMedia("(prefers-color-scheme: dark)");
    const updateSystemTheme = () => {
        if (document.documentElement.dataset.themeMode === "system") {
            applyTheme("system");
            render();
        }
    };
    if (systemThemeQuery.addEventListener) systemThemeQuery.addEventListener("change", updateSystemTheme);
    else if (systemThemeQuery.addListener) systemThemeQuery.addListener(updateSystemTheme);
}

migrateDeckLanguages();
state.studyEvents = loadStudyEvents();
seedDecksIfNeeded().then(() => {
    state.words = loadWords();
    state.topics = loadTopics();
    render();
});

if ("serviceWorker" in navigator && location.protocol !== "file:") {
    navigator.serviceWorker.register("./service-worker.js").catch(() => {});
}
