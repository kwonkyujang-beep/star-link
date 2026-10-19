package com.example.star_link.server.websockets

import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import com.example.star_link.server.*

fun Route.configureWebSocketRoutes(
    kitchenSessions: MutableSet<DefaultWebSocketServerSession>,
    customerSessions: ConcurrentHashMap<Int, MutableSet<DefaultWebSocketServerSession>>,
    broadcastMenuUpdate: suspend () -> Unit
) {
    webSocket("/kitchen-stream") {
        kitchenSessions.add(this)
        println("🌟 [주방] 실시간 세션 연결됨")
        
        try {
            incoming.consumeEach { frame ->
                if (frame is Frame.Text) {
                    val text = frame.readText()
                    val json = Json.parseToJsonElement(text).jsonObject
                    
                    when (json["action"]?.jsonPrimitive?.content) {
                        "ADD_MENU" -> {
                            val newMenu = json["data"]?.jsonObject ?: return@consumeEach
                            transaction {
                                val maxOrder = Menus.selectAll().maxOfOrNull { it[Menus.sortOrder] } ?: -1
                                Menus.insert {
                                    it[id] = newMenu["id"]?.jsonPrimitive?.content ?: ""
                                    it[name] = newMenu["name"]?.jsonPrimitive?.content ?: ""
                                    it[price] = newMenu["price"]?.jsonPrimitive?.int ?: 0
                                    it[category] = newMenu["category"]?.jsonPrimitive?.content ?: ""
                                    it[img] = newMenu["img"]?.jsonPrimitive?.content ?: ""
                                    it[sortOrder] = maxOrder + 1
                                }
                            }
                            println("🥘 [신메뉴 등록] ${newMenu["name"]} (DB 저장 완료)")
                            broadcastMenuUpdate()
                        }
                        "MOVE_MENU" -> {
                            val menuId = json["menuId"]?.jsonPrimitive?.content ?: return@consumeEach
                            val direction = json["direction"]?.jsonPrimitive?.content ?: return@consumeEach
                            transaction {
                                val allMenus = Menus.selectAll().orderBy(Menus.sortOrder to SortOrder.ASC).toList()
                                val mutableList = allMenus.toMutableList()
                                val index = mutableList.indexOfFirst { it[Menus.id] == menuId }
                                if (index != -1) {
                                    if (direction == "UP" && index > 0) {
                                        val temp = mutableList[index]
                                        mutableList[index] = mutableList[index - 1]
                                        mutableList[index - 1] = temp
                                    } else if (direction == "DOWN" && index < mutableList.size - 1) {
                                        val temp = mutableList[index]
                                        mutableList[index] = mutableList[index + 1]
                                        mutableList[index + 1] = temp
                                    }
                                    mutableList.forEachIndexed { newIndex, row ->
                                        Menus.update({ Menus.id eq row[Menus.id] }) {
                                            it[sortOrder] = newIndex
                                        }
                                    }
                                }
                            }
                            println("↕️ [메뉴 순서 변경] ID: $menuId, 방향: $direction")
                            broadcastMenuUpdate()
                        }
                        "DELETE_MENU" -> {
                            val idToDelete = json["menuId"]?.jsonPrimitive?.content ?: return@consumeEach
                            transaction {
                                Menus.deleteWhere { Menus.id eq idToDelete }
                            }
                            println("🗑️ [메뉴 삭제] ID: $idToDelete")
                            broadcastMenuUpdate()
                        }
                        "CLEAR_TABLE" -> {
                            val tableNum = json["tableNumber"]?.jsonPrimitive?.int ?: return@consumeEach
                            val terminate = json["terminateSession"]?.jsonPrimitive?.booleanOrNull ?: false
                            
                            println("🧹 [초기화] $tableNum 번 테이블 (세션종료: $terminate)")
                            
                            val signal = buildJsonObject { 
                                put("type", "SERVICE_TERMINATED") 
                                put("message", "식사가 완료되었습니다. 방문해 주셔서 감사합니다!")
                            }.toString()

                            customerSessions[tableNum]?.toList()?.forEach { session ->
                                try {
                                    session.send(Frame.Text(signal))
                                    if (terminate) {
                                        launch {
                                            delay(500)
                                            session.close(CloseReason(CloseReason.Codes.NORMAL, "Service Terminated"))
                                        }
                                    }
                                } catch (_: Exception) {}
                            }
                            
                            if (terminate) {
                                customerSessions.remove(tableNum)
                            }
                        }
                        "SERVE_FOOD" -> {
                            val tableNum = json["tableNumber"]?.jsonPrimitive?.int ?: return@consumeEach
                            println("🔔 [서빙완료] $tableNum 번 테이블 음료/메뉴 준비 완료 알림 전송 요청")
                            
                            val signal = buildJsonObject {
                                put("type", "ORDER_SERVED")
                                put("message", "주문하신 메뉴가 준비되었습니다! 픽업대로 오셔서 수령해 주세요.")
                            }.toString()

                            customerSessions[tableNum]?.toList()?.forEach { session ->
                                try {
                                    session.send(Frame.Text(signal))
                                } catch (_: Exception) {}
                            }
                        }
                    }
                }
            }
        } finally { kitchenSessions.remove(this) }
    }

    webSocket("/place-order") {
        var currentTableNum: Int? = null
        println("📱 [연결] 새로운 손님 단말기 접속 시도")
        try {
            incoming.consumeEach { frame ->
                if (frame is Frame.Text) {
                    val rawJson = frame.readText()
                    val json = try { Json.parseToJsonElement(rawJson).jsonObject } catch (_: Exception) { null } ?: return@consumeEach
                    
                    val tNum = json["tableNumber"]?.jsonPrimitive?.intOrNull
                    if (tNum != null) {
                        if (currentTableNum != tNum) {
                            currentTableNum?.let { oldNum ->
                                customerSessions[oldNum]?.remove(this)
                            }
                            
                            currentTableNum = tNum
                            customerSessions.getOrPut(tNum) { 
                                Collections.newSetFromMap(ConcurrentHashMap<DefaultWebSocketServerSession, Boolean>()) 
                            }.add(this)
                            println("📍 [세션매핑] $tNum 번 테이블 손님(기기 추가) 식별 완료")
                        }
                    }

                    val packetType = json["type"]?.jsonPrimitive?.content
                    if (packetType == "ORDER_RECEIVED" || packetType == "STAFF_CALL") {
                        val tNum = json["tableNumber"]?.jsonPrimitive?.intOrNull ?: currentTableNum
                        val msg = if (packetType == "STAFF_CALL") json["message"]?.jsonPrimitive?.content ?: "직원 호출" else "주문 접수"
                        
                        println("🚨 [신호접수] $tNum 번 테이블 -> 주방 전송 시도 ($msg)")
                        
                        if (kitchenSessions.isEmpty()) {
                            println("⚠️ [경고] 연결된 주방 대시보드가 없습니다!")
                        } else {
                            var successCount = 0
                            kitchenSessions.forEach { 
                                try {
                                    it.send(Frame.Text(rawJson))
                                    successCount++
                                } catch (e: Exception) {
                                    println("❌ [전송실패] 특정 주방 세션 전송 에러: ${e.message}")
                                }
                            }
                            println("📡 [전송성공] 총 $successCount 개의 주방 화면으로 신호를 전송했습니다.")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            println("🔴 [끊김] $currentTableNum 번 테이블 손님 접속 종료: ${e.message}")
        } finally {
            currentTableNum?.let { 
                customerSessions[it]?.remove(this)
                if (customerSessions[it]?.isEmpty() == true) {
                    customerSessions.remove(it)
                }
            }
        }
    }
}
