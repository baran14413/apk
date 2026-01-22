
package com.bematch.bematch;

import android.os.Bundle;
import android.util.Log;

import com.getcapacitor.BridgeActivity;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.ProductDetails;
import com.android.billingclient.api.QueryProductDetailsParams;
import com.android.billingclient.api.PurchasesUpdatedListener;
import com.google.common.collect.ImmutableList;


public class MainActivity extends BridgeActivity {
    private BillingPlugin billingPlugin;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Register the billing plugin
        billingPlugin = new BillingPlugin();
        registerPlugin(BillingPlugin.class);
    }
}

// Define the Capacitor Plugin for Billing
@CapacitorPlugin(name = "AppBilling")
class BillingPlugin extends Plugin {

    private BillingClient billingClient;
    public static final String TAG = "BillingPlugin";

    // Listener for purchase updates
    private final PurchasesUpdatedListener purchasesUpdatedListener = (billingResult, purchases) -> {
        if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK && purchases != null) {
            // Purchase successful. Handle entitlement and notify the web view.
            Log.d(TAG, "Purchase successful!");
            notifyListeners("purchaseSuccess", new JSObject().put("status", "success"));
        } else {
            // Handle other error cases or user cancellation
            Log.e(TAG, "Purchase failed or was cancelled. Response code: " + billingResult.getResponseCode());
            notifyListeners("purchaseFailed", new JSObject().put("error", billingResult.getDebugMessage()));
        }
    };

    @Override
    public void load() {
        super.load();

        // Initialize BillingClient and start the connection
        billingClient = BillingClient.newBuilder(getContext())
                .setListener(purchasesUpdatedListener)
                .enablePendingPurchases()
                .build();

        billingClient.startConnection(new BillingClientStateListener() {
            @Override
            public void onBillingSetupFinished(BillingResult billingResult) {
                if (billingResult.getResponseCode() ==  BillingClient.BillingResponseCode.OK) {
                    Log.d(TAG, "Google Play Billing is ready.");
                } else {
                    Log.e(TAG, "Google Play Billing setup failed: " + billingResult.getDebugMessage());
                }
            }
            @Override
            public void onBillingServiceDisconnected() {
                Log.w(TAG, "Google Play Billing service disconnected.");
            }
        });
    }

    // This method will be callable from your web app's JavaScript
    @PluginMethod()
    public void purchase(PluginCall call) {
        String productId = call.getString("productId");
        if (productId == null || productId.isEmpty()) {
            call.reject("Product ID is required.");
            return;
        }

        if (!billingClient.isReady()) {
            call.reject("Billing client is not ready.");
            return;
        }

        // Query for the product details
        QueryProductDetailsParams params = QueryProductDetailsParams.newBuilder()
            .setProductList(ImmutableList.of(QueryProductDetailsParams.Product.newBuilder()
                .setProductId(productId)
                .setProductType(BillingClient.ProductType.INAPP) // Or .SUBS for subscriptions
                .build()))
            .build();

        billingClient.queryProductDetailsAsync(params, (billingResult, productDetailsList) -> {
            if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK && productDetailsList != null && !productDetailsList.isEmpty()) {
                ProductDetails productDetails = productDetailsList.get(0);

                // Launch the billing flow
                BillingFlowParams billingFlowParams = BillingFlowParams.newBuilder()
                    .setProductDetailsParamsList(ImmutableList.of(BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(productDetails)
                        .build()))
                    .build();

                billingClient.launchBillingFlow(getActivity(), billingFlowParams);
                call.resolve(); // Let JS know the flow was launched
            } else {
                call.reject("Failed to query product: " + billingResult.getDebugMessage());
            }
        });
    }
}
