package com.example.star_link.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.readText
import io.ktor.websocket.close
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.json.*
import java.net.Inet4Address
import java.net.NetworkInterface
import android.os.Build

/**
 * [상용 양방향 통신 클라이언트]
 * 서버와 상시 연결을 유지하며 주문을 보내고, 주방의 알림(영수증)을 받습니다.
 */
object KtorServerSimulator {

    private val client = HttpClient(CIO) {
        install(WebSockets)
    }
    
    private val clientScope = CoroutineScope(Dispatchers.IO)
    
    // [📍 서버 IP 동적 관리] 초기값: 에뮬레이터 여부에 따라 적절한 로컬 주소로 시작
    var serverHost: String by mutableStateOf(if (isEmulator()) "10.0.2.2" else detectLocalIp())
        private set

    private fun isEmulator(): Boolean {
        return Build.FINGERPRINT.startsWith("generic")
                || Build.FINGERPRINT.startsWith("unknown")
                || Build.MODEL.contains("google_sdk")
                || Build.MODEL.contains("Emulator")
                || Build.MODEL.contains("Android SDK built for x86")
                || Build.MANUFACTURER.contains("Genymotion")
                || (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic"))
                || "google_sdk" == Build.PRODUCT
    }

    var currentStoreName: String by mutableStateOf("서버 정보 확인 중...")
        private set

    var syncStatus: String by mutableStateOf("동기화 준비 중")
        private set

    var tunnelUrl: String? by mutableStateOf(null)
        private set

    private fun detectLocalIp(): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (iface.isLoopback || !iface.isUp) continue
                val addresses = iface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        val host = addr.hostAddress
                        // [필터링] 에뮬레이터 내부 IP(10.0.x.x)는 제외하고 실제 WiFi IP만 감지
                        if (host != null && (host.startsWith("192.168.") || host.startsWith("172."))) {
                            return host
                        }
                    }
                }
            }
        } catch (e: Exception) {
            println("⚠️ IP 감지 오류: ${e.message}")
        }
        return "172.30.1.13" // 기본값
    }
    
    val hostPcUrl: String
        get() = if (serverHost.contains("trycloudflare.com")) {
            "wss://$serverHost/place-order"
        } else {
            "ws://$serverHost:8888/place-order"
        }

    val webMenuBaseUrl: String
        get() = if (serverHost.contains("trycloudflare.com")) {
            "https://$serverHost/menu"
        } else {
            "http://$serverHost:8888/menu"
        }

    // 주방 알림을 UI로 전달하기 위한 이벤트 스트림
    private val _serverEvents = MutableSharedFlow<String>()
    val serverEvents: SharedFlow<String> = _serverEvents.asSharedFlow()

    private var activeSession: WebSocketSession? = null
    private var shouldReconnect = true

    init {
        // 앱 시작 시 즉시 동기화 루프 가동
        clientScope.launch {
            while (isActive) {
                syncWithCloud()
                delay(15000L) // 15초마다 체크 (매장 확장 시 트래픽 부담 완화)
            }
        }
    }

    /**
     * [강제 동기화] 클라우드(Firebase)에서 최신 매장 정보를 가져와 즉시 적용합니다.
     */
    fun startCloudDiscovery() {
        clientScope.launch {
            syncWithCloud()
        }
    }

    suspend fun syncWithCloud() {
        try {
            // [확장성 아키텍처] 특정 storeId에 얽매이지 않고, 클라우드에 등록된 모든 매장(stores.json)을 조회하여
            // 가장 최근에 활성화된(Online) 매장의 5G 터널 및 IP 정보를 자동 탐색합니다. (매장 수 증가 완벽 대응)
            val firebaseDbUrl = "https://qrrolink-order-web-default-rtdb.asia-southeast1.firebasedatabase.app/stores.json"
            
            val response: HttpResponse = client.get(firebaseDbUrl)
            if (response.status == HttpStatusCode.OK) {
                val body = response.bodyAsText()
                val jsonElement = Json.parseToJsonElement(body)
                val storesObj = jsonElement.jsonObject
                
                var bestStore: JsonObject? = null
                var latestTime = 0L
                
                for ((_, storeVal) in storesObj) {
                    val storeObj = storeVal as? JsonObject ?: continue
                    val lastSeen = storeObj["lastSeen"]?.jsonPrimitive?.longOrNull ?: 0L
                    if (lastSeen >= latestTime) {
                        latestTime = lastSeen
                        bestStore = storeObj
                    }
                }
                
                bestStore?.let { store ->
                    val cloudIp = store["localIp"]?.jsonPrimitive?.content
                    val cloudStoreName = store["storeName"]?.jsonPrimitive?.content
                    val cloudTunnelUrl = store["tunnelUrl"]?.jsonPrimitive?.content
                    
                    withContext(Dispatchers.Main) {
                        if (cloudStoreName != null) {
                            currentStoreName = cloudStoreName
                        }

                        // [1순위] 매장 로컬 와이파이 IP (동일 네트워크 고속 통신 우선)
                        if (cloudIp != null && cloudIp != serverHost && !cloudIp.startsWith("10.0.") && !cloudIp.startsWith("127.")) {
                            updateServerHost(cloudIp)
                            syncStatus = if (isEmulator()) "에뮬레이터(맥북) 연결됨" else "매장 와이파이 연결됨 ($cloudStoreName)"
                        } 
                        // [2순위] 로컬 IP가 안 되거나 외부 5G망인 경우에만 클라우드 터널 주소 사용
                        else if (cloudTunnelUrl != null && cloudTunnelUrl.startsWith("https://")) {
                            val tunnelOnly = cloudTunnelUrl.replace("https://", "")
                            if (serverHost != tunnelOnly) {
                                updateServerHost(tunnelOnly)
                            }
                            tunnelUrl = tunnelOnly
                            syncStatus = "5G 하이패스 연결됨 ($cloudStoreName)"
                        } else {
                            syncStatus = if (isEmulator()) "에뮬레이터 대기 중" else "서버 대기 중 (5G/WiFi)"
                        }
                    }
                }
            } else {
                withContext(Dispatchers.Main) { syncStatus = "클라우드 응답 없음: ${response.status}" }
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) { syncStatus = "연결 확인 필요 (${e.message})" }
        }
    }

    /**
     * 동적으로 서버 IP를 변경하고 재연동합니다.
     */
    fun updateServerHost(newIp: String) {
        if (newIp.isBlank() || newIp == serverHost) return
        serverHost = newIp.trim()
        println("📡 [IP변경] 새로운 서버 IP 적용: $serverHost")
        disconnect()
        connectToServer()
    }

    /**
     * 서버와 영구적인 양방향 채널을 개설합니다. (앱 시작 시 호출)
     */
    fun connectToServer() {
        shouldReconnect = true
        clientScope.launch {
            while (shouldReconnect && isActive) {
                try {
                    println("🔄 [연결시도] $hostPcUrl 연결 중...")
                    client.webSocket(hostPcUrl) {
                        activeSession = this
                        println("✅ [연결성공] $serverHost 서버와 실시간 채널이 동기화되었습니다.")
                        
                        incoming.consumeEach { frame ->
                            if (frame is Frame.Text) {
                                val response = frame.readText()
                                if (response.contains("PAYMENT_COMPLETED")) {
                                    _serverEvents.emit("RECEIPT_POPUP")
                                } else if (response.contains("SERVICE_TERMINATED")) {
                                    _serverEvents.emit("TERMINATE_UI")
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    activeSession = null
                    println("❌ [연결실패/끊김] 3초 후 재접속 시도... (${e.message})")
                }
                
                if (shouldReconnect) {
                    delay(3000)
                }
            }
        }
    }

    /**
     * 서버와의 연결을 명시적으로 종료합니다.
     */
    fun disconnect() {
        shouldReconnect = false
        clientScope.launch {
            try {
                activeSession?.let {
                    it.close()
                    activeSession = null
                    println("🔌 [연결해제] 서버와의 세션이 종료되었습니다.")
                }
            } catch (e: Exception) {
                println("❌ 연결 해제 실패: ${e.message}")
            }
        }
    }

    /**
     * 특정 테이블 세션으로 등록합니다.
     */
    fun joinTable(tableNumber: Int) {
        val jsonPayload = """
            {
                "type": "JOIN",
                "tableNumber": $tableNumber
            }
        """.trimIndent()
        
        clientScope.launch {
            try {
                activeSession?.send(Frame.Text(jsonPayload))
                println("📍 [세션등록] $tableNumber 번 테이블로 서버에 등록되었습니다.")
            } catch (e: Exception) {
                println("❌ 세션 등록 실패: ${e.message}")
            }
        }
    }

    /**
     * 메뉴 주문 데이터를 서버로 즉시 전송합니다.
     */
    fun broadcastOrderToKitchen(tableNumber: Int, itemsSummary: String, totalAmount: Int) {
        val jsonPayload = """
            {
                "type": "ORDER_RECEIVED",
                "tableNumber": $tableNumber,
                "totalAmount": $totalAmount,
                "items": [
                    $itemsSummary
                ]
            }
        """.trimIndent()

        clientScope.launch {
            try {
                activeSession?.send(Frame.Text(jsonPayload)) ?: run {
                    client.webSocket(hostPcUrl) {
                        send(Frame.Text(jsonPayload))
                    }
                }
                println("✅ 주문 전송 완료!")
            } catch (e: Exception) {
                println("❌ 주문 전송 실패: ${e.message}")
            }
        }
    }

    fun startLocalServer() {}
}
