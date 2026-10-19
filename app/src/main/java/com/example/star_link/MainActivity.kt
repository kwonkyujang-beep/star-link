package com.example.star_link

import android.os.Bundle
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.star_link.data.KtorServerSimulator
import com.example.star_link.data.OrderManager
import com.example.star_link.ui.theme.StarlinkTheme
import com.example.star_link.BuildConfig
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import java.net.Inet4Address
import java.net.NetworkInterface

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        KtorServerSimulator.connectToServer()
        syncStoreProfileAndMenus(KtorServerSimulator.serverHost)

        setContent {
            StarlinkTheme {
                QRScannerSimulationScreen()
            }
        }
    }

    fun syncStoreProfileAndMenus(serverHost: String) {
        val flavor = try {
            BuildConfig.FLAVOR
        } catch (_: Exception) {
            "restaurant"
        }
        val (storeName, menus) = when (flavor) {
            "cafe" -> Pair(
                "스타링크 감성 카페 (호계점)",
                listOf(
                    mapOf("id" to "c1", "name" to "아메리카노", "price" to 4500, "category" to "커피", "img" to "https://images.unsplash.com/photo-1514432324607-a09d9b4aefdd?auto=format&fit=crop&w=600&q=80"),
                    mapOf("id" to "c2", "name" to "카페 라떼", "price" to 5000, "category" to "커피", "img" to "https://images.unsplash.com/photo-1534778101976-62847782c213?auto=format&fit=crop&w=600&q=80"),
                    mapOf("id" to "c3", "name" to "달콤 딸기 스무디", "price" to 6500, "category" to "논커피", "img" to "https://images.unsplash.com/photo-1556679343-c7306c1976bc?auto=format&fit=crop&w=600&q=80"),
                    mapOf("id" to "c4", "name" to "제주 감귤 에이드", "price" to 6000, "category" to "에이드/티", "img" to "https://images.unsplash.com/photo-1513558161293-cdaf765ed2fd?auto=format&fit=crop&w=600&q=80"),
                    mapOf("id" to "c5", "name" to "뉴욕 치즈 케이크", "price" to 7000, "category" to "디저트", "img" to "https://images.unsplash.com/photo-1578985545062-69928b1d9587?auto=format&fit=crop&w=600&q=80")
                )
            )
            "pub" -> Pair(
                "스타링크 수제맥주 & 펍",
                listOf(
                    mapOf("id" to "p1", "name" to "생맥주 500cc", "price" to 5000, "category" to "주류", "img" to "https://images.unsplash.com/photo-1535958636474-b021ee887b13?auto=format&fit=crop&w=600&q=80"),
                    mapOf("id" to "p2", "name" to "하이볼", "price" to 8000, "category" to "주류", "img" to "https://images.unsplash.com/photo-1514362545857-3bc16c4c7d1b?auto=format&fit=crop&w=600&q=80"),
                    mapOf("id" to "p3", "name" to "참소주 / 진로", "price" to 5000, "category" to "주류", "img" to "https://images.unsplash.com/photo-1563227812-0ea4c22e6cc8?auto=format&fit=crop&w=600&q=80"),
                    mapOf("id" to "p4", "name" to "바삭 후라이드 치킨", "price" to 20000, "category" to "안주류", "img" to "https://images.unsplash.com/photo-1626082927389-6cd097cdc6ec?auto=format&fit=crop&w=600&q=80"),
                    mapOf("id" to "p5", "name" to "매콤 골뱅이무침", "price" to 22000, "category" to "안주류", "img" to "https://images.unsplash.com/photo-1555396273-367ea4eb4db5?auto=format&fit=crop&w=600&q=80"),
                    mapOf("id" to "p6", "name" to "먹태구이", "price" to 18000, "category" to "안주류", "img" to "https://images.unsplash.com/photo-1544025162-d76694265947?auto=format&fit=crop&w=600&q=80")
                )
            )
            else -> Pair(
                "스타링크 명가 한식당",
                listOf(
                    mapOf("id" to "r1", "name" to "얼큰 차돌박이 해장국", "price" to 11000, "category" to "식사류", "img" to "https://images.unsplash.com/photo-1627308595229-7830a5c91f9f?auto=format&fit=crop&w=600&q=80"),
                    mapOf("id" to "r2", "name" to "묵은지 김치찜", "price" to 18000, "category" to "식사류", "img" to "https://images.unsplash.com/photo-1569058242253-92a9c71f9867?auto=format&fit=crop&w=600&q=80"),
                    mapOf("id" to "r3", "name" to "전통 불고기 전골", "price" to 32000, "category" to "요리류", "img" to "https://images.unsplash.com/photo-1525755662778-989d0524087e?auto=format&fit=crop&w=600&q=80"),
                    mapOf("id" to "r4", "name" to "매콤 제육볶음", "price" to 25000, "category" to "요리류", "img" to "https://images.unsplash.com/photo-1541696432-82c6da8ce7bf?auto=format&fit=crop&w=600&q=80")
                )
            )
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val client = HttpClient(CIO)
                val host = if (serverHost.contains("trycloudflare.com")) serverHost else "$serverHost:8888"
                val scheme = if (serverHost.contains("trycloudflare.com")) "https" else "http"
                
                val jsonArray = JsonArray(menus.map { m ->
                    buildJsonObject {
                        m.forEach { (k, v) ->
                            if (v is Int) put(k, v) else put(k, v.toString())
                        }
                    }
                })
                client.post("$scheme://$host/api/menus/bulk") {
                    setBody(jsonArray.toString())
                    contentType(ContentType.Application.Json)
                }
                client.close()
                println("✨ [업종 자동 동기화 성공] $storeName 메뉴가 서버에 반영되었습니다.")
            } catch (e: Exception) {
                println("⚠️ 업종 자동 동기화 실패: ${e.message}")
            }
        }
    }
}

fun getLocalWifiIpAddress(): String {
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
                    if (host != null && (host.startsWith("192.168.") || host.startsWith("10.") || host.startsWith("172."))) {
                        return host
                    }
                }
            }
        }
    } catch (_: Exception) {}
    return "172.30.1.13"
}

data class StoreLocationPreset(
    val name: String,
    val ip: String,
    val icon: String
)

/**
 * [상용 시뮬레이터]: 테이블 QR 코드를 찍는 행위를 모사합니다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QRScannerSimulationScreen() {
    val context = LocalContext.current
    val detectedWifiIp = remember { getLocalWifiIpAddress() }
    
    var showReceiptDialog by remember { mutableStateOf(false) }
    var showExitDialog by remember { mutableStateOf(false) }
    var showIpDialog by remember { mutableStateOf(false) }
    var activeTableNumber by remember { mutableIntStateOf(0) }
    var webViewUrl by remember { mutableStateOf<String?>(null) }
    
    var inputIp by remember { mutableStateOf(KtorServerSimulator.serverHost) }

    // 시스템 뒤로가기 버튼 처리
    BackHandler {
        if (webViewUrl != null) {
            webViewUrl = null // 메뉴판 닫고 스캐너로 복귀
        } else {
            showExitDialog = true
        }
    }

    // 서버 알림 관찰
    LaunchedEffect(Unit) {
        KtorServerSimulator.serverEvents.collect { event ->
            when (event) {
                "RECEIPT_POPUP" -> {
                    showReceiptDialog = true
                    // 메뉴판은 닫지 않고 영수증만 띄움 (추가 주문 가능)
                }
                "TERMINATE_UI" -> {
                    // 서비스 종료 시 모든 대화상자를 닫고 첫 화면으로 강제 이동
                    showReceiptDialog = false
                    showIpDialog = false
                    webViewUrl = null 
                    activeTableNumber = 0
                }
            }
        }
    }

    // 서버 IP 주소 직접 입력 다이얼로그 (수정)
    if (showIpDialog) {
        AlertDialog(
            onDismissRequest = { showIpDialog = false },
            confirmButton = {
                Button(
                    onClick = {
                        KtorServerSimulator.updateServerHost(inputIp)
                        showIpDialog = false
                    }
                ) {
                    Text("적용 및 연결")
                }
            },
            dismissButton = {
                TextButton(onClick = { showIpDialog = false }) {
                    Text("취소")
                }
            },
            title = { Text("⚙️ 접속 서버 설정") },
            text = {
                Column {
                    // [5G 하이패스 우선 추천 카드]
                    KtorServerSimulator.tunnelUrl?.let { tunnel ->
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                            onClick = {
                                inputIp = tunnel
                                KtorServerSimulator.updateServerHost(tunnel)
                                showIpDialog = false
                            }
                        ) {
                            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("🌐", fontSize = 24.sp)
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text("5G 하이패스 연결 (권장)", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    Text("어디서나 접속 가능한 보안 터널", fontSize = 11.sp)
                                }
                            }
                        }
                    }

                    // [수동 동기화 버튼]
                    TextButton(
                        onClick = { 
                            KtorServerSimulator.startCloudDiscovery() 
                        },
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) {
                        Text("🔄 클라우드 정보 강제 새로고침")
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = inputIp,
                        onValueChange = { inputIp = it },
                        label = { Text("서버 주소 (IP 또는 도메인)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        )
    }

    // [종료 확인 팝업]
    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            confirmButton = {
                Button(
                    onClick = { (context as? ComponentActivity)?.finish() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("종료")
                }
            },
            dismissButton = {
                TextButton(onClick = { showExitDialog = false }) {
                    Text("취소")
                }
            },
            title = { Text("시스템 종료") },
            text = { Text("Starlink 주문 시뮬레이션을 종료하시겠습니까?") },
            shape = RoundedCornerShape(20.dp)
        )
    }

    // [영수증 및 감사 팝업]
    if (showReceiptDialog) {
        AlertDialog(
            onDismissRequest = { /* 강제 확인 필요 */ },
            confirmButton = {
                Button(
                    onClick = {
                        showReceiptDialog = false
                        OrderManager.clearTable(activeTableNumber)
                        activeTableNumber = 0
                        webViewUrl = null // 메뉴판 닫고 첫 스캐너 화면으로 복귀
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("확인 (첫 화면으로 이동)")
                }
            },
            title = { 
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text("✨ 결제 완료 ✨", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                }
            },
            text = { 
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text("맛있게 드셨나요?", fontSize = 18.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("결제 및 테이블 정리가 완료되었습니다.", textAlign = TextAlign.Center)
                    Text("다음에 또 방문해 주세요!", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
            },
            shape = RoundedCornerShape(28.dp)
        )
    }

    if (webViewUrl != null) {
        // [메뉴판 WebView 화면]
        Scaffold(
            modifier = Modifier.fillMaxSize().systemBarsPadding(),
            topBar = {
                TopAppBar(
                    title = { Text("${activeTableNumber}번 테이블 메뉴판", fontSize = 16.sp) },
                    actions = {
                        IconButton(onClick = { webViewUrl = null }) {
                            Icon(Icons.Default.Close, contentDescription = "닫기")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
            }
        ) { padding ->
            AndroidView(
                factory = {
                    WebView(it).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        webViewClient = WebViewClient()
                        webChromeClient = WebChromeClient()
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                        addJavascriptInterface(object {
                            @JavascriptInterface
                            fun closeWebView() {
                                (context as? ComponentActivity)?.runOnUiThread {
                                    webViewUrl = null
                                }
                            }
                        }, "Android")
                        loadUrl(webViewUrl!!)
                    }
                },
                update = { it.loadUrl(webViewUrl!!) },
                modifier = Modifier.padding(padding).fillMaxSize()
            )
        }
    } else {
        // [기존 QR 스캐너 화면]
        Surface(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
            Box(modifier = Modifier.fillMaxSize()) {
                // 우측 상단 서버 IP 설정 버튼
                IconButton(
                    onClick = {
                        inputIp = KtorServerSimulator.serverHost
                        showIpDialog = true
                    },
                    modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "서버 IP 설정",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.QrCodeScanner,
                        contentDescription = null,
                        modifier = Modifier.size(70.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Text(
                        text = "Starlink QR Scanner",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    
                    val flavorLabel = when (BuildConfig.FLAVOR) {
                        "cafe" -> "☕ 카페 전용 (선불 결제 & 음료 옵션)"
                        "restaurant" -> "🍽️ 식당 전용 (테이블 배치 & 코스 요리)"
                        "pub" -> "🍻 주점 전용 (주류 재고 & 더치페이 정산)"
                        else -> "✨ 통합 스마트 오더"
                    }
                    Text(
                        text = flavorLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "테이블의 QR 마크를 찍어주세요",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(12.dp),
                        onClick = {
                            inputIp = KtorServerSimulator.serverHost
                            showIpDialog = true
                        }
                    ) {
                        val displayHost = if (KtorServerSimulator.serverHost.contains("trycloudflare.com")) {
                            "5G 하이패스 모드"
                        } else {
                            "${KtorServerSimulator.serverHost}:8888"
                        }
                        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "📡 [${KtorServerSimulator.currentStoreName}]",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "$displayHost (${KtorServerSimulator.syncStatus})",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(28.dp))

                    // 1번부터 20번 테이블 QR 모의 버튼들
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(20) { index ->
                            val tableNum = index + 1
                                Button(
                                onClick = {
                                    activeTableNumber = tableNum
                                    KtorServerSimulator.joinTable(tableNum)
                                    // [개선] 5G 터널 주소일 경우 와이파이 체크 없이 즉시 웹뷰 이동
                                    webViewUrl = "${KtorServerSimulator.webMenuBaseUrl}?table=$tableNum"
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = if (activeTableNumber == tableNum) 
                                    ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                                else 
                                    ButtonDefaults.buttonColors()
                            ) {
                                Text("${tableNum}번", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }

    fun syncStoreProfileAndMenus(serverHost: String) {
        val flavor = try {
            BuildConfig.FLAVOR
        } catch (_: Exception) {
            "restaurant"
        }
        val (storeName, menus) = when (flavor) {
            "cafe" -> Pair(
                "스타링크 감성 카페 (호계점)",
                listOf(
                    mapOf("id" to "c1", "name" to "아메리카노", "price" to 4500, "category" to "커피", "img" to "https://images.unsplash.com/photo-1514432324607-a09d9b4aefdd?auto=format&fit=crop&w=600&q=80"),
                    mapOf("id" to "c2", "name" to "카페 라떼", "price" to 5000, "category" to "커피", "img" to "https://images.unsplash.com/photo-1534778101976-62847782c213?auto=format&fit=crop&w=600&q=80"),
                    mapOf("id" to "c3", "name" to "달콤 딸기 스무디", "price" to 6500, "category" to "논커피", "img" to "https://images.unsplash.com/photo-1556679343-c7306c1976bc?auto=format&fit=crop&w=600&q=80"),
                    mapOf("id" to "c4", "name" to "제주 감귤 에이드", "price" to 6000, "category" to "에이드/티", "img" to "https://images.unsplash.com/photo-1513558161293-cdaf765ed2fd?auto=format&fit=crop&w=600&q=80"),
                    mapOf("id" to "c5", "name" to "뉴욕 치즈 케이크", "price" to 7000, "category" to "디저트", "img" to "https://images.unsplash.com/photo-1578985545062-69928b1d9587?auto=format&fit=crop&w=600&q=80")
                )
            )
            "pub" -> Pair(
                "스타링크 수제맥주 & 펍",
                listOf(
                    mapOf("id" to "p1", "name" to "생맥주 500cc", "price" to 5000, "category" to "주류", "img" to "https://images.unsplash.com/photo-1535958636474-b021ee887b13?auto=format&fit=crop&w=600&q=80"),
                    mapOf("id" to "p2", "name" to "하이볼", "price" to 8000, "category" to "주류", "img" to "https://images.unsplash.com/photo-1514362545857-3bc16c4c7d1b?auto=format&fit=crop&w=600&q=80"),
                    mapOf("id" to "p3", "name" to "참소주 / 진로", "price" to 5000, "category" to "주류", "img" to "https://images.unsplash.com/photo-1563227812-0ea4c22e6cc8?auto=format&fit=crop&w=600&q=80"),
                    mapOf("id" to "p4", "name" to "바삭 후라이드 치킨", "price" to 20000, "category" to "안주류", "img" to "https://images.unsplash.com/photo-1626082927389-6cd097cdc6ec?auto=format&fit=crop&w=600&q=80"),
                    mapOf("id" to "p5", "name" to "매콤 골뱅이무침", "price" to 22000, "category" to "안주류", "img" to "https://images.unsplash.com/photo-1555396273-367ea4eb4db5?auto=format&fit=crop&w=600&q=80"),
                    mapOf("id" to "p6", "name" to "먹태구이", "price" to 18000, "category" to "안주류", "img" to "https://images.unsplash.com/photo-1544025162-d76694265947?auto=format&fit=crop&w=600&q=80")
                )
            )
            else -> Pair(
                "스타링크 명가 한식당",
                listOf(
                    mapOf("id" to "r1", "name" to "얼큰 차돌박이 해장국", "price" to 11000, "category" to "식사류", "img" to "https://images.unsplash.com/photo-1627308595229-7830a5c91f9f?auto=format&fit=crop&w=600&q=80"),
                    mapOf("id" to "r2", "name" to "묵은지 김치찜", "price" to 18000, "category" to "식사류", "img" to "https://images.unsplash.com/photo-1569058242253-92a9c71f9867?auto=format&fit=crop&w=600&q=80"),
                    mapOf("id" to "r3", "name" to "전통 불고기 전골", "price" to 32000, "category" to "요리류", "img" to "https://images.unsplash.com/photo-1525755662778-989d0524087e?auto=format&fit=crop&w=600&q=80"),
                    mapOf("id" to "r4", "name" to "매콤 제윤볶음", "price" to 25000, "category" to "요리류", "img" to "https://images.unsplash.com/photo-1541696432-82c6da8ce7bf?auto=format&fit=crop&w=600&q=80")
                )
            )
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val client = HttpClient(CIO)
                val host = if (serverHost.contains("trycloudflare.com")) serverHost else "$serverHost:8888"
                val scheme = if (serverHost.contains("trycloudflare.com")) "https" else "http"
                
                val jsonArray = JsonArray(menus.map { m ->
                    buildJsonObject {
                        m.forEach { (k, v) ->
                            if (v is Int) put(k, v) else put(k, v.toString())
                        }
                    }
                })
                client.post("$scheme://$host/api/menus/bulk") {
                    setBody(jsonArray.toString())
                    contentType(ContentType.Application.Json)
                }
                client.close()
                println("✨ [업종 자동 동기화 성공] $storeName 메뉴가 서버에 반영되었습니다.")
            } catch (e: Exception) {
                println("⚠️ 업종 자동 동기화 실패: ${e.message}")
            }
        }
    }
}
