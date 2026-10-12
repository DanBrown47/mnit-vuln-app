package com.danwand.mnit.vulnapp

import android.os.Bundle
import android.widget.ListView
import androidx.appcompat.app.AppCompatActivity

class MyTeaActivity : AppCompatActivity() {
    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        setContentView(R.layout.activity_my_tea)
        val store = Store(this)
        val list = findViewById<ListView>(R.id.list)
        val api = Api(this)

        api.users { usersArr ->
            val names = HashMap<Int, String>()
            if (usersArr != null) for (i in 0 until usersArr.length()) {
                val o = usersArr.getJSONObject(i)
                names[o.getInt("id")] = o.getString("username")
            }
            api.tea(store.userId) { teaArr ->
                val rows = ArrayList<Row>()
                if (teaArr != null) for (i in 0 until teaArr.length()) {
                    val o = teaArr.getJSONObject(i)
                    val fromId = o.getInt("from_user_id")
                    val fromName = names[fromId] ?: "user #$fromId"
                    rows.add(Row(fromName.take(1).uppercase(),
                        "From $fromName", o.getString("body")))
                }
                runOnUiThread { list.adapter = RowAdapter(this, rows) }
            }
        }
    }
}
