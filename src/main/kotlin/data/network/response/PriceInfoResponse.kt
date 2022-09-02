package data.network.response

import com.google.gson.annotations.SerializedName

data class PriceInfoResponse(
    @SerializedName("symbol")
    val symbol: String,
    @SerializedName("price")
    val price: Double
)