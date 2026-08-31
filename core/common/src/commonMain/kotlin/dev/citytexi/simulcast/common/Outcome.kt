package dev.citytexi.simulcast.common

sealed interface Outcome<out T, out E> {
    data class Ok<out T>(val value: T) : Outcome<T, Nothing>
    data class Err<out E>(val error: E) : Outcome<Nothing, E>
}

inline fun <T, E, R> Outcome<T, E>.map(transform: (T) -> R): Outcome<R, E> = when (this) {
    is Outcome.Ok -> Outcome.Ok(transform(value))
    is Outcome.Err -> this
}

fun <T, E> Outcome<T, E>.valueOrNull(): T? = when (this) {
    is Outcome.Ok -> value
    is Outcome.Err -> null
}
