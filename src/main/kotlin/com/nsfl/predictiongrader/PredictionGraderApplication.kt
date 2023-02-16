package com.nsfl.predictiongrader

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.nield.kotlinstatistics.*
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.regex.Pattern
import java.util.regex.Matcher

@RestController
@SpringBootApplication
class PredictionGraderApplication {

    @RequestMapping("/")
    fun getIndex(): String {
        return "<form action=/results>Post URL<br><input name=postUrl><br><br>Correct Predictions (comma separated)<br><textarea name=correctPredictions  rows='3' cols='70'>For example: BAL,CHI,COL,PHI,SAR,YKW,ARI,AUS,HON,NOLA,OCO,SJS</textarea><br><br>Base TPE Reward<br><input name=baseTPE value=0><br><br>Per Prediction TPE Reward<br><input name=perPredictionTPE value=0.5><br><br><input type=submit></form>"
    }

    @RequestMapping("/results")
    fun getResults(
            @RequestParam postUrl: String,
            @RequestParam correctPredictions: String,
            @RequestParam baseTPE: Double,
            @RequestParam perPredictionTPE: Double
    ): String {

        val correctPredictionList =
                correctPredictions.toLowerCase().split(",").map { it }

        val documentList = ArrayList<Document>()

        val firstDocument = Jsoup.connect(postUrl)
                                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:80.0) Gecko/20100101 Firefox/80.0")
                                .get()
        val firstDocumentBody = firstDocument.body().toString()
        documentList.add(firstDocument)

        val pageCount = try {
            val startIndex = firstDocumentBody.indexOf("Pages (")
            val endIndex = firstDocumentBody.indexOf(")", startIndex)
            firstDocumentBody.substring(startIndex, endIndex)
                    .replace(Pattern.compile("[^0-9.]").toRegex(), "")
                    .toInt()
        } catch (exception: Exception) {
            1
        }

        for (i in 2..(pageCount)) {
            documentList.add(
                    Jsoup.connect(
                            "$postUrl&page=${i}"
                    ).userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:80.0) Gecko/20100101 Firefox/80.0")
                    .get()
            )
        }

        val successList = ArrayList<Pair<String, Double>>()
        val errorList = ArrayList<Pair<String, Double>>()

        documentList.forEachIndexed { documentIndex, document ->
            document.body().getElementsByClass("post classic ").forEachIndexed { elementIndex, element ->

                val username = element.getElementsByClass("largetext").text()
                val content = element.getElementsByClass("post_body scaleimages").toString()

                val predictionList = arrayListOf<String>()

                val my_pattern = "alt=\"([A-Z]+)\"".toRegex()
                var matches = my_pattern.findAll(content)

                matches.forEach { matchResult ->
                    predictionList.add(matchResult.groupValues[1].toLowerCase())
                }

                var correctPredictionCount = 0

                if (predictionList.size == correctPredictionList.size) {
                    predictionList.forEachIndexed { index, prediction ->
                        if (correctPredictionList[index].contains(prediction)) {
                            correctPredictionCount++
                        }
                    }
                } else if (predictionList.size == correctPredictionList.size * 3) {
                    predictionList.forEachIndexed { index, prediction ->
                        if (index % 3 == 2
                                && correctPredictionList[index - (((index / 3) + 1) * 2)].contains(prediction)) {
                            correctPredictionCount++
                        }
                    }
                } else {
                    correctPredictionCount = -1
                }

                val result = Pair(
                        if (username.isEmpty()) {
                            "Guest"
                        } else {
                            username
                        },
                        if (correctPredictionCount == -1) {
                            -1.0
                        } else {
                            baseTPE + (correctPredictionCount * perPredictionTPE)
                        }
                )

                if (username.isNotEmpty() && correctPredictionCount != -1) {
                    if (successList.find { it.first == username } == null) {
                        successList.add(result)
                    } else {
                        errorList.add(result)
                    }
                } else if (documentIndex != 0 || elementIndex != 0) {

                    errorList.add(result)
                }
            }
        }

        val userCount = successList.size
        val totalTPE = successList.sumByDouble { it.second }
        val modeTPE = successList.map{ it.second }.mode().joinToString()
        val averageTPE = totalTPE / userCount
        val highestTPE = successList.sortedByDescending { it.second }.first().second
        val failureTPE = averageTPE / 2

        return "<b>Errors (please verify manually, users with a positive score have likely posted twice):</b><br>" +
                errorList.sortedBy { it.first.toLowerCase() }.joinToString("<br>") {
                    "<font color=\"red\"><b>${it.first} ${it.second}</b></font>"
                } + "<br><br><b>Please verify post times manually.</b><br><br>" +
                "User count: $userCount<br>Total TPE: $totalTPE<br>Average TPE: $averageTPE<br>Mode TPE: $modeTPE<br>Highest TPE: $highestTPE<br><br>" +
                successList.sortedBy { it.first.toLowerCase() }.joinToString("<br>") {
                    if (it.second > failureTPE) {
                        "${it.first} ${it.second}"
                    } else {
                        "<font color=\"red\"><b>${it.first} ${it.second}</b></font>"
                    }
                }
    }
}

fun main(args: Array<String>) {
    runApplication<PredictionGraderApplication>(*args)
}
