package data.network.response

import com.google.gson.annotations.SerializedName

data class ExchangeInfoResponse(
    @SerializedName("symbols")
    val symbolResponses: List<SymbolResponse>
)

data class SymbolResponse(
    @SerializedName("symbol")
    val symbol: String,
    @SerializedName("baseAsset")
    val baseAsset: String,
    @SerializedName("quoteAsset")
    val quoteAsset: String,
    @SerializedName("status")
    val status: Status,
    @SerializedName("orderTypes")
    val orderTypes: List<OrderType>,
    @SerializedName("permissions")
    val permissions: List<Permission>,
    @SerializedName("filters")
    private val filters: List<Filter>
) {
    private val lotStepSize: Double
        get() = filters.find { it.filterType == "LOT_SIZE" }?.stepSize ?: 0.0

    private val marketLotStepSize: Double
        get() = filters.find { it.filterType == "MARKET_LOT_SIZE" }?.stepSize ?: 0.0

    val stepSize: Double
        get() = if (lotStepSize == 0.0) marketLotStepSize else lotStepSize
}

enum class Status {
    @SerializedName("TRADING")
    TRADING,

    @SerializedName("BREAK")
    BREAK
}

enum class OrderType {
    @SerializedName("LIMIT")
    LIMIT,

    @SerializedName("LIMIT_MAKER")
    LIMIT_MAKER,

    @SerializedName("MARKET")
    MARKET,

    @SerializedName("STOP_LOSS")
    STOP_LOSS,

    @SerializedName("STOP_LOSS_LIMIT")
    STOP_LOSS_LIMIT,

    @SerializedName("TAKE_PROFIT")
    TAKE_PROFIT,

    @SerializedName("TAKE_PROFIT_LIMIT")
    TAKE_PROFIT_LIMIT
}

enum class Permission {
    @SerializedName("SPOT")
    SPOT,

    @SerializedName("MARGIN")
    MARGIN
}

data class Filter(
    @SerializedName("filterType")
    val filterType: String,
    @SerializedName("stepSize")
    val stepSize: Double?
)