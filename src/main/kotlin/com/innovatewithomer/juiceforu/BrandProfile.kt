package com.innovatewithomer.juiceforu

data class BrandProfile(
    val id: String,
    val businessName: String,
    val windowTitle: String,
    val dataDirectoryName: String,
    val databaseFileName: String,
    val backupFilePrefix: String,
    val logoResource: String,
    val receiptLogoResource: String,
    val receiptAddressLines: List<String>,
    val telephone: String,
    val whatsapp: String,
    val receiptFooterLines: List<String>
)

object AppBrand {
    const val PROPERTY = "juiceforu.brand"
    const val JUICE_FOR_U = "juice-for-u"
    const val MANDRA_PIZZA_HUT = "mandra-pizza-hut"

    val current: BrandProfile by lazy { profileFor(System.getProperty(PROPERTY, JUICE_FOR_U)) }

    internal fun profileFor(id: String): BrandProfile = when (id.trim().lowercase()) {
        MANDRA_PIZZA_HUT -> BrandProfile(
            id = MANDRA_PIZZA_HUT,
            businessName = "Mandra Pizza Hut",
            windowTitle = "Mandra Pizza Hut - POS",
            dataDirectoryName = "Mandra Pizza Hut",
            databaseFileName = "pizza_hut.db",
            backupFilePrefix = "pizza-hut",
            logoResource = "/com/innovatewithomer/juiceforu/brands/mandra-pizza-hut/logo.png",
            receiptLogoResource = "/com/innovatewithomer/juiceforu/brands/mandra-pizza-hut/receipt-logo.jpg",
            receiptAddressLines = listOf("Mandra Pizza Hut Near Thandi Sarak", "G.T Road Mandra"),
            telephone = "051-3591155",
            whatsapp = "0309-5107040",
            receiptFooterLines = listOf(
                "THANKS FOR CHOOSING MANDRA PIZZA HUT",
                "YOUR OPINION HELPS US IMPROVE",
                "SHARE YOUR FEEDBACK!"
            )
        )
        else -> BrandProfile(
            id = JUICE_FOR_U,
            businessName = "Juice For U",
            windowTitle = "Juice For U - POS",
            dataDirectoryName = "Juice For U",
            databaseFileName = "juiceforyou.db",
            backupFilePrefix = "juice-for-you",
            logoResource = "/com/innovatewithomer/juiceforu/logo/logo.jpg",
            receiptLogoResource = "/com/innovatewithomer/juiceforu/logo/logo.jpg",
            receiptAddressLines = listOf("Juice For U Near Thandi Sarak", "G.T Road Mandra"),
            telephone = "051-3591155",
            whatsapp = "0309-5107000",
            receiptFooterLines = listOf(
                "THANKS FOR CHOOSING JUICE FOR U",
                "YOUR SATISFACTION IS OUR PLEASURE",
                "PLEASE VISIT US AGAIN!"
            )
        )
    }
}
