package dev.citytexi.simulcast.feature.devices

import androidx.lifecycle.ViewModel
import dev.citytexi.simulcast.domain.GetDevicesUseCase
import org.orbitmvi.orbit.OrbitContainerHost
import org.orbitmvi.orbit.viewmodel.orbitContainer
import kotlin.coroutines.cancellation.CancellationException

class DeviceListViewModel(
    private val getDevices: GetDevicesUseCase,
) : ViewModel(), OrbitContainerHost<DeviceListState, DeviceListState, Nothing> {

    override val container = orbitContainer<DeviceListState, Nothing>(DeviceListState())

    fun refresh() = intent {
        if (state.loading) return@intent
        reduce { state.copy(loading = true, refreshFailure = null) }
        try {
            val listing = getDevices()
            reduce { state.copy(loading = false, android = listing.android, ios = listing.ios) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // getDevices()가 던지는 일은 계약 위반이다(ADR-0004: 실패는 값으로 돌아온다) — 그래도
            // 던지면 loading 가드가 영원히 잠기지 않게 여기서 되돌리고, 실패 자체도 다른 실패들과
            // 마찬가지로 state 의 값으로 남긴다. 다시 던지지는 않는다: Orbit 12.0.0의 intent
            // 디스패치는 인텐트마다 컨테이너의 intentJob 아래 자식 Job으로 실행되는데, 처리 안 된
            // 예외가 그 Job 밖으로 나가면 intentJob 자체가 취소되어 이 호출뿐 아니라 이후의 모든
            // refresh()가 조용히 아무 일도 안 하게 된다 — loading은 false로 돌아왔는데 새로고침
            // 버튼을 눌러도 반응이 없는, 지금 가드보다 알아채기 더 어려운 상태다.
            reduce { state.copy(loading = false, refreshFailure = e.message ?: e::class.simpleName ?: "알 수 없는 오류") }
        }
    }
}
