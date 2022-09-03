package data.network

import com.binance.connector.client.enums.DefaultUrls
import com.binance.connector.client.impl.spot.Market
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import data.network.response.*

object Api {

    const val WS_URL: String = DefaultUrls.WS_URL
    const val HTTP_URL: String = DefaultUrls.PROD_URL

    private val market: Market = Market(HTTP_URL, "", false)

    private val gson: Gson = Gson()

    fun getExchangeInfo(): List<SymbolResponse> {
        val info = market.exchangeInfo(linkedMapOf())
        return gson.fromJson(info, ExchangeInfoResponse::class.java).symbolResponses.filter {
            it.status == Status.TRADING && it.orderTypes.contains(OrderType.MARKET)
                    && it.isSpotTradingAllowed
        }
    }

    fun getPrices(): List<PriceAssetResponse> {
        val info = market.tickerSymbol(linkedMapOf())
        val typeOfT = object : TypeToken<List<PriceAssetResponse>>() {}.type
        return gson.fromJson(info, typeOfT)
    }

    fun getBidsAndAsks(): List<PriceBookResponse> {
        val info = market.bookTicker(linkedMapOf())
        val typeOfT = object : TypeToken<List<PriceBookResponse>>() {}.type
        return gson.fromJson(info, typeOfT)
    }

}
