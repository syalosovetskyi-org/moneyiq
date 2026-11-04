package org.syalosovetskyi.onemoney.ui.components.calculator

import androidx.compose.runtime.*
import java.text.NumberFormat
import java.util.Locale

@Stable
class CalcStateHolder(initial: Double = 0.0) {

    var currentStr by mutableStateOf(
        when {
            !initial.isFinite() || initial <= 0.0                         -> "0"
            initial == initial.toLong().toDouble() -> initial.toLong().toString()
            else -> initial.toBigDecimal().stripTrailingZeros().toPlainString().replace(".", ",")
        }
    )
    var pendingVal by mutableStateOf(0.0)
    var pendingOp  by mutableStateOf<String?>(null)

    fun result(): Double {
        val c = currentStr.replace(",", ".").toDoubleOrNull() ?: 0.0
        val value = when (pendingOp) {
            "+"  -> pendingVal + c
            "−"  -> pendingVal - c
            "×"  -> pendingVal * c
            "÷"  -> if (c != 0.0) pendingVal / c else pendingVal
            else -> c
        }
        return if (value.isFinite()) value else {
            android.util.Log.w("CalcState", "Rejected non-finite calculator result")
            0.0
        }
    }

    fun displayExprNoSymbol(): String {
        val nf = NumberFormat.getNumberInstance(Locale.getDefault())
        nf.minimumFractionDigits = 0
        nf.maximumFractionDigits = 2
        return if (pendingOp != null)
            "${nf.format(pendingVal)} $pendingOp $currentStr"
        else
            currentStr
    }

    fun displayExpr(symbol: String = "₴"): String =
        "${displayExprNoSymbol()} $symbol"

    fun onKey(key: String) {
        when (key) {
            "÷", "×", "−", "+" -> { pendingVal = result(); pendingOp = key; currentStr = "0" }
            "=" -> if (pendingOp != null) {
                val r = result()
                currentStr = when {
                    r == r.toLong().toDouble() -> r.toLong().toString()
                    else -> r.toBigDecimal().stripTrailingZeros().toPlainString().replace(".", ",")
                }
                pendingOp  = null
                pendingVal = 0.0
            }
            "⌫" -> currentStr = if (currentStr.length <= 1) "0" else currentStr.dropLast(1)
            "C" -> { currentStr = "0"; pendingOp = null; pendingVal = 0.0 }
            "," -> {
                if ("," !in currentStr && "." !in currentStr)
                    currentStr = if (currentStr == "0") "0," else "$currentStr,"
            }
            else -> {
                if (key.length != 1 || key[0] !in '0'..'9') return
                val dotIdx = currentStr.indexOfFirst { it == ',' || it == '.' }
                currentStr = when {
                    dotIdx >= 0 && currentStr.length - dotIdx > 2 -> currentStr
                    currentStr == "0"      -> key
                    currentStr.length < 12 -> "$currentStr$key"
                    else                   -> currentStr
                }
            }
        }
    }
}

@Composable
fun rememberCalcState(initial: Double = 0.0): CalcStateHolder =
    androidx.compose.runtime.saveable.rememberSaveable(saver = androidx.compose.runtime.saveable.listSaver<CalcStateHolder, String>(
        save = { listOf(it.currentStr, it.pendingVal.toString(), it.pendingOp ?: "") },
        restore = { saved -> CalcStateHolder().apply {
            currentStr = saved[0]
            pendingVal = saved[1].toDoubleOrNull()?.takeIf { it.isFinite() } ?: 0.0
            pendingOp = saved[2].takeIf { it.isNotEmpty() }
        } }
    )) { CalcStateHolder(initial) }
