package io.github.aleks007k.poezdki

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Нажал на «Износ» и т. п.: формула, цифры и откуда они взялись. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExplainSheet(e: Explanation, onDismiss: () -> Unit, onChange: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = C.Panel,
        contentColor = C.Text,
        scrimColor = C.Bg.copy(alpha = 0.7f),
    ) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Big(e.title, 22, modifier = Modifier.weight(1f))
                Big(e.amount, 22, e.color)
            }
            if (e.notSet) {
                Text(stringResource(R.string.exp_not_set), color = C.Soft, fontSize = 17.sp, lineHeight = 24.sp)
            } else {
                e.steps.forEach { step ->
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Caption(step.caption)
                        Column(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(C.Bg).padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(step.words, color = C.Soft, fontSize = 15.sp)
                            Text(step.numbers, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        }
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Caption(stringResource(R.string.exp_sources))
                    e.sources.forEachIndexed { i, src ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                buildAnnotatedString {
                                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(src.value) }
                                    append(" ")
                                    append(src.label)
                                },
                                fontSize = 15.sp, modifier = Modifier.weight(1f),
                            )
                            Text(src.origin, color = C.Muted, fontSize = 14.sp)
                        }
                        if (i < e.sources.lastIndex) HorizontalDivider(color = C.Track)
                    }
                }
            }
            Box(
                Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(18.dp))
                    .border(1.dp, C.Line, RoundedCornerShape(18.dp)).clickable(onClick = onChange),
                contentAlignment = Alignment.Center,
            ) { Text(stringResource(R.string.exp_change), fontWeight = FontWeight.Bold, fontSize = 17.sp) }
        }
    }
}
