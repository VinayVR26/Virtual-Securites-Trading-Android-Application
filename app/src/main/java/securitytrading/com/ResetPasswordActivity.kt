package securitytrading.com

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import com.google.firebase.auth.FirebaseAuth

class ResetPasswordActivity : AppCompatActivity() {
    private lateinit var emailUsedToReset: EditText
    private lateinit var resetPasswordButton: Button
    val fAuth = FirebaseAuth.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reset_password)

        emailUsedToReset = findViewById(R.id.emailUsedToReset)
        resetPasswordButton = findViewById(R.id.resetPasswordButton)

        resetPasswordButton.setOnClickListener {
            val email = emailUsedToReset.text.toString()

            if (email.isNotEmpty()) {
                fAuth.sendPasswordResetEmail(email).addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        Toast.makeText(this, "Reset Password email sent.", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "Error sending reset password email.", Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                emailUsedToReset.error = "Email is missing"
                return@setOnClickListener
            }
        }
    }

    fun onSignInClickResetPassword (view: View) {
        startActivity(Intent(this@ResetPasswordActivity, LoginActivity::class.java))
    }
}