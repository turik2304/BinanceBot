package data

import data.network.Api
import presentation.items.AssetItem

class Repository(private val api: Api) {

    fun getAssetItems(): List<AssetItem> {
        val symbols = api.getExchangeInfo()
        val prices = api.getPrices()
        return symbols.mapNotNull { symbolResponse ->
            prices.find { it.symbol == symbolResponse.symbol }?.price?.let { price ->
                AssetItem(
                    asset = symbolResponse.symbol,
                    baseSymbol = symbolResponse.baseAsset,
                    quoteSymbol = symbolResponse.quoteAsset,
                    price = price,
                    stepSize = symbolResponse.stepSize
                )
            }
        }
    }

}