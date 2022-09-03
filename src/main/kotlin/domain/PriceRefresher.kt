package domain

import com.binance.connector.client.impl.WebsocketClientImpl
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import data.network.Api
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.math.BigDecimal

class PriceRefresher {

    private val _priceEventRawFlow: MutableSharedFlow<String> = MutableSharedFlow()
    val priceFlow: Flow<List<PriceEvent>>
        get() = _priceEventRawFlow.map(::parseJson)

    private val scope: CoroutineScope = CoroutineScope(SupervisorJob())

    init {
        WebsocketClientImpl(Api.WS_URL).allTickerStream { rawJson ->
            scope.launch { _priceEventRawFlow.emit(rawJson) }
        }
    }

    private fun parseJson(event: String): List<PriceEvent> {
        return Gson().fromJson(event, object : TypeToken<List<PriceEvent>>() {}.type)
    }

    data class PriceEvent(
        @SerializedName("s")
        val asset: String,
        @SerializedName("c")
        val price: BigDecimal,
        @SerializedName("b")
        val bidPrice: BigDecimal,
        @SerializedName("B")
        val bidQuantity: BigDecimal,
        @SerializedName("a")
        val askPrice: BigDecimal,
        @SerializedName("A")
        val askQuantity: BigDecimal,
    )

}