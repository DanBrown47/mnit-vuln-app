package com.danwand.mnit.vulnapp

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ListView
import androidx.appcompat.app.AppCompatActivity

class UsersActivity : AppCompatActivity() {
    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        setContentView(R.layout.activity_users)
        val api = Api(this)
        val list = findViewById<ListView>(R.id.list)
        findViewById<Button>(R.id.my_tea).setOnClickListener {
            startActivity(Intent(this, MyTeaActivity::class.java))
        }
        api.users { arr ->
            if (arr == null) return@users
            val ids = IntArray(arr.length()); val names = ArrayList<String>()
            val rows = ArrayList<Row>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val id = o.getInt("id"); val name = o.getString("username")
                ids[i] = id; names.add(name)
                rows.add(Row(name.take(1).uppercase(), name, "ID #$id  •  tap to send tea"))
            }
            runOnUiThread {
                list.adapter = RowAdapter(this, rows)
                list.setOnItemClickListener { _, _, pos, _ ->
                    startActivity(Intent(this, SendTeaActivity::class.java)
                        .putExtra("to", ids[pos])
                        .putExtra("toName", names[pos]))
                }
            }
        }
    }
}
