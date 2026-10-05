package com.goodyaoshi.parentonetap.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.goodyaoshi.parentonetap.location.Fix
import com.goodyaoshi.parentonetap.location.LocateOutcome

private val LocateGreen = Color(0xFF1B8A3A)
private val LocateBlue = Color(0xFF1565C0)
private val LocateOrange = Color(0xFFEF6C00)

fun minutesAgo(fix: Fix): Long =
    ((System.currentTimeMillis() - fix.timestampMs) / 60000L).coerceAtLeast(0)

/** 定位全屏态（方案 3.2）：正在定位 / 超时用上次位置 / 失败重试 */
@Composable
fun LocateOverlay(
    state: HomeViewModel.LocateUi,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onUseLast: () -> Unit,
    onOpenLocationSettings: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            when (state) {
                is HomeViewModel.LocateUi.Locating -> Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(64.dp), strokeWidth = 6.dp)
                    Spacer(Modifier.height(20.dp))
                    Text(text = "正在定位…", fontSize = 34.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(28.dp))
                    TextButton(onClick = onCancel) {
                        Text(text = "取消", fontSize = 24.sp)
                    }
                }

                is HomeViewModel.LocateUi.Timeout -> Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(text = "定位慢", fontSize = 34.sp, fontWeight = FontWeight.Bold)
                    val last = state.last
                    if (last != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "可以先用上次的位置\n（约 ${minutesAgo(last)} 分钟前）",
                            fontSize = 24.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(24.dp))
                        OverlayButton(text = "用上次位置", color = LocateGreen, onClick = onUseLast)
                    } else {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "请到窗边或空旷处\n再试一次",
                            fontSize = 24.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(24.dp))
                        OverlayButton(text = "重试定位", color = LocateBlue, onClick = onRetry)
                    }
                    Spacer(Modifier.height(12.dp))
                    TextButton(onClick = onCancel) {
                        Text(text = "取消", fontSize = 24.sp)
                    }
                }

                is HomeViewModel.LocateUi.Error -> {
                    val providersOff = state.reason == LocateOutcome.Reason.PROVIDERS_OFF
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = if (providersOff) "手机定位没打开" else "定位失败",
                            fontSize = 34.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = errorText(state.reason),
                            fontSize = 24.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(24.dp))
                        if (providersOff) {
                            // 老人不会自己开定位：一键跳到系统定位设置页
                            OverlayButton(
                                text = "去打开定位开关",
                                color = LocateOrange,
                                onClick = onOpenLocationSettings
                            )
                            Spacer(Modifier.height(12.dp))
                            TextButton(onClick = onRetry) {
                                Text(text = "我已打开，重试", fontSize = 24.sp)
                            }
                        } else {
                            OverlayButton(text = "重试定位", color = LocateBlue, onClick = onRetry)
                        }
                        Spacer(Modifier.height(12.dp))
                        TextButton(onClick = onCancel) {
                            Text(text = "取消", fontSize = 24.sp)
                        }
                    }
                }

                else -> {}
            }
        }
    }
}

private fun errorText(reason: LocateOutcome.Reason): String = when (reason) {
    LocateOutcome.Reason.NO_PERMISSION -> "定位权限未打开，请联系家人协助设置"
    LocateOutcome.Reason.PROVIDERS_OFF -> "点下面的大按钮打开定位\n打开后就能发位置了"
    else -> "定位出现问题，请再试一次"
}

@Composable
private fun OverlayButton(text: String, color: Color, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(96.dp),
        shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = Color.White)
    ) {
        Text(text = text, fontSize = 28.sp, fontWeight = FontWeight.Bold)
    }
}

/**
 * 「打车回家」前系统定位开关未打开时的全屏提示（方案 3.3）：
 * 地图要知道爸妈当前在哪才能算路线，所以打车/导航同样需要定位；
 * 老人不会自己开，给一键跳系统定位设置的大按钮。
 */
@Composable
fun TaxiLocationOffOverlay(
    onOpenLocationSettings: () -> Unit,
    onRetry: () -> Unit,
    onClose: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "手机定位没打开",
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "打车要知道您在哪里\n点下面的大按钮打开定位",
                    fontSize = 24.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(24.dp))
                OverlayButton(
                    text = "去打开定位开关",
                    color = LocateOrange,
                    onClick = onOpenLocationSettings
                )
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = onRetry) {
                    Text(text = "我已打开，重试", fontSize = 24.sp)
                }
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = onClose) {
                    Text(text = "取消", fontSize = 24.sp)
                }
            }
        }
    }
}

/** 家坐标未配置时的全屏提示（方案 3.3 / Prompt 4）：特大按钮直接打电话给家人 */
@Composable
fun TaxiHomeMissingOverlay(
    onCallFamily: () -> Unit,
    onClose: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "请家人在设置页\n填好家的位置",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(24.dp))
                OverlayButton(text = "打电话给家人", color = LocateGreen, onClick = onCallFamily)
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = onClose) {
                    Text(text = "知道了", fontSize = 24.sp)
                }
            }
        }
    }
}
