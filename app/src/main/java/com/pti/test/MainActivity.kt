package com.pti.test

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val Teal = Color(0xFF0F4C5C)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(primary = Teal, secondary = Color(0xFF12707F))
            ) {
                PtiApp()
            }
        }
    }
}

enum class Tab(val label: String) {
    QUERY("查机"),
    RECORDS("申请记录"),
    ALERTS("报警"),
    MY("我的")
}

@Composable
fun PtiApp() {
    val state = remember { PtiState() }
    var tab by remember { mutableStateOf(Tab.QUERY) }
    var detail: Machine? by remember { mutableStateOf(null) }
    var showScan by remember { mutableStateOf(false) }
    var showManage by remember { mutableStateOf(false) }
    val user = state.currentUser

    if (user == null) {
        LoginScreen(state)
        return
    }
    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == Tab.QUERY, onClick = { tab = Tab.QUERY; detail = null; showScan = false; showManage = false },
                    icon = { androidx.compose.material3.Icon(Icons.Filled.Search, contentDescription = null) },
                    label = { Text("查机") }
                )
                NavigationBarItem(
                    selected = tab == Tab.RECORDS, onClick = { tab = Tab.RECORDS; detail = null; showScan = false; showManage = false },
                    icon = { androidx.compose.material3.Icon(Icons.Filled.History, contentDescription = null) },
                    label = { Text("申请记录") }
                )
                NavigationBarItem(
                    selected = tab == Tab.ALERTS, onClick = { tab = Tab.ALERTS; detail = null; showScan = false; showManage = false },
                    icon = { androidx.compose.material3.Icon(Icons.Filled.Warning, contentDescription = null) },
                    label = { Text("报警") }
                )
                NavigationBarItem(
                    selected = tab == Tab.MY, onClick = { tab = Tab.MY; detail = null; showScan = false; showManage = false },
                    icon = { androidx.compose.material3.Icon(Icons.Filled.Person, contentDescription = null) },
                    label = { Text("我的") }
                )
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                showScan -> ScanScreen(
                    onScanned = { code ->
                        val m = state.machines.firstOrNull {
                            it.machineNo == code || it.powerBoxSn == code || it.boardSn == code
                        }
                        if (m != null) {
                            detail = m
                            showScan = false
                            tab = Tab.QUERY
                        } else {
                            val order = state.orders.firstOrNull { it.orderNo == code }
                            if (order != null) {
                                val mm = state.machines.firstOrNull { it.machineNo == order.machineNo }
                                if (mm != null) detail = mm
                                showScan = false
                                tab = Tab.QUERY
                                state.message = "扫到单号 $code，对应机器 ${order.machineNo}，请在详情里做发货核验"
                            } else {
                                state.message = "未找到：$code（测试版模拟数据里没有这条）"
                                showScan = false
                            }
                        }
                    }
                )
                showManage -> ManageScreen(state) { showManage = false }
                detail != null -> MachineDetailScreen(state, detail!!, onBack = { detail = null }, onScanShip = { showScan = true })
                else -> when (tab) {
                    Tab.QUERY -> MachineListScreen(state, onOpen = { detail = it }, onScan = { showScan = true })
                    Tab.RECORDS -> RecordsScreen(state)
                    Tab.ALERTS -> AlertsScreen(state) { detail = it }
                    Tab.MY -> MyScreen(state, onManage = { showManage = true }, onLogout = { state.currentUser = null })
                }
            }
        }
    }
    // 扫码图标在查机页已有按钮；底部导航不另设扫码 Tab，见架构 §6.4
    val unused = Icons.Filled.QrCodeScanner
}

@Composable
fun LoginScreen(state: PtiState) {
    var phone by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Spacer(Modifier.height(48.dp))
        Text("二手机批签", style = MaterialTheme.typography.headlineLarge)
        Text("测试版 · 模拟数据 · 无单不发货", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(phone, { phone = it }, label = { Text("手机号（用户名）") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(password, { password = it }, label = { Text("密码") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))
        if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error)
        Button(
            onClick = {
                val u = state.users.firstOrNull { it.phone == phone.trim() && it.password == password }
                if (u == null) {
                    error = "手机号或密码不对（测试账号见下方）"
                } else {
                    state.currentUser = u
                    error = ""
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("登录") }
        Spacer(Modifier.height(16.dp))
        Text("测试账号：", style = MaterialTheme.typography.titleMedium)
        Text("管理员：13800000000 / 123456（王工）", style = MaterialTheme.typography.bodyMedium)
        Text("申请人：13900000000 / 123456（李工）", style = MaterialTheme.typography.bodyMedium)
        Text("测试版账号仅供安装后试用，正式版由管理员在管理界面创建账号，测试账号必须删除。", style = MaterialTheme.typography.bodySmall)
    }
}
