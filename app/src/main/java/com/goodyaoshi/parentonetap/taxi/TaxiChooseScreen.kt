package com.goodyaoshi.parentonetap.taxi

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import com.goodyaoshi.parentonetap.config.ConfigViewModel
import com.goodyaoshi.parentonetap.config.HomePlace
import com.goodyaoshi.parentonetap.core.ui.TaxiHomeMissingOverlay
import com.goodyaoshi.parentonetap.share.showBigToast
import kotlinx.coroutines.launch

private val ModeBlue = Color(0xFF1565C0)
private val ModeOrange = Color(0xFFEF6C00)
private val ModeTeal = Color(0xFF00838F)

/**
 * 回家选择页（方案 3.3）：目的地（家/常用地点）+ 三种方式，优先级 导航 → 顺风车 → 打车
 * 导航（高德步行路线页，页内可切公交/驾车） / 顺风车、打车（高德驾车路线页，页内点打车，
 * 车型里可选顺风车；无高德/腾讯则兜底拨 95128）。
 */
@Composable
fun TaxiChooseScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val vm: ConfigViewModel = hiltViewModel()
    val config by vm.config.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    var selectedPlace by remember { mutableStateOf<HomePlace?>(null) }
    var shownTip by remember { mutableStateOf<TaxiMode?>(null) }

    val taxiLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        // 从地图 App 返回：提醒爸妈路线还在地图里
        showBigToast(context, "请看手机上的地图 App")
    }

    // 目的地：默认家，可选常用地点
    val places: List<HomePlace> = listOfNotNull(config.home) + config.extraPlaces
    val current = selectedPlace?.let { sel -> places.firstOrNull { it === sel } }
        ?: places.firstOrNull()

    fun callHotline() {
        if (config.taxiHotline) {
            dial(context, TaxiFlow.TAXI_HOTLINE)
        } else {
            showBigToast(context, "打车渠道都不可用，请让家人在设置页检查")
        }
    }

    fun launchChain(intents: List<Intent>, fallbackHotline: Boolean) {
        try {
            if (intents.isNotEmpty()) {
                taxiLauncher.launch(intents.first())
            } else if (fallbackHotline) {
                callHotline()
            } else {
                showBigToast(context, "手机上没有地图 App，请让家人安装")
            }
        } catch (e: Exception) {
            // 探测通过但拉起失败 → 降级次选 → 兜底
            try {
                if (intents.size > 1) {
                    taxiLauncher.launch(intents[1])
                } else if (fallbackHotline) {
                    callHotline()
                } else {
                    showBigToast(context, "打不开地图，请让家人检查")
                }
            } catch (e2: Exception) {
                if (fallbackHotline) callHotline()
                else showBigToast(context, "打不开地图，请让家人检查")
            }
        }
    }

    fun startMode(mode: TaxiMode) {
        val place = current ?: run {
            showBigToast(context, "请家人先在设置页填好家的位置")
            return
        }
        if (mode != TaxiMode.NAVI && shownTip != mode) {
            shownTip = mode
            scope.launch {
                snackbarHostState.showSnackbar(
                    when (mode) {
                        TaxiMode.TAXI -> "进入地图后，点屏幕最上面的「打车」"
                        TaxiMode.RIDESHARE -> "进入地图后，点屏幕最上面的「顺风车」"
                        TaxiMode.NAVI -> ""
                    }
                )
            }
        }
        launchChain(
            TaxiFlow.candidates(
                context, place, mode, config.taxiAmap, config.taxiTencent
            ),
            fallbackHotline = mode != TaxiMode.NAVI
        )
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text(text = "返回", fontSize = 22.sp) }
                Text(
                    text = "回家",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.width(72.dp))
            }

            if (current != null) {
                Text(text = "去哪里？", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    places.forEach { place ->
                        FilterChip(
                            selected = place === current,
                            onClick = { selectedPlace = place },
                            label = { Text(place.name, fontSize = 20.sp) }
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            ModeButton(
                text = "导航回家",
                subtitle = "走路带路，一步一步走",
                color = ModeTeal,
                enabled = current != null
            ) { startMode(TaxiMode.NAVI) }
            Spacer(Modifier.height(16.dp))
            ModeButton(
                text = "顺风车回家",
                subtitle = "便宜一点，等的时间长",
                color = ModeOrange,
                enabled = current != null
            ) { startMode(TaxiMode.RIDESHARE) }
            Spacer(Modifier.height(16.dp))
            ModeButton(
                text = "打车回家",
                subtitle = "叫车到家门口",
                color = ModeBlue,
                enabled = current != null
            ) { startMode(TaxiMode.TAXI) }
        }
    }

    if (config.home == null) {
        TaxiHomeMissingOverlay(
            onCallFamily = { dial(context, config.emergencyTarget()?.phone ?: "120") },
            onClose = onBack
        )
    }
}

@Composable
private fun ModeButton(
    text: String,
    subtitle: String,
    color: Color,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .height(104.dp),
        shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = color,
            contentColor = Color.White,
            disabledContainerColor = color.copy(alpha = 0.4f),
            disabledContentColor = Color.White
        )
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = text, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Text(text = subtitle, fontSize = 18.sp)
        }
    }
}
