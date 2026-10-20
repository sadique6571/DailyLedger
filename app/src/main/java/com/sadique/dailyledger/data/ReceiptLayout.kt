package com.sadique.dailyledger.data

/** OCR may return item names and prices as separate blocks; rebuild rows from their vertical positions. */
data class ReceiptTextLine(val text:String,val left:Int,val top:Int,val bottom:Int)
fun receiptTextRows(lines:List<ReceiptTextLine>):String {
    val groups=mutableListOf<MutableList<ReceiptTextLine>>()
    lines.filter{it.text.isNotBlank()}.sortedBy{(it.top+it.bottom)/2}.forEach { line ->
        val group=groups.lastOrNull()
        val center=(line.top+line.bottom)/2.0
        val prior=group?.map{(it.top+it.bottom)/2.0}?.average()
        val height=maxOf(line.bottom-line.top,group?.maxOfOrNull{it.bottom-it.top} ?: 0)
        if(group!=null && prior!=null && kotlin.math.abs(center-prior)<=maxOf(4.0,height*.55))group.add(line) else groups.add(mutableListOf(line))
    }
    return groups.joinToString("\n"){row->row.sortedBy{it.left}.joinToString(" "){it.text.trim()}}
}
fun recognizedReceiptText(result:com.google.mlkit.vision.text.Text):String {
    val lines=result.textBlocks.flatMap{it.lines}.mapNotNull{line->line.boundingBox?.let{ReceiptTextLine(line.text,it.left,it.top,it.bottom)}}
    return if(lines.isEmpty())result.text else receiptTextRows(lines)
}
