package uk.co.reallysmall.cordova.plugin.firestore;


import android.support.annotation.NonNull;
import android.util.Log;
import android.webkit.ValueCallback;
import android.webkit.WebView;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.Transaction;

import org.apache.cordova.CallbackContext;
import org.json.JSONArray;
import org.json.JSONException;

public class RunTransactionHandler implements ActionHandler {

    private FirestorePlugin firestorePlugin;

    public RunTransactionHandler(FirestorePlugin firestorePlugin) {
        this.firestorePlugin = firestorePlugin;
    }

    @Override
    public boolean handle(JSONArray args, final CallbackContext callbackContext) {
        try {
            final String transactionId = args.getString(0);

            Log.d(FirestorePlugin.TAG, "Running transaction");

            try {
                firestorePlugin.getDatabase().runTransaction(new Transaction.Function<String>() {
                    @Override
                    public String apply(Transaction transaction) throws FirebaseFirestoreException {

                        Log.d(FirestorePlugin.TAG, String.format("Applying transaction %s", transactionId));

                        firestorePlugin.storeTransaction(transactionId, transaction);
                        TransactionWrapper transactionWrapper = firestorePlugin.getTransaction();

                        Runnable runnable = new Runnable() {
                            @Override
                            public void run() {

                                WebView wv = (WebView)firestorePlugin.webView.getView();
                                wv.evaluateJavascript(String.format("Firestore.__executeTransaction('%s');", transactionId), new ValueCallback<String>() {
                                    @Override
                                    public void onReceiveValue(String value) {
                                    }
                                });
                            }
                        };

                        synchronized (transactionWrapper.sync) {
                            firestorePlugin.cordova.getActivity().runOnUiThread(runnable);

                            try {
                                transactionWrapper.sync.wait();
                                Log.d(FirestorePlugin.TAG, String.format("Sync result complete for transaction %s", transactionId));
                            } catch (InterruptedException e) {
                                Log.w(FirestorePlugin.TAG, "Transaction failure whilst waiting", e);
                            }
                        }
                        firestorePlugin.removeTransaction();
                        Log.d(FirestorePlugin.TAG, String.format("Returning transaction %s result %s", transactionId, transactionWrapper.sync.toString()));
                        return transactionWrapper.sync.toString();
                    }
                }).addOnSuccessListener(new OnSuccessListener<String>() {
                    @Override
                    public void onSuccess(String result) {
                        callbackContext.success(result);
                        Log.d(FirestorePlugin.TAG, "Transaction success");
                    }
                }).addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Log.w(FirestorePlugin.TAG, "Transaction failure", e);
                        callbackContext.error(e.getMessage());
                    }
                });

            } catch (Exception e) {
                Log.e(FirestorePlugin.TAG, "Error running transaction", e);
                callbackContext.error(e.getMessage());
            }

        } catch (JSONException e) {
            Log.e(FirestorePlugin.TAG, "Error running transaction", e);
            callbackContext.error(e.getMessage());
        }

        return true;
    }
}