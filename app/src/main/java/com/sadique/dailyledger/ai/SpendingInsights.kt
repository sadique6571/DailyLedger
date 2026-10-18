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
        creditPayments: List<CreditPaymentEntity> = emptyList(),
        planning: com.sadique.dailyledger.planning.PlannerData = com.sadique.dailyledger.planning.PlannerData(),
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
        val currentCreditPayments = creditPayments.filter { payment ->
            payment.ownerId == owner && runCatching { LocalDate.parse(payment.date) <= today }.getOrDefault(false)
        }
        val summary = ledgerCycle?.let {
            ledgerCycleSummary(current, currentCommitteePayments, currentSavings, it, creditPayments = currentCreditPayments)
        } ?: monthlySalarySummary(current, month.toString(), currentCommitteePayments, currentSavings, currentCreditPayments)
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
        val loanPaidById = loanPayments.filter { it.ownerId == owner }.groupBy { it.loanId }.mapValues { (_, rows) -> rows.sumOf { it.amountMinor } }
        val borrowedOutstanding = loans.filter { it.ownerId == owner && it.direction == "BORROWED" }.sumOf { loan ->
            (loan.principalMinor - (loanPaidById[loan.id] ?: 0L)).coerceAtLeast(0L)
        }
        val lentOutstanding = loans.filter { it.ownerId == owner && it.direction == "LENT" }.sumOf { loan ->
            (loan.principalMinor - (loanPaidById[loan.id] ?: 0L)).coerceAtLeast(0L)
        }

        val flexibleCategories = setOf("restaurant", "takeaway", "tea & coffee", "snacks", "subscriptions", "cinema & outings", "games & hobbies", "pizza", "burger", "biryani", "shawarma", "barbecue & kebab", "ice cream", "cake & sweets", "fries", "juice & shakes")
        val optionalSpend = categories.filter { it.first.lowercase(Locale.ROOT) in flexibleCategories }.sumOf { it.second }
        val potentialSaving = optionalSpend / 5 // conservative 20% reduction scenario

        var score = 50
        if (summary.salary > 0L) {
            val available = summary.salary + summary.otherIncome + summary.openingBalanceMinor
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
        val budgetRows = com.sadique.dailyledger.planning.PlannerMath.budgets(owner, planning.budgets, own, month)
        budgetRows.filter { it.nearLimit }.sortedByDescending { it.spent.toDouble() / it.limit }.take(2).forEach {
            tips += SpendingTip(if (it.exceeded) "Budget limit reached" else "Near budget limit",
                "${it.category}: ${rs(it.spent)} of ${rs(it.limit)} recorded. ${rs((it.limit - it.spent).coerceAtLeast(0))} remains in this monthly category budget.")
        }
        val soon = planning.bills.filter { it.enabled && it.type == "EXPENSE" && it.nextDue() <= today.plusDays(7) }
        if (soon.isNotEmpty()) {
            val dueTotal = soon.sumOf { it.amountMinor }
            tips += SpendingTip("Bills to reserve for", "${soon.size} reminders due by ${today.plusDays(7)}: ${rs(dueTotal)} planned. " +
                if (dueTotal > summary.remaining.coerceAtLeast(0)) "This exceeds your remaining balance by ${rs(dueTotal - summary.remaining.coerceAtLeast(0))}. Review due dates and essentials first." else "Keep this amount aside before optional spending. Reminders never deduct automatically.")
        }
        val guide = DailySpendingGuide.calculate(own, summary, today)
        if (guide.actualDailyExpenseMinor != null && guide.dailyAllowanceMinor != null && guide.elapsedSalaryDays!! >= 3 && guide.actualDailyExpenseMinor > guide.dailyAllowanceMinor) {
            tips += SpendingTip("Spending pace before salary", "Since salary, actual expenses average ${rs(guide.actualDailyExpenseMinor)} per day; remaining allowance is ${rs(guide.dailyAllowanceMinor)} per day. Review optional categories before your next salary.")
        }
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

            // Compare each category over the same elapsed calendar days, not a full previous month.
            fun comparableCategories(m: YearMonth) = own.filter {
                it.type == "EXPENSE" && it.date.startsWith("$m-") && LocalDate.parse(it.date).dayOfMonth <= days
            }.groupBy { it.category }.mapValues { (_, rows) -> rows.sumOf { it.amountMinor } }
            val previousByCategory = comparableCategories(previous)
            val rise = comparableCategories(month).mapNotNull { (name, amount) ->
                previousByCategory[name]?.takeIf { it > 0 && amount > it }?.let { Triple(name, amount - it, it) }
            }.maxByOrNull { it.second }
            rise?.let { (name, increase, baseline) ->
                tips += SpendingTip("Category mein izafa", "$name: dono mahino ke pehle $days dinon mein ${rs(increase)} (${(increase.toDouble() / baseline * 100).roundToInt()}%) zyada. Quantity, prices aur repeated purchases ko history mein compare karein.")
            }

            if (potentialSaving > 0L) {
                tips += SpendingTip("Bachat ka ek option", "Recorded optional categories ko 20% kam karne ka example: ${rs(potentialSaving)}. Yeh mumkin bachat ka scenario hai; zaroori food aur medical costs is mein shamil nahi.")
            }

            if (summary.remaining < 0) {
                tips += SpendingTip("Available balance se zyada outflow", "Expenses, kameti aur savings mila kar available income aur carried balance se ${rs(-summary.remaining)} zyada hain. Pehle zaroori bills alag karein, phir optional kharchay kam karein.")
            } else if (summary.salary + summary.otherIncome + summary.openingBalanceMinor > 0L &&
                BigDecimal.valueOf(summary.totalOutflow).multiply(BigDecimal(100)) >= BigDecimal.valueOf(summary.salary + summary.otherIncome + summary.openingBalanceMinor).multiply(BigDecimal(85))) {
                tips += SpendingTip("Budget pressure", "Available income aur carried balance ka 85% ya zyada use ho chuka hai. Agle optional kharchay save karne se pehle review karein.")
            }

        }

        if (summary.salary > 0L && summary.remaining > 0L) {
            val tenPercent = summary.salary / 10
            when {
                savedThisMonth == 0L -> tips += SpendingTip("Bachat start karein", "Is mahine saving entry nahi hai. Agar mumkin ho to salary ka 10% (${rs(tenPercent)}) target rakh sakte hain.")
                savedThisMonth < tenPercent -> tips += SpendingTip("Saving target", "Abhi ${rs(savedThisMonth)} save hua hai. Agar zaroori bills cover hain, 10% ka optional target ${rs(tenPercent)} hai.")
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
            tips = tips.take(10),
            savedThisMonth = savedThisMonth,
            potentialSaving = potentialSaving,
            savingRatePercent = savingRate,
            healthScore = score,
        )
    }
}
