package app.cove.companion.feature.money

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.cove.companion.core.Undo
import app.cove.companion.design.components.UndoHost

/** Area name of Money's Undo offers (expense and category deletes). */
internal const val MONEY_UNDO = "money"

/** "Expense deleted · Undo" bar shown for a few seconds after a Money delete; see [Undo]. */
@Composable
fun MoneyUndoBar(modifier: Modifier = Modifier) = UndoHost(MONEY_UNDO, modifier)
