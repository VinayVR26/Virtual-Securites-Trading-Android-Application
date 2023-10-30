package securitytrading.com


import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.Toast
import com.google.firebase.auth.FirebaseAuth

class VerifyEmailActivity : AppCompatActivity() {
    private lateinit var resendLinkButton: Button
    val fAuth = FirebaseAuth.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_verify_email)

        resendLinkButton = findViewById(R.id.resendLinkButton)

        resendLinkButton.setOnClickListener {
            val user = fAuth.currentUser
            if (user != null) {
                user.sendEmailVerification().addOnCompleteListener {task ->
                    if (task.isSuccessful) {
                        Toast.makeText(this, "Verification email sent.", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "Error sending verification email.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }
    fun onSignInClickVerifyEmail(view: View) {
        val intent = Intent(this, LoginActivity::class.java)
        startActivity(intent)
    }
}
