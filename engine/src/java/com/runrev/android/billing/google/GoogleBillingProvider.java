

package com.runrev.android.billing.google;

import com.runrev.android.billing.google.Purchase;
import com.runrev.android.billing.*;
import com.runrev.android.Engine;

import android.app.*;
import android.util.*;
import android.content.*;

import java.util.*;



public class GoogleBillingProvider implements BillingProvider
{
    public static final String TAG = "GoogleBillingProvider";
    private Activity mActivity;
    private Boolean started = false;
    private PurchaseObserver mPurchaseObserver;
    private Map<String,String> types = new HashMap<String,String>();
    private Map<String,Map<String,String>> itemProps = new HashMap<String, Map<String,String>>();
	
	private List<SkuDetails> knownItems = new ArrayList<SkuDetails>();
    private Set<String> ownedItems = new HashSet<String>();
    
    /* 
     Temp var for holding the productId, to pass it in onIabPurchaseFinished(IabResult result, Purchase purchase), in case purchase is null.
     Thus, purchase.getSku() will not work
    */
    private String pendingPurchaseSku = "";
    
    // (arbitrary) request code for the purchase flow
    static final int RC_REQUEST = 10001;
    
    // The helper object
    IabHelper mHelper = null;
    
    public void initBilling()
    {
        String t_public_key = Engine.doGetCustomPropertyValue("cREVStandaloneSettings", "android,storeKey");
        
        if (t_public_key != null && t_public_key.length() > 0)
            Security.setPublicKey(t_public_key);
        
        // Create the helper, passing it our context and the public key to verify signatures with
        Log.d(TAG, "Creating IAB helper.");
        mHelper = new IabHelper(getActivity(), t_public_key);
        
        // TODO enable debug logging (for a production application, you should set this to false).
        mHelper.enableDebugLogging(true);
        
        // Start setup. This is asynchronous and the specified listener
        // will be called once setup completes.
        Log.d(TAG, "Starting setup.");
        mHelper.startSetup(new IabHelper.OnIabSetupFinishedListener()
                           {
            public void onIabSetupFinished(IabResult result)
            {
                Log.d(TAG, "Setup finished.");
                
                if (!result.isSuccess())
                {
                    // Oh no, there was a problem.
                    complain("Problem setting up in-app billing: " + result);
                    return;
                }
                
                // Have we been disposed of in the meantime? If so, quit.
                if (mHelper == null) return;
                
                // IAB is fully set up.
                Log.d(TAG, "Setup successful.");
                
                mHelper.queryInventoryAsync(mGotInventoryListener);
            }
        });
    }
    
    public boolean canMakePurchase()
    {
        if (mHelper == null)
            return false;
        
        else
            return mHelper.is_billing_supported;
    }
    
    public boolean enableUpdates()
    {
        if (mHelper == null)
            return false;
        
        //TODO
        return true;
    }
    
    public boolean disableUpdates()
    {
        if (mHelper == null)
            return false;
        
        //TODO
        return true;
    }
    
    public boolean restorePurchases()
    {
        // Not sure if this is needed, since inventory is queried when the app is launched (initBilling())
        /*
         if (mHelper == null)
         {
         
         return false;
         }
         
         Log.d(TAG, "Querying inventory.");
         mHelper.queryInventoryAsync(mGotInventoryListener);
         */
        return true;
    }
    
    //public boolean sendRequest(int purchaseId, String productId, Map<String, String> properties)
    public boolean sendRequest(int purchaseId, String productId, String developerPayload)
    {
        if (mHelper == null)
            return false;
        
        String type = productGetType(productId);
        if (type == null)
        {
            Log.i(TAG, "Item type is null (not specified). Exiting..");
            return false;
        }
        
        pendingPurchaseSku = productId;
        
        Log.i(TAG, "purchaseSendRequest(" + purchaseId + ", " + productId + ", " + type + ")");
        
        if (type.equals("SUBS"))
        {
            mHelper.launchSubscriptionPurchaseFlow(getActivity(), productId, RC_REQUEST, mPurchaseFinishedListener, "");
        }
        else
        {
            mHelper.launchPurchaseFlow(getActivity(), productId, RC_REQUEST, mPurchaseFinishedListener, "");
        }
        return true;
    }
    
    public boolean makePurchase(String productId, String quantity, String payload)
    {
        if (mHelper == null)
            return false;
        
        String type = productGetType(productId);
        if (type == null)
        {
            Log.i(TAG, "Item type is null (not specified). Exiting..");
            return false;
        }
        
        pendingPurchaseSku = productId;
        
        Log.i(TAG, "purchaseSendRequest("  + productId + ", " + type + ")");
        
        if (type.equals("SUBS"))
        {
            mHelper.launchSubscriptionPurchaseFlow(getActivity(), productId, RC_REQUEST, mPurchaseFinishedListener, payload);
        }
        else
        {
            mHelper.launchPurchaseFlow(getActivity(), productId, RC_REQUEST, mPurchaseFinishedListener, payload);
        }
        return true;
    }
    
    public boolean productSetType(String productId, String productType)
    {
        //TODO
        Log.d(TAG, "Setting type for productId" + productId + ", type is : " + productType);
        types.put(productId, productType);
        Log.d(TAG, "Querying HashMap, type is " + types.get(productId));
        return true;
    }
    
    private String productGetType(String productId)
    {
        //TODO
        return types.get(productId);
    }
    
    public boolean consumePurchase(final String productId)
    {
        mHelper.queryInventoryAsync(new IabHelper.QueryInventoryFinishedListener()
                                    {
            @Override
            public void onQueryInventoryFinished(IabResult result, Inventory inventory)
            {
                
                if (result.isFailure())
                {
                    return;
                }
                else
                {
                    Purchase purchase = inventory.getPurchase(productId);
                    // Do this check to avoid a NullPointerException
                    if (purchase == null)
                    {
                        Log.d(TAG, "You cannot consume item : " + productId + ", since you don't own it!");
                        return;
                    }
                    mHelper.consumeAsync(purchase, mConsumeFinishedListener);
                }
            }
        });
        return true;
    }
    
    public boolean requestProductDetails(final String productId)
    {
        //arbitrary initial capacity
        int capacity = 25; 
        List<String> productList = new ArrayList<String>(capacity);
        productList.add(productId);
        mHelper.queryInventoryAsync(true, productList, new IabHelper.QueryInventoryFinishedListener()
                                    {
            @Override
            public void onQueryInventoryFinished(IabResult result, Inventory inventory)
            {
                
                if (result.isFailure())
                {
                    return;
                }
                else
                {
                    SkuDetails skuDetails = inventory.getSkuDetails(productId);
                    // Do this check to avoid a NullPointerException
                    if (skuDetails == null)
                    {
                        Log.d(TAG, "No product found with the specified ID : " + productId + " !");
                        return;
                    }
                   
					knownItems.add(skuDetails);
					loadKnownItemToLocalInventory(skuDetails);
                    Log.d(TAG, "Details for requested product : " + skuDetails.toString());
                    // TODO : Call productDetailsReceived 
                }
            }
        });
		
		return true;
    }
	
	public String receiveProductDetails(String productId)
    {
		for (SkuDetails skuDetails : knownItems)
		{
			if (productId.equals(skuDetails.getSku()))
			{
				return skuDetails.toString();
			}
		}
		
        return "Product ID not found";
	}
    
    public boolean confirmDelivery(int purchaseId)
    {
        if (mHelper == null)
            return false;
        
        else
            //TODO
            return false;
    }
    
    public void setPurchaseObserver(PurchaseObserver observer)
    {
        mPurchaseObserver = observer;
    }
    
    public Map<String, String> getPurchaseProperties(int purchaseId)
    {
        //TODO
        return new HashMap<String,String>();
    }
    
    Activity getActivity()
    {
        return mActivity;
    }
    
    public void setActivity(Activity activity)
    {
        mActivity = activity;
    }
    
    public boolean setPurchaseProperty(String productId, String propertyName, String propertyValue)
    {
        if (!itemProps.containsKey(productId))
            itemProps.put(productId, new HashMap<String,String>());
        (itemProps.get(productId)).put(propertyName, propertyValue);
                
        return true;
    }
    
    public String getPurchaseProperty(String productId, String propName)
    {
        Log.d(TAG, "Stored properties for productId :" + productId);
        Map<String,String> map = itemProps.get(productId);
        if (map != null)
            return map.get(propName);
        else
            return "";
    }
    
    public String getPurchaseList()
    {
        return ownedItems.toString();
    }
    
    //some helper methods
    
    boolean addPurchaseToLocalInventory(Purchase purchase)
    {
        boolean success = true;
        if (success)
            success = setPurchaseProperty(purchase.getSku(), "itemType", purchase.getItemType());
        
        if (success)
            success = setPurchaseProperty(purchase.getSku(), "orderId", purchase.getOrderId());
        
        if (success)
            success = setPurchaseProperty(purchase.getSku(), "packageName", purchase.getPackageName());

        if (success)
            success = setPurchaseProperty(purchase.getSku(), "purchaseToken", purchase.getToken());

        if (success)
            success = setPurchaseProperty(purchase.getSku(), "signature", purchase.getSignature());

        if (success)
            success = setPurchaseProperty(purchase.getSku(), "purchaseTime", new Long(purchase.getPurchaseTime()).toString());

        return success;

    }
	
	boolean loadKnownItemToLocalInventory(SkuDetails skuDetails)
	{
		boolean success = true;
        if (success)
            success = setPurchaseProperty(skuDetails.getSku(), "itemType", skuDetails.getType());
        
        if (success)
            success = setPurchaseProperty(skuDetails.getSku(), "price", skuDetails.getPrice());
        
        if (success)
            success = setPurchaseProperty(skuDetails.getSku(), "title", skuDetails.getTitle());
		
        if (success)
            success = setPurchaseProperty(skuDetails.getSku(), "description", skuDetails.getDescription());
		
        return success;
	}
    
    void removePurchaseFromLocalInventory(Purchase purchase)
    {
        ownedItems.remove(purchase.getSku());
        
    }
    
    // Enables or disables the "please wait" screen.
    void setWaitScreen(boolean set)
    {
        // Uncomment these lines after fixing the "R package not found " error
        // getActivity().findViewById(R.id.screen_main).setVisibility(set ? View.GONE : View.VISIBLE);
        //  getActivity().findViewById(R.id.screen_wait).setVisibility(set ? View.VISIBLE : View.GONE);
    }
    
    void complain(String message)
    {
        Log.e(TAG, "**** Error: " + message);
        alert("Error: " + message);
    }
    
    void alert(String message)
    {
        AlertDialog.Builder bld = new AlertDialog.Builder(getActivity());
        bld.setMessage(message);
        bld.setNeutralButton("OK", null);
        Log.d(TAG, "Showing alert dialog: " + message);
        bld.create().show();
    }
    
    // Listeners
    
    IabHelper.OnIabPurchaseFinishedListener mPurchaseFinishedListener = new IabHelper.OnIabPurchaseFinishedListener()
    {
        // parameter "purchase" is null if purchase failed
        public void onIabPurchaseFinished(IabResult result, Purchase purchase)
        {
            Log.d(TAG, "Purchase finished: " + result + ", purchase: " + purchase);
            
            // if we were disposed of in the meantime, quit.
            if (mHelper == null)
            {
                return;
            }
    
            if (result.isFailure())
            {
                complain("Error purchasing: " + result);
                setWaitScreen(false);
                mPurchaseObserver.onPurchaseStateChanged(pendingPurchaseSku, mapResponseCode(result.getResponse()));
                // TODO : Call void onPurchaseRequestError(String errorMessage) with input result.getMessage()
                pendingPurchaseSku = "";
                return;
            }
            
            if (!verifyDeveloperPayload(purchase))
            {
                complain("Error purchasing. Authenticity verification failed.");
                setWaitScreen(false);
                return;
            }
            
            Log.d(TAG, "Purchase successful.");
            pendingPurchaseSku = "";
			ownedItems.add(purchase.getSku());
            addPurchaseToLocalInventory(purchase);
            offerPurchasedItems(purchase);
                
        }
    };

    void offerPurchasedItems(Purchase purchase)
    {
        if (purchase != null)
            mPurchaseObserver.onPurchaseStateChanged(purchase.getSku(), mapResponseCode(purchase.getPurchaseState()));

    }

    // Called when consumption is complete
    IabHelper.OnConsumeFinishedListener mConsumeFinishedListener = new IabHelper.OnConsumeFinishedListener()
    {
        public void onConsumeFinished(Purchase purchase, IabResult result)
        {
            Log.d(TAG, "Consumption finished. Purchase: " + purchase + ", result: " + result);

            if (result.isSuccess())
            {
                Log.d(TAG, "Consumption successful. Provisioning.");
                removePurchaseFromLocalInventory(purchase);
            }
            else
            {
                complain("Error while consuming: " + result);
            }

            setWaitScreen(false);
            Log.d(TAG, "End consumption flow.");
        }
    };

    // Called when we finish querying the items we own
    IabHelper.QueryInventoryFinishedListener mGotInventoryListener = new IabHelper.QueryInventoryFinishedListener()
    {
        public void onQueryInventoryFinished(IabResult result, Inventory inventory)
        {
            Log.d(TAG, "Query inventory finished.");

            // Have we been disposed of in the meantime? If so, quit.
            if (mHelper == null)
                return;

            if (result.isFailure())
            {
                complain("Failed to query inventory: " + result);
                return;
            }

            Log.d(TAG, "Query inventory was successful.");

            setWaitScreen(false);
            Log.d(TAG, "Initial inventory query finished; enabling main UI.");

            List<Purchase> purchaseList = inventory.getallpurchases();
            for (Purchase p : purchaseList)
            {
                addPurchaseToLocalInventory(p);
				ownedItems.add(p.getSku());
                offerPurchasedItems(p);
            }
        }
    };

    /** Verifies the developer payload of a purchase. */
    boolean verifyDeveloperPayload(Purchase p)
    {
        String payload = p.getDeveloperPayload();

    /*
     * TODO: verify that the developer payload of the purchase is correct. It will be
     * the same one that you sent when initiating the purchase.
     *
     * WARNING: Locally generating a random string when starting a purchase and
     * verifying it here might seem like a good approach, but this will fail in the
     * case where the user purchases an item on one device and then uses your app on
     * a different device, because on the other device you will not have access to the
     * random string you originally generated.
     *
     * So a good developer payload has these characteristics:
     *
     * 1. If two different users purchase an item, the payload is different between them,
     *    so that one user's purchase can't be replayed to another user.
     *
     * 2. The payload must be such that you can verify it even when the app wasn't the
     *    one who initiated the purchase flow (so that items purchased by the user on
     *    one device work on other devices owned by the user).
     *
     * Using your own server to store and verify developer payloads across app
     * installations is recommended.
     */

        return true;
    }

    public void onActivityResult (int requestCode, int resultCode, Intent data)
    {
        Log.d(TAG, "onActivityResult(" + requestCode + "," + resultCode + "," + data);

        // Pass on the activity result to the helper for handling
        if (!mHelper.handleActivityResult(requestCode, resultCode, data))
        {
            // not handled
            Log.d(TAG, "onActivityResult NOT handled by IABUtil.");
        }
        else
        {
            Log.d(TAG, "onActivityResult handled by IABUtil.");
        }
    }

    // Should match the order of enum MCAndroidPurchaseState (mblandroidstore.cpp)
    int mapResponseCode(int responseCode)
    {
        int result;
        switch(responseCode)
        {
            case IabHelper.BILLING_RESPONSE_RESULT_OK:
                result = 0;
                break;

            case IabHelper.BILLING_RESPONSE_RESULT_USER_CANCELED:
                result = 1;
                break;

            case IabHelper.BILLING_RESPONSE_RESULT_ITEM_ALREADY_OWNED:
                result = 3;
                break;
            default:
                result = 1;
                break;
        }
        return result;
    }

}