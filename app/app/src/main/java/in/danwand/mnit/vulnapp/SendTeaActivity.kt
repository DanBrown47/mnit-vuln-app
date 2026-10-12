package in.danwand.mnit.vulnapp

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class SendTeaActivity : AppCompatActivity() {
    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        setContentView(R.layout.activity_send_tea)
        val store = Store(this); val api = Api(this)
        val recipient = findViewById<EditText>(R.id.recipient)
        val body = findViewById<EditText>(R.id.body)
        intent.getIntExtra("to", -1).let { if (it > 0) recipient.setText(it.toString()) }
        findViewById<Button>(R.id.send).setOnClickListener {
            val to = recipient.text.toString().toIntOrNull() ?: return@setOnClickListener
            api.sendTea(store.userId, to, body.text.toString()) { ok ->
                runOnUiThread {
                    Toast.makeText(this, if (ok) "sent" else "failed", Toast.LENGTH_SHORT).show()
                    if (ok) finish()
                }
            }
        }
    }
}
