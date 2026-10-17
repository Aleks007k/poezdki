package io.github.aleks007k.poezdki

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** «Ночная приборная панель»: тёмный фон, янтарные цифры. */
object C {
    val Bg = Color(0xFF0F1115)
    val Panel = Color(0xFF191C22)
    val Track = Color(0xFF22262E)
    val Line = Color(0xFF2C313A)
    val Text = Color(0xFFF4F1EA)
    val Soft = Color(0xFFC9CDD3)
    val Muted = Color(0xFF9AA0AA)
    val Dim = Color(0xFF5C626C)
    val Faint = Color(0xFF4A505A)
    val Amber = Color(0xFFFFB020)
    val OnAmber = Color(0xFF1A1300)
    val AmberDark = Color(0xFF2A2312)
    val AmberSoft = Color(0xFFE8D6AE)
    val Blue = Color(0xFF7CB7FF)
    val Teal = Color(0xFF7CE0B8)
    val Red = Color(0xFFE5483B)
    val RedSoft = Color(0xFFFF8A80)
    val RedLine = Color(0xFF5A2A27)
}

val Unbounded = FontFamily(
    Font(R.font.unbounded_500, FontWeight.Medium),
    Font(R.font.unbounded_700, FontWeight.Bold),
)
val Manrope = FontFamily(
    Font(R.font.manrope_400, FontWeight.Normal),
    Font(R.font.manrope_600, FontWeight.SemiBold),
    Font(R.font.manrope_700, FontWeight.Bold),
)

@Composable
fun PoezdkiTheme(content: @Composable () -> Unit) {
    val base = Typography()
    val m = TextStyle(fontFamily = Manrope)
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = C.Amber, onPrimary = C.OnAmber,
            background = C.Bg, onBackground = C.Text,
            surface = C.Panel, onSurface = C.Text,
            surfaceVariant = C.Track, onSurfaceVariant = C.Muted,
            surfaceContainerHigh = C.Panel, outline = C.Line,
        ),
        typography = Typography(
            bodyLarge = base.bodyLarge.merge(m), bodyMedium = base.bodyMedium.merge(m),
            bodySmall = base.bodySmall.merge(m), titleLarge = base.titleLarge.merge(m),
            titleMedium = base.titleMedium.merge(m), labelLarge = base.labelLarge.merge(m),
            headlineSmall = base.headlineSmall.merge(m),
        ),
    ) {
        Surface(color = C.Bg, contentColor = C.Text, content = content)
    }
}

// ---------- Общие кусочки ----------

/** Крупные цифры и заголовки — Unbounded. */
@Composable
fun Big(text: String, size: Int, color: Color = C.Text, bold: Boolean = true, modifier: Modifier = Modifier) {
    Text(
        text, modifier = modifier, color = color, fontSize = size.sp, maxLines = 1,
        fontFamily = Unbounded, fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium,
    )
}

/** Подпись раздела: «ТОПЛИВО». */
@Composable
fun Caption(text: String, color: Color = C.Muted, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(), modifier = modifier, color = color, fontSize = 13.sp,
        fontWeight = FontWeight.Bold, letterSpacing = 1.sp,
    )
}

/** Тёмная плашка с тонкой рамкой. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Panel(
    modifier: Modifier = Modifier,
    radius: Dp = 22.dp,
    padding: Dp = 16.dp,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(radius)
    var m = modifier.clip(shape).background(C.Panel).border(BorderStroke(1.dp, C.Line), shape)
    if (onClick != null) m = m.combinedClickable(onClick = onClick, onLongClick = onLongClick)
    Column(m.padding(padding), content = content)
}

/** Пустая плитка с пунктирной рамкой (добавить). */
fun Modifier.dashedBorder(color: Color, radius: Dp) = drawBehind {
    drawRoundRect(
        color = color,
        cornerRadius = CornerRadius(radius.toPx()),
        style = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))),
    )
}

@Composable
fun Ico(res: Int, tint: Color, size: Dp = 24.dp, description: String? = null) {
    Icon(painterResource(res), contentDescription = description, tint = tint, modifier = Modifier.size(size))
}

/** Квадратная кнопка «назад» и заголовок. */
@Composable
fun BackHeader(title: String, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        SquareButton(R.drawable.ic_chevron_left, stringResource(R.string.back), C.Text, onBack)
        Spacer(Modifier.width(12.dp))
        Big(title, 22)
    }
}

@Composable
fun SquareButton(icon: Int, description: String, tint: Color, onClick: () -> Unit, enabled: Boolean = true) {
    Box(
        Modifier.size(48.dp).clip(RoundedCornerShape(14.dp))
            .border(1.dp, C.Line, RoundedCornerShape(14.dp))
            .combinedClickableIf(enabled, onClick),
        contentAlignment = Alignment.Center,
    ) { Ico(icon, if (enabled) tint else C.Faint, 22.dp, description) }
}

@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.combinedClickableIf(enabled: Boolean, onClick: () -> Unit) =
    if (enabled) combinedClickable(onClick = onClick) else this

/** Поле ввода в плашке: подпись сверху, крупное число снизу. */
@Composable
fun Field(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    numeric: Boolean = true,
    placeholder: String = "",
) {
    Panel(modifier, radius = 18.dp, padding = 0.dp) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, color = C.Muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            BasicTextField(
                value = value,
                onValueChange = { s -> onChange(if (numeric) s.filter { it.isDigit() || it == ',' || it == '.' || it == ' ' || it == '\u00A0' } else s) },
                textStyle = if (numeric) TextStyle(color = C.Text, fontFamily = Unbounded, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                else TextStyle(color = C.Text, fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 20.sp),
                singleLine = numeric,
                cursorBrush = SolidColor(C.Amber),
                keyboardOptions = if (numeric) KeyboardOptions(keyboardType = KeyboardType.Decimal) else KeyboardOptions.Default,
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { inner ->
                    Box {
                        if (value.isEmpty() && placeholder.isNotEmpty()) {
                            Text(placeholder, color = C.Dim, fontSize = 20.sp, fontFamily = if (numeric) Unbounded else Manrope)
                        }
                        inner()
                    }
                },
            )
        }
    }
}

/** Большая янтарная кнопка. */
@Composable
fun AmberButton(text: String, onClick: () -> Unit, enabled: Boolean = true, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(24.dp)
    Box(
        modifier.fillMaxWidth().clip(shape).background(if (enabled) C.Amber else C.Track)
            .combinedClickableIf(enabled, onClick).padding(vertical = 22.dp),
        contentAlignment = Alignment.Center,
    ) { Big(text, 22, if (enabled) C.OnAmber else C.Dim) }
}

/** Кнопка-ссылка в рамке: значок + текст. */
@Composable
fun OutlineLink(icon: Int, text: String, onClick: () -> Unit, color: Color = C.Amber) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier.clip(shape).border(1.5.dp, color, shape).combinedClickableIf(true, onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Ico(icon, color, 24.dp)
        Spacer(Modifier.width(10.dp))
        Text(text, color = C.Text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
    }
}

/** Полоска «● Запись идёт · 12,3 км» на других экранах во время поездки. */
@Composable
fun RecordingBar(distanceM: Double, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp).clip(shape).background(C.AmberDark)
            .combinedClickableIf(true, onClick).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(10.dp).clip(RoundedCornerShape(5.dp)).background(Color(0xFFFF5A4E)))
        Spacer(Modifier.width(10.dp))
        Text(stringResource(R.string.recording), color = C.AmberSoft, fontWeight = FontWeight.Bold, fontSize = 15.sp,
            modifier = Modifier.weight(1f))
        Text(stringResource(R.string.km_value, Format.decimal(distanceM / 1000.0, 1)), color = C.Amber,
            fontWeight = FontWeight.Bold, fontSize = 15.sp)
        Ico(R.drawable.ic_chevron_right, C.Amber, 18.dp)
    }
}
