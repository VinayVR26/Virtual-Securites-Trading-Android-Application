package securitytrading.com

import android.content.ContentValues
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.content.Intent
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.SignInButton
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore

class RegisterActivity : AppCompatActivity() {
    private lateinit var registerEmail: EditText
    private lateinit var registerPassword: EditText
    private lateinit var registerConfirmPassword: EditText
    private lateinit var signUpButton: Button

    val fAuth = FirebaseAuth.getInstance()
    val fStore = FirebaseFirestore.getInstance()

    val googleSignInButton: SignInButton by lazy {
        findViewById<SignInButton>(R.id.googleSignInButton)
    }

    val RC_SIGN_IN = 123

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        googleSignInButton.setOnClickListener {
            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build()

            val googleSignInClient = GoogleSignIn.getClient(this, gso)
            val signInIntent = googleSignInClient.signInIntent
            startActivityForResult(signInIntent, RC_SIGN_IN)
        }

        registerEmail = findViewById(R.id.signUpEmail)
        registerPassword = findViewById(R.id.signUpPassword)
        registerConfirmPassword = findViewById(R.id.signUpConfirmPassword)

        signUpButton = findViewById(R.id.signUpButton)

        signUpButton.setOnClickListener {
            val email = registerEmail.text.toString()
            val password = registerPassword.text.toString()
            val confirmPassword = registerConfirmPassword.text.toString()

            if (email.isEmpty()) {
                registerEmail.error = "Email is required"
                return@setOnClickListener
            }

            if (password.isEmpty()) {
                registerPassword.error = "Password is required"
                return@setOnClickListener
            }

            if (confirmPassword.isEmpty()) {
                registerConfirmPassword.error = "Confirm password is required"
                return@setOnClickListener
            }

            if (!password.equals(confirmPassword)) {
                registerConfirmPassword.error = "Password does not match"
                return@setOnClickListener
            }

            fAuth.createUserWithEmailAndPassword(email,password).addOnSuccessListener { authResult ->
                val user = authResult.user
                user?.sendEmailVerification()?.addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        Toast.makeText(this, "Verification email sent.", Toast.LENGTH_SHORT).show()
                        startActivity(Intent(this@RegisterActivity, VerifyEmailActivity::class.java))
                        finish()
                    } else {
                        Toast.makeText(this, "Error sending verification email.", Toast.LENGTH_SHORT).show()
                    }
                }?.addOnFailureListener { exception ->
                    Toast.makeText(this, "Error when creating user: ${exception.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    fun onSignInClick(view: View) {
        val intent = Intent(this, LoginActivity::class.java)
        startActivity(intent)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == RC_SIGN_IN) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            try {
                val account = task.getResult(ApiException::class.java)
                val credential = GoogleAuthProvider.getCredential(account?.idToken, null)
                firebaseAuthWithGoogle(credential)
            } catch (e: ApiException) {
                Log.w(ContentValues.TAG, "Google sign in failed", e)
            }
        }
    }

    private fun firebaseAuthWithGoogle(credential: AuthCredential) {
        fAuth.signInWithCredential(credential).addOnCompleteListener(this) { task ->
            if (task.isSuccessful) {
                val user = fAuth.currentUser
                if (user != null) {
                    checkUsernameExistence()
                }
            }
        }
    }

    private fun checkUsernameExistence() {
        val email = fAuth.currentUser?.email.toString()
        val userDocument = fStore.collection("users").document(email)
        userDocument.get().addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val documentSnapshot = task.result
                if (documentSnapshot?.exists() == true) {
                    startActivity(Intent(this@RegisterActivity, MainActivity::class.java))
                    finish()
                } else {
                    startActivity(Intent(this@RegisterActivity, CreateProfileActivity::class.java))
                    finish()
                }
            } else {
                Toast.makeText(this, "ERROR", Toast.LENGTH_SHORT).show()
            }
        }
    }
}