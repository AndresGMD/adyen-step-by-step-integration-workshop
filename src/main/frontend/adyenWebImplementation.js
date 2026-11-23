// Tree shaking: import only the components we use, instead of the full `window.AdyenWeb` bundle.
import { AdyenCheckout, Dropin, Card, Redirect, Klarna } from "@adyen/adyen-web";
const clientKey = document.getElementById("clientKey").innerHTML;
// Tokenization: on /checkout?type=subscription the card is tokenized with a zero-auth payment.
// Pre-authorisation: on /checkout?type=preauthorisation the payment is authorised but not captured.
const type = document.getElementById("type")?.innerHTML;
const paymentsEndpoint = type === "subscription" ? "/api/subscription-create"
    : type === "preauthorisation" ? "/api/preauthorisation"
    : "/api/payments";

// Starts the (Adyen.Web) AdyenCheckout with your specified configuration by calling the `/paymentMethods` endpoint.
async function startCheckout() {
    try {
        // Step 8 - Retrieve the available payment methods
        const paymentMethodsResponse = await fetch("/api/paymentMethods", {
            method: "POST",
            headers: {
                "Content-Type": "application/json"
            }
        }).then(response => response.json());

        const configuration = {
            paymentMethodsResponse: paymentMethodsResponse,
            clientKey,
            locale: "en_US",
            countryCode: "NL",
            environment: "test",
            showPayButton: true,
            translations: {
                'en-US': {
                    'creditCard.securityCode.label': 'CVV/CVC'
                }
            },
            // Step 10 - Add the onSubmit handler by telling it what endpoint to call when the pay button is pressed.
            onSubmit: async (state, component, actions) => {
                console.info("onSubmit", state, component, actions);
                try {
                    if (state.isValid) {
                        const { action, order, resultCode } = await fetch(paymentsEndpoint, {
                            method: "POST",
                            body: state.data ? JSON.stringify(state.data) : "",
                            headers: {
                                "Content-Type": "application/json",
                            }
                        }).then(response => response.json());

                        if (!resultCode) {
                            console.warn("reject");
                            actions.reject();
                        }

                        actions.resolve({
                            resultCode,
                            action,
                            order
                        });
                    }
                } catch (error) {
                    console.error(error);
                    actions.reject();
                }
            },
            onPaymentCompleted: (result, component) => {
                console.info("onPaymentCompleted", result, component);
                handleOnPaymentCompleted(result, component);
            },
            onPaymentFailed: (result, component) => {
                console.info("onPaymentFailed", result, component);
                handleOnPaymentFailed(result, component);
            },
            onError: (error, component) => {
                console.error("onError", error.name, error.message, error.stack, component);
                window.location.href = "/result/error";
            },
            // Step 13 onAdditionalDetails(...) - Use this to finalize the Native 3DS2 authentication.
            onAdditionalDetails: async (state, component, actions) => {
                console.info("onAdditionalDetails", state, component);
                try {
                    const { resultCode } = await fetch("/api/payments/details", {
                        method: "POST",
                        body: state.data ? JSON.stringify(state.data) : "",
                        headers: {
                            "Content-Type": "application/json",
                        }
                    }).then(response => response.json());

                    if (!resultCode) {
                        console.warn("reject");
                        actions.reject();
                    }

                    actions.resolve({ resultCode });
                } catch (error) {
                    console.error(error);
                    actions.reject();
                }
            }
        };

        // Optional configuration for card
        const paymentMethodsConfiguration = {
            card: {
                showBrandIcon: true,
                hasHolderName: true,
                holderNameRequired: true,
                name: "Credit or debit card",
                amount: {
                    value: 9998,
                    currency: "EUR",
                },
                placeholders: {
                    cardNumber: '1234 5678 9012 3456',
                    expiryDate: 'NN/YY',
                    securityCodeThreeDigits: '123',
                    securityCodeFourDigits: '1234',
                    holderName: 'Developer Relations Team'
                }
            }
        };

        // start the AdyenCheckout and mount the element onto the 'payment' -div.
        const adyenCheckout = await AdyenCheckout(configuration);
        const dropin = new Dropin(adyenCheckout, {
            // Steps 18 & 19 - Only the payment methods listed here end up in the bundle: Card, Redirect (iDEAL) and Klarna.
            paymentMethodComponents: [Card, Redirect, Klarna],
            paymentMethodsConfiguration: paymentMethodsConfiguration
        }).mount(document.getElementById("payment"));
    } catch (error) {
        console.error(error);
        alert("Error occurred. Look at console for details.");
    }
}

// Step 10 - Function to handle payment completion redirects
function handleOnPaymentCompleted(response) {
    switch (response.resultCode) {
        case "Authorised":
            window.location.href = "/result/success";
            break;
        case "Pending":
        case "Received":
            window.location.href = "/result/pending";
            break;
        default:
            window.location.href = "/result/error";
            break;
    }
}

// Step 10 - Function to handle payment failure redirects
function handleOnPaymentFailed(response) {
    switch (response.resultCode) {
        case "Cancelled":
        case "Refused":
            window.location.href = "/result/failed";
            break;
        default:
            window.location.href = "/result/error";
            break;
    }
}

startCheckout();