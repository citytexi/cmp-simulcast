package dev.citytexi.simulcast.feature.devices

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.citytexi.simulcast.common.Outcome
import dev.citytexi.simulcast.domain.Device
import dev.citytexi.simulcast.domain.DeviceError
import org.koin.compose.viewmodel.koinViewModel
import org.orbitmvi.orbit.compose.collectAsState

@Composable
fun DeviceListScreen(viewModel: DeviceListViewModel = koinViewModel()) {
    val state by viewModel.collectAsState()

    LaunchedEffect(Unit) { viewModel.refresh() }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Button(onClick = viewModel::refresh, enabled = !state.loading) { Text("새로고침") }
        if (state.loading) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        Row(Modifier.fillMaxSize()) {
            DeviceColumn("Android", state.android, Modifier.weight(1f))
            DeviceColumn("iOS", state.ios, Modifier.weight(1f))
        }
    }
}

@Composable
private fun DeviceColumn(
    title: String,
    outcome: Outcome<List<Device>, DeviceError>?,
    modifier: Modifier = Modifier,
) {
    Column(modifier.padding(8.dp)) {
        Text(title)
        when (outcome) {
            null -> Text("조회 전")
            is Outcome.Err ->
                // weight(1f)로 이 Text의 높이를 컬럼 내 남은 공간으로 못박아야 verticalScroll이
                // 먹는다 — 안 그러면 Column이 자식을 넘치는 높이 그대로 그려서(클리핑 없음) 긴
                // stderr가 화면 아래로 흘러나간다. 스크롤은 기본적으로 맨 위에서 시작하므로 진단에
                // 중요한 첫 줄은 스크롤 없이도 보인다.
                Text(
                    outcome.error.describe(),
                    modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                )
            is Outcome.Ok ->
                if (outcome.value.isEmpty()) {
                    Text("없음")
                } else {
                    LazyColumn {
                        items(outcome.value, key = { it.id }) { device ->
                            Text("${device.name} · ${device.state}")
                        }
                    }
                }
        }
    }
}

private fun DeviceError.describe(): String = when (this) {
    is DeviceError.ToolNotFound -> "$tool 을 찾지 못했다"
    is DeviceError.ToolFailed -> "$tool 실패 (exit $exitCode): ${stderr.truncateForDisplay()}"
    is DeviceError.Timeout -> "$tool 응답이 없다"
    is DeviceError.ParseFailed -> "$tool 출력을 읽지 못했다: $detail"
}

/** 시작 부분(보통 가장 진단적인 줄)을 남기고 자른다 — 스크롤이 있어도 병적으로 큰 stderr는 막는다. */
private fun String.truncateForDisplay(maxLength: Int = MAX_ERROR_TEXT_LENGTH): String =
    if (length <= maxLength) this else take(maxLength) + "…"

private const val MAX_ERROR_TEXT_LENGTH = 4_000
