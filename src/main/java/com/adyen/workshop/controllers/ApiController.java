package com.adyen.workshop.controllers;

import com.adyen.model.RequestOptions;
import com.adyen.model.checkout.*;
import com.adyen.workshop.configurations.ApplicationConfiguration;
import com.adyen.workshop.configurations.TokenStore;
import com.adyen.service.checkout.ModificationsApi;
import com.adyen.service.checkout.PaymentsApi;
import com.adyen.service.exception.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.view.RedirectView;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.UUID;

/**
 * REST controller for using the Adyen payments API.
 */
@RestController
public class ApiController {
    private final Logger log = LoggerFactory.getLogger(ApiController.class);

    private final ApplicationConfiguration applicationConfiguration;
    private final PaymentsApi paymentsApi;
    private final ModificationsApi modificationsApi;
    private final TokenStore tokenStore;

    public ApiController(ApplicationConfiguration applicationConfiguration, PaymentsApi paymentsApi,
                         ModificationsApi modificationsApi, TokenStore tokenStore) {
        this.applicationConfiguration = applicationConfiguration;
        this.paymentsApi = paymentsApi;
        this.modificationsApi = modificationsApi;
        this.tokenStore = tokenStore;
    }

    // Body of the modification endpoints: the pspReference of the pre-authorisation and, where needed, the amount
    public record ModificationBody(String pspReference, Amount amount) {
    }

    // Step 0
    @GetMapping("/hello-world")
    public ResponseEntity<String> helloWorld() throws Exception {
        return ResponseEntity.ok()
                .body("This is the 'Hello World' from the workshop - You've successfully finished step 0!");
    }

    // Step 7
    @PostMapping("/api/paymentMethods")
    public ResponseEntity<PaymentMethodsResponse> paymentMethods() throws IOException, ApiException {
        var paymentMethodsRequest = new PaymentMethodsRequest();
        paymentMethodsRequest.setMerchantAccount(applicationConfiguration.getAdyenMerchantAccount());

        log.info("retrieving avalable Payment Methos from Adyen {}", paymentMethodsRequest);
        var response = paymentsApi.paymentMethods(paymentMethodsRequest);
        log.info("Payment Methods response from Adyen {}", response);
        return ResponseEntity.ok().body(response);
    }

    // Step 9 - Implement the /payments call to Adyen.
    @PostMapping("/api/payments")
    public ResponseEntity<PaymentResponse> payments(@RequestBody PaymentRequest body) throws IOException, ApiException {

        var paymentRequest = new PaymentRequest();

        var amount = new Amount()
                .currency("EUR")
                .value(9998L);
        paymentRequest.setAmount(amount);
        paymentRequest.setMerchantAccount(applicationConfiguration.getAdyenMerchantAccount());
        paymentRequest.setChannel(PaymentRequest.ChannelEnum.WEB);

        paymentRequest.setPaymentMethod(body.getPaymentMethod());

        var orderRef = UUID.randomUUID().toString();
        paymentRequest.setReference(orderRef);
        // The returnUrl field basically means: Once done with the payment, where should
        // the application redirect you?
        paymentRequest.setReturnUrl("http://localhost:8080/handleShopperRedirect");

        // Step 11 - Optionally, add the idempotency key
        var requestOptions = new RequestOptions();
        requestOptions.setIdempotencyKey(UUID.randomUUID().toString());

        // Step 12 3DS2 Redirect - Add the following additional parameters to your
        // existing payment request for 3DS2 Redirect:
        // Note: Visa requires additional properties to be sent in the request, see
        // documentation for Redirect 3DS2:
        // https://docs.adyen.com/online-payments/3d-secure/redirect-3ds2/web-drop-in/#make-a-payment
        var authenticationData = new AuthenticationData();
        authenticationData.setAttemptAuthentication(AuthenticationData.AttemptAuthenticationEnum.ALWAYS);

        // Change the following lines, if you want to enable the Native 3DS2 flow:
        // Note: Visa requires additional properties to be sent in the request, see
        // documentation for Native 3DS2:
        // https://docs.adyen.com/online-payments/3d-secure/native-3ds2/web-drop-in/#make-a-payment
        authenticationData.setThreeDSRequestData(
                new ThreeDSRequestData().nativeThreeDS(ThreeDSRequestData.NativeThreeDSEnum.PREFERRED));
        paymentRequest.setAuthenticationData(authenticationData);

        paymentRequest.setOrigin("https://localhost:8080");
        paymentRequest.setBrowserInfo(body.getBrowserInfo());
        paymentRequest.setShopperIP("192.168.0.1");
        paymentRequest.setShopperInteraction(PaymentRequest.ShopperInteractionEnum.ECOMMERCE);

        var billingAddress = new BillingAddress();
        billingAddress.setCity("Amsterdam");
        billingAddress.setCountry("NL");
        billingAddress.setPostalCode("1012KK");
        billingAddress.setStreet("Rokin");
        billingAddress.setHouseNumberOrName("49");
        paymentRequest.setBillingAddress(billingAddress);

        // Step 19 - Klarna requires countryCode, shopperLocale, shopperEmail and lineItems.
        // The sum of (amountIncludingTax * quantity) must equal the payment amount (9998).
        paymentRequest.setCountryCode("NL");
        paymentRequest.setShopperLocale("en-US");
        paymentRequest.setShopperEmail("test@example.com");
        paymentRequest.setLineItems(Arrays.asList(
                new LineItem()
                        .quantity(2L)
                        .amountExcludingTax(4131L)
                        .taxPercentage(2100L)
                        .description("Sunglasses")
                        .id("Item #1")
                        .taxAmount(868L)
                        .amountIncludingTax(4999L)
                        .productUrl("URL_TO_PURCHASED_ITEM")
                        .imageUrl("URL_TO_PICTURE_OF_PURCHASED_ITEM")
        ));

        log.info("PaymentsRequest {}", paymentRequest);
        var response = paymentsApi.payments(paymentRequest, requestOptions);
        log.info("PaymentsResponse {}", response);
        return ResponseEntity.ok().body(response);
    }

    // Tokenization - Step 1: zero-auth payment (amount 0) that creates a token for the card.
    // The token (recurringDetailReference) arrives in the webhook, see WebhookController.
    @PostMapping("/api/subscription-create")
    public ResponseEntity<PaymentResponse> subscriptionCreate(@RequestBody PaymentRequest body) throws IOException, ApiException {
        var paymentRequest = new PaymentRequest();
        paymentRequest.setAmount(new Amount().currency("EUR").value(0L));
        paymentRequest.setMerchantAccount(applicationConfiguration.getAdyenMerchantAccount());
        paymentRequest.setChannel(PaymentRequest.ChannelEnum.WEB);
        paymentRequest.setPaymentMethod(body.getPaymentMethod());
        paymentRequest.setReference(UUID.randomUUID().toString());
        paymentRequest.setReturnUrl("http://localhost:8080/handleShopperRedirect");
        paymentRequest.setCountryCode("NL");
        paymentRequest.setShopperLocale("en-US");
        paymentRequest.setBrowserInfo(body.getBrowserInfo());
        paymentRequest.setOrigin("https://localhost:8080");
        paymentRequest.setShopperIP("192.168.0.1");

        // Flag the payment as the first of a subscription and ask Adyen to store the card
        paymentRequest.setShopperReference(TokenStore.SHOPPER_REFERENCE);
        paymentRequest.setStorePaymentMethod(true);
        paymentRequest.setRecurringProcessingModel(PaymentRequest.RecurringProcessingModelEnum.SUBSCRIPTION);
        paymentRequest.setShopperInteraction(PaymentRequest.ShopperInteractionEnum.ECOMMERCE);

        var authenticationData = new AuthenticationData();
        authenticationData.setAttemptAuthentication(AuthenticationData.AttemptAuthenticationEnum.ALWAYS);
        authenticationData.setThreeDSRequestData(
                new ThreeDSRequestData().nativeThreeDS(ThreeDSRequestData.NativeThreeDSEnum.PREFERRED));
        paymentRequest.setAuthenticationData(authenticationData);

        var requestOptions = new RequestOptions();
        requestOptions.setIdempotencyKey(UUID.randomUUID().toString());

        log.info("Subscription create request {}", paymentRequest);
        var response = paymentsApi.payments(paymentRequest, requestOptions);
        log.info("Subscription create response {}", response);
        return ResponseEntity.ok().body(response);
    }

    // Tokenization - Step 3: charge the stored token. Pass the recurringDetailReference from the webhook,
    // or call it without a token to use the latest stored one.
    @RequestMapping(value = {"/makepaymentwithtoken/{token}", "/makepaymentwithtoken"}, method = {RequestMethod.GET, RequestMethod.POST})
    public ResponseEntity<?> makePaymentWithToken(@PathVariable(required = false) String token) throws IOException, ApiException {
        var storedToken = token != null ? token : tokenStore.latest().orElse(null);
        if (storedToken == null) {
            return ResponseEntity.badRequest().body("No token available yet. Wait for the webhook or call /makepaymentwithtoken/{token}");
        }

        var cardDetails = new CardDetails()
                .type(CardDetails.TypeEnum.SCHEME)
                .storedPaymentMethodId(storedToken);

        var paymentRequest = new PaymentRequest();
        paymentRequest.setAmount(new Amount().currency("EUR").value(500L)); // 5 euros / month
        paymentRequest.setMerchantAccount(applicationConfiguration.getAdyenMerchantAccount());
        paymentRequest.setPaymentMethod(new CheckoutPaymentMethod(cardDetails));
        paymentRequest.setReference(UUID.randomUUID().toString());
        paymentRequest.setShopperReference(TokenStore.SHOPPER_REFERENCE);
        paymentRequest.setShopperInteraction(PaymentRequest.ShopperInteractionEnum.CONTAUTH);
        paymentRequest.setRecurringProcessingModel(PaymentRequest.RecurringProcessingModelEnum.SUBSCRIPTION);

        var requestOptions = new RequestOptions();
        requestOptions.setIdempotencyKey(UUID.randomUUID().toString());

        log.info("Payment with token request {}", paymentRequest);
        var response = paymentsApi.payments(paymentRequest, requestOptions);
        log.info("Payment with token response {}", response);
        return ResponseEntity.ok().body(response);
    }

    // Pre-authorisation - Step 1: authorise 10.00 EUR without capturing it, so it can be adjusted, captured or cancelled later.
    @PostMapping("/api/preauthorisation")
    public ResponseEntity<PaymentResponse> preauthorisation(@RequestBody PaymentRequest body) throws IOException, ApiException {
        var paymentRequest = new PaymentRequest();
        paymentRequest.setAmount(new Amount().currency("EUR").value(1000L));
        paymentRequest.setMerchantAccount(applicationConfiguration.getAdyenMerchantAccount());
        paymentRequest.setChannel(PaymentRequest.ChannelEnum.WEB);
        paymentRequest.setPaymentMethod(body.getPaymentMethod());
        paymentRequest.setReference(UUID.randomUUID().toString());
        paymentRequest.setReturnUrl("http://localhost:8080/handleShopperRedirect");
        paymentRequest.setCountryCode("NL");
        paymentRequest.setShopperLocale("en-US");
        paymentRequest.setBrowserInfo(body.getBrowserInfo());
        paymentRequest.setOrigin("https://localhost:8080");
        paymentRequest.setShopperIP("192.168.0.1");
        paymentRequest.setShopperInteraction(PaymentRequest.ShopperInteractionEnum.ECOMMERCE);

        // Flag the payment as a pre-authorisation and ask for manual capture on this payment
        var additionalData = new HashMap<String, String>();
        additionalData.put("authorisationType", "PreAuth");
        additionalData.put("manualCapture", "true");
        paymentRequest.setAdditionalData(additionalData);

        var authenticationData = new AuthenticationData();
        authenticationData.setAttemptAuthentication(AuthenticationData.AttemptAuthenticationEnum.ALWAYS);
        authenticationData.setThreeDSRequestData(
                new ThreeDSRequestData().nativeThreeDS(ThreeDSRequestData.NativeThreeDSEnum.PREFERRED));
        paymentRequest.setAuthenticationData(authenticationData);

        var requestOptions = new RequestOptions();
        requestOptions.setIdempotencyKey(UUID.randomUUID().toString());

        log.info("Pre-authorisation request {}", paymentRequest);
        var response = paymentsApi.payments(paymentRequest, requestOptions);
        log.info("Pre-authorisation response {} (keep the pspReference {})", response, response.getPspReference());
        return ResponseEntity.ok().body(response);
    }

    // Pre-authorisation - Step 2: adjust the authorised amount of the pre-authorisation (AUTHORISATION_ADJUSTMENT webhook).
    @PostMapping("/api/modify-amount")
    public ResponseEntity<?> modifyAmount(@RequestBody ModificationBody body) throws IOException, ApiException {
        if (body.pspReference() == null || body.amount() == null) {
            return ResponseEntity.badRequest().body("Send { pspReference, amount: { currency, value } }");
        }

        var request = new PaymentAmountUpdateRequest();
        request.setMerchantAccount(applicationConfiguration.getAdyenMerchantAccount());
        request.setAmount(body.amount());
        request.setReference(UUID.randomUUID().toString());

        var requestOptions = new RequestOptions();
        requestOptions.setIdempotencyKey(UUID.randomUUID().toString());

        log.info("Modify amount request for {}: {}", body.pspReference(), request);
        var response = modificationsApi.updateAuthorisedAmount(body.pspReference(), request, requestOptions);
        log.info("Modify amount response {}", response);
        return ResponseEntity.ok().body(response);
    }

    // Pre-authorisation - Step 3: capture the (adjusted) amount (CAPTURE webhook).
    @PostMapping("/api/capture")
    public ResponseEntity<?> capture(@RequestBody ModificationBody body) throws IOException, ApiException {
        if (body.pspReference() == null || body.amount() == null) {
            return ResponseEntity.badRequest().body("Send { pspReference, amount: { currency, value } }");
        }

        var request = new PaymentCaptureRequest();
        request.setMerchantAccount(applicationConfiguration.getAdyenMerchantAccount());
        request.setAmount(body.amount());
        request.setReference(UUID.randomUUID().toString());

        var requestOptions = new RequestOptions();
        requestOptions.setIdempotencyKey(UUID.randomUUID().toString());

        log.info("Capture request for {}: {}", body.pspReference(), request);
        var response = modificationsApi.captureAuthorisedPayment(body.pspReference(), request, requestOptions);
        log.info("Capture response {}", response);
        return ResponseEntity.ok().body(response);
    }

    // Pre-authorisation - Step 4: cancel a pre-authorisation that was not captured yet (CANCELLATION webhook).
    @PostMapping("/api/cancel")
    public ResponseEntity<?> cancel(@RequestBody ModificationBody body) throws IOException, ApiException {
        if (body.pspReference() == null) {
            return ResponseEntity.badRequest().body("Send { pspReference }");
        }

        var request = new PaymentCancelRequest();
        request.setMerchantAccount(applicationConfiguration.getAdyenMerchantAccount());
        request.setReference(UUID.randomUUID().toString());

        var requestOptions = new RequestOptions();
        requestOptions.setIdempotencyKey(UUID.randomUUID().toString());

        log.info("Cancel request for {}: {}", body.pspReference(), request);
        var response = modificationsApi.cancelAuthorisedPaymentByPspReference(body.pspReference(), request, requestOptions);
        log.info("Cancel response {}", response);
        return ResponseEntity.ok().body(response);
    }

    // Pre-authorisation - Step 5: refund a captured payment (REFUND webhook).
    @PostMapping("/api/refund")
    public ResponseEntity<?> refund(@RequestBody ModificationBody body) throws IOException, ApiException {
        if (body.pspReference() == null || body.amount() == null) {
            return ResponseEntity.badRequest().body("Send { pspReference, amount: { currency, value } }");
        }

        var request = new PaymentRefundRequest();
        request.setMerchantAccount(applicationConfiguration.getAdyenMerchantAccount());
        request.setAmount(body.amount());
        request.setReference(UUID.randomUUID().toString());

        var requestOptions = new RequestOptions();
        requestOptions.setIdempotencyKey(UUID.randomUUID().toString());

        log.info("Refund request for {}: {}", body.pspReference(), request);
        var response = modificationsApi.refundCapturedPayment(body.pspReference(), request, requestOptions);
        log.info("Refund response {}", response);
        return ResponseEntity.ok().body(response);
    }

    // Step 13 - Handle details call (triggered after Native 3DS2 flow)
    @PostMapping("/api/payments/details")
    public ResponseEntity<PaymentDetailsResponse> paymentsDetails(@RequestBody PaymentDetailsRequest detailsRequest)
            throws IOException, ApiException {

        log.info("PaymentDetailsRequest {}", detailsRequest);
        var response = paymentsApi.paymentsDetails(detailsRequest);
        log.info("PaymentDetailsResponse {}", response);
        return ResponseEntity.ok().body(response);
    }

    // Step 14 - Handle Redirect 3DS2 during payment.
    @GetMapping("/handleShopperRedirect")
    public RedirectView redirect(@RequestParam(required = false) String payload,
            @RequestParam(required = false) String redirectResult) throws IOException, ApiException {
        var paymentDetailsRequest = new PaymentDetailsRequest();

        PaymentCompletionDetails paymentCompletionDetails = new PaymentCompletionDetails();

        // Handle redirect result or payload
        if (redirectResult != null && !redirectResult.isEmpty()) {
            // For redirect, you are redirected to an Adyen domain to complete the 3DS2
            // challenge
            // After completing the 3DS2 challenge, you get the redirect result from Adyen
            // in the returnUrl
            // We then pass on the redirectResult
            paymentCompletionDetails.redirectResult(redirectResult);
        } else if (payload != null && !payload.isEmpty()) {
            paymentCompletionDetails.payload(payload);
        }

        paymentDetailsRequest.setDetails(paymentCompletionDetails);

        var paymentsDetailsResponse = paymentsApi.paymentsDetails(paymentDetailsRequest);
        log.info("PaymentsDetailsResponse {}", paymentsDetailsResponse);

        // Handle response and redirect user accordingly
        var redirectURL = "http://localhost:8080/result/"; // Update your url here by replacing `http://localhost:8080`
                                                           // with where your application is hosted (if needed)
        switch (paymentsDetailsResponse.getResultCode()) {
            case AUTHORISED:
                redirectURL += "success";
                break;
            case PENDING:
            case RECEIVED:
                redirectURL += "pending";
                break;
            case REFUSED:
                redirectURL += "failed";
                break;
            default:
                redirectURL += "error";
                break;
        }
        return new RedirectView(redirectURL + "?reason=" + paymentsDetailsResponse.getResultCode());
    }

}
