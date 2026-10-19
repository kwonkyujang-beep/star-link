package com.example.star_link.data

data class MenuItem(
    val id: String,
    val name: String,
    val price: Int,
    val category: String,
    val description: String,
    val imageUrl: String // 고해상도 웹 이미지 URL 추가 필드
)

data class CartItem(
    val menuItem: MenuItem,
    var quantity: Int,
    // [업종별 확장 필드] 카페 옵션, 식당 코스, 주점 탭 등
    val extraShot: Boolean = false,
    val syrupOption: String = "기본",
    val iceLevel: String = "보통",
    val isTakeout: Boolean = false
)

data class Order(
    val orderId: String,
    val tableNumber: Int,
    val items: List<CartItem>,
    val totalAmount: Int,
    val timestamp: Long,
    val isCompleted: Boolean = false,
    val paymentType: String = "PRE_PAY" // PRE_PAY (카페) 또는 POST_PAY (식당/주점)
)

data class TableStatus(
    val tableNumber: Int,
    val currentOrders: List<Order> = emptyList(),
    val totalUnpaidAmount: Int = 0,
    val lastOrderTime: String = "",
    val zoneName: String = "홀", // 식당/주점 구역 (홀/룸/테라스)
    val guestCount: Int = 2,     // 착석 인원수
    val courseStatus: String = "대기", // 식당 코스 상태 (대기/진행중/후식)
    val isDutchPayActive: Boolean = false, // 주점 더치페이 활성화 여부
    val splitCount: Int = 1      // 더치페이 인원수
)
