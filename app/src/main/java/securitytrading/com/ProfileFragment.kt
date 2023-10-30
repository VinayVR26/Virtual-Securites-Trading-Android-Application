package securitytrading.com

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import com.squareup.picasso.Picasso
import java.io.ByteArrayOutputStream

class ProfileFragment : Fragment() {

    private lateinit var usernameET: EditText
    private lateinit var firstNameET: EditText
    private lateinit var lastNameET: EditText
    private lateinit var profileImageView: ImageView
    private lateinit var updateProfileButton: Button

    private val PICK_IMAGE_REQUEST = 1
    private var selectedImageUri: Uri? = null

    private val fAuth = FirebaseAuth.getInstance()
    private val fStore = FirebaseFirestore.getInstance()
    private val fStorage = FirebaseStorage.getInstance()
    private val storageRef: StorageReference = fStorage.reference

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_profile, container, false)

        usernameET = view.findViewById(R.id.username)
        firstNameET = view.findViewById(R.id.firstName)
        lastNameET = view.findViewById(R.id.lastName)
        profileImageView = view.findViewById(R.id.profileImageView)
        updateProfileButton = view.findViewById(R.id.updateProfileButton)

        profileImageView.setOnClickListener {
            openImagePicker()
        }

        updateProfileButton.setOnClickListener {
            updateProfile()
        }

        loadUserProfileDataAndUpdateSidebar()

        return view
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
            selectedImageUri = data.data
            profileImageView.setImageURI(selectedImageUri)
        }
    }

    private fun updateProfile() {
        val email = fAuth.currentUser?.email.toString()
        val username = usernameET.text.toString()
        val firstName = firstNameET.text.toString()
        val lastName = lastNameET.text.toString()

        if (username.isEmpty() || firstName.isEmpty() || lastName.isEmpty()) {
            Toast.makeText(context, "Please fill in all fields", Toast.LENGTH_SHORT).show()
            return
        }

        val userData = hashMapOf(
            "username" to username,
            "firstName" to firstName,
            "lastName" to lastName
        )

        fStore.collection("users").document(email)
            .update(userData as Map<String, Any>)
            .addOnSuccessListener {
                Toast.makeText(context, "Profile updated successfully", Toast.LENGTH_SHORT).show()

                if (selectedImageUri != null) {
                    uploadProfileImage(email)
                }

                updateSidebar(username, selectedImageUri.toString())
            }
            .addOnFailureListener {
                Toast.makeText(context, "Failed to update profile", Toast.LENGTH_SHORT).show()
            }
    }

    private fun uploadProfileImage(email: String) {
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
            imageRef.downloadUrl
        }.addOnCompleteListener { urlTask ->
            if (urlTask.isSuccessful) {
                val downloadUrl = urlTask.result

                val userData = hashMapOf("imageUrl" to downloadUrl.toString())
                fStore.collection("users").document(email)
                    .update(userData as Map<String, Any>)
                    .addOnSuccessListener {
                        Toast.makeText(context, "Profile image updated successfully", Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener {
                        Toast.makeText(context, "Failed to update profile image", Toast.LENGTH_SHORT).show()
                    }
            }
        }
    }


    private fun loadUserProfileDataAndUpdateSidebar() {
        val email = fAuth.currentUser?.email.toString()
        val userDocument = fStore.collection("users").document(email)

        userDocument.get().addOnSuccessListener { documentSnapshot ->
            if (documentSnapshot.exists()) {
                val username = documentSnapshot.getString("username")
                val firstName = documentSnapshot.getString("firstName")
                val lastName = documentSnapshot.getString("lastName")
                val imageUrl = documentSnapshot.getString("imageUrl")

                usernameET.setText(username)
                firstNameET.setText(firstName)
                lastNameET.setText(lastName)

                if (!imageUrl.isNullOrBlank()) {
                    Picasso.get().load(imageUrl).into(profileImageView)
                }

                updateSidebar(username.toString(), imageUrl.toString())
            }
        }
    }

    private fun updateSidebar(username: String, imageUrl: String) {
        val mainActivity = activity as MainActivity
        mainActivity.updateSidebar(username, imageUrl)
    }
}