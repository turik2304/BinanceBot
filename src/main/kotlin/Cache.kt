import java.math.BigDecimal

object Cache {
    var amount: BigDecimal = BigDecimal.ZERO
        @Synchronized get
        @Synchronized set
}