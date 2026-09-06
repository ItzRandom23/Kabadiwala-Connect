package com.irinteractivestudios.kabadiwalaconnect

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import com.irinteractivestudios.kabadiwalaconnect.data.repository.PaymentRepository
import com.irinteractivestudios.kabadiwalaconnect.domain.model.EarningsSummary
import com.irinteractivestudios.kabadiwalaconnect.domain.model.Lot
import com.irinteractivestudios.kabadiwalaconnect.domain.model.Payment
import com.irinteractivestudios.kabadiwalaconnect.ui.screens.payments.PaymentRecordScreen
import com.irinteractivestudios.kabadiwalaconnect.ui.theme.KabadiwalaConnectTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Rule
import org.junit.Test

class CoreWorkflowTrustTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun paymentConfirmationShowsLotProofAndOfflineStatus() {
        composeRule.setContent {
            KabadiwalaConnectTheme {
                PaymentRecordScreen(
                    lots = listOf(
                        Lot(
                            id = "lot-proof",
                            materialLabel = "Copper wire",
                            weightKg = 4.5,
                            estimatedValueRupees = 900.0,
                            createdAtEpochMs = 0L
                        )
                    ),
                    repo = emptyPaymentRepository(),
                    onSaved = { _, _ -> }
                )
            }
        }

        composeRule.onNodeWithText("Amount in rupees").performTextInput("900")

        composeRule.onAllNodesWithText("Copper wire").assertCountEquals(2)
        composeRule.onNodeWithText("4.5 kg").assertIsDisplayed()
        composeRule.onNodeWithText("Saved locally — waiting to sync").assertIsDisplayed()
    }

    private fun emptyPaymentRepository() = object : PaymentRepository {
        override fun observeSummary(): Flow<EarningsSummary> = emptyFlow()
        override fun observePayments(): Flow<List<Payment>> = emptyFlow()
        override suspend fun record(payment: Payment) = Unit
    }
}
