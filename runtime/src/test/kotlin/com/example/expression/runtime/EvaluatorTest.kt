package com.example.expression.runtime

import com.example.expression.core.Expression
import com.example.expression.core.all
import com.example.expression.core.every
import com.example.expression.core.fold
import com.example.expression.core.map
import com.example.expression.core.then
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EvaluatorTest {
    private val evaluator = Evaluator(WorkerPool(4))

    @Test
    fun `value expression evaluates`() {
        val expr = Expression.value(5).then { it * 2 }
        val result = evaluator.eval(expr)
        assertEquals(10, result)
    }

    @Test
    fun `if expression`() {
        val expr = com.example.expression.core.Expression.value(true)
            .ifThen(com.example.expression.core.Expression.value("yes"))
            .orElse(com.example.expression.core.Expression.value("no"))
        val result = evaluator.eval(expr)
        assertEquals("yes", result)
    }

    @Test
    fun `parallel map`() {
        val expr = Expression.value(listOf(1, 2, 3, 4))
            .map({ it * 2 }, parallel = true)
        val result = evaluator.eval(expr)
        assertEquals(listOf(2, 4, 6, 8), result)
    }

    @Test
    fun `fold associative`() {
        val expr = Expression.value(listOf(1, 2, 3, 4))
            .fold(0, associative = true) { acc, v -> acc + v }
        val result = evaluator.eval(expr)
        assertEquals(10, result)
    }

    @Test
    fun `every combines`() {
        val expr = Expression.value(2, 3)
            .every({ (a, b) -> a + b }, { (a, b) -> a * b })
            .then { (sum, mul) -> sum + mul }
        val result = evaluator.eval(expr)
        assertEquals(2 + 3 + 6, result)
    }

    @Test
    fun `all predicate`() {
        val expr = Expression.value(listOf(2, 4, 6)).all { it % 2 == 0 }
        val result = evaluator.eval(expr)
        assertTrue(result)
    }

    @Test
    fun `async cancel`() {
        val expr = Expression.value(listOf(1, 2, 3, 4, 5)).map({
            Thread.sleep(50)
            it * 2
        }, parallel = true)
        val publisher = evaluator.evalAsync(expr)
        val events = mutableListOf<EvalEvent<List<Int>>>()
        val subscription = publisher.subscribe(object : Subscriber<EvalEvent<List<Int>>> {
            override fun onNext(value: EvalEvent<List<Int>>) { events.add(value) }
            override fun onError(error: Throwable) {}
            override fun onComplete() {}
        })
        subscription.cancel()
        assertTrue(events.any { it is EvalEvent.Cancelled })
    }
}
