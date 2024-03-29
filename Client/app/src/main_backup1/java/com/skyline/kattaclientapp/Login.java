package com.skyline.kattaclientapp;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.TargetApi;
import android.app.LoaderManager.LoaderCallbacks;
import android.content.CursorLoader;
import android.content.Intent;
import android.content.Loader;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.support.annotation.NonNull;
import android.support.design.widget.Snackbar;
import android.support.design.widget.TextInputLayout;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import android.util.Patterns;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static android.Manifest.permission.READ_CONTACTS;

/**
 * A login screen that offers login via email/password.
 */
public class Login extends AppCompatActivity implements LoaderCallbacks<Cursor>, AsyncTaskComplete {

    /**
     * Id to identity READ_CONTACTS permission request.
     */
    private static final int REQUEST_READ_CONTACTS = 0;

     /* Keep track of the login task to ensure we can cancel it if requested.
     */
    //private UserLoginTask mAuthTask = null;

    private SharedPreferences sharedPreferences;
    private ActionHandler actionHandler;

    // UI references.
    private AutoCompleteTextView mEmailView;
    private EditText mPasswordView;
    private View mLoginFormView;
    private TextInputLayout lNameLayoutPassword;
    private TextInputLayout lNameLayoutEmail;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        sharedPreferences = getSharedPreferences("ClientApp", MODE_PRIVATE);
        actionHandler = new ActionHandler(this, Login.this);
        if (!sharedPreferences.getString("email", "").isEmpty())
            actionHandler.logout(sharedPreferences.getString("email", ""));

        // Set up the login form.
        TextView goToSignUp = (TextView) findViewById(R.id.go_to_signUp);
        goToSignUp.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(Login.this, SignupActivity.class);
                startActivity(intent);
            }
        });

        TextView resendEmail = (TextView) findViewById(R.id.resend_email);
        resendEmail.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                String email = mEmailView.getText().toString();
                boolean cancel = false;

                if (TextUtils.isEmpty(email)) {
                    lNameLayoutEmail.setError(getString(R.string.error_field_required));
                    cancel = true;
                } else if (!isEmailValid(email)) {
                    lNameLayoutEmail.setError(getString(R.string.error_invalid_email));
                    cancel = true;
                }
                if (!cancel) {
                    actionHandler.resendEmail(email);
                } else {
                    mEmailView.requestFocus();
                }
            }
        });

        TextView changePassword = (TextView) findViewById(R.id.change_password);
        changePassword.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                String email = mEmailView.getText().toString();
                boolean cancel = false;

                if (TextUtils.isEmpty(email)) {
                    lNameLayoutEmail.setError(getString(R.string.error_field_required));
                    cancel = true;
                } else if (!isEmailValid(email)) {
                    lNameLayoutEmail.setError(getString(R.string.error_invalid_email));
                    cancel = true;
                }
                if (!cancel) {
                    actionHandler.changePassword(email);
                } else {
                    mEmailView.requestFocus();
                }
            }
        });

        mEmailView = (AutoCompleteTextView) findViewById(R.id.email);
        mEmailView.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == R.id.goToPassword || actionId == EditorInfo.IME_NULL) {
                    mPasswordView.requestFocus();
                    return true;
                }
                return false;
            }
        });
        populateAutoComplete();

        lNameLayoutPassword = (TextInputLayout) findViewById(R.id.TextLayoutPassword);
        lNameLayoutEmail = (TextInputLayout) findViewById(R.id.TextLayoutEmail);

        //lNameLayoutPassword.setErrorEnabled(true);
        //lNameLayoutPassword.setError("Lol, just checking");

        mPasswordView = (EditText) findViewById(R.id.password);
        mPasswordView.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView textView, int id, KeyEvent keyEvent) {
                if (id == R.id.login || id == EditorInfo.IME_NULL) {
                    attemptLogin();
                    return true;
                }
                return false;
            }
        });

        Button mEmailSignInButton = (Button) findViewById(R.id.email_sign_in_button);
        mEmailSignInButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                attemptLogin();
            }
        });

        mLoginFormView = findViewById(R.id.email_login_form);
        //  mProgressView = findViewById(R.id.login_progress);
    }

    private void populateAutoComplete() {
        if (!mayRequestContacts()) {
            return;
        }

        getLoaderManager().initLoader(0, null, this);
    }

    private boolean mayRequestContacts() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true;
        }
        if (checkSelfPermission(READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
            return true;
        }
        if (shouldShowRequestPermissionRationale(READ_CONTACTS)) {
            Snackbar.make(mEmailView, R.string.permission_rationale, Snackbar.LENGTH_INDEFINITE)
                    .setAction(android.R.string.ok, new View.OnClickListener() {
                        @Override
                        @TargetApi(Build.VERSION_CODES.M)
                        public void onClick(View v) {
                            requestPermissions(new String[]{READ_CONTACTS}, REQUEST_READ_CONTACTS);
                        }
                    });
        } else {
            requestPermissions(new String[]{READ_CONTACTS}, REQUEST_READ_CONTACTS);
        }
        return false;
    }

    /**
     * Callback received when a permissions request has been completed.
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (requestCode == REQUEST_READ_CONTACTS) {
            if (grantResults.length == 1 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                populateAutoComplete();
            }
        }
    }


    /**
     * Attempts to sign in or register the account specified by the login form.
     * If there are form errors (invalid email, missing fields, etc.), the
     * errors are presented and no actual login attempt is made.
     */
    private void attemptLogin() {
        /*if (mAuthTask != null) {
            return;
        }*/
        // Reset errors.
        mEmailView.setError(null);
        mPasswordView.setError(null);
        lNameLayoutEmail.setError(null);
        lNameLayoutPassword.setError(null);

        // Store values at the time of the login attempt.
        String email = mEmailView.getText().toString();
        String password = mPasswordView.getText().toString();

        boolean cancel = false;
        View focusView = null;

        // Check for a valid password, if the user entered one.
        if (!TextUtils.isEmpty(password) && !isPasswordValid(password)) {
            lNameLayoutPassword.setError(getString(R.string.error_invalid_password));
            focusView = mPasswordView;
            cancel = true;
        }

        // Check for a valid email address.
        if (TextUtils.isEmpty(email)) {
            lNameLayoutEmail.setError(getString(R.string.error_field_required));
            focusView = mEmailView;
            cancel = true;
        } else if (!isEmailValid(email)) {
            lNameLayoutEmail.setError(getString(R.string.error_invalid_email));
            focusView = mEmailView;
            cancel = true;
        }

        if (cancel) {
            // There was an error; don't attempt login and focus the first
            // form field with an error.
            focusView.requestFocus();
        } else {
            // Show a progress spinner, and kick off a background task to
            // perform the user login attempt.
            actionHandler.login(email, password);
            showProgress(true);
            //        mAuthTask = new UserLoginTask(email, password);
            //        mAuthTask.execute((Void) null);
        }
    }

    private boolean isEmailValid(String email) {
        return (Patterns.EMAIL_ADDRESS.matcher(email).matches());
    }

    private boolean isPasswordValid(String password) {
        return password.length() > 7;
    }

    /**
     * Shows the progress UI and hides the login form.
     */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB_MR2)
    private void showProgress(final boolean show) {
        // On Honeycomb MR2 we have the ViewPropertyAnimator APIs, which allow
        // for very easy animations. If available, use these APIs to fade-in
        // the progress spinner.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB_MR2) {
            int shortAnimTime = getResources().getInteger(android.R.integer.config_shortAnimTime);

            mLoginFormView.setVisibility(show ? View.GONE : View.VISIBLE);
            mLoginFormView.animate().setDuration(shortAnimTime).alpha(
                    show ? 0 : 1).setListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    mLoginFormView.setVisibility(show ? View.GONE : View.VISIBLE);
                }
            });

            /*mProgressView.setVisibility(show ? View.VISIBLE : View.GONE);
            mProgressView.animate().setDuration(shortAnimTime).alpha(
                    show ? 1 : 0).setListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    mProgressView.setVisibility(show ? View.VISIBLE : View.GONE);
                }
            });*/
        } else {
            // The ViewPropertyAnimator APIs are not available, so simply show
            // and hide the relevant UI components.
            //mProgressView.setVisibility(show ? View.VISIBLE : View.GONE);
            mLoginFormView.setVisibility(show ? View.GONE : View.VISIBLE);
        }
    }

    @Override
    public Loader<Cursor> onCreateLoader(int i, Bundle bundle) {
        return new CursorLoader(this,
                // Retrieve data rows for the device user's 'profile' contact.
                Uri.withAppendedPath(ContactsContract.Profile.CONTENT_URI,
                        ContactsContract.Contacts.Data.CONTENT_DIRECTORY), ProfileQuery.PROJECTION,

                // Select only email addresses.
                ContactsContract.Contacts.Data.MIMETYPE +
                        " = ?", new String[]{ContactsContract.CommonDataKinds.Email
                .CONTENT_ITEM_TYPE},

                // Show primary email addresses first. Note that there won't be
                // a primary email address if the user hasn't specified one.
                ContactsContract.Contacts.Data.IS_PRIMARY + " DESC");
    }

    @Override
    public void onLoadFinished(Loader<Cursor> cursorLoader, Cursor cursor) {
        List<String> emails = new ArrayList<>();
        cursor.moveToFirst();
        while (!cursor.isAfterLast()) {
            emails.add(cursor.getString(ProfileQuery.ADDRESS));
            cursor.moveToNext();
        }

        addEmailsToAutoComplete(emails);
    }

    @Override
    public void onLoaderReset(Loader<Cursor> cursorLoader) {

    }

    private void addEmailsToAutoComplete(List<String> emailAddressCollection) {
        //Create adapter to tell the AutoCompleteTextView what to show in its dropdown list.
        ArrayAdapter<String> adapter =
                new ArrayAdapter<>(Login.this,
                        android.R.layout.simple_dropdown_item_1line, emailAddressCollection);

        mEmailView.setAdapter(adapter);
    }

    @Override
    public void handleResult(JsonObject result, String action) throws JSONException {
        if (result.get("success").getAsInt() == -1) {
            Snackbar snackbar = Snackbar.make(findViewById(R.id.login_root), "No connection", Snackbar.LENGTH_LONG);
            snackbar.show();
            if (action.equals("Login"))
                showProgress(false);
            return;
        }
        switch (action) {
            case "Login":
                if (result.get("success").getAsInt() == 1) {
                    for (Map.Entry<String, JsonElement> entry : result.entrySet()) {
                        sharedPreferences.edit().putString(entry.getKey(), entry.getValue().getAsString()).apply();
                    }
                    actionHandler.setFirebaseID(sharedPreferences.getString("email", ""));
                    FirebaseMessaging.getInstance().subscribeToTopic("clients");
                    subscribetopics(true);
                    sharedPreferences=getSharedPreferences("ClientApp", MODE_PRIVATE);
                    /*int count = sharedPreferences.getInt("count",1);
                    if(count == 1){
                        startActivity(new Intent(this,App_intro.class));
                    }*/
                    Intent intent = new Intent(this, App_intro.class);
                    intent.putExtra("is_called_from_login",true);
                    startActivity(intent);
                    finish();
                    //LoginHandler.setemail(email.getText().toString(), getApplicationContext());
                } else {
                    showProgress(false);
                    if (result.get("message").getAsString().toLowerCase().contains("email")) {
                        lNameLayoutEmail.setErrorEnabled(true);
                        lNameLayoutEmail.setError(result.get("message").getAsString());
                    } else {
                        lNameLayoutPassword.setErrorEnabled(true);
                        lNameLayoutPassword.setError(result.get("message").getAsString());
                    }
                }
                break;
            case "Reverify":
                if (result.get("success").getAsInt() == 1) {
                    Toast.makeText(Login.this, "Verification email sent!", Toast.LENGTH_SHORT).show();
                } else {
                    lNameLayoutEmail.setError(result.get("message").getAsString());
                }
                break;
            case "ChangePass":
                if (result.get("success").getAsInt() == 1) {
                    Toast.makeText(Login.this, "Password reset email sent!", Toast.LENGTH_SHORT).show();
                } else {
                    lNameLayoutEmail.setError(result.get("message").getAsString());
                }
                break;
            case "Logout":
                if (result.get("success").getAsInt() == 1) {
                    subscribetopics(false);
                    String fid = sharedPreferences.getString("FirebaseID", "");
                    sharedPreferences.edit().clear().apply();
                    sharedPreferences.edit().putString("FirebaseID", fid).apply();
                }
                break;
        }
    }

    private void subscribetopics(boolean flag) {
        String year = sharedPreferences.getString("year", "");
        String branch = sharedPreferences.getString("branch", "");
        String block = sharedPreferences.getString("block", "");
        year = year.replace(" ", "_");
        branch = branch.replace(" ", "_");
        if (flag) {
            FirebaseMessaging.getInstance().subscribeToTopic("clients_" + year);
            FirebaseMessaging.getInstance().subscribeToTopic("clients_" + branch);
            FirebaseMessaging.getInstance().subscribeToTopic("clients_" + year + "_" + branch);
            FirebaseMessaging.getInstance().subscribeToTopic("block_" + block);
            Log.d("ClintApp", "clients_" + year + "_" + branch);
            Log.d("ClintApp", "clients_" + year);
            Log.d("ClintApp", "clients_" + branch);
        } else {
            FirebaseMessaging.getInstance().unsubscribeFromTopic("clients_" + year);
            FirebaseMessaging.getInstance().unsubscribeFromTopic("clients_" + branch);
            FirebaseMessaging.getInstance().unsubscribeFromTopic("clients_" + year + "_" + branch);
            FirebaseMessaging.getInstance().unsubscribeFromTopic("block_" + block);
            Log.d("ClintApp", "clients_" + year + "_" + branch);
            Log.d("ClintApp", "clients_" + year);
            Log.d("ClintApp", "clients_" + branch);
        }
    }

    /**
     * public class UserLoginTask extends AsyncTask<Void, Void, Boolean> {
     * <p/>
     * private final String mEmail;
     * private final String mPassword;
     * <p/>
     * UserLoginTask(String email, String password) {
     * mEmail = email;
     * mPassword = password;
     * }
     *
     * @Override protected Boolean doInBackground(Void... params) {
     * // TODO: attempt authentication against a network service.
     * <p/>
     * try {
     * //ActionHandler actionHandler = new ActionHandler(this,this);
     * //actionHandler.
     * // Simulate network access.
     * Thread.sleep(2000);
     * } catch (InterruptedException e) {
     * return false;
     * }
     * <p/>
     * for (String credential : DUMMY_CREDENTIALS) {
     * String[] pieces = credential.split(":");
     * if (pieces[0].equals(mEmail)) {
     * // Account exists, return true if the password matches.
     * return pieces[1].equals(mPassword);
     * }
     * }
     * <p/>
     * // TODO: register the new account here.
     * return true;
     * }
     * @Override protected void onPostExecute(final Boolean success) {
     * mAuthTask = null;
     * //  showProgress(false);
     * <p/>
     * if (success) {
     * //finish();
     * } else {
     * mPasswordView.setError(getString(R.string.error_incorrect_password));
     * mPasswordView.requestFocus();
     * }
     * }
     * @Override protected void onCancelled() {
     * mAuthTask = null;
     * showProgress(false);
     * }
     * }
     */

    @Override
    public void onBackPressed() {
        finish();
    }

    /**
     * Represents an asynchronous login/registration task used to authenticate
     * the user.
     */
    private interface ProfileQuery {
        String[] PROJECTION = {
                ContactsContract.CommonDataKinds.Email.ADDRESS,
                ContactsContract.CommonDataKinds.Email.IS_PRIMARY,
        };

        int ADDRESS = 0;
        int IS_PRIMARY = 1;
    }

}
