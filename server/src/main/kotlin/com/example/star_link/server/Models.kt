package com.example.star_link.server

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.intOrNull
import java.util.Collections

data class TableSession(
    val orders: MutableList<JsonObject> = Collections.synchronizedList(mutableListOf()),
    val payments: MutableList<JsonObject> = Collections.synchronizedList(mutableListOf()),
    var paidAmount: Int = 0
) {
    val totalAmount: Int
        get() = orders.sumOf { 
            it["total"]?.jsonPrimitive?.intOrNull ?: 
            it["totalAmount"]?.jsonPrimitive?.intOrNull ?: 
            it["amount"]?.jsonPrimitive?.intOrNull ?: 0 
        }
}
