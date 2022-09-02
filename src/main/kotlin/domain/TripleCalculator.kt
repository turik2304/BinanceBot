package domain

import data.Repository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import presentation.items.AssetItem
import java.io.File
import java.math.RoundingMode
import java.text.SimpleDateFormat
import java.util.*

class TripleCalculator(private val repository: Repository) {

    private val priceRefresher: PriceRefresher = PriceRefresher()

    private val scope: CoroutineScope = CoroutineScope(SupervisorJob())

    private val sdf = SimpleDateFormat("dd/M/yyyy hh:mm:ss")

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
        var tripleResults = calculateTriple(paySymbol)
        scope.launch {
            priceRefresher.priceFlow.collect { priceEvents ->
                tripleResults = updatePrices(tripleResults, priceEvents)
                val tradeResult = calculate(
                    payAmount = payAmount,
                    profitPercent = profitPercent,
                    triples = tripleResults
                ).sortedByDescending { it.profit }.take(3)
//                tradeResult.firstOrNull()?.let {
//                    if (it.profit > cache) {
//                        cache = it.profit
//                        writeToFile(it.toString())
//                    }
//                }
                repeat(10) { println() }
                tradeResult.forEach {
                    println(it)
                }
            }
        }
    }

    private fun updatePrices(triples: List<TripleResult>, priceEvents: List<PriceRefresher.PriceEvent>): List<TripleResult> {
        val priceMap = priceEvents.associate { it.asset to it.price }.let {
            val hashMap = hashMapOf<String, Double>()
            hashMap.putAll(it)
            hashMap
        }
        return triples.map { triple ->
            val newBuyAsset = if (priceMap.contains(triple.buyAsset.asset)) {
                triple.buyAsset.copy(price = priceMap[triple.buyAsset.asset]!!)
            } else {
                triple.buyAsset
            }
            val newMiddleResults = triple.middleResult.map { middle ->
                val newMiddleAsset = if (priceMap.contains(middle.middleAsset.asset)) {
                    middle.middleAsset.copy(price = priceMap[middle.middleAsset.asset]!!)
                } else {
                    middle.middleAsset
                }
                val newSellAssets = middle.sellAssets.map { sell ->
                    if (priceMap.contains(sell.asset)) {
                        sell.copy(price = priceMap[sell.asset]!!)
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
        payAmount: Double,
        profitPercent: Double,
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
                    val percent = ((finalResult.purchasedAmount - buyTradeResult.soldAmount) / buyTradeResult.soldAmount) * 100
                    if (percent > profitPercent) {
                        val result = FinalTradeResult(
                            paySymbol = buyTradeResult.soldSymbol,
                            payAmount = buyTradeResult.soldAmount,

                            buyAsset = buyResult.buyAsset.asset,
                            buySymbol = buyTradeResult.purchasedSymbol,
                            buyAmount = buyTradeResult.purchasedAmount,
                            buyPrice = buyResult.buyAsset.price,

                            middleAsset = middleResult.middleAsset.asset,
                            middleSymbol = middleTradeResult.purchasedSymbol,
                            middleAmount = middleTradeResult.purchasedAmount,
                            middlePrice = middleResult.middleAsset.price,

                            sellAsset = sellAsset.asset,
                            sellSymbol = finalResult.purchasedSymbol,
                            sellAmount = finalResult.purchasedAmount,
                            sellPrice = sellAsset.price,
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
        //inital
        //BTCUSDT
        //ADABUSD

        //BTCUSDT
        //ETHUSDT
        //ADAUSDT
        val buyAssets = allAssets.filter { it.quoteSymbol == quoteSymbol }
        //BTCAUD
        //XMRETH
        //ADABTC
        //BUSDBTC
        val middleAssets = allAssets.filter { it.baseSymbol != quoteSymbol && it.quoteSymbol != quoteSymbol }
        //USDTBUSD
        //BNBUSDT
        //BTCUSDT
        //USDTADA
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

    //42_0000, USDT, BTCUSDT 21_000
    //2, BTC, BTCUSDT 21_000
    private fun trade(payAmount: Double, paySymbol: String, asset: AssetItem): TradeResult {
        return when (paySymbol) {
            //buy
            asset.quoteSymbol -> {
                //8.3536645351697
                val purchasedAmount = payAmount / asset.price
                //8.35
                val realPurchasedAmount = purchasedAmount.roundByStepSize(asset.stepSize)
                //8.35 minus Fee
                val realPurchasedAmountWithFee = realPurchasedAmount * FEE_FACTOR
                val realPayAmount = realPurchasedAmount * asset.price
                TradeResult(
                    soldSymbol = paySymbol,
                    soldAmount = realPayAmount,
                    purchasedSymbol = asset.baseSymbol,
                    purchasedAmount = realPurchasedAmountWithFee,
                )
            }
            //sell
            asset.baseSymbol -> {
                val realPayAmount = payAmount.roundByStepSize(asset.stepSize)
                val realPurchasedAmount = realPayAmount * asset.price * FEE_FACTOR
                TradeResult(
                    soldSymbol = paySymbol,
                    soldAmount = realPayAmount,
                    purchasedSymbol = asset.quoteSymbol,
                    purchasedAmount = realPurchasedAmount
                )
            }
            else -> throw Exception("Symbol don't match!, payAmount = $payAmount, paySymbol = $paySymbol, symbol = $asset")
        }
    }

    private fun Double.roundByStepSize(stepSize: Double): Double {
        return this.toBigDecimal().setScale(stepSize.getScale(), RoundingMode.DOWN).toDouble()
    }

    private fun Double.getScale(): Int {
        var double = this
        var scale = 0
        while (double < 1.0) {
            double *= 10
            scale++
        }
        return scale
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
        val soldAmount: Double,
        val purchasedSymbol: String,
        val purchasedAmount: Double,
    )

    data class FinalTradeResult(
        val paySymbol: String,
        val payAmount: Double,

        val buyAsset: String,
        val buySymbol: String,
        val buyAmount: Double,
        val buyPrice: Double,

        val middleAsset: String,
        val middleSymbol: String,
        val middleAmount: Double,
        val middlePrice: Double,

        val sellAsset: String,
        val sellSymbol: String,
        val sellAmount: Double,
        val sellPrice: Double,
    ) {
        val profit: Double = sellAmount - payAmount
        private val profitPercent: Double = (profit / payAmount) * 100.0

        override fun toString(): String {
            //100 USDT -> BTCUSDT 21432.43 -> 0.2242 BTC -> BTCADA 6435.42 -> 433.42 ADA -> ADAUSDT 42.13 -> 103 USDT. Profit: 3 USDT, 3%.
            return "$payAmount $paySymbol -> $buyAsset $buyPrice -> $buyAmount $buySymbol -> $middleAsset $middlePrice -> $middleAmount $middleSymbol -> $sellAsset $sellPrice -> $sellAmount $sellSymbol. Profit: $profit $sellSymbol, $profitPercent %."
        }
    }

    companion object {
        private const val FEE_FACTOR = 0.999
    }

}