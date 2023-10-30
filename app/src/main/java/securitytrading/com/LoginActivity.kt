package securitytrading.com

import android.content.ContentValues.TAG
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.content.Intent
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.SignInButton
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.GoogleAuthProvider

class LoginActivity : AppCompatActivity() {
    private lateinit var loginEmail: EditText
    private lateinit var loginPassword: EditText
    private lateinit var signInButton: Button

    val fAuth = FirebaseAuth.getInstance()
    val fStore = FirebaseFirestore.getInstance()

    val googleSignInButton: SignInButton by lazy {
        findViewById<SignInButton>(R.id.googleSignInButton)
    }

    val RC_SIGN_IN = 123

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        googleSignInButton.setOnClickListener {
            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build()

            val googleSignInClient = GoogleSignIn.getClient(this, gso)
            val signInIntent = googleSignInClient.signInIntent
            startActivityForResult(signInIntent, RC_SIGN_IN)
        }

        loginEmail = findViewById(R.id.signInEmail)
        loginPassword = findViewById(R.id.signInPassword)
        signInButton = findViewById(R.id.signInButton)

        signInButton.setOnClickListener {

            val email = loginEmail.text.toString()
            val password = loginPassword.text.toString()

            if (email.isEmpty()) {
                loginEmail.error = "Email is missing"
                return@setOnClickListener
            }

            if (password.isEmpty()) {
                loginPassword.error = "Password is missing"
                return@setOnClickListener
            }

            fAuth.signInWithEmailAndPassword(email, password).addOnSuccessListener {
                val user = fAuth.currentUser
                if (user != null) {
                    if (user.isEmailVerified) {
                        checkUsernameExistence()
                    } else {
                        startActivity(Intent(this@LoginActivity, VerifyEmailActivity::class.java))
                    }
                }
            }.addOnFailureListener { exception ->
                Toast.makeText(this, "${exception.message}", Toast.LENGTH_SHORT).show()
            }
        }
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
                Log.w(TAG, "Google sign in failed", e)
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
                    startActivity(Intent(this@LoginActivity, MainActivity::class.java))
                    finish()
                } else {
                    startActivity(Intent(this@LoginActivity, CreateProfileActivity::class.java))
                    finish()
                }
            } else {
                Toast.makeText(this, "ERROR", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun onSignUpClick(view: View) {
        val intent = Intent(this@LoginActivity, RegisterActivity::class.java)
        startActivity(intent)
    }

    fun onForgotPasswordClick(view: View) {
        startActivity(Intent(this@LoginActivity, ResetPasswordActivity::class.java))
    }

    override fun onStart() {
        super.onStart()
        val currentUser = fAuth.currentUser
        if (currentUser != null) {
            if (!currentUser.isEmailVerified) {
                return
            }

            val userEmail = currentUser.email.toString()
            fStore.collection("users").document(userEmail).get().addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val documentSnapshot = task.result
                    if (documentSnapshot != null && documentSnapshot.exists()) {
                        val username = documentSnapshot.getString("username")
                        if (!username.isNullOrEmpty()) {
                            startActivity(Intent(this@LoginActivity, MainActivity::class.java))
                            finish()
                        } else {
                            return@addOnCompleteListener
                        }
                    } else {
                        return@addOnCompleteListener
                    }
                } else {
                    return@addOnCompleteListener
                }
            }
        }
    }
}