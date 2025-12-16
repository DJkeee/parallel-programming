package com.example.expression.core

import java.util.concurrent.atomic.AtomicLong

sealed interface Expression<T> {
    val id: Long

    companion object : ExpressionFactory
}

interface ExpressionFactory {
    fun <T> value(value: T): Expression<T> = ValueExpression(value)

    fun <A, B> value(first: A, second: B): Expression<Pair<A, B>> = ValueExpression(first to second)

    fun <T> joinValues(vararg expressions: Expression<out T>): Expression<List<T>> = JoinValuesExpression(expressions.toList())

    fun <T> fromInput(index: Int): Expression<T> = InputExpression(index)
}

private val idGenerator = AtomicLong(1L)

fun nextId(): Long = idGenerator.getAndIncrement()

data class ValueExpression<T>(val value: T, override val id: Long = nextId()) : Expression<T>

data class JoinValuesExpression<T>(val expressions: List<Expression<out T>>, override val id: Long = nextId()) : Expression<List<T>>

data class InputExpression<T>(val index: Int, override val id: Long = nextId()) : Expression<T>

data class ThenExpression<I, O>(val source: Expression<I>, val transform: (I) -> O, override val id: Long = nextId()) : Expression<O>

data class IfExpression<T>(val condition: Expression<Boolean>, val ifTrue: Expression<T>, val ifFalse: Expression<T>, override val id: Long = nextId()) : Expression<T>

data class SwitchCase<T, R>(val predicate: (T) -> Boolean, val expr: Expression<R>)

data class SwitchExpression<T, R>(
    val selector: Expression<T>,
    val cases: List<SwitchCase<T, R>>,
    val default: Expression<R>,
    override val id: Long = nextId(),
) : Expression<R>

data class Every2<I, A, B>(val source: Expression<I>, val f1: (I) -> A, val f2: (I) -> B, override val id: Long = nextId()) : Expression<Pair<A, B>>

data class Every3<I, A, B, C>(val source: Expression<I>, val f1: (I) -> A, val f2: (I) -> B, val f3: (I) -> C, override val id: Long = nextId()) : Expression<Triple<A, B, C>>

data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

data class Every4<I, A, B, C, D>(val source: Expression<I>, val f1: (I) -> A, val f2: (I) -> B, val f3: (I) -> C, val f4: (I) -> D, override val id: Long = nextId()) : Expression<Quad<A, B, C, D>>

data class ProjectExpression<I, O>(val source: Expression<I>, val projection: (I) -> O, override val id: Long = nextId()) : Expression<O>

data class MapExpression<I, O>(val source: Expression<List<I>>, val mapper: (I) -> O, val parallel: Boolean = true, override val id: Long = nextId()) : Expression<List<O>>

data class FlatMapExpression<I, O>(val source: Expression<List<I>>, val mapper: (I) -> List<O>, val parallel: Boolean = true, override val id: Long = nextId()) : Expression<List<O>>

data class FoldExpression<T>(
    val source: Expression<List<T>>,
    val initial: T,
    val op: (T, T) -> T,
    val associative: Boolean = false,
    override val id: Long = nextId(),
) : Expression<T>

data class AllExpression<T>(val source: Expression<List<T>>, val predicate: (T) -> Boolean, override val id: Long = nextId()) : Expression<Boolean>

data class AnyExpression<T>(val source: Expression<List<T>>, val predicate: (T) -> Boolean, override val id: Long = nextId()) : Expression<Boolean>

data class ZipExpression<A, B>(val left: Expression<A>, val right: Expression<B>, override val id: Long = nextId()) : Expression<Pair<A, B>>

data class UnzipExpression<A, B>(val source: Expression<Pair<A, B>>, override val id: Long = nextId()) : Expression<Pair<A, B>>

data class RecExpression<T>(val build: (Expression<T>) -> Expression<T>, override val id: Long = nextId()) : Expression<T>
