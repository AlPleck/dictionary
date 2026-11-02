package com.example.myapplication

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.Locale as ComposeLocale
import androidx.compose.ui.text.intl.LocaleList as ComposeLocaleList
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.ui.theme.MyApplicationTheme
import java.util.Calendar
import java.util.Locale
import java.util.UUID
import kotlin.math.min

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                VocabularyApp()
            }
        }
    }
}

private enum class AppScreen { HOME, STUDY, WORDS, TOPICS, TOPIC_WORDS }

@Composable
private fun VocabularyApp() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val storage = remember { WordStorage(context.applicationContext) }
    val initialData = remember(storage) {
        storage.seedEnglish300IfNeeded()
        storage.load() to storage.loadTopics()
    }
    var words by remember { mutableStateOf(initialData.first) }
    var topics by remember { mutableStateOf(initialData.second) }
    var screen by rememberSaveable { mutableStateOf(AppScreen.HOME) }
    var currentWordId by rememberSaveable { mutableStateOf<String?>(null) }
    var answerShown by rememberSaveable { mutableStateOf(false) }
    var editor by remember { mutableStateOf<WordDraft?>(null) }
    var topicEditor by remember { mutableStateOf<TopicDraft?>(null) }
    var topicDeleteTarget by remember { mutableStateOf<Topic?>(null) }
    var selectedTopicId by rememberSaveable { mutableStateOf<String?>(null) }
    var studyTopicId by rememberSaveable { mutableStateOf<String?>(null) }
    var isTopicStudy by rememberSaveable { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<Word?>(null) }
    var selectionMode by rememberSaveable { mutableStateOf(false) }
    var selectedWordIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var bulkDeleteTargets by remember { mutableStateOf<List<Word>?>(null) }

    BackHandler(enabled = screen != AppScreen.HOME || selectionMode) {
        if ((screen == AppScreen.WORDS || screen == AppScreen.TOPIC_WORDS) && selectionMode) {
            selectionMode = false
            selectedWordIds = emptySet()
        } else if (screen == AppScreen.TOPIC_WORDS) {
            screen = AppScreen.TOPICS
        } else {
            screen = AppScreen.HOME
        }
    }

    fun saveWords(updated: List<Word>) {
        words = updated
        storage.save(updated)
    }

    fun saveTopics(updated: List<Topic>) {
        topics = updated
        storage.saveTopics(updated)
    }

    fun studyWords(): List<Word> = if (isTopicStudy) {
        words.filter { it.topicId == studyTopicId }
    } else {
        words
    }

    fun openStudy(topicId: String? = null, fromTopic: Boolean = false) {
        studyTopicId = topicId
        isTopicStudy = fromTopic
        val wordsToStudy = if (fromTopic) words.filter { it.topicId == topicId } else words
        currentWordId = wordsToStudy
            .filter { it.nextReviewAtMillis <= System.currentTimeMillis() }
            .minByOrNull { it.nextReviewAtMillis }
            ?.id
        answerShown = false
        screen = AppScreen.STUDY
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
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
                        onClick = {
                            val now = System.currentTimeMillis()
                            val updated = words.map {
                                if (it.id == currentWord.id) {
                                    it.copy(successCount = 0, nextReviewAtMillis = now)
                                } else it
                            }
                            saveWords(updated)
                            currentWordId = updated
                                .filter { !isTopicStudy || it.topicId == studyTopicId }
                                .filter { it.nextReviewAtMillis <= now && it.id != currentWord.id }
                                .minByOrNull { it.nextReviewAtMillis }
                                ?.id ?: currentWord.id
                            answerShown = false
                        },
                        modifier = Modifier.weight(1f).height(54.dp),
                        shape = RoundedCornerShape(18.dp),
                        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text("Заново", fontSize = 16.sp)
                    }
                    Button(
                        onClick = {
                            val nextSuccessCount = currentWord.successCount + 1
                            val intervalDays = min(365, 1 shl (nextSuccessCount - 1).coerceAtMost(9))
                            val nextReview = Calendar.getInstance().apply {
                                add(Calendar.DAY_OF_YEAR, intervalDays)
                            }.timeInMillis
                            val updated = words.map {
                                if (it.id == currentWord.id) {
                                    it.copy(
                                        successCount = nextSuccessCount,
                                        nextReviewAtMillis = nextReview
                                    )
                                } else it
                            }
                            saveWords(updated)
                            currentWordId = updated
                                .filter { !isTopicStudy || it.topicId == studyTopicId }
                                .filter { it.nextReviewAtMillis <= System.currentTimeMillis() }
                                .minByOrNull { it.nextReviewAtMillis }
                                ?.id
                            answerShown = false
                        },
                        modifier = Modifier.weight(1f).height(54.dp),
                        shape = RoundedCornerShape(18.dp),
                        elevation = androidx.compose.material3.ButtonDefaults.buttonElevation(
                            defaultElevation = 3.dp,
                            pressedElevation = 1.dp
                        )
                    ) {
                        Text("Хорошо", fontSize = 16.sp)
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
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 24.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = {
                        if ((screen == AppScreen.WORDS || screen == AppScreen.TOPIC_WORDS) && selectionMode) {
                            selectionMode = false
                            selectedWordIds = emptySet()
                        } else if (screen == AppScreen.TOPIC_WORDS) {
                            screen = AppScreen.TOPICS
                        } else {
                            screen = AppScreen.HOME
                        }
                    }) {
                        Text("←", fontSize = 28.sp, lineHeight = 30.sp)
                    }
                    Text(
                        text = when (screen) {
                            AppScreen.STUDY -> if (isTopicStudy) {
                                topics.firstOrNull { it.id == studyTopicId }?.name ?: "Без колоды"
                            } else "Учить"
                            AppScreen.WORDS -> "Слова"
                            AppScreen.TOPICS -> "Колоды"
                            AppScreen.TOPIC_WORDS -> topics.firstOrNull { it.id == selectedTopicId }?.name ?: "Без колоды"
                            AppScreen.HOME -> "Словарь"
                        },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            when (screen) {
                AppScreen.HOME -> HomeScreen(
                    wordCount = words.size,
                    onStudy = { openStudy() },
                    onDecks = { screen = AppScreen.TOPICS }
                )
                AppScreen.STUDY -> StudyScreen(
                    words = studyWords(),
                    currentWordId = currentWordId,
                    answerShown = answerShown,
                    onShowAnswer = { answerShown = true },
                    onOpenWords = {
                        screen = if (isTopicStudy) AppScreen.TOPIC_WORDS else AppScreen.TOPICS
                    }
                )
                AppScreen.WORDS -> WordsScreen(
                    words = words,
                    selectionMode = selectionMode,
                    selectedWordIds = selectedWordIds,
                    onAdd = { editor = WordDraft() },
                    onEdit = {
                        editor = WordDraft(
                            id = it.id,
                            original = it.original,
                            translation = it.translation,
                            originalLanguageTag = it.originalLanguageTag,
                            translationLanguageTag = it.translationLanguageTag,
                            topicId = it.topicId
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
                    onAddTopic = { topicEditor = TopicDraft() },
                    onEditTopic = { topic ->
                        topicEditor = TopicDraft(topic.id, topic.name, topic.languageTag)
                    },
                    onDeleteTopic = { topicDeleteTarget = it }
                )
                AppScreen.TOPIC_WORDS -> {
                    val topicWords = words.filter { it.topicId == selectedTopicId }
                    WordsScreen(
                        words = topicWords,
                        selectionMode = selectionMode,
                        selectedWordIds = selectedWordIds,
                        onAdd = { editor = WordDraft(topicId = selectedTopicId) },
                        onEdit = {
                            editor = WordDraft(
                                id = it.id,
                                original = it.original,
                                translation = it.translation,
                                originalLanguageTag = it.originalLanguageTag,
                                translationLanguageTag = it.translationLanguageTag,
                                topicId = it.topicId
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
                val updated = if (draft.id == null) {
                    words + Word(
                        id = UUID.randomUUID().toString(),
                        original = original,
                        translation = translation,
                        originalLanguageTag = savedDraft.originalLanguageTag,
                        translationLanguageTag = savedDraft.translationLanguageTag,
                        topicId = savedDraft.topicId?.takeIf { id -> topics.any { it.id == id } }
                    )
                } else {
                    words.map {
                        if (it.id == draft.id) {
                            it.copy(
                                original = original,
                                translation = translation,
                                originalLanguageTag = savedDraft.originalLanguageTag,
                                translationLanguageTag = savedDraft.translationLanguageTag,
                                topicId = savedDraft.topicId?.takeIf { id -> topics.any { it.id == id } }
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
            title = { Text("Удалить слово?") },
            text = { Text("Вы точно хотите удалить «${word.original}»?") },
            confirmButton = {
                TextButton(onClick = {
                    saveWords(words.filterNot { it.id == word.id })
                    deleteTarget = null
                }) { Text("Удалить", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Отмена") }
            }
        )
    }

    bulkDeleteTargets?.let { targets ->
        AlertDialog(
            onDismissRequest = { bulkDeleteTargets = null },
            title = { Text("Удалить выбранные слова?") },
            text = { Text("Вы точно хотите удалить ${targets.size} ${wordCountLabel(targets.size)}?") },
            confirmButton = {
                TextButton(onClick = {
                    val idsToDelete = targets.map { it.id }.toSet()
                    saveWords(words.filterNot { it.id in idsToDelete })
                    bulkDeleteTargets = null
                    selectionMode = false
                    selectedWordIds = emptySet()
                }) { Text("Удалить", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { bulkDeleteTargets = null }) { Text("Отмена") }
            }
        )
    }

    topicEditor?.let { draft ->
        TopicEditorDialog(
            draft = draft,
            onDraftChange = { topicEditor = it },
            onDismiss = { topicEditor = null },
            onSave = { savedDraft ->
                val name = savedDraft.name.trim()
                val updated = if (savedDraft.id == null) {
                    val newTopic = Topic(
                        id = UUID.randomUUID().toString(),
                        name = name,
                        languageTag = savedDraft.languageTag
                    )
                    if (topics.isEmpty()) {
                        saveWords(words.map { if (it.topicId == null) it.copy(topicId = newTopic.id) else it })
                    }
                    topics + newTopic
                } else {
                    topics.map {
                        if (it.id == savedDraft.id) {
                            it.copy(name = name, languageTag = savedDraft.languageTag)
                        } else it
                    }
                }
                saveTopics(updated)
                topicEditor = null
            }
        )
    }

    topicDeleteTarget?.let { topic ->
        val topicWordCount = words.count { it.topicId == topic.id }
        AlertDialog(
            onDismissRequest = { topicDeleteTarget = null },
            title = { Text("Удалить колоду?") },
            text = {
                Text("Колода «${topic.name}» и все слова внутри неё ($topicWordCount ${wordCountLabel(topicWordCount)}) будут удалены.")
            },
            confirmButton = {
                TextButton(onClick = {
                    saveTopics(topics.filterNot { it.id == topic.id })
                    saveWords(words.filterNot { it.topicId == topic.id })
                    if (selectedTopicId == topic.id) {
                        selectedTopicId = null
                        screen = AppScreen.TOPICS
                    }
                    topicDeleteTarget = null
                }) { Text("Удалить", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { topicDeleteTarget = null }) { Text("Отмена") }
            }
        )
    }
}

private fun wordCountLabel(count: Int): String = when {
    count % 100 in 11..14 -> "слов"
    count % 10 == 1 -> "слово"
    count % 10 in 2..4 -> "слова"
    else -> "слов"
}

@Composable
private fun HomeScreen(wordCount: Int, onStudy: () -> Unit, onDecks: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Словарь", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            text = if (wordCount == 0) "Добавьте слова и учите их в своём темпе" else "$wordCount слов в вашем словаре",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(36.dp))
        Button(
            onClick = onStudy,
            modifier = Modifier.fillMaxWidth().height(58.dp),
            shape = RoundedCornerShape(20.dp),
            elevation = androidx.compose.material3.ButtonDefaults.buttonElevation(
                defaultElevation = 3.dp,
                pressedElevation = 1.dp
            )
        ) { Text("Учить", fontSize = 18.sp) }
        Spacer(Modifier.height(14.dp))
        FilledTonalButton(
            onClick = onDecks,
            modifier = Modifier.fillMaxWidth().height(58.dp),
            shape = RoundedCornerShape(20.dp)
        ) { Text("Колоды", fontSize = 18.sp) }
    }
}

@Composable
private fun TopicsScreen(
    topics: List<Topic>,
    words: List<Word>,
    onOpenTopic: (String?) -> Unit,
    onStudyTopic: (String?) -> Unit,
    onAddTopic: () -> Unit,
    onEditTopic: (Topic) -> Unit,
    onDeleteTopic: (Topic) -> Unit
) {
    val unassignedCount = words.count { it.topicId == null }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        if (topics.isEmpty()) {
            EmptyMessage(
                title = "Нет колод",
                subtitle = if (unassignedCount > 0) {
                    "Создайте колоду — существующие слова будут добавлены в неё."
                } else {
                    "Создайте колоду, чтобы добавлять и учить слова по отдельности."
                }
            )
            Spacer(Modifier.height(20.dp))
        } else {
            Text(
                "Откройте колоду, чтобы управлять её словами.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }
        val topicRows = topics.map { it as Topic? } +
            if (unassignedCount > 0 && topics.isNotEmpty()) listOf(null) else emptyList()
        topicRows.forEach { topic ->
            val topicId = topic?.id
            val count = words.count { it.topicId == topicId }
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(
                        topic?.name ?: "Без колоды",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "$count ${wordCountLabel(count)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        DeckActionButton(
                            label = "Открыть",
                            onClick = { onOpenTopic(topicId) },
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                        DeckActionButton(
                            label = "Учить",
                            onClick = { onStudyTopic(topicId) },
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (topic != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            DeckActionButton(
                                label = "Изменить",
                                onClick = { onEditTopic(topic) },
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(Modifier.width(8.dp))
                            DeckActionButton(
                                label = "Удалить",
                                onClick = { onDeleteTopic(topic) },
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
        DeckActionButton(
            label = "＋  Добавить колоду",
            onClick = onAddTopic,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun DeckActionButton(
    label: String,
    onClick: () -> Unit,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
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
    onShowAnswer: () -> Unit,
    onOpenWords: () -> Unit
) {
    val currentWord = words.firstOrNull { it.id == currentWordId }
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        when {
            words.isEmpty() -> {
                EmptyMessage(
                    title = "Пока нет слов",
                    subtitle = "Добавьте первое слово, чтобы начать учиться."
                )
                Spacer(Modifier.height(20.dp))
                Button(onClick = onOpenWords, shape = RoundedCornerShape(16.dp)) {
                    Text("Добавить слово")
                }
            }
            currentWord == null -> {
                val nextReview = words.minByOrNull { it.nextReviewAtMillis }?.nextReviewAtMillis
                EmptyMessage(
                    title = "На сегодня всё",
                    subtitle = nextReview?.let { "Следующее повторение ${formatDate(it)}" }
                        ?: "Новые слова скоро появятся здесь."
                )
            }
            else -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 44.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            currentWord.original,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center
                        )
                        if (answerShown) {
                            Spacer(Modifier.height(20.dp))
                            Text(
                                currentWord.translation,
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.primary,
                                textAlign = TextAlign.Center
                            )
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
                    ) { Text("Показать ответ", fontSize = 16.sp) }
                }
            }
        }
    }
}

@Composable
private fun WordsScreen(
    words: List<Word>,
    selectionMode: Boolean,
    selectedWordIds: Set<String>,
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
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        verticalArrangement = if (words.isEmpty()) Arrangement.Center else Arrangement.Top
    ) {
        if (words.isEmpty()) {
            EmptyMessage(title = "Слов нет", subtitle = "Ваш словарь пока пуст.")
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (selectionMode) "Выбрано: ${selectedWordIds.size}" else "Всего слов: ${words.size}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (selectionMode) {
                    TextButton(
                        onClick = {
                            if (selectedWordIds.size == words.size) onClearSelection() else onSelectAll()
                        }
                    ) {
                        Text(if (selectedWordIds.size == words.size) "Снять выбор" else "Выбрать все")
                    }
                } else {
                    TextButton(onClick = { onSelectionModeChange(true) }) { Text("Выбрать") }
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
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (word.id in selectedWordIds) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        }
                    )
                ) {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(word.original, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.height(4.dp))
                                Text(word.translation, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                                ) { Text("Изменить") }
                                Spacer(Modifier.width(4.dp))
                                OutlinedButton(
                                    onClick = { onDelete(word) },
                                    shape = RoundedCornerShape(14.dp),
                                    colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                                        contentColor = MaterialTheme.colorScheme.error
                                    )
                                ) {
                                    Text("Удалить", color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        if (selectionMode) {
            Button(
                onClick = onDeleteSelected,
                enabled = selectedWordIds.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(16.dp),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) { Text("Удалить выбранные (${selectedWordIds.size})", fontSize = 16.sp) }
        } else {
            Button(
                onClick = onAdd,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(18.dp),
                elevation = androidx.compose.material3.ButtonDefaults.buttonElevation(
                    defaultElevation = 2.dp,
                    pressedElevation = 0.dp
                )
            ) { Text("Добавить слово", fontSize = 16.sp) }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun EmptyMessage(title: String, subtitle: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

private data class WordDraft(
    val id: String? = null,
    val original: String = "",
    val translation: String = "",
    val originalLanguageTag: String = "en",
    val translationLanguageTag: String = "ru",
    val topicId: String? = null
)

private data class TopicDraft(
    val id: String? = null,
    val name: String = "",
    val languageTag: String = "ru"
)

private data class InputLanguage(val tag: String, val label: String)

private val inputLanguages = listOf(
    InputLanguage("ru", "Русский"),
    InputLanguage("en", "English"),
    InputLanguage("es", "Español"),
    InputLanguage("fr", "Français"),
    InputLanguage("de", "Deutsch")
)

@Composable
private fun WordEditorDialog(
    draft: WordDraft,
    topics: List<Topic>,
    onDraftChange: (WordDraft) -> Unit,
    onDismiss: () -> Unit,
    onSave: (WordDraft) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (draft.id == null) "Новое слово" else "Редактировать слово") },
        text = {
            Column {
                LanguagePicker(
                    label = "Язык слова",
                    selectedTag = draft.originalLanguageTag,
                    onLanguageSelected = { onDraftChange(draft.copy(originalLanguageTag = it)) }
                )
                OutlinedTextField(
                    value = draft.original,
                    onValueChange = { onDraftChange(draft.copy(original = it)) },
                    label = { Text("Слово") },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        capitalization = KeyboardCapitalization.Sentences,
                        hintLocales = ComposeLocaleList(ComposeLocale(draft.originalLanguageTag))
                    ),
                    maxLines = 3,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                LanguagePicker(
                    label = "Язык перевода",
                    selectedTag = draft.translationLanguageTag,
                    onLanguageSelected = { onDraftChange(draft.copy(translationLanguageTag = it)) }
                )
                OutlinedTextField(
                    value = draft.translation,
                    onValueChange = { onDraftChange(draft.copy(translation = it)) },
                    label = { Text("Перевод") },
                    placeholder = {
                        Text(
                            when (draft.translationLanguageTag) {
                                "ru" -> "Например: привет"
                                "en" -> "For example: hello"
                                "es" -> "Por ejemplo: hola"
                                "fr" -> "Par exemple : bonjour"
                                "de" -> "Zum Beispiel: hallo"
                                else -> "Перевод"
                            }
                        )
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
                TopicPicker(
                    topics = topics,
                    selectedTopicId = draft.topicId,
                    onTopicSelected = { onDraftChange(draft.copy(topicId = it)) }
                )
                Text(
                    text = "Можно вводить любые слова и фразы. Выбранный язык только подсказывает раскладку клавиатуре.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(draft) },
                enabled = draft.original.isNotBlank() && draft.translation.isNotBlank()
            ) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

@Composable
private fun TopicPicker(
    topics: List<Topic>,
    selectedTopicId: String?,
    onTopicSelected: (String?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedName = topics.firstOrNull { it.id == selectedTopicId }?.name ?: "Без колоды"

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Колода", style = MaterialTheme.typography.labelLarge)
        Box {
            OutlinedButton(
                onClick = { expanded = true },
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) { Text("$selectedName  ▾") }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(
                    text = { Text("Без колоды") },
                    onClick = {
                        onTopicSelected(null)
                        expanded = false
                    }
                )
                topics.forEach { topic ->
                    DropdownMenuItem(
                        text = { Text(topic.name) },
                        onClick = {
                            onTopicSelected(topic.id)
                            expanded = false
                        }
                    )
                }
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
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (draft.id == null) "Новая колода" else "Переименовать колоду") },
        text = {
            Column {
                LanguagePicker(
                    label = "Язык названия",
                    selectedTag = draft.languageTag,
                    onLanguageSelected = { onDraftChange(draft.copy(languageTag = it)) }
                )
                OutlinedTextField(
                    value = draft.name,
                    onValueChange = { onDraftChange(draft.copy(name = it)) },
                    label = { Text("Название колоды") },
                    placeholder = { Text("Например, Путешествия") },
                    supportingText = { Text("Можно написать на любом языке") },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        capitalization = KeyboardCapitalization.Sentences,
                        hintLocales = ComposeLocaleList(ComposeLocale(draft.languageTag))
                    ),
                    maxLines = 2,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(draft) }, enabled = draft.name.isNotBlank()) {
                Text("Сохранить")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

@Composable
private fun LanguagePicker(
    label: String,
    selectedTag: String,
    onLanguageSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLanguage = inputLanguages.firstOrNull { it.tag == selectedTag } ?: inputLanguages.first()

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Box {
            OutlinedButton(
                onClick = { expanded = true },
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text("${selectedLanguage.label}  ▾")
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                inputLanguages.forEach { language ->
                    DropdownMenuItem(
                        text = { Text(language.label) },
                        onClick = {
                            onLanguageSelected(language.tag)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

private fun formatDate(timestamp: Long): String {
    val date = java.text.SimpleDateFormat("d MMMM", Locale.forLanguageTag("ru"))
    return date.format(timestamp)
}
