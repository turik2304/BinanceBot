package presentation

import data.Repository
import data.network.Api
import domain.TripleCalculator

private val repository = Repository(Api)
private val calculator = TripleCalculator(repository)

fun main(args: Array<String>) {
    calculator.start(
        payAmount = 16.63647066,
        paySymbol = "USDT",
        profitPercent = 0.0
    )
}

