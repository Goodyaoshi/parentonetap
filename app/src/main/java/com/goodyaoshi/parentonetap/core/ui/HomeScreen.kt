package com.goodyaoshi.parentonetap.core.ui

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Emergency
import androidx.compose.material.icons.filled.LocalTaxi
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.goodyaoshi.parentonetap.call.dial
import com.goodyaoshi.parentonetap.core.perm.PermissionGate
import com.goodyaoshi.parentonetap.core.perm.PermissionGuideScreen
import com.goodyaoshi.parentonetap.location.LocateOutcome
import com.goodyaoshi.parentonetap.share.MapLinkBuilder
import com.goodyaoshi.parentonetap.share.WeChatSharer
import com.goodyaoshi.parentonetap.share.showBigToast
import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.Settings
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// 首页四巨钮配色（方案 3.1）：绿-发位置 / 蓝-打车 / 橙-打电话 / 红-紧急求助
private val ColorSendLocation = Color(0xFF1B8A3A)
private val ColorTaxi = Color(0xFF1565C0)
private val ColorCall = Color(0xFFEF6C00)
private val ColorEmergency = Color(0xFFC62828)

@Composable
fun HomeScreen(
    onOpenContacts: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenTaxi: () -> Unit
) {
    val context = LocalContext.current
    val viewModel: HomeViewModel = hiltViewModel()
    val locateState by viewModel.locateState.collectAsStateWithLifecycle()
    val config by viewModel.config.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var permissionGranted by remember {
        mutableStateOf(PermissionGate.hasLocationPermission(context))
    }
    // 首次启动「家人协助设置」引导（方案 3.7）：完成前不进首页
    val onboardingDone by viewModel.onboardingDone.collectAsStateWithLifecycle()
    var onboardingLocallyDone by remember { mutableStateOf(false) }
    // 打车回家同样需要定位（地图要先知道爸妈在哪）：系统定位开关未开时先给引导
    var taxiLocationOff by remember { mutableStateOf(false) }
    var firstShareTipShown by remember { mutableStateOf(false) }
    val shareLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        // 从微信/选择器返回即视为已发出（能否送达由微信决定）
        showBigToast(context, "已发出")
    }

    // 配置尚未加载完：先不渲染，避免闪一下引导
    if (onboardingDone == null) return

    // 首次启动：走三步引导（授权 → 试发位置 → 完成）
    if (onboardingDone == false && !onboardingLocallyDone) {
        PermissionGuideScreen(onboarding = true, onGranted = {
            onboardingLocallyDone = true
            permissionGranted = true
        })
        return
    }

    // 权限被撤销（非首次）：只走授权一步
    if (!permissionGranted) {
        PermissionGuideScreen(onGranted = { permissionGranted = true })
        return
    }

    // 从系统定位设置返回：若定位已打开且还停在「定位开关未开」的失败态，自动重试
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val s = viewModel.locateState.value
                if (s is HomeViewModel.LocateUi.Error &&
                    s.reason == LocateOutcome.Reason.PROVIDERS_OFF &&
                    PermissionGate.anyProviderEnabled(context)
                ) {
                    viewModel.startLocate()
                }
                // 打车回家：从系统定位设置返回且已打开，直接进回家页
                if (taxiLocationOff && PermissionGate.anyProviderEnabled(context)) {
                    taxiLocationOff = false
                    onOpenTaxi()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            HomeTitle(onOpenSettings = onOpenSettings)

            BigButton(
                container = ColorSendLocation,
                icon = Icons.Filled.LocationOn,
                label = "发位置",
                subtitle = "发到微信给家人",
                modifier = Modifier.weight(1f),
                onClick = viewModel::startLocate
            )
            BigButton(
                container = ColorTaxi,
                icon = Icons.Filled.LocalTaxi,
                label = "打车回家",
                subtitle = "打车 · 顺风车 · 导航",
                modifier = Modifier.weight(1f),
                onClick = {
                    // 与发位置一致：定位没开先引导老人打开，再进回家页
                    if (PermissionGate.anyProviderEnabled(context)) {
                        onOpenTaxi()
                    } else {
                        taxiLocationOff = true
                    }
                }
            )
            BigButton(
                container = ColorCall,
                icon = Icons.Filled.Call,
                label = "打电话",
                subtitle = "点一下就拨号",
                modifier = Modifier.weight(1f),
                onClick = onOpenContacts
            )
            // 直接跳系统拨号盘（拨号盘本身即二次确认），对象为紧急联系人
            BigButton(
                container = ColorEmergency,
                icon = Icons.Filled.Emergency,
                label = "紧急求助",
                subtitle = "打电话给家人",
                modifier = Modifier.weight(1f),
                onClick = { dial(context, config.emergencyTarget()?.phone ?: "120") }
            )
        }
    }

    // 定位全屏态
    when (locateState) {
        is HomeViewModel.LocateUi.Locating,
        is HomeViewModel.LocateUi.Timeout,
        is HomeViewModel.LocateUi.Error ->
            LocateOverlay(
                state = locateState,
                onCancel = viewModel::cancelLocate,
                onRetry = viewModel::startLocate,
                onUseLast = viewModel::useLast,
                onOpenLocationSettings = {
                    context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                }
            )
        else -> {}
    }

    // 打车回家：系统定位开关未开时的引导（打开后自动进回家页）
    if (taxiLocationOff) {
        TaxiLocationOffOverlay(
            onOpenLocationSettings = {
                context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            },
            onRetry = {
                if (PermissionGate.anyProviderEnabled(context)) {
                    taxiLocationOff = false
                    onOpenTaxi()
                }
            },
            onClose = { taxiLocationOff = false }
        )
    }

    // 定位成功 → 组装文案 → 弹微信分享（方案 3.2 完整链路）
    LaunchedEffect(locateState) {
        val s = locateState
        if (s is HomeViewModel.LocateUi.Done) {
            viewModel.consumeResult()
            val name = config.safePlaceName()
            val nameLine = if (s.usedLast) "$name（约 ${minutesAgo(s.fix)} 分钟前）" else name
            // 按设置页勾选的地图服务商生成链接：选 1 家就发 1 条，选多家发多条供家人自选
            val links = MapLinkBuilder.selectedLinks(
                s.fix, name, config.shareAmap, config.shareBaidu
            )
            val text = if (links.size == 1) {
                "$nameLine：${links.first().second}"
            } else {
                "$nameLine（点自己用的地图打开）：\n" +
                    links.joinToString("\n") { "${it.first}：${it.second}" }
            }
            // 首次分享给一次大字提醒（方案 3.2 后半）
            if (!firstShareTipShown) {
                firstShareTipShown = true
                scope.launch { snackbarHostState.showSnackbar("请在微信里选择发送对象") }
            }
            try {
                shareLauncher.launch(WeChatSharer.createIntent(context, text))
            } catch (e: ActivityNotFoundException) {
                WeChatSharer.copyToClipboard(context, text)
                showBigToast(context, "发送不了，已复制内容，去微信粘贴给家人")
            }
        }
    }
}

/**
 * 首页标题：连点 5 次进入隐藏设置页（子女配置入口，方案 3.1）。
 * 1.5 秒无点击自动清零计数。
 */
@Composable
private fun HomeTitle(onOpenSettings: () -> Unit) {
    var taps by remember { mutableIntStateOf(0) }
    LaunchedEffect(taps) {
        if (taps > 0) {
            delay(1500)
            taps = 0
        }
    }
    Text(
        text = "爸妈一键通",
        fontSize = 30.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectTapGestures(onTap = {
                    taps++
                    if (taps >= 5) {
                        taps = 0
                        onOpenSettings()
                    }
                })
            }
            .padding(vertical = 10.dp)
    )
}

/** 色块 + 图标 + 大字 + 副标题的巨钮；主按钮字号 32sp，副标题 20sp */
@Composable
private fun BigButton(
    container: Color,
    icon: ImageVector,
    label: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = container,
            contentColor = Color.White
        )
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(40.dp))
            Spacer(Modifier.height(6.dp))
            Text(text = label, fontSize = 32.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(2.dp))
            Text(text = subtitle, fontSize = 20.sp)
        }
    }
}
