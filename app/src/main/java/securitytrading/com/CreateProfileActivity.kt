package securitytrading.com

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.content.Intent
import android.app.Activity
import android.graphics.Bitmap
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import java.io.ByteArrayOutputStream

class CreateProfileActivity : AppCompatActivity() {
    private lateinit var usernameCP: EditText
    private lateinit var firstNameCP: EditText
    private lateinit var lastNameCP: EditText
    private lateinit var ageCP: EditText

    private lateinit var profileImageView: ImageView
    private lateinit var createProfileButton: Button

    private val PICK_IMAGE_REQUEST = 1


    val fAuth = FirebaseAuth.getInstance()
    val fStore = FirebaseFirestore.getInstance()
    val fStorage = FirebaseStorage.getInstance()
    val storageRef: StorageReference = fStorage.reference



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_profile)

        usernameCP = findViewById(R.id.username)
        firstNameCP = findViewById(R.id.firstName)
        lastNameCP = findViewById(R.id.lastName)
        ageCP = findViewById(R.id.age)

        profileImageView = findViewById(R.id.profileImageView)
        profileImageView.setOnClickListener {
            openImagePicker()
        }

        createProfileButton = findViewById(R.id.createProfileButton)

        createProfileButton.setOnClickListener {

            var validationFailed = false

            val username = usernameCP.text.toString()
            val firstName = firstNameCP.text.toString()
            val lastName = lastNameCP.text.toString()
            val ageText = ageCP.text.toString()

            if (username.isEmpty()) {
                usernameCP.error = "Username cannot be empty"
                validationFailed = true
            } else {
                fStore.collection("usernames").document("usernameList").get().addOnCompleteListener { usernameListTask ->
                    if (usernameListTask.isSuccessful) {
                        val usernameList = usernameListTask.result?.get("usernames") as? ArrayList<String> ?: arrayListOf()
                        if (usernameList.contains(username)) {
                            usernameCP.error = "Username is already in use"
                            validationFailed = true
                        }
                    }
                }
            }

            if (firstName.isEmpty()) {
                firstNameCP.error = "First name cannot be empty"
                validationFailed = true
            }

            if (lastName.isEmpty()) {
                lastNameCP.error = "First name cannot be empty"
                validationFailed = true
            }

            if (ageText.isEmpty()) {
                ageCP.error = "First name cannot be empty"
                validationFailed = true
            } else {
                val age: Int = ageText.toInt()
                if (age < 0) {
                    ageCP.error = "Age cannot be negative"
                    validationFailed = true
                }
            }

            if (validationFailed) {
                return@setOnClickListener
            }

            val email = fAuth.currentUser?.email.toString()

            // Check if the username is already in use
            fStore.collection("usernames").document("usernameList").get().addOnCompleteListener { usernameListTask ->
                if (validationFailed) {
                    return@addOnCompleteListener
                }
                if (usernameListTask.isSuccessful) {
                    val usernameList = usernameListTask.result?.get("usernames") as? ArrayList<String> ?: arrayListOf()

                    usernameList.add(username)
                    usernameList.sort()

                    // Update the username list in Firestore
                    val usernameListData = hashMapOf("usernames" to usernameList)
                    fStore.collection("usernames").document("usernameList").set(usernameListData).addOnCompleteListener { usernameUpdateTask ->
                        if (usernameUpdateTask.isSuccessful) {
                            Toast.makeText(this, "UsernameListUpdated", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this, "Error", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }

            val imageRef = storageRef.child("user_images/$email")
            profileImageView.isDrawingCacheEnabled = true
            profileImageView.buildDrawingCache()
            val bitmap = profileImageView.drawingCache
            val baos = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, baos)
            val imageData = baos.toByteArray()

            val uploadTask = imageRef.putBytes(imageData)
            uploadTask.continueWithTask { task ->
                if (!task.isSuccessful) {
                    task.exception?.let {
                        throw it
                    }
                }
                imageRef.downloadUrl }.addOnCompleteListener { urlTask ->
                if (validationFailed) {
                    return@addOnCompleteListener
                }
                if (urlTask.isSuccessful) {
                    val downloadUrl = urlTask.result

                    val userData = hashMapOf(
                        "email" to email,
                        "username" to username,
                        "firstName" to firstName,
                        "lastName" to lastName,
                        "age" to ageText,
                        "imageUrl" to downloadUrl.toString(),
                        "fundsForTrading" to 10000,
                        "totalCashFlow" to 10000
                    )



                    val userDocument = fStore.collection("users").document(email)
                    userDocument.get().addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            userDocument.set(userData).addOnCompleteListener { profileUpdateTask ->
                                if (profileUpdateTask.isSuccessful) {
                                    Toast.makeText(this, "Profile created successfully", Toast.LENGTH_SHORT).show()
                                    startActivity(Intent(this@CreateProfileActivity, MainActivity::class.java))
                                    finish()
                                } else {
                                    Toast.makeText(this, "ERROR", Toast.LENGTH_SHORT).show()
                                }
                            }
                        } else {
                            Toast.makeText(this, "ERROR", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }

    private fun openImagePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
        }
        startActivityForResult(intent, PICK_IMAGE_REQUEST)
    }


    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == PICK_IMAGE_REQUEST && resultCode == Activity.RESULT_OK && data != null) {
            val selectedImageUri = data.data
            if (selectedImageUri != null) {
                contentResolver.takePersistableUriPermission(
                    selectedImageUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                profileImageView.setImageURI(selectedImageUri)
            }
        }
    }
}