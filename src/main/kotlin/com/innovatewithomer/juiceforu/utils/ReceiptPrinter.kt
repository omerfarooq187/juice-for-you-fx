package com.innovatewithomer.juiceforu.utils

import com.github.anastaciocintra.escpos.EscPos
import com.github.anastaciocintra.escpos.EscPosConst
import com.github.anastaciocintra.escpos.image.CoffeeImageImpl
import com.github.anastaciocintra.escpos.image.EscPosImage
import com.github.anastaciocintra.escpos.image.BitImageWrapper
import com.github.anastaciocintra.escpos.image.BitonalOrderedDither
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
import javax.print.PrintService

class ReceiptPrinter {

    private val lineWidth = 48
    private val charset = Charset.forName("CP437")

    fun buildReceiptContent(order: Order, isOldOrder: Boolean = false): ByteArray {
        val sdfDateTime = SimpleDateFormat("dd/MM/yyyy hh:mm a", Locale.getDefault())

        val initialize = byteArrayOf(0x1B, 0x40)
        val boldOn = byteArrayOf(0x1B, 0x45, 0x01)
        val boldOff = byteArrayOf(0x1B, 0x45, 0x00)
        val doubleHeightOn = byteArrayOf(0x1B, 0x21, 0x10)
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
            write(doubleHeightOn)
            write(boldOn)
            write("JUICE FOR U\n".toByteArray(charset))
            write(normalText)
            write(boldOff)

            if (isOldOrder) {
                write(boldOn)
                write("** DUPLICATE / EDITED ORDER **\n".toByteArray(charset))
                write(boldOff)
            }
            write("\n".toByteArray(charset))

            // --- CONTACT INFO ---
            write(leftAlign)
            write("Juice For U Near Thandi Sarak\nG.T Road Mandra\n".toByteArray(charset))
            write("Tel: 051-3591155  WhatsApp: 0309-5107000\n".toByteArray(charset))
            write("${"=".repeat(lineWidth)}\n".toByteArray(charset))
            write("ORDER NO: #${order.orderNo}\n".toByteArray(charset))
            write("ORDER TIME: ${sdfDateTime.format(Date(if (order.createdAt > 0) order.createdAt else System.currentTimeMillis()))}\n".toByteArray(charset))
            write("ORDER TYPE: ${order.orderType.uppercase()}\n".toByteArray(charset))

            if (order.orderType.equals("delivery", ignoreCase = true)) {
                order.customerAddress?.takeIf { it.isNotBlank() && it.lowercase() != "null" }?.let {
                    write("DELIVERY ADDRESS: ${it.uppercase()}\n".toByteArray(charset))
                }
                order.customerPhone?.takeIf { it.isNotBlank() }?.let {
                    write("PHONE NUMBER: $it\n".toByteArray(charset))
                }
            }

            write("${"-".repeat(lineWidth)}\n".toByteArray(charset))

            // --- ITEMS TABLE HEADER ---
            write(boldOn)
            val headerFormat = "%-${colItemWidth}s %${colQtyWidth}s %${colPriceWidth}s\n"
            write(headerFormat.format("ITEM", "QTY", "PRICE").toByteArray(charset))
            write("${"-".repeat(lineWidth)}\n".toByteArray(charset))
            write(boldOff)

            // --- ITEMS ---
            var calculatedSubtotal = 0.0
            order.items.forEach { item: OrderItem ->
                val name = "${item.itemName.uppercase()} (${item.size.uppercase()})".take(colItemWidth)
                val qty = "x${item.quantity}".take(colQtyWidth)
                val price = item.price * item.quantity
                calculatedSubtotal += price

                val formattedPrice = "Rs.${"%,.2f".format(price)}"
                val lineFormat = "%-${colItemWidth}s %${colQtyWidth}s %${colPriceWidth}s\n"
                write(lineFormat.format(name, qty, formattedPrice).toByteArray(charset))
            }

            // --- TOTALS ---
            write("${"-".repeat(lineWidth)}\n".toByteArray(charset))
            write(boldOn)

            fun writeTotalLine(label: String, amount: String) {
                val line = "%-${colItemWidth}s %${colQtyWidth}s %${colPriceWidth}s\n"
                    .format(label, "", amount)
                write(line.toByteArray(charset))
            }

            writeTotalLine("SUBTOTAL:", "Rs.${"%,.2f".format(calculatedSubtotal)}")

            if (order.serviceCharges > 0) {
                writeTotalLine("SERVICE CHARGES:", "Rs.${"%,.2f".format(order.serviceCharges.toDouble())}")
            }
            if (order.deliveryCharges > 0) {
                writeTotalLine("DELIVERY CHARGES:", "Rs.${"%,.2f".format(order.deliveryCharges.toDouble())}")
            }
            if (order.discountPercent > 0) {
                writeTotalLine("DISCOUNT (${order.discountPercent.toInt()}%):", "-Rs.${"%,.2f".format(order.discountAmount)}")
            }

            writeTotalLine("TOTAL AMOUNT:", "Rs.${"%,d".format(order.total)}")

            write(boldOff)
            write("${"=".repeat(lineWidth)}\n".toByteArray(charset))

            // --- FOOTER ---
            write(centerAlign)
            write(boldOn)
            write("THANKS FOR CHOOSING JUICE FOR U\n\n".toByteArray(charset))
            write("YOUR SATISFACTION IS OUR PLEASURE\n".toByteArray(charset))
            write("PLEASE VISIT US AGAIN!\n\n".toByteArray(charset))
            write(boldOff)
            write(cutPaper)

        }.toByteArray()
    }

    fun printReceipt(printerName: String, order: Order, isOldOrder: Boolean = false) {
        var escpos: EscPos? = null
        var outputStream: PrinterOutputStream? = null

        try {
            val printService: PrintService = PrinterOutputStream.getPrintServiceByName(printerName)
                ?: throw IllegalStateException("Printer service '$printerName' not found.")

            outputStream = PrinterOutputStream(printService)
            escpos = EscPos(outputStream)

            // --- OPTIONAL LOGO ---
            try {
                val imageStream = javaClass.getResourceAsStream("/com/innovatewithomer/juiceforu/logo/logo.jpg")
                if (imageStream != null) {
                    imageStream.use { stream ->
                        val originalImage = ImageIO.read(stream)
                        if (originalImage != null) {
                            val targetWidth = 300
                            val targetHeight = (originalImage.height * targetWidth) / originalImage.width
                            val resizedImage = BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB).apply {
                                createGraphics().run {
                                    drawImage(originalImage, 0, 0, targetWidth, targetHeight, null)
                                    dispose()
                                }
                            }
                            val escPosImage = EscPosImage(
                                CoffeeImageImpl(adjustForThermalPrint(resizedImage)),
                                BitonalOrderedDither()
                            )
                            escpos.write(BitImageWrapper().setJustification(EscPosConst.Justification.Center), escPosImage)
                            escpos.feed(1)
                        }
                    }
                }
            } catch (imgEx: Exception) {
                Logger.logError(imgEx, "Receipt logo print skipped")
            }

            // --- MAIN RECEIPT ---
            val receiptBytes = buildReceiptContent(order, isOldOrder)
            outputStream.write(receiptBytes)
            outputStream.flush()
            escpos.cut(EscPos.CutMode.FULL)

        } catch (e: Exception) {
            Logger.logError(e, "Printing failed for order #${order.orderNo}")
            throw e
        } finally {
            try {
                escpos?.close()
                outputStream?.close()
            } catch (_: Exception) {}
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

