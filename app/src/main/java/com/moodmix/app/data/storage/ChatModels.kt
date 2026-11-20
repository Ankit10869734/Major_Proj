package com.moodmix.app.data.storage

import java.util.UUID

enum class MessageStatus { DONE, GENERATING, ERROR, CANCELLED }

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val isUser: Boolean,
    val text: String,
    val durationSeconds: Int = 0,
    val audioFileName: String? = null,
    val exportedUri: String? = null,
    val status: MessageStatus = MessageStatus.DONE,
    val errorText: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

data class ChatSession(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "New chat",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val messages: List<ChatMessage> = emptyList()
)