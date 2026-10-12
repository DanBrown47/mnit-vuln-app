package com.danwand.mnit.vulnapp

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class SendTeaActivity : AppCompatActivity() {
    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        setContentView(R.layout.activity_send_tea)
        val store = Store(this); val api = Api(this)
        val body = findViewById<EditText>(R.id.body)
        val to = intent.getIntExtra("to", -1)
        val toName = intent.getStringExtra("toName") ?: "user #$to"

        val avatar = findViewById<TextView>(R.id.recipient_avatar)
        val nameView = findViewById<TextView>(R.id.recipient_name)
        avatar.text = toName.take(1).uppercase()
        avatar.background.setTint(avatarColorFor(this, toName))
        nameView.text = toName

        findViewById<Button>(R.id.send).setOnClickListener {
            if (to <= 0) return@setOnClickListener
            api.sendTea(store.userId, to, body.text.toString()) { ok ->
                runOnUiThread {
                    Toast.makeText(this,
                        if (ok) "Tea sent to $toName" else "Failed to send",
                        Toast.LENGTH_SHORT).show()
                    if (ok) finish()
                }
            }
        }
    }
}
