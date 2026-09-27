package com.irinteractivestudios.kabadiwalaconnect

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import com.irinteractivestudios.kabadiwalaconnect.data.remote.AuthorizationDto
import com.irinteractivestudios.kabadiwalaconnect.data.remote.RecyclerDto
import com.irinteractivestudios.kabadiwalaconnect.domain.model.AccountProfile
import com.irinteractivestudios.kabadiwalaconnect.domain.model.AccountRole
import com.irinteractivestudios.kabadiwalaconnect.domain.model.RecyclerVerificationStatus
import com.irinteractivestudios.kabadiwalaconnect.ui.screens.recycler.RecyclerVerificationScreen
import com.irinteractivestudios.kabadiwalaconnect.ui.theme.KabadiwalaConnectTheme
import org.junit.Rule
import org.junit.Test

class RecyclerVerificationGateTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun pendingRequestAlreadySubmittedHidesEvidenceForm() {
        composeRule.setContent {
            KabadiwalaConnectTheme {
                RecyclerVerificationScreen(
                    profile = pendingAccount(),
                    recyclerProfile = RecyclerDto(
                        id = "recycler-1",
                        name = "Green Loop",
                        authorizationStatus = "PENDING",
                        authorizationDetails = AuthorizationDto(
                            authority = "MPCB",
                            type = "CTO",
                            registrationNumber = "REG-123",
                            evidenceReference = "https://example.test/evidence"
                        )
                    )
                )
            }
        }

        composeRule.onAllNodesWithText("Your authorization evidence is already with the review team. You can submit updated evidence after they respond.").assertCountEquals(1)
        composeRule.onNodeWithText("Pending review").assertIsDisplayed()
        composeRule.onAllNodesWithText("Submit official details. A reviewer checks them before access opens.").assertCountEquals(0)
        composeRule.onAllNodesWithText("Submit authorization evidence").assertCountEquals(0)
        composeRule.onAllNodesWithText("Issuing authority").assertCountEquals(0)
    }

    @Test
    fun newPendingProfileCanSubmitEvidenceOnceProfileIsLoaded() {
        composeRule.setContent {
            KabadiwalaConnectTheme {
                RecyclerVerificationScreen(
                    profile = pendingAccount(),
                    recyclerProfile = RecyclerDto(
                        id = "recycler-1",
                        name = "Green Loop",
                        authorizationStatus = "PENDING",
                        authorizationDetails = AuthorizationDto()
                    )
                )
            }
        }

        composeRule.onNodeWithText("Evidence needed").assertIsDisplayed()
        composeRule.onNodeWithText("Your Recycler account has not sent authorization evidence yet. Submit it once; the review team can then verify your account.").assertIsDisplayed()
        composeRule.onNodeWithText("Submit official details. A reviewer checks them before access opens.").assertIsDisplayed()
        // The evidence form is taller than a phone viewport; the CTA is
        // intentionally reached by scrolling rather than clipped offscreen.
        composeRule.onNodeWithText("Submit authorization evidence").assertExists()
    }

    @Test
    fun pendingProfileWithoutLoadedServerStateFailsClosed() {
        composeRule.setContent {
            KabadiwalaConnectTheme {
                RecyclerVerificationScreen(profile = pendingAccount(), loading = false, error = "Profile could not load. Retry.")
            }
        }

        composeRule.onAllNodesWithText("Submit authorization evidence").assertCountEquals(0)
        composeRule.onNodeWithText("Profile could not load. Retry.").assertIsDisplayed()
    }

    @Test
    fun expiredAuthorizationCanBeRenewed() {
        composeRule.setContent {
            KabadiwalaConnectTheme {
                RecyclerVerificationScreen(
                    profile = pendingAccount(),
                    recyclerProfile = RecyclerDto(
                        id = "recycler-1",
                        name = "Green Loop",
                        authorizationStatus = "EXPIRED",
                        authorizationDetails = AuthorizationDto(
                            authority = "MPCB",
                            type = "CTO",
                            registrationNumber = "REG-123",
                            evidenceReference = "https://example.test/evidence"
                        )
                    )
                )
            }
        }

        composeRule.onNodeWithText("Authorization expired").assertIsDisplayed()
        composeRule.onNodeWithText("Your authorization has expired. Submit current evidence for review before using Recycler services.").assertIsDisplayed()
        composeRule.onAllNodesWithText("Submit authorization evidence").assertCountEquals(1)
        composeRule.onAllNodesWithText("Your authorization evidence is already with the review team. You can submit updated evidence after they respond.").assertCountEquals(0)
    }

    @Test
    fun serverReviewStateOverridesStaleVerifiedAccountStatus() {
        composeRule.setContent {
            KabadiwalaConnectTheme {
                RecyclerVerificationScreen(
                    profile = verifiedAccount(),
                    recyclerProfile = RecyclerDto(
                        id = "recycler-1",
                        name = "Green Loop",
                        authorizationStatus = "UNDER_REVIEW",
                        authorizationDetails = AuthorizationDto(
                            authority = "MPCB",
                            type = "CTO",
                            registrationNumber = "REG-123",
                            evidenceReference = "https://example.test/evidence"
                        )
                    )
                )
            }
        }

        composeRule.onNodeWithText("Pending review").assertIsDisplayed()
        composeRule.onAllNodesWithText("Your facility is verified").assertCountEquals(0)
        composeRule.onAllNodesWithText("Submit authorization evidence").assertCountEquals(0)
    }

    private fun pendingAccount() = AccountProfile(
        id = "recycler-1",
        email = "recycler@example.test",
        role = AccountRole.RECYCLER,
        verificationStatus = RecyclerVerificationStatus.PENDING
    )

    private fun verifiedAccount() = AccountProfile(
        id = "recycler-1",
        email = "recycler@example.test",
        role = AccountRole.RECYCLER,
        verificationStatus = RecyclerVerificationStatus.VERIFIED
    )
}
