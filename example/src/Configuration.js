import { Platform } from 'react-native';

const channel = Platform.select({
  ios: () => 'iOS',
  android: () => 'Android',
})();

export const defaultConfiguration = {
  environment: 'test',
  channel: channel,
  clientKey: '{YOUR_CLIENT_KEY}',
  countryCode: 'NL',
  amount: {
    currency: 'EUR',
    value: 1000 // The amount information for the transaction (in minor units). For BIN or card verification requests, set amount to 0 (zero).
  },
  reference: 'React Native',
  returnUrl: 'myapp://',
  shopperReference: 'Checkout Shopper',
  merchantAccount: '{YOUR_MERCHANT_ACCOUNT}',
  shopperLocale: 'en-US',
  additionalData: { allow3DS2: true },
  dropin: {
    skipListWhenSinglePaymentMethod: true,
    showPreselectedStoredPaymentMethod: false
  },
  card: {
    holderNameRequired: true,
    showStorePaymentField : false,
    hideCvcStoredCard: true,
    hideCvc: true,
    addressVisibility: `postalCode`
  },
  applepay: {
    // merchantID: 'merchant.com.adyen.MY_MERCHANT_ID',
    // merchantName: 'MY_MERCHANT'
  },
  googlepay: {

  },
  style: {
    // TODO: add styling
  }
};

export const environment = {
  apiKey:
    '{YOUR_DEMO_SERVER_API_KEY}',
  url: 'https://checkout-test.adyen.com/v67/',
};
