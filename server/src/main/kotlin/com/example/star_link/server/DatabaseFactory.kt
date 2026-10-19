package com.example.star_link.server

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.File
import kotlinx.serialization.json.*
import java.time.LocalDate
import java.time.LocalTime

// [메뉴 테이블]
object Menus : Table("menus") {
    val id = varchar("id", 50)
    val name = varchar("name", 255)
    val price = integer("price")
    val category = varchar("category", 50)
    val img = varchar("img", 1024)
    val sortOrder = integer("sort_order").default(0)
    override val primaryKey = PrimaryKey(id)
}

// [매출 마감 내역 테이블]
object Sales : Table("sales_records") {
    val id = integer("id").autoIncrement()
    val saleDate = varchar("sale_date", 20) // YYYY-MM-DD
    val saleTime = varchar("sale_time", 20)
    val grandTotal = long("grand_total")
    val paidTotal = long("paid_total")
    val unpaidTotal = long("unpaid_total")
    val orderCount = integer("order_count")
    override val primaryKey = PrimaryKey(id)
}

// [메뉴별 판매 상세 테이블]
object OrderItems : Table("order_items") {
    val id = integer("id").autoIncrement()
    val saleId = integer("sale_id").references(Sales.id, onDelete = ReferenceOption.CASCADE)
    val itemName = varchar("item_name", 255)
    val qty = integer("qty")
    val total = long("total")
    override val primaryKey = PrimaryKey(id)
}

object DatabaseFactory {
    fun init() {
        val dbFile = File("starlink.db")
        Database.connect("jdbc:sqlite:${dbFile.absolutePath}", driver = "org.sqlite.JDBC")
        
        transaction {
            // 테이블 생성
            SchemaUtils.create(Menus, Sales, OrderItems)
            
            // 기존 DB에 sort_order 컬럼 추가 마이그레이션 안전장치
            try {
                exec("ALTER TABLE menus ADD COLUMN sort_order INTEGER DEFAULT 0") { rs -> }
            } catch (_: Exception) {
                // 이미 존재할 경우 무시
            }
            
            // 기존 데이터 마이그레이션 (메뉴)
            migrateMenusIfEmpty()

            // 과거 중복 누적된 매출 데이터 자동 정리 및 병합
            cleanupDuplicateSales()

            // 9월 테스트 가상 매출 데이터 시딩
            seedSeptemberTestDataIfNeeded()
        }
    }

    private fun cleanupDuplicateSales() {
        try {
            val dates = Sales.selectAll().map { it[Sales.saleDate] }.distinct()
            dates.forEach { date ->
                val salesRows = Sales.select { Sales.saleDate eq date }.orderBy(Sales.id to SortOrder.ASC).toList()
                if (salesRows.size > 1) {
                    val targetRow = salesRows.last()
                    val targetId = targetRow[Sales.id]
                    
                    var totalGrand = 0L
                    var totalPaid = 0L
                    var totalUnpaid = 0L
                    var totalCount = 0

                    salesRows.forEach { row ->
                        totalGrand += row[Sales.grandTotal]
                        totalPaid += row[Sales.paidTotal]
                        totalUnpaid += row[Sales.unpaidTotal]
                        totalCount += row[Sales.orderCount]
                    }

                    Sales.update({ Sales.id eq targetId }) {
                        it[grandTotal] = totalGrand
                        it[paidTotal] = totalPaid
                        it[unpaidTotal] = totalUnpaid
                        it[orderCount] = totalCount
                    }

                    salesRows.dropLast(1).forEach { oldRow ->
                        val oldId = oldRow[Sales.id]
                        OrderItems.update({ OrderItems.saleId eq oldId }) {
                            it[OrderItems.saleId] = targetId
                        }
                        Sales.deleteWhere { Sales.id eq oldId }
                    }
                }

                // 동일 saleId 내에서 itemName이 같은 OrderItems 합산 정리
                val saleIds = Sales.select { Sales.saleDate eq date }.map { it[Sales.id] }
                saleIds.forEach { sId ->
                    val items = OrderItems.select { OrderItems.saleId eq sId }.toList()
                    val grouped = items.groupBy { it[OrderItems.itemName] }
                    grouped.forEach { (_, group) ->
                        if (group.size > 1) {
                            val first = group.first()
                            val firstId = first[OrderItems.id]
                            val sumQty = group.sumOf { it[OrderItems.qty] }
                            val sumTotal = group.sumOf { it[OrderItems.total] }

                            OrderItems.update({ OrderItems.id eq firstId }) {
                                it[qty] = sumQty
                                it[total] = sumTotal
                            }
                            group.drop(1).forEach { duplicate ->
                                OrderItems.deleteWhere { OrderItems.id eq duplicate[OrderItems.id] }
                            }
                        }
                    }
                }
            }
            println("✅ [Migration] 과거 중복 누적된 매출 데이터 및 아이템을 깔끔하게 병합 정돈했습니다.")
        } catch (e: Exception) {
            println("⚠️ [Migration] 매출 데이터 정돈 실패: ${e.message}")
        }
    }

    private fun seedSeptemberTestDataIfNeeded() {
        // OOM(Exit 137) 방지를 위해 대량 시딩 비활성화
        return
    }

    private fun migrateMenusIfEmpty() {
        if (Menus.selectAll().count() == 0L) {
            val menuFile = findFile("menus.json") ?: File("menus.json")
            if (menuFile.exists()) {
                try {
                    val content = menuFile.readText()
                    val menus = Json.decodeFromString<List<JsonObject>>(content)
                    menus.forEachIndexed { index, m ->
                        Menus.insert {
                            it[id] = m["id"]?.jsonPrimitive?.content ?: ""
                            it[name] = m["name"]?.jsonPrimitive?.content ?: ""
                            it[price] = m["price"]?.jsonPrimitive?.int ?: 0
                            it[category] = m["category"]?.jsonPrimitive?.content ?: ""
                            it[img] = m["img"]?.jsonPrimitive?.content ?: ""
                            it[sortOrder] = index
                        }
                    }
                    println("✅ [Migration] menus.json 데이터를 DB로 성공적으로 이전했습니다.")
                } catch (e: Exception) {
                    println("⚠️ [Migration] 메뉴 이전 실패: ${e.message}")
                }
            }
        }
    }
    
    // 영업 마감 시 호출되는 함수 (실시간 누적된 매출 데이터를 보호하고 마감 시간/상태만 확정)
    fun saveDailySales(salesData: JsonObject) {
        val dateStr = salesData["date"]?.jsonPrimitive?.content ?: LocalDate.now().toString()
        val timeStr = salesData["time"]?.jsonPrimitive?.content ?: LocalTime.now().toString().substringBefore(".")
        transaction {
            val existingSale = Sales.select { Sales.saleDate eq dateStr }.orderBy(Sales.id to SortOrder.DESC).firstOrNull()
            if (existingSale != null) {
                // 이미 실시간으로 누적된 매출이 존재하므로, 클라이언트의 불완전한 스냅샷으로 grandTotal을 덮어쓰지 않고
                // 기존 누적값과 클라이언트 값 중 더 큰 값을 유지하면서 마감 시간만 업데이트합니다.
                val currentGrand = existingSale[Sales.grandTotal]
                val clientGrand = salesData["grandTotal"]?.jsonPrimitive?.long ?: 0L
                val finalGrand = maxOf(currentGrand, clientGrand)

                Sales.update({ Sales.id eq existingSale[Sales.id] }) {
                    it[saleTime] = timeStr
                    it[grandTotal] = finalGrand
                }
            } else {
                // 만약 실시간 누적이 전혀 없는 상태에서 마감이 요청된 경우에만 안전하게 삽입
                Sales.insert {
                    it[saleDate] = dateStr
                    it[saleTime] = timeStr
                    it[grandTotal] = salesData["grandTotal"]?.jsonPrimitive?.long ?: 0L
                    it[paidTotal] = salesData["paidTotal"]?.jsonPrimitive?.long ?: 0L
                    it[unpaidTotal] = salesData["unpaidTotal"]?.jsonPrimitive?.long ?: 0L
                    it[orderCount] = salesData["orderCount"]?.jsonPrimitive?.int ?: 0
                }
            }
        }
    }

    // 실시간 주문 결제 시 자동 누적 백업 (마감을 깜빡해도 데이터 유실 방지)
    fun recordOrderSale(amount: Long, items: JsonArray, isPaid: Boolean = true) {
        val dateStr = LocalDate.now().toString()
        val timeStr = LocalTime.now().toString().substringBefore(".")
        transaction {
            val existingSale = Sales.select { Sales.saleDate eq dateStr }.orderBy(Sales.id to SortOrder.DESC).firstOrNull()
            val sId = if (existingSale != null) {
                val id = existingSale[Sales.id]
                val oldGrand = existingSale[Sales.grandTotal]
                val oldPaid = existingSale[Sales.paidTotal]
                val oldUnpaid = existingSale[Sales.unpaidTotal]
                val oldCnt = existingSale[Sales.orderCount]
                
                Sales.update({ Sales.id eq id }) {
                    it[saleTime] = timeStr
                    it[grandTotal] = oldGrand + amount
                    if (isPaid) {
                        it[paidTotal] = oldPaid + amount
                    } else {
                        it[unpaidTotal] = oldUnpaid + amount
                    }
                    it[orderCount] = oldCnt + 1
                }
                id
            } else {
                Sales.insert {
                    it[saleDate] = dateStr
                    it[saleTime] = timeStr
                    it[grandTotal] = amount
                    it[paidTotal] = if (isPaid) amount else 0L
                    it[unpaidTotal] = if (isPaid) 0L else amount
                    it[orderCount] = 1
                } get Sales.id
            }

            items.forEach { itemEl ->
                val itemObj = itemEl.jsonObject
                val itemName = itemObj["name"]?.jsonPrimitive?.content ?: "기본 메뉴"
                val qty = itemObj["qty"]?.jsonPrimitive?.int ?: 1
                val price = itemObj["price"]?.jsonPrimitive?.long ?: itemObj["unitPrice"]?.jsonPrimitive?.long ?: 0L
                val itemTotal = if (price > 0) price * qty else amount

                val existingItem = OrderItems.select { (OrderItems.saleId eq sId) and (OrderItems.itemName eq itemName) }.firstOrNull()
                if (existingItem != null) {
                    val itemId = existingItem[OrderItems.id]
                    val oldQty = existingItem[OrderItems.qty]
                    val oldTotal = existingItem[OrderItems.total]
                    OrderItems.update({ OrderItems.id eq itemId }) {
                        it[OrderItems.qty] = oldQty + qty
                        it[OrderItems.total] = oldTotal + itemTotal
                    }
                } else {
                    OrderItems.insert {
                        it[OrderItems.saleId] = sId
                        it[OrderItems.itemName] = itemName
                        it[OrderItems.qty] = qty
                        it[OrderItems.total] = itemTotal
                    }
                }
            }
        }
    }

    fun recordPaymentSettlement(amount: Long) {
        val dateStr = LocalDate.now().toString()
        transaction {
            val existingSale = Sales.select { Sales.saleDate eq dateStr }.orderBy(Sales.id to SortOrder.DESC).firstOrNull()
            if (existingSale != null) {
                val id = existingSale[Sales.id]
                val oldPaid = existingSale[Sales.paidTotal]
                val oldUnpaid = existingSale[Sales.unpaidTotal]
                Sales.update({ Sales.id eq id }) {
                    it[paidTotal] = oldPaid + amount
                    it[unpaidTotal] = maxOf(0L, oldUnpaid - amount)
                }
            } else {
                Sales.insert {
                    it[saleDate] = dateStr
                    it[saleTime] = LocalTime.now().toString().substringBefore(".")
                    it[grandTotal] = amount
                    it[paidTotal] = amount
                    it[unpaidTotal] = 0L
                    it[orderCount] = 0
                }
            }
        }
    }
}
