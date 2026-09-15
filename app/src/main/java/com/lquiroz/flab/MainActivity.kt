package com.lquiroz.flab

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import com.lquiroz.flab.fold.FoldSnapshot
import com.lquiroz.flab.fold.rememberWindowLayoutInfo
import com.lquiroz.flab.ui.home.FLabHomeScreen
import com.lquiroz.flab.ui.theme.FLabTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FLabTheme {
                val layoutInfo by rememberWindowLayoutInfo()
                FLabHomeScreen(FoldSnapshot.from(layoutInfo))
            }
        }
    }
}
