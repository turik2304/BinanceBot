package data.network

import com.binance.connector.client.enums.DefaultUrls
import com.binance.connector.client.impl.spot.Market
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import data.network.response.ExchangeInfoResponse
import data.network.response.PriceInfoResponse
import data.network.response.Status
import data.network.response.SymbolResponse

object Api {

    const val WS_URL: String = DefaultUrls.WS_URL
    const val HTTP_URL: String = DefaultUrls.PROD_URL

    fun getExchangeInfo(status: Status = Status.TRADING): List<SymbolResponse> {
        val info = Market(HTTP_URL, "", false).exchangeInfo(linkedMapOf())
        return Gson().fromJson(info, ExchangeInfoResponse::class.java).symbolResponses.filter { it.status == status }
    }

    fun getPrices(): List<PriceInfoResponse> {
        val info = Market(HTTP_URL, "", false).tickerSymbol(linkedMapOf())
        val typeOfT = object : TypeToken<List<PriceInfoResponse>>() {}.type
        return Gson().fromJson<List<PriceInfoResponse>>(info, typeOfT)
    }


}
