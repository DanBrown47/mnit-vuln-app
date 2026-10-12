package in.danwand.mnit.vulnapp

import android.content.Context
import android.util.Log

class Store(ctx: Context) {
    private val p = ctx.getSharedPreferences("mnit_prefs", Context.MODE_PRIVATE)
    fun saveCreds(username: String, password: String, userId: Int) {
        p.edit().putString("username", username)
            .putString("password", password)      // plaintext, intentional
            .putInt("user_id", userId).apply()
        Log.d("MNIT", "stored creds $username / $password id=$userId")  // insecure log
    }
    val userId: Int get() = p.getInt("user_id", -1)
    val username: String get() = p.getString("username", "") ?: ""
}
