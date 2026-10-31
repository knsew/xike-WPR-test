package com.pti.test

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.io.File
import java.io.FileOutputStream

class PtiState {
    val machines: SnapshotStateList<Machine> = androidx.compose.runtime.mutableStateListOf<Machine>().apply { addAll(seedMachines()) }
    val orders: SnapshotStateList<BatchOrder> = androidx.compose.runtime.mutableStateListOf<BatchOrder>().apply { addAll(seedOrders()) }
    val users: SnapshotStateList<MockUser> = androidx.compose.runtime.mutableStateListOf<MockUser>().apply { addAll(seedUsers()) }
    val audits: SnapshotStateList<AuditItem> = androidx.compose.runtime.mutableStateListOf<AuditItem>().apply {
        add(AuditItem("2026-10-20", "王工", "发货核验", "M-2024001 单号 PTI-20261020-001 核验通过，已发货"))
        add(AuditItem("2026-09-07", "李工", "申请提交", "M-2024004 单号 PTI-20260907-001 生成"))
        add(AuditItem("2026-06-01", "王工", "规则拦截", "M-2024003 已过保，申请被拦截"))
    }
    val capturedPhotos: MutableMap<String, MutableList<String>> = mutableMapOf()
    var currentUser: MockUser? by mutableStateOf(null)
    var message: String by mutableStateOf("")
}

fun statusColor(status: String): Color {
    return when (status) {
        "已过保" -> Color(0xFFB3261E)
        "临近到期" -> Color(0xFF9A6700)
        "在保" -> Color(0xFF1E7D32)
        "已拦截" -> Color(0xFFB3261E)
        "已通过" -> Color(0xFF1E7D32)
        "已发货" -> Color(0xFF0F4C5C)
        "已作废" -> Color(0xFF757575)
        else -> Color(0xFF757575)
    }
}

fun assetBitmap(context: Context, name: String): Bitmap? {
    return try {
        context.assets.open("mock_photos/$name").use { BitmapFactory.decodeStream(it) }
    } catch (_: Exception) {
        null
    }
}

fun fileBitmap(path: String): Bitmap? {
    return try {
        BitmapFactory.decodeFile(path)
    } catch (_: Exception) {
        null
    }
}

@Composable
fun PhotoThumb(bitmap: Bitmap?, modifier: Modifier = Modifier) {
    if (bitmap != null) {
        Image(bitmap.asImageBitmap(), contentDescription = "机器照片", modifier = modifier.size(96.dp))
    } else {
        Card(modifier = modifier.size(96.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFE0E0E0))) {
            Column(Modifier.padding(8.dp)) {
                Text("无照片", style = MaterialTheme.typography.bodySmall)
                Text("点详情可拍照", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
fun MachineCard(m: Machine, onClick: () -> Unit) {
    val context = LocalContext.current
    val bmp = remember(m.assetPhoto) { if (m.assetPhoto != null) assetBitmap(context, m.assetPhoto) else null }
    val st = warrantyStatus(m)
    val idle = idleDays(m)
    Card(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PhotoThumb(bmp)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(m.machineNo, style = MaterialTheme.typography.titleMedium)
                Text(m.displayName, style = MaterialTheme.typography.bodyMedium)
                Text("${m.customer} · 到期 ${m.endDate}", style = MaterialTheme.typography.bodySmall)
                Text(st, color = statusColor(st), style = MaterialTheme.typography.bodyMedium)
                if (idle >= 180) Text("长期未批签：已闲置 $idle 天", color = Color(0xFF9A6700), style = MaterialTheme.typography.bodySmall)
                Text("上次批签：${m.lastSignDate}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
fun MachineListScreen(state: PtiState, onOpen: (Machine) -> Unit, onScan: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("全部") }
    var customer by remember { mutableStateOf("全部客户") }
    val list = state.machines.filter { m ->
        (query.isBlank() || m.machineNo.contains(query, true) || m.displayName.contains(query, true) || m.powerBoxSn.contains(query, true) || m.boardSn.contains(query, true)) &&
            (filter == "全部" || warrantyStatus(m) == filter || (filter == "长期未批签" && idleDays(m) >= 180)) &&
            (customer == "全部客户" || m.customer == customer)
    }
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("查机（测试版）", style = MaterialTheme.typography.headlineSmall)
            Text("模拟数据 12 台 · 3 家客户 · 照片来自老高参考图", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(query, { query = it }, label = { Text("机号 / 名称 / 序列号") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("全部", "在保", "临近到期", "已过保", "长期未批签").forEach { f ->
                    OutlinedButton(onClick = { filter = f }) { Text(f) }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("全部客户", "客户A", "客户B", "客户C").forEach { c ->
                    OutlinedButton(onClick = { customer = c }) { Text(c) }
                }
            }
            Button(onClick = onScan, modifier = Modifier.fillMaxWidth()) { Text("扫码查机（条码 / 二维码）") }
            Text("当前筛选：$filter · $customer · 共 ${list.size} 台", style = MaterialTheme.typography.bodySmall)
        }
        items(list) { m -> MachineCard(m) { onOpen(m) } }
    }
}

@Composable
fun MachineDetailScreen(state: PtiState, machine: Machine, onBack: () -> Unit, onScanShip: () -> Unit) {
    val context = LocalContext.current
    var showApply by remember { mutableStateOf(false) }
    var showShip by remember { mutableStateOf(false) }
    var shipCode by remember { mutableStateOf("") }
    var shipMsg by remember { mutableStateOf("") }
    var photoTick by remember { mutableStateOf(0) }
    val assetBmp = remember(machine.assetPhoto) { if (machine.assetPhoto != null) assetBitmap(context, machine.assetPhoto) else null }
    val captured = state.capturedPhotos[machine.id] ?: emptyList()
    val photoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) {
            val list = state.capturedPhotos.getOrPut(machine.id) { mutableListOf() }
            val dir = File(context.filesDir, "photos")
            if (!dir.exists()) dir.mkdirs()
            val f = File(dir, "${machine.id}_${System.currentTimeMillis()}.jpg")
            // TakePicture 需要预先给 Uri，见下方 photoUri；这里只刷新
            photoTick++
            state.message = "拍照已保存（测试版：照片存手机本地）"
        }
    }
    var photoUri by remember { mutableStateOf<Uri?>(null) }
    val st = warrantyStatus(machine)
    val records = state.orders.filter { it.machineNo == machine.machineNo }
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            OutlinedButton(onClick = onBack) { Text("返回查机") }
            Text(machine.machineNo, style = MaterialTheme.typography.headlineSmall)
            Text(machine.displayName, style = MaterialTheme.typography.titleMedium)
            Text("${machine.customer} · 起始 ${machine.startDate} · 到期 ${machine.endDate}", style = MaterialTheme.typography.bodyMedium)
            Text(st, color = statusColor(st), style = MaterialTheme.typography.titleMedium)
            Text("电源箱：${machine.powerBoxSn}", style = MaterialTheme.typography.bodyMedium)
            Text("板子：${machine.boardSn}", style = MaterialTheme.typography.bodyMedium)
            Text("上次批签：${machine.lastSignDate} · 闲置 ${idleDays(machine)} 天", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            Text("机器照片", style = MaterialTheme.typography.titleMedium)
        }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PhotoThumb(assetBmp)
                captured.forEach { path ->
                    val b = remember(path, photoTick) { fileBitmap(path) }
                    PhotoThumb(b)
                }
            }
            Button(onClick = {
                val dir = File(context.filesDir, "photos")
                if (!dir.exists()) dir.mkdirs()
                val f = File(dir, "${machine.id}_${System.currentTimeMillis()}.jpg")
                val uri = androidx.core.content.FileProvider.getUriForFile(context, context.packageName + ".fileprovider", f)
                val list = state.capturedPhotos.getOrPut(machine.id) { mutableListOf() }
                list.add(f.absolutePath)
                photoUri = uri
                photoLauncher.launch(uri)
            }) { Text("拍照") }
            Text("测试版：拍照存手机本地，重启后模拟数据会恢复，实拍照片不随模拟数据保存", style = MaterialTheme.typography.bodySmall)
        }
        item {
            Button(onClick = { showApply = true }, modifier = Modifier.fillMaxWidth()) { Text("申请批签") }
            OutlinedButton(onClick = { showShip = true }, modifier = Modifier.fillMaxWidth()) { Text("发货核验（无单不发货）") }
            OutlinedButton(onClick = onScanShip, modifier = Modifier.fillMaxWidth()) { Text("扫码核验单号") }
            if (shipMsg.isNotEmpty()) Text(shipMsg, color = MaterialTheme.colorScheme.error)
            Text("申请记录", style = MaterialTheme.typography.titleMedium)
        }
        items(records) { o ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("${o.orderNo} · ${o.status}", color = statusColor(o.status))
                    Text("申请人：${o.applicantName} · ${o.appliedAt}", style = MaterialTheme.typography.bodySmall)
                    Text(o.ruleResult, style = MaterialTheme.typography.bodySmall)
                    if (o.checkerName.isNotEmpty()) Text("核验人：${o.checkerName}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
    if (showApply) {
        val blocked = st == "已过保"
        AlertDialog(
            onDismissRequest = { showApply = false },
            title = { Text("批签申请") },
            text = {
                Column {
                    if (blocked) {
                        Text("规则拦截：已过保，不能提交申请。", color = Color(0xFFB3261E))
                        Text("到期日 ${machine.endDate}，今天 ${todayStr()}，已过保机器仍给客户换新会造成损失，系统直接不允许提交。")
                    } else {
                        Text("规则校验通过：$st，可生成批签单号。")
                        Text("申请人：${state.currentUser?.name} · 机器：${machine.machineNo}")
                    }
                }
            },
            confirmButton = {
                if (!blocked) {
                    TextButton(onClick = {
                        val no = nextOrderNo(state.orders)
                        state.orders.add(BatchOrder(no, machine.machineNo, machine.displayName, state.currentUser?.name ?: "", todayStr(), "已通过", "通过"))
                        state.audits.add(AuditItem(todayStr(), state.currentUser?.name ?: "", "申请提交", "${machine.machineNo} 单号 $no 生成"))
                        showApply = false
                        state.message = "批签单号已生成：$no"
                    }) { Text("确认生成单号") }
                } else {
                    TextButton(onClick = { showApply = false }) { Text("知道了") }
                }
            },
            dismissButton = { TextButton(onClick = { showApply = false }) { Text("取消") } }
        )
    }
    if (showShip) {
        AlertDialog(
            onDismissRequest = { showShip = false },
            title = { Text("发货核验") },
            text = {
                Column {
                    OutlinedTextField(shipCode, { shipCode = it }, label = { Text("批签单号，如 PTI-20261025-001") }, modifier = Modifier.fillMaxWidth())
                    Text("无单不发货：单号必须存在、对应本机、状态为已通过，否则拒绝发货。", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val order = state.orders.firstOrNull { it.orderNo == shipCode.trim() }
                    when {
                        order == null -> shipMsg = "拒绝发货：单号不存在"
                        order.machineNo != machine.machineNo -> shipMsg = "拒绝发货：单号与本机不匹配"
                        order.status != "已通过" -> shipMsg = "拒绝发货：单据状态为 ${order.status}，不是可发货状态"
                        else -> {
                            val idx = state.orders.indexOf(order)
                            if (idx >= 0) state.orders[idx] = order.copy(status = "已发货", checkerName = state.currentUser?.name ?: "")
                            state.audits.add(AuditItem(todayStr(), state.currentUser?.name ?: "", "发货核验", "${machine.machineNo} 单号 ${order.orderNo} 核验通过，已发货"))
                            shipMsg = "核验通过，已发货：${order.orderNo}"
                        }
                    }
                    showShip = false
                }) { Text("核验发货") }
            },
            dismissButton = { TextButton(onClick = { showShip = false }) { Text("取消") } }
        )
    }
}

@Composable
fun RecordsScreen(state: PtiState) {
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("申请记录", style = MaterialTheme.typography.headlineSmall) }
        items(state.orders) { o ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("${o.orderNo} · ${o.status}", color = statusColor(o.status))
                    Text("${o.machineNo} ${o.machineName}", style = MaterialTheme.typography.bodyMedium)
                    Text("申请人：${o.applicantName} · ${o.appliedAt}", style = MaterialTheme.typography.bodySmall)
                    Text(o.ruleResult, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
fun AlertsScreen(state: PtiState, onOpen: (Machine) -> Unit) {
    val alerts = state.machines.filter { warrantyStatus(it) == "已过保" || idleDays(it) >= 180 }
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("报警", style = MaterialTheme.typography.headlineSmall)
            Text("已过保与长期无人批签（闲置 180 天以上）的机器，共 ${alerts.size} 台", style = MaterialTheme.typography.bodySmall)
        }
        items(alerts) { m -> MachineCard(m) { onOpen(m) } }
    }
}

@Composable
fun MyScreen(state: PtiState, onManage: () -> Unit, onLogout: () -> Unit) {
    val user = state.currentUser
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("我的", style = MaterialTheme.typography.headlineSmall)
            if (user != null) {
                Text(user.name, style = MaterialTheme.typography.titleMedium)
                Text("登录账号：${user.phone} · 角色：${user.role}", style = MaterialTheme.typography.bodyMedium)
            }
            if (state.message.isNotEmpty()) Text(state.message, style = MaterialTheme.typography.bodySmall)
            if (user?.role == "管理员") {
                Button(onClick = onManage, modifier = Modifier.fillMaxWidth()) { Text("管理（仅管理员）") }
            } else {
                Text("管理入口仅管理员可见，当前角色无权进入", style = MaterialTheme.typography.bodySmall)
            }
            OutlinedButton(onClick = onLogout, modifier = Modifier.fillMaxWidth()) { Text("退出登录") }
            Text("测试版账号：管理员 13800000000 / 123456（王工）；申请人 13900000000 / 123456（李工）", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun ManageScreen(state: PtiState, onBack: () -> Unit) {
    var tab by remember { mutableStateOf("账号") }
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            OutlinedButton(onClick = onBack) { Text("返回我的") }
            Text("管理", style = MaterialTheme.typography.headlineSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("账号", "客户", "机器", "单据", "规则", "审计").forEach { t ->
                    OutlinedButton(onClick = { tab = t }) { Text(t) }
                }
            }
            Text("当前：$tab", style = MaterialTheme.typography.bodySmall)
        }
        when (tab) {
            "账号" -> items(state.users) { u ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text("${u.name} · ${u.role}")
                        Text("登录账号：${u.phone}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            "客户" -> items(listOf("客户A", "客户B", "客户C")) { c ->
                val n = state.machines.count { it.customer == c }
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(c)
                        Text("名下机器 $n 台", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            "机器" -> items(state.machines) { m ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text("${m.machineNo} ${m.displayName}")
                        Text("${m.customer} · 到期 ${m.endDate} · ${warrantyStatus(m)}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            "单据" -> items(state.orders) { o ->
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text("${o.orderNo} · ${o.status}")
                            Text("${o.machineNo} · ${o.applicantName}", style = MaterialTheme.typography.bodySmall)
                        }
                        if (o.status == "已通过") {
                            TextButton(onClick = {
                                val idx = state.orders.indexOf(o)
                                if (idx >= 0) state.orders[idx] = o.copy(status = "已作废")
                                state.audits.add(AuditItem(todayStr(), state.currentUser?.name ?: "", "单据作废", "${o.orderNo} 已作废，作废后不可再发货"))
                            }) { Text("作废") }
                        }
                    }
                }
            }
            "规则" -> item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text("规则配置（测试版展示，参数待客户确认）")
                        Text("已过保直接拦截，不生成单号；任一 BLOCK 规则命中即拦截；规则错误也拦截", style = MaterialTheme.typography.bodySmall)
                        Text("长期闲置提醒阈值：180 天（示例值，正式版待确认）", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            "审计" -> items(state.audits) { a ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text("${a.time} · ${a.actorName} · ${a.action}")
                        Text(a.detail, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
