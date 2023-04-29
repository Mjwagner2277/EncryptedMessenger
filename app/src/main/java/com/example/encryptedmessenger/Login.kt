package com.example.encryptedmessenger

import android.content.ContentValues
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import java.security.KeyStore

class Login : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth

    public override fun onStart() {
        super.onStart()
        // Check if user is signed in (non-null) and update UI accordingly.
        val currentUser = auth.currentUser
        if (currentUser != null) {
            val intent = Intent(this, NewMessages::class.java)
            startActivity(intent)
            finish()
        }
    }

    /**
     * Called when the activity is starting. This is where the UI is set up and click listeners are
     * added for the register and login buttons.
     *
     * @param savedInstanceState If the activity is being re-initialized after previously being
     * shut down, this Bundle contains the data it most recently supplied in onSaveInstanceState.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        auth = Firebase.auth
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        // Initialize views
        val editTextEmail = findViewById<TextView>(R.id.email)
        val editTextPassword = findViewById<TextView>(R.id.password)
        val buttonLogin = findViewById<Button>(R.id.btn_login)
        val progressBar = findViewById<ProgressBar>(R.id.progressBar)
        val textView = findViewById<TextView>(R.id.registerNow)

        // Set up click listener for "registerNow" TextView
        textView.setOnClickListener {
            // Navigate to Register activity
            val intent = Intent(this, Register::class.java)
            startActivity(intent)
            finish()
        }

        // Set up click listener for "btn_login" Button
        buttonLogin.setOnClickListener {
            progressBar.visibility = View.VISIBLE
            val email = editTextEmail.text.toString()
            val password = editTextPassword.text.toString()

            // Validate email and password inputs
            if (email.isEmpty()) {
                Toast.makeText(this, "Enter email", Toast.LENGTH_SHORT).show()
            }
            if (password.isEmpty()) {
                Toast.makeText(this, "Enter password", Toast.LENGTH_SHORT).show()
            }

            // Sign in with email and password
            auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener { task ->
                    progressBar.visibility = View.GONE
                    if (task.isSuccessful) {
                        // If sign in is successful, check for the private key
                        Log.d(ContentValues.TAG, "signInWithEmail:success")
                        val uid = auth.currentUser?.uid ?: ""

                        if (hasPrivateKey("key_$uid")) {
                            // If private key is available, navigate to NewMessages activity
                            Toast.makeText(this, "Login Successful.", Toast.LENGTH_SHORT).show()
                            val intent = Intent(this, NewMessages::class.java)
                            startActivity(intent)
                            finish()
                        } else {
                            // If private key is not available, show an error message
                            Toast.makeText(this, "No private key available.", Toast.LENGTH_SHORT)
                                .show()
                        }
                    } else {
                        // If sign in fails, display an error message to the user
                        Log.w(ContentValues.TAG, "signInWithEmail:failure", task.exception)
                        Toast.makeText(this, "Authentication failed.", Toast.LENGTH_SHORT).show()
                    }
                }
        }
    }

    /**
     * Checks if the private key for the specified alias exists in the Android KeyStore.
     *
     * @param alias The alias of the key to check for.
     * @return True if the private key exists, false otherwise.
     */
    private fun hasPrivateKey(alias: String): Boolean {
        return try {
            val keyStore = KeyStore.getInstance("AndroidKeyStore")
            keyStore.load(null)
            keyStore.containsAlias(alias) && keyStore.getKey(alias, null) != null
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}

