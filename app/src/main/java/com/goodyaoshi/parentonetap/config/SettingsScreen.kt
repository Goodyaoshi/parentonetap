package com.goodyaoshi.parentonetap.config

import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.goodyaoshi.parentonetap.call.dial
import com.goodyaoshi.parentonetap.core.perm.PermissionGate
import com.goodyaoshi.parentonetap.location.Fix
import com.goodyaoshi.parentonetap.location.LocateOutcome
import com.goodyaoshi.parentonetap.share.MapLinkBuilder
import com.goodyaoshi.parentonetap.share.WeChatSharer
import com.goodyaoshi.parentonetap.share.showBigToast
import com.goodyaoshi.parentonetap.taxi.TaxiFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val ActionGreen = Color(0xFF1B8A3A)
private val ActionBlue = Color(0xFF1565C0)
private val ActionOrange = Color(0xFFEF6C00)

/**
 * 隐藏设置页（方案 3.6/4，入口：首页标题连点 5 次）。
 * 子女操作：家坐标、联系人、打车渠道、地图服务商、高级项；
 * 全部改动保存即写入 DataStore 全局生效。
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onReenterPermissionGuide: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val vm: ConfigViewModel = hiltViewModel()
    val config by vm.config.collectAsStateWithLifecycle()

    // 家的位置
    var locating by remember { mutableStateOf(false) }
    var pendingHomeFix by remember { mutableStateOf<Fix?>(null) }
    var pasteText by remember { mutableStateOf("") }
    var pasteError by remember { mutableStateOf<String?>(null) }
    var homeName by remember(config.home?.name) {
        mutableStateOf(config.home?.name ?: "家")
    }

    // 联系人编辑
    var showContactDialog by remember { mutableStateOf(false) }
    var contactEditIndex by remember { mutableStateOf(-1) }
    var contactLabel by remember { mutableStateOf("") }
    var contactPhone by remember { mutableStateOf("") }

    // 常用地点编辑
    var showPlaceDialog by remember { mutableStateOf(false) }
    var placeEditName by remember { mutableStateOf("") }
    var placeEditCoord by remember { mutableStateOf("") }
    var placeCoordError by remember { mutableStateOf<String?>(null) }
    var placeParsed by remember { mutableStateOf<PlaceLinkParser.Parsed?>(null) }
    var resolvingHomeLink by remember { mutableStateOf(false) }
    var resolvingPlaceLink by remember { mutableStateOf(false) }

    // 位置名称 / 高级项
    var advancedOpen by remember { mutableStateOf(false) }
    var placeNameText by remember(config.placeName) { mutableStateOf(config.placeName) }

    /** 地图分享短链联网解析（方案 4.2）：阻塞网络请求放 IO 线程 */
    suspend fun resolveShortLink(text: String): PlaceLinkParser.Parsed =
        withContext(Dispatchers.IO) { ShortLinkResolver.resolve(text) }
    var timeoutText by remember(config.locationTimeoutSeconds) {
        mutableStateOf(config.locationTimeoutSeconds.toString())
    }
    var emergencyCustom by remember(config.emergencyCustomPhone) {
        mutableStateOf(config.emergencyCustomPhone)
    }

    // 信息展示
    var lastFix by remember { mutableStateOf<Fix?>(null) }
    LaunchedEffect(Unit) { lastFix = vm.lastFix() }

    // 测试用 launcher
    val testLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { }
    val shareTestLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { showBigToast(context, "测试分享完成") }

    fun locateAndSetHome() {
        scope.launch {
            locating = true
            val outcome = vm.locate()
            locating = false
            if (outcome is LocateOutcome.Success) {
                pendingHomeFix = outcome.fix
            } else {
                showBigToast(context, "定位失败，请到窗边空旷处再试")
            }
        }
    }

    fun openContactDialog(index: Int, contact: ContactEntry?) {
        contactEditIndex = index
        contactLabel = contact?.label.orEmpty()
        contactPhone = contact?.phone.orEmpty()
        showContactDialog = true
    }

    fun saveContact() {
        val label = contactLabel.trim()
        val phone = contactPhone.trim()
        if (label.isEmpty() || phone.isEmpty()) return
        vm.updateConfig { c ->
            val list = c.contacts.toMutableList()
            if (contactEditIndex in list.indices) {
                list[contactEditIndex] = ContactEntry(label, phone)
            } else if (list.size < 6) {
                list.add(ContactEntry(label, phone))
            }
            c.copy(contacts = list)
        }
        showContactDialog = false
    }

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            // 顶栏
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text(text = "返回", fontSize = 22.sp) }
                Text(
                    text = "隐藏设置页",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.width(72.dp))
            }

            SectionTitle("家的位置（打车目的地）")
            Text(
                text = config.home?.let {
                    "已设置：${it.name}（${"%.5f".format(it.lat)}, ${"%.5f".format(it.lng)}）"
                } ?: "未设置。推荐用「设当前位置为家」：手机放在家里时点一下即可。",
                fontSize = 20.sp
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = homeName,
                    onValueChange = { homeName = it },
                    label = { Text("地名", fontSize = 18.sp) },
                    modifier = Modifier.width(140.dp)
                )
                Spacer(Modifier.width(12.dp))
                Button(
                    onClick = { locateAndSetHome() },
                    enabled = !locating,
                    modifier = Modifier.height(64.dp)
                ) {
                    Text(
                        text = if (locating) "定位中…" else "设当前位置为家",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = pasteText,
                    onValueChange = { pasteText = it },
                    label = { Text("粘贴地图分享链接或坐标（备选）", fontSize = 18.sp) },
                    isError = pasteError != null,
                    supportingText = { pasteError?.let { Text(it, fontSize = 16.sp) } },
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(12.dp))
                Button(
                    onClick = {
                        val localParsed = PlaceLinkParser.parse(pasteText)
                        when {
                            localParsed != null -> {
                                vm.updateConfig { c ->
                                    c.copy(
                                        home = HomePlace(
                                            homeName.ifBlank { localParsed.name ?: "家" },
                                            localParsed.lat, localParsed.lng
                                        )
                                    )
                                }
                                pasteError = null
                                pasteText = ""
                                showBigToast(context, "已保存家的位置")
                            }
                            // 地图 App「地点分享」的短链（surl.amap.com 等）：联网跟随跳转解析
                            PlaceLinkParser.isUnresolvableMapLink(pasteText) -> {
                                scope.launch {
                                    resolvingHomeLink = true
                                    pasteError = null
                                    val result = runCatching { resolveShortLink(pasteText) }
                                    resolvingHomeLink = false
                                    result.fold(
                                        onSuccess = { p ->
                                            vm.updateConfig { c ->
                                                c.copy(
                                                    home = HomePlace(
                                                        homeName.ifBlank { p.name ?: "家" },
                                                        p.lat, p.lng
                                                    )
                                                )
                                            }
                                            pasteText = ""
                                            showBigToast(context, "已保存家的位置")
                                        },
                                        onFailure = { e ->
                                            pasteError = e.message ?: "短链解析失败，请检查网络后重试"
                                        }
                                    )
                                }
                            }
                            else -> pasteError =
                                "没识别到坐标，请粘贴地图分享的链接或如 23.12,113.36 的坐标"
                        }
                    },
                    enabled = !resolvingHomeLink,
                    modifier = Modifier.height(64.dp)
                ) {
                    Text(
                        text = if (resolvingHomeLink) "解析中…" else "解析并保存",
                        fontSize = 20.sp
                    )
                }
            }
            Text(
                text = "提示：主推上面的「设当前位置为家」；地图分享的链接（短链自动联网解析）或坐标都能粘贴识别；勿用百度地图复制的坐标（BD-09 会偏几百米）",
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.outline
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = placeNameText,
                onValueChange = {
                    placeNameText = it
                    vm.updateConfig { c -> c.copy(placeName = it) }
                },
                label = { Text("位置名称（发位置时显示的名字，可自定义）", fontSize = 18.sp) },
                modifier = Modifier.fillMaxWidth()
            )

            SectionTitle("联系人（最多 6 个，打电话页/紧急求助共用）")
            config.contacts.forEachIndexed { index, contact ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${contact.label} ${contact.phone}",
                        fontSize = 22.sp,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { openContactDialog(index, contact) }) {
                        Text("编辑", fontSize = 20.sp)
                    }
                    TextButton(onClick = {
                        vm.updateConfig { c ->
                            c.copy(contacts = c.contacts.filterIndexed { i, _ -> i != index })
                        }
                    }) {
                        Text("删除", fontSize = 20.sp, color = Color(0xFFC62828))
                    }
                }
            }
            Button(
                onClick = {
                    if (config.contacts.size >= 6) {
                        showBigToast(context, "最多 6 个联系人")
                    } else {
                        openContactDialog(-1, null)
                    }
                },
                modifier = Modifier.height(64.dp)
            ) {
                Text(text = "添加联系人", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }

            SectionTitle("常用地点（打车/导航备用目的地，最多 3 个）")
            config.extraPlaces.forEachIndexed { index, place ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${place.name}（${"%.5f".format(place.lat)}, ${"%.5f".format(place.lng)}）",
                        fontSize = 20.sp,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = {
                        vm.updateConfig { c ->
                            c.copy(extraPlaces = c.extraPlaces.filterIndexed { i, _ -> i != index })
                        }
                    }) {
                        Text("删除", fontSize = 20.sp, color = Color(0xFFC62828))
                    }
                }
            }
            Button(
                onClick = {
                    if (config.extraPlaces.size >= 3) {
                        showBigToast(context, "最多 3 个常用地点")
                    } else {
                        placeEditName = ""
                        placeEditCoord = ""
                        placeCoordError = null
                        placeParsed = null
                        showPlaceDialog = true
                    }
                },
                modifier = Modifier.height(64.dp)
            ) {
                Text(text = "添加常用地点", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }

            SectionTitle("打车渠道（尝试顺序固定）")
            SwitchRow("高德地图打车", config.taxiAmap) {
                vm.updateConfig { c -> c.copy(taxiAmap = it) }
            }
            SwitchRow("腾讯地图打车", config.taxiTencent) {
                vm.updateConfig { c -> c.copy(taxiTencent = it) }
            }
            SwitchRow("95128 电话叫车（兜底）", config.taxiHotline) {
                vm.updateConfig { c -> c.copy(taxiHotline = it) }
            }

            SectionTitle("地图服务商（发位置链接，可多选）")
            SwitchRow("高德地图", config.shareAmap) { checked ->
                if (!checked && !config.shareBaidu) {
                    showBigToast(context, "至少要选一个地图")
                } else {
                    vm.updateConfig { c -> c.copy(shareAmap = checked) }
                }
            }
            SwitchRow("百度地图", config.shareBaidu) { checked ->
                if (!checked && !config.shareAmap) {
                    showBigToast(context, "至少要选一个地图")
                } else {
                    vm.updateConfig { c -> c.copy(shareBaidu = checked) }
                }
            }
            Text(
                text = "勾选哪几家，发位置消息里就带哪几家的链接；家人点自己用的地图即可。" +
                    "选多家时每条链接带地图名，选一家时直接一条链接",
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.outline
            )

            SectionTitle("高级项")
            TextButton(onClick = { advancedOpen = !advancedOpen }) {
                Text(if (advancedOpen) "收起" else "展开", fontSize = 20.sp)
            }
            if (advancedOpen) {
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = timeoutText,
                    onValueChange = {
                        timeoutText = it.filter { ch -> ch.isDigit() }.take(3)
                        timeoutText.toIntOrNull()?.let { v ->
                            if (v in 3..120) vm.updateConfig { c -> c.copy(locationTimeoutSeconds = v) }
                        }
                    },
                    label = { Text("定位超时秒数（3~120）", fontSize = 18.sp) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Text(text = "紧急联系人（紧急求助拨打对象）", fontSize = 20.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    config.contacts.forEachIndexed { index, contact ->
                        FilterChip(
                            selected = config.emergencyCustomPhone.isBlank() &&
                                config.emergencyContactIndex == index,
                            onClick = {
                                vm.updateConfig { c ->
                                    c.copy(emergencyContactIndex = index)
                                }
                            },
                            label = { Text(contact.label, fontSize = 18.sp) }
                        )
                    }
                }
                OutlinedTextField(
                    value = emergencyCustom,
                    onValueChange = {
                        emergencyCustom = it
                        vm.updateConfig { c -> c.copy(emergencyCustomPhone = it.trim()) }
                    },
                    label = { Text("或手填号码（如邻居/村医，优先于上面选择）", fontSize = 18.sp) },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "都没配置时，紧急求助兜底拨 120",
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            SectionTitle("信息")
            InfoRow("定位权限", if (PermissionGate.hasLocationPermission(context)) "已授予" else "未授予")
            InfoRow("系统定位开关", if (PermissionGate.anyProviderEnabled(context)) "开" else "关")
            InfoRow(
                "最近一次定位",
                lastFix?.let {
                    "${"%.5f".format(it.lat)}, ${"%.5f".format(it.lng)}（精度 ${it.accuracyMeters} 米，${it.provider}）"
                } ?: "暂无"
            )

            SectionTitle("测试（真机验证用）")
            Button(
                onClick = {
                    scope.launch {
                        val outcome = vm.locate()
                        if (outcome is LocateOutcome.Success) {
                            val name = config.safePlaceName()
                            val links = MapLinkBuilder.selectedLinks(
                                outcome.fix, name, config.shareAmap, config.shareBaidu
                            )
                            val text = if (links.size == 1) {
                                "$name：${links.first().second}（测试）"
                            } else {
                                "$name（点自己用的地图打开）：\n" +
                                    links.joinToString("\n") { "${it.first}：${it.second}" }
                            }
                            try {
                                shareTestLauncher.launch(WeChatSharer.createIntent(context, text))
                            } catch (e: ActivityNotFoundException) {
                                WeChatSharer.copyToClipboard(context, text)
                                showBigToast(context, "已复制内容，去微信粘贴发送")
                            }
                        } else {
                            showBigToast(context, "定位失败，请到窗边空旷处再试")
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(64.dp)
            ) {
                Text(text = "发送测试位置", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    config.home?.let { home ->
                        try {
                            testLauncher.launch(TaxiFlow.amapDriveIntent(home))
                        } catch (e: ActivityNotFoundException) {
                            showBigToast(context, "本机没有安装高德地图")
                        }
                    } ?: showBigToast(context, "请先设置家的位置")
                },
                modifier = Modifier.fillMaxWidth().height(64.dp)
            ) {
                Text(text = "高德打车渠道测试", fontSize = 22.sp)
            }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    config.home?.let { home ->
                        try {
                            testLauncher.launch(TaxiFlow.tencentIntent(home, "drive"))
                        } catch (e: ActivityNotFoundException) {
                            showBigToast(context, "本机没有安装腾讯地图")
                        }
                    } ?: showBigToast(context, "请先设置家的位置")
                },
                modifier = Modifier.fillMaxWidth().height(64.dp)
            ) {
                Text(text = "腾讯地图打车渠道测试", fontSize = 22.sp)
            }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { dial(context, TaxiFlow.TAXI_HOTLINE) },
                modifier = Modifier.fillMaxWidth().height(64.dp)
            ) {
                Text(text = "95128 渠道测试", fontSize = 22.sp)
            }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = onReenterPermissionGuide,
                modifier = Modifier.fillMaxWidth().height(64.dp)
            ) {
                Text(text = "重新进入权限引导", fontSize = 22.sp)
            }
            Spacer(Modifier.height(32.dp))
        }
    }

    // 「设当前位置为家」结果确认
    pendingHomeFix?.let { fix ->
        AlertDialog(
            onDismissRequest = { pendingHomeFix = null },
            title = { Text("确认家的位置", fontSize = 26.sp, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    text = "${"%.5f".format(fix.lat)}, ${"%.5f".format(fix.lng)}\n" +
                        "精度 ${fix.accuracyMeters} 米（${fix.provider}）\n\n" +
                        "确认保存为家的位置吗？",
                    fontSize = 22.sp
                )
            },
            confirmButton = {
                Button(onClick = {
                    vm.updateConfig { c ->
                        c.copy(home = HomePlace(homeName.ifBlank { "家" }, fix.lat, fix.lng))
                    }
                    pendingHomeFix = null
                    showBigToast(context, "已保存家的位置")
                }) { Text("确认保存", fontSize = 20.sp) }
            },
            dismissButton = {
                TextButton(onClick = { pendingHomeFix = null }) {
                    Text("取消", fontSize = 20.sp)
                }
            }
        )
    }

    // 联系人编辑对话框
    if (showContactDialog) {
        AlertDialog(
            onDismissRequest = { showContactDialog = false },
            title = {
                Text(
                    if (contactEditIndex >= 0) "编辑联系人" else "添加联系人",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    OutlinedTextField(
                        value = contactLabel,
                        onValueChange = { contactLabel = it },
                        label = { Text("称呼（如：大儿子）", fontSize = 18.sp) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = contactPhone,
                        onValueChange = { contactPhone = it.filter { ch -> ch.isDigit() } },
                        label = { Text("电话号码", fontSize = 18.sp) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = { saveContact() }) { Text("保存", fontSize = 20.sp) }
            },
            dismissButton = {
                TextButton(onClick = { showContactDialog = false }) {
                    Text("取消", fontSize = 20.sp)
                }
            }
        )
    }

    // 常用地点编辑对话框
    if (showPlaceDialog) {
        AlertDialog(
            onDismissRequest = { showPlaceDialog = false },
            title = { Text("添加常用地点", fontSize = 26.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    OutlinedTextField(
                        value = placeEditName,
                        onValueChange = { placeEditName = it },
                        label = { Text("名称（如：圩镇市场）", fontSize = 18.sp) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = placeEditCoord,
                        onValueChange = {
                            placeEditCoord = it
                            placeCoordError = null
                            placeParsed = PlaceLinkParser.parse(it)
                            val parsedName = placeParsed?.name
                            if (!parsedName.isNullOrBlank() && placeEditName.isBlank()) {
                                placeEditName = parsedName
                            }
                        },
                        label = { Text("粘贴地图分享链接或坐标", fontSize = 18.sp) },
                        isError = placeCoordError != null,
                        supportingText = {
                            when {
                                placeCoordError != null ->
                                    Text(placeCoordError!!, fontSize = 16.sp)
                                placeParsed != null ->
                                    Text("已识别：${placeParsed!!.lat}, ${placeParsed!!.lng}", fontSize = 16.sp)
                                PlaceLinkParser.isUnresolvableMapLink(placeEditCoord) ->
                                    Text("地图分享短链，点「保存」后联网自动解析", fontSize = 16.sp)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = "推荐人到现场后点下面的「用当前位置」；也可粘贴高德/百度地图分享的链接（短链自动联网解析）或直接粘贴坐标",
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.outline
                    )
                    TextButton(
                        onClick = {
                            scope.launch {
                                locating = true
                                val outcome = vm.locate()
                                locating = false
                                if (outcome is LocateOutcome.Success) {
                                    val text = "${outcome.fix.lat},${outcome.fix.lng}"
                                    placeEditCoord = text
                                    placeParsed = PlaceLinkParser.parse(text)
                                } else {
                                    showBigToast(context, "定位失败，请到窗边空旷处再试")
                                }
                            }
                        },
                        enabled = !locating
                    ) {
                        Text(
                            text = if (locating) "定位中…" else "用当前位置",
                            fontSize = 20.sp
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val localParsed = PlaceLinkParser.parse(placeEditCoord)
                        when {
                            localParsed != null -> {
                                val name = placeEditName.trim().ifBlank { localParsed.name.orEmpty() }
                                if (name.isEmpty()) {
                                    placeCoordError = "请填名称"
                                } else {
                                    vm.updateConfig { c ->
                                        c.copy(
                                            extraPlaces = c.extraPlaces +
                                                HomePlace(name, localParsed.lat, localParsed.lng)
                                        )
                                    }
                                    showPlaceDialog = false
                                }
                            }
                            // 地图分享短链：联网跟随跳转解析
                            PlaceLinkParser.isUnresolvableMapLink(placeEditCoord) -> {
                                scope.launch {
                                    resolvingPlaceLink = true
                                    placeCoordError = null
                                    val result = runCatching { resolveShortLink(placeEditCoord) }
                                    resolvingPlaceLink = false
                                    result.fold(
                                        onSuccess = { p ->
                                            val name = placeEditName.trim().ifBlank { p.name.orEmpty() }
                                            if (name.isEmpty()) {
                                                placeCoordError = "请填名称"
                                            } else {
                                                vm.updateConfig { c ->
                                                    c.copy(
                                                        extraPlaces = c.extraPlaces +
                                                            HomePlace(name, p.lat, p.lng)
                                                    )
                                                }
                                                showPlaceDialog = false
                                            }
                                        },
                                        onFailure = { e ->
                                            placeCoordError = e.message ?: "短链解析失败，请检查网络后重试"
                                        }
                                    )
                                }
                            }
                            else -> placeCoordError =
                                "没识别到坐标，请粘贴地图分享的链接或如 23.12,113.36 的坐标"
                        }
                    },
                    enabled = !resolvingPlaceLink
                ) {
                    Text(
                        text = if (resolvingPlaceLink) "解析中…" else "保存",
                        fontSize = 20.sp
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showPlaceDialog = false }) {
                    Text("取消", fontSize = 20.sp)
                }
            }
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Spacer(Modifier.height(20.dp))
    HorizontalDivider()
    Text(
        text = text,
        fontSize = 24.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
    )
}

@Composable
private fun SwitchRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = title, fontSize = 22.sp, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.padding(vertical = 2.dp)) {
        Text(text = "$label：", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(text = value, fontSize = 20.sp, modifier = Modifier.weight(1f))
    }
    Box(Modifier.height(0.dp))
}
