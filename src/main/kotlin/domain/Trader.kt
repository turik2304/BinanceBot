package domain

import Cache
import com.binance.connector.client.SpotClient
import com.binance.connector.client.exceptions.BinanceClientException
import com.binance.connector.client.impl.SpotClientImpl
import com.binance.connector.client.impl.WebsocketClientImpl
import com.binance.connector.client.impl.spot.Market
import com.binance.connector.client.impl.spot.Trade
import com.binance.connector.client.impl.spot.UserData
import com.binance.connector.client.impl.spot.Wallet
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import data.network.Api
import data.network.response.ExchangeInfoResponse
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import java.math.BigDecimal
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean


class Trader {
    companion object {
        private const val KEY_REFRESH_PERIOD: Long = 1000L * 60 * 30
    }

    private val spotClient: SpotClient = SpotClientImpl(PROD_API_KEY, PROD_SECRET_KEY, Api.HTTP_URL)

    private val wallet: Wallet = spotClient.createWallet()

    private val orderExecutor: Trade = spotClient.createTrade()

    private val userData: UserData = spotClient.createUserData()

    private val market: Market = spotClient.createMarket()

    private val isSocketOpened: AtomicBoolean = AtomicBoolean(false)

    private val isTradingInProgress: AtomicBoolean = AtomicBoolean(false)

    private val isTradingCompleted: AtomicBoolean = AtomicBoolean(false)

    @Volatile
    private var middleOrder: Order? = null

    @Volatile
    private var sellOrder: Order? = null

    private var paySymbol: String = ""

    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val eventFlow: MutableSharedFlow<String> = MutableSharedFlow(extraBufferCapacity = 10)

    private val gson: Gson = Gson()

    private var listenKey: String = ""

    private val timer: Timer = Timer("Timer")

    private val keyRefreshTask: TimerTask = object : TimerTask() {
        override fun run() {
            userData.extendListenKey(linkedMapOf("listenKey" to listenKey))
        }
    }

    init {
        listenKey = userData.createListenKey().let { Gson().fromJson(it, ListenKey::class.java) }.key
        WebsocketClientImpl(Api.WS_URL).listenUserStream(listenKey, ::onOpenUserStream, ::onUpdateUserBalance, {}, {})
        eventFlow.onEach(::nextStep).launchIn(scope)
    }

    fun tradeTriple(
        paySymbol: String,
        buyOrder: Order,
        middleOrder: Order,
        sellOrder: Order
    ): Boolean {
        return if (!isTradingInProgress.get()) {
            isTradingInProgress.compareAndSet(false, true)

            this.paySymbol = paySymbol
            this.middleOrder = middleOrder
            this.sellOrder = sellOrder

            scope.launch { trade(buyOrder) }
            true
        } else {
            false
        }
    }

    @Volatile
    private var repeatNumber: Int = 0

    private suspend fun trade(order: Order) {
        if (isSocketOpened.get()) {
            try {
                val response = orderExecutor.newOrder(
                    linkedMapOf(
                        "symbol" to order.asset,
                        "side" to order.side,
                        "type" to "MARKET",
                        "quantity" to order.quantity,
                        "newOrderRespType" to "FULL",
                        "recvWindow" to "3000"
                    )
                )
                println("TradeResponse: $response")
            } catch (e: BinanceClientException) {
                when {
                    repeatNumber < 4 -> {
                        repeatNumber++
                        delay(200)
                        trade(order)
                    }
                    e.errorCode == -2010 -> {
                        repeatNumber = 0
                        middleOrder = null
                        sellOrder = null
                        isTradingCompleted.set(false)
                        println("FUCK, Insufficient!!! Sell back, $order")
                        sellBack()
                    }
                    else -> println(e.stackTraceToString())
                }
            }
        } else {
            this.middleOrder = null
            this.sellOrder = null
            throw Error("User Stream closed!")
        }
    }

    private fun sellBack() {
        val walletSymbols = wallet.getUserAsset(linkedMapOf("needBtcValuation" to true)).let {
            val token = object : TypeToken<List<WalletResponse>>() {}
            gson.fromJson<List<WalletResponse>>(it, token.type)
        }
        val maxWalletSymbol = walletSymbols.maxByOrNull { it.btcValuation }!!
        if (maxWalletSymbol.symbol != "USDT") {
            try {
                val exchangeInfo = market.exchangeInfo(linkedMapOf("symbol" to "${maxWalletSymbol.symbol}USDT"))
                val symbolResponse = gson.fromJson(exchangeInfo, ExchangeInfoResponse::class.java).symbolResponses.first()
                val quantity = maxWalletSymbol.amount.roundByStepSize(symbolResponse.stepSize)
                val response = orderExecutor.newOrder(
                    linkedMapOf(
                        "symbol" to symbolResponse.asset,
                        "side" to "SELL",
                        "type" to "MARKET",
                        "quantity" to quantity,
                        "newOrderRespType" to "FULL",
                        "recvWindow" to "3000"
                    )
                )
                println("Sell back response, $response")
                isTradingInProgress.set(false)
            } catch (e: Exception) {
                println("Sell back is not handled((( ${e.stackTraceToString()}")
            }
        } else {
            println("WTF, USDT exists and max, $walletSymbols")
            Cache.amount = maxWalletSymbol.amount
            middleOrder = null
            sellOrder = null
            isTradingCompleted.set(false)
            isTradingInProgress.set(false)
        }
    }

    private fun onOpenUserStream(openMessage: String) {
        isSocketOpened.compareAndSet(false, true)
        timer.scheduleAtFixedRate(keyRefreshTask, 0L, KEY_REFRESH_PERIOD)
    }

    private fun onUpdateUserBalance(rawJson: String) {
        scope.launch { eventFlow.emit(rawJson) }
    }

    private suspend fun nextStep(rawJson: String) {
        try {
            val event = gson.fromJson(rawJson, WebSocketEvent::class.java)
            if (event.eventType != EventType.ACCOUNT_UPDATE) return
            val balances = event.balances.orEmpty()
            when {
                middleOrder != null -> {
                    if (balances.isValidBalanceTo(middleOrder!!)) {
                        trade(middleOrder!!)
                        middleOrder = null
                    } else {
                        val balanceInfo = balances.find { it.symbol == middleOrder!!.paySymbol }
                        println("Insufficient funds!, $balanceInfo, $middleOrder")
                    }
                }
                sellOrder != null -> {
                    if (balances.isValidBalanceTo(sellOrder!!)) {
                        trade(sellOrder!!)
                        sellOrder = null
                        isTradingCompleted.compareAndSet(false, true)
                    } else {
                        val balanceInfo = balances.find { it.symbol == sellOrder!!.paySymbol }
                        println("Insufficient funds!, $balanceInfo, $sellOrder")
                    }
                }
                isTradingCompleted.get() -> {
                    val currentBalance = Cache.amount
                    val resultBalance = balances.find { it.symbol == paySymbol }?.amount!!
                    val profit = resultBalance - currentBalance
                    Cache.amount = resultBalance
                    val profitPercent = profit.precDiv(currentBalance) * DECIMAL_100
                    println("Result: initialBalance = $currentBalance, resultBalance = $resultBalance, profit = $profit, $profitPercent %")
                    println()
                    isTradingCompleted.compareAndSet(true, false)
                    isTradingInProgress.compareAndSet(true, false)
                }
                else -> {
                    println("Balances Updated!")
                    balances.forEach { println(it) }
                    balances.find { it.symbol == "USDT" }?.let { Cache.amount = it.amount }
                }
            }
        } catch (e: Exception) {
            if (e !is JsonSyntaxException) {
                println(e.stackTraceToString())
            }
        }
    }

    private fun List<BalanceInfo>.isValidBalanceTo(order: Order): Boolean {
        return this.any { it.symbol == order.paySymbol && it.amount >= order.payAmount }
    }

    data class ListenKey(
        @SerializedName("listenKey")
        val key: String
    )

    data class WebSocketEvent(
        @SerializedName("e")
        val eventType: EventType,
        @SerializedName("B")
        val balances: List<BalanceInfo>?
    )

    enum class EventType {
        @SerializedName("outboundAccountPosition")
        ACCOUNT_UPDATE,

        @SerializedName("balanceUpdate")
        BALANCE_UPDATE,

        @SerializedName("executionReport")
        ORDER_UPDATE
    }

    data class BalanceInfo(
        @SerializedName("a")
        val symbol: String,
        @SerializedName("f")
        val amount: BigDecimal
    )

    data class WalletResponse(
        @SerializedName("asset")
        val symbol: String,
        @SerializedName("free")
        val amount: BigDecimal,
        @SerializedName("btcValuation")
        val btcValuation: BigDecimal
    )

    data class Order(
        val asset: String,
        val quantity: BigDecimal,
        val side: String,

        val paySymbol: String,
        val payAmount: BigDecimal,
        val tradingSymbolForLogs: String
    )

}