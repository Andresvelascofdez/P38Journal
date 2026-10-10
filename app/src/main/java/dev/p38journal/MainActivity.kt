package dev.p38journal

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.mutableStateOf
import dev.p38journal.ui.JournalApp
import dev.p38journal.ui.JournalTheme
import dev.p38journal.ui.JournalViewModel

class MainActivity : ComponentActivity() {
    private val model by viewModels<JournalViewModel>()
    private val targetRule = mutableStateOf<String?>(null)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        targetRule.value = intent.getStringExtra("ruleId")
        setContent { JournalTheme { JournalApp(model, targetRule.value) { targetRule.value = null; intent.removeExtra("ruleId") } } }
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        targetRule.value = intent.getStringExtra("ruleId")
    }
    override fun onResume() {
        super.onResume()
        model.resume()
    }
}
