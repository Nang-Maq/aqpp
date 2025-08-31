package com.yourapp.flagenabler

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    // buka setting Accessibility biar gampang diaktifin
    startActivity(Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS))
    finish()
  }
}
