package com.pti.test

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// 测试版说明：数据全部在内存里，模拟数据来自老高参考图（物品名、PFK 码、申请日期等）。
// 重启 App 后恢复模拟数据；正式版改为 Room + 服务端，不沿用这套内存数据。

data class MockUser(
    val phone: String,
    val password: String,
    val name: String,
    val role: String // 管理员 / 申请人 / 发货核验人 / 只读
)

data class Machine(
    val id: String,
    val machineNo: String,
    val displayName: String,
    val customer: String,
    val powerBoxSn: String,
    val boardSn: String,
    val startDate: String,
    val endDate: String,
    val lastSignDate: String,
    val assetPhoto: String? = null,
    var status: String = "在保"
)

data class BatchOrder(
    val orderNo: String,
    val machineNo: String,
    val machineName: String,
    val applicantName: String,
    val appliedAt: String,
    var status: String, // 已通过 / 已拦截 / 已发货 / 已作废
    val ruleResult: String,
    var checkerName: String = ""
)

data class AuditItem(
    val time: String,
    val actorName: String,
    val action: String,
    val detail: String
)

fun todayStr(): String {
    return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
}

fun daysBetween(from: String, to: String): Long {
    return try {
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val a = fmt.parse(from) ?: return 0L
        val b = fmt.parse(to) ?: return 0L
        (b.time - a.time) / (24 * 60 * 60 * 1000)
    } catch (_: Exception) {
        0L
    }
}

/** 机器状态：以今天为准算。已过保 = endDate < 今天；临近到期 = 30 天内到期；其余在保。 */
fun warrantyStatus(m: Machine): String {
    val today = todayStr()
    if (m.endDate < today) return "已过保"
    if (daysBetween(today, m.endDate) <= 30) return "临近到期"
    return "在保"
}

/** 闲置天数：今天 - 上次批签日。 */
fun idleDays(m: Machine): Long {
    return daysBetween(m.lastSignDate, todayStr())
}

fun seedMachines(): MutableList<Machine> {
    return mutableListOf(
        Machine("1", "M-2024001", "测温枪", "客户A", "PB-0888001", "BD-0734001", "2025-01-10", "2027-01-10", "2026-10-20", "photo1.jpg"),
        Machine("2", "M-2024002", "REINSHAW ENCODER SETUP KIT", "客户A", "PB-0888002", "BD-0734002", "2024-06-01", "2026-11-20", "2026-08-01", "photo2.jpg"),
        Machine("3", "M-2024003", "OBPF P-PART", "客户A", "PB-0888003", "BD-0755001", "2024-02-01", "2026-05-31", "2025-12-01", "photo3.jpg"),
        Machine("4", "M-2024004", "BB SERVICE DONGLE", "客户B", "PB-1169801", "116980018", "2025-03-01", "2027-03-15", "2026-09-07", null),
        Machine("5", "M-2024005", "TABLE MAPPING LA", "客户B", "PB-0888005", "BD-0755002", "2024-09-01", "2026-12-31", "2025-10-01", null),
        Machine("6", "M-2024006", "TABLE MAPPING ELA", "客户B", "PB-0888006", "BD-0755003", "2025-05-01", "2027-02-28", "2026-10-25", null),
        Machine("7", "M-2024007", "Crosshair Offset Mapping Jig", "客户C", "PB-0888007", "0734-000-X", "2024-04-01", "2026-10-15", "2026-03-01", null),
        Machine("8", "M-2024008", "ProCu Setup Gauge", "客户C", "PB-0888008", "0166-000-03", "2025-07-01", "2027-08-01", "2026-09-30", null),
        Machine("9", "M-2024009", "ProCu EFO Setup", "客户C", "PB-0888009", "0755-001-01", "2024-11-01", "2026-11-05", "2026-04-15", null),
        Machine("10", "M-2024010", "UC Flowhead Setup Gauge", "客户A", "PB-0888010", "0755-000-00", "2025-08-01", "2027-05-01", "2026-10-10", null),
        Machine("11", "M-2024011", "测温专用备机", "客户B", "PB-0888011", "BD-0734003", "2023-06-01", "2025-12-31", "2025-06-01", null),
        Machine("12", "M-2024012", "Reinsaw Z Encoder 校准工具", "客户C", "PB-0888012", "BD-0734004", "2025-09-01", "2027-09-01", "2026-10-28", null)
    )
}

fun seedOrders(): MutableList<BatchOrder> {
    return mutableListOf(
        BatchOrder("PTI-20261020-001", "M-2024001", "测温枪", "王工", "2026-10-20", "已发货", "通过", "李工"),
        BatchOrder("PTI-20260907-001", "M-2024004", "BB SERVICE DONGLE", "李工", "2026-09-07", "已通过", "通过"),
        BatchOrder("PTI-20260601-001", "M-2024003", "OBPF P-PART", "王工", "2026-06-01", "已拦截", "已过保：保修到期日 2026-05-31，申请时已过保"),
        BatchOrder("PTI-20261025-001", "M-2024012", "Reinsaw Z Encoder 校准工具", "王工", "2026-10-25", "已通过", "通过")
    )
}

fun seedUsers(): MutableList<MockUser> {
    return mutableListOf(
        MockUser("13800000000", "123456", "王工", "管理员"),
        MockUser("13900000000", "123456", "李工", "申请人")
    )
}

fun nextOrderNo(orders: List<BatchOrder>): String {
    val today = todayStr().replace("-", "")
    val count = orders.count { it.orderNo.contains(today) } + 1
    return "PTI-$today-%03d".format(count)
}

fun addDays(dateStr: String, days: Int): String {
    return try {
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val cal = Calendar.getInstance()
        cal.time = fmt.parse(dateStr) ?: Date()
        cal.add(Calendar.DAY_OF_YEAR, days)
        fmt.format(cal.time)
    } catch (_: Exception) {
        dateStr
    }
}
