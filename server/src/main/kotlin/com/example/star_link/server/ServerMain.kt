package com.example.star_link.server

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.server.http.content.*
import io.ktor.server.response.*
import io.ktor.websocket.*
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.io.File
import java.net.Inet4Address
import java.net.NetworkInterface
import com.example.star_link.server.cloud.CloudSync
import com.example.star_link.server.routes.configureApiRoutes
import com.example.star_link.server.websockets.configureWebSocketRoutes

fun getLocalIpAddress(): String {
    try {
        val interfaces = NetworkInterface.getNetworkInterfaces()
        val ipList = mutableListOf<String>()
        while (interfaces.hasMoreElements()) {
            val iface = interfaces.nextElement()
            if (iface.isLoopback || !iface.isUp) continue
            val isWifi = iface.name.contains("en0")
            val addresses = iface.inetAddresses
            while (addresses.hasMoreElements()) {
                val addr = addresses.nextElement()
                if (addr is Inet4Address && !addr.isLoopbackAddress) {
                    val host = addr.hostAddress
                    if (host.startsWith("192.168.") || host.startsWith("10.") || host.startsWith("172.")) {
                        if (isWifi) return host
                        ipList.add(host)
                    }
                }
            }
        }
        return ipList.firstOrNull() ?: "172.30.1.13"
    } catch (_: Exception) {}
    return "172.30.1.13"
}

fun getCurrentWifiSsid(): String {
    return try {
        val nsResult = Runtime.getRuntime().exec("networksetup -getairportnetwork en0").inputStream.bufferedReader().readText()
        val nsSsid = nsResult.substringAfter("Current Wi-Fi Network: ").trim()
        if (nsSsid.isNotBlank() && !nsSsid.contains("error") && !nsSsid.contains("not associated")) {
            nsSsid
        } else {
            "감지중"
        }
    } catch (_: Exception) {
        "감지중"
    }
}

fun main() {
    DatabaseFactory.init()

    val storeConfigFile = findFile("store_config.json") ?: File("store_config.json")
    var storeConfig: JsonObject? = if (storeConfigFile.exists()) {
        try { Json.decodeFromString<JsonObject>(storeConfigFile.readText()) } catch (_: Exception) { null }
    } else null

    var tunnelUrl = storeConfig?.get("tunnelUrl")?.jsonPrimitive?.content ?: ""
    if (tunnelUrl.isBlank() || tunnelUrl.contains("Binary file") || !tunnelUrl.startsWith("https://")) {
        val cloudflaredLog = File("cloudflared.log").takeIf { it.exists() } ?: File("../cloudflared.log")
        if (cloudflaredLog.exists()) {
            try {
                val logText = cloudflaredLog.readText()
                val regex = Regex("https://[a-zA-Z0-9.-]+\\.trycloudflare\\.com")
                val match = regex.find(logText)
                if (match != null) {
                    tunnelUrl = match.value
                    println("🌐 [자동 추출] cloudflared.log에서 5G 터널 주소를 성공적으로 복구했습니다: $tunnelUrl")
                }
            } catch (e: Exception) {
                println("⚠️ [터널 URL 복구 실패]: ${e.message}")
            }
        }
    }

    if (storeConfig != null && storeConfig.get("tunnelUrl")?.jsonPrimitive?.content != tunnelUrl) {
        storeConfig = buildJsonObject {
            storeConfig.forEach { (k, v) ->
                if (k == "tunnelUrl") put(k, JsonPrimitive(tunnelUrl)) else put(k, v)
            }
        }
        storeConfigFile.writeText(storeConfig.toString())
    }

    val currentLocalIp = getLocalIpAddress()
    var lastStoreConfig: JsonObject? = storeConfig

    fun updateLocalConfig(newConfig: JsonObject) {
        lastStoreConfig = newConfig
        storeConfigFile.writeText(newConfig.toString())
    }

    if (storeConfig == null || storeConfig.get("storeIp")?.jsonPrimitive?.content != currentLocalIp) {
        val existingTunnelUrl = storeConfig?.get("tunnelUrl")?.jsonPrimitive?.content ?: ""
        val existingTableCount = storeConfig?.get("tableCount")?.jsonPrimitive?.intOrNull ?: 20
        val agentId = storeConfig?.get("agentId")?.jsonPrimitive?.content ?: "A101"
        val storeId = storeConfig?.get("storeId")?.jsonPrimitive?.content ?: "S-A101-001"
        val storeName = storeConfig?.get("storeName")?.jsonPrimitive?.content ?: "현장 테스트 매장"
        val storeType = storeConfig?.get("storeType")?.jsonPrimitive?.content ?: "FOOD"
        val pgKey = storeConfig?.get("pgClientKey")?.jsonPrimitive?.content ?: "test_ck_D5Ge2x3M8pv991005b6VgL1N0Ene"
        val payMode = storeConfig?.get("paymentMode")?.jsonPrimitive?.content ?: "POST_PAY"

        storeConfig = buildJsonObject {
            put("agentId", agentId)
            put("storeId", storeId)
            put("storeName", storeName)
            put("storeType", storeType)
            put("storeIp", currentLocalIp)
            put("tunnelUrl", existingTunnelUrl)
            put("pgClientKey", pgKey)
            put("paymentMode", payMode)
            put("tableCount", existingTableCount)
            put("isSetupComplete", true)
            put("setupPin", "1234")
            put("wifiSsid", getCurrentWifiSsid())
            put("wifiPw", "starlink1234")
        }
        storeConfigFile.writeText(storeConfig.toString())
        lastStoreConfig = storeConfig
    }

    // CloudSync 루프 시작
    CloudSync.startSyncLoop { lastStoreConfig }

    val kitchenSessions = Collections.newSetFromMap(ConcurrentHashMap<DefaultWebSocketServerSession, Boolean>())
    val customerSessions = ConcurrentHashMap<Int, MutableSet<DefaultWebSocketServerSession>>()
    val tableSessions = ConcurrentHashMap<Int, TableSession>()

    suspend fun broadcastMenuUpdate() {
        val signal = buildJsonObject { put("type", "MENU_UPDATED") }.toString()
        customerSessions.values.flatten().forEach { 
            try { it.send(Frame.Text(signal)) } catch(_: Exception) {}
        }
        kitchenSessions.forEach { 
            try { it.send(Frame.Text(signal)) } catch(_: Exception) {}
        }
    }

    embeddedServer(Netty, port = 8888, host = "0.0.0.0") {
        install(WebSockets) {
            pingPeriodMillis = 15_000
            timeoutMillis = 15_000
            maxFrameSize = Long.MAX_VALUE
            masking = false
        }

        routing {
            intercept(ApplicationCallPipeline.Call) {
                call.response.header("Access-Control-Allow-Origin", "*")
                call.response.header("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS")
                call.response.header("Access-Control-Allow-Headers", "Content-Type, Authorization")
                if (call.request.local.method == HttpMethod.Options) {
                    call.respond(HttpStatusCode.OK)
                    return@intercept
                }
            }

            route("/join") {
                get { call.respondFile(findFile("cloud_landing.html")!!) }
            }

            route("/kitchen") {
                get { 
                    val type = System.getenv("STORE_MODE")?.lowercase() ?: "cafe"
                    call.respondFile(findFile("$type/kitchen_dashboard.html") ?: findFile("kitchen_dashboard.html")!!) 
                }
            }
            route("/setup") {
                get { call.respondFile(findFile("setup.html")!!) }
            }
            route("/menu") {
                get { 
                    val type = System.getenv("STORE_MODE")?.lowercase() ?: "cafe"
                    call.respondFile(findFile("$type/customer_menu.html") ?: findFile("customer_menu.html")!!) 
                }
            }
            route("/qr_board") {
                get { call.respondFile(findFile("table_qr_dashboard.html")!!) }
                head { call.respondFile(findFile("table_qr_dashboard.html")!!) }
            }
            route("/hq_menu_composer") {
                get { call.respondFile(findFile("hq_menu_composer.html")!!) }
                head { call.respondFile(findFile("hq_menu_composer.html")!!) }
            }

            // 모듈화된 API 라우팅 설정
            configureApiRoutes(
                getStoreConfig = { lastStoreConfig },
                updateStoreConfig = { newConfig -> updateLocalConfig(newConfig) },
                getCurrentLocalIp = { currentLocalIp },
                getCurrentWifiSsid = { getCurrentWifiSsid() },
                getTableSessions = { tableSessions },
                removeTableSession = { tNum -> tableSessions.remove(tNum) },
                getKitchenSessions = { kitchenSessions },
                syncFirebase = { CloudSync.syncWithFirebase(lastStoreConfig) },
                broadcastMenuUpdate = { broadcastMenuUpdate() }
            )

            // 모듈화된 웹소켓 라우팅 설정
            configureWebSocketRoutes(
                kitchenSessions = kitchenSessions,
                customerSessions = customerSessions,
                broadcastMenuUpdate = { broadcastMenuUpdate() }
            )
        }
    }.start(wait = true)
}

fun findFile(name: String): File? {
    val projectRoot = "/Users/spacerabbit/AndroidStudioProjects/starlink"
    val paths = listOf(
        File(projectRoot, "server/public/$name"),
        File(projectRoot, "$name"),
        File(projectRoot, "server/$name"),
        File(projectRoot, "public/$name")
    )
    for (path in paths) {
        if (path.exists()) return path
    }
    return null
}
