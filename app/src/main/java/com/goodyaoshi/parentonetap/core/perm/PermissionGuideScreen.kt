package com.goodyaoshi.parentonetap.core.perm

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.core.app.ActivityCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.goodyaoshi.parentonetap.config.ConfigViewModel
import com.goodyaoshi.parentonetap.location.LocateOutcome
import com.goodyaoshi.parentonetap.share.MapLinkBuilder
import com.goodyaoshi.parentonetap.share.WeChatSharer
import com.goodyaoshi.parentonetap.share.showBigToast
import kotlinx.coroutines.launch

private val GuideGreen = Color(0xFF1B8A3A)
private val GuideBlue = Color(0xFF1565C0)
private val GuideOrange = Color(0xFFEF6C00)

/** 引导步骤（方案 3.7）：授权定位 → 试发位置 → 完成 */
private enum class GuideStep { PERMISSION, TEST, DONE }

private fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

/**
 * 家人协助设置引导（方案 3.7）。
 *
 * onboarding = true（首次启动）：特大字分三步——授定位权限 → 测发位置 → 完成，
 * 完成后写入 onboardingDone 并提示子女「连点标题 5 次进隐藏设置页」。
 * onboarding = false（设置页「重新进入权限引导」）：只走授权一步，已授权直接返回。
 */
@Composable
fun PermissionGuideScreen(
    onGranted: () -> Unit,
    onboarding: Boolean = false
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val activity = remember { context.findActivity() }
    val vm: ConfigViewModel = hiltViewModel()
    val config by vm.config.collectAsStateWithLifecycle()

    val initiallyGranted = remember { PermissionGate.hasLocationPermission(context) }
    var step by remember {
        mutableStateOf(
            when {
                !initiallyGranted -> GuideStep.PERMISSION
                onboarding -> GuideStep.TEST
                else -> GuideStep.DONE
            }
        )
    }
    var permanentlyDenied by remember { mutableStateOf(false) }
    var systemLocationOff by remember { mutableStateOf(!PermissionGate.anyProviderEnabled(context)) }
    var testing by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result.values.all { it }) {
            systemLocationOff = !PermissionGate.anyProviderEnabled(context)
            step = if (onboarding) GuideStep.TEST else GuideStep.DONE
        } else {
            val deniedForever = activity?.let {
                !ActivityCompat.shouldShowRequestPermissionRationale(
                    it, PermissionGate.permissions.first()
                )
            } ?: false
            if (deniedForever) permanentlyDenied = true
        }
    }

    // 测发位置：定位 → 按勾选的地图服务商生成链接 → 拉起微信（方案 3.2 链路）
    val shareLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { showBigToast(context, "已发出") }

    fun onPermissionReady() {
        systemLocationOff = !PermissionGate.anyProviderEnabled(context)
    }

    fun runTestShare() {
        scope.launch {
            testing = true
            val outcome = vm.locate()
            testing = false
            if (outcome !is LocateOutcome.Success) {
                showBigToast(context, "定位失败，请到窗边空旷处再试")
                return@launch
            }
            val cfg = config
            val name = cfg.safePlaceName()
            val links = MapLinkBuilder.selectedLinks(
                outcome.fix, name, cfg.shareAmap, cfg.shareBaidu
            )
            val text = if (links.size == 1) {
                "$name：${links.first().second}"
            } else {
                "$name（点自己用的地图打开）：\n" +
                    links.joinToString("\n") { "${it.first}：${it.second}" }
            }
            try {
                shareLauncher.launch(WeChatSharer.createIntent(context, text))
            } catch (e: ActivityNotFoundException) {
                WeChatSharer.copyToClipboard(context, text)
                showBigToast(context, "发送不了，已复制内容，去微信粘贴给家人")
            }
            step = GuideStep.DONE
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (step == GuideStep.PERMISSION && PermissionGate.hasLocationPermission(context)) {
                    onPermissionReady()
                    step = if (onboarding) GuideStep.TEST else GuideStep.DONE
                }
                systemLocationOff = !PermissionGate.anyProviderEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // 非首次启动模式：授权已就绪 → 直接返回
    LaunchedEffect(step, onboarding) {
        if (!onboarding && step == GuideStep.DONE) onGranted()
    }

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            when (step) {
                GuideStep.PERMISSION -> PermissionStep(
                    permanentlyDenied = permanentlyDenied,
                    systemLocationOff = systemLocationOff,
                    onRequest = { permissionLauncher.launch(PermissionGate.permissions) },
                    onOpenAppSettings = {
                        context.startActivity(
                            Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.fromParts("package", context.packageName, null)
                            )
                        )
                    },
                    onOpenLocationSettings = {
                        context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                    }
                )

                GuideStep.TEST -> TestStep(
                    testing = testing,
                    onTest = { runTestShare() },
                    onSkip = { step = GuideStep.DONE }
                )

                GuideStep.DONE -> DoneStep(
                    onboarding = onboarding,
                    onFinish = {
                        if (onboarding) vm.updateConfig { c -> c.copy(onboardingDone = true) }
                        onGranted()
                    }
                )
            }
        }
    }
}

/** 第 1 步：定位权限 + 系统定位总开关 */
@Composable
private fun PermissionStep(
    permanentlyDenied: Boolean,
    systemLocationOff: Boolean,
    onRequest: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onOpenLocationSettings: () -> Unit
) {
    StepHint("第 1 步 / 共 3 步")
    Text(text = "家人协助设置", fontSize = 34.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(12.dp))
    Text(
        text = "为了能发位置给家人，\n需要先打开定位权限。请点下面的按钮，选「允许」。",
        fontSize = 24.sp,
        textAlign = TextAlign.Center
    )
    Spacer(Modifier.height(32.dp))
    if (!permanentlyDenied) {
        GuideButton(text = "授权定位权限", color = GuideGreen, onClick = onRequest)
    } else {
        GuideButton(text = "去应用设置打开定位", color = GuideBlue, onClick = onOpenAppSettings)
    }
    if (systemLocationOff) {
        Spacer(Modifier.height(12.dp))
        GuideButton(text = "打开系统定位开关", color = GuideOrange, onClick = onOpenLocationSettings)
    }
}

/** 第 2 步：试发一次位置，确认整条链路好用 */
@Composable
private fun TestStep(testing: Boolean, onTest: () -> Unit, onSkip: () -> Unit) {
    StepHint("第 2 步 / 共 3 步")
    Text(text = "试一下发位置", fontSize = 34.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(12.dp))
    Text(
        text = "点下面的按钮，会定位当前位置并打开微信，\n随便发给一个家人试试看。",
        fontSize = 24.sp,
        textAlign = TextAlign.Center
    )
    Spacer(Modifier.height(32.dp))
    GuideButton(
        text = if (testing) "定位中…" else "试试发位置",
        color = GuideGreen,
        enabled = !testing,
        onClick = onTest
    )
    Spacer(Modifier.height(8.dp))
    TextButton(onClick = onSkip) { Text(text = "先跳过", fontSize = 24.sp) }
}

/** 第 3 步：完成 + 交给子女继续配置 */
@Composable
private fun DoneStep(onboarding: Boolean, onFinish: () -> Unit) {
    StepHint("第 3 步 / 共 3 步")
    Text(text = "设置完成！", fontSize = 36.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(16.dp))
    if (onboarding) {
        Text(
            text = "接下来请子女帮忙：\n\n" +
                "1. 连点最上面的标题「爸妈一键通」5 次，\n" +
                "   进入隐藏设置页；\n" +
                "2. 填好「家的位置」和联系人电话；\n" +
                "3. 把桌面的「发位置」小组件摆出来。\n\n" +
                "做完这些，爸妈点一下就能用了。",
            fontSize = 24.sp,
            textAlign = TextAlign.Start
        )
    } else {
        Text(text = "定位权限已就绪。", fontSize = 24.sp, textAlign = TextAlign.Center)
    }
    Spacer(Modifier.height(32.dp))
    GuideButton(
        text = if (onboarding) "我知道了，开始使用" else "返回",
        color = GuideBlue,
        onClick = onFinish
    )
}

@Composable
private fun StepHint(text: String) {
    Text(text = text, fontSize = 20.sp, color = Color(0xFF666666))
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun GuideButton(
    text: String,
    color: Color,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .height(96.dp),
        shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = color,
            contentColor = Color.White,
            disabledContainerColor = color.copy(alpha = 0.5f),
            disabledContentColor = Color.White
        )
    ) {
        Text(text = text, fontSize = 28.sp, fontWeight = FontWeight.Bold)
    }
}
