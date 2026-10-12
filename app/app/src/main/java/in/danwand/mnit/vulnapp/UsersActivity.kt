package in.danwand.mnit.vulnapp

import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
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
            val ids = IntArray(arr.length()); val labels = ArrayList<String>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                ids[i] = o.getInt("id"); labels.add("${o.getInt("id")}  ${o.getString("username")}")
            }
            runOnUiThread {
                list.adapter = ArrayAdapter(this, R.layout.row_item, R.id.row_text, labels)
                list.setOnItemClickListener { _, _, pos, _ ->
                    startActivity(Intent(this, SendTeaActivity::class.java)
                        .putExtra("to", ids[pos]))
                }
            }
        }
    }
}
