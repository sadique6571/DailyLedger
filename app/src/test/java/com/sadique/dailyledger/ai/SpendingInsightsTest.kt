package com.sadique.dailyledger.ai
import com.sadique.dailyledger.data.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
class SpendingInsightsTest {
 @Test fun spendingPaceRespectsAnEarlyOrLateManuallyPlannedSalary() {
  val rows = listOf(tx("s", "2026-10-01", 1000000, "Salary", type="INCOME"), tx("e", "2026-10-02", 300000, "Pizza"))
  val today = LocalDate.parse("2026-10-10")
  val early = SpendingInsights.calculate("me", rows, today, plannedSalaryDate=LocalDate.parse("2026-10-15"), salaryRecordedWhenPlanned=LocalDate.parse("2026-10-01"))
  val late = SpendingInsights.calculate("me", rows, today, plannedSalaryDate=LocalDate.parse("2026-11-20"), salaryRecordedWhenPlanned=LocalDate.parse("2026-10-01"))
  assertFalse(early.tips.any { it.title == "Spending pace before salary" })
  assertTrue(late.tips.any { it.title == "Spending pace before salary" })
 }

 @Test fun plannedBillsAndCategoryLimitsPrioritizeRealWarningsWithoutCreatingEntries() {
  val rows = listOf(tx("s", "2026-10-01", 100000, "Salary", type="INCOME"), tx("p", "2026-10-03", 90000, "Pizza"))
  val planning = com.sadique.dailyledger.planning.PlannerData(listOf(com.sadique.dailyledger.planning.CategoryBudget("Pizza", 100000)),
   listOf(com.sadique.dailyledger.planning.RecurringBill("rent", "Rent", "EXPENSE", "Rent", 50000, 7, "2026-10")))
  val s = SpendingInsights.calculate("me", rows, LocalDate.parse("2026-10-06"), planning=planning)
  assertEquals("Near budget limit", s.tips.first().title)
  assertTrue(s.tips.any { it.title == "Bills to reserve for" && it.detail.contains("exceeds") })
  assertTrue(s.tips.any { it.title == "Spending pace before salary" })
  assertEquals(10000L, s.summary.remaining)
  assertEquals(2, rows.size)
 }

 private fun tx(id:String,date:String,amount:Long,cat:String="Fuel",owner:String="me",type:String="EXPENSE")=TransactionEntity(id,owner,type,amount,cat,"private",date,1,1)
 @Test fun otherIncomeHasItsOwnTotalAndAddsToAvailableBalance(){val s=SpendingInsights.calculate("me",listOf(tx("a","2026-10-01",6000000,"Salary",type="INCOME"),tx("b","2026-10-02",200000),tx("c","2026-10-03",100000,"Bonus",type="INCOME")),LocalDate.parse("2026-10-06"));assertEquals(5900000L,s.summary.remaining);assertEquals(100000L,s.summary.otherIncome)}
 @Test fun carriedBalanceIsIncludedInDashboardAndAiSummary() {
  val today = LocalDate.now()
  val cycle = LedgerCycleEntity("me", 1, 25000)
  val salary = tx("salary", today.toString(), 100000, "Salary", type = "INCOME")
  val snapshot = SpendingInsights.calculate("me", listOf(salary), today, ledgerCycle = cycle)
  assertEquals(125000L, snapshot.summary.remaining)
 }
 @Test fun monthlyBalanceAndAiContextDeductKametiAndSavingsOnce(){
  val today=LocalDate.parse("2026-10-06")
  val payment=CommitteePaymentEntity("cp","me","committee",1,"2026-10",150000,1)
  val saving=SavingEntity("saving","me","DIRECT",50000,"2026-10-05","",1)
  val snapshot=SpendingInsights.calculate("me",listOf(tx("salary","2026-10-01",1000000,"Salary",type="INCOME"),tx("expense","2026-10-02",200000)),today,savings=listOf(saving),committeePayments=listOf(payment))
  assertEquals(150000L,snapshot.summary.committeeContributions)
  assertEquals(50000L,snapshot.summary.savingsContributions)
  assertEquals(600000L,snapshot.summary.remaining)
 }
 @Test fun sameDaysInShortMonths(){val s=SpendingInsights.calculate("me",listOf(tx("a","2026-02-28",10000),tx("b","2026-03-28",15000),tx("c","2026-03-31",20000)),LocalDate.parse("2026-03-31"));assertEquals(28,s.comparisonDays);assertEquals(15000L,s.currentComparable);assertEquals(10000L,s.previousComparable);assertEquals(35000L,s.summary.expenses)}
 @Test fun noOtherAccountsOrFutureRows(){val s=SpendingInsights.calculate("me",listOf(tx("a","2026-10-01",10000),tx("b","2026-10-01",99999,owner="other"),tx("c","2026-10-07",99999)),LocalDate.parse("2026-10-06"));assertEquals(10000L,s.summary.expenses);assertEquals(1,s.count)}
 @Test fun onlyOptionalSpendingReduced(){val s=SpendingInsights.calculate("me",listOf(tx("a","2026-10-01",100000,"Restaurant"),tx("b","2026-10-01",500000,"Medicines")),LocalDate.parse("2026-10-06"));assertTrue(s.tips.single{it.title=="Bachat ka ek option"}.detail.contains("Rs 200"))}
 @Test fun noFakeComparisonWithoutHistory(){val s=SpendingInsights.calculate("me",listOf(tx("a","2026-10-01",10000)),LocalDate.parse("2026-10-06"));assertTrue(s.tips.any{it.title=="Comparison ke liye data chahiye"});assertFalse(s.tips.any{it.title=="Kharchay mein izafa"})}
 @Test fun essentialFoodAndFutureSavingsAreNotOptionalSpend() {
  val today=LocalDate.parse("2026-10-06")
  val s=SpendingInsights.calculate("me",listOf(tx("salary","2026-10-01",100000,"Salary",type="INCOME"),tx("pet","2026-10-01",10000,"Pet food")),today,savings=listOf(SavingEntity("future","me","DIRECT",50000,"2026-10-08","",1),SavingEntity("now","me","DIRECT",10000,"2026-10-01","",1)))
  assertEquals(0L,s.potentialSaving);assertEquals(10000L,s.savedThisMonth);assertEquals(10,s.savingRatePercent)
 }
}
