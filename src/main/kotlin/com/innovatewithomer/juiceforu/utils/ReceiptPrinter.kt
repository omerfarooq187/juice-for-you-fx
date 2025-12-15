package com.innovatewithomer.juiceforu.utils

import com.github.anastaciocintra.escpos.EscPos
import com.github.anastaciocintra.escpos.EscPosConst
import com.github.anastaciocintra.escpos.image.CoffeeImageImpl
import com.github.anastaciocintra.escpos.image.EscPosImage
import com.github.anastaciocintra.escpos.image.BitImageWrapper
import com.github.anastaciocintra.escpos.image.BitonalOrderedDither
import com.github.anastaciocintra.escpos.image.BitonalThreshold
import com.github.anastaciocintra.output.PrinterOutputStream
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.nio.charset.Charset
import java.text.SimpleDateFormat
import java.util.*
import javax.imageio.ImageIO
import com.innovatewithomer.juiceforu.models.Order
import com.innovatewithomer.juiceforu.models.OrderItem
import java.awt.Color

class ReceiptPrinter {

    private val lineWidth = 48
    private val charset = Charset.forName("CP437")

    fun buildReceiptContent(order: Order): ByteArray {
        val sdfDate = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        val sdfTime = SimpleDateFormat("HH:mm", Locale.getDefault())

        val initialize = byteArrayOf(0x1B, 0x40)
        val boldOn = byteArrayOf(0x1B, 0x45, 0x01)
        val boldOff = byteArrayOf(0x1B, 0x45, 0x00)
        val doubleHeightOn = byteArrayOf(0x1B, 0x21, 0x10)
        val doubleWidthOn = byteArrayOf(0x1B, 0x21, 0x20)
        val normalText = byteArrayOf(0x1B, 0x21, 0x00)
        val centerAlign = byteArrayOf(0x1B, 0x61, 0x01)
        val leftAlign = byteArrayOf(0x1B, 0x61, 0x00)
        val cutPaper = byteArrayOf(0x1D, 0x56, 0x41, 0x10)

        val colItemWidth = 30
        val colQtyWidth = 4
        val colPriceWidth = 12

        return ByteArrayOutputStream().apply {
            write(initialize)

            // --- HEADER ---
            write(centerAlign)
            write(doubleWidthOn)
            write(boldOn)
            write("KITCHEN - ${order.orderType}\n".toByteArray(charset))
            write(normalText)
            write(boldOff)
            write("\n".toByteArray(charset))

            // --- ORDER INFO ---
            write(leftAlign)
            write("Order No: ${order.orderNo}\n".toByteArray(charset))
            write("Date: ${sdfDate.format(Date())}\n".toByteArray(charset))
            write("Time: ${sdfTime.format(Date())}\n".toByteArray(charset))

            // 🟢 Delivery address printed right below time
            if (order.orderType.equals("delivery", ignoreCase = true)) {
                order.customerAddress?.takeIf { it.isNotBlank() && it.lowercase() != "null" }?.let {
                    write("Delivery Address: ${it.uppercase()}\n".toByteArray(charset))
                }
            }

            write("${"-".repeat(lineWidth)}\n".toByteArray(charset))

            // --- TABLE HEADER ---
            write(boldOn)
            val headerFormat = "%-${colItemWidth}s %-${colQtyWidth}s %${colPriceWidth}s\n"
            write(headerFormat.format("ITEM", "QTY", "PRICE").toByteArray(charset))
            write("${"-".repeat(lineWidth)}\n".toByteArray(charset))
            write(boldOff)

            // --- ITEMS ---
            order.items.forEach { item: OrderItem ->
                val name = "${item.itemName.uppercase()} (${item.size.uppercase()})".take(colItemWidth)
                val qty = "x${item.quantity}".take(colQtyWidth)
                val price = String.format("%.2f", item.price * item.quantity)

                val lineFormat = "%-${colItemWidth}s %-${colQtyWidth}s %${colPriceWidth}s\n"
                val line = lineFormat.format(name, qty, price)
                write(line.toByteArray(charset))
            }

            // --- TOTAL SECTION ---
            write("${"-".repeat(lineWidth)}\n".toByteArray(charset))
            write(boldOn)

            val totalQty = order.items.sumOf { it.quantity }
            val totalAmount = order.items.sumOf { it.price * it.quantity }

            // 🟢 Total line
            val totalLine = String.format(
                "%-${colItemWidth}s %-${colQtyWidth}s %${colPriceWidth}.2f\n",
                "TOTAL:",
                "x$totalQty",
                totalAmount
            )
            write(totalLine.toByteArray(charset))

            // 🟢 Delivery Charges below total
            if (order.deliveryCharges > 0) {
                val deliveryChargesLine = String.format(
                    "%-${colItemWidth}s %-${colQtyWidth}s %${colPriceWidth}.2f\n",
                    "DELIVERY CHARGES:",
                    "",
                    order.deliveryCharges.toDouble()
                )
                write(deliveryChargesLine.toByteArray(charset))
            }

            // 🟢 Grand total (if delivery charges exist)
            if (order.deliveryCharges > 0) {
                val grandTotal = totalAmount + order.deliveryCharges
                val grandTotalLine = String.format(
                    "%-${colItemWidth}s %-${colQtyWidth}s %${colPriceWidth}.2f\n",
                    "GRAND TOTAL:",
                    "",
                    grandTotal
                )
                write(grandTotalLine.toByteArray(charset))
            }

            write(boldOff)
            write("${"=".repeat(lineWidth)}\n".toByteArray(charset))

            // --- FOOTER ---
            write(centerAlign)
            write(boldOn)
            write("THANK YOU!\n\n\n".toByteArray(charset))
            write(boldOff)
            write(cutPaper)

        }.toByteArray()
    }

    fun printReceipt(printerName: String, order: Order, isOldOrder: Boolean) {
        var escpos: EscPos? = null
        var outputStream: PrinterOutputStream? = null

        try {
            val printServices = PrinterOutputStream.getListPrintServicesNames()
            if (!printServices.contains(printerName)) {
                throw IllegalArgumentException("Printer '$printerName' not found. Available printers: ${printServices.joinToString()}")
            }

            val printService = PrinterOutputStream.getPrintServiceByName(printerName)
            outputStream = PrinterOutputStream(printService)
            escpos = EscPos(outputStream)

            // --- HEADER RECEIPT INFO ---
            outputStream.write(byteArrayOf(0x1B, 0x40)) // initialize
            outputStream.write(byteArrayOf(0x1B, 0x21, 0x30)) // double size
            outputStream.write(byteArrayOf(0x1B, 0x45, 0x01)) // bold
            outputStream.write("Receipt ID: ${order.orderNo}\n".toByteArray(charset))

            if (isOldOrder) {
                outputStream.write("OLD ORDER\n".toByteArray(charset))
            }

            outputStream.write(byteArrayOf(0x1B, 0x45, 0x00)) // bold off
            outputStream.write(byteArrayOf(0x1B, 0x21, 0x00)) // normal size
            outputStream.write("\n".toByteArray(charset))

            // --- LOGO ---
            val imageStream = javaClass.getResourceAsStream("/com/innovatewithomer/juiceforu/logo/logo.jpg").use { stream ->
                val originalImage = ImageIO.read(stream)
                val targetWidth = 300
                val targetHeight = (originalImage.height * targetWidth) / originalImage.width

                val resizedImage = BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB).apply {
                    createGraphics().run {
                        drawImage(originalImage, 0, 0, targetWidth, targetHeight, null)
                        dispose()
                    }
                }

                EscPosImage(
                    CoffeeImageImpl(resizedImage),
                    BitonalThreshold()
                )
            }

            escpos.write(BitImageWrapper().setJustification(EscPosConst.Justification.Center), imageStream)
            escpos.feed(2)

            // --- MAIN RECEIPT ---
            outputStream.write(buildReceiptContent(order))
            outputStream.flush()

            escpos.cut(EscPos.CutMode.FULL)

        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            escpos?.close()
            outputStream?.close()
        }
    }

    private fun adjustForThermalPrint(image: BufferedImage): BufferedImage {
        val width = image.width
        val height = image.height
        val adjusted = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)

        for (x in 0 until width) {
            for (y in 0 until height) {
                val color = Color(image.getRGB(x, y))
                val r = (color.red * 1.4).coerceAtMost(255.0)
                val g = (color.green * 1.3).coerceAtMost(255.0)
                val b = (color.blue * 1.3).coerceAtMost(255.0)
                adjusted.setRGB(x, y, Color(r.toInt(), g.toInt(), b.toInt()).rgb)
            }
        }

        return adjusted
    }
}
