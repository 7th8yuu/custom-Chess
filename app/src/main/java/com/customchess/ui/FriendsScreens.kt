package com.customchess.ui

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.customchess.net.Bt
import com.customchess.net.Lan
import com.customchess.vm.AppViewModel
import com.customchess.vm.Screen
import com.customchess.vm.TIME_PRESETS

@Composable
fun FriendsScreen(vm: AppViewModel) {
    Column(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 20.dp)) {
        ScreenHeader("Chơi với bạn bè", vm::back)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Text2("Một người mở phòng, người kia vào phòng. Không cần Internet.", Type.Small)
            SectionTitle("Wi-Fi")
            ChoiceRow("Mở phòng", "Bạn bè nhập địa chỉ của bạn hoặc tự tìm thấy trong cùng mạng", false, { vm.go(Screen.WIFI_HOST) })
            Spacer(Modifier.height(8.dp))
            ChoiceRow("Vào phòng", "Tìm phòng của bạn bè trong cùng mạng Wi-Fi", false, { vm.go(Screen.WIFI_JOIN) })
            SectionTitle("Bluetooth")
            ChoiceRow("Mở phòng", "Cho phép bạn bè tìm thấy máy này", false, { vm.go(Screen.BT_HOST) })
            Spacer(Modifier.height(8.dp))
            ChoiceRow("Vào phòng", "Chọn thiết bị của bạn bè để kết nối", false, { vm.go(Screen.BT_JOIN) })
            SectionTitle("Cùng một máy")
            ChoiceRow("Hai người một điện thoại", "Thay phiên nhau đi, có thể bật đồng hồ", false, { vm.go(Screen.LOCAL_SETUP) })
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
fun LocalSetupScreen(vm: AppViewModel) {
    var time by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 20.dp)) {
        ScreenHeader("Cùng một máy", vm::back)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            SectionTitle("Đồng hồ")
            TimePills(time) { time = it }
            Spacer(Modifier.height(12.dp))
            Text2("Trắng đi trước. Bấm Lật bàn trong ván nếu muốn xoay bàn cho người kia.", Type.Small)
        }
        PrimaryButton("Bắt đầu", { vm.startLocalGame(TIME_PRESETS[time]) }, Modifier.padding(vertical = 16.dp))
    }
}

@Composable
private fun TimePills(selected: Int, onSelect: (Int) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(TIME_PRESETS.size) { i -> Pill(TIME_PRESETS[i].label, selected == i, { onSelect(i) }) }
    }
}

/** Chọn màu quân và đồng hồ của người mở phòng. */
@Composable
private fun HostOptions(vm: AppViewModel) {
    SectionTitle("Bạn cầm quân")
    ColorPills(vm.lobbyColor) { vm.lobbyColor = it }
    SectionTitle("Đồng hồ")
    TimePills(vm.lobbyTime) { vm.lobbyTime = it }
}

@Composable
private fun Waiting(text: String) {
    Row(Modifier.padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(Modifier.size(18.dp), color = Pal.Gold, strokeWidth = 2.dp)
        Text2(text, Type.Body.copy(color = Pal.EggDim), Modifier.padding(start = 12.dp))
    }
}

// ---------------------------------------------------------------------------------- Wi-Fi

@Composable
fun WifiHostScreen(vm: AppViewModel) {
    LaunchedEffect(Unit) { vm.startWifiHost() }
    Column(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 20.dp)) {
        ScreenHeader("Mở phòng Wi-Fi", vm::back)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Text2("Địa chỉ của bạn", Type.Small)
            val first = vm.hostAddresses.firstOrNull()
            Text2(first ?: "Chưa có mạng", Type.Display.copy(fontSize = 40.sp))
            Text2("Cổng ${Lan.PORT}", Type.Small)
            if (vm.hostAddresses.size > 1) {
                Text2("Địa chỉ khác: " + vm.hostAddresses.drop(1).joinToString(", "), Type.Small, Modifier.padding(top = 4.dp))
            }
            if (vm.hostWaiting) Waiting("Đang chờ bạn bè vào phòng...")
            ErrorText(vm.lobbyError)
            HostOptions(vm)
            Spacer(Modifier.height(20.dp))
            InfoCard(
                "Bạn bè bấm Vào phòng và sẽ thấy phòng này, hoặc nhập địa chỉ ở trên. " +
                    "Nếu không có Wi-Fi chung, hãy bật điểm phát sóng (hotspot) trên một máy rồi máy kia kết nối vào.",
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
fun WifiJoinScreen(vm: AppViewModel) {
    var manual by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { vm.startWifiScan() }
    Column(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 20.dp)) {
        ScreenHeader("Vào phòng Wi-Fi", vm::back)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Waiting("Đang tìm phòng trong mạng này...")
            if (vm.lanHosts.isEmpty()) {
                Text2("Chưa thấy phòng nào. Hãy chắc rằng bạn bè đã bấm Mở phòng và hai máy cùng mạng.", Type.Small)
            }
            for (h in vm.lanHosts) {
                Spacer(Modifier.height(8.dp))
                ChoiceRow(h.name, "${h.address}:${h.port}", false, { vm.connectWifi(h.address, h.port) }, trailing = "Vào")
            }
            SectionTitle("Nhập địa chỉ thủ công")
            OutlinedTextField(
                value = manual, onValueChange = { manual = it },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
                placeholder = { Text2("Ví dụ 192.168.1.5", Type.Small) },
                colors = fieldColors(),
            )
            Spacer(Modifier.height(10.dp))
            PrimaryButton(if (vm.lobbyBusy) "Đang kết nối..." else "Kết nối", { vm.connectWifi(manual) }, enabled = !vm.lobbyBusy)
            ErrorText(vm.lobbyError)
            Spacer(Modifier.height(24.dp))
        }
    }
}

// ---------------------------------------------------------------------------------- Bluetooth

/** Xin quyền Bluetooth; trả về (đã cấp, hàm xin lại). */
@Composable
private fun rememberBtPermission(): Pair<Boolean, () -> Unit> {
    val ctx = LocalContext.current
    val perms = remember { Bt.requiredPermissions() }
    fun has() = perms.all { ContextCompat.checkSelfPermission(ctx, it) == PackageManager.PERMISSION_GRANTED }
    var granted by remember { mutableStateOf(has()) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted = has() }
    LaunchedEffect(Unit) { if (!granted) launcher.launch(perms) }
    return granted to { launcher.launch(perms) }
}

@Composable
private fun PermissionCard(onRequest: () -> Unit) {
    InfoCard("Cần quyền Bluetooth để tìm và kết nối thiết bị. Bấm nút bên dưới để cấp quyền.")
    Spacer(Modifier.height(10.dp))
    PrimaryButton("Cấp quyền Bluetooth", onRequest)
}

@Composable
fun BtHostScreen(vm: AppViewModel) {
    val ctx = LocalContext.current
    val (granted, request) = rememberBtPermission()
    val discoverable = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { }
    LaunchedEffect(granted) { if (granted) vm.startBtHost() }
    Column(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 20.dp)) {
        ScreenHeader("Mở phòng Bluetooth", vm::back)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            if (!granted) {
                PermissionCard(request)
            } else {
                if (vm.hostWaiting) Waiting("Đang chờ bạn bè kết nối...")
                ErrorText(vm.lobbyError)
                HostOptions(vm)
                Spacer(Modifier.height(20.dp))
                PrimaryButton("Cho bạn bè tìm thấy máy này", {
                    val i = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE)
                        .putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 180)
                    runCatching { discoverable.launch(i) }
                })
                Spacer(Modifier.height(10.dp))
                GhostButton("Mở cài đặt Bluetooth", {
                    runCatching { ctx.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }
                }, Modifier.fillMaxWidth())
                Spacer(Modifier.height(16.dp))
                InfoCard("Nếu hai máy chưa từng ghép đôi, máy kia sẽ hiện yêu cầu ghép đôi trong lần kết nối đầu tiên.")
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun BtDiscovery(vm: AppViewModel, active: Boolean) {
    val ctx = LocalContext.current
    DisposableEffect(active) {
        if (!active) return@DisposableEffect onDispose { }
        val adapter = Bt.adapter(ctx)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                if (intent.action == BluetoothDevice.ACTION_FOUND) {
                    try { Bt.deviceOf(intent)?.let { vm.addBtDevice(Bt.toDevice(it)) } } catch (_: SecurityException) { }
                }
            }
        }
        ContextCompat.registerReceiver(ctx, receiver, IntentFilter(BluetoothDevice.ACTION_FOUND), ContextCompat.RECEIVER_EXPORTED)
        try { adapter?.startDiscovery() } catch (_: SecurityException) { }
        onDispose {
            try { adapter?.cancelDiscovery() } catch (_: SecurityException) { }
            try { ctx.unregisterReceiver(receiver) } catch (_: Exception) { }
        }
    }
}

@Composable
fun BtJoinScreen(vm: AppViewModel) {
    val ctx = LocalContext.current
    val (granted, request) = rememberBtPermission()
    LaunchedEffect(granted) { if (granted) vm.refreshPairedDevices() }
    BtDiscovery(vm, granted)
    Column(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 20.dp)) {
        ScreenHeader("Vào phòng Bluetooth", vm::back)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            if (!granted) {
                PermissionCard(request)
            } else {
                Text2("Bạn bè cần đang ở màn hình Mở phòng. Chọn thiết bị của họ bên dưới.", Type.Small)
                Waiting("Đang tìm thiết bị gần đây...")
                if (vm.lobbyBusy) Text2("Đang kết nối...", Type.Small.copy(color = Pal.Gold))
                for (d in vm.btDevices) {
                    Spacer(Modifier.height(8.dp))
                    ChoiceRow(d.name, d.address, false, { vm.connectBt(d.address) }, trailing = if (d.paired) "Đã ghép đôi" else "Mới")
                }
                ErrorText(vm.lobbyError)
                Spacer(Modifier.height(16.dp))
                GhostButton("Mở cài đặt Bluetooth", {
                    runCatching { ctx.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }
                }, Modifier.fillMaxWidth())
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
