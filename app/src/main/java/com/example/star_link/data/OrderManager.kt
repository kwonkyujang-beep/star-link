package com.example.star_link.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * 전역에서 리얼타임 데이터 동기화를 처리하는 중앙 주문 상태 관리 싱글톤 매니저입니다.
 * 고해상도 메뉴 이미지 매핑 및 실시간 주문 파이프라인 처리를 지원합니다.
 */
object OrderManager {

    private const val TOTAL_TABLES = 20

    // 실제 식당 고급 태블릿 메뉴판에 나타날 맛있는 중식 요리 샘플 이미지 세트 매핑
    val menuList = listOf(
        MenuItem(
            id = "1", 
            name = "명품 삼선 짜장면", 
            price = 8500, 
            category = "식사류", 
            description = "신선한 해산물과 춘장을 강한 불로 볶아내어 깊은 불맛을 선사하는 고급 짜장면입니다.",
            imageUrl = "https://images.unsplash.com/photo-1585032226651-759b368d7246?auto=format&fit=crop&w=400&q=80"
        ),
        MenuItem(
            id = "2", 
            name = "차돌박이 불짬뽕", 
            price = 11000, 
            category = "식사류", 
            description = "고소한 차돌박이를 아낌없이 얹어 얼큰하고 진한 고기 육수의 풍미가 일품인 짬뽕입니다.",
            imageUrl = "https://images.unsplash.com/photo-1627308595229-7830a5c91f9f?auto=format&fit=crop&w=400&q=80"
        ),
        MenuItem(
            id = "3", 
            name = "바삭 찹쌀 탕수육", 
            price = 19000, 
            category = "요리류", 
            description = "국산 돼지고기 등심을 100% 찹쌀 전분으로 튀겨내어 식어도 전혀 굳지 않고 쫄깃 바삭합니다.",
            imageUrl = "https://images.unsplash.com/photo-1525755662778-989d0524087e?auto=format&fit=crop&w=400&q=80"
        ),
        MenuItem(
            id = "4", 
            name = "매콤 달콤 깐풍기", 
            price = 26000, 
            category = "요리류", 
            description = "바삭하게 튀긴 닭고기에 매콤한 고추기름 and 새콤한 특제 소스를 입혀 볶아낸 최고급 요리.",
            imageUrl = "https://images.unsplash.com/photo-1563379091339-03b21ab4a4f8?auto=format&fit=crop&w=400&q=80"
        ),
        MenuItem(
            id = "5", 
            name = "속이 꽉 찬 수제 군만두", 
            price = 6000, 
            category = "사이드", 
            description = "매일 아침 직접 빚은 만두소에 육즙이 가득 들어있는 겉은 바삭하고 속은 촉촉한 별미 군만두.",
            imageUrl = "https://images.unsplash.com/photo-1541696432-82c6da8ce7bf?auto=format&fit=crop&w=400&q=80"
        ),
        MenuItem(
            id = "6", 
            name = "시원한 칭따오 맥주", 
            price = 7000, 
            category = "주류/음료", 
            description = "중식 요리의 기름진 맛을 깔끔하게 잡아주는 청량감 넘치는 오리지널 청도 맥주.",
            imageUrl = "https://images.unsplash.com/photo-1600788886242-5c96aabe3757?auto=format&fit=crop&w=400&q=80"
        ),
        MenuItem(
            id = "7", 
            name = "톡 쏘는 탄산 콜라/사이다", 
            price = 2000, 
            category = "주류/음료", 
            description = "얼음컵과 함께 제공되는 시원하고 청량한 탄산 음료입니다.",
            imageUrl = "https://images.unsplash.com/photo-1622483767028-3f66f32aef97?auto=format&fit=crop&w=400&q=80"
        )
    )

    private val _tableStatuses = MutableStateFlow<List<TableStatus>>(emptyList())
    val tableStatuses: StateFlow<List<TableStatus>> = _tableStatuses.asStateFlow()

    init {
        resetAllTables()
    }

    fun resetAllTables() {
        val initialList = (1..TOTAL_TABLES).map { TableStatus(tableNumber = it) }
        _tableStatuses.value = initialList
    }

    fun placeOrder(tableNumber: Int, cartItems: List<CartItem>) {
        if (cartItems.isEmpty()) return

        val orderTime = SimpleDateFormat("HH:mm:ss", Locale.KOREA).format(Date())
        val orderAmount = cartItems.sumOf { it.menuItem.price * it.quantity }

        val newOrder = Order(
            orderId = UUID.randomUUID().toString().take(6),
            tableNumber = tableNumber,
            items = cartItems.map { it.copy() },
            totalAmount = orderAmount,
            timestamp = System.currentTimeMillis()
        )

        val currentList = _tableStatuses.value.map { status ->
            if (status.tableNumber == tableNumber) {
                val updatedOrders = status.currentOrders + newOrder
                val updatedTotal = status.totalUnpaidAmount + orderAmount
                status.copy(
                    currentOrders = updatedOrders,
                    totalUnpaidAmount = updatedTotal,
                    lastOrderTime = orderTime
                )
            } else {
                status
            }
        }
        _tableStatuses.value = currentList

        // [해결] 주방 브라우저가 인식할 수 있도록 데이터를 깨끗하고 안전한 JSON 규격으로 조립합니다.
        val targetTable = currentList.first { it.tableNumber == tableNumber }
        val allItemsSummary = targetTable.currentOrders.flatMap { it.items }
            .groupBy { it.menuItem.name }
            .mapValues { entry -> entry.value.sumOf { it.quantity } }
            .map { entry -> "{\"name\": \"${entry.key}\", \"qty\": ${entry.value}}" }
            .joinToString(",")

        KtorServerSimulator.broadcastOrderToKitchen(
            tableNumber = tableNumber,
            itemsSummary = allItemsSummary,
            totalAmount = targetTable.totalUnpaidAmount
        )
    }

    fun clearTable(tableNumber: Int) {
        val currentList = _tableStatuses.value.map { status ->
            if (status.tableNumber == tableNumber) {
                TableStatus(tableNumber = tableNumber)
            } else {
                status
            }
        }
        _tableStatuses.value = currentList
    }
}
