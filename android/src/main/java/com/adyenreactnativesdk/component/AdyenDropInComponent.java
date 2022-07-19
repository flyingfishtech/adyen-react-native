/*
 * Copyright (c) 2021 Adyen N.V.
 *
 * This file is open source and available under the MIT license. See the LICENSE file for more info.
 */

package com.adyenreactnativesdk.component;

import android.app.Activity;
import android.content.Intent;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.adyen.checkout.adyen3ds2.Adyen3DS2Configuration;
import com.adyen.checkout.bcmc.BcmcConfiguration;
import com.adyen.checkout.card.CardConfiguration;
import com.adyen.checkout.components.model.PaymentMethodsApiResponse;
import com.adyen.checkout.components.model.payments.Amount;
import com.adyen.checkout.core.api.Environment;
import com.adyen.checkout.dropin.DropIn;
import com.adyen.checkout.dropin.DropInConfiguration;
import com.adyen.checkout.googlepay.GooglePayConfiguration;
import com.adyen.checkout.redirect.RedirectComponent;
import com.adyenreactnativesdk.AdyenDropInService;
import com.adyenreactnativesdk.DropInServiceProxy;
import com.adyenreactnativesdk.ReactNativeError;
import com.adyenreactnativesdk.ReactNativeJson;
import com.adyenreactnativesdk.configuration.CardConfigurationParser;
import com.adyenreactnativesdk.configuration.DropInConfigurationParser;
import com.adyenreactnativesdk.configuration.GooglePayConfigurationParser;
import com.adyenreactnativesdk.configuration.RootConfigurationParser;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableMap;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Locale;

public class AdyenDropInComponent extends BaseModule implements DropInServiceProxy.DropInServiceListener {

    private final String TAG = "DropInComponent";

    public AdyenDropInComponent(ReactApplicationContext context) {
        super(context);
        DropInServiceProxy.shared.setServiceListener(this);
    }

    @Override
    public String getName() {
        return "AdyenDropIn";
    }

    @ReactMethod
    public void open(ReadableMap paymentMethodsData, ReadableMap configuration) {
        final PaymentMethodsApiResponse paymentMethodsResponse = getPaymentMethodsApiResponse(paymentMethodsData);
        if (paymentMethodsResponse == null) return;

        final RootConfigurationParser parser = new RootConfigurationParser(configuration);
        final Environment environment;
        final String clientKey;


        try {
            environment = parser.getEnvironment();
            clientKey = parser.getClientKey();
        } catch (NoSuchFieldException e) {
            sendEvent(DID_FAILED, ReactNativeError.mapError(e));
            return;
        }

        DropInConfiguration.Builder builder;
        builder = new DropInConfiguration.Builder(getReactApplicationContext(), AdyenDropInService.class, clientKey)
                .setEnvironment(environment);

        try {
            final Locale shopperLocale = parser.getLocale();
            builder.setShopperLocale(shopperLocale);
        } catch (NoSuchFieldException e) {  }

        configureDropIn(builder, configuration);
        configureCards(builder, configuration);
        configureBcmc(builder, configuration);
        configure3DS(builder);

        final Amount amount = parser.getAmount();
        if (amount != null) {
            builder.setAmount(amount);

            try {
                final String countryCode = parser.getCountryCode();
                configureGooglePay(countryCode, builder, configuration);
            } catch (NoSuchFieldException e) {
                Log.d(TAG, "Can't configure GooglePayComponent: No `countryCode` in configuration.");
            }
        }

        Activity currentActivity = getReactApplicationContext().getCurrentActivity();
        Intent resultIntent = new Intent(currentActivity, currentActivity.getClass());
        resultIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

        DropIn.startPayment(currentActivity, paymentMethodsResponse, builder.build(), resultIntent);
    }

    @ReactMethod
    public void handle(ReadableMap actionMap) {
        final DropInServiceProxy.DropInModuleListener listener = DropInServiceProxy.shared.getModuleListener();
        if (listener == null) {
            IllegalStateException e = new IllegalStateException("Invalid state: DropInModuleListener is missing");
            sendEvent(DID_FAILED, ReactNativeError.mapError(e));
            return;
        }

        try {
            JSONObject jsonObject = ReactNativeJson.convertMapToJson(actionMap);
            listener.onAction(jsonObject);
        } catch (JSONException e) {
            sendEvent(DID_FAILED, ReactNativeError.mapError(e));
        }
    }

    @ReactMethod
    public void hide(Boolean success, ReadableMap message) {
        proxyHideDropInCommand(success, message);
    }

    @ReactMethod
    public void addListener(String eventName) {
        // Set up any upstream listeners or background tasks as necessary
    }

    @ReactMethod
    public void removeListeners(Integer count) {
        // Remove upstream listeners, stop unnecessary background tasks
    }

    @Override
    public void onDidSubmit(@NonNull JSONObject jsonObject) {
        WritableMap map;
        try {
            map = ReactNativeJson.convertJsonToMap(jsonObject);
        } catch (JSONException e) {
            sendEvent(DID_FAILED, ReactNativeError.mapError(e));
            return;
        }

        final ReactApplicationContext context = getReactApplicationContext();
        map.putString("returnUrl", RedirectComponent.getReturnUrl(context));
        sendEvent(DID_SUBMIT, map);
    }

    @Override
    public void onDidProvide(@NonNull JSONObject jsonObject) {
        try {
            WritableMap map = ReactNativeJson.convertJsonToMap(jsonObject);
            sendEvent(DID_PROVIDE, map);
        } catch (JSONException e) {
            sendEvent(DID_FAILED, ReactNativeError.mapError(e));
        }
    }

    private void proxyHideDropInCommand(Boolean success, @Nullable ReadableMap message) {
        final DropInServiceProxy.DropInModuleListener listener = DropInServiceProxy.shared.getModuleListener();
        if (listener == null) {
            IllegalStateException e = new IllegalStateException("Invalid state: DropInModuleListener is missing");
            sendEvent(DID_FAILED, ReactNativeError.mapError(e));
            return;
        }

        if (success) {
            listener.onComplete(message);
        } else {
            listener.onFail(message);
        }
    }

    private void configureDropIn(DropInConfiguration.Builder builder, ReadableMap configuration) {
        DropInConfigurationParser parser = new DropInConfigurationParser(configuration);
        builder.setShowPreselectedStoredPaymentMethod(parser.getShowPreselectedStoredPaymentMethod());
        builder.setSkipListWhenSinglePaymentMethod(parser.getSkipListWhenSinglePaymentMethod());
    }

    private void configureGooglePay(String countryCode, DropInConfiguration.Builder builder, ReadableMap configuration) {
        GooglePayConfigurationParser parser = new GooglePayConfigurationParser(configuration);
        GooglePayConfiguration.Builder configBuilder = new GooglePayConfiguration.Builder(
                builder.getBuilderShopperLocale(),
                builder.getBuilderEnvironment(),
                builder.getBuilderClientKey())
                .setCountryCode(countryCode)
                .setAllowedCardNetworks(parser.getAllowedCardNetworks())
                .setAllowedAuthMethods(parser.getAllowedAuthMethods())
                .setAllowPrepaidCards(parser.getAllowPrepaidCards())
                .setBillingAddressRequired(parser.getBillingAddressRequired())
                .setEmailRequired(parser.getEmailRequired())
                .setShippingAddressRequired(parser.getShippingAddressRequired())
                .setExistingPaymentMethodRequired(parser.getExistingPaymentMethodRequired())
                .setGooglePayEnvironment(parser.getGooglePayEnvironment())
                .setMerchantAccount(parser.getMerchantAccount())
                .setAmount(builder.getAmount());
        configBuilder.setTotalPriceStatus(parser.getTotalPriceStatus());
        builder.addGooglePayConfiguration(configBuilder.build());
    }

    private void configure3DS(DropInConfiguration.Builder builder) {
        Adyen3DS2Configuration adyen3DS2Configuration;
        adyen3DS2Configuration = new Adyen3DS2Configuration.Builder(
                builder.getBuilderShopperLocale(),
                builder.getBuilderEnvironment(),
                builder.getBuilderClientKey())
                .build();
        builder.add3ds2ActionConfiguration(adyen3DS2Configuration);
    }

    private void configureBcmc(DropInConfiguration.Builder builder, ReadableMap configuration) {
        BcmcConfiguration bcmcConfiguration;
        ReadableMap bcmcConfig = configuration.getMap("bcmc");
        if (bcmcConfig == null) {
            bcmcConfig = new com.facebook.react.bridge.JavaOnlyMap();
        }

        CardConfigurationParser parser = new CardConfigurationParser(bcmcConfig);
        bcmcConfiguration = new BcmcConfiguration.Builder(
                builder.getBuilderShopperLocale(),
                builder.getBuilderEnvironment(),
                builder.getBuilderClientKey())
                .setShowStorePaymentField(parser.getShowStorePaymentField())
                .build();
        builder.addBcmcConfiguration(bcmcConfiguration);
    }

    private void configureCards(DropInConfiguration.Builder builder, ReadableMap configuration) {
        CardConfigurationParser parser = new CardConfigurationParser(configuration);
        CardConfiguration cardConfiguration;
        cardConfiguration = new CardConfiguration.Builder(
                builder.getBuilderShopperLocale(),
                builder.getBuilderEnvironment(),
                builder.getBuilderClientKey())
                .setShowStorePaymentField(parser.getShowStorePaymentField())
                .setHideCvcStoredCard(parser.getHideCvcStoredCard())
                .setHideCvc(parser.getHideCvc())
                .setHolderNameRequired(parser.getHolderNameRequired())
                .setAddressVisibility(parser.getAddressVisibility())
                .setKcpAuthVisibility(parser.getKcpVisibility())
                .build();
        builder.addCardConfiguration(cardConfiguration);
    }
}
