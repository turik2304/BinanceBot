package data

import data.network.Api
import presentation.items.AssetItem

class Repository(private val api: Api) {

    fun getAssetItems(): List<AssetItem> {
        val symbols = api.getExchangeInfo()
        val prices = api.getPrices()
        val book = api.getBidsAndAsks()
        val items = symbols.mapNotNull { symbolResponse ->
            prices.find { it.asset == symbolResponse.asset }?.price?.let { price ->
                book.find { it.asset == symbolResponse.asset }?.let { book ->
                    AssetItem(
                        asset = symbolResponse.asset,
                        baseSymbol = symbolResponse.baseSymbol,
                        quoteSymbol = symbolResponse.quoteSymbol,
                        stepSize = symbolResponse.stepSize,
                        price = price,
                        bidPrice = book.bidPrice,
                        bidQuantity = book.bidQuantity,
                        askPrice = book.askPrice,
                        askQuantity = book.askQuantity
                    )
                }
            }
        }
        println("getAssetItems()")
        return items
    }

}