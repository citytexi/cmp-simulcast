package dev.citytexi.simulcast.data

import dev.citytexi.simulcast.data.android.AndroidDeviceSource
import dev.citytexi.simulcast.data.ios.IosDeviceSource
import dev.citytexi.simulcast.domain.DeviceListing
import dev.citytexi.simulcast.domain.DeviceRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

class DeviceRepositoryImpl(
    private val android: AndroidDeviceSource,
    private val ios: IosDeviceSource,
) : DeviceRepository {

    override suspend fun listDevices(): DeviceListing = coroutineScope {
        val androidResult = async { android.list() }
        val iosResult = async { ios.list() }
        DeviceListing(androidResult.await(), iosResult.await())
    }
}
