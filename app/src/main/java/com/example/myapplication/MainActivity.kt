package com.example.myapplication

import android.os.Bundle
import android.content.Intent
import android.content.pm.PackageManager
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.Locale as ComposeLocale
import androidx.compose.ui.text.intl.LocaleList as ComposeLocaleList
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.myapplication.ui.theme.MyApplicationTheme
import java.util.Calendar
import java.util.Locale
import java.util.UUID
import kotlin.math.min
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    private var textToSpeech: TextToSpeech? = null
    private var textToSpeechReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val preferredSpeechEngine = packageManager.queryIntentServices(
            Intent(TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE),
            PackageManager.MATCH_ALL
        ).firstOrNull { it.serviceInfo.packageName == "com.google.android.tts" }
            ?.serviceInfo?.packageName
        val onSpeechEngineReady = TextToSpeech.OnInitListener { status ->
            textToSpeechReady = status == TextToSpeech.SUCCESS
            if (status != TextToSpeech.SUCCESS && preferredSpeechEngine != null) {
                textToSpeech?.shutdown()
                textToSpeech = TextToSpeech(this) { fallbackStatus ->
                    textToSpeechReady = fallbackStatus == TextToSpeech.SUCCESS
                }
            }
        }
        textToSpeech = preferredSpeechEngine?.let { engine ->
            TextToSpeech(this, onSpeechEngineReady, engine)
        } ?: TextToSpeech(this, onSpeechEngineReady)
        val preferences = getSharedPreferences("vocabulary", MODE_PRIVATE)
        setContent {
            val systemDarkTheme = isSystemInDarkTheme()
            val uiStrings = remember { UiStrings.load(assets) }
            val savedLanguage = preferences.getString("application_language", "ru")
                ?.takeIf { it in setOf("ru", "en", "es", "fr", "de", "ja", "zh") } ?: "ru"
            var appLanguage by rememberSaveable { mutableStateOf(savedLanguage) }
            val storedThemeMode = preferences.getString("theme_mode", null)
            val initialThemeMode = storedThemeMode
                ?.takeIf { it == "light" || it == "dark" || it == "system" }
                ?: if (preferences.contains("dark_theme")) {
                    if (preferences.getBoolean("dark_theme", systemDarkTheme)) "dark" else "light"
                } else {
                    "system"
                }
            var themeMode by rememberSaveable { mutableStateOf(initialThemeMode) }
            val darkTheme = when (themeMode) {
                "light" -> false
                "dark" -> true
                else -> systemDarkTheme
            }
            CompositionLocalProvider(
                LocalAppLanguage provides appLanguage,
                LocalUiStrings provides uiStrings
            ) {
                MyApplicationTheme(darkTheme = darkTheme) {
                    VocabularyApp(
                        themeMode = themeMode,
                        onThemeChange = { selectedThemeMode ->
                            themeMode = selectedThemeMode
                            preferences.edit()
                                .putString("theme_mode", selectedThemeMode)
                                .remove("dark_theme")
                                .apply()
                        },
                        onAppLanguageChange = { selectedLanguage ->
                            appLanguage = selectedLanguage
                            preferences.edit().putString("application_language", selectedLanguage).apply()
                        },
                        onSpeakWord = ::speakWord
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        textToSpeech = null
        textToSpeechReady = false
        super.onDestroy()
    }

    private fun speakWord(word: Word): Boolean {
        val speaker = textToSpeech?.takeIf { textToSpeechReady } ?: return false
        val locale = Locale.forLanguageTag(word.originalLanguageTag)
        val bestVoice = speaker.voices.orEmpty()
            .asSequence()
            .filter { it.locale.language.equals(locale.language, ignoreCase = true) }
            .filter { TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED !in it.features }
            .sortedWith(
                compareByDescending<android.speech.tts.Voice> {
                    it.locale.country.equals(locale.country, ignoreCase = true)
                }
                    .thenByDescending { it.quality }
                    .thenBy { it.latency }
                    .thenBy { it.isNetworkConnectionRequired }
            )
            .firstOrNull()
        val languageReady = (bestVoice != null && speaker.setVoice(bestVoice) == TextToSpeech.SUCCESS) ||
            speaker.setLanguage(locale) >= TextToSpeech.LANG_AVAILABLE
        if (!languageReady) return false
        speaker.setSpeechRate(0.95f)
        speaker.setPitch(1.0f)
        return speaker.speak(word.pronunciationText(), TextToSpeech.QUEUE_FLUSH, Bundle(), word.id) == TextToSpeech.SUCCESS
    }
}

private enum class AppScreen { HOME, STUDY, WORDS, TOPICS, TOPIC_WORDS, DUE_WORDS, QUIZ, QUIZ_RESULTS, STATS }

private fun Word.asTranslationSpeechWord(): Word = copy(
    original = translation,
    originalLanguageTag = translationLanguageTag,
    transcription = null
)

private data class QuizMistake(val word: Word, val answer: String)

@Composable
private fun VocabularyApp(
    themeMode: String,
    onThemeChange: (String) -> Unit,
    onAppLanguageChange: (String) -> Unit,
    onSpeakWord: (Word) -> Boolean
) {
    val appLanguage = LocalAppLanguage.current
    val uiStrings = LocalUiStrings.current
    fun t(source: String): String = uiStrings.text(appLanguage, source)
    fun tf(source: String, values: Map<String, String>): String = uiStrings.format(appLanguage, source, values)
    val context = androidx.compose.ui.platform.LocalContext.current
    fun speakWord(word: Word) {
        if (!onSpeakWord(word)) {
            Toast.makeText(context, t("Озвучивание недоступно"), Toast.LENGTH_SHORT).show()
        }
    }
    val storage = remember { WordStorage(context.applicationContext) }
    val statsStorage = remember { StudyStatsStorage(context.applicationContext) }
    val initialData = remember(storage) {
        storage.migrateDeckLanguages()
        storage.seedEnglish300IfNeeded()
        storage.seedRuEng300IfNeeded()
        storage.seedEnglish100NounsIfNeeded()
        storage.seedRuEng100NounsIfNeeded()
        storage.seedEnglish100VerbsIfNeeded()
        storage.seedRuEng100VerbsIfNeeded()
        storage.seedJapanese100IfNeeded()
        storage.load() to storage.loadTopics()
    }
    var words by remember { mutableStateOf(initialData.first) }
    var topics by remember { mutableStateOf(initialData.second) }
    var studyEvents by remember { mutableStateOf(statsStorage.load()) }
    var screen by rememberSaveable { mutableStateOf(AppScreen.HOME) }
    var currentWordId by rememberSaveable { mutableStateOf<String?>(null) }
    var answerShown by rememberSaveable { mutableStateOf(false) }
    var editor by remember { mutableStateOf<WordDraft?>(null) }
    var topicEditor by remember { mutableStateOf<TopicDraft?>(null) }
    var topicDeleteTarget by remember { mutableStateOf<Topic?>(null) }
    var topicRenameTarget by remember { mutableStateOf<TopicDraft?>(null) }
    var selectedTopicId by rememberSaveable { mutableStateOf<String?>(null) }
    var studyTopicId by rememberSaveable { mutableStateOf<String?>(null) }
    var isTopicStudy by rememberSaveable { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<Word?>(null) }
    var selectionMode by rememberSaveable { mutableStateOf(false) }
    var selectedWordIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var bulkDeleteTargets by remember { mutableStateOf<List<Word>?>(null) }
    var quizQuestions by rememberSaveable { mutableStateOf<List<String>>(emptyList()) }
    var quizIndex by rememberSaveable { mutableStateOf(0) }
    var quizOptions by rememberSaveable { mutableStateOf<List<String>>(emptyList()) }
    var quizOptionPool by rememberSaveable { mutableStateOf<List<String>>(emptyList()) }
    var quizSelectedOption by rememberSaveable { mutableStateOf<String?>(null) }
    var quizAnswerSubmitted by rememberSaveable { mutableStateOf(false) }
    var quizAnswerCorrect by rememberSaveable { mutableStateOf(false) }
    var quizScore by rememberSaveable { mutableStateOf(0) }
    var quizMistakeWordIds by rememberSaveable { mutableStateOf<List<String>>(emptyList()) }
    var quizMistakeAnswers by rememberSaveable { mutableStateOf<List<String>>(emptyList()) }
    var quizTitle by rememberSaveable { mutableStateOf("Все слова") }
    var quizReturnScreen by rememberSaveable { mutableStateOf(AppScreen.HOME) }
    var calendarYear by rememberSaveable { mutableStateOf(Calendar.getInstance().get(Calendar.YEAR)) }
    val quizMistakes = quizMistakeWordIds.mapIndexedNotNull { index, wordId ->
        words.firstOrNull { it.id == wordId }?.let { word ->
            QuizMistake(word, quizMistakeAnswers.getOrNull(index).orEmpty())
        }
    }
    var undoMessage by remember { mutableStateOf<String?>(null) }
    var undoAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var undoVersion by remember { mutableStateOf(0) }

    LaunchedEffect(undoVersion) {
        if (undoMessage != null) {
            val version = undoVersion
            delay(6000)
            if (undoVersion == version) {
                undoMessage = null
                undoAction = null
            }
        }
    }

    fun navigateBack() {
        if ((screen == AppScreen.WORDS || screen == AppScreen.TOPIC_WORDS) && selectionMode) {
            selectionMode = false
            selectedWordIds = emptySet()
        } else if (screen == AppScreen.QUIZ || screen == AppScreen.QUIZ_RESULTS) {
            screen = quizReturnScreen
        } else if (screen == AppScreen.STUDY && isTopicStudy) {
            screen = AppScreen.TOPICS
        } else if (screen == AppScreen.STATS) {
            screen = AppScreen.HOME
        } else if (screen == AppScreen.TOPIC_WORDS) {
            screen = AppScreen.TOPICS
        } else {
            screen = AppScreen.HOME
        }
    }

    BackHandler(enabled = screen != AppScreen.HOME || selectionMode) {
        navigateBack()
    }

    fun saveWords(updated: List<Word>) {
        check(updated.all { word ->
            val memberships = (word.topicIds + listOfNotNull(word.topicId)).distinct()
            memberships.isNotEmpty() && memberships.all { id -> topics.firstOrNull { it.id == id }?.let(word::matches) == true }
        }) { "Word languages must match deck languages" }
        words = updated
        storage.save(updated)
    }

    fun saveTopics(updated: List<Topic>) {
        check(updated.all { topic ->
            val previous = topics.firstOrNull { it.id == topic.id }
            previous == null ||
                (previous.originalLanguageTag == topic.originalLanguageTag &&
                    previous.translationLanguageTag == topic.translationLanguageTag)
        }) { "Deck languages cannot be changed after creation" }
        topics = updated
        storage.saveTopics(updated)
    }

    fun offerUndo(message: String, action: () -> Unit) {
        undoMessage = message
        undoAction = action
        undoVersion++
    }

    fun undoLastAction() {
        val action = undoAction
        undoMessage = null
        undoAction = null
        undoVersion++
        action?.invoke()
    }

    fun studyWords(): List<Word> = if (isTopicStudy) {
        words.filter { studyTopicId != null && it.belongsTo(studyTopicId!!) }
    } else {
        words
    }

    fun recordStudyAnswer(correct: Boolean, now: Long) {
        studyEvents = studyEvents + StudyEvent(now, correct)
        statsStorage.save(studyEvents)
    }

    fun selectNextStudyCard(updatedWords: List<Word>, topicId: String?, scopedToTopic: Boolean, now: Long): List<Word> {
        val limit = if (scopedToTopic) {
            topics.firstOrNull { it.id == topicId }?.newCardsPerDay ?: DEFAULT_NEW_CARDS_PER_DAY
        } else DEFAULT_NEW_CARDS_PER_DAY
        val topicLimits = topics.associate { it.id as String? to it.newCardsPerDay } + (null to DEFAULT_NEW_CARDS_PER_DAY)
        val nextId = nextStudyWordId(updatedWords, topicId, limit, now, scopedToTopic, topicLimits)
        val selected = updatedWords.firstOrNull { it.id == nextId }
        currentWordId = nextId
        return if (selected != null && isUnseen(selected)) {
            val introTopicId = introductionTopicIdFor(
                selected, updatedWords, topicId, scopedToTopic, topicLimits, now
            )
            updatedWords.map { word ->
                if (word.id == nextId) word.copy(
                    introducedAtMillis = now,
                    introducedInTopicId = introTopicId,
                    nextReviewAtMillis = now
                ) else word
            }
        } else updatedWords
    }

    fun answerStudyCard(word: Word, correct: Boolean) {
        val now = System.currentTimeMillis()
        val updated = words.map {
            if (it.id == word.id) scheduleStudyAnswer(it, correct = correct, nowMillis = now) else it
        }
        recordStudyAnswer(correct, now)
        saveWords(selectNextStudyCard(updated, studyTopicId, isTopicStudy, now))
        answerShown = false
    }

    fun openStudy(topicId: String? = null, fromTopic: Boolean = false) {
        studyTopicId = topicId
        isTopicStudy = fromTopic
        val now = System.currentTimeMillis()
        val updated = selectNextStudyCard(words, topicId, fromTopic, now)
        if (updated != words) saveWords(updated)
        answerShown = false
        screen = AppScreen.STUDY
    }

    fun prepareQuizOptions(questionId: String?) {
        val question = words.firstOrNull { it.id == questionId } ?: return
        val candidateWords = quizOptionPool.mapNotNull { wordId -> words.firstOrNull { it.id == wordId } }
        val pool = quizDistractorTranslations(question, candidateWords, words)
        quizOptions = buildQuizOptions(question.translation, pool)
        quizSelectedOption = null
    }

    fun startQuiz(
        questionPool: List<Word>,
        returnScreen: AppScreen,
        title: String,
        optionPool: List<Word> = questionPool
    ) {
        val uniqueQuestions = questionPool.distinctBy(::quizPromptKey)
        quizQuestions = uniqueQuestions
            .filter { question -> quizDistractorTranslations(question, optionPool, words).isNotEmpty() }
            .shuffled()
            .take(10)
            .map { it.id }
        quizIndex = 0
        quizOptionPool = optionPool.map { it.id }.distinct()
        quizAnswerSubmitted = false
        quizAnswerCorrect = false
        quizScore = 0
        quizMistakeWordIds = emptyList()
        quizMistakeAnswers = emptyList()
        quizTitle = title
        quizReturnScreen = returnScreen
        prepareQuizOptions(quizQuestions.firstOrNull())
        screen = AppScreen.QUIZ
    }

    fun submitQuizAnswer() {
        val questionId = quizQuestions.getOrNull(quizIndex) ?: return
        val question = words.firstOrNull { it.id == questionId } ?: return
        if (quizAnswerSubmitted) return
        val answer = quizSelectedOption ?: return
        if (answer.isBlank()) return

        val isCorrect = words
            .filter { sameQuizPrompt(it, question) }
            .any { isQuizAnswerCorrect(answer, it.translation) }
        quizAnswerCorrect = isCorrect
        quizAnswerSubmitted = true
        if (isCorrect) {
            quizScore++
        } else {
            quizMistakeWordIds = quizMistakeWordIds + question.id
            quizMistakeAnswers = quizMistakeAnswers + answer
        }
        saveWords(words.map { word ->
            if (word.id == question.id) {
                word.copy(
                    quizAttemptCount = word.quizAttemptCount + 1,
                    quizCorrectCount = word.quizCorrectCount + if (isCorrect) 1 else 0,
                    quizCorrectStreak = if (isCorrect) word.quizCorrectStreak + 1 else 0
                )
            } else word
        })
    }

    fun nextQuizQuestion() {
        if (quizIndex + 1 >= quizQuestions.size) {
            screen = AppScreen.QUIZ_RESULTS
        } else {
            quizIndex++
            quizAnswerSubmitted = false
            quizAnswerCorrect = false
            prepareQuizOptions(quizQuestions.getOrNull(quizIndex))
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            Column(Modifier.windowInsetsPadding(WindowInsets.navigationBars)) {
                undoMessage?.let { message ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                message,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            TextButton(onClick = { undoLastAction() }) { Text(t("Отменить")) }
                        }
                    }
                }
                if (screen == AppScreen.TOPICS) {
                    DeckActionButton(
                        label = "＋  ${t("Добавить колоду")}",
                        onClick = { topicEditor = TopicDraft() },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)
                    )
                }
                if (screen == AppScreen.DUE_WORDS) {
                    DeckActionButton(
                        label = t("Начать повторение"),
                        onClick = { openStudy() },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)
                    )
                }
            val wordsToStudy = studyWords()
            val currentWord = wordsToStudy.firstOrNull { it.id == currentWordId }
            if (screen == AppScreen.STUDY && answerShown && currentWord != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.background)
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = { answerStudyCard(currentWord, correct = false) },
                        modifier = Modifier.weight(1f).height(54.dp),
                        shape = RoundedCornerShape(18.dp),
                        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text(t("Заново"), fontSize = 16.sp)
                    }
                    Button(
                        onClick = { answerStudyCard(currentWord, correct = true) },
                        modifier = Modifier.weight(1f).height(54.dp),
                        shape = RoundedCornerShape(18.dp),
                        elevation = androidx.compose.material3.ButtonDefaults.buttonElevation(
                            defaultElevation = 3.dp,
                            pressedElevation = 1.dp
                        )
                    ) {
                        Text(t("Хорошо"), fontSize = 16.sp)
                    }
                }
            }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (screen != AppScreen.HOME) {
                Column(Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 12.dp, end = 24.dp, top = 8.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { navigateBack() }) {
                            Text("←", fontSize = 28.sp, lineHeight = 30.sp)
                        }
                        Text(
                            text = when (screen) {
                                AppScreen.STUDY -> if (isTopicStudy) {
                                    topics.firstOrNull { it.id == studyTopicId }?.name ?: t("Без колоды")
                                } else t("Учить")
                                AppScreen.QUIZ -> t(quizTitle)
                                AppScreen.QUIZ_RESULTS -> t("Результаты")
                                AppScreen.WORDS -> t("Слова")
                                AppScreen.TOPICS -> t("Колоды")
                                AppScreen.TOPIC_WORDS -> topics.firstOrNull { it.id == selectedTopicId }?.name ?: t("Без колоды")
                                AppScreen.DUE_WORDS -> t("Слова к повторению")
                                AppScreen.HOME -> t("Словарь")
                                AppScreen.STATS -> t("Статистика")
                            },
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            when (screen) {
                AppScreen.HOME -> HomeScreen(
                    wordCount = words.size,
                    deckCount = topics.size,
                    dueTodayCount = dueCardsCount(words, System.currentTimeMillis()),
                    newTodayCount = availableGlobalNewCardsCount(
                        words,
                        topics.associate { it.id as String? to it.newCardsPerDay } + (null to DEFAULT_NEW_CARDS_PER_DAY),
                        System.currentTimeMillis()
                    ),
                    onStudy = { openStudy() },
                    onShowDueWords = { screen = AppScreen.DUE_WORDS },
                    onDecks = { screen = AppScreen.TOPICS },
                    onStats = { calendarYear = Calendar.getInstance().get(Calendar.YEAR); screen = AppScreen.STATS },
                    onQuiz = { startQuiz(words, AppScreen.HOME, "Все слова") },
                    themeMode = themeMode,
                    onThemeChange = onThemeChange,
                    appLanguage = appLanguage,
                    onAppLanguageChange = onAppLanguageChange
                )
                AppScreen.STUDY -> StudyScreen(
                    words = studyWords(),
                    currentWordId = currentWordId,
                    answerShown = answerShown,
                    onSpeakWord = { speakWord(it) },
                    onShowAnswer = { answerShown = true },
                    onOpenWords = {
                        screen = if (isTopicStudy) AppScreen.TOPIC_WORDS else AppScreen.WORDS
                    }
                )
                AppScreen.DUE_WORDS -> DueWordsScreen(
                    words = dueCards(words, System.currentTimeMillis()),
                    topics = topics,
                    onSpeakWord = { speakWord(it) }
                )
                AppScreen.WORDS -> WordsScreen(
                    words = words,
                    selectionMode = selectionMode,
                    selectedWordIds = selectedWordIds,
                    onSpeakWord = { speakWord(it) },
                    onAdd = { editor = WordDraft() },
                    onEdit = {
                        editor = WordDraft(
                            id = it.id,
                            original = it.original,
                            translation = it.translation,
                            originalLanguageTag = it.originalLanguageTag,
                            translationLanguageTag = it.translationLanguageTag,
                            topicId = it.topicId,
                            topicIds = it.topicIds,
                            transcription = it.transcription.orEmpty(),
                            example = it.example.orEmpty(),
                            exampleTranslation = it.exampleTranslation.orEmpty()
                        )
                    },
                    onDelete = { deleteTarget = it },
                    onSelectionModeChange = { enabled ->
                        selectionMode = enabled
                        if (!enabled) selectedWordIds = emptySet()
                    },
                    onToggleSelection = { id ->
                        selectedWordIds = if (id in selectedWordIds) {
                            selectedWordIds - id
                        } else {
                            selectedWordIds + id
                        }
                    },
                    onSelectAll = { selectedWordIds = words.map { it.id }.toSet() },
                    onClearSelection = { selectedWordIds = emptySet() },
                    onDeleteSelected = {
                        bulkDeleteTargets = words.filter { it.id in selectedWordIds }
                    }
                )
                AppScreen.TOPICS -> TopicsScreen(
                    topics = topics,
                    words = words,
                    onOpenTopic = { topicId ->
                        selectedTopicId = topicId
                        screen = AppScreen.TOPIC_WORDS
                    },
                    onStudyTopic = { topicId ->
                        selectedTopicId = topicId
                        openStudy(topicId, fromTopic = true)
                    },
                    onQuizTopic = { topicId ->
                        val deckWords = words.filter { it.belongsTo(topicId.orEmpty()) }
                        val deckName = topics.firstOrNull { it.id == topicId }?.name ?: t("Без колоды")
                        startQuiz(deckWords, AppScreen.TOPICS, deckName)
                    },
                    onReorderTopics = { sourceId, targetId ->
                        val reordered = reorderTopics(topics, sourceId, targetId)
                        if (reordered !== topics) saveTopics(reordered)
                    },
                    onEditTopic = { topic ->
                        topicEditor = TopicDraft(
                            topic.id, topic.name, topic.originalLanguageTag,
                            topic.translationLanguageTag, topic.newCardsPerDay
                        )
                    },
                    onDeleteTopic = { topicDeleteTarget = it }
                )
                AppScreen.STATS -> StatisticsScreen(
                    words = words,
                    events = studyEvents,
                    year = calendarYear,
                    onPreviousYear = { calendarYear-- },
                    onNextYear = { calendarYear = minOf(Calendar.getInstance().get(Calendar.YEAR), calendarYear + 1) }
                )
                AppScreen.TOPIC_WORDS -> {
                    val topicWords = words.filter { selectedTopicId != null && it.belongsTo(selectedTopicId!!) }
                    WordsScreen(
                        words = topicWords,
                        selectionMode = selectionMode,
                        selectedWordIds = selectedWordIds,
                        onSpeakWord = { speakWord(it) },
                        onAdd = {
                            val topic = topics.firstOrNull { it.id == selectedTopicId }
                            editor = WordDraft(
                                topicId = selectedTopicId,
                                topicIds = listOfNotNull(selectedTopicId),
                                originalLanguageTag = topic?.originalLanguageTag ?: "en",
                                translationLanguageTag = topic?.translationLanguageTag ?: "ru"
                            )
                        },
                        onEdit = {
                            editor = WordDraft(
                                id = it.id,
                                original = it.original,
                                translation = it.translation,
                                originalLanguageTag = it.originalLanguageTag,
                                translationLanguageTag = it.translationLanguageTag,
                                topicId = it.topicId,
                                topicIds = it.topicIds,
                                transcription = it.transcription.orEmpty(),
                                example = it.example.orEmpty(),
                                exampleTranslation = it.exampleTranslation.orEmpty()
                            )
                        },
                        onDelete = { deleteTarget = it },
                        onSelectionModeChange = { enabled ->
                            selectionMode = enabled
                            if (!enabled) selectedWordIds = emptySet()
                        },
                        onToggleSelection = { id ->
                            selectedWordIds = if (id in selectedWordIds) selectedWordIds - id else selectedWordIds + id
                        },
                        onSelectAll = { selectedWordIds = topicWords.map { it.id }.toSet() },
                        onClearSelection = { selectedWordIds = emptySet() },
                        onDeleteSelected = { bulkDeleteTargets = topicWords.filter { it.id in selectedWordIds } }
                    )
                }
                AppScreen.QUIZ -> QuizScreen(
                    question = quizQuestions.getOrNull(quizIndex)?.let { id -> words.firstOrNull { it.id == id } },
                    hasWords = quizOptionPool.isNotEmpty(),
                    questionNumber = quizIndex + 1,
                    questionCount = quizQuestions.size,
                    title = quizTitle,
                    options = quizOptions,
                    selectedOption = quizSelectedOption,
                    answerSubmitted = quizAnswerSubmitted,
                    answerCorrect = quizAnswerCorrect,
                    onSpeakWord = { speakWord(it) },
                    onOptionSelect = { quizSelectedOption = it },
                    onSubmit = { submitQuizAnswer() },
                    onNext = { nextQuizQuestion() },
                    onDone = { screen = quizReturnScreen }
                )
                AppScreen.QUIZ_RESULTS -> QuizResultsScreen(
                    correctCount = quizScore,
                    questionCount = quizQuestions.size,
                    mistakes = quizMistakes,
                    onSpeakWord = { speakWord(it) },
                    onRetryMistakes = {
                        startQuiz(quizMistakes.map { it.word }, quizReturnScreen, "Повтор ошибок", words)
                    },
                    onDone = { screen = quizReturnScreen }
                )
            }
        }
    }

    editor?.let { draft ->
        WordEditorDialog(
            draft = draft,
            topics = topics,
            onDraftChange = { editor = it },
            onDismiss = { editor = null },
            onSave = { savedDraft ->
                val original = savedDraft.original.trim()
                val translation = savedDraft.translation.trim()
                val transcription = if (savedDraft.originalLanguageTag == "en") {
                    savedDraft.transcription.trim().takeIf { it.isNotBlank() }
                } else null
                val updated = if (draft.id == null) {
                    words + Word(
                        id = UUID.randomUUID().toString(),
                        original = original,
                        translation = translation,
                        originalLanguageTag = savedDraft.originalLanguageTag,
                        translationLanguageTag = savedDraft.translationLanguageTag,
                        topicId = savedDraft.topicIds.firstOrNull(),
                        topicIds = savedDraft.topicIds,
                        transcription = transcription,
                        example = savedDraft.example.trim().takeIf { it.isNotBlank() },
                        exampleTranslation = savedDraft.exampleTranslation.trim().takeIf { it.isNotBlank() }
                    )
                } else {
                    words.map {
                        if (it.id == draft.id) {
                            val contentChanged = it.original != original ||
                                it.translation != translation ||
                                it.originalLanguageTag != savedDraft.originalLanguageTag ||
                                it.translationLanguageTag != savedDraft.translationLanguageTag
                            val baseWord = if (contentChanged) it.resetStudyProgress() else it
                            val introDeck = baseWord.introducedInTopicId?.takeIf { id -> id in savedDraft.topicIds }
                                    ?: savedDraft.topicIds.firstOrNull()
                            baseWord.copy(
                                original = original,
                                translation = translation,
                                originalLanguageTag = savedDraft.originalLanguageTag,
                                translationLanguageTag = savedDraft.translationLanguageTag,
                                topicId = savedDraft.topicIds.firstOrNull(),
                                topicIds = savedDraft.topicIds,
                                introducedInTopicId = introDeck,
                                transcription = transcription,
                                example = savedDraft.example.trim().takeIf { it.isNotBlank() },
                                exampleTranslation = savedDraft.exampleTranslation.trim().takeIf { it.isNotBlank() },
                                quizAttemptCount = if (contentChanged) 0 else it.quizAttemptCount,
                                quizCorrectCount = if (contentChanged) 0 else it.quizCorrectCount,
                                quizCorrectStreak = if (contentChanged) 0 else it.quizCorrectStreak
                            )
                        }
                        else it
                    }
                }
                saveWords(updated)
                editor = null
            }
        )
    }

    deleteTarget?.let { word ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(t("Удалить слово?")) },
            confirmButton = {
                TextButton(onClick = {
                    saveWords(words.filterNot { it.id == word.id })
                    deleteTarget = null
                    offerUndo(t("Слово удалено")) { saveWords(words + word) }
                }) { Text(t("Удалить"), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text(t("Отмена")) }
            }
        )
    }

    bulkDeleteTargets?.let { targets ->
        AlertDialog(
            onDismissRequest = { bulkDeleteTargets = null },
            title = { Text(t("Удалить выбранные слова?")) },
            text = {
                Text(tf("Вы точно хотите удалить {count} {words}?", mapOf(
                    "count" to targets.size.toString(),
                    "words" to wordCountLabel(targets.size)
                )))
            },
            confirmButton = {
                TextButton(onClick = {
                    val idsToDelete = targets.map { it.id }.toSet()
                    saveWords(words.filterNot { it.id in idsToDelete })
                    bulkDeleteTargets = null
                    selectionMode = false
                    selectedWordIds = emptySet()
                    offerUndo(t("Слова удалены")) { saveWords(words + targets) }
                }) { Text(t("Удалить"), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { bulkDeleteTargets = null }) { Text(t("Отмена")) }
            }
        )
    }

    topicEditor?.let { draft ->
        TopicEditorDialog(
            draft = draft,
            onDraftChange = { topicEditor = it },
            onDismiss = { topicEditor = null },
            onSave = { savedDraft ->
                if (savedDraft.id == null) {
                    val name = savedDraft.name.trim()
                    val newTopic = Topic(
                        id = UUID.randomUUID().toString(),
                        name = name,
                        originalLanguageTag = savedDraft.originalLanguageTag,
                        translationLanguageTag = savedDraft.translationLanguageTag,
                        newCardsPerDay = savedDraft.newCardsPerDay.coerceAtLeast(0)
                    )
                    if (topics.isEmpty()) {
                        saveWords(words.map {
                            if (it.topicIds.isEmpty() && it.matches(newTopic)) it.copy(topicId = newTopic.id, topicIds = listOf(newTopic.id)) else it
                        })
                    }
                    saveTopics(topics + newTopic)
                    topicEditor = null
                } else {
                    val oldName = topics.firstOrNull { it.id == savedDraft.id }?.name
                    if (oldName == savedDraft.name.trim()) {
                        saveTopics(topics.map {
                            if (it.id == savedDraft.id) it.copy(newCardsPerDay = savedDraft.newCardsPerDay.coerceAtLeast(0)) else it
                        })
                        topicEditor = null
                    } else {
                        topicRenameTarget = savedDraft
                        topicEditor = null
                    }
                }
            }
        )
    }

    topicRenameTarget?.let { draft ->
        val oldName = topics.firstOrNull { it.id == draft.id }?.name ?: ""
        AlertDialog(
            onDismissRequest = {
                topicRenameTarget = null
                topicEditor = draft
            },
            title = { Text(t("Изменить имя колоды?")) },
            text = { Text(tf("Колода «{old}» будет переименована в «{new}».", mapOf(
                "old" to oldName,
                "new" to draft.name.trim()
            ))) },
            confirmButton = {
                TextButton(onClick = {
                    val name = draft.name.trim()
                    saveTopics(topics.map {
                        if (it.id == draft.id) {
                            it.copy(name = name, newCardsPerDay = draft.newCardsPerDay.coerceAtLeast(0))
                        } else it
                    })
                    topicRenameTarget = null
                }) { Text(t("Сохранить")) }
            },
            dismissButton = {
                TextButton(onClick = {
                    topicRenameTarget = null
                    topicEditor = draft
                }) { Text(t("Отмена")) }
            }
        )
    }

    topicDeleteTarget?.let { topic ->
        AlertDialog(
            onDismissRequest = { topicDeleteTarget = null },
            title = { Text(t("Удалить колоду?")) },
            text = {
                val onlyHere = words.count { word ->
                    word.belongsTo(topic.id) && word.topicIds.none { it != topic.id }
                }
                Text(tf("Слова, которые есть только в этой колоде, тоже будут удалены: {count}.", mapOf("count" to onlyHere.toString())))
            },
            confirmButton = {
                TextButton(onClick = {
                    val previousWords = words
                    val previousScreen = screen
                    val previousSelectedTopicId = selectedTopicId
                    saveTopics(topics.filterNot { it.id == topic.id })
                    saveWords(words.mapNotNull { word ->
                        if (!word.belongsTo(topic.id)) word else {
                            val memberships = word.topicIds.filterNot { it == topic.id }
                            if (memberships.isEmpty()) null else word.copy(
                                topicId = memberships.first(),
                                topicIds = memberships,
                                introducedInTopicId = word.introducedInTopicId?.takeIf { it in memberships } ?: memberships.first()
                            )
                        }
                    })
                    if (selectedTopicId == topic.id) {
                        selectedTopicId = null
                        screen = AppScreen.TOPICS
                    }
                    topicDeleteTarget = null
                    offerUndo(t("Колода удалена")) {
                        saveTopics(topics + topic)
                        saveWords(previousWords)
                        screen = previousScreen
                        selectedTopicId = previousSelectedTopicId
                    }
                }) { Text(t("Удалить"), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { topicDeleteTarget = null }) { Text(t("Отмена")) }
            }
        )
    }
}

@Composable
private fun wordCountLabel(count: Int): String {
    if (LocalAppLanguage.current != "ru") {
        return uiText(if (count == 1) "слово" else "слова")
    }
    return when {
        count % 100 in 11..14 -> uiText("слов")
        count % 10 == 1 -> uiText("слово")
        count % 10 in 2..4 -> uiText("слова")
        else -> uiText("слов")
    }
}

@Composable
private fun HomeScreen(
    wordCount: Int,
    deckCount: Int,
    dueTodayCount: Int,
    newTodayCount: Int,
    onStudy: () -> Unit,
    onShowDueWords: () -> Unit,
    onDecks: () -> Unit,
    onStats: () -> Unit,
    onQuiz: () -> Unit,
    themeMode: String,
    onThemeChange: (String) -> Unit,
    appLanguage: String,
    onAppLanguageChange: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                    )
                )
            )
            .padding(horizontal = 24.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppLanguageDropdown(
                selectedTag = appLanguage,
                onLanguageSelected = onAppLanguageChange
            )
            ThemeSelector(themeMode = themeMode, onThemeChange = onThemeChange)
        }
        Spacer(Modifier.height(14.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(32.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(Modifier.fillMaxWidth().padding(24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Text(
                            "Aa",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(uiText("Словарь"), style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(22.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    HomeStat(
                        value = wordCount.toString(),
                        label = uiText("СЛОВ"),
                        modifier = Modifier.weight(1f)
                    )
                    HomeStat(
                        value = deckCount.toString(),
                        label = uiText("КОЛОД"),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
        Spacer(Modifier.height(22.dp))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.primary,
            shadowElevation = 4.dp
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                TextButton(
                    onClick = onStudy,
                    modifier = Modifier.weight(1f).height(62.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp),
                    colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    HomeActionContent(
                        R.drawable.ic_study,
                        uiText("Учить слова"),
                        uiFormat("{due} повторить · {new} новых", mapOf(
                            "due" to dueTodayCount.toString(), "new" to newTodayCount.toString()
                        ))
                    )
                }
                if (dueTodayCount > 0) {
                    OutlinedButton(
                        onClick = onShowDueWords,
                        modifier = Modifier.height(44.dp),
                        shape = RoundedCornerShape(15.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.65f)),
                        contentPadding = PaddingValues(horizontal = 9.dp),
                        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Text(uiText("Повторить"), fontSize = 13.sp, maxLines = 1, softWrap = false)
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        FilledTonalButton(
            onClick = onDecks,
            modifier = Modifier.fillMaxWidth().height(58.dp),
            shape = RoundedCornerShape(20.dp)
        ) {
            HomeActionContent(R.drawable.ic_decks, uiText("Колоды"))
        }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(
            onClick = onQuiz,
            enabled = wordCount > 0,
            modifier = Modifier.fillMaxWidth().height(58.dp),
            shape = RoundedCornerShape(20.dp)
        ) {
            HomeActionContent(R.drawable.ic_quiz, uiText("Проверить знания"))
        }
        Spacer(Modifier.height(10.dp))
        FilledTonalButton(
            onClick = onStats,
            modifier = Modifier.fillMaxWidth().height(58.dp),
            shape = RoundedCornerShape(20.dp)
        ) {
            HomeActionContent(R.drawable.ic_stats, uiText("Статистика"))
        }
    }
}

@Composable
private fun DueWordsScreen(
    words: List<Word>,
    topics: List<Topic>,
    onSpeakWord: (Word) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        verticalArrangement = if (words.isEmpty()) Arrangement.Center else Arrangement.Top
    ) {
        if (words.isEmpty()) {
            EmptyMessage(uiText("На сегодня все!"), uiText("Сейчас нет карточек для повторения."))
        } else {
            Text(
                uiFormat("Эти карточки нужно повторить сегодня: {count}", mapOf("count" to words.size.toString())),
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            val todayStart = localDayStartMillis(System.currentTimeMillis())
            words.forEach { word ->
                val deckNames = topics.filter { word.belongsTo(it.id) }.joinToString(" · ") { it.name }
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                ) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(word.original, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            WordAudioButton(onClick = { onSpeakWord(word) })
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(word.translation, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            WordAudioButton(onClick = { onSpeakWord(word.asTranslationSpeechWord()) }, description = "Озвучить перевод")
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                deckNames,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                uiText(if (localDayStartMillis(word.nextReviewAtMillis) < todayStart) "Просрочено" else "Сегодня"),
                                modifier = Modifier.padding(start = 8.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun HomeActionContent(iconRes: Int, label: String, trailing: String? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .background(LocalContentColor.current.copy(alpha = 0.12f), RoundedCornerShape(13.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                modifier = Modifier.size(23.dp)
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                label,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            trailing?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun ThemeSelector(themeMode: String, onThemeChange: (String) -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ThemeChoice(uiText("Светлая"), selected = themeMode == "light", onClick = { onThemeChange("light") })
        ThemeChoice(uiText("Тёмная"), selected = themeMode == "dark", onClick = { onThemeChange("dark") })
        ThemeChoice(uiText("Система"), selected = themeMode == "system", onClick = { onThemeChange("system") })
    }
}

@Composable
private fun AppLanguageDropdown(selectedTag: String, onLanguageSelected: (String) -> Unit) {
    LanguagePicker(label = "", selectedTag = selectedTag, onLanguageSelected = onLanguageSelected)
}

@Composable
private fun ThemeChoice(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.height(36.dp),
        shape = RoundedCornerShape(12.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
            )
        }
    }
}

@Composable
private fun HomeStat(value: String, label: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                value,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 1.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun TopicsScreen(
    topics: List<Topic>,
    words: List<Word>,
    onOpenTopic: (String?) -> Unit,
    onStudyTopic: (String?) -> Unit,
    onQuizTopic: (String?) -> Unit,
    onReorderTopics: (String, String) -> Unit,
    onEditTopic: (Topic) -> Unit,
    onDeleteTopic: (Topic) -> Unit
) {
    val wordCountsByTopic = remember(words) {
        words.flatMap { word -> word.topicIds.distinct().map { it to word } }
            .groupingBy { it.first }.eachCount()
    }
    val unassignedCount = 0
    val cardBounds = remember { mutableMapOf<String, Pair<Float, Float>>() }
    val dragThresholdPx = with(LocalDensity.current) { 16.dp.toPx() }
    var draggingTopicId by remember { mutableStateOf<String?>(null) }
    var dragStartY by remember { mutableStateOf(0f) }
    var dragOffsetY by remember { mutableStateOf(0f) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        if (topics.isEmpty()) {
            EmptyMessage(
                title = uiText("Нет колод"),
                subtitle = if (unassignedCount > 0) {
                    uiText("Создайте колоду — существующие слова будут добавлены в неё.")
                } else {
                    uiText("Создайте колоду, чтобы добавлять и учить слова по отдельности.")
                }
            )
            Spacer(Modifier.height(20.dp))
        } else {
            if (topics.size > 1) Spacer(Modifier.height(4.dp))
        }
        val topicRows = topics.map { it as Topic? } +
            if (unassignedCount > 0 && topics.isNotEmpty()) listOf(null) else emptyList()
        topicRows.forEach { topic ->
            val topicId = topic?.id
            val count = wordCountsByTopic[topicId] ?: 0
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { coordinates ->
                        if (topicId != null) {
                            cardBounds[topicId] =
                                coordinates.positionInRoot().y to coordinates.size.height.toFloat()
                        }
                    }
                    .offset {
                        if (topicId != null && draggingTopicId == topicId) {
                            IntOffset(0, dragOffsetY.roundToInt())
                        } else {
                            IntOffset.Zero
                        }
                    }
                    .zIndex(if (topicId != null && draggingTopicId == topicId) 1f else 0f)
                    .padding(bottom = 14.dp),
                shape = RoundedCornerShape(26.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(Modifier.fillMaxWidth().padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(15.dp))
                                .size(44.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                (topic?.name ?: "—").take(1).uppercase(),
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                topic?.name ?: uiText("Без колоды"),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (topic != null) {
                                Text(
                                    "${languageLabel(topic.originalLanguageTag)} → " +
                                        languageLabel(topic.translationLanguageTag),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Text(
                                uiFormat("{count} {word}", mapOf(
                                    "count" to count.toString(),
                                    "word" to wordCountLabel(count)
                                )),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        if (topic != null) {
                            Spacer(Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .pointerInput(topic.id, topics) {
                                        detectDragGesturesAfterLongPress(
                                            onDragStart = {
                                                draggingTopicId = topic.id
                                                dragStartY = cardBounds[topic.id]?.first ?: 0f
                                                dragOffsetY = 0f
                                            },
                                            onDrag = { change, dragAmount ->
                                                change.consume()
                                                dragOffsetY += dragAmount.y
                                            },
                                            onDragEnd = {
                                                if (abs(dragOffsetY) >= dragThresholdPx) {
                                                    val draggedCenter = dragStartY + dragOffsetY +
                                                        (cardBounds[topic.id]?.second ?: 0f) / 2f
                                                    val targetTopic = topics.asSequence()
                                                        .filter { it.id != topic.id }
                                                        .minByOrNull { target ->
                                                            val bounds = cardBounds[target.id]
                                                            val targetCenter = bounds?.let { it.first + it.second / 2f }
                                                                ?: Float.MAX_VALUE
                                                            abs(draggedCenter - targetCenter)
                                                        }
                                                    if (targetTopic != null) {
                                                        onReorderTopics(topic.id, targetTopic.id)
                                                    }
                                                }
                                                draggingTopicId = null
                                                dragOffsetY = 0f
                                            },
                                            onDragCancel = {
                                                draggingTopicId = null
                                                dragOffsetY = 0f
                                            }
                                        )
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text("↕", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    val deckWords = words.filter { topicId != null && it.belongsTo(topicId) }
                    val dueCount = dueCardsCount(deckWords, System.currentTimeMillis())
                    val newCount = availableNewCardsCount(
                        deckWords,
                        topicId,
                        topic?.newCardsPerDay ?: DEFAULT_NEW_CARDS_PER_DAY,
                        System.currentTimeMillis()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                uiFormat("Повторить сегодня: {count}", mapOf("count" to dueCount.toString())),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center
                            )
                        }
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Text(
                                uiFormat("Новых сегодня: {count}", mapOf("count" to newCount.toString())),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        DeckActionButton(
                            label = uiText("Открыть колоду"),
                            onClick = { onOpenTopic(topicId) },
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                        DeckActionButton(
                            label = uiText("Учить слова"),
                            onClick = { onStudyTopic(topicId) },
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (topic != null) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                        ) {
                            DeckActionButton(
                                label = uiText("Настройки колоды"),
                                onClick = { onEditTopic(topic) },
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(Modifier.width(8.dp))
                            DeckActionButton(
                                label = uiText("Удалить колоду"),
                                onClick = { onDeleteTopic(topic) },
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    DeckActionButton(
                        label = uiText("Проверить знания"),
                        onClick = { onQuizTopic(topicId) },
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        enabled = count > 0
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun DeckActionButton(
    label: String,
    onClick: () -> Unit,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(48.dp),
        shape = RoundedCornerShape(15.dp),
        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        elevation = androidx.compose.material3.ButtonDefaults.buttonElevation(
            defaultElevation = 1.dp,
            pressedElevation = 0.dp
        ),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun StudyScreen(
    words: List<Word>,
    currentWordId: String?,
    answerShown: Boolean,
    onSpeakWord: (Word) -> Unit,
    onShowAnswer: () -> Unit,
    onOpenWords: () -> Unit
) {
    val appLanguage = LocalAppLanguage.current
    val currentWord = words.firstOrNull { it.id == currentWordId }
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        when {
            words.isEmpty() -> {
                EmptyMessage(
                    title = uiText("Пока нет слов"),
                    subtitle = uiText("Добавьте первое слово, чтобы начать учиться.")
                )
                Spacer(Modifier.height(20.dp))
                Button(onClick = onOpenWords, shape = RoundedCornerShape(16.dp)) {
                    Text(uiText("Добавить слово"))
                }
            }
            currentWord == null -> {
                val nextReview = words.filterNot(::isUnseen).minByOrNull { it.nextReviewAtMillis }?.nextReviewAtMillis
                EmptyMessage(
                    title = uiText("На сегодня все!"),
                    subtitle = nextReview?.let {
                        uiFormat(
                            "Следующее повторение {date}",
                            mapOf("date" to formatDate(it, appLanguage))
                        )
                    } ?: uiText("Новые слова скоро появятся здесь.")
                )
            }
            else -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(32.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 5.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            uiText("ВСПОМНИТЕ ПЕРЕВОД"),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.secondary,
                            letterSpacing = 1.3.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(20.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                currentWord.original,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center
                            )
                            currentWord.transcription?.let {
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    "[$it]",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                            WordAudioButton(onClick = { onSpeakWord(currentWord) })
                        }
                        if (answerShown) {
                            Spacer(Modifier.height(20.dp))
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    currentWord.translation,
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    textAlign = TextAlign.Center
                                )
                                WordAudioButton(onClick = { onSpeakWord(currentWord.asTranslationSpeechWord()) }, description = "Озвучить перевод")
                            }
                            currentWord.example?.takeIf { it.isNotBlank() }?.let { example ->
                                Text(
                                    example,
                                    modifier = Modifier.padding(top = 12.dp),
                                    style = MaterialTheme.typography.bodyLarge,
                                    textAlign = TextAlign.Center
                                )
                            }
                            currentWord.exampleTranslation?.takeIf { it.isNotBlank() }?.let { exampleTranslation ->
                                Text(
                                    exampleTranslation,
                                    modifier = Modifier.padding(top = 4.dp),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
                if (!answerShown) {
                    Spacer(Modifier.height(24.dp))
                    Button(
                        onClick = onShowAnswer,
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(18.dp),
                        elevation = androidx.compose.material3.ButtonDefaults.buttonElevation(
                            defaultElevation = 2.dp,
                            pressedElevation = 0.dp
                        )
                    ) { Text(uiText("Показать ответ"), fontSize = 16.sp) }
                }
            }
        }
    }
}

@Composable
private fun StatisticsScreen(
    words: List<Word>,
    events: List<StudyEvent>,
    year: Int,
    onPreviousYear: () -> Unit,
    onNextYear: () -> Unit
) {
    val appLanguage = LocalAppLanguage.current
    val today = System.currentTimeMillis()
    val todayStart = localDayStartMillis(today)
    val yearEvents = events.filter {
        Calendar.getInstance().apply { timeInMillis = it.timestampMillis }.get(Calendar.YEAR) == year
    }
    val dailyCounts = yearEvents.groupingBy { localDayStartMillis(it.timestampMillis) }.eachCount()
    val allDailyCounts = events.groupingBy { localDayStartMillis(it.timestampMillis) }.eachCount()
    val janFirst = Calendar.getInstance().apply {
        clear()
        set(year, Calendar.JANUARY, 1)
    }
    val firstDayOffset = (janFirst.get(Calendar.DAY_OF_WEEK) + 5) % 7
    val gridStart = janFirst.clone() as Calendar
    gridStart.add(Calendar.DAY_OF_YEAR, -firstDayOffset)
    val defaultSelectedDay = remember(year, allDailyCounts) {
        if (year == Calendar.getInstance().get(Calendar.YEAR)) todayStart
        else dailyCounts.keys.maxOrNull() ?: localDayStartMillis(janFirst.timeInMillis)
    }
    var selectedDay by rememberSaveable(year) { mutableStateOf(defaultSelectedDay) }
    val monthFormat = java.text.SimpleDateFormat("MMM", Locale.forLanguageTag(appLanguage))
    val activeDays = allDailyCounts.keys.filter { it <= todayStart }.sorted()
    var longestStreak = 0
    var streak = 0
    var previousDay: Long? = null
    activeDays.forEach { day ->
        streak = if (previousDay != null && day - previousDay!! in 82_800_000L..90_000_000L) streak + 1 else 1
        longestStreak = maxOf(longestStreak, streak)
        previousDay = day
    }
    var currentStreak = 0
    if (activeDays.isNotEmpty() && todayStart - activeDays.last() <= 90_000_000L) currentStreak = streak
    val yearCount = yearEvents.size
    val yearActiveDays = dailyCounts.keys.count { it <= todayStart }
    val averagePerActiveDay = if (yearActiveDays == 0) 0 else (yearCount + yearActiveDays / 2) / yearActiveDays
    val todayStats = dailyStudyStats(events, today)
    val selectedEvents = events.filter { localDayStartMillis(it.timestampMillis) == selectedDay }
    val selectedCorrect = selectedEvents.count { it.correct }
    val selectedRetention = if (selectedEvents.isEmpty()) 0 else selectedCorrect * 100 / selectedEvents.size
    val selectedDateFormat = java.text.SimpleDateFormat("EEEE, d MMMM yyyy", Locale.forLanguageTag(appLanguage))
    val unseen = words.count(::isUnseen)
    val mastered = words.count { !isUnseen(it) && it.intervalDays >= 21 }
    val learning = words.size - unseen - mastered
    val colors = listOf(Color(0xFF3B82F6), Color(0xFFA3E635), Color(0xFF166534))
    val slices = listOf(unseen, learning, mastered)
    val totalWords = words.size
    val chartTotal = totalWords.coerceAtLeast(1)
    val chartSize = if (LocalConfiguration.current.screenWidthDp < 380) 112.dp else 152.dp
    val calendarEmptyColor = MaterialTheme.colorScheme.surfaceVariant
    val calendarSelectionColor = MaterialTheme.colorScheme.primary
    val calendarDescription = uiText("Календарь повторений")

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatsMetric(
                        Modifier.weight(1f),
                        uiText("Сегодня изучено {count} карточек").replace("{count}", todayStats.answered.toString()),
                        uiText("Удержание: {percent}%").replace("{percent}", todayStats.retentionPercent.toString())
                    )
                    StatsMetric(
                        Modifier.weight(1f),
                        uiText("Текущая серия: {count} дн.").replace("{count}", currentStreak.toString()),
                        uiText("Самая длинная серия: {count} дн.").replace("{count}", longestStreak.toString())
                    )
                }
            }
        }
        Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(uiText("Календарь повторений"), modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    TextButton(onClick = onPreviousYear) { Text("‹") }
                    Text(year.toString(), fontWeight = FontWeight.Bold)
                    TextButton(onClick = onNextYear, enabled = year < Calendar.getInstance().get(Calendar.YEAR)) { Text("›") }
                }
                Row(Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth().padding(start = 20.dp, bottom = 4.dp)) {
                            (0..11).forEach { month ->
                                val date = Calendar.getInstance().apply { clear(); set(year, month, 1) }.time
                                Text(monthFormat.format(date), modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                            }
                        }
                        Canvas(
                            Modifier.fillMaxWidth().height(112.dp)
                                .pointerInput(year, gridStart.timeInMillis) {
                                    detectTapGestures { offset ->
                                        val gap = 3.dp.toPx()
                                        val cellWidth = (size.width - gap * 52) / 53f
                                        val cellHeight = (size.height - gap * 6) / 7f
                                        val week = (offset.x / (cellWidth + gap)).toInt()
                                        val dayOfWeek = (offset.y / (cellHeight + gap)).toInt()
                                        val insideCell = offset.x % (cellWidth + gap) <= cellWidth &&
                                            offset.y % (cellHeight + gap) <= cellHeight
                                        if (week in 0..52 && dayOfWeek in 0..6 && insideCell) {
                                            val date = gridStart.clone() as Calendar
                                            date.add(Calendar.DAY_OF_YEAR, week * 7 + dayOfWeek)
                                            if (date.get(Calendar.YEAR) == year) selectedDay = localDayStartMillis(date.timeInMillis)
                                        }
                                    }
                                }
                                .semantics { contentDescription = calendarDescription }
                        ) {
                            val gap = 3.dp.toPx()
                            val cellWidth = (size.width - gap * 52) / 53
                            val cellHeight = (size.height - gap * 6) / 7
                            for (index in 0 until 53 * 7) {
                                val week = index / 7
                                val dayOfWeek = index % 7
                                val date = gridStart.clone() as Calendar
                                date.add(Calendar.DAY_OF_YEAR, index)
                                val timestamp = localDayStartMillis(date.timeInMillis)
                                val isInYear = date.get(Calendar.YEAR) == year
                                val count = dailyCounts[timestamp] ?: 0
                                val futureDue = words.count { !isUnseen(it) && localDayStartMillis(it.nextReviewAtMillis) == timestamp }
                                val color = when {
                                    !isInYear -> calendarEmptyColor.copy(alpha = 0.2f)
                                    count == 0 && futureDue > 0 && timestamp > todayStart -> Color(0xFFCBD5E1)
                                    count == 0 -> calendarEmptyColor
                                    count < 3 -> Color(0xFF9BE9A8)
                                    count < 6 -> Color(0xFF40C463)
                                    count < 10 -> Color(0xFF30A14E)
                                    else -> Color(0xFF216E39)
                                }
                                drawRoundRect(
                                    color = color,
                                    topLeft = androidx.compose.ui.geometry.Offset(week * (cellWidth + gap), dayOfWeek * (cellHeight + gap)),
                                    size = androidx.compose.ui.geometry.Size(cellWidth, cellHeight),
                                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx())
                                )
                                if (timestamp == selectedDay) {
                                    drawRoundRect(
                                        color = calendarSelectionColor,
                                        topLeft = androidx.compose.ui.geometry.Offset(week * (cellWidth + gap), dayOfWeek * (cellHeight + gap)),
                                        size = androidx.compose.ui.geometry.Size(cellWidth, cellHeight),
                                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()),
                                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
                                    )
                                }
                            }
                        }
                    }
                }
                Column(Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(uiFormat("Повторов за год: {count}", mapOf("count" to yearCount.toString())), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    Text(uiFormat("Учебных дней: {count}", mapOf("count" to yearActiveDays.toString())), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(uiFormat("Среднее за активный день: {count}", mapOf("count" to averagePerActiveDay.toString())), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)) {
                    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(selectedDateFormat.format(java.util.Date(selectedDay)), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(uiFormat("Повторов за день: {count}", mapOf("count" to selectedEvents.size.toString())), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                            Text(uiFormat("Удержание: {percent}%", mapOf("percent" to selectedRetention.toString())), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.End)
                        }
                    }
                }
            }
        }
        Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Text(uiText("Карточки по состоянию"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Canvas(Modifier.size(chartSize)) {
                        val total = chartTotal
                        if (totalWords == 0) {
                            drawCircle(calendarEmptyColor)
                        } else {
                            var start = -90f
                            slices.forEachIndexed { index, value ->
                                val sweep = 360f * value / total
                                if (sweep > 0f) {
                                    drawArc(colors[index], start, sweep, useCenter = true)
                                    start += sweep
                                }
                            }
                        }
                    }
                    Column(Modifier.weight(1f).padding(start = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        listOf("Неизученные" to unseen, "Изучаемые" to learning, "Запомненные" to mastered).forEachIndexed { index, (label, count) ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(11.dp).clip(RoundedCornerShape(50)).background(colors[index]))
                                Text(uiText(label), modifier = Modifier.weight(1f).padding(start = 8.dp), style = MaterialTheme.typography.bodySmall)
                                Text("$count · ${count * 100 / chartTotal}%", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
                Text(
                    uiFormat("Всего карточек: {count}", mapOf("count" to totalWords.toString())),
                    modifier = Modifier.padding(top = 12.dp),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    uiText(if (totalWords == 0) "Добавьте слова, чтобы увидеть распределение карточек." else "Запомненной считается карточка с интервалом не менее 21 дня."),
                    modifier = Modifier.padding(top = 10.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun StatsMetric(modifier: Modifier, title: String, value: String) {
    Surface(modifier = modifier, shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun QuizScreen(
    question: Word?,
    hasWords: Boolean,
    questionNumber: Int,
    questionCount: Int,
    title: String,
    options: List<String>,
    selectedOption: String?,
    answerSubmitted: Boolean,
    answerCorrect: Boolean,
    onSpeakWord: (Word) -> Unit,
    onOptionSelect: (String) -> Unit,
    onSubmit: () -> Unit,
    onNext: () -> Unit,
    onDone: () -> Unit
) {
    if (question == null) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            EmptyMessage(
                uiText(if (hasWords) "Недостаточно вариантов" else "Нет слов для проверки"),
                uiText(if (hasWords) "Добавьте слово с другим переводом в эту языковую пару." else "Добавьте слова в словарь или выберите непустую колоду.")
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = onDone, shape = RoundedCornerShape(16.dp)) { Text(uiText("Готово")) }
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            uiText(title).uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            letterSpacing = 1.2.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            uiFormat("Вопрос {current} из {total}", mapOf(
                "current" to questionNumber.toString(),
                "total" to questionCount.toString()
            )),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(7.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(questionNumber.toFloat() / questionCount.coerceAtLeast(1))
                    .height(7.dp)
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
        Spacer(Modifier.height(22.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
                Text(
                    uiText("Выберите правильный перевод"),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(14.dp))
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        question.original,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center
                    )
                    question.transcription?.let {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "[$it]",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    WordAudioButton(onClick = { onSpeakWord(question) })
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            options.forEach { option ->
                val selected = option == selectedOption
                val correctOption = answerSubmitted && isQuizAnswerCorrect(option, question.translation)
                val wrongSelection = answerSubmitted && selected && !answerCorrect
                val optionColor = when {
                    correctOption -> MaterialTheme.colorScheme.primaryContainer
                    wrongSelection -> MaterialTheme.colorScheme.errorContainer
                    selected -> MaterialTheme.colorScheme.secondaryContainer
                    else -> MaterialTheme.colorScheme.surface
                }
                val optionContentColor = when {
                    correctOption -> MaterialTheme.colorScheme.onPrimaryContainer
                    wrongSelection -> MaterialTheme.colorScheme.onErrorContainer
                    selected -> MaterialTheme.colorScheme.onSecondaryContainer
                    else -> MaterialTheme.colorScheme.onSurface
                }
                Surface(
                    onClick = { onOptionSelect(option) },
                    enabled = !answerSubmitted,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = optionColor,
                    contentColor = optionContentColor,
                    border = BorderStroke(
                        1.dp,
                        when {
                            correctOption -> MaterialTheme.colorScheme.primary
                            wrongSelection -> MaterialTheme.colorScheme.error
                            selected -> MaterialTheme.colorScheme.secondary
                            else -> MaterialTheme.colorScheme.outline
                        }
                    )
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(option, modifier = Modifier.weight(1f))
                        if (selected || correctOption) {
                            Text("✓", fontWeight = FontWeight.Bold)
                        }
                        if (correctOption) {
                            WordAudioButton(onClick = { onSpeakWord(question.asTranslationSpeechWord()) }, description = "Озвучить перевод")
                        }
                    }
                }
            }
        }
        if (answerSubmitted) {
            if (!answerCorrect) {
                Spacer(Modifier.height(12.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Text(
                            uiFormat("Правильный ответ: {translation}", mapOf("translation" to question.translation)),
                            modifier = Modifier.padding(top = 4.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
            question.example?.takeIf { it.isNotBlank() }?.let { example ->
                Text(
                    example,
                    modifier = Modifier.padding(top = 12.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center
                )
            }
            question.exampleTranslation?.takeIf { it.isNotBlank() }?.let { exampleTranslation ->
                Text(
                    exampleTranslation,
                    modifier = Modifier.padding(top = 4.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
            Spacer(Modifier.height(14.dp))
            Button(
                onClick = onNext,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(18.dp)
            ) {
                Text(uiText(if (questionNumber == questionCount) "Показать результат" else "Следующее слово"))
            }
        } else {
            Spacer(Modifier.height(14.dp))
            Button(
                onClick = onSubmit,
                enabled = selectedOption != null,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(18.dp)
            ) { Text(uiText("Проверить ответ")) }
        }
    }
}

@Composable
private fun QuizResultsScreen(
    correctCount: Int,
    questionCount: Int,
    mistakes: List<QuizMistake>,
    onSpeakWord: (Word) -> Unit,
    onRetryMistakes: () -> Unit,
    onDone: () -> Unit
) {
    val accuracy = if (questionCount == 0) 0 else (correctCount * 100 / questionCount)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(uiText("Проверка завершена"), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text(
                    uiFormat("{correct} из {total}", mapOf(
                        "correct" to correctCount.toString(),
                        "total" to questionCount.toString()
                    )),
                    modifier = Modifier.padding(top = 8.dp),
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    uiFormat("Точность: {accuracy}%", mapOf("accuracy" to accuracy.toString())),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        if (mistakes.isEmpty()) {
            EmptyMessage(uiText("Все ответы верны!"), "")
        } else {
            Text(
                uiText("Нужно повторить"),
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            mistakes.forEach { mistake ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(mistake.word.original, modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                            WordAudioButton(onClick = { onSpeakWord(mistake.word) })
                        }
                        Text(
                            uiFormat("Ваш ответ: {answer}", mapOf("answer" to mistake.answer.ifBlank { "—" })),
                            modifier = Modifier.padding(top = 4.dp),
                            color = MaterialTheme.colorScheme.error
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                uiFormat("Перевод: {translation}", mapOf("translation" to mistake.word.translation)),
                                modifier = Modifier.weight(1f).padding(top = 2.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            WordAudioButton(onClick = { onSpeakWord(mistake.word.asTranslationSpeechWord()) }, description = "Озвучить перевод")
                        }
                    }
                }
            }
            Button(
                onClick = onRetryMistakes,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(18.dp)
            ) { Text(uiText("Проверить ошибки ещё раз")) }
            Spacer(Modifier.height(10.dp))
        }
        FilledTonalButton(
            onClick = onDone,
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(18.dp)
        ) { Text(uiText("Готово")) }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun WordsScreen(
    words: List<Word>,
    selectionMode: Boolean,
    selectedWordIds: Set<String>,
    onSpeakWord: (Word) -> Unit,
    onAdd: () -> Unit,
    onEdit: (Word) -> Unit,
    onDelete: (Word) -> Unit,
    onSelectionModeChange: (Boolean) -> Unit,
    onToggleSelection: (String) -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onDeleteSelected: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = if (words.isEmpty()) Arrangement.Center else Arrangement.Top
        ) {
            if (words.isEmpty()) {
                EmptyMessage(title = uiText("Слов нет"), subtitle = uiText("Ваш словарь пока пуст."))
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (selectionMode) {
                            uiFormat("Выбрано: {count}", mapOf("count" to selectedWordIds.size.toString()))
                        } else {
                            uiFormat("Всего слов: {count}", mapOf("count" to words.size.toString()))
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (selectionMode) {
                        TextButton(
                            onClick = {
                                if (selectedWordIds.size == words.size) onClearSelection() else onSelectAll()
                            }
                        ) {
                            Text(uiText(if (selectedWordIds.size == words.size) "Снять выбор" else "Выбрать все"))
                        }
                    } else {
                        TextButton(onClick = { onSelectionModeChange(true) }) { Text(uiText("Выбрать")) }
                    }
                }
                Spacer(Modifier.height(8.dp))
                words.forEach { word ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (selectionMode) Modifier.clickable { onToggleSelection(word.id) }
                                else Modifier
                            )
                            .padding(bottom = 12.dp),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (word.id in selectedWordIds) {
                                MaterialTheme.colorScheme.secondaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerLow
                            }
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column(Modifier.fillMaxWidth().padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(word.original, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                        word.transcription?.let {
                                            Spacer(Modifier.width(8.dp))
                                            Text(
                                                "[$it]",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        WordAudioButton(onClick = { onSpeakWord(word) })
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Row(Modifier.fillMaxWidth().padding(top = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            word.translation,
                                            modifier = Modifier.weight(1f),
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        WordAudioButton(onClick = { onSpeakWord(word.asTranslationSpeechWord()) }, description = "Озвучить перевод")
                                    }
                                    if (!word.example.isNullOrBlank() || !word.exampleTranslation.isNullOrBlank()) {
                                        Surface(
                                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                                            shape = RoundedCornerShape(14.dp),
                                            color = MaterialTheme.colorScheme.surfaceContainerHighest
                                        ) {
                                            Column(Modifier.fillMaxWidth().padding(12.dp)) {
                                                word.example?.takeIf { it.isNotBlank() }?.let { example ->
                                                    Text(
                                                        example,
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        fontWeight = FontWeight.Medium
                                                    )
                                                }
                                                word.exampleTranslation?.takeIf { it.isNotBlank() }?.let { exampleTranslation ->
                                                    Text(
                                                        exampleTranslation,
                                                        modifier = Modifier.padding(top = 3.dp),
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    if (word.quizCorrectStreak >= 3) {
                                        Text(
                                            uiText("Освоено ✓"),
                                            modifier = Modifier.padding(top = 5.dp),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold
                                        )
                                    } else if (word.quizAttemptCount > 0) {
                                        Text(
                                            uiFormat("В проверках: {correct}/{attempts} · подряд {streak}", mapOf(
                                                "correct" to word.quizCorrectCount.toString(),
                                                "attempts" to word.quizAttemptCount.toString(),
                                                "streak" to word.quizCorrectStreak.toString()
                                            )),
                                            modifier = Modifier.padding(top = 5.dp),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                if (selectionMode) {
                                    Checkbox(
                                        checked = word.id in selectedWordIds,
                                        onCheckedChange = { onToggleSelection(word.id) }
                                    )
                                }
                            }
                            if (!selectionMode) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    FilledTonalButton(
                                        onClick = { onEdit(word) },
                                        shape = RoundedCornerShape(14.dp)
                                    ) { Text(uiText("Изменить")) }
                                    Spacer(Modifier.width(4.dp))
                                    OutlinedButton(
                                        onClick = { onDelete(word) },
                                        shape = RoundedCornerShape(14.dp),
                                        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                                            contentColor = MaterialTheme.colorScheme.error
                                        )
                                    ) {
                                        Text(uiText("Удалить"), color = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.background,
            tonalElevation = 2.dp
        ) {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                if (!selectionMode) {
                    Button(
                        onClick = onAdd,
                        modifier = Modifier.fillMaxWidth().height(54.dp),
                        shape = RoundedCornerShape(18.dp),
                        elevation = androidx.compose.material3.ButtonDefaults.buttonElevation(
                            defaultElevation = 2.dp,
                            pressedElevation = 0.dp
                        )
                    ) { Text(uiText("Добавить слово"), fontSize = 16.sp) }
                }
                if (selectionMode) {
                    Button(
                        onClick = onDeleteSelected,
                        enabled = selectedWordIds.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth().height(54.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text(uiFormat("Удалить выбранные ({count})", mapOf("count" to selectedWordIds.size.toString())), fontSize = 16.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyMessage(title: String, subtitle: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        if (subtitle.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun WordAudioButton(onClick: () -> Unit, description: String = "Озвучить слово") {
    val description = uiText(description)
    IconButton(
        onClick = onClick,
        modifier = Modifier.semantics { contentDescription = description }
    ) {
        Text("🔊", fontSize = 20.sp)
    }
}

private data class WordDraft(
    val id: String? = null,
    val original: String = "",
    val translation: String = "",
    val originalLanguageTag: String = "en",
    val translationLanguageTag: String = "ru",
    val topicId: String? = null,
    val topicIds: List<String> = listOfNotNull(topicId),
    val transcription: String = "",
    val example: String = "",
    val exampleTranslation: String = ""
)

private data class TopicDraft(
    val id: String? = null,
    val name: String = "",
    val originalLanguageTag: String = "en",
    val translationLanguageTag: String = "ru",
    val newCardsPerDay: Int = DEFAULT_NEW_CARDS_PER_DAY
)

private data class InputLanguage(val tag: String, val label: String)

private val inputLanguages = listOf(
    InputLanguage("ru", "Русский"),
    InputLanguage("en", "English"),
    InputLanguage("es", "Español"),
    InputLanguage("fr", "Français"),
    InputLanguage("de", "Deutsch"),
    InputLanguage("ja", "日本語"),
    InputLanguage("zh", "中文")
)

private fun languageLabel(tag: String): String =
    inputLanguages.firstOrNull { it.tag == tag }?.label ?: tag

@Composable
private fun WordEditorDialog(
    draft: WordDraft,
    topics: List<Topic>,
    onDraftChange: (WordDraft) -> Unit,
    onDismiss: () -> Unit,
    onSave: (WordDraft) -> Unit
) {
    val selectedDecks = topics.filter { it.id in draft.topicIds }
    val deck = selectedDecks.firstOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(uiText(if (draft.id == null) "Новое слово" else "Редактировать слово")) },
        text = {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                if (deck == null) {
                    LanguagePicker(
                        label = uiText("Язык слова"),
                        selectedTag = draft.originalLanguageTag,
                        onLanguageSelected = { onDraftChange(draft.copy(originalLanguageTag = it, topicId = null, topicIds = emptyList())) }
                    )
                } else {
                    Text("${uiText("Язык слова")}: ${languageLabel(deck.originalLanguageTag)}")
                }
                OutlinedTextField(
                    value = draft.original,
                    onValueChange = { onDraftChange(draft.copy(original = it)) },
                    label = { Text(uiText("Слово")) },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        capitalization = KeyboardCapitalization.Sentences,
                        hintLocales = ComposeLocaleList(ComposeLocale(draft.originalLanguageTag))
                    ),
                    maxLines = 3,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                if (draft.originalLanguageTag == "en") {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = draft.transcription,
                        onValueChange = { onDraftChange(draft.copy(transcription = it)) },
                        label = { Text(uiText("Транскрипция (необязательно)")) },
                        placeholder = { Text(uiText("Например: həˈloʊ")) },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            capitalization = KeyboardCapitalization.None
                        ),
                        maxLines = 1,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(Modifier.height(8.dp))
                if (deck == null) {
                    LanguagePicker(
                        label = uiText("Язык перевода"),
                        selectedTag = draft.translationLanguageTag,
                        onLanguageSelected = { onDraftChange(draft.copy(translationLanguageTag = it, topicId = null, topicIds = emptyList())) }
                    )
                } else {
                    Text("${uiText("Язык перевода")}: ${languageLabel(deck.translationLanguageTag)}")
                }
                OutlinedTextField(
                    value = draft.translation,
                    onValueChange = { onDraftChange(draft.copy(translation = it)) },
                    label = { Text(uiText("Перевод")) },
                    placeholder = {
                        val placeholder = when (draft.translationLanguageTag) {
                            "ru" -> uiText("Например: привет")
                            "en" -> "For example: hello"
                            "es" -> "Por ejemplo: hola"
                            "fr" -> "Par exemple : bonjour"
                            "de" -> "Zum Beispiel: hallo"
                            else -> uiText("Перевод")
                        }
                        Text(uiText(placeholder))
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        capitalization = KeyboardCapitalization.Sentences,
                        hintLocales = ComposeLocaleList(ComposeLocale(draft.translationLanguageTag))
                    ),
                    maxLines = 3,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = draft.example,
                    onValueChange = { onDraftChange(draft.copy(example = it)) },
                    label = { Text(uiText("Пример употребления (необязательно)")) },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        capitalization = KeyboardCapitalization.Sentences,
                        hintLocales = ComposeLocaleList(ComposeLocale(draft.originalLanguageTag))
                    ),
                    maxLines = 2,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = draft.exampleTranslation,
                    onValueChange = { onDraftChange(draft.copy(exampleTranslation = it)) },
                    label = { Text(uiText("Перевод примера (необязательно)")) },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        capitalization = KeyboardCapitalization.Sentences,
                        hintLocales = ComposeLocaleList(ComposeLocale(draft.translationLanguageTag))
                    ),
                    maxLines = 2,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                TopicPicker(
                    topics = topics.filter {
                        it.originalLanguageTag == draft.originalLanguageTag &&
                            it.translationLanguageTag == draft.translationLanguageTag
                    },
                    selectedTopicIds = draft.topicIds,
                    onTopicToggle = { id ->
                        val selected = if (id in draft.topicIds) draft.topicIds - id else draft.topicIds + id
                        onDraftChange(draft.copy(topicId = selected.firstOrNull(), topicIds = selected))
                    }
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(draft) },
                enabled = draft.original.isNotBlank() && draft.translation.isNotBlank() &&
                    draft.topicIds.any { id -> topics.any { it.id == id } }
            ) { Text(uiText("Сохранить")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(uiText("Отмена")) } }
    )
}

@Composable
private fun TopicPicker(
    topics: List<Topic>,
    selectedTopicIds: List<String>,
    onTopicToggle: (String) -> Unit
) {
    Column(Modifier.fillMaxWidth()) {
        Text(uiText("Колоды (выберите одну или несколько)"), style = MaterialTheme.typography.labelLarge)
        if (topics.isEmpty()) Text(uiText("Сначала создайте колоду"), style = MaterialTheme.typography.bodySmall)
        topics.forEach { topic ->
            Row(
                modifier = Modifier.fillMaxWidth().clickable { onTopicToggle(topic.id) }.padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(checked = topic.id in selectedTopicIds, onCheckedChange = null)
                Text(topic.name)
            }
        }
    }
}

@Composable
private fun TopicEditorDialog(
    draft: TopicDraft,
    onDraftChange: (TopicDraft) -> Unit,
    onDismiss: () -> Unit,
    onSave: (TopicDraft) -> Unit
) {
    val isNew = draft.id == null
    Dialog(onDismissRequest = onDismiss) {
        val maxHeight = (LocalConfiguration.current.screenHeightDp.dp * 0.84f).coerceAtMost(680.dp)
        Surface(
            modifier = Modifier.fillMaxWidth().widthIn(max = 480.dp).heightIn(max = maxHeight),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(Modifier.fillMaxWidth().heightIn(max = maxHeight)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier.size(48.dp).clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            if (isNew) "＋" else draft.name.trim().firstOrNull()?.uppercase() ?: "—",
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                    Text(
                        uiText(if (isNew) "Новая колода" else "Настройки колоды"),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Column(
                    modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())
                        .padding(horizontal = 22.dp, vertical = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    OutlinedTextField(
                        value = draft.name,
                        onValueChange = { onDraftChange(draft.copy(name = it)) },
                        label = { if (!isNew) Text(uiText("Название колоды")) },
                        placeholder = { Text(uiText("Например, Путешествия")) },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            capitalization = KeyboardCapitalization.Sentences
                        ),
                        singleLine = !isNew,
                        maxLines = if (isNew) 2 else 1,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (isNew) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                LanguagePicker(
                                    label = uiText("Язык слова"),
                                    selectedTag = draft.originalLanguageTag,
                                    onLanguageSelected = { onDraftChange(draft.copy(originalLanguageTag = it)) }
                                )
                                LanguagePicker(
                                    label = uiText("Язык перевода"),
                                    selectedTag = draft.translationLanguageTag,
                                    onLanguageSelected = { onDraftChange(draft.copy(translationLanguageTag = it)) }
                                )
                            }
                        }
                    } else {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(uiText("Язык слова"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(languageLabel(draft.originalLanguageTag), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                }
                                Text("→", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                                Column(Modifier.weight(1f)) {
                                    Text(uiText("Язык перевода"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(languageLabel(draft.translationLanguageTag), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(uiText("Количество новых слов в день"), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                Text(uiText("Лимит применяется к новым словам этой колоды."), modifier = Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
                            }
                            OutlinedTextField(
                                value = draft.newCardsPerDay.toString(),
                                onValueChange = { value -> onDraftChange(draft.copy(newCardsPerDay = value.toIntOrNull()?.coerceAtLeast(0) ?: 0)) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.width(88.dp),
                                textStyle = MaterialTheme.typography.titleMedium.copy(textAlign = TextAlign.Center)
                            )
                        }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) {
                        Text(uiText("Отмена"))
                    }
                    Button(onClick = { onSave(draft) }, enabled = draft.name.isNotBlank(), modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) {
                        Text(uiText("Сохранить"))
                    }
                }
            }
        }
    }
}

@Composable
private fun LanguagePicker(
    label: String,
    selectedTag: String,
    onLanguageSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val selectedLanguage = inputLanguages.firstOrNull { it.tag == selectedTag } ?: inputLanguages.first()
    val matchingLanguages = inputLanguages.filter {
        it.label.contains(query.trim(), ignoreCase = true) || it.tag.contains(query.trim(), ignoreCase = true)
    }
    val maxDialogHeight = (LocalConfiguration.current.screenHeightDp.dp * 0.78f).coerceAtMost(600.dp)
    val compact = label.isBlank()

    Row(
        modifier = if (compact) Modifier.wrapContentWidth() else Modifier.fillMaxWidth(),
        horizontalArrangement = if (compact) Arrangement.Start else Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (!compact) Text(label, style = MaterialTheme.typography.labelLarge)
        Box {
            OutlinedButton(
                onClick = { query = ""; expanded = true },
                shape = RoundedCornerShape(14.dp),
                contentPadding = if (compact) PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                else PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    "${selectedLanguage.label}  ▾",
                    style = if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodyMedium,
                    maxLines = 1
                )
            }
            if (expanded) {
                Dialog(onDismissRequest = { expanded = false }) {
                    Surface(
                        modifier = Modifier.fillMaxWidth().widthIn(max = 440.dp).heightIn(max = maxDialogHeight),
                        shape = RoundedCornerShape(28.dp),
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 6.dp
                    ) {
                        Column(Modifier.fillMaxWidth().padding(20.dp)) {
                            Text(uiText("Выберите язык"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                            OutlinedTextField(
                                value = query,
                                onValueChange = { query = it },
                                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                                label = { Text(uiText("Поиск языка")) },
                                singleLine = true,
                                shape = RoundedCornerShape(16.dp)
                            )
                            Spacer(Modifier.height(8.dp))
                            if (matchingLanguages.isEmpty()) {
                                Text(
                                    uiText("Язык не найден"),
                                    modifier = Modifier.weight(1f).padding(vertical = 20.dp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            } else {
                                LazyColumn(
                                    modifier = Modifier.weight(1f).heightIn(min = 120.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    items(matchingLanguages, key = { it.tag }) { language ->
                                        val isSelected = language.tag == selectedTag
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(14.dp))
                                                .background(
                                                    if (isSelected) MaterialTheme.colorScheme.secondaryContainer
                                                    else MaterialTheme.colorScheme.surface
                                                )
                                                .clickable {
                                                    onLanguageSelected(language.tag)
                                                    expanded = false
                                                }
                                                .padding(horizontal = 14.dp, vertical = 12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(language.label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                                            if (isSelected) Text("✓", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                            TextButton(
                                onClick = { expanded = false },
                                modifier = Modifier.align(Alignment.End).padding(top = 4.dp)
                            ) { Text(uiText("Отмена")) }
                        }
                    }
                }
            }
        }
    }
}

private fun formatDate(timestamp: Long, language: String): String {
    val date = java.text.SimpleDateFormat("d MMMM", Locale.forLanguageTag(language))
    return date.format(timestamp)
}
