package com.danwand.mnit.vulnapp

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class AuthActivity : AppCompatActivity() {
    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        setContentView(R.layout.activity_auth)
        val api = Api(this); val store = Store(this)
        val u = findViewById<EditText>(R.id.username)
        val p = findViewById<EditText>(R.id.password)
        fun done(id: Int?, msg: String) = runOnUiThread {
            if (id != null) {
                store.saveCreds(u.text.toString(), p.text.toString(), id)
                startActivity(Intent(this, UsersActivity::class.java)); finish()
            } else Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
        }
        findViewById<Button>(R.id.register).setOnClickListener {
            api.register(u.text.toString(), p.text.toString(), ::done)
        }
        findViewById<Button>(R.id.login).setOnClickListener {
            api.login(u.text.toString(), p.text.toString(), ::done)
        }
    }
}
