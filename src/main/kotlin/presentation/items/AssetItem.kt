package presentation.items

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
    val price: Double,
    val stepSize: Double
)