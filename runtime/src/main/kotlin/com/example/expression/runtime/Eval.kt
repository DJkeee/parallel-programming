package com.example.expression.runtime

import com.example.expression.core.AllExpression
import com.example.expression.core.AnyExpression
import com.example.expression.core.Every2
import com.example.expression.core.Every3
import com.example.expression.core.Every4
import com.example.expression.core.Expression
import com.example.expression.core.FlatMapExpression
import com.example.expression.core.FoldExpression
import com.example.expression.core.IfExpression
import com.example.expression.core.InputExpression
import com.example.expression.core.JoinValuesExpression
import com.example.expression.core.MapExpression
import com.example.expression.core.ProjectExpression
import com.example.expression.core.Quad
import com.example.expression.core.RecExpression
import com.example.expression.core.SwitchExpression
import com.example.expression.core.ThenExpression
import com.example.expression.core.UnzipExpression
import com.example.expression.core.ValueExpression
import com.example.expression.core.ZipExpression
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

sealed interface EvalEvent<out T> {
    data class Started(val rootId: Long) : EvalEvent<Nothing>
    data class NodeStarted(val nodeId: Long) : EvalEvent<Nothing>
    data class NodeCompleted<T>(val nodeId: Long, val value: T) : EvalEvent<T>
    data class Completed<T>(val result: T) : EvalEvent<T>
    data class Failed(val error: Throwable) : EvalEvent<Nothing>
    data class Cancelled(val reason: String) : EvalEvent<Nothing>
}

class Evaluator(private val workerPool: WorkerPool = WorkerPool.default) {
    fun <T> eval(expression: Expression<T>, inputs: List<Any?> = emptyList()): T {
        return evaluateNode(expression, inputs)
    }

    fun <T> evalAsync(expression: Expression<T>, inputs: List<Any?> = emptyList()): Publisher<EvalEvent<T>> {
        val publisher = EventPublisher<EvalEvent<T>>()
        val cancelled = AtomicBoolean(false)
        val subscription = SimpleSubscription {
            cancelled.set(true)
            publisher.emit(EvalEvent.Cancelled("cancelled"))
            publisher.complete()
        }
        publisher.attachSubscription(subscription)

        workerPool.execute {
            publisher.emit(EvalEvent.Started(expression.id))
            try {
                val result = evaluateNode(expression, inputs, publisher, cancelled)
                if (!cancelled.get()) {
                    publisher.emit(EvalEvent.Completed(result))
                    publisher.complete()
                }
            } catch (ex: Throwable) {
                if (ex is CancellationException) {
                    publisher.emit(EvalEvent.Cancelled(ex.message ?: "cancelled"))
                    publisher.complete()
                } else if (!cancelled.get()) {
                    publisher.error(ex)
                }
            }
        }
        return publisher
    }

    private fun <T> evaluateNode(
        expression: Expression<T>,
        inputs: List<Any?>,
        publisher: EventPublisher<EvalEvent<T>>? = null,
        cancelled: AtomicBoolean? = null,
    ): T {
        fun onNodeStarted(id: Long) {
            publisher?.emit(EvalEvent.NodeStarted(id))
        }
        fun onNodeCompleted(id: Long, value: T) {
            publisher?.emit(EvalEvent.NodeCompleted(id, value))
        }

        if (cancelled?.get() == true) throw CancellationException()

        when (expression) {
            is ValueExpression -> {
                onNodeStarted(expression.id)
                val v = expression.value
                @Suppress("UNCHECKED_CAST")
                val result = v as T
                onNodeCompleted(expression.id, result)
                return result
            }
            is InputExpression -> {
                onNodeStarted(expression.id)
                val v = inputs.getOrNull(expression.index) as? T
                    ?: throw IllegalArgumentException("Missing input at index ${expression.index}")
                onNodeCompleted(expression.id, v)
                return v
            }
            is ThenExpression<*, *> -> {
                onNodeStarted(expression.id)
                val sourceValue = evaluateNode(expression.source as Expression<Any?>, inputs, publisher as? EventPublisher<EvalEvent<Any?>>, cancelled)
                val mapped = (expression.transform as (Any?) -> T).invoke(sourceValue)
                onNodeCompleted(expression.id, mapped)
                return mapped
            }
            is IfExpression -> {
                onNodeStarted(expression.id)
                val cond = evaluateNode(expression.condition, inputs, publisher, cancelled)
                val branch = if (cond) expression.ifTrue else expression.ifFalse
                val value = evaluateNode(branch, inputs, publisher, cancelled)
                onNodeCompleted(expression.id, value)
                return value
            }
            is SwitchExpression<*, *> -> {
                onNodeStarted(expression.id)
                val selector = evaluateNode(expression.selector as Expression<Any?>, inputs, publisher as? EventPublisher<EvalEvent<Any?>>, cancelled)
                val case = expression.cases.firstOrNull { (predicate, _) -> predicate(selector) }
                val chosen = case?.expr ?: expression.default
                val value = evaluateNode(chosen as Expression<T>, inputs, publisher, cancelled)
                onNodeCompleted(expression.id, value)
                return value
            }
            is Every2<*, *, *> -> {
                onNodeStarted(expression.id)
                val input = evaluateNode(expression.source as Expression<Any?>, inputs, publisher as? EventPublisher<EvalEvent<Any?>>, cancelled)
                val pair = Pair((expression.f1 as (Any?) -> Any?).invoke(input), (expression.f2 as (Any?) -> Any?).invoke(input))
                @Suppress("UNCHECKED_CAST")
                val result = pair as T
                onNodeCompleted(expression.id, result)
                return result
            }
            is Every3<*, *, *, *> -> {
                onNodeStarted(expression.id)
                val input = evaluateNode(expression.source as Expression<Any?>, inputs, publisher as? EventPublisher<EvalEvent<Any?>>, cancelled)
                val triple = Triple(
                    (expression.f1 as (Any?) -> Any?).invoke(input),
                    (expression.f2 as (Any?) -> Any?).invoke(input),
                    (expression.f3 as (Any?) -> Any?).invoke(input),
                )
                @Suppress("UNCHECKED_CAST")
                val result = triple as T
                onNodeCompleted(expression.id, result)
                return result
            }
            is Every4<*, *, *, *, *> -> {
                onNodeStarted(expression.id)
                val input = evaluateNode(expression.source as Expression<Any?>, inputs, publisher as? EventPublisher<EvalEvent<Any?>>, cancelled)
                val quad = Quad(
                    (expression.f1 as (Any?) -> Any?).invoke(input),
                    (expression.f2 as (Any?) -> Any?).invoke(input),
                    (expression.f3 as (Any?) -> Any?).invoke(input),
                    (expression.f4 as (Any?) -> Any?).invoke(input),
                )
                @Suppress("UNCHECKED_CAST")
                val result = quad as T
                onNodeCompleted(expression.id, result)
                return result
            }
            is ProjectExpression<*, *> -> {
                onNodeStarted(expression.id)
                val input = evaluateNode(expression.source as Expression<Any?>, inputs, publisher as? EventPublisher<EvalEvent<Any?>>, cancelled)
                val projected = (expression.projection as (Any?) -> Any?).invoke(input)
                @Suppress("UNCHECKED_CAST")
                val result = projected as T
                onNodeCompleted(expression.id, result)
                return result
            }
            is JoinValuesExpression<*> -> {
                onNodeStarted(expression.id)
                val resultList = expression.expressions.map { evaluateNode(it as Expression<Any?>, inputs, publisher as? EventPublisher<EvalEvent<Any?>>, cancelled) }
                @Suppress("UNCHECKED_CAST")
                val result = resultList as T
                onNodeCompleted(expression.id, result)
                return result
            }
            is MapExpression<*, *> -> {
                onNodeStarted(expression.id)
                val list = evaluateNode(expression.source as Expression<List<Any?>>, inputs, publisher as? EventPublisher<EvalEvent<Any?>>, cancelled)
                val result = if (expression.parallel) parallelMap(list, expression.mapper as (Any?) -> Any?) else list.map(expression.mapper as (Any?) -> Any?)
                @Suppress("UNCHECKED_CAST")
                val cast = result as T
                onNodeCompleted(expression.id, cast)
                return cast
            }
            is FlatMapExpression<*, *> -> {
                onNodeStarted(expression.id)
                val list = evaluateNode(expression.source as Expression<List<Any?>>, inputs, publisher as? EventPublisher<EvalEvent<Any?>>, cancelled)
                val mapped = if (expression.parallel) parallelMap(list, expression.mapper as (Any?) -> List<Any?>) else list.map(expression.mapper as (Any?) -> List<Any?>)
                val flat = mapped.flatten()
                @Suppress("UNCHECKED_CAST")
                val cast = flat as T
                onNodeCompleted(expression.id, cast)
                return cast
            }
            is FoldExpression<*> -> {
                onNodeStarted(expression.id)
                val list = evaluateNode(expression.source as Expression<List<Any?>>, inputs, publisher as? EventPublisher<EvalEvent<Any?>>, cancelled)
                val initial = expression.initial
                val op = expression.op as (Any?, Any?) -> Any?
                val result = if (expression.associative && list.size > 1) parallelFold(list, initial, op) else list.fold(initial, op)
                @Suppress("UNCHECKED_CAST")
                val cast = result as T
                onNodeCompleted(expression.id, cast)
                return cast
            }
            is AllExpression<*> -> {
                onNodeStarted(expression.id)
                val list = evaluateNode(expression.source as Expression<List<Any?>>, inputs, publisher as? EventPublisher<EvalEvent<Any?>>, cancelled)
                val predicate = expression.predicate as (Any?) -> Boolean
                val result = list.all(predicate)
                @Suppress("UNCHECKED_CAST")
                val cast = result as T
                onNodeCompleted(expression.id, cast)
                return cast
            }
            is AnyExpression<*> -> {
                onNodeStarted(expression.id)
                val list = evaluateNode(expression.source as Expression<List<Any?>>, inputs, publisher as? EventPublisher<EvalEvent<Any?>>, cancelled)
                val predicate = expression.predicate as (Any?) -> Boolean
                val result = list.any(predicate)
                @Suppress("UNCHECKED_CAST")
                val cast = result as T
                onNodeCompleted(expression.id, cast)
                return cast
            }
            is ZipExpression<*, *> -> {
                onNodeStarted(expression.id)
                val left = evaluateNode(expression.left as Expression<Any?>, inputs, publisher as? EventPublisher<EvalEvent<Any?>>, cancelled)
                val right = evaluateNode(expression.right as Expression<Any?>, inputs, publisher as? EventPublisher<EvalEvent<Any?>>, cancelled)
                val pair = left to right
                @Suppress("UNCHECKED_CAST")
                val cast = pair as T
                onNodeCompleted(expression.id, cast)
                return cast
            }
            is UnzipExpression<*, *> -> {
                onNodeStarted(expression.id)
                val pair = evaluateNode(expression.source as Expression<Pair<Any?, Any?>>, inputs, publisher as? EventPublisher<EvalEvent<Any?>>, cancelled)
                @Suppress("UNCHECKED_CAST")
                val cast = pair as T
                onNodeCompleted(expression.id, cast)
                return cast
            }
            is RecExpression<*> -> {
                onNodeStarted(expression.id)
                val selfRef = AtomicReference<Expression<Any?>>()
                lateinit var built: Expression<Any?>
                built = (expression.build as (Expression<Any?>) -> Expression<Any?>).invoke(
                    object : Expression<Any?> {
                        override val id: Long = expression.id
                    }.also { selfRef.set(it) },
                )
                val result = evaluateNode(built as Expression<Any?>, inputs, publisher as? EventPublisher<EvalEvent<Any?>>, cancelled)
                @Suppress("UNCHECKED_CAST")
                val cast = result as T
                onNodeCompleted(expression.id, cast)
                return cast
            }
        }
    }

    private fun <T, R> parallelMap(list: List<T>, mapper: (T) -> R): List<R> {
        val result = MutableList<R?>(list.size) { null }
        val latch = CountDownLatch(list.size)
        list.forEachIndexed { index, item ->
            workerPool.execute {
                try {
                    result[index] = mapper(item)
                } finally {
                    latch.countDown()
                }
            }
        }
        latch.await()
        @Suppress("UNCHECKED_CAST")
        return result.map { it as R }
    }

    private fun parallelFold(list: List<Any?>, initial: Any?, op: (Any?, Any?) -> Any?): Any? {
        if (list.isEmpty()) return initial
        var acc = initial
        list.chunked(2).forEach { pair ->
            acc = if (pair.size == 2) op(pair[0], pair[1]) else op(pair[0], acc)
        }
        return acc
    }
}

class CancellationException : RuntimeException("Cancelled")

class EventPublisher<T> : Publisher<T> {
    private val subscribers = mutableListOf<Subscriber<T>>()
    private var subscription: Subscription? = null

    override fun subscribe(subscriber: Subscriber<T>): Subscription {
        subscribers += subscriber
        return subscription ?: SimpleSubscription { cancelInternal("explicit cancel") }.also { subscription = it }
    }

    fun attachSubscription(sub: Subscription) {
        subscription = sub
    }

    fun emit(value: T) {
        if (subscription?.cancelled == true) return
        subscribers.forEach { it.onNext(value) }
    }

    fun error(error: Throwable) {
        if (subscription?.cancelled == true) return
        subscribers.forEach { it.onError(error) }
    }

    fun complete() {
        if (subscription?.cancelled == true) return
        subscribers.forEach { it.onComplete() }
    }

    private fun cancelInternal(reason: String) {
        emit(EvalEvent.Cancelled(reason) as T)
    }
}

fun <T> Expression<T>.eval(vararg inputs: Any?): T = Evaluator().eval(this, inputs.toList())

fun <T> Expression<T>.evalAsync(vararg inputs: Any?): Publisher<EvalEvent<T>> = Evaluator().evalAsync(this, inputs.toList())
