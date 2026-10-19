package com.sadique.dailyledger.data

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
class FinanceMathTest {
    private fun tx(id:String,amount:Long,date:String="2026-10-01",refund:String="",q:String="",rate:Long=0)=TransactionEntity(id,"o","EXPENSE",amount,"Milk","Milk",date,1,1,quantity=q,unit="litre",unitPriceMinor=rate,refundOf=refund)
    @Test fun quantityRoundsPaisaOnceAndRejectsInvalidOrOverflowingValues(){
        assertEquals(15038L,quantityAmount("1.5",10025));assertEquals(50L,quantityAmount("0.5",100))
        listOf("0","-1","garbage","10000000000000000").forEach{assertTrue(runCatching{quantityAmount(it,10000)}.isFailure)}
    }
    @Test fun transfersConserveMoneyAndNeverBecomeExpenses(){
        val w=FinanceWorkspace(items=listOf(FinanceItem(kind=FinanceKind.ACCOUNT,title="Cash",amount=100000,date="2026-10-01"),FinanceItem(kind=FinanceKind.TRANSFER,title="Move",amount=30000,date="2026-10-01",account="Cash",reference="Bank",note="Deposit")))
        assertEquals(70000L,FinanceMath.accountBalance("Cash",w,emptyList()));assertEquals(30000L,FinanceMath.accountBalance("Bank",w,emptyList()))
        assertEquals(100000L,w.accounts.sumOf{FinanceMath.accountBalance(it,w,emptyList())})
    }
    @Test fun refundsIncreaseCashAndReduceNetExpenseWithoutIncome(){
        val rows=listOf(tx("p",10000),tx("r",2500,refund="p"))
        assertEquals(7500L,monthlySalarySummary(rows,"2026-10").expenses);assertEquals(-7500L,monthlySalarySummary(rows,"2026-10").remaining)
        assertEquals(-7500L,FinanceMath.accountBalance("Cash",FinanceWorkspace(),rows))
    }
    @Test fun spendingForecastIncludesNoPurchaseDaysAndShowsShortfall(){
        val f=FinanceMath.forecast(10000,listOf(tx("p",50000)),LocalDate.parse("2026-10-01"),LocalDate.parse("2026-10-20"),LocalDate.parse("2026-10-10"))
        assertEquals(5000L,f.daily);assertEquals(10L,f.days);assertEquals(40000L,f.shortfall);assertEquals(2L,f.daysCovered)
    }
    @Test fun rateAndQuantityEffectsReconcileToActualChange(){
        val old=tx("a",20000,q="2",rate=10000);val next=tx("b",36000,date="2026-10-02",q="3",rate=12000)
        val c=priceComparisons(listOf(old,next)).single();assertEquals(16000L,c.totalChange);assertEquals(10000L,c.quantityEffect);assertEquals(6000L,c.rateEffect)
        assertEquals(c.totalChange,c.quantityEffect+c.rateEffect)
    }
    @Test fun monthlyAttributionUsesSameElapsedDaysAndIgnoresPrivateItems(){
        val rows=listOf(tx("old",20000,"2026-09-02",q="2",rate=10000),tx("now",36000,"2026-10-02",q="3",rate=12000),tx("future",90000,"2026-10-25",q="3",rate=30000))
        val c=monthlyRateComparisons(rows,LocalDate.parse("2026-10-19")).single()
        assertEquals(16000L,c.totalChange);assertEquals(10000L,c.quantityEffect);assertEquals(6000L,c.rateEffect)
    }
    @Test fun privatePurchasesNeverEnterPriceComparisons(){
        assertTrue(priceComparisons(listOf(tx("a",10000,q="1",rate=10000),tx("b",12000,date="2026-10-02",q="1",rate=12000).copy(privateEntry=true))).isEmpty())
    }
    @Test fun goalUsesRemainingSalariesAndCeilingRounding(){
        val first=LocalDate.now().plusMonths(1).withDayOfMonth(5)
        assertEquals(3334L,FinanceMath.goalPerSalary(10000,0,first.plusMonths(2),first))
    }
    @Test fun extremeValidAmountsDoNotCrashForecastOrRateReports(){
        val f=FinanceMath.forecast(0,(1..10).map{tx("p$it",1_000_000_000_000)},LocalDate.parse("2026-10-01"),LocalDate.parse("9999-10-01"),LocalDate.parse("2026-10-01"))
        assertEquals(Long.MAX_VALUE,f.needed)
        val rows=listOf(tx("old",1_000_000_000_000,q="1",rate=1_000_000_000_000),tx("new",1_000_000_000_000,"2026-10-02",q="1000000000000",rate=1))
        assertTrue(priceComparisons(rows).isEmpty())
    }

}
