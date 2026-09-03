package com.anantmittal.cartbridge

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.anantmittal.cartbridge.data.CartDatabase
import com.anantmittal.cartbridge.domain.HybridExtractionRepository
import com.anantmittal.cartbridge.ui.CartScreen
import com.anantmittal.cartbridge.ui.MainViewModel
import com.anantmittal.cartbridge.ui.MainViewModelFactory
import com.anantmittal.cartbridge.ui.theme.CartBridgeTheme
import com.anantmittal.cartbridge.utils.ImageDownsampler
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels {
        MainViewModelFactory(
            HybridExtractionRepository(
                FirebaseRemoteConfig.getInstance(),
                Firebase.ai(backend = GenerativeBackend.googleAI())
            ),
            CartDatabase.getDatabase(this).cartDao()
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        handleIntent(intent)

        setContent {
            CartBridgeTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    CartScreen(viewModel = viewModel)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        if (intent.action == Intent.ACTION_SEND && intent.type?.startsWith("image/") == true) {
            val imageUri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
            imageUri?.let { uri ->
                lifecycleScope.launch {
                    val bitmap = ImageDownsampler.downsampleImage(this@MainActivity, uri)
                    if (bitmap != null) {
                        viewModel.processImage(bitmap)
                    } else {
                        Log.e("MainActivity", "Failed to downsample image")
                    }
                }
            }
        }
    }
}