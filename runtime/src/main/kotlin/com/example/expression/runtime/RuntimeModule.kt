package com.example.expression.runtime

import org.koin.dsl.module

val runtimeModule = module {
    single { WorkerPool() }
    factory { Evaluator(get()) }
}
