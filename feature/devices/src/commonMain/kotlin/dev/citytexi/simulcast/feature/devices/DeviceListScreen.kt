package dev.citytexi.simulcast.feature.devices

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
            is Outcome.Err -> Text(outcome.error.describe())
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
}
