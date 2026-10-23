package com.convx.migration.androidhost

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.convx.migration.shared.ProofScreen
import com.convx.migration.shared.proofState

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ProofScreen(proofState("Android"))
        }
    }
}
