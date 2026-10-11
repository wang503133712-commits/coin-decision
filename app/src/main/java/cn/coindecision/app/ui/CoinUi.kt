package cn.coindecision.app.ui

import android.animation.ValueAnimator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cn.coindecision.app.BuildConfig
import cn.coindecision.app.R
import cn.coindecision.app.data.*
import cn.coindecision.app.domain.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.sin
import kotlin.math.cos
import kotlin.math.PI
import kotlin.math.roundToInt

private val Ink = Color(0xFF272C31)
private val Champagne = Color(0xFFE9C98B)
@Composable fun CoinApp(vm: CoinViewModel) {
    val state by vm.ui.collectAsStateWithLifecycle()
    val dark = isSystemInDarkTheme()
    val scheme = if (dark) darkColorScheme(primary = Champagne, onPrimary = Ink, primaryContainer = Color(0xFF443B29), onPrimaryContainer = Color(0xFFF7EBCF),
        background = Color(0xFF1A1917), surface = Color(0xFF292722), onBackground = Color(0xFFF7F3EC),
        onSurface = Color(0xFFF7F3EC), onSurfaceVariant = Color(0xFFC5BDAE), outline = Color(0xFF7A7265), outlineVariant = Color(0xFF443F36))
    else lightColorScheme(primary = Color(0xFF806021), onPrimary = Color.White, primaryContainer = Color(0xFFF7EBCF), onPrimaryContainer = Color(0xFF493A20),
        background = Color(0xFFFFFDFA), surface = Color.White, onBackground = Ink, onSurface = Ink,
        onSurfaceVariant = Color(0xFF737067), outline = Color(0xFFBBB5AB), outlineVariant = Color(0xFFE8E1D5))
    val audio = remember { CoinAudio() }
    DisposableEffect(Unit) { onDispose { audio.release() } }
    MaterialTheme(colorScheme = scheme) {
        val canBack = state.page != Page.MAIN || state.session.stage != Stage.EDIT
        BackHandler(enabled = canBack) { vm.back() }
        Surface(Modifier.fillMaxSize(), color = scheme.background) {
            Column(Modifier.safeDrawingPadding().imePadding().fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (canBack) TextButton(onClick = vm::back, enabled = !state.tossing) { Text("返回") }
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.app_name), fontWeight = FontWeight.SemiBold, fontSize = 19.sp, letterSpacing = 1.sp)
                        Text("v${BuildConfig.VERSION_NAME}  /  DAILY DECISIONS", fontSize = 9.sp, letterSpacing = 0.5.sp, color = scheme.onSurfaceVariant)
                    }
                    TextButton(onClick = { vm.page(Page.HISTORY) }, enabled = !state.tossing && state.session.stage != Stage.ANIMATING) { Text("历史") }
                    TextButton(onClick = { vm.page(Page.SETTINGS) }, enabled = !state.tossing && state.session.stage != Stage.ANIMATING) { Text("设置") }
                }
                if (state.page == Page.HISTORY && !state.loading) History(state, vm, Modifier.weight(1f))
                else Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Column(Modifier.widthIn(max = 560.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        if (state.notice.isNotBlank()) Notice(state.notice)
                        if (state.loading) { CircularProgressIndicator(); Text("正在恢复本地草稿…") }
                        else when (state.page) {
                            Page.MAIN -> MainFlow(state, vm, audio)
                            Page.HISTORY -> Unit
                            Page.DETAIL -> state.detail?.let { ResultContent(it, null, vm) }
                            Page.SETTINGS -> SettingsPage(state, vm)
                        }
                        Spacer(Modifier.height(12.dp))
                        Text(stringResource(R.string.boundary), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
@Composable private fun Heading(title: String, subtitle: String = "") {
    Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp)
    if (subtitle.isNotEmpty()) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
@Composable private fun Notice(text: String) {
    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.primaryContainer) { Text(text, Modifier.padding(16.dp).semantics { liveRegion = LiveRegionMode.Polite }, style = MaterialTheme.typography.bodyMedium) }
}
@Composable private fun Panel(content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(24.dp), shadowElevation = 2.dp, color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}
@Composable private fun Primary(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) .98f else 1f, spring(stiffness = 700f), label = "buttonPress")
    Button(onClick = onClick, enabled = enabled, interactionSource = interaction,
        colors = ButtonDefaults.buttonColors(containerColor = Champagne, contentColor = Ink,
            disabledContainerColor = Champagne.copy(alpha = .35f), disabledContentColor = Ink.copy(alpha = .45f)),
        modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp).graphicsLayer { scaleX = scale; scaleY = scale }, shape = RoundedCornerShape(18.dp)) {
        Text(label, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(vertical = 4.dp))
    }
}
@Composable private fun SourceLabel(source: Source) {
    Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(50)) {
        Text(if (source == Source.OFFLINE) "离线通用题目" else "DeepSeek 生成题目", Modifier.padding(horizontal = 12.dp, vertical = 6.dp), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
    }
}
@Composable private fun Faces(session: Session) {
    Panel {
        FaceRow("A", "正面 A", session.optionA)
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f))
        FaceRow("B", "反面 B", session.optionB)
    }
}
@Composable private fun FaceRow(letter: String, label: String, text: String) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.size(38.dp), contentAlignment = Alignment.Center) {
            Text(letter, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(text, fontWeight = FontWeight.Medium)
        }
    }
}
@Composable private fun Hero() {
    val coinSize = if (LocalDensity.current.fontScale > 1.2f) 96.dp else 116.dp
    val scheme = MaterialTheme.colorScheme
    val colors = if (isSystemInDarkTheme()) listOf(Color(0xFF2D2B25), Color(0xFF393329)) else listOf(Color(0xFFFFFCF4), Color(0xFFF8F1E4))
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Brush.linearGradient(colors))) {
        Canvas(Modifier.matchParentSize()) {
            drawCircle(Champagne.copy(alpha = .08f), radius = size.width * .45f, center = Offset(size.width * .92f, size.height * .05f))
            drawCircle(Champagne.copy(alpha = .06f), radius = size.width * .28f, center = Offset(size.width, size.height * .75f))
        }
        Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("一枚硬币 · 一点偏心", color = scheme.primary, fontSize = 12.sp, letterSpacing = 1.sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("让选择，\n轻一点。", color = scheme.onSurface, fontSize = 28.sp, lineHeight = 39.sp, fontWeight = FontWeight.Bold)
                    Text("先听听自己，\n再交给一点运气。", color = scheme.onSurfaceVariant, fontSize = 13.sp, lineHeight = 21.sp)
                }
                Box(Modifier.size(coinSize).graphicsLayer { rotationZ = -13f; rotationY = -20f; cameraDistance = 16 * density }) {
                    CoinMedallion(Face.A, Modifier.fillMaxSize())
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("70%", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = scheme.onSurface)
                Text(" 运气", fontSize = 12.sp, color = scheme.onSurfaceVariant)
                Spacer(Modifier.width(18.dp))
                Text("30%", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = scheme.primary)
                Text(" 自己", fontSize = 12.sp, color = scheme.onSurfaceVariant)
            }
        }
    }
}
@Composable private fun GenerateButton(label: String, state: UiState, vm: CoinViewModel, enabled: Boolean = true) {
    var consent by remember { mutableStateOf(false) }
    Primary(label, enabled) { if (state.settings.online) consent = true else vm.generate() }
    if (consent) AlertDialog(onDismissRequest = { consent = false }, title = { Text("确认联网出题") },
        text = { Text("本次事项和确认的 A/B 将发送至 ${state.settings.config.baseUrl}，会产生 API 用量。回答和历史留在本机。仅在你同意后发送。") },
        confirmButton = { TextButton(onClick = { consent = false; vm.generate() }) { Text("同意并出题") } },
        dismissButton = { TextButton(onClick = { consent = false }) { Text("取消") } })
}
@Composable private fun MainFlow(state: UiState, vm: CoinViewModel, audio: CoinAudio) {
    val s = state.session
    when (s.stage) {
        Stage.EDIT -> {
            Hero()
            Panel {
                Text("01  /  写下这次犹豫", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(s.matter, { vm.edit(it, s.optionA, s.optionB) }, label = { Text("你在犹豫什么？") },
                    shape = RoundedCornerShape(16.dp), supportingText = { Text("日常的是否选择 · ${s.matter.length}/500") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                TextButton(onClick = { vm.edit("这周末要不要去露营？", "这周末去露营", "这周末不去露营，保留原有安排") }, contentPadding = PaddingValues(horizontal = 0.dp)) { Text("试试示例：周末露营") }
            }
            Panel {
                Text("02  /  定义硬币的两面", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(s.optionA, { vm.edit(s.matter, it, s.optionB) }, label = { Text("正面 A：执行什么行动") }, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(s.optionB, { vm.edit(s.matter, s.optionA, it) }, label = { Text("反面 B：不做、暂缓或什么现状") }, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth())
                Text("把两面写成具体、互斥的行为。否定句也请自己核对。每面最多 120 字。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Primary("确认两面含义", s.matter.isNotBlank() && s.optionA.isNotBlank() && s.optionB.isNotBlank(), vm::confirm)
        }
        Stage.CONFIRM -> {
            Heading("先确认，你的两种选择", s.matter)
            Faces(s)
            var checked by rememberSaveable(s.decisionId, s.matter, s.optionA, s.optionB) { mutableStateOf(false) }
            Row(Modifier.fillMaxWidth().toggleableCompat(checked) { checked = it }, verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked, null); Text("我确认 A/B 是不同、互斥的安排，文字准确表达我的意思。", Modifier.weight(1f))
            }
            GenerateButton("开始了解自己", state, vm, checked)
            TextButton(onClick = vm::back) { Text("返回编辑") }
        }
        Stage.GENERATING -> {
            Heading("正在准备五道题", "题目只帮助梳理选择，最终投币由本机完成。")
            SourceLabel(s.source); CircularProgressIndicator(); Primary("取消出题", onClick = vm::cancelGeneration)
        }
        Stage.ANSWER -> {
            SourceLabel(s.source)
            Text("${s.index + 1} / 5", color = MaterialTheme.colorScheme.primary)
            LinearProgressIndicator(progress = { (s.index + 1) / 5f }, modifier = Modifier.fillMaxWidth(),
                trackColor = MaterialTheme.colorScheme.outlineVariant)
            val q = s.questions[s.index]
            Heading(q.text)
            Text("A：${s.optionA}\nB：${s.optionB}", style = MaterialTheme.typography.bodySmall)
            val answer = s.answers[s.index]
            q.options.forEach { option ->
                val selected = answer.kind == AnswerKind.SELECTED && answer.optionId == option.id
                Surface(shape = RoundedCornerShape(20.dp), border = BorderStroke(if (selected) 1.5.dp else 0.5.dp,
                    if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f)),
                    color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface) {
                    Row(Modifier.fillMaxWidth().selectable(selected, role = Role.RadioButton, onClick = { vm.answer(option.id) }).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected, null); Text(option.label, Modifier.weight(1f).padding(end = 8.dp, top = 6.dp, bottom = 6.dp))
                    }
                }
            }
            if (answer.kind == AnswerKind.SKIPPED) Text("本题已主动跳过，按中立处理。", color = MaterialTheme.colorScheme.primary)
            TextButton(onClick = { vm.answer(null); vm.next() }) { Text("跳过这题（按中立）") }
            Primary(if (s.index == 4) "准备投币" else "下一题", answer.kind != AnswerKind.UNANSWERED, vm::next)
            TextButton(onClick = vm::back) { Text(if (s.index == 0) "返回确认" else "上一题") }
        }
        Stage.READY -> {
            Heading("留一点空间，给运气", s.matter)
            SourceLabel(s.source); Coin(null); Faces(s)
            Text("五题已完成。70% 公平硬币 + 30% 本次答案倾向；正面始终在 35%～65% 之间。")
            Primary(if (state.tossing) "正在保存结果…" else "投出这枚硬币", !state.tossing, vm::toss)
            BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                val buttonWidth = (maxWidth * .25f).coerceAtLeast(88.dp * LocalDensity.current.fontScale).coerceAtMost(maxWidth)
                FilledTonalButton(onClick = vm::back, enabled = !state.tossing,
                    modifier = Modifier.width(buttonWidth).heightIn(min = 48.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp), shape = RoundedCornerShape(14.dp)) {
                    Text("修改回答", fontSize = 14.sp)
                }
            }
            var formula by rememberSaveable(s.decisionId) { mutableStateOf(false) }
            TextButton(onClick = { formula = !formula }) { Text(if (formula) "收起权重计算公式" else "查看权重计算公式") }
            if (formula) Panel { FormulaExplanation() }
        }
        Stage.ANIMATING -> state.result?.let { AnimatedCoin(it, state.settings.vibration, state.settings.sound, audio, vm::finishAnimation) }
        Stage.RESULT -> state.result?.let { ResultContent(it, state, vm) }
        Stage.ERROR, Stage.CANCELLED -> {
            Heading(if (s.stage == Stage.ERROR) "这次出题没完成" else "出题已取消")
            Notice(s.message)
            GenerateButton("重新出题", state, vm)
            if (state.settings.online) OutlinedButton(onClick = { vm.generate(offline = true) }, modifier = Modifier.fillMaxWidth()) { Text("明确切换：用离线通用题目") }
            TextButton(onClick = vm::back) { Text("返回修改") }
        }
        Stage.BLOCKED, Stage.CLARIFY -> {
            Heading(if (s.stage == Stage.BLOCKED) "这件事，不交给硬币" else "先把选择说清楚")
            Notice(s.message)
            Primary("返回修改事项和两面含义", onClick = vm::back)
        }
    }
}
private fun Modifier.toggleableCompat(value: Boolean, onChange: (Boolean) -> Unit) = toggleable(value, role = Role.Checkbox, onValueChange = onChange)
@Composable private fun Coin(face: Face?, rotation: Float = 0f, lift: Float = 0f) {
    Box(Modifier.fillMaxWidth().height(240.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(200.dp)) {
            drawCircle(Brush.radialGradient(listOf(Champagne.copy(alpha = .12f), Color.Transparent)), radius = size.minDimension * .5f)
            drawOval(Color(0xFF4B3A20).copy(alpha = .07f), topLeft = Offset(size.width * .21f, size.height * .85f), size = Size(size.width * .58f, size.height * .065f))
        }
        CoinMedallion(face, Modifier.size(156.dp).graphicsLayer {
            rotationX = rotation; translationY = lift * density; cameraDistance = 16 * density
            rotationZ = sin(rotation / 180f * PI).toFloat() * 5f
        })
    }
}
@Composable private fun CoinMedallion(face: Face?, modifier: Modifier) {
    val darkFace = face == Face.B
    val ink = Color(0xFF665024)
    Box(modifier.shadow(12.dp, CircleShape).semantics { contentDescription = when (face) { Face.A -> "正面 A"; Face.B -> "反面 B"; null -> "待投掷硬币，尚未产生结果" } }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val r = size.minDimension / 2
            val c = center
            drawCircle(Brush.linearGradient(listOf(Color(0xFFFFF5D4), Color(0xFFAB843F), Color(0xFFFAE8B5), Color(0xFFC4A364))), r)
            for (i in 0..71) {
                val angle = i * PI / 36
                drawLine(Color(0xFF9E7B38).copy(alpha = .38f),
                    c + Offset(cos(angle).toFloat(), sin(angle).toFloat()) * r * .93f,
                    c + Offset(cos(angle).toFloat(), sin(angle).toFloat()) * r * .995f, strokeWidth = r * .012f)
            }
            drawCircle(Brush.linearGradient(if (darkFace) listOf(Color(0xFFE6CD94), Color(0xFFD1B477), Color(0xFFF7E5B5), Color(0xFFC5A364))
                else listOf(Color(0xFFFFF5D6), Color(0xFFEAD199), Color(0xFFFFF0C8), Color(0xFFD7B774))), r * .89f)
            drawCircle(Color(0xFFAA8845), r * .78f, style = Stroke(r * .009f))
            drawArc(Color.White.copy(alpha = .45f), 195f, 135f, false, topLeft = Offset(r * .06f, r * .06f),
                size = Size(r * 1.88f, r * 1.88f), style = Stroke(r * .016f))
            val symbol = face?.name ?: "?"
            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = ink.toArgb()
                textSize = 43.sp.toPx()
                typeface = android.graphics.Typeface.create("serif", android.graphics.Typeface.BOLD)
            }
            val bounds = android.graphics.Rect()
            paint.getTextBounds(symbol, 0, symbol.length, bounds)
            drawContext.canvas.nativeCanvas.drawText(symbol, c.x - bounds.exactCenterX(), c.y - bounds.exactCenterY(), paint)
        }
        Text(when (face) { Face.A -> "正 面"; Face.B -> "反 面"; null -> "待 投 掷" },
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 18.dp), fontSize = 11.sp, letterSpacing = 2.sp, color = ink)
    }
}
@Composable private fun AnimatedCoin(result: TossSnapshot, vibration: Boolean, sound: Boolean, audio: CoinAudio, finished: () -> Unit) {
    Heading("硬币正在落定…", "结果已在本机保存，动画不会改变它。")
    val progress = remember(result.tossId) { Animatable(0f) }
    val context = LocalContext.current
    LaunchedEffect(result.tossId) {
        if (ValueAnimator.areAnimatorsEnabled()) {
            if (sound) audio.play(CoinSound.launch())
            progress.animateTo(1f, tween(1600, easing = LinearOutSlowInEasing))
        }
        if (sound) audio.play(CoinSound.landing())
        if (vibration) {
            @Suppress("DEPRECATION")
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            vibrator?.vibrate(if (Build.VERSION.SDK_INT >= 29) VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
                else VibrationEffect.createOneShot(14, 48))
        }
        finished()
    }
    val p = progress.value
    val lift = if (p < .78f) -sin(p / .78f * PI).toFloat() * 42f else sin((p - .78f) / .22f * PI).toFloat() * 3f
    val rotation = p * 1440f
    val reverse = cos(rotation / 180f * PI) < 0
    val face = if (p < .12f) null else if (reverse) { if (result.face == Face.A) Face.B else Face.A } else result.face
    Coin(face, if (reverse) rotation + 180f else rotation, lift)
}
@Composable private fun FormulaExplanation() {
    Text("五题等权，每题占本次答案倾向的 20%。", fontWeight = FontWeight.Medium)
    Text("强烈倾向 A = 1.00\n稍微倾向 A = 0.75\n中立或主动跳过 = 0.50\n稍微倾向 B = 0.25\n强烈倾向 B = 0.00")
    Text("s = (x₁ + x₂ + x₃ + x₄ + x₅) ÷ 5\npA = 0.70 × 0.50 + 0.30 × s\npB = 1 − pA")
    Text("70% 是公平硬币分布，30% 是本次答案倾向；不是正面固定 70%。正面范围 35%～65%。")
}
@Composable private fun ResultContent(record: TossSnapshot, state: UiState?, vm: CoinViewModel) {
    val s = record.session
    Heading(if (state == null) "这枚硬币的记录" else "这一次，硬币落在", s.matter)
    Coin(record.face)
    Text(if (record.face == Face.A) "正面 A" else "反面 B", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
    Text(if (record.face == Face.A) s.optionA else s.optionB, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    Text("可以采纳，也可以重新想一想。随机结果不证明哪种选择更正确。")
    Text("第 ${record.number} 枚 · ${time(record.createdAt)}", style = MaterialTheme.typography.bodySmall)
    SourceLabel(s.source)
    var expanded by rememberSaveable(record.tossId) { mutableStateOf(false) }
    TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "收起概率说明" else "展开概率与回答说明") }
    if (expanded) Panel {
        val p = record.probability
        Text("本次回答估计：" + when { p.score > 0.5 -> "更倾向 A"; p.score < 0.5 -> "更倾向 B"; else -> "两面平衡" })
        Text("s = ${"%.2f".format(Locale.ROOT, p.score)} · 正面 ${(p.pA*100).roundToInt()}% · 反面 ${(p.pB*100).roundToInt()}%")
        FormulaExplanation()
        Text("这些数值是本次答案的规则估计，不是心理学量表、成功率或正确率。即使全部倾向 A，仍有 35% 机会出现 B。")
        s.questions.zip(s.answers).forEach { (q, a) ->
            Text(q.text, fontWeight = FontWeight.Medium)
            Text(if (a.kind == AnswerKind.SKIPPED) "主动跳过" else q.options.firstOrNull { it.id == a.optionId }?.label ?: "未回答", style = MaterialTheme.typography.bodySmall)
        }
        Text("算法 v${s.algorithmVersion} · 题组 v${s.questionVersion}")
        s.config?.let { Text("模型快照：${it.model}\n接口：${it.baseUrl}") }
    }
    if (state != null) {
        Text("看到结果，你的感觉是？（可选，不改结果）", style = MaterialTheme.typography.bodyMedium)
        listOf("松了一口气", "有点失望", "没什么感觉").forEach { text ->
            OutlinedButton(onClick = { vm.feedback(text) }, modifier = Modifier.fillMaxWidth()) { Text(if (record.feedback == text) "✓ $text" else text) }
        }
        Primary("开始一个新决策", onClick = vm::newDecision)
        TextButton(onClick = vm::again) { Text("明确再投一次（保留之前结果）") }
        Text("多投几次不会更准确。", style = MaterialTheme.typography.bodySmall)
    } else {
        record.feedback?.let { Text("当时的反馈：$it") }
        var delete by remember { mutableStateOf(false) }
        OutlinedButton(onClick = { delete = true }) { Text("删除此记录") }
        if (delete) ConfirmDialog("删除这枚硬币的记录？", "删除后无法恢复。其他投币记录仍保留。", { delete = false }) { delete = false; vm.delete(record.tossId) }
    }
}
private fun time(value: Long) = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date(value))
@Composable private fun History(state: UiState, vm: CoinViewModel, modifier: Modifier) {
    var clear by remember { mutableStateOf(false) }
    LazyColumn(modifier.fillMaxWidth(), contentPadding = PaddingValues(22.dp), verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        if (state.notice.isNotBlank()) item { Notice(state.notice) }
        item { Column(Modifier.widthIn(max = 560.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Heading("留在本机的小小决定", "${state.history.size} 枚硬币 · 查看记录不会重新投币")
            if (state.history.isEmpty()) Text("还没有记录。完成一次投币，它会留在这里。")
        } }
        items(state.history, key = { it.tossId }) { r ->
        Surface(onClick = { vm.detail(r) }, modifier = Modifier.widthIn(max = 560.dp), shape = RoundedCornerShape(24.dp), shadowElevation = 2.dp) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(r.session.matter, fontWeight = FontWeight.Bold)
                Text("${r.face.name} · ${if (r.face == Face.A) r.session.optionA else r.session.optionB}")
                SourceLabel(r.session.source)
                Text("${time(r.createdAt)} · 第 ${r.number} 枚", style = MaterialTheme.typography.bodySmall)
            }
        }
        }
        if (state.history.isNotEmpty()) item { TextButton(onClick = { clear = true }) { Text("清空全部历史") } }
        item { Text(stringResource(R.string.boundary), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
    if (clear) ConfirmDialog("清空全部历史？", "记录和当前已完成结果将删除，无法恢复。", { clear = false }) { clear = false; vm.clearHistory() }
}
@Composable private fun ConfirmDialog(title: String, text: String, dismiss: () -> Unit, confirm: () -> Unit) {
    AlertDialog(onDismissRequest = dismiss, title = { Text(title) }, text = { Text(text) },
        confirmButton = { TextButton(onClick = confirm) { Text("确认") } }, dismissButton = { TextButton(onClick = dismiss) { Text("取消") } })
}
@Composable private fun SettingsPage(state: UiState, vm: CoinViewModel) {
    Heading("设置", "默认离线。把隐私和选择权留在你手中。")
    var online by rememberSaveable(state.settings.online) { mutableStateOf(state.settings.online) }
    var base by rememberSaveable(state.settings.config.baseUrl) { mutableStateOf(state.settings.config.baseUrl) }
    var model by rememberSaveable(state.settings.config.model) { mutableStateOf(state.settings.config.model) }
    var vibration by rememberSaveable(state.settings.vibration) { mutableStateOf(state.settings.vibration) }
    var sound by rememberSaveable(state.settings.sound) { mutableStateOf(state.settings.sound) }
    var key by remember { mutableStateOf("") }
    var saveConfirm by remember { mutableStateOf(false) }
    var testConfirm by remember { mutableStateOf(false) }
    Panel {
        Text("题目来源", fontWeight = FontWeight.Bold)
        SettingSwitch(if (online) "个人 DeepSeek 模式" else "离线通用题目", online) { online = it }
        Text("不填 Key 也可以完整投币。离线题目是通用自省模板。", style = MaterialTheme.typography.bodySmall)
    }
    OutlinedTextField(base, { base = it.take(300) }, label = { Text("Base URL（仅 HTTPS）") }, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(model, { model = it.take(100) }, label = { Text("Model API ID") }, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(key, { key = it.take(4096) }, label = { Text(if (state.hasKey) "新个人 Key（留空保留已有）" else "个人 API Key") },
        visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(), singleLine = true)
    Text(if (state.hasKey) "已为当前地址保存加密 Key" else "当前地址未配置 Key", style = MaterialTheme.typography.bodySmall)
    SettingSwitch("投币落定时振动", vibration) { vibration = it }
    SettingSwitch("轻柔投币音效", sound) { sound = it }
    Text("音效跟随手机媒体音量。", style = MaterialTheme.typography.bodySmall)
    Primary("保存设置") { saveConfirm = true }
    if (saveConfirm) ConfirmDialog("确认保存接口配置", "目标地址：$base\n模型：$model\n改地址会删除旧 Key，需为新地址重新输入和授权。Key 只加密保存在本机。", { saveConfirm = false }) {
        saveConfirm = false
        vm.saveSettings(AppSettings(online, AiConfig(base.trim(), model.trim()), vibration, sound), key)
        key = ""
    }
    OutlinedButton(onClick = { testConfirm = true }, enabled = !state.testing, modifier = Modifier.fillMaxWidth()) { Text("测试已保存配置的连接") }
    if (testConfirm) ConfirmDialog("运行连接测试？", "只向 ${state.settings.config.baseUrl} 发送简短无隐私的测试内容，会产生少量 API 用量。不会发送事项或历史。", { testConfirm = false }) {
        testConfirm = false; vm.testConnection()
    }
    if (state.testing) TextButton(onClick = vm::cancelTest) { Text("取消连接测试") }
    TextButton(onClick = vm::deleteKey) { Text("删除已保存的个人 Key") }
    UpdateSection(vm, state.settings.githubRepository)
    Panel {
        Text("规则与隐私", fontWeight = FontWeight.Bold)
        Text("70% 公平硬币 + 30% 本次答案倾向。AI 只出题，本地规则计算概率，本地安全随机源投币。")
        Text("历史、回答和草稿保存在本机，不接入埋点，不上传完整历史，禁用云备份和设备迁移备份。删除历史不会删除个人 Key，删除 Key 不会删除历史。")
        Text("个人 Key 由 Android Keystore 加密保存，设备端加密无法保证绝对不可提取。变更接口地址必须重新授权 Key。")
        Text("公共服务端代理尚未部署。高风险事项不进入投币；基础检测不能覆盖所有风险。")
    }
}
@Composable private fun SettingSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(checked, role = Role.Switch, onValueChange = onChange), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f).padding(end = 12.dp)); Switch(checked, null)
    }
}
@Composable private fun UpdateSection(vm: CoinViewModel, savedRepository: String) {
    val context = LocalContext.current
    val updater = remember { ApkUpdater(context) }
    val scope = rememberCoroutineScope()
    val github = remember { GitHubUpdates() }
    val store = remember { SettingsStore(context.applicationContext) }
    var repository by rememberSaveable(savedRepository) { mutableStateOf(savedRepository) }
    var release by remember { mutableStateOf<GitHubRelease?>(null) }
    var status by remember { mutableStateOf("") }
    var progress by remember { mutableIntStateOf(0) }
    var downloading by remember { mutableStateOf(false) }
    var candidate by remember { mutableStateOf<UpdateCandidate?>(null) }
    var checking by remember { mutableStateOf(false) }
    var permissionNeeded by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            checking = true
            try { candidate = updater.inspect(uri); permissionNeeded = false }
            catch (cancel: CancellationException) { throw cancel }
            catch (e: Exception) { status = e.message?.take(160) ?: "更新包校验失败" }
            finally { checking = false }
        }
    }
    Panel {
        Text("版本与更新", fontWeight = FontWeight.Bold)
        Text("${stringResource(R.string.app_name)} ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        OutlinedTextField(repository, { repository = it.take(240); release = null; status = "" },
            label = { Text("GitHub 更新仓库") }, placeholder = { Text("用户名/仓库名") },
            singleLine = true, enabled = !checking, modifier = Modifier.fillMaxWidth())
        Text("填写公开仓库，发布正式 Release 并附上签名 APK。只在点击时联网，不发送事项、回答或 API Key。", style = MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick = {
            scope.launch {
                checking = true; candidate = null; release = null; permissionNeeded = false; status = "正在检查更新…"
                try {
                    val repo = githubRepository(repository)
                    store.saveGithubRepository(repo)
                    release = github.latest(repo, BuildConfig.VERSION_NAME)
                    status = if (release == null) "当前已是最新版本" else "发现新版 ${release!!.tag}"
                } catch (cancel: CancellationException) { throw cancel }
                catch (e: Exception) { status = if (e is java.io.IOException) "无法连接 GitHub，请检查网络后重试" else e.message?.take(160) ?: "检查更新失败" }
                finally { checking = false }
            }
        }, enabled = !checking && repository.isNotBlank()) { Text("保存并检查更新") }
        val available = release
        if (available != null) {
            Text("${available.tag} · ${"%.1f".format(available.size / 1048576.0)} MB", fontWeight = FontWeight.SemiBold)
            if (available.notes.isNotBlank()) Text(available.notes, style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = {
                scope.launch {
                    checking = true; downloading = true; progress = 0; candidate = null; status = "正在下载更新…"
                    try {
                        candidate = updater.download(available, github) { value -> withContext(Dispatchers.Main) { progress = value } }
                        permissionNeeded = false; status = "下载完成，包名、签名及版号校验通过"
                    } catch (cancel: CancellationException) { throw cancel }
                    catch (e: Exception) { status = if (e is java.io.IOException) "下载中断或超时，请检查网络后重试" else e.message?.take(160) ?: "下载或校验失败" }
                    finally { checking = false; downloading = false }
                }
            }, enabled = !checking && candidate == null) { Text("下载并安装新版") }
        }
        if (downloading) {
            LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth())
            Text("下载进度 $progress%", style = MaterialTheme.typography.bodySmall)
        }
        if (status.isNotBlank()) Text(status, Modifier.semantics { liveRegion = LiveRegionMode.Polite }, style = MaterialTheme.typography.bodySmall)
        Text("选择后续 APK 更新包，校验同包名、同签名和更高版号。系统确认安装后保留本地数据。", style = MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick = { candidate = null; permissionNeeded = false; picker.launch(arrayOf("application/vnd.android.package-archive", "application/octet-stream")) }, enabled = !checking) { Text("选择本地 APK 更新包") }
        if (permissionNeeded) Text("请在系统页允许此来源，返回后再次点击安装。")
        if (candidate != null && permissionNeeded) TextButton(onClick = {
            try { if (updater.install(candidate!!)) { permissionNeeded = false; candidate = null } } catch (_: Exception) { vm.notice("无法打开系统安装器") }
        }) { Text("继续安装已校验更新") }
    }
    val selected = candidate
    if (selected != null && !permissionNeeded) ConfirmDialog("安装更新 ${selected.versionName}？", "版号 ${selected.versionCode}。已校验包名和签名，接下来由安卓系统确认安装。", { candidate = null }) {
        try { permissionNeeded = !updater.install(selected); if (!permissionNeeded) candidate = null }
        catch (_: Exception) { vm.notice("无法打开系统安装器"); candidate = null }
    }
}
