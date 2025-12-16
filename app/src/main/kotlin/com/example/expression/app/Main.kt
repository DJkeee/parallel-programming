package com.example.expression.app

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.example.expression.core.Expression
import com.example.expression.core.then
import com.example.expression.runtime.EvalEvent
import com.example.expression.runtime.evalAsync
import com.example.expression.runtime.runtimeModule
import org.koin.core.context.startKoin
import org.koin.java.KoinJavaComponent.inject

fun main() {
    startKoin { modules(runtimeModule) }
    application {
        Window(onCloseRequest = ::exitApplication, title = "Expression DSL") {
            App()
        }
    }
}

@Composable
@Preview
fun App() {
    val log = remember { mutableStateListOf<String>() }
    val resultState = remember { mutableStateOf<String?>(null) }
    val evaluator by inject<com.example.expression.runtime.Evaluator>(com.example.expression.runtime.Evaluator::class.java)
    val subscriptionState = remember { mutableStateOf<com.example.expression.runtime.Subscription?>(null) }

    MaterialTheme {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    log.clear()
                    resultState.value = null
                    val expr = sampleComputation()
                    val publisher = expr.evalAsync()
                    val subscription = publisher.subscribe(object : com.example.expression.runtime.Subscriber<EvalEvent<Double>> {
                        override fun onNext(value: EvalEvent<Double>) {
                            log.add(value.toString())
                            if (value is EvalEvent.Completed) resultState.value = value.result.toString()
                            if (value is EvalEvent.Failed) resultState.value = value.error.message
                        }

                        override fun onError(error: Throwable) {
                            log.add("error: ${error.message}")
                            resultState.value = error.message
                        }

                        override fun onComplete() {
                            log.add("completed")
                        }
                    })
                    subscriptionState.value = subscription
                }) { Text("Запустить пример из задания") }
                Button(onClick = {
                    subscriptionState.value?.cancel()
                    log.add("cancel requested")
                }) { Text("Отменить") }
                Button(onClick = {
                    log.clear()
                    resultState.value = null
                    val expr = mapFoldDemo()
                    val publisher = expr.evalAsync()
                    val subscription = publisher.subscribe(object : com.example.expression.runtime.Subscriber<EvalEvent<Int>> {
                        override fun onNext(value: EvalEvent<Int>) {
                            log.add(value.toString())
                            if (value is EvalEvent.Completed) resultState.value = value.result.toString()
                        }

                        override fun onError(error: Throwable) { log.add("error: ${error.message}") }
                        override fun onComplete() { log.add("completed") }
                    })
                    subscriptionState.value = subscription
                }) { Text("Запустить Map/Fold demo") }
            }
            Text("События:")
            Column(modifier = Modifier.weight(1f)) {
                log.forEach { Text(it) }
            }
            Text("Результат: ${resultState.value ?: ""}")
        }
    }
}

fun sampleComputation(): Expression<Double> {
    return Expression.value(2, 3)
        .every(
            { (a, b) -> ((a + b) / 2.0).let { it * it } },
            { (a, b) -> ((a - b) / 2.0).let { it * it } },
            { (a, b) -> maxOf(a, b) },
            { (a, b) -> minOf(a, b) },
        )
        .then { (halfSum2, halfDiff2, mx, mn) -> (halfSum2 + halfDiff2) * mx / mn }
}

fun mapFoldDemo(): Expression<Int> {
    val numbers = Expression.value(listOf(1, 2, 3, 4, 5))
    return numbers
        .map({ it * 2 }, parallel = true)
        .fold(0, associative = true) { acc, value -> acc + value }
}
