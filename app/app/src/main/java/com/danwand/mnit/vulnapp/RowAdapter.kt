package com.danwand.mnit.vulnapp

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.TextView
import androidx.core.content.ContextCompat

data class Row(val avatarLetter: String, val title: String, val subtitle: String)

private val AVATAR_COLORS = intArrayOf(
    R.color.avatar1, R.color.avatar2, R.color.avatar3, R.color.avatar4, R.color.avatar5
)

fun avatarColorFor(ctx: Context, seed: String): Int {
    val idx = (seed.hashCode().and(0x7fffffff)) % AVATAR_COLORS.size
    return ContextCompat.getColor(ctx, AVATAR_COLORS[idx])
}

class RowAdapter(private val ctx: Context, private val rows: List<Row>) : BaseAdapter() {
    override fun getCount() = rows.size
    override fun getItem(position: Int) = rows[position]
    override fun getItemId(position: Int) = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val view = convertView ?: LayoutInflater.from(ctx).inflate(R.layout.row_item, parent, false)
        val row = rows[position]
        val avatar = view.findViewById<TextView>(R.id.row_avatar)
        avatar.text = row.avatarLetter
        avatar.background.setTint(avatarColorFor(ctx, row.title))
        view.findViewById<TextView>(R.id.row_title).text = row.title
        view.findViewById<TextView>(R.id.row_subtitle).text = row.subtitle
        return view
    }
}
