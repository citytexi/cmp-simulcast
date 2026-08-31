package dev.citytexi.simulcast.data.tool

/**
 * @param exists 경로가 실제로 존재하는지. 테스트에서 파일 시스템을 대체할 수 있게 주입한다.
 */
class ToolLocator(
    private val env: Map<String, String>,
    private val homeDir: String,
    private val exists: (String) -> Boolean,
) {

    fun adb(): String? = firstExisting(androidSdkRoots().map { "$it/platform-tools/adb" })

    fun emulator(): String? = firstExisting(androidSdkRoots().map { "$it/emulator/emulator" })

    fun xcrun(): String? = firstExisting(listOf("/usr/bin/xcrun"))

    private fun androidSdkRoots(): List<String> = buildList {
        env["ANDROID_HOME"]?.let(::add)
        env["ANDROID_SDK_ROOT"]?.let(::add)
        add("$homeDir/Library/Android/sdk")
    }

    private fun firstExisting(candidates: List<String>): String? = candidates.firstOrNull(exists)
}
