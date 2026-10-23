package com.convx.migration.androidhost

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.convx.migration.shared.ProofScreen
import com.convx.migration.shared.ProofPlatform
import com.convx.migration.shared.sharedKoinModule
import org.koin.compose.KoinApplication
import org.koin.dsl.module

private val androidHostModule = module {
    single { ProofPlatform("Android") }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            KoinApplication(application = { modules(sharedKoinModule, androidHostModule) }) {
                ProofScreen()
            }
        }
    }
}
