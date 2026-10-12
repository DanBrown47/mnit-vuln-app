package com.danwand.mnit.vulnapp

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.ListView
import androidx.appcompat.app.AppCompatActivity

class MyTeaActivity : AppCompatActivity() {
    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        setContentView(R.layout.activity_my_tea)
        val store = Store(this)
        val list = findViewById<ListView>(R.id.list)
        Api(this).tea(store.userId) { arr ->
            val rows = ArrayList<String>()
            if (arr != null) for (i in 0 until arr.length())
                rows.add(arr.getJSONObject(i).getString("body"))
            runOnUiThread {
                list.adapter = ArrayAdapter(this, R.layout.row_item, R.id.row_text, rows)
            }
        }
    }
}
