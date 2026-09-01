package dev.citytexi.simulcast.feature.devices

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
        state.refreshFailure?.let { failure ->
            Text("새로고침 실패: ${failure.truncateForDisplay()}")
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
            is Outcome.Err -> {
                // 에러가 바뀌면(예: 재시도 후 다른 실패) 스크롤 위치도 처음(0)부터 다시 시작해야
                // 새 에러의 첫 줄이 가려지지 않는다 — 같은 슬롯의 이전 ScrollState를 재사용하면 안 된다.
                val scrollState = remember(outcome.error) { ScrollState(0) }
                // weight(1f)로 이 Text가 컬럼의 남은 공간 전체를 차지하도록 못박아야 verticalScroll에
                // 안정적인 뷰포트가 생긴다. 이게 없으면 Text는 남은 공간만큼만 측정되어 그 안에서
                // 잘려 보일 뿐 스크롤로 나머지에 닿을 방법이 없었다 — 그게 고치기 전 증상이었다.
                // 스크롤은 기본적으로 맨 위(0)에서 시작하므로 진단에 중요한 첫 줄은 그대로 보인다.
                Text(
                    outcome.error.describe(),
                    modifier = Modifier.weight(1f).verticalScroll(scrollState),
                )
            }
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
    is DeviceError.ToolFailed -> "$tool 실패 (exit $exitCode): $stderr"
    is DeviceError.Timeout -> "$tool 응답이 없다"
    is DeviceError.ParseFailed -> "$tool 출력을 읽지 못했다: $detail"
}.truncateForDisplay()

/** 시작 부분(보통 가장 진단적인 줄)을 남기고 자른다 — 스크롤이 있어도 병적으로 큰 메시지는 막는다. */
private fun String.truncateForDisplay(): String =
    if (length <= MAX_ERROR_TEXT_LENGTH) this else take(MAX_ERROR_TEXT_LENGTH) + "…"

private const val MAX_ERROR_TEXT_LENGTH = 4_000
