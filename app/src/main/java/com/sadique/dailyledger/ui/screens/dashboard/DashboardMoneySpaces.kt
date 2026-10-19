package com.sadique.dailyledger.ui.screens

import com.sadique.dailyledger.ui.LedgerSurface as Surface

import com.sadique.dailyledger.i18n.LocalizedIcon as Icon

import com.sadique.dailyledger.i18n.LocalizedText as Text

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sadique.dailyledger.data.CommitteeEntity
import com.sadique.dailyledger.data.CommitteeMemberEntity
import com.sadique.dailyledger.data.CommitteePaymentEntity
import com.sadique.dailyledger.data.CommitteeReceiptEntity
import com.sadique.dailyledger.data.LoanEntity
import com.sadique.dailyledger.data.LoanPaymentEntity
import com.sadique.dailyledger.data.SavingEntity
import com.sadique.dailyledger.data.committeeBalance
import com.sadique.dailyledger.ui.GlowIcon
import com.sadique.dailyledger.ui.LedgerCard
import com.sadique.dailyledger.ui.LedgerTokens
import com.sadique.dailyledger.ui.SummaryCard
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Savings / kameti / loans "money spaces" kept separate from the salary balance. */
@Composable
internal fun DashboardMoneySpaces(
    savings: List<SavingEntity>,
    cp: List<CommitteePaymentEntity>,
    receipts: List<CommitteeReceiptEntity>,
    committees: List<CommitteeEntity>,
    committeeMembers: List<CommitteeMemberEntity>,
    loans: List<LoanEntity>,
    lp: List<LoanPaymentEntity>,
    month: YearMonth,
    savingsFirst: Boolean,
    money: (Long) -> String,
    onOpenSavings: () -> Unit,
    onOpenKameti: () -> Unit,
    onOpenLoans: () -> Unit,
) {
    LedgerCard(Modifier.fillMaxWidth(), contentSpacing = LedgerTokens.Space16) {
        Text("Your money spaces", fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Text("Savings and kameti stay separate from salary.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        MoneySpacePair(
            first = { cardModifier ->
                if (savingsFirst) {
                    SummaryCard("Savings total", savings.sumOf { it.amountMinor },
                        Icons.Rounded.AccountBalanceWallet, cardModifier, "All saved amounts", onOpenSavings, compact = true)
                } else {
                    SummaryCard("Kameti paid", cp.sumOf { it.amountMinor },
                        Icons.Rounded.Groups, cardModifier,
                        "Received: ${money(receipts.sumOf { it.amountMinor })}\nPending: ${money(committees.sumOf { committeeBalance(it, receipts).remaining })}",
                        onOpenKameti, compact = true)
                }
            },
            second = { cardModifier ->
                if (savingsFirst) {
                    SummaryCard("Kameti paid", cp.sumOf { it.amountMinor },
                        Icons.Rounded.Groups, cardModifier,
                        "Received: ${money(receipts.sumOf { it.amountMinor })}\nPending: ${money(committees.sumOf { committeeBalance(it, receipts).remaining })}",
                        onOpenKameti, compact = true)
                } else {
                    SummaryCard("Savings total", savings.sumOf { it.amountMinor },
                        Icons.Rounded.AccountBalanceWallet, cardModifier, "All saved amounts", onOpenSavings, compact = true)
                }
            },
        )
        committeeMembers.filter { it.isMe && !it.received }.mapNotNull { member ->
            runCatching { YearMonth.parse(member.turnMonth) }.getOrNull()?.let { it to member }
        }.filter { !it.first.isBefore(month) }.minByOrNull { it.first }?.let { (turn, _) ->
            val shape = LedgerTokens.ShapeCard
            val accent = MaterialTheme.colorScheme.tertiary
            Surface(
                modifier = Modifier.fillMaxWidth().testTag("kameti-turn-card")
                    .clickable(onClick = onOpenKameti),
                shape = shape,
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = LedgerTokens.Elevation1,
                border = androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = .38f)),
            ) {
                Row(
                    Modifier.fillMaxWidth().clip(shape)
                        .background(
                            Brush.horizontalGradient(listOf(
                                accent.copy(alpha = .13f),
                                MaterialTheme.colorScheme.surface,
                            )),
                            shape = shape,
                        ).padding(horizontal = LedgerTokens.Space12, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    GlowIcon(Icons.Rounded.EventAvailable, accent, size = 30.dp)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("Your kameti turn • $turn", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Text(
                            if (turn == month) "Your receiving month is here" else "Upcoming personal turn",
                            fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(Icons.Rounded.ChevronRight, null, tint = accent)
                }
            }
        }
        if (loans.isNotEmpty()) {
            val outstanding: (String) -> Long = { direction ->
                loans.filter { it.direction == direction }.sumOf { loan ->
                    (loan.principalMinor - lp.filter { it.loanId == loan.id }.sumOf { it.amountMinor })
                        .coerceAtLeast(0)
                }
            }
            MoneySpacePair(
                first = { cardModifier ->
                    SummaryCard("I owe", outstanding("BORROWED"), Icons.Rounded.Handshake,
                        cardModifier, onClick = onOpenLoans, compact = true)
                },
                second = { cardModifier ->
                    SummaryCard("Owed to me", outstanding("LENT"), Icons.Rounded.AccountBalance,
                        cardModifier, onClick = onOpenLoans, compact = true)
                },
            )
        }
    }
}

/** Responsive compact tiles. Stack only on very narrow screens; keep 14dp gaps. */
@Composable
internal fun MoneySpacePair(
    first: @Composable (Modifier) -> Unit,
    second: @Composable (Modifier) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth < 275.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(LedgerTokens.Space16)) {
                first(Modifier.fillMaxWidth().heightIn(min = 126.dp).testTag("money-space-first"))
                second(Modifier.fillMaxWidth().heightIn(min = 126.dp).testTag("money-space-second"))
            }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(LedgerTokens.Space16)) {
                first(Modifier.weight(1f).heightIn(min = 134.dp).testTag("money-space-first"))
                second(Modifier.weight(1f).heightIn(min = 134.dp).testTag("money-space-second"))
            }
        }
    }
}
