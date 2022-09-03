package data.network.response

import com.google.gson.annotations.SerializedName
import java.math.BigDecimal

data class PriceAssetResponse(
    @SerializedName("symbol")
    val asset: String,
    @SerializedName("price")
    val price: BigDecimal
)

data class PriceBookResponse(
    @SerializedName("symbol")
    val asset: String,
    @SerializedName("bidPrice")
    val bidPrice: BigDecimal,
    @SerializedName("bidQty")
    val bidQuantity: BigDecimal,
    @SerializedName("askPrice")
    val askPrice: BigDecimal,
    @SerializedName("askQty")
    val askQuantity: BigDecimal
)