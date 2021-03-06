package com.stripe.android.paymentsheet

import android.content.Intent
import com.google.common.truth.Truth.assertThat
import com.nhaarman.mockitokotlin2.isA
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyNoMoreInteractions
import com.stripe.android.ApiKeyFixtures
import com.stripe.android.ApiResultCallback
import com.stripe.android.PaymentController
import com.stripe.android.PaymentIntentResult
import com.stripe.android.R
import com.stripe.android.googlepay.StripeGooglePayEnvironment
import com.stripe.android.googlepay.StripeGooglePayLauncher
import com.stripe.android.model.CardBrand
import com.stripe.android.model.PaymentIntentFixtures
import com.stripe.android.model.PaymentMethod
import com.stripe.android.model.PaymentMethodFixtures
import com.stripe.android.paymentsheet.model.PaymentOption
import com.stripe.android.paymentsheet.model.PaymentSelection
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test

@RunWith(RobolectricTestRunner::class)
class DefaultPaymentSheetFlowControllerTest {
    private val googlePayLauncher = mock<StripeGooglePayLauncher>()
    private val paymentController = mock<PaymentController>()
    private val flowController = DefaultPaymentSheetFlowController(
        paymentController,
        ApiKeyFixtures.FAKE_PUBLISHABLE_KEY,
        null,
        DefaultPaymentSheetFlowController.Args(
            "client_secret",
            PaymentSheet.Configuration(
                customer = PaymentSheet.CustomerConfiguration(
                    id = "cus_123",
                    ephemeralKeySecret = "ephkey"
                ),
                googlePay = ConfigFixtures.GOOGLE_PAY,
            )
        ),
        paymentIntent = PaymentIntentFixtures.PI_REQUIRES_MASTERCARD_3DS2,
        paymentMethodTypes = listOf(PaymentMethod.Type.Card),
        paymentMethods = emptyList(),
        googlePayLauncherFactory = { googlePayLauncher },
        defaultPaymentMethodId = null
    )

    @Test
    fun `onPaymentOptionResult() with saved payment method selection result should return payment option`() {
        val paymentOption = flowController.onPaymentOptionResult(
            Intent().putExtras(
                PaymentOptionResult.Succeeded(
                    PaymentSelection.Saved(PaymentMethodFixtures.CARD_PAYMENT_METHOD)
                ).toBundle()
            )
        )
        assertThat(paymentOption)
            .isEqualTo(
                PaymentOption(
                    drawableResourceId = R.drawable.stripe_ic_visa,
                    label = CardBrand.Visa.displayName
                )
            )
    }

    @Test
    fun `onPaymentOptionResult() with cancelled result should return null`() {
        val paymentOption = flowController.onPaymentOptionResult(
            Intent().putExtras(
                PaymentOptionResult.Cancelled(null).toBundle()
            )
        )
        assertThat(paymentOption)
            .isNull()
    }

    @Test
    fun `confirmPayment() without paymentSelection should not call paymentController`() {
        verifyNoMoreInteractions(paymentController)
        flowController.confirmPayment(mock()) {
        }
    }

    @Test
    fun `confirmPayment() with GooglePay should start StripeGooglePayLauncher`() {
        flowController.onPaymentOptionResult(
            Intent().putExtras(
                PaymentOptionResult.Succeeded(
                    PaymentSelection.GooglePay
                ).toBundle()
            )
        )
        flowController.confirmPayment(mock()) {
        }
        verify(googlePayLauncher).startForResult(
            StripeGooglePayLauncher.Args(
                environment = StripeGooglePayEnvironment.Test,
                paymentIntent = PaymentIntentFixtures.PI_REQUIRES_MASTERCARD_3DS2,
                countryCode = "US"
            )
        )
    }

    @Test
    fun `isPaymentResult with Google Pay request code should return true`() {
        assertThat(
            flowController.isPaymentResult(
                StripeGooglePayLauncher.REQUEST_CODE,
                Intent()
            )
        ).isTrue()
    }

    @Test
    fun `onPaymentResult with Google Pay PaymentIntent result should invoke callback's onSuccess()`() {
        val callback = mock<ApiResultCallback<PaymentIntentResult>>()
        flowController.onPaymentResult(
            StripeGooglePayLauncher.REQUEST_CODE,
            Intent().putExtras(
                StripeGooglePayLauncher.Result.PaymentIntent(
                    paymentIntentResult = PAYMENT_INTENT_RESULT
                ).toBundle()
            ),
            callback = callback
        )
        verify(callback).onSuccess(PAYMENT_INTENT_RESULT)
    }

    @Test
    fun `onPaymentResult with Google Pay Error result should invoke callback's onError()`() {
        val callback = mock<ApiResultCallback<PaymentIntentResult>>()
        flowController.onPaymentResult(
            StripeGooglePayLauncher.REQUEST_CODE,
            Intent().putExtras(
                StripeGooglePayLauncher.Result.Error(
                    exception = RuntimeException()
                ).toBundle()
            ),
            callback = callback
        )
        verify(callback).onError(isA<RuntimeException>())
    }

    private companion object {
        private val PAYMENT_INTENT_RESULT = PaymentIntentResult(
            intent = PaymentIntentFixtures.PI_REQUIRES_MASTERCARD_3DS2
        )
    }
}
