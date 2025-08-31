package com.yourapp.flagenabler

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.SystemClock
import androidx.preference.PreferenceManager    // <— ganti ini
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.*
import java.util.regex.Pattern

class FlagEnablerService : AccessibilityService() {

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

  override fun onServiceConnected() {
    super.onServiceConnected()
  }

  override fun onAccessibilityEvent(event: AccessibilityEvent?) {
    val prefs = PreferenceManager.getDefaultSharedPreferences(this)
    if (!prefs.getBoolean("pending_enable_flag", false)) return
    val pkg = event?.packageName?.toString() ?: return
    if (pkg != "com.android.chrome") return

    prefs.edit().putBoolean("pending_enable_flag", false).apply()
    scope.launch { runFlow() }
  }

  override fun onInterrupt() {}

  private suspend fun runFlow() {
    // onboarding (best-effort)
    maybeTapText("Accept & continue", 1200)
    maybeTapText("ACCEPT", 600)
    maybeTapText("No thanks", 600)
    maybeTapText("Skip", 600)
    maybeTapText("Set as default", 600)

    // scroll sampai judul flag ketemu
    scrollUntilVisible("Enable command line on non-rooted devices", 12000)

    // tap dropdown terdekat
    val title = findNodeByText("Enable command line on non-rooted devices") ?: return
    val dropdown = findNearestByTexts(title, arrayOf("Default", "Disabled", "Enabled")) ?: return
    clickNode(dropdown)
    delay(300)

    // pilih Enabled
    waitNodeAndClick("Enabled", 4000)

    // Relaunch/Restart
    if (!maybeTapText("Relaunch", 3000)) {
      waitNodeAndClick("Restart", 5000)
    }
  }

  // ---------- helpers ----------
  private suspend fun scrollUntilVisible(text: String, timeoutMs: Long) {
    val end = SystemClock.uptimeMillis() + timeoutMs
    while (SystemClock.uptimeMillis() < end) {
      if (findNodeByText(text) != null) return
      val scrollers = findAll { it.isScrollable }
      if (scrollers.isNotEmpty()) scrollers.first().performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
      else swipeUp()
      delay(300)
    }
  }

  private suspend fun maybeTapText(text: String, waitMs: Long = 800): Boolean {
    val node = waitNode(text, waitMs) ?: return false
    clickNode(node); delay(150)
    return true
  }

  private suspend fun waitNodeAndClick(text: String, timeoutMs: Long): Boolean {
    val n = waitNode(text, timeoutMs) ?: return false
    clickNode(n); delay(150)
    return true
  }

  private suspend fun waitNode(text: String, timeoutMs: Long): AccessibilityNodeInfo? {
    val end = SystemClock.uptimeMillis() + timeoutMs
    while (SystemClock.uptimeMillis() < end) {
      findNodeByText(text)?.let { return it }
      delay(180)
    }
    return null
  }

  private fun findNodeByText(text: String): AccessibilityNodeInfo? {
    val root = rootInActiveWindow ?: return null
    val rx = Pattern.compile(Pattern.quote(text), Pattern.CASE_INSENSITIVE)
    return bfsFind(root) { n ->
      val t = n.text?.toString() ?: n.contentDescription?.toString()
      t?.let { rx.matcher(it).find() } == true
    }
  }

  private fun findNearestByTexts(anchor: AccessibilityNodeInfo, texts: Array<String>): AccessibilityNodeInfo? {
    val root = rootInActiveWindow ?: return null
    val regexes = texts.map { Pattern.compile(Pattern.quote(it), Pattern.CASE_INSENSITIVE) }
    val cands = mutableListOf<AccessibilityNodeInfo>()
    bfs(root) { n ->
      val t = n.text?.toString() ?: n.contentDescription?.toString() ?: ""
      if (regexes.any { it.matcher(t).find() }) cands.add(n)
    }
    if (cands.isEmpty()) return null
    val ar = Rect().also { anchor.getBoundsInScreen(it) }
    return cands.minByOrNull {
      val r = Rect().also { n -> it.getBoundsInScreen(n) }
      kotlin.math.abs(r.centerY() - ar.centerY()).toDouble()
    }
  }

  private fun clickNode(node: AccessibilityNodeInfo) {
    var cur: AccessibilityNodeInfo? = node
    repeat(6) {
      if (cur == null) return@repeat
      if (cur!!.isClickable) {
        cur!!.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        return
      }
      cur = cur!!.parent
    }
    val r = Rect(); node.getBoundsInScreen(r)
    tap(r.centerX().toFloat(), r.centerY().toFloat())
  }

  private fun tap(x: Float, y: Float) {
    val path = Path().apply { moveTo(x, y) }
    val stroke = GestureDescription.StrokeDescription(path, 0, 60)
    dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), null, null)
  }

  private fun swipeUp() {
    val dm = resources.displayMetrics
    val x = dm.widthPixels * 0.5f
    val y1 = dm.heightPixels * 0.72f
    val y2 = dm.heightPixels * 0.28f
    val p = Path().apply { moveTo(x, y1); lineTo(x, y2) }
    dispatchGesture(GestureDescription.Builder().addStroke(
      GestureDescription.StrokeDescription(p, 0, 220)
    ).build(), null, null)
  }

  private fun bfsFind(root: AccessibilityNodeInfo, pred: (AccessibilityNodeInfo)->Boolean): AccessibilityNodeInfo? {
    val q = ArrayDeque<AccessibilityNodeInfo>()
    q.add(root)
    while (q.isNotEmpty()) {
      val n = q.removeFirst()
      try {
        if (pred(n)) return n
        for (i in 0 until n.childCount) n.getChild(i)?.let { q.add(it) }
      } catch (_: Exception) {}
    }
    return null
  }

  private fun bfs(root: AccessibilityNodeInfo, fn: (AccessibilityNodeInfo)->Unit) {
    val q = ArrayDeque<AccessibilityNodeInfo>()
    q.add(root)
    while (q.isNotEmpty()) {
      val n = q.removeFirst()
      try {
        fn(n)
        for (i in 0 until n.childCount) n.getChild(i)?.let { q.add(it) }
      } catch (_: Exception) {}
    }
  }
}
