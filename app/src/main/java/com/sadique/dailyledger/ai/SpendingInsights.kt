package com.sadique.dailyledger.ai

import com.sadique.dailyledger.data.*
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale
import kotlin.math.roundToInt

data class SpendingTip(val title: String, val detail: String)

data class SpendingSnapshot(
    val month: String,
    val through: String,
    val summary: SalarySummary,
    val count: Int,
    val categories: List<Pair<String, Long>>,
    val comparisonDays: Int,
    val previousComparable: Long,
    val currentComparable: Long,
    val tips: List<SpendingTip>,
    val savedThisMonth: Long = 0L,
    val potentialSaving: Long = 0L,
    val savingRatePercent: Int = 0,
    val healthScore: Int = 0,
)

/** Fast, fully-offline finance analysis. Exact math stays outside any language model. */
object SpendingInsights {
    private fun rs(v: Long) = "Rs " + BigDecimal.valueOf(v, 2).stripTrailingZeros().toPlainString()

    fun calculate(
        owner: String,
        transactions: List<TransactionEntity>,
        today: LocalDate = LocalDate.now(),
        savings: List<SavingEntity> = emptyList(),
        loans: List<LoanEntity> = emptyList(),
        loanPayments: List<LoanPaymentEntity> = emptyList(),
        committeePayments: List<CommitteePaymentEntity> = emptyList(),
        ledgerCycle: LedgerCycleEntity? = null,
    ): SpendingSnapshot {
        val month = YearMonth.from(today)
        val previous = month.minusMonths(1)
        val days = minOf(today.dayOfMonth, previous.lengthOfMonth())
        val own = transactions.filter { row ->
            row.ownerId == owner && runCatching { LocalDate.parse(row.date) <= today }.getOrDefault(false)
        }
        val current = own.filter { row ->
            ledgerCycle?.let { row.createdAt >= it.startAt } ?: row.date.startsWith("$month-")
        }
        val currentSavings = savings.filter { item ->
            item.ownerId == owner && (ledgerCycle?.let { item.createdAt >= it.startAt } ?: item.date.startsWith("$month-")) &&
                runCatching { LocalDate.parse(item.date) <= today }.getOrDefault(false)
        }
        val currentCommitteePayments = committeePayments.filter { payment ->
            payment.ownerId == owner && (ledgerCycle?.let { payment.paidAt >= it.startAt } ?: payment.month == month.toString())
        }
        val summary = ledgerCycle?.let {
            ledgerCycleSummary(current, currentCommitteePayments, currentSavings, it)
        } ?: monthlySalarySummary(current, month.toString(), currentCommitteePayments, currentSavings)
        val categories = current
            .filter { it.type == "EXPENSE" }
            .groupBy { it.category.trim().ifBlank { "Other" } }
            .map { (name, rows) -> name to rows.sumOf { it.amountMinor } }
            .sortedByDescending { it.second }

        fun comparable(m: YearMonth) = own
            .filter { it.type == "EXPENSE" && it.date.startsWith("$m-") && LocalDate.parse(it.date).dayOfMonth <= days }
            .sumOf { it.amountMinor }

        val now = comparable(month)
        val old = comparable(previous)
        val savedThisMonth = currentSavings.sumOf { it.amountMinor }
        val savingRate = if (summary.salary > 0L) ((savedThisMonth.toDouble() / summary.salary) * 100.0).roundToInt().coerceIn(0, 100) else 0
        val borrowedOutstanding = loans.filter { it.ownerId == owner && it.direction == "BORROWED" }.sumOf { loan ->
            (loan.principalMinor - loanPayments.filter { it.ownerId == owner && it.loanId == loan.id }.sumOf { it.amountMinor }).coerceAtLeast(0L)
        }
        val lentOutstanding = loans.filter { it.ownerId == owner && it.direction == "LENT" }.sumOf { loan ->
            (loan.principalMinor - loanPayments.filter { it.ownerId == owner && it.loanId == loan.id }.sumOf { it.amountMinor }).coerceAtLeast(0L)
        }

        val flexibleCategories = setOf("restaurant", "takeaway", "tea & coffee", "snacks", "subscriptions", "cinema & outings", "games & hobbies")
        val optionalSpend = categories.filter { it.first.lowercase(Locale.ROOT) in flexibleCategories }.sumOf { it.second }
        val potentialSaving = optionalSpend / 5 // conservative 20% reduction scenario

        var score = 50
        if (summary.salary > 0L) {
            val available = summary.salary + summary.openingBalanceMinor
            val expenseRate = if (available > 0L) summary.totalOutflow.toDouble() / available.toDouble() else 1.0
            score += when {
                expenseRate <= 0.60 -> 20
                expenseRate <= 0.80 -> 10
                expenseRate <= 1.00 -> 0
                else -> -20
            }
            score += when {
                savingRate >= 20 -> 20
                savingRate >= 10 -> 10
                savingRate > 0 -> 5
                else -> 0
            }
        }
        if (old > 0L) score += if (now <= old) 10 else -10
        score = score.coerceIn(0, 100)

        val tips = mutableListOf<SpendingTip>()
        if (categories.isEmpty()) {
            tips += SpendingTip("Start your monthly picture", "Kharchay record karein; Mini AI unhi records se offline bachat ke mashwaray banayega.")
        } else {
            val biggest = categories.first()
            val biggestShare = if (summary.expenses > 0) ((biggest.second * 100.0) / summary.expenses).roundToInt() else 0
            tips += SpendingTip("Sab se zyada kharcha", "${biggest.first}: ${rs(biggest.second)} ($biggestShare%). Is category ki entries dekh kar limit set karein.")

            if (old > 0 && now > old) {
                tips += SpendingTip("Kharchay mein izafa", "Dono mahino ke pehle $days din: ${rs(now - old)} (${String.format(Locale.US, "%.0f", (now - old).toDouble() / old * 100)}%) zyada kharcha.")
            } else if (old > 0 && now < old) {
                tips += SpendingTip("Kharcha kam hua", "Pichle mahine ke pehle $days dinon ke muqablay mein ${rs(old - now)} kam kharcha record hua.")
            } else if (old == 0L) {
                tips += SpendingTip("Comparison ke liye data chahiye", "Pichle mahine ke pehle $days dinon ki expense entries nahi hain; abhi izafay ka faisla nahi kar sakte.")
            }

            if (potentialSaving > 0L) {
                tips += SpendingTip("Bachat ka ek option", "Optional food/shopping/entertainment ko 20% kam karein to takreeban ${rs(potentialSaving)} bach sakte hain.")
            }

            if (summary.salary > 0L && summary.remaining < 0) {
                tips += SpendingTip("Salary se zyada monthly outflow", "Expenses, kameti aur savings mila kar salary se ${rs(-summary.remaining)} zyada hain. Pehle zaroori bills alag karein, phir optional kharchay kam karein.")
            } else if (summary.salary + summary.openingBalanceMinor > 0L &&
                BigDecimal.valueOf(summary.totalOutflow).multiply(BigDecimal(100)) >= BigDecimal.valueOf(summary.salary + summary.openingBalanceMinor).multiply(BigDecimal(85))) {
                tips += SpendingTip("Budget pressure", "Salary ka 85% ya zyada kharch ho chuka hai. Agle optional kharchay save karne se pehle review karein.")
            }

        }

        if (summary.salary > 0L) {
            val tenPercent = summary.salary / 10
            when {
                savedThisMonth == 0L -> tips += SpendingTip("Bachat start karein", "Is mahine saving entry nahi hai. Agar mumkin ho to salary ka 10% (${rs(tenPercent)}) target rakh sakte hain.")
                savedThisMonth < tenPercent -> tips += SpendingTip("Saving target", "Abhi ${rs(savedThisMonth)} save hua hai. 10% salary target tak ${rs(tenPercent - savedThisMonth)} aur chahiye.")
                else -> tips += SpendingTip("Saving progress", "Is mahine ${rs(savedThisMonth)} save hua hai (${savingRate}% of salary). Consistency maintain karein.")
            }
        }
        if (borrowedOutstanding > 0L) {
            tips += SpendingTip("Loan balance", "Aap par ${rs(borrowedOutstanding)} borrowed balance baqi hai. Due dates check karein; extra repayment sirf zaroori bills aur emergency needs cover hone ke baad consider karein.")
        }
        if (lentOutstanding > 0L) {
            tips += SpendingTip("Money to receive", "${rs(lentOutstanding)} loan amount dusron se receive hona baqi hai. Loans tab se WhatsApp/SMS reminder bhej sakte hain.")
        }

        return SpendingSnapshot(
            month = month.toString(),
            through = today.toString(),
            summary = summary,
            count = current.size,
            categories = categories,
            comparisonDays = days,
            previousComparable = old,
            currentComparable = now,
            tips = tips.take(8),
            savedThisMonth = savedThisMonth,
            potentialSaving = potentialSaving,
            savingRatePercent = savingRate,
            healthScore = score,
        )
    }
}
