package domain

import Cache
import data.Repository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import presentation.items.AssetItem
import java.io.File
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.text.SimpleDateFormat
import java.util.*

class TripleCalculator(private val repository: Repository) {

    private val priceRefresher: PriceRefresher = PriceRefresher()

    private val scope: CoroutineScope = CoroutineScope(SupervisorJob())

    private val sdf: SimpleDateFormat = SimpleDateFormat("dd/M/yyyy hh:mm:ss")

    private val trader: Trader = Trader()

    private fun writeToFile(result: String) {
        val currentDate = sdf.format(Date())
        File("/Users/artursibagatullin/Downloads/test2").appendText(
            "$currentDate: $result\n"
        )
    }

    var cache = 0.0

    fun start(
        payAmount: Double,
        paySymbol: String,
        profitPercent: Double,
    ) {
        val payAmountDecimal = payAmount.toBigDecimal()
        Cache.amount = payAmountDecimal
        val profitPercentDecimal = profitPercent.toBigDecimal()
        var tripleResults = calculateTriple(paySymbol)
        scope.launch {
            priceRefresher.priceFlow.collect { priceEvents ->
                tripleResults = updatePrices(tripleResults, priceEvents)
                val tradeResult = calculate(
                    payAmount = Cache.amount,
                    profitPercent = profitPercentDecimal,
                    triples = tripleResults
                ).sortedByDescending { it.profit }.take(1)
                tradeResult.forEach {
                    val isTradeStarted = trader.tradeTriple(
                        paySymbol = it.paySymbol,
                        buyOrder = it.buyOrder,
                        middleOrder = it.middleOrder,
                        sellOrder = it.sellOrder,
                    )
                    println(it.ordersInfo())
                    println(it)
                    println("IS_STARTED = $isTradeStarted")
                    println()
                }
            }
        }
    }

    private fun updatePrices(triples: List<TripleResult>, priceEvents: List<PriceRefresher.PriceEvent>): List<TripleResult> {
        val priceMap = priceEvents.associateBy { it.asset }.let {
            val hashMap = hashMapOf<String, PriceRefresher.PriceEvent>()
            hashMap.putAll(it)
            hashMap
        }
        return triples.map { triple ->
            val newBuyAsset = if (priceMap.contains(triple.buyAsset.asset)) {
                val priceInfo = priceMap[triple.buyAsset.asset]!!
                triple.buyAsset.copy(
                    price = priceInfo.price,
                    bidPrice = priceInfo.bidPrice,
                    bidQuantity = priceInfo.bidQuantity,
                    askPrice = priceInfo.askPrice,
                    askQuantity = priceInfo.askQuantity
                )
            } else {
                triple.buyAsset
            }
            val newMiddleResults = triple.middleResult.map { middle ->
                val newMiddleAsset = if (priceMap.contains(middle.middleAsset.asset)) {
                    val priceInfo = priceMap[middle.middleAsset.asset]!!
                    middle.middleAsset.copy(
                        price = priceInfo.price,
                        bidPrice = priceInfo.bidPrice,
                        bidQuantity = priceInfo.bidQuantity,
                        askPrice = priceInfo.askPrice,
                        askQuantity = priceInfo.askQuantity
                    )
                } else {
                    middle.middleAsset
                }
                val newSellAssets = middle.sellAssets.map { sell ->
                    if (priceMap.contains(sell.asset)) {
                        val priceInfo = priceMap[sell.asset]!!
                        sell.copy(
                            price = priceInfo.price,
                            bidPrice = priceInfo.bidPrice,
                            bidQuantity = priceInfo.bidQuantity,
                            askPrice = priceInfo.askPrice,
                            askQuantity = priceInfo.askQuantity
                        )
                    } else {
                        sell
                    }
                }
                middle.copy(
                    middleAsset = newMiddleAsset,
                    sellAssets = newSellAssets
                )
            }
            triple.copy(
                buyAsset = newBuyAsset,
                middleResult = newMiddleResults
            )
        }
    }

    private fun calculate(
        payAmount: BigDecimal,
        profitPercent: BigDecimal,
        triples: List<TripleResult>
    ): List<FinalTradeResult> {
        val tripleInfo = mutableListOf<FinalTradeResult>()
        triples.forEach { buyResult ->
            val buyTradeResult = trade(
                payAmount = payAmount,
                paySymbol = buyResult.buyAsset.quoteSymbol,
                asset = buyResult.buyAsset
            )
            buyResult.middleResult.forEach { middleResult ->
                val middleTradeResult = trade(
                    payAmount = buyTradeResult.purchasedAmount,
                    paySymbol = buyTradeResult.purchasedSymbol,
                    asset = middleResult.middleAsset
                )
                middleResult.sellAssets.forEach { sellAsset ->
                    val finalResult = trade(
                        payAmount = middleTradeResult.purchasedAmount,
                        paySymbol = middleTradeResult.purchasedSymbol,
                        asset = sellAsset
                    )
                    val percent =
                        ((finalResult.purchasedAmount - buyTradeResult.soldAmount).precDiv(buyTradeResult.soldAmount)) * DECIMAL_100
                    if (percent > profitPercent) {
                        val result = FinalTradeResult(
                            paySymbol = buyTradeResult.soldSymbol,
                            payAmount = buyTradeResult.soldAmount,

                            buyAsset = buyResult.buyAsset.asset,
                            buySymbol = buyTradeResult.purchasedSymbol,
                            buyAmount = buyTradeResult.purchasedAmount,
                            buyPrice = buyResult.buyAsset.price,
                            buyOrder = buyTradeResult.order,

                            middleAsset = middleResult.middleAsset.asset,
                            middleSymbol = middleTradeResult.purchasedSymbol,
                            middleAmount = middleTradeResult.purchasedAmount,
                            middlePrice = middleResult.middleAsset.price,
                            middleOrder = middleTradeResult.order,

                            sellAsset = sellAsset.asset,
                            sellSymbol = finalResult.purchasedSymbol,
                            sellAmount = finalResult.purchasedAmount,
                            sellPrice = sellAsset.price,
                            sellOrder = finalResult.order
                        )
                        tripleInfo.add(result)
                    }
                }
            }
        }
        return tripleInfo
    }

    /**
     * @param quoteSymbol what the purchase is for
     */
    private fun calculateTriple(quoteSymbol: String): List<TripleResult> {
        val allAssets = repository.getAssetItems()
        val buyAssets = allAssets.filter { it.quoteSymbol == quoteSymbol }
        val middleAssets = allAssets.filter { it.baseSymbol != quoteSymbol && it.quoteSymbol != quoteSymbol }
        val sellAssets = allAssets.filter { it.baseSymbol == quoteSymbol || it.quoteSymbol == quoteSymbol }

        return buyAssets.mapNotNull { buyAsset ->
            val symbolInMiddleTrade = buyAsset.baseSymbol

            val middleAssetsByBase = middleAssets.filter { it.baseSymbol == symbolInMiddleTrade }
            val middleResultsByBase = middleAssetsByBase.mapNotNull { middleAssetByBase ->
                val sellAssetsByBase =
                    sellAssets.filter { it.baseSymbol == middleAssetByBase.quoteSymbol || it.quoteSymbol == middleAssetByBase.quoteSymbol }
                if (sellAssetsByBase.isNotEmpty()) {
                    MiddleResult(
                        middleAsset = middleAssetByBase,
                        sellAssets = sellAssetsByBase
                    )
                } else {
                    null
                }
            }

            val middleAssetsByQuote = middleAssets.filter { it.quoteSymbol == symbolInMiddleTrade }
            val middleResultsByQuote = middleAssetsByQuote.mapNotNull { middleAssetByQuote ->
                val sellAssetsByQuote =
                    sellAssets.filter { it.baseSymbol == middleAssetByQuote.baseSymbol || it.quoteSymbol == middleAssetByQuote.baseSymbol }
                if (sellAssetsByQuote.isNotEmpty()) {
                    MiddleResult(
                        middleAsset = middleAssetByQuote,
                        sellAssets = sellAssetsByQuote
                    )
                } else {
                    null
                }
            }

            val middleResult = middleResultsByBase + middleResultsByQuote
            if (middleResult.isNotEmpty()) {
                TripleResult(
                    buyAsset = buyAsset,
                    middleResult = middleResult
                )
            } else {
                null
            }
        }
    }

    private val zeroFeeAssets: List<String> = listOf(
        "BTCAUD",
        "BTCBIDR",
        "BTCBRL",
        "BTCBUSD",
        "BTCEUR",
        "BTCGBP",
        "BTCRUB",
        "BTCTRY",
        "BTCTUSD",
        "BTCUAH",
        "BTCUSDC",
        "BTCUSDP",
        "BTCUSDT",
        "ETHBUSD"
    )

    //42_0000, USDT, BTCUSDT 21_000
    //2, BTC, BTCUSDT 21_000
    private fun trade(payAmount: BigDecimal, paySymbol: String, asset: AssetItem): TradeResult {
        val feeFactor = if (asset.asset in zeroFeeAssets) BigDecimal.ONE else FEE_FACTOR
        return when (paySymbol) {
            //buy -> sell quote, buy base -> realPurchasedAmount and BUY side to request
            asset.quoteSymbol -> {
                //8.3536645351697
                val purchasedAmount = payAmount.precDiv(asset.askPrice)
                //8.35
                val realPurchasedAmount = purchasedAmount.roundByStepSize(asset.stepSize)
                //8.35 minus Fee
                val realPurchasedAmountWithFee = realPurchasedAmount * feeFactor
                val realPayAmount = realPurchasedAmount * asset.askPrice
                TradeResult(
                    soldSymbol = paySymbol,
                    soldAmount = realPayAmount,
                    purchasedSymbol = asset.baseSymbol,
                    purchasedAmount = realPurchasedAmountWithFee,
                    order = Trader.Order(
                        asset = asset.asset,
                        quantity = realPurchasedAmount,
                        side = "BUY",
                        paySymbol = paySymbol,
                        payAmount = realPayAmount,
                        tradingSymbolForLogs = asset.baseSymbol
                    )
                )
            }
            //sell -> sell base, buy quote -> realPayAmount and SELL side to request
            asset.baseSymbol -> {
                val realPayAmount = payAmount.roundByStepSize(asset.stepSize)
                val realPurchasedAmount = realPayAmount * asset.bidPrice * feeFactor
                TradeResult(
                    soldSymbol = paySymbol,
                    soldAmount = realPayAmount,
                    purchasedSymbol = asset.quoteSymbol,
                    purchasedAmount = realPurchasedAmount,
                    order = Trader.Order(
                        asset = asset.asset,
                        quantity = realPayAmount,
                        side = "SELL",
                        paySymbol = paySymbol,
                        payAmount = realPayAmount,
                        tradingSymbolForLogs = asset.baseSymbol
                    )
                )
            }
            else -> throw Exception("Symbol don't match!, payAmount = $payAmount, paySymbol = $paySymbol, symbol = $asset")
        }
    }


    data class MiddleResult(
        val middleAsset: AssetItem,
        val sellAssets: List<AssetItem>
    )

    data class TripleResult(
        val buyAsset: AssetItem,
        val middleResult: List<MiddleResult>,
    )

    data class TradeResult(
        val soldSymbol: String,
        val soldAmount: BigDecimal,
        val purchasedSymbol: String,
        val purchasedAmount: BigDecimal,
        val order: Trader.Order
    )

    data class FinalTradeResult(
        val paySymbol: String,
        val payAmount: BigDecimal,

        val buyAsset: String,
        val buySymbol: String,
        val buyAmount: BigDecimal,
        val buyPrice: BigDecimal,
        val buyOrder: Trader.Order,

        val middleAsset: String,
        val middleSymbol: String,
        val middleAmount: BigDecimal,
        val middlePrice: BigDecimal,
        val middleOrder: Trader.Order,

        val sellAsset: String,
        val sellSymbol: String,
        val sellAmount: BigDecimal,
        val sellPrice: BigDecimal,
        val sellOrder: Trader.Order
    ) {
        val profit: BigDecimal = sellAmount - payAmount
        private val profitPercent: BigDecimal = profit.precDiv(payAmount) * DECIMAL_100

        fun ordersInfo(): String {
            return "${buyOrder.asset}, ${buyOrder.side} ${buyOrder.quantity} ${buyOrder.tradingSymbolForLogs} ___ ${middleOrder.asset}, ${middleOrder.side} ${middleOrder.quantity} ${middleOrder.tradingSymbolForLogs} ___ ${sellOrder.asset}, ${sellOrder.side} ${sellOrder.quantity} ${sellOrder.tradingSymbolForLogs} ___ Profit $profit $sellSymbol, $profitPercent %."
        }

        override fun toString(): String {
            //100 USDT -> BTCUSDT 21432.43 -> 0.2242 BTC -> BTCADA 6435.42 -> 433.42 ADA -> ADAUSDT 42.13 -> 103 USDT. Profit: 3 USDT, 3%.
            return "$payAmount $paySymbol -> $buyAsset $buyPrice -> $buyAmount $buySymbol -> $middleAsset $middlePrice -> $middleAmount $middleSymbol -> $sellAsset $sellPrice -> $sellAmount $sellSymbol. Profit: $profit $sellSymbol, $profitPercent %."
        }
    }

}

fun BigDecimal.roundByStepSize(stepSize: BigDecimal): BigDecimal {
    return this.setScale(stepSize.getScale(), RoundingMode.DOWN)
}

private fun BigDecimal.getScale(): Int {
    var decimal = this
    var scale = 0
    while (decimal < BigDecimal.ONE) {
        decimal *= BigDecimal.TEN
        scale++
    }
    return scale
}

private val FEE_FACTOR = 0.999.toBigDecimal()
val DECIMAL_100 = BigDecimal("100.0")

private val mathContext = MathContext(12, RoundingMode.FLOOR)
fun BigDecimal.precDiv(other: BigDecimal): BigDecimal = this.divide(other, mathContext)
