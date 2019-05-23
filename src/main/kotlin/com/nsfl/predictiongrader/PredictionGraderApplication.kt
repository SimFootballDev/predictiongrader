package com.nsfl.predictiongrader

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@SpringBootApplication
class PredictionGraderApplication {

    @RequestMapping
    fun test(): String {
        return "Hello"
    }
}

fun main(args: Array<String>) {
    runApplication<PredictionGraderApplication>(*args)
}