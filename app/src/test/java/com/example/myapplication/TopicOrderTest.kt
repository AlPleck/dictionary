package com.example.myapplication

import org.junit.Assert.assertEquals
import org.junit.Test

class TopicOrderTest {
    @Test
    fun movesTopicUpAndDownToTheTargetIndex() {
        val topics = listOf("a", "b", "c", "d").map { Topic(it, it) }

        assertEquals(listOf("b", "c", "a", "d"), reorderTopics(topics, "a", "c").map { it.id })
        assertEquals(listOf("a", "d", "b", "c"), reorderTopics(topics, "d", "b").map { it.id })
    }

    @Test
    fun keepsOrderWhenEitherTopicIsMissingOrTheTargetIsTheSource() {
        val topics = listOf(Topic("a", "a"), Topic("b", "b"))

        assertEquals(topics, reorderTopics(topics, "a", "missing"))
        assertEquals(topics, reorderTopics(topics, "missing", "b"))
        assertEquals(topics, reorderTopics(topics, "a", "a"))
    }
}
