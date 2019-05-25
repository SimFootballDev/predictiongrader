package com.nsfl.predictiongrader

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.regex.Pattern

@RestController
@SpringBootApplication
class PredictionGraderApplication {

    @RequestMapping("/")
    fun getIndex(): String {
        return "<form action=/results>Post URL<br><input name=postUrl><br><br>Correct Predictions (comma separated)<br><input name=correctPredictions><br><br>Base TPE Reward<br><input name=baseTPE value=0><br><br>Per Prediction TPE Reward<br><input name=perPredictionTPE value=0.5><br><br><input type=submit></form>"
    }

    @RequestMapping("/results")
    fun getResults(
            @RequestParam postUrl: String,
            @RequestParam correctPredictions: String,
            @RequestParam baseTPE: Double,
            @RequestParam perPredictionTPE: Double
    ): String {

        val correctPredictionList =
                correctPredictions.toLowerCase().split(",").map { Prediction.fromName(it) }

        val documentList = ArrayList<Document>()

        val firstDocument = Jsoup.connect(postUrl).get()
        val firstDocumentBody = firstDocument.body().toString()
        documentList.add(firstDocument)

        val pageCount = try {
            val startIndex = firstDocumentBody.indexOf("Pages:</a>")
            val endIndex = firstDocumentBody.indexOf(")", startIndex)
            firstDocumentBody.substring(startIndex, endIndex)
                    .replace(Pattern.compile("[^0-9.]").toRegex(), "")
                    .toInt()
        } catch (exception: Exception) {
            1
        }

        for (i in 1..(pageCount - 1)) {
            documentList.add(
                    Jsoup.connect(
                            "$postUrl&st=${i * 14}"
                    ).get()
            )
        }

        val successList = ArrayList<Pair<String, Double>>()
        val errorList = ArrayList<Pair<String, Double>>()

        documentList.forEachIndexed { documentIndex, document ->
            document.body().getElementsByClass("post-normal").forEachIndexed { elementIndex, element ->

                val username = element.getElementsByClass("normalname").text()
                val content = element.getElementsByClass("postcolor").toString()

                val predictionList = arrayListOf<Prediction>()
                var currentIndex = 0

                while (currentIndex < content.length &&
                        content.substring(currentIndex).contains("emo&:")) {

                    val startIndex = content.indexOf("emo&:", currentIndex) + 5
                    val endIndex = content.indexOf(":", startIndex)

                    Prediction.fromName(content.substring(startIndex, endIndex))?.let {
                        predictionList.add(it)
                    }

                    currentIndex = endIndex + 1
                }

                var correctPredictionCount = 0

                if (predictionList.size == correctPredictionList.size) {
                    predictionList.forEachIndexed { index, prediction ->
                        if (correctPredictionList[index] == prediction) {
                            correctPredictionCount++
                        }
                    }
                } else if (predictionList.size == correctPredictionList.size * 3) {
                    predictionList.forEachIndexed { index, prediction ->
                        if (index % 3 == 2
                                && correctPredictionList[index - (((index / 3) + 1) * 2)] == prediction) {
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
        val averageTPE = totalTPE / userCount
        val highestTPE = successList.sortedByDescending { it.second }.first().second
        val failureTPE = averageTPE / 2

        return "<b>Errors:</b><br>" +
                errorList.sortedBy { it.first.toLowerCase() }.joinToString("<br>") {
                    "<font color=\"red\"><b>${it.first} ${it.second}</b></font>"
                } + "<br><br><b>Please verify post times manually.</b><br><br>" +
                "User count: $userCount<br>Total TPE: $totalTPE<br>Average TPE: $averageTPE<br>Highest TPE: $highestTPE<br><br>" +
                successList.sortedBy { it.first.toLowerCase() }.joinToString("<br>") {
                    if (it.second > failureTPE) {
                        "${it.first} ${it.second}"
                    } else {
                        "<font color=\"red\"><b>${it.first} ${it.second}</b></font>"
                    }
                }
    }

    enum class Prediction(val predictionName: String) {

        BALTIMORE_HAWKS("hawks"),
        COLORADO_YETI("yeti"),
        PHILADELPHIA_LIBERTY("liberty"),
        YELLOWKNIFE_WRAITHS("wraiths"),
        ARIZONA_OUTLAWS("outlaws"),
        NEW_ORLEANS_SECOND_LINE("secondline"),
        ORANGE_COUNTY_OTTERS("otters"),
        SAN_JOSE_SABERCATS("sabercats");

        companion object {
            fun fromName(predictionName: String) =
                    values().find { it.predictionName == predictionName }
        }
    }
}

fun main(args: Array<String>) {
    runApplication<PredictionGraderApplication>(*args)
}