package com.innovatewithomer.juiceforu

import javax.print.PrintServiceLookup

object PrinterService {
    fun availablePrinterNames(): List<String> =
        PrintServiceLookup.lookupPrintServices(null, null)
            .map { it.name }
            .distinct()
            .sortedWith(String.CASE_INSENSITIVE_ORDER)

    fun defaultPrinterName(): String? = PrintServiceLookup.lookupDefaultPrintService()?.name
}
