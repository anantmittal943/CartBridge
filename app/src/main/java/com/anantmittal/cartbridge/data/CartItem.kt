package com.anantmittal.cartbridge.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Entity(tableName = "cart_items")
@Serializable
data class CartItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val qty: Int,
    val timestamp: Long = System.currentTimeMillis()
)
