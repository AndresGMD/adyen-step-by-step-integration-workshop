package com.adyen.workshop.controllers;

import com.adyen.model.notification.NotificationRequest;
import com.adyen.model.notification.NotificationRequestItem;
import com.adyen.util.HMACValidator;
import com.adyen.workshop.configurations.ApplicationConfiguration;
import com.adyen.workshop.configurations.TokenStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.security.SignatureException;

/**
 * REST controller for receiving Adyen webhook notifications
 */
@RestController
public class WebhookController {
    private final Logger log = LoggerFactory.getLogger(WebhookController.class);

    private final ApplicationConfiguration applicationConfiguration;

    private final HMACValidator hmacValidator;
    private final TokenStore tokenStore;

    public WebhookController(ApplicationConfiguration applicationConfiguration, HMACValidator hmacValidator, TokenStore tokenStore) {
        this.applicationConfiguration = applicationConfiguration;
        this.hmacValidator = hmacValidator;
        this.tokenStore = tokenStore;
    }

    // Step 16 - Validate the HMAC signature using the ADYEN_HMAC_KEY
    @PostMapping("/webhooks")
    public ResponseEntity<String> webhooks(@RequestBody String json) throws Exception {
        log.info("Received: {}", json);
        var notificationRequest = NotificationRequest.fromJson(json);
        var notificationRequestItem = notificationRequest.getNotificationItems().stream().findFirst();

        try {
            NotificationRequestItem item = notificationRequestItem.get();

            // Step 16 - Validate the HMAC signature using the ADYEN_HMAC_KEY
            if (!hmacValidator.validateHMAC(item, this.applicationConfiguration.getAdyenHmacKey())) {
                log.warn("Could not validate HMAC signature for incoming webhook message: {}", item);
                return ResponseEntity.unprocessableEntity().build();
            }

            // Tokenization - Step 2: keep the token (recurringDetailReference) from the webhook.
            // It is in additionalData of the RECURRING_CONTRACT webhook (and of AUTHORISATION when tokenization is enabled).
            var additionalData = item.getAdditionalData();
            if (additionalData != null) {
                var token = additionalData.getOrDefault("recurring.recurringDetailReference",
                        additionalData.get("tokenization.storedPaymentMethodId"));
                if (token != null) {
                    tokenStore.save(token);
                    log.info("{} webhook: stored token (recurringDetailReference) {}", item.getEventCode(), token);
                }
            }

            // Success, log it for now
            log.info("Received webhook with event {}", item.toString());

            return ResponseEntity.accepted().build();
        } catch (SignatureException e) {
            // Handle invalid signature
            return ResponseEntity.unprocessableEntity().build();
        } catch (Exception e) {
            // Handle all other errors
            return ResponseEntity.status(500).build();
        }
    }
}