package presentation.items

import java.math.BigDecimal

/**
 * Example:
 *
 * @param asset: ETHBTC
 *
 * @param baseSymbol: ETH
 *
 * @param quoteSymbol: BTC
 *
 * @param price: 3243.04
 *
 * @param stepSize: 0.01
 */
data class AssetItem(
    val asset: String,
    val baseSymbol: String,
    val quoteSymbol: String,
    val stepSize: BigDecimal,
    val price: BigDecimal,
    val bidPrice: BigDecimal,
    val bidQuantity: BigDecimal,
    val askPrice: BigDecimal,
    val askQuantity: BigDecimal
)