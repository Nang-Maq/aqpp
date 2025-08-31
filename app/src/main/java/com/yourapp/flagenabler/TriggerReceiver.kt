package com.yourapp.flagenabler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.preference.PreferenceManager   // <— ganti ini

class TriggerReceiver : BroadcastReceiver() {
  override fun onReceive(ctx: Context, intent: Intent) {
    if (intent.action != "com.yourapp.ENABLE_NONROOT_CMDLINE") return

    // tandai pending task
    PreferenceManager.getDefaultSharedPreferences(ctx)
      .edit().putBoolean("pending_enable_flag", true).apply()

    // buka Chrome langsung ke flags
    val url = "chrome://flags/#enable-command-line-on-non-rooted-devices"
    val i = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
      setPackage("com.android.chrome")
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { ctx.startActivity(i) }
  }
}
