package com.example.encryptedmessenger

import java.security.Signature

data class EncryptedMessageData(
    val encryptedMessage: String,
    val encryptedAesKey: String,
)