package presentation

import data.Repository
import data.network.Api
import domain.TripleCalculator

//WebSocket connections have a limit of 5 incoming messages per second. A message is considered:
//A PING frame
//A PONG frame
//A JSON controlled message (e.g. subscribe, unsubscribe)
//A connection that goes beyond the limit will be disconnected; IPs that are repeatedly disconnected may be banned.
//A single connection can listen to a maximum of 1024 streams.

private val repository = Repository(Api)
private val calculator = TripleCalculator(repository)

fun main(args: Array<String>) {
    calculator.start(payAmount = 17.13379848, paySymbol = "USDT", profitPercent = 1.0)


}

private fun parsePrice(event: String): Float {
    return event.substringAfterLast('c')
        .substringBefore(',')
        .filter { it.isDigit() || it == '.' }
        .toFloat()
}






