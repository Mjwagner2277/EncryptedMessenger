package com.example.encryptedmessenger


import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import java.security.KeyPairGenerator
import java.security.spec.X509EncodedKeySpec
import android.util.Base64

class Register: AppCompatActivity() {

    private lateinit var auth: FirebaseAuth

    /**
     * Called when the activity is starting. This is where the UI is set up and click listeners are
     * added for the login and register buttons.
     *
     * @param savedInstanceState If the activity is being re-initialized after previously being
     * shut down, this Bundle contains the data it most recently supplied in onSaveInstanceState.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        auth = Firebase.auth
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        // Initialize views
        val editTextEmail = findViewById<TextView>(R.id.email)
        val editTextPassword = findViewById<TextView>(R.id.password)
        val editTextUsername = findViewById<TextView>(R.id.username)
        val buttonReg = findViewById<Button>(R.id.btn_register)
        val progressBar = findViewById<ProgressBar>(R.id.progressBar)
        val textView = findViewById<TextView>(R.id.loginNow)

        // Set up click listener for "loginNow" TextView
        textView.setOnClickListener {
            // Navigate to Login activity
            val intent = Intent(this, Login::class.java)
            startActivity(intent)
            finish()
        }

        // Set up click listener for "btn_register" Button
        buttonReg.setOnClickListener {
            progressBar.visibility = View.VISIBLE
            val email = editTextEmail.text.toString()
            val password = editTextPassword.text.toString()
            val username = editTextUsername.text.toString()

            // Validate email and password inputs
            if (email.isEmpty()) {
                Toast.makeText(this, "Enter email", Toast.LENGTH_SHORT).show()
            }
            if (password.isEmpty()) {
                Toast.makeText(this, "Enter password", Toast.LENGTH_SHORT).show()
            }

            // Register the user and handle the result
            createUserWithEmailAndPassword(email, password, username) { success, exception ->
                progressBar.visibility = View.GONE
                if (success) {
                    // If registration is successful, navigate to NewMessages activity
                    Toast.makeText(this, "Registration Successful.", Toast.LENGTH_SHORT).show()
                    val intent = Intent(this, NewMessages::class.java)
                    startActivity(intent)
                    finish()
                } else {
                    // If registration fails, show an error message
                    Toast.makeText(
                        this,
                        "Authentication failed: ${exception?.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }


    /**
     * Creates a new user with the given email, password, and username. Upon successful registration,
     * it generates a public-private key pair, stores the public key in Firestore, and calls the
     * onComplete callback with the success status and any exception that occurred.
     *
     * @param email The email address of the new user.
     * @param password The password for the new user.
     * @param username The username of the new user.
     * @param onComplete A callback function to be called once the operation is complete.
     */
    private fun createUserWithEmailAndPassword(email: String, password: String, username: String, onComplete: (Boolean, Exception?) -> Unit) {
        val auth = Firebase.auth
        val db = Firebase.firestore

        // Create a new user with the email and password
        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    // If the user is successfully registered
                    val uid = auth.currentUser?.uid ?: ""
                    val user = hashMapOf(
                        "userID" to uid,
                        "email" to email,
                        "password" to password,
                        "username" to username
                    )

                    // Generate a public-private key pair using Android Keystore System
                    val alias = "key_$uid"
                    val keyPairGenerator = KeyPairGenerator.getInstance("RSA", "AndroidKeyStore")
                    keyPairGenerator.initialize(
                        KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_PKCS1)
                            .setKeySize(2048)
                            .build()
                    )
                    val keyPair = keyPairGenerator.generateKeyPair()
                    val publicKey = keyPair.public

                    // Convert the public key to a base64-encoded string
                    val publicKeySpec = X509EncodedKeySpec(publicKey.encoded)
                    val keyFactory = java.security.KeyFactory.getInstance("RSA")
                    val base64PublicKey = Base64.encodeToString(publicKeySpec.encoded, Base64.DEFAULT)

                    // Add the public key to the user document in Firestore
                    user["publicKey"] = base64PublicKey
                    db.collection("users")
                        .document(uid)
                        .set(user)
                        .addOnSuccessListener {
                            onComplete(true, null)
                        }
                        .addOnFailureListener { exception ->
                            onComplete(false, exception)
                        }

                } else {
                    // If the registration failed, pass the exception to the callback
                    onComplete(false, task.exception)
                }
            }
    }

}
