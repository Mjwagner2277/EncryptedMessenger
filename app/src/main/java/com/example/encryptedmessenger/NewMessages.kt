package com.example.encryptedmessenger

import android.content.ContentValues.TAG
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.ktx.Firebase

class NewMessages : AppCompatActivity(), MyAdapter.OnItemClickListener {

    private lateinit var recyclerView: RecyclerView
    private lateinit var dataList: ArrayList<DataClass>

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.nav_menu, menu)
        return super.onCreateOptionsMenu(menu)
    }
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_sign_out -> {
                Firebase.auth.signOut()
                val intent = Intent(this, Login::class.java)
                startActivity(intent)
                finish()
            }
        }
        return super.onOptionsItemSelected(item)
    }

    /**
     * Initializes the activity and sets up the UI elements and listeners.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_new_messages)

        // Find the RecyclerView in the layout and set its layout manager and size to fixed
        recyclerView = findViewById(R.id.recyclerview_newmessage)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.setHasFixedSize(true)

        // Initialize an empty list to hold data
        dataList = arrayListOf()

        // Fetch data from the Firestore database and display it in the RecyclerView
        getData()
    }

    /**
     * Fetches user data from the Firestore database, excluding the current user's data.
     * Populates the dataList with DataClass objects containing the usernames and UIDs of each user.
     * Sets up a click listener for each item in the RecyclerView.
     */
    private fun getData() {
        val db = FirebaseFirestore.getInstance()
        val usersRef = db.collection("users")
            .whereNotEqualTo("userID", FirebaseAuth.getInstance().currentUser?.uid)

        // Fetch data from Firestore and add it to the dataList
        usersRef.get().addOnSuccessListener { documents ->
            for (document in documents) {
                val username = document.getString("username")
                val uid = document.getString("userID")
                if (username != null && uid != null) {
                    val dataClass = DataClass(username, uid)
                    dataList.add(dataClass)
                }
            }
            // Set the RecyclerView adapter to display the data
            recyclerView.adapter = MyAdapter(this, dataList)
        }.addOnFailureListener { exception ->
            Log.w(TAG, "Error getting documents.", exception)
        }
    }

    /**
     * Handles item click events in the RecyclerView and starts a new chat with the selected user.
     * @param position the position of the selected item in the RecyclerView
     */
    override fun onItemClick(position: Int) {
        // Fetch the username of the current user and pass it along with the selected user's information to the ChatLog activity
        fetchCurrentUserUsername { username ->
            // Get the username and user ID of the selected user
            val recipientUsername = dataList[position].dataName
            val uid = dataList[position].userID

            // Create an intent to start the ChatLog activity
            val intent = Intent(this, ChatLog::class.java)

            // Add the selected user's information and the current user's username to the intent
            intent.putExtra("recipientUsername", recipientUsername)
            intent.putExtra("username", username)
            intent.putExtra("uid", uid)

            // Start the ChatLog activity with the created intent
            startActivity(intent)
        }
    }

    /**
     * Fetches the username of the current user from the Firestore database,
     * and passes it to a callback function.
     * @param callback A lambda function that takes the current user's username as a parameter.
     */
    private fun fetchCurrentUserUsername(callback: (String) -> Unit) {
        // Get an instance of the Firestore database.
        val db = FirebaseFirestore.getInstance()

        // Get the ID of the currently logged-in user.
        val currentUserId = getCurrentUserId()

        // Query the "users" collection in the Firestore database for the user with the ID of the currently logged-in user.
        db.collection("users")
            .document(currentUserId!!)
            .get()
            .addOnSuccessListener { documentSnapshot ->
                // If the query is successful, retrieve the username from the returned document snapshot.
                val username = documentSnapshot.getString("username")

                // If the username is not null, invoke the callback function with the retrieved username as an argument.
                if (username != null) {
                    callback(username)
                } else {
                    // Otherwise, log an error message.
                    Log.w("com.example.encryptedmessenger", "Error fetching username")
                }
            }
            .addOnFailureListener { exception ->
                // If there is an error fetching the username, log an error message with the exception.
                Log.w("com.example.encryptedmessenger", "Error fetching username", exception)
            }
    }

    /**
     * Gets the ID of the current user from the Firebase Authentication instance.
     * @return The ID of the current user as a String if they are logged in, or null if not.
     */
    private fun getCurrentUserId(): String? {
        val currentUser = FirebaseAuth.getInstance().currentUser
        return currentUser?.uid
    }

}

