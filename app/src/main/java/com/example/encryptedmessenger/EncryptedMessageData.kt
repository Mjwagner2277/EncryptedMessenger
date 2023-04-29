package com.example.encryptedmessenger

data class EncryptedMessageData(
    val encryptedMessage: String,
    val encryptedAesKey: String
)