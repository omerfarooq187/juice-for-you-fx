package com.innovatewithomer.juiceforu

import javafx.scene.image.Image

/** Reuse one scaled logo image across the desktop UI. */
object BrandAssets {
    val logo: Image by lazy {
        val resource = requireNotNull(javaClass.getResource(AppBrand.current.logoResource)) {
            "${AppBrand.current.businessName} logo is missing from application resources"
        }
        Image(resource.toExternalForm(), 320.0, 0.0, true, true)
    }
}
