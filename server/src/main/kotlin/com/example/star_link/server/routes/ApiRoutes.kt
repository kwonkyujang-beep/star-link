package com.example.star_link.server.routes

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.http.content.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.websocket.*
import io.ktor.server.websocket.DefaultWebSocketServerSession
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import com.example.star_link.server.*

fun Route.configureApiRoutes(
    getStoreConfig: () -> JsonObject?,
    updateStoreConfig: (JsonObject) -> Unit,
    getCurrentLocalIp: () -> String,
    getCurrentWifiSsid: () -> String,
    getTableSessions: () -> ConcurrentHashMap<Int, TableSession>,
    removeTableSession: (Int) -> Unit,
    getKitchenSessions: () -> Set<DefaultWebSocketServerSession>,
    syncFirebase: suspend () -> Unit,
    broadcastMenuUpdate: suspend () -> Unit
) {
    // [신규] 원격 A/S 진단 및 헬스체크 API (/api/health)
    get("/api/health") {
        val config = getStoreConfig()
        val sId = config?.get("storeId")?.jsonPrimitive?.content ?: "UNKNOWN"
        val storeName = config?.get("storeName")?.jsonPrimitive?.content ?: "미등록"
        
        val dbFile = File("starlink.db")
        val dbExists = dbFile.exists()
        val dbSizeKb = if (dbExists) dbFile.length() / 1024 else 0L

        val activeKitchenCount = getKitchenSessions().size
        val activeTableCount = getTableSessions().size

        val runtime = Runtime.getRuntime()
        val usedMemoryMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
        val maxMemoryMb = runtime.maxMemory() / (1024 * 1024)

        val healthReport = buildJsonObject {
            put("status", "OK")
            put("storeId", sId)
            put("storeName", storeName)
            put("uptimeMinutes", System.currentTimeMillis() / 1000 / 60)
            put("database", buildJsonObject {
                put("exists", dbExists)
                put("sizeKb", dbSizeKb)
            })
            put("memory", buildJsonObject {
                put("usedMb", usedMemoryMb)
                put("maxMb", maxMemoryMb)
            })
            put("activeSessions", buildJsonObject {
                put("kitchenKdsCount", activeKitchenCount)
                put("activeTablesCount", activeTableCount)
            })
            put("timestamp", System.currentTimeMillis())
        }

        call.respondText(healthReport.toString(), ContentType.Application.Json)
    }

    // [API] 본사 클라우드 중계 시뮬레이션
    post("/api/cloud-sim/heartbeat") {
        val body = call.receiveText()
        val json = Json.parseToJsonElement(body).jsonObject
        val sId = json["storeId"]?.jsonPrimitive?.content ?: "Unknown"
        val ip = json["localIp"]?.jsonPrimitive?.content ?: "0.0.0.0"
        println("☁️ [Cloud Sim] 매장 '$sId'의 접속 위치를 확인했습니다: $ip")
        call.respond(HttpStatusCode.OK, "{\"status\": \"synced\"}")
    }

    // [API] Wi-Fi 설정 조회 및 업데이트
    get("/api/wifi/status") {
        val config = getStoreConfig()
        val savedSsid = config?.get("wifiSsid")?.jsonPrimitive?.content ?: ""
        val currentSsid = if (savedSsid.isNotBlank() && !savedSsid.contains("감지중")) savedSsid else getCurrentWifiSsid()
        
        val response = buildJsonObject {
            put("ssid", currentSsid)
            put("password", config?.get("wifiPw")?.jsonPrimitive?.content ?: "")
        }
        call.respondText(response.toString(), ContentType.Application.Json)
    }

    post("/api/wifi/update") {
        val bodyText = call.receiveText()
        val json = try { Json.parseToJsonElement(bodyText).jsonObject } catch (_: Exception) { null }
        val newSsid = json?.get("ssid")?.jsonPrimitive?.content
        val newPw = json?.get("password")?.jsonPrimitive?.content
        
        if (newSsid != null && newPw != null) {
            val currentMap = getStoreConfig()?.toMutableMap() ?: mutableMapOf()
            currentMap["wifiSsid"] = JsonPrimitive(newSsid)
            currentMap["wifiPw"] = JsonPrimitive(newPw)
            updateStoreConfig(JsonObject(currentMap))
            
            println("⚙️ [Wi-Fi 설정 변경] SSID: $newSsid | PW: $newPw")
            syncFirebase()
            call.respondText("{\"success\": true}", ContentType.Application.Json)
        } else {
            call.respond(HttpStatusCode.BadRequest)
        }
    }

    // [API] 테이블별 실시간 주문 내역 조회
    get("/api/table/{tableNumber}/orders") {
        val tableNum = call.parameters["tableNumber"]?.toIntOrNull() ?: 1
        val config = getStoreConfig()
        val mode = config?.get("paymentMode")?.jsonPrimitive?.content ?: "POST_PAY"
        
        if (mode != "POST_PAY") {
            call.respondText("{\"orders\": [], \"totalAmount\": 0, \"paidAmount\": 0, \"remainingAmount\": 0}", ContentType.Application.Json)
            return@get
        }
        
        val session = getTableSessions()[tableNum] ?: TableSession()
        val remaining = maxOf(0, session.totalAmount - session.paidAmount)
        val responseObj = buildJsonObject {
            put("orders", buildJsonArray { session.orders.forEach { add(it) } })
            put("totalAmount", session.totalAmount)
            put("paidAmount", session.paidAmount)
            put("remainingAmount", remaining)
        }
        call.respondText(responseObj.toString(), ContentType.Application.Json)
    }

    // [API] 전체 테이블 활성 세션 상태 조회
    get("/api/tables/status") {
        val responseObj = buildJsonObject {
            getTableSessions().forEach { entry ->
                val tNum = entry.key
                val session = entry.value
                put(tNum.toString(), buildJsonObject {
                    put("totalAmount", session.totalAmount)
                    put("paidAmount", session.paidAmount)
                    put("orders", buildJsonArray { session.orders.forEach { add(it) } })
                })
            }
        }
        call.respondText(responseObj.toString(), ContentType.Application.Json)
    }

    // [API] 매장 연동 설정 조회
    get("/api/store/config") {
        val modeParam = call.request.queryParameters["mode"]
        val config = getStoreConfig()
        val activeMode = if (modeParam == "PRE_PAY" || modeParam == "POST_PAY") modeParam else (config?.get("paymentMode")?.jsonPrimitive?.content ?: "POST_PAY")
        
        val responseConfig = buildJsonObject {
            put("agentId", config?.get("agentId")?.jsonPrimitive?.content ?: "HQ-DEFAULT")
            put("storeId", config?.get("storeId")?.jsonPrimitive?.content ?: "UNKNOWN")
            put("storeName", config?.get("storeName")?.jsonPrimitive?.content ?: "미등록 매장")
            put("storeType", config?.get("storeType")?.jsonPrimitive?.content ?: "NONE")
            put("storeIp", getCurrentLocalIp())
            put("tunnelUrl", config?.get("tunnelUrl")?.jsonPrimitive?.content ?: "")
            put("pgClientKey", config?.get("pgClientKey")?.jsonPrimitive?.content ?: "test_ck_D5Ge2x3M8pv991005b6VgL1N0Ene")
            put("paymentMode", activeMode)
            put("tableCount", config?.get("tableCount")?.jsonPrimitive?.intOrNull ?: 20)
            put("isSetupComplete", config?.get("isSetupComplete")?.jsonPrimitive?.booleanOrNull ?: false)
        }
        call.respondText(responseConfig.toString(), ContentType.Application.Json)
    }

    // [API] 테이블 개수 등 설정 업데이트
    post("/api/store/config/update") {
        val bodyText = call.receiveText()
        val json = try { Json.parseToJsonElement(bodyText).jsonObject } catch (_: Exception) { null }
        val tCount = json?.get("tableCount")?.jsonPrimitive?.intOrNull ?: 20
        val config = getStoreConfig()
        if (config != null) {
            val updatedConfig = JsonObject(config.toMutableMap().apply {
                put("tableCount", JsonPrimitive(tCount))
            })
            updateStoreConfig(updatedConfig)

            val updateMsg = "{\"type\": \"CONFIG_UPDATED\", \"tableCount\": $tCount}"
            getKitchenSessions().forEach { session ->
                try { session.send(Frame.Text(updateMsg)) } catch (_: Exception) {}
            }

            call.respond(HttpStatusCode.OK, "{\"status\": \"success\", \"tableCount\": $tCount}")
        } else {
            call.respond(HttpStatusCode.BadRequest, "{\"error\": \"No store config\"}")
        }
    }

    // [API] PIN 번호 검증
    post("/api/setup/verify") {
        val bodyText = call.receiveText()
        val json = try { Json.parseToJsonElement(bodyText).jsonObject } catch (_: Exception) { null }
        val inputPin = json?.get("pin")?.jsonPrimitive?.content
        val config = getStoreConfig()
        val savedPin = config?.get("setupPin")?.jsonPrimitive?.content
        val isSetupComplete = config?.get("isSetupComplete")?.jsonPrimitive?.booleanOrNull ?: false

        if (isSetupComplete) {
            call.respondText("{\"success\": false, \"message\": \"이미 세팅이 완료된 기기입니다.\"}", ContentType.Application.Json, HttpStatusCode.Forbidden)
            return@post
        }

        if (savedPin != null && inputPin == savedPin) {
            call.respondText("{\"success\": true}", ContentType.Application.Json)
        } else {
            call.respondText("{\"success\": false}", ContentType.Application.Json)
        }
    }

    // [API] 세팅 완료 및 기기 영구 잠금
    post("/api/setup/complete") {
        val bodyText = call.receiveText()
        val json = try { Json.parseToJsonElement(bodyText).jsonObject } catch (_: Exception) { null }
        val inputPin = json?.get("pin")?.jsonPrimitive?.content
        val config = getStoreConfig()
        val savedPin = config?.get("setupPin")?.jsonPrimitive?.content
        
        if (savedPin == null || inputPin != savedPin) {
            call.respond(HttpStatusCode.Forbidden)
            return@post
        }

        val newType = json?.get("storeType")?.jsonPrimitive?.content ?: "CAFE"
        val payMode = if (newType == "CAFE") "PRE_PAY" else "POST_PAY"
        
        val updatedConfig = buildJsonObject {
            put("agentId", json?.get("agentId")?.jsonPrimitive?.content ?: "UNKNOWN")
            put("storeId", config?.get("storeId")?.jsonPrimitive?.content ?: "UNKNOWN")
            put("storeName", config?.get("storeName")?.jsonPrimitive?.content ?: "이름 없음")
            put("storeType", newType)
            put("storeIp", getCurrentLocalIp())
            put("pgClientKey", config?.get("pgClientKey")?.jsonPrimitive?.content ?: "")
            put("paymentMode", payMode)
            put("wifiPw", json?.get("wifiPw")?.jsonPrimitive?.content ?: "")
            put("tunnelUrl", config?.get("tunnelUrl")?.jsonPrimitive?.content ?: "")
            put("setupPin", savedPin)
            put("isSetupComplete", true)
        }

        updateStoreConfig(updatedConfig)
        syncFirebase()
        call.respondText("{\"success\": true}", ContentType.Application.Json)
    }

    // [API] 일일 영업 마감
    post("/api/sales/close") {
        val bodyText = call.receiveText()
        val json = try { Json.parseToJsonElement(bodyText).jsonObject } catch (_: Exception) { null }
        if (json != null) {
            DatabaseFactory.saveDailySales(json)
            call.respondText("{\"success\": true, \"message\": \"오늘 매출이 로컬 DB에 성공적으로 저장되었습니다.\"}", ContentType.Application.Json)
        } else {
            call.respond(HttpStatusCode.BadRequest)
        }
    }

    // [API] 매출 통계 분석
    get("/api/sales/stats") {
        val targetDateStr = call.request.queryParameters["date"] ?: LocalDate.now().toString()
        val targetDate = try { LocalDate.parse(targetDateStr) } catch (_: Exception) { LocalDate.now() }
        
        val dailyMap = mutableMapOf<String, Long>()
        val hourlyMap = mutableMapOf<String, Long>()
        val week7Map = mutableMapOf<String, Long>()
        val monthDaysMap = mutableMapOf<String, Long>()
        val itemStatsMap = mutableMapOf<String, JsonObject>()

        for (i in 6 downTo 0) {
            week7Map[targetDate.minusDays(i.toLong()).toString()] = 0L
        }
        val firstDayOfMonth = targetDate.withDayOfMonth(1)
        for (day in 1..targetDate.lengthOfMonth()) {
            monthDaysMap[firstDayOfMonth.withDayOfMonth(day).toString()] = 0L
        }
        for (h in 0..23) {
            hourlyMap[h.toString().padStart(2, '0')] = 0L
        }

        transaction {
            // 날짜별로 유일한(최신) Sales 레코드 1개만 매핑하여 중복 합산 원천 차단
            val latestSalesByDate = Sales.selectAll().groupBy { it[Sales.saleDate] }.mapValues { entry ->
                entry.value.maxByOrNull { it[Sales.id] }!!
            }

            latestSalesByDate.values.forEach { row ->
                val dateStr = row[Sales.saleDate]
                val total = row[Sales.grandTotal]
                dailyMap[dateStr] = (dailyMap[dateStr] ?: 0L) + total
                if (week7Map.containsKey(dateStr)) week7Map[dateStr] = (week7Map[dateStr] ?: 0L) + total
                if (monthDaysMap.containsKey(dateStr)) monthDaysMap[dateStr] = (monthDaysMap[dateStr] ?: 0L) + total
            }

            val targetRow = latestSalesByDate[targetDateStr]
            if (targetRow != null) {
                val timeStr = targetRow[Sales.saleTime]
                val total = targetRow[Sales.grandTotal]
                val hour = if (timeStr.length >= 2) timeStr.substring(0, 2) else "12"
                if (hourlyMap.containsKey(hour)) hourlyMap[hour] = (hourlyMap[hour] ?: 0L) + total
                
                val saleId = targetRow[Sales.id]
                OrderItems.select { OrderItems.saleId eq saleId }.forEach { row ->
                    val name = row[OrderItems.itemName]
                    val qty = row[OrderItems.qty]
                    val itemTotal = row[OrderItems.total]
                    val existing = itemStatsMap[name]
                    val newQty = (existing?.get("qty")?.jsonPrimitive?.int ?: 0) + qty
                    val newTotal = (existing?.get("total")?.jsonPrimitive?.long ?: 0L) + itemTotal
                    itemStatsMap[name] = buildJsonObject {
                        put("qty", newQty)
                        put("total", newTotal)
                    }
                }
            }
        }

        val yesterday = LocalDate.now().minusDays(1).toString()
        val yesterdayHasSales = (dailyMap[yesterday] ?: 0L) > 0L
        val yesterdayClosedInDb = transaction { Sales.select { Sales.saleDate eq yesterday }.count() > 0 }
        val yesterdayClosed = !yesterdayHasSales || yesterdayClosedInDb

        val response = buildJsonObject {
            put("daily", buildJsonObject { dailyMap.toSortedMap().forEach { (k, v) -> put(k, v) } })
            put("hourly", buildJsonObject { hourlyMap.toSortedMap().forEach { (k, v) -> put(k, v) } })
            put("week7", buildJsonObject { week7Map.toSortedMap().forEach { (k, v) -> put(k, v) } })
            put("monthDays", buildJsonObject { monthDaysMap.toSortedMap().forEach { (k, v) -> put(k, v) } })
            put("items", buildJsonObject { itemStatsMap.forEach { (k, v) -> put(k, v) } })
            put("selectedDate", targetDateStr)
            put("yesterdayClosed", yesterdayClosed)
            put("yesterdayDate", yesterday)
        }
        call.respondText(response.toString(), ContentType.Application.Json)
    }

    // [API] PG 결제 승인 요청
    post("/api/payment/confirm") {
        val bodyText = call.receiveText()
        val json = try { Json.parseToJsonElement(bodyText).jsonObject } catch (_: Exception) { null }
        if (json == null) {
            call.respondText("{\"success\": false, \"message\": \"잘못된 요청 형식입니다.\"}", ContentType.Application.Json, HttpStatusCode.BadRequest)
            return@post
        }

        val orderId = json["orderId"]?.jsonPrimitive?.content ?: ""
        val amount = json["amount"]?.jsonPrimitive?.intOrNull ?: 0
        val tableNumber = json["tableNumber"]?.jsonPrimitive?.intOrNull ?: 1
        val itemsSummary = json["items"]?.jsonArray ?: buildJsonArray { }
        val isDutch = json["isDutchPayment"]?.jsonPrimitive?.booleanOrNull ?: false

        val sessions = getTableSessions()
        if (isDutch) {
            val session = sessions.getOrPut(tableNumber) { TableSession() }
            
            // [방어 로직] 이미 전액 결제되었거나 미결재 잔액을 초과하는 결제 요청 차단 (이중 결제 방지)
            val remaining = maxOf(0, session.totalAmount - session.paidAmount)
            if (remaining <= 0) {
                call.respondText("{\"success\": false, \"message\": \"이미 전액 결제 완료된 테이블입니다.\"}", ContentType.Application.Json, HttpStatusCode.BadRequest)
                return@post
            }
            if (amount > remaining) {
                call.respondText("{\"success\": false, \"message\": \"미결재 잔액($remaining 원)을 초과할 수 없습니다.\"}", ContentType.Application.Json, HttpStatusCode.BadRequest)
                return@post
            }

            session.paidAmount += amount
            if (session.paidAmount >= session.totalAmount && session.totalAmount > 0) {
                removeTableSession(tableNumber)
            }
            try { DatabaseFactory.recordPaymentSettlement(amount.toLong()) } catch (_: Exception) {}

            val paymentPacket = buildJsonObject {
                put("type", "PAYMENT_RECEIVED")
                put("tableNumber", tableNumber)
                put("totalAmount", amount)
                put("isPayment", true)
                put("items", itemsSummary)
            }.toString()

            getKitchenSessions().forEach {
                try { it.send(Frame.Text(paymentPacket)) } catch (_: Exception) {}
            }
        } else {
            val session = sessions.getOrPut(tableNumber) { TableSession() }
            session.paidAmount += amount
            session.orders.add(buildJsonObject {
                put("time", LocalTime.now().toString().substringBefore("."))
                put("total", amount)
                put("isPaid", true)
                put("items", itemsSummary)
            })

            try { DatabaseFactory.recordOrderSale(amount.toLong(), itemsSummary, isPaid = true) } catch (_: Exception) {}

            val orderReceivedPacket = buildJsonObject {
                put("type", "ORDER_RECEIVED")
                put("tableNumber", tableNumber)
                put("totalAmount", amount)
                put("orderId", orderId)
                put("isPaid", true)
                put("paymentMode", "PRE_PAY")
                put("items", itemsSummary)
            }.toString()

            getKitchenSessions().forEach {
                try { it.send(Frame.Text(orderReceivedPacket)) } catch (_: Exception) {}
            }
        }

        call.respondText("{\"success\": true, \"message\": \"결제 승인 및 주문 접수가 완료되었습니다.\"}", ContentType.Application.Json)
    }

    // [API] 후불 주문 HTTP 접수
    post("/api/order/submit") {
        val bodyText = call.receiveText()
        val json = try { Json.parseToJsonElement(bodyText).jsonObject } catch (_: Exception) { null }
        if (json == null) {
            call.respondText("{\"success\": false, \"message\": \"잘못된 요청 형식입니다.\"}", ContentType.Application.Json, HttpStatusCode.BadRequest)
            return@post
        }

        val orderId = json["orderId"]?.jsonPrimitive?.content ?: ""
        val amount = json["totalAmount"]?.jsonPrimitive?.intOrNull ?: json["amount"]?.jsonPrimitive?.intOrNull ?: 0
        val tableNumber = json["tableNumber"]?.jsonPrimitive?.intOrNull ?: 1
        val itemsSummary = json["items"]?.jsonArray ?: buildJsonArray { }
        val paymentMode = json["paymentMode"]?.jsonPrimitive?.content ?: "POST_PAY"
        val isPaid = json["isPaid"]?.jsonPrimitive?.booleanOrNull ?: false

        val session = getTableSessions().getOrPut(tableNumber) { TableSession() }
        session.orders.add(buildJsonObject {
            put("time", LocalTime.now().toString().substringBefore("."))
            put("total", amount)
            put("isPaid", isPaid)
            put("items", itemsSummary)
        })

        try { DatabaseFactory.recordOrderSale(amount.toLong(), itemsSummary, isPaid = isPaid) } catch (_: Exception) {}

        val orderReceivedPacket = buildJsonObject {
            put("type", "ORDER_RECEIVED")
            put("tableNumber", tableNumber)
            put("totalAmount", amount)
            put("orderId", orderId)
            put("isPaid", isPaid)
            put("paymentMode", paymentMode)
            put("items", itemsSummary)
        }.toString()

        getKitchenSessions().forEach {
            try { it.send(Frame.Text(orderReceivedPacket)) } catch (_: Exception) {}
        }

        call.respondText("{\"success\": true, \"message\": \"주문이 성공적으로 접수되었습니다.\"}", ContentType.Application.Json)
    }

    // [API] 실시간 메뉴 조회
    get("/api/menus") {
        val menus = transaction {
            Menus.selectAll().orderBy(Menus.sortOrder to SortOrder.ASC).map { 
                buildJsonObject {
                    put("id", it[Menus.id])
                    put("name", it[Menus.name])
                    put("price", it[Menus.price])
                    put("category", it[Menus.category])
                    put("img", it[Menus.img])
                    put("sortOrder", it[Menus.sortOrder])
                }
            }
        }
        call.respondText(Json.encodeToString(menus), ContentType.Application.Json)
    }

    // [API] 전체 메뉴 일괄 교체
    post("/api/menus/bulk") {
        val bodyText = call.receiveText()
        val newMenus = try { Json.decodeFromString<List<JsonObject>>(bodyText) } catch (_: Exception) { null }
        if (newMenus != null) {
            transaction {
                Menus.deleteAll()
                newMenus.forEachIndexed { index, m ->
                    Menus.insert {
                        it[id] = m["id"]?.jsonPrimitive?.content ?: ""
                        it[name] = m["name"]?.jsonPrimitive?.content ?: ""
                        it[price] = m["price"]?.jsonPrimitive?.int ?: 0
                        it[category] = m["category"]?.jsonPrimitive?.content ?: ""
                        it[img] = m["img"]?.jsonPrimitive?.content ?: ""
                        it[sortOrder] = index
                    }
                }
            }
            broadcastMenuUpdate()
            call.respondText("{\"success\": true}", ContentType.Application.Json)
        } else {
            call.respondText("{\"success\": false}", ContentType.Application.Json, HttpStatusCode.BadRequest)
        }
    }

    // [API] 이미지 업로드
    post("/api/upload") {
        val uploadDir = File("uploads").apply { if (!exists()) mkdirs() }
        val multipart = call.receiveMultipart()
        var fileName = ""
        multipart.forEachPart { part ->
            if (part is PartData.FileItem) {
                val ext = part.originalFileName?.substringAfterLast(".", "jpg") ?: "jpg"
                fileName = "${UUID.randomUUID()}.$ext"
                val fileBytes = part.streamProvider().readBytes()
                File(uploadDir, fileName).writeBytes(fileBytes)
            }
            part.dispose()
        }
        call.respondText("{\"url\": \"/uploads/$fileName\"}", ContentType.Application.Json)
    }

    // 정적 파일 서빙
    staticFiles("/uploads", File("uploads").apply { if (!exists()) mkdirs() })
}
