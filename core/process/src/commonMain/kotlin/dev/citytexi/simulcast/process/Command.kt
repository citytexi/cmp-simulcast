package dev.citytexi.simulcast.process

/**
 * @param executable 실행 파일의 절대 경로. 셸을 거치지 않으므로 PATH 조회는 호출부 몫이다.
 * @param env 상속받은 부모 환경 위에 얹는 값이다(`environment().putAll(env)`) — 대체가 아니라 병합이다.
 */
data class Command(
    val executable: String,
    val args: List<String> = emptyList(),
    val env: Map<String, String> = emptyMap(),
    val workingDir: String? = null,
)
