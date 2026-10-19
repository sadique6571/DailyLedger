package com.sadique.dailyledger.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.sadique.dailyledger.data.FinanceIntake
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class ReceiptOcrTest {
    @Test fun bundledLatinRecognizerReadsLocalReceiptBitmap() {
        val bitmap=Bitmap.createBitmap(1200,600,Bitmap.Config.ARGB_8888)
        val canvas=Canvas(bitmap);canvas.drawColor(Color.WHITE)
        val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=Color.BLACK;textSize=64f;typeface=Typeface.MONOSPACE}
        canvas.drawText("Milk        150.00",60f,120f,paint)
        canvas.drawText("Bread       100.00",60f,250f,paint)
        canvas.drawText("Grand Total 250.00",60f,380f,paint)
        val done=CountDownLatch(1);var text="";var failure:Exception?=null
        val client=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            client.process(InputImage.fromBitmap(bitmap,0)).addOnSuccessListener{text=it.text;done.countDown()}.addOnFailureListener{failure=it;done.countDown()}
            assertTrue("Bundled OCR timed out",done.await(45,TimeUnit.SECONDS))
            assertNull(failure)
            val rows=FinanceIntake.receipt(text,"2026-10-19")
            assertEquals("Recognized text: $text",2,rows.size)
            assertEquals(25000L,rows.sumOf{it.amountMinor})
        }finally{client.close()}
    }
}
