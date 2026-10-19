package com.example.star_link.server.cloud

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import java.io.File
import com.example.star_link.server.findFile
import com.example.star_link.server.getLocalIpAddress
import com.example.star_link.server.getCurrentWifiSsid

object CloudSync {
    private val cloudClient = HttpClient(CIO)
    private val serverScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun getLatestTunnelUrl(): String {
        val logFiles = listOf(File("cloudflared.log"), File("../cloudflared.log"), File("../../cloudflared.log"))
        for (logFile in logFiles) {
            if (logFile.exists()) {
                try {
                    val text = logFile.readText()
                    val regex = Regex("https://[a-zA-Z0-9.-]+\\.trycloudflare\\.com")
                    val matches = regex.findAll(text).toList()
                    if (matches.isNotEmpty()) {
                        return matches.last().value
                    }
                } catch (e: Exception) {}
            }
        }
        return ""
    }

    suspend fun syncWithFirebase(lastStoreConfig: JsonObject?) {
        try {
            val freshConfigFile = findFile("store_config.json")
            val freshConfig = if (freshConfigFile?.exists() == true) {
                try { Json.decodeFromString<JsonObject>(freshConfigFile.readText()) } catch (e: Exception) { lastStoreConfig }
            } else { lastStoreConfig }
            
            val currentConfig = freshConfig ?: return
            val sId = currentConfig["storeId"]?.jsonPrimitive?.content ?: "S-A101-001"
            
            val savedSsid = currentConfig["wifiSsid"]?.jsonPrimitive?.content ?: ""
            val currentSsid = if (savedSsid.isNotBlank() && !savedSsid.contains("감지중")) savedSsid else getCurrentWifiSsid()
            
            val localIp = getLocalIpAddress()
            val fileTunnelUrl = currentConfig["tunnelUrl"]?.jsonPrimitive?.content ?: ""
            val liveLogTunnelUrl = getLatestTunnelUrl()
            val tunnelUrl = if (liveLogTunnelUrl.isNotBlank()) liveLogTunnelUrl else fileTunnelUrl
            
            val payload = buildJsonObject {
                put("localIp", localIp)
                put("lastSeen", System.currentTimeMillis())
                put("storeName", currentConfig["storeName"] ?: JsonNull)
                put("wifiSsid", currentSsid)
                put("wifiPw", currentConfig["wifiPw"] ?: JsonNull)
                put("tunnelUrl", tunnelUrl)
                put("status", "ONLINE")
            }
            
            val firebaseDbUrl = "https://qrrolink-order-web-default-rtdb.asia-southeast1.firebasedatabase.app/stores/$sId.json"
            val response = cloudClient.put(firebaseDbUrl) {
                contentType(ContentType.Application.Json)
                setBody(payload.toString())
            }
            if (response.status == HttpStatusCode.OK) {
                println("\n******************************************************************")
                println("★★★★★★★★ [Firebase 클라우드 관제 동기화 성공] ★★★★★★★★")
                println("  매장명: ${currentConfig["storeName"]?.jsonPrimitive?.content ?: "알 수 없음"}")
                println("  SSID: $currentSsid | IP: $localIp")
                if (tunnelUrl.isNotBlank()) {
                    println("  🌐 5G 보안 터널 활성화: $tunnelUrl")
                }
                println("******************************************************************\n")
            }
        } catch (e: Exception) {
            println("⚠️ [Firebase] 동기화 실패: ${e.message}")
        }
    }

    fun startSyncLoop(getConfig: () -> JsonObject?) {
        serverScope.launch {
            syncWithFirebase(getConfig())
            while (isActive) {
                delay(180000L) // 3분마다 보고
                syncWithFirebase(getConfig())
            }
        }
    }
}
