package com.example.expression.core

fun <I, O> Expression<I>.then(transform: (I) -> O): Expression<O> = ThenExpression(this, transform)

fun <T> Expression<Boolean>.ifThen(ifTrue: Expression<T>): PartialIf<T> = PartialIf(this, ifTrue)

class PartialIf<T>(private val condition: Expression<Boolean>, private val ifTrue: Expression<T>) {
    fun orElse(ifFalse: Expression<T>): Expression<T> = IfExpression(condition, ifTrue, ifFalse)
}

fun <T> Expression<Boolean>.then(ifTrue: Expression<T>): PartialIf<T> = PartialIf(this, ifTrue)
fun <T> Expression<Boolean>.then(ifTrue: () -> Expression<T>): PartialIf<T> = PartialIf(this, ifTrue())

fun <T> Expression<T>.switch(builder: SwitchBuilder<T>.() -> Unit): Expression<Any?> {
    val b = SwitchBuilder<T>()
    builder.invoke(b)
    val defaultBranch = b.default ?: throw IllegalStateException("Default branch required")
    @Suppress("UNCHECKED_CAST")
    return SwitchExpression(this, b.cases as List<SwitchCase<T, Any?>>, defaultBranch as Expression<Any?>)
}

class SwitchBuilder<T> {
    val cases = mutableListOf<SwitchCase<T, *>>()
    var default: Expression<*>? = null

    fun <R> case(predicate: (T) -> Boolean, expr: Expression<R>) {
        cases += SwitchCase(predicate, expr)
    }

    fun <R> otherwise(expr: Expression<R>) {
        default = expr
    }
}

fun <I, A, B> Expression<I>.every(f1: (I) -> A, f2: (I) -> B): Expression<Pair<A, B>> = Every2(this, f1, f2)

fun <I, A, B, C> Expression<I>.every(f1: (I) -> A, f2: (I) -> B, f3: (I) -> C): Expression<Triple<A, B, C>> = Every3(this, f1, f2, f3)

fun <I, A, B, C, D> Expression<I>.every(
    f1: (I) -> A,
    f2: (I) -> B,
    f3: (I) -> C,
    f4: (I) -> D,
): Expression<Quad<A, B, C, D>> = Every4(this, f1, f2, f3, f4)

fun <I, O> Expression<I>.project(projection: (I) -> O): Expression<O> = ProjectExpression(this, projection)

fun <T, R> Expression<List<T>>.map(mapper: (T) -> R, parallel: Boolean = true): Expression<List<R>> = MapExpression(this, mapper, parallel)

fun <T, R> Expression<List<T>>.flatMap(mapper: (T) -> List<R>, parallel: Boolean = true): Expression<List<R>> = FlatMapExpression(this, mapper, parallel)

fun <T> Expression<List<T>>.fold(initial: T, associative: Boolean = false, op: (T, T) -> T): Expression<T> = FoldExpression(this, initial, op, associative)

fun <T> Expression<List<T>>.all(predicate: (T) -> Boolean): Expression<Boolean> = AllExpression(this, predicate)

fun <T> Expression<List<T>>.any(predicate: (T) -> Boolean): Expression<Boolean> = AnyExpression(this, predicate)

fun <A, B> Expression<A>.zip(other: Expression<B>): Expression<Pair<A, B>> = ZipExpression(this, other)

fun <A, B> Expression<Pair<A, B>>.unzip(): Expression<Pair<A, B>> = UnzipExpression(this)

fun <T> Expression<T>.recurse(build: (Expression<T>) -> Expression<T>): Expression<T> = RecExpression(build)
