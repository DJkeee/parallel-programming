package com.example.expression.core

import kotlin.test.Test
import kotlin.test.assertEquals

class ExpressionDslTest {
    @Test
    fun `then composes`() {
        val expr = Expression.value(2).then { it + 3 }
        if (expr is ThenExpression<*, *>) {
            val value = (expr.transform as (Int) -> Int).invoke(2)
            assertEquals(5, value)
        }
    }
}
