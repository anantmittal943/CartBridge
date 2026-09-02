package com.anantmittal.cartbridge.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.anantmittal.cartbridge.data.CartItem
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CartScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val items by viewModel.allItems.collectAsState()
    val isProcessing by viewModel.isProcessing.collectAsState()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cart Bridge") },
                actions = {
                    IconButton(onClick = { viewModel.clearHistory() }) {
                        Text("Clear")
                    }
                }
            )
        },
        modifier = modifier
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (items.isEmpty() && !isProcessing) {
                Text(
                    text = "Share a cart screenshot to this app to extract items.",
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(items) { item ->
                        CartItemRow(item = item, context = context)
                        HorizontalDivider()
                    }
                }
            }
            
            if (isProcessing) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
    }
}

@Composable
fun CartItemRow(item: CartItem, context: Context) {
    Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
        Text(
            text = "${item.qty}x ${item.name}",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Added: ${formatDate(item.timestamp)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            StoreButton("Amazon", context, "https://www.amazon.in/s?k=", item.name)
            StoreButton("Blinkit", context, "https://blinkit.com/s/?q=", item.name)
            StoreButton("Zepto", context, "https://www.zeptonow.com/search?q=", item.name)
        }
    }
}

@Composable
fun StoreButton(storeName: String, context: Context, baseUrl: String, query: String) {
    Button(
        onClick = {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(baseUrl + encodedQuery)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        },
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(text = storeName, style = MaterialTheme.typography.labelSmall)
    }
}

private fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
