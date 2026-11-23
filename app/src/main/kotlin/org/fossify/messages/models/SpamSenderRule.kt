package org.fossify.messages.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "spam_sender_rules")
data class SpamSenderRule(
    @PrimaryKey
    @ColumnInfo(name = "address")
    val address: String,
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)
