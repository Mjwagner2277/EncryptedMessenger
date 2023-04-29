package com.example.encryptedmessenger

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import java.security.PublicKey
import java.security.KeyFactory
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import android.util.Base64
import android.view.KeyEvent
import android.view.View
import java.security.KeyStore
import java.security.PrivateKey
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class ChatLog : AppCompatActivity() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var chatList: ArrayList<ChatMessage>
    private lateinit var adapter: ChatAdapter

    /**
     * Initializes the activity and sets up the UI elements and listeners.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat_log)

        // Find UI elements by ID
        val btnSend = findViewById<Button>(R.id.btn_send)
        val editTextInput = findViewById<EditText>(R.id.editTextInput)

        // Extract username and recipient ID from intent extras
        val recipientUserName = intent.getStringExtra("recipientUsername")
        val recipientId = intent.getStringExtra("uid")

        // Set the activity title to the username of the chat partner
        supportActionBar?.title = recipientUserName

        // Set up the RecyclerView and adapter
        setUpRecyclerView()

        // Fetch and decrypt messages from the Firestore database
        fetchAndDecryptMessages(recipientId.toString())

        // Set up a click listener for the send button
        btnSend.setOnClickListener {
            // Encrypt and send the user's message
            performSymmetricSendMessage(editTextInput.text.toString(), recipientId.toString())

            // Clear the input field after sending the message
            editTextInput.setText("")
            hideKeyboard(editTextInput)
        }

        // Listen for Enter key press to send the message
        editTextInput.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_ENTER) {
                // Encrypt and send the user's message
                performSymmetricSendMessage(editTextInput.text.toString(), recipientId.toString())

                // Clear the input field after sending the message
                editTextInput.setText("")
                hideKeyboard(editTextInput)

                return@setOnKeyListener true
            }
            false
        }
    }

    /**
     * Encrypts a user's message using AES-GCM with a new key and IV,
     * and stores the encrypted message, along with its key, IV, recipient ID, and timestamp,
     * in the Firestore database under the current user's collection of sent messages.
     *
     * @param message The plain text message to be encrypted and stored.
     * @param recipientId The ID of the recipient user.
     */
    private fun storeUserMessages(message: String, recipientId: String) {
        // Encrypt the user's message using AES-GCM and generate a new key and IV
        val (encryptedMessage, encryptedAesKey, iv) = encryptUsermessages(message)

        // Get an instance of the Firestore database and the current user's ID
        val db = FirebaseFirestore.getInstance()
        val currentUserId = getCurrentUserId()

        // Create a HashMap containing the encrypted message, key, IV, recipient ID,
        // and timestamp of the message
        val messageData = hashMapOf(
            "toID" to recipientId,
            "encryptedMessage" to encryptedMessage,
            "encryptedAesKey" to encryptedAesKey,
            "iv" to iv,
            "timestamp" to FieldValue.serverTimestamp()
        )

        // Store the encrypted message, key, IV, recipient ID, and timestamp in the
        // Firestore database under the current user's collection of sent messages
        db.collection("UserSentMessages")
            .document(currentUserId!!)
            .collection("SentMessages")
            .add(messageData)
            .addOnSuccessListener {
                Log.d("com.example.encryptedmessenger.ChatLog", "Message sent and stored")
            }
            .addOnFailureListener { e ->
                Log.w("com.example.encryptedmessenger.ChatLog", "Error sending message", e)
            }
    }

    /**
     * Encrypts a plaintext message using AES-GCM encryption and returns the encrypted message,
     * key, and initialization vector (IV) in the form of a Triple of Base64-encoded strings.
     *
     * @param message The plaintext message to be encrypted.
     * @return A Triple of Base64-encoded strings containing the encrypted message, key, and IV.
     */
    private fun encryptUsermessages(message: String): Triple<String, String, String> {
        // Generate a new AES key
        val keyGenerator = KeyGenerator.getInstance("AES")
        keyGenerator.init(256)
        val secretKey = keyGenerator.generateKey()

        // Initialize a cipher with the new key and a random IV
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val iv = ByteArray(12)
        SecureRandom().nextBytes(iv) // Generate a random IV
        val gcmSpec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec)

        // Encrypt the message with the cipher and return the encrypted message,
        // key, and IV as a Triple
        val encryptedMessage = cipher.doFinal(message.toByteArray(StandardCharsets.UTF_8))
        val encryptedMessageBase64 = Base64.encodeToString(encryptedMessage, Base64.DEFAULT)
        val keyBase64 = Base64.encodeToString(secretKey.encoded, Base64.DEFAULT)
        val ivBase64 = Base64.encodeToString(iv, Base64.DEFAULT)

        return Triple(encryptedMessageBase64, keyBase64, ivBase64)
    }

    /**
     * Handles the query of the Firestore database for the current user's messages and calls decryptMessage
     * to handle only user sent messages to and from a specific recipient ID (toID).
     * Updates the chat list with the decrypted messages.
     */
    private fun decryptUserOnlyMessages() {

        // Get an instance of the Firestore database
        val db = FirebaseFirestore.getInstance()

        // Get the ID of the current user
        val currentUserId = getCurrentUserId()

        // Query the "UserSentMessages" collection in Firestore for the current user's messages to a specific recipient ID (toID)
        db.collection("UserSentMessages")
            .document(currentUserId!!)
            .collection("SentMessages")
            .orderBy("timestamp")

            // Listen for changes to the query results, and decrypt and retrieve the messages
            .addSnapshotListener { querySnapshot, error ->
                if (error != null) {
                    // Log an error message if there was an error fetching the messages
                    Log.w(
                        "com.example.encryptedmessenger.ChatLog",
                        "Error fetching user sent messages",
                        error
                    )
                    return@addSnapshotListener
                }

                // Map the query results to a list of decrypted messages
                val userSentMessages = querySnapshot?.documents?.mapNotNull { document ->
                    // Retrieve the sender's username, encrypted message, encrypted AES key, IV, and timestamp from the document
                    val username = intent.getStringExtra("username")
                    val encryptedMessage = document.getString("encryptedMessage")
                    val encryptedAesKey = document.getString("encryptedAesKey")
                    val iv = document.getString("iv")
                    val timestamp = document.getDate("timestamp")

                    // Decrypt the message using the encrypted AES key and IV, and create a ChatMessage object with the decrypted message, username, and timestamp
                    if (encryptedMessage != null && encryptedAesKey != null && iv != null && timestamp != null) {
                        val decryptedMessage = decryptUserMessage(encryptedMessage, encryptedAesKey, iv)
                        if (decryptedMessage != null) {
                            ChatMessage(username, decryptedMessage, timestamp)
                        } else {
                            null
                        }
                    } else {
                        null
                    }
                } ?: emptyList()

                // Update the chat list with the decrypted messages
                updateChatList(userSentMessages)
            }
    }

    /**
     * Decrypts an AES encrypted message using the provided encrypted AES key and IV.
     *
     * @param encryptedMessage The base64-encoded encrypted message to decrypt.
     * @param encryptedAesKey The base64-encoded encrypted AES key to use for decryption.
     * @param iv The base64-encoded IV to use for decryption.
     * @return The decrypted message, or null if there was an error.
     */
    private fun decryptUserMessage(encryptedMessage: String, encryptedAesKey: String, iv: String): String? {
        return try {
            // Decode the encrypted message, key, and IV from Base64
            val decodedEncryptedMessage = Base64.decode(encryptedMessage, Base64.DEFAULT)
            val decodedKey = Base64.decode(encryptedAesKey, Base64.DEFAULT)
            val decodedIv = Base64.decode(iv, Base64.DEFAULT)

            // Reconstruct the SecretKey and IV from their decoded byte arrays
            val secretKey = SecretKeySpec(decodedKey, 0, decodedKey.size, "AES")
            val ivParameterSpec = IvParameterSpec(decodedIv)

            // Initialize the cipher for decryption
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey, ivParameterSpec)

            // Decrypt the message
            val decryptedMessageBytes = cipher.doFinal(decodedEncryptedMessage)

            // Convert the decrypted message bytes back to a string
            String(decryptedMessageBytes, StandardCharsets.UTF_8)
        } catch (e: Exception) {
            // Log an error message if there was a problem decrypting the message
            Log.e("com.example.encryptedmessenger.ChatLog", "Error decrypting message", e)
            null
        }
    }

    /**
     * Performs a symmetric send operation, where the message is encrypted using an ephemeral key and stored in the database for the recipient.
     * Also calls storeUserMessages if the original encryption's works to store user only messages from chat adapter view
     *
     * @param message The message to send.
     * @param recipientId The ID of the recipient user.
     */
    private fun performSymmetricSendMessage(message: String, recipientId: String) {
        // Retrieve the public key of the recipient from the database
        getRecipientPublicKey(this, recipientId) { recipientPublicKey ->
            // Encrypt the message using an ephemeral key and the recipient's public key
            val encryptedMessageData = encryptMessageWithEphemeralKey(message, recipientPublicKey)

            if (encryptedMessageData != null) {
                // Store the encrypted message in the database for the recipient
                storeEncryptedMessage(encryptedMessageData, recipientId)

                // Add this line to store the plain text message in the database for the current user
                storeUserMessages(message, recipientId)
            } else {
                // Display an error message if there was a problem encrypting the message
                Toast.makeText(this, "Error encrypting message", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Encrypts a plaintext message using AES-GCM encryption with a randomly generated AES key,
     * encrypts the AES key using the recipient's public key using RSA encryption, and
     * returns the encrypted message and encrypted AES key as an instance of EncryptedMessageData.
     *
     * @param message The plaintext message to be encrypted.
     * @param recipientPublicKey The public key of the message recipient.
     * @return An instance of EncryptedMessageData containing the encrypted message and encrypted AES key, or null if there was an error encrypting the message.
     */
    private fun encryptMessageWithEphemeralKey(message: String, recipientPublicKey: PublicKey): EncryptedMessageData? {
        // Generate a new AES key
        val aesKey = generateAesKey()

        // Encrypt the message using the AES key
        val encryptedMessage = encryptMessageWithAesKey(message, aesKey)

        // Encrypt the AES key using the recipient's public key
        val encryptedAesKey = encryptAesKeyWithRecipientPublicKey(aesKey, recipientPublicKey)

        // Return an instance of EncryptedMessageData containing the encrypted message and encrypted AES key
        return if (encryptedMessage != null && encryptedAesKey != null) {
            EncryptedMessageData(encryptedMessage, encryptedAesKey)
        } else {
            null
        }
    }

    /**
     * Retrieves the public key of the message recipient from the Firestore database,
     * decodes it from Base64 format, and returns it to the caller via the provided callback function.
     *
     * @param context The context of the current activity or application.
     * @param recipientId The ID of the message recipient.
     * @param callback A function to be called with the recipient's public key after it has been retrieved and decoded.
     */
    private fun getRecipientPublicKey(context: Context, recipientId: String, callback: (PublicKey) -> Unit) {
        // Get an instance of the Firestore database
        val db = FirebaseFirestore.getInstance()

        // Query the "users" collection in Firestore for the recipient's public key
        db.collection("users").document(recipientId).get()
            .addOnSuccessListener { documentSnapshot ->
                // Retrieve the recipient's public key as a Base64-encoded string
                val publicKeyString = documentSnapshot.getString("publicKey")

                if (publicKeyString != null) {
                    // Decode the recipient's public key from Base64 format
                    val publicKeyBytes = Base64.decode(publicKeyString, Base64.DEFAULT)
                    val keySpec = X509EncodedKeySpec(publicKeyBytes)
                    val keyFactory = KeyFactory.getInstance("RSA")
                    val publicKey = keyFactory.generatePublic(keySpec)

                    // Call the callback function with the recipient's public key
                    callback(publicKey)
                } else {
                    // Show an error message if the recipient's public key was not found
                    Toast.makeText(context, "Error: Public key not found", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                // Show an error message if there was an error retrieving the recipient's public key
                Toast.makeText(context, "Error retrieving recipient's public key", Toast.LENGTH_SHORT).show()
            }
    }

    /**
     * Generates a new AES key with a key length of 256 bits using the KeyGenerator class.
     *
     * @return The new AES key as a SecretKey object.
     */
    private fun generateAesKey(): SecretKey {
        // Get an instance of the KeyGenerator class for AES
        val keyGen = KeyGenerator.getInstance("AES")

        // Initialize the key generator with a key length of 256 bits
        keyGen.init(256)

        // Generate a new AES key and return it as a SecretKey object
        return keyGen.generateKey()
    }

    /**
     * Encrypts a plaintext message using the provided AES key and returns the resulting ciphertext as a Base64-encoded string.
     *
     * @param message The plaintext message to encrypt.
     * @param aesKey The AES key to use for encryption.
     * @return The Base64-encoded ciphertext of the encrypted message, or null if there was an error during encryption.
     */
    private fun encryptMessageWithAesKey(message: String, aesKey: SecretKey): String? {
        try {
            // Get an instance of the Cipher class for AES encryption
            val cipher = Cipher.getInstance("AES")

            // Initialize the cipher in encryption mode with the provided AES key
            cipher.init(Cipher.ENCRYPT_MODE, aesKey)

            // Encrypt the plaintext message with the cipher and convert the resulting ciphertext to a Base64-encoded string
            val encryptedBytes = cipher.doFinal(message.toByteArray(Charsets.UTF_8))
            return Base64.encodeToString(encryptedBytes, Base64.DEFAULT)
        } catch (e: Exception) {
            // Log any errors that occur during encryption and return null to indicate failure
            e.printStackTrace()
            return null
        }
    }

    /**
     * Encrypts the provided AES key using the recipient's public key and returns the resulting ciphertext as a Base64-encoded string.
     *
     * @param aesKey The AES key to encrypt.
     * @param recipientPublicKey The recipient's public key.
     * @return The Base64-encoded ciphertext of the encrypted AES key, or null if there was an error during encryption.
     */
    private fun encryptAesKeyWithRecipientPublicKey(aesKey: SecretKey, recipientPublicKey: PublicKey): String? {
        try {
            // Get an instance of the Cipher class for RSA encryption with PKCS1Padding
            val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")

            // Initialize the cipher in encryption mode with the recipient's public key
            cipher.init(Cipher.ENCRYPT_MODE, recipientPublicKey)

            // Encrypt the AES key with the cipher and convert the resulting ciphertext to a Base64-encoded string
            val encryptedBytes = cipher.doFinal(aesKey.encoded)
            return Base64.encodeToString(encryptedBytes, Base64.DEFAULT)
        } catch (e: Exception) {
            // Log any errors that occur during encryption and return null to indicate failure
            e.printStackTrace()
            return null
        }
    }

    /**
     * Stores an encrypted message in the Firestore database, along with the encrypted ephemeral key, recipient ID, timestamp, and recipient's username.
     *
     * @param encryptedMessageData The encrypted message and encrypted ephemeral key wrapped in an EncryptedMessageData object.
     * @param recipientId The ID of the recipient of the message.
     */
    private fun storeEncryptedMessage(encryptedMessageData: EncryptedMessageData, recipientId: String) {
        // Get an instance of the Firestore database and the current user's ID and the recipient's username
        val db = FirebaseFirestore.getInstance()
        val currentUserId = getCurrentUserId()
        val recipientUserName = intent.getStringExtra("recipientUsername")

        // Create a HashMap containing the encrypted message, encrypted ephemeral key, recipient ID,
        // timestamp, and recipient's username
        val messageData = hashMapOf(
            "fromID" to currentUserId,
            "toID" to recipientId,
            "encryptedMessage" to encryptedMessageData.encryptedMessage,
            "encryptedAesKey" to encryptedMessageData.encryptedAesKey,
            "timestamp" to FieldValue.serverTimestamp(),
            "username" to recipientUserName
        )

        // Add the message data to the Firestore collection "Messages"
        db.collection("Messages")
            .add(messageData)
            .addOnSuccessListener {
                // Print a success message if the message was stored successfully
                println("Success storing Messages")
            }
            .addOnFailureListener { e ->
                // Log an error message if there was an error storing the message data
                Log.w(
                    "com.example.encryptedmessenger.ChatLog",
                    "Error storing encrypted message and encrypted ephemeral key in Firestore",
                    e
                )
            }
    }

    /**
     *This function fetches symmetrically encrypted messages from the Firestore database,
     *where the messages are encrypted with a shared symmetric key.
     *The function listens for changes in the database and updates the chat list accordingly.
     *It retrieves messages that were sent to and from the recipient identified by recipientId.
     */
    private fun fetchSymmetricMessages(recipientId: String) {
// Get an instance of the Firestore database and the ID of the current user
        val db = FirebaseFirestore.getInstance()
        val currentUser = FirebaseAuth.getInstance().currentUser
        val currentUserId = currentUser?.uid
                // Listen for changes to the "Messages" collection in Firestore
                db.collection("Messages")
                    .orderBy("timestamp")
                    .addSnapshotListener { querySnapshot, error ->
                        if (error != null) {
                            // Log an error message if there was an error fetching the messages
                            Log.w(
                                "com.example.encryptedmessenger.ChatLog",
                                "Error fetching messages",
                                error
                            )
                            return@addSnapshotListener
                        }

                        // Clear the current chat list
                        chatList.clear()

                        // Iterate through the documents in the query results
                        querySnapshot?.documents?.forEach { document ->
                            val recipientUserName = intent.getStringExtra("recipientUsername")
                            val fromID = document.getString("fromID")
                            val toID = document.getString("toID")
                            val timestamp = document.getDate("timestamp")

                            // Check if the message was sent to and from the recipient identified by recipientId
                            if ((fromID == recipientId) && (toID == currentUserId)) {
                                val encryptedMessage = document.getString("encryptedMessage")
                                val encryptedAesKey = document.getString("encryptedAesKey")

                                // Decrypt the message using the encrypted AES key
                                if (encryptedMessage != null && encryptedAesKey != null) {
                                    val decryptedMessage = decryptMessage(encryptedMessage, encryptedAesKey)
                                    if (decryptedMessage != null) {
                                        chatList.add(ChatMessage(recipientUserName, decryptedMessage, timestamp!!))
                                    }
                                }
                            }
                        }

                        // Notify the adapter that the chat list has been updated
                        adapter.notifyDataSetChanged()
                    }
    }

    /**
     * Updates the chat list with new chat messages.
     *
     * @param newMessages a list of new chat messages to be added to the chat list.
     */
    private fun updateChatList(newMessages: List<ChatMessage>) {
        // Add the new messages to the chat list.
        chatList.addAll(newMessages)

        // Sort the chat list by the timestamp of each message.
        chatList.sortBy { it.timestamp }

        // Notify the adapter that the chat list has been updated.
        adapter.notifyDataSetChanged()

        // Scroll to the bottom of the RecyclerView.
        // The position of the last message in the chat list is chatList.size - 1.
        recyclerView.layoutManager?.scrollToPosition(chatList.size - 1)
    }

    /**
     * Decrypts an encrypted message using the user's private key and the AES key used for encryption.
     *
     * @param encryptedMessage the message to be decrypted.
     * @param encryptedAesKey the encrypted AES key used to encrypt the message.
     * @return the decrypted message or null if decryption failed.
     */
    private fun decryptMessage(encryptedMessage: String, encryptedAesKey: String): String? {
        // Get the user's private key.
        val privateKey = getCurrentUserPrivateKey()

        // Decrypt the AES key using the user's private key.
        val aesKey = decryptAesKeyWithPrivateKey(encryptedAesKey, privateKey)

        // Decrypt the message using the decrypted AES key.
        return if (aesKey != null) {
            decryptMessageWithAesKey(encryptedMessage, aesKey)
        } else {
            null
        }
    }

    /**
     * Gets the current user's private key from the Android Keystore.
     *
     * @return the user's private key.
     * @throws RuntimeException if the private key cannot be found or there is an error retrieving it.
     */
    private fun getCurrentUserPrivateKey(): PrivateKey {
        // Get the ID of the current user.
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid

        // Generate the alias for the private key.
        val privateKeyAlias = "key_$currentUserId"

        // Initialize the Android Keystore instance.
        val keyStore = KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)

        return try {
            // Get the private key entry from the Keystore using the alias.
            val privateKeyEntry = keyStore.getEntry(privateKeyAlias, null) as? KeyStore.PrivateKeyEntry

            // If the private key entry is not null, return the private key.
            if (privateKeyEntry != null) {
                privateKeyEntry.privateKey
            } else {
                // Otherwise, throw a runtime exception.
                throw RuntimeException("Private key not found for the current user")
            }
        } catch (e: Exception) {
            // If there is an error retrieving the private key, throw a runtime exception with the error message.
            throw RuntimeException("Error retrieving current user's private key", e)
        }
    }

    /**
     * Decrypts an AES key using the user's private key.
     *
     * @param encryptedAesKey the encrypted AES key to be decrypted.
     * @param privateKey the user's private key used to decrypt the AES key.
     * @return the decrypted AES key or null if decryption failed.
     */
    private fun decryptAesKeyWithPrivateKey(encryptedAesKey: String, privateKey: PrivateKey): SecretKey? {
        return try {
            // Get an instance of the RSA cipher.
            val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")

            // Initialize the cipher in decryption mode with the user's private key.
            cipher.init(Cipher.DECRYPT_MODE, privateKey)

            // Decode the encrypted AES key from Base64 to a byte array.
            val encryptedAesKeyBytes = Base64.decode(encryptedAesKey, Base64.DEFAULT)

            // Decrypt the AES key using the RSA cipher.
            val decryptedAesKeyBytes = cipher.doFinal(encryptedAesKeyBytes)

            // Create a SecretKeySpec from the decrypted AES key bytes and return it.
            val secretKeySpec = SecretKeySpec(decryptedAesKeyBytes, "AES")
            secretKeySpec
        } catch (e: Exception) {
            // If there is an error decrypting the AES key, print the stack trace and return null.
            e.printStackTrace()
            null
        }
    }

    /**
     * Decrypts a message using an AES key.
     *
     * @param encryptedMessage the encrypted message to be decrypted.
     * @param aesKey the AES key used to decrypt the message.
     * @return the decrypted message or null if decryption failed.
     */
    private fun decryptMessageWithAesKey(encryptedMessage: String, aesKey: SecretKey): String? {
        return try {
            // Get an instance of the AES cipher.
            val cipher = Cipher.getInstance("AES")

            // Initialize the cipher in decryption mode with the AES key.
            cipher.init(Cipher.DECRYPT_MODE, aesKey)

            // Decode the encrypted message from Base64 to a byte array.
            val encryptedMessageBytes = Base64.decode(encryptedMessage, Base64.DEFAULT)

            // Decrypt the message using the AES cipher.
            val decryptedBytes = cipher.doFinal(encryptedMessageBytes)

            // Convert the decrypted bytes to a UTF-8 encoded string and return it.
            String(decryptedBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            // If there is an error decrypting the message, print the stack trace and return null.
            e.printStackTrace()
            null
        }
    }

    /**
     * Gets the ID of the current user.
     *
     * @return the ID of the current user or null if the user is not authenticated.
     */
    private fun getCurrentUserId(): String? {
        // Get the current user using the Firebase Authentication API.
        val currentUser = FirebaseAuth.getInstance().currentUser

        // If the current user is not null, return their ID. Otherwise, return null.
        return currentUser?.uid
    }

    /**
     * Hides the keyboard after sending a message.
     *
     * @param view the view that currently has focus.
     */
    private fun hideKeyboard(view: View) {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }

    /**
     * Fetches and decrypts messages from the Firestore database.
     *
     * @param recipientId the ID of the chat partner.
     */
    private fun fetchAndDecryptMessages(recipientId: String) {
        fetchSymmetricMessages(recipientId)
        decryptUserOnlyMessages()
    }

    /**
     * Sets up the RecyclerView with its layout manager and adapter.
     */
    private fun setUpRecyclerView() {
        recyclerView = findViewById(R.id.recyclerview_chat)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.setHasFixedSize(true)

        chatList = ArrayList()
        adapter = ChatAdapter(chatList)
        recyclerView.adapter = adapter
    }




}


