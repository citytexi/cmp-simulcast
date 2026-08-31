package dev.citytexi.simulcast.domain

class GetDevicesUseCase(private val repository: DeviceRepository) {
    suspend operator fun invoke(): DeviceListing = repository.listDevices()
}
