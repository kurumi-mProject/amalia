package com.my.amali

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.my.amali.ui.assistant.AssistantScreen
import com.my.amali.ui.theme.AmaliaTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AmaliaTheme {
                AssistantScreen()
            }
        }
    }
}
