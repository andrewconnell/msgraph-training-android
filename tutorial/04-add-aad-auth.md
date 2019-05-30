<!-- markdownlint-disable MD002 MD041 -->

In this exercise you will extend the application from the previous exercise to support authentication with Azure AD. This is required to obtain the necessary OAuth access token to call the Microsoft Graph. In this step you will integrate the [Microsoft Authentication Library (MSAL) for Android](https://github.com/AzureAD/microsoft-authentication-library-for-android) into the application.

## Add MSAL dependencies to the project

1. Within Android Studio, expand **Gradle Scripts**, then open the **build.gradle (Module: app)** file.
1. Add the following lines inside the `dependencies` value:

    ```Gradle
    implementation 'com.microsoft.identity.client:msal:0.2.2
    ```

1. Save your change and within Android Studio, select **File > Sync Project with Gradle Files**.

1. Add authentication configuration values to the app:
    1. Right-click the **app/res/values** folder and select **New**, then **Values resource file**. Name the file `oauth_strings` and select **OK**.
    1. Add the following values to the `<resources>` element:

        ```xml
        <string name="oauth_app_id">YOUR_APP_ID_HERE</string>
        <string name="oauth_redirect_uri">msalYOUR_APP_ID_HERE</string>
        <string-array name="oauth_scopes">
            <item>User.Read</item>
            <item>Calendars.Read</item>
        </string-array>
        ```

    1. Update the values of the `oauth_app_id` & `oauth_redirect_uri` where it says **YOUR_APP_ID_HERE** with the values you copied from the Azure AD admin portal in the previous step.

        > [!NOTE]
        > If the redirect URI contains `://auth` at the end of it, remove it. The resulting file should look similar to the following:
        > 
        > ![Screenshot of the oauth_strings resource file](./images/aad-android-auth-settings.png)

> [!IMPORTANT]
> If you're using source control such as git, now would be a good time to exclude the `oauth_strings.xml` file from source control to avoid inadvertently leaking your app ID.

## Implement sign-in

1. Expand the **app/manifests** folder and open **AndroidManifest.xml**.
1. Add the following elements above the `<application>` element.

    ```xml
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
    ```

    These permissions are required in order for the MSAL library to authenticate the user.

1. Now add the following element inside the `<application>` element:

    ```xml
    <activity android:name="com.microsoft.identity.client.BrowserTabActivity">
        <intent-filter>
            <action android:name="android.intent.action.VIEW" />

            <category android:name="android.intent.category.DEFAULT" />
            <category android:name="android.intent.category.BROWSABLE" />

            <data
                android:host="auth"
                android:scheme="@string/oauth_redirect_uri" />
        </intent-filter>
    </activity>
    ```

    This allows MSAL to use a browser to authenticate the user, and registers your redirect URI as being handled by the app.

### Implement an authentication helper

1. Create a new authentication help class:
    1. Right-click the **app/java/com.microsoft.graphtutorial** folder and select **New**, then **Java Class**.
    1. Name the class `AuthenticationHelper`.
    1. Select **OK**.
1. Replace the contents of the **AuthenticationHelper** file with the following code:

    ```java
    package com.microsoft.graphtutorial;

    import android.app.Activity;
    import android.content.Context;
    import android.content.Intent;

    import com.microsoft.identity.client.AuthenticationCallback;
    import com.microsoft.identity.client.IAccount;
    import com.microsoft.identity.client.PublicClientApplication;

    // Singleton class - the app only needs a single instance
    // of PublicClientApplication
    public class AuthenticationHelper {
        private static AuthenticationHelper INSTANCE = null;
        private PublicClientApplication mPCA = null;
        private String[] mScopes;

        private AuthenticationHelper(Context ctx) {
            String appId = ctx.getResources().getString(R.string.oauth_app_id);
            mScopes = ctx.getResources().getStringArray(R.array.oauth_scopes);
            mPCA = new PublicClientApplication(ctx, appId);
        }

        public static synchronized AuthenticationHelper getInstance(Context ctx) {
            if (INSTANCE == null) {
                INSTANCE = new AuthenticationHelper(ctx);
            }

            return INSTANCE;
        }

        // Version called from fragments. Does not create an
        // instance if one doesn't exist
        public static synchronized AuthenticationHelper getInstance() {
            if (INSTANCE == null) {
                throw new IllegalStateException(
                        "AuthenticationHelper has not been initialized from MainActivity");
            }

            return INSTANCE;
        }

        public boolean hasAccount() {
            return !mPCA.getAccounts().isEmpty();
        }

        public void handleRedirect(int requestCode, int resultCode, Intent data) {
            mPCA.handleInteractiveRequestRedirect(requestCode, resultCode, data);
        }

        public void acquireTokenInteractively(Activity activity, AuthenticationCallback callback) {
            mPCA.acquireToken(activity, mScopes, callback);
        }

        public void acquireTokenSilently(AuthenticationCallback callback) {
            mPCA.acquireTokenSilentAsync(mScopes, mPCA.getAccounts().get(0), callback);
        }

        public void signOut() {
            for (IAccount account : mPCA.getAccounts()) {
                mPCA.removeAccount(account);
            }
        }
    }
    ```

1. Update `MainActivity` to use this new class.
    1. Open **MainActivity** from the **app/java/com.microsoft.graphtutorial** folder.
    1. Add the following `import` statements to the top of the **MainActivity** file.

        ```java
        import android.content.Intent;
        import android.support.annotation.Nullable;
        import android.util.Log;

        import com.microsoft.identity.client.AuthenticationCallback;
        import com.microsoft.identity.client.AuthenticationResult;
        import com.microsoft.identity.client.exception.MsalClientException;
        import com.microsoft.identity.client.exception.MsalException;
        import com.microsoft.identity.client.exception.MsalServiceException;
        import com.microsoft.identity.client.exception.MsalUiRequiredException;
        ```

1. Add the following member property to the `MainActivity` class.

    ```java
    private AuthenticationHelper mAuthHelper = null;
    ```

1. Update `onCreate()` to set `mAuthHelper`.
    1. Add the following to the end of the `onCreate()` function.

        ```java
        // Get the authentication helper
        mAuthHelper = AuthenticationHelper.getInstance(getApplicationContext());
        ```

1. Add an override for `onActivityResult` to handle authentication responses. Add the following code to the `MainActivity` class:

    ```java
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        mAuthHelper.handleRedirect(requestCode, resultCode, data);
    }
    ```

1. Add the following functions to the `MainActivity` class. These will implement the sign in and sign out functionally in the application.

    ```java
    // Silently sign in - used if there is already a
    // user account in the MSAL cache
    private void doSilentSignIn() {
        mAuthHelper.acquireTokenSilently(getAuthCallback());
    }

    // Prompt the user to sign in
    private void doInteractiveSignIn() {
        mAuthHelper.acquireTokenInteractively(this, getAuthCallback());
    }

    // Handles the authentication result
    public AuthenticationCallback getAuthCallback() {
        return new AuthenticationCallback() {

            @Override
            public void onSuccess(AuthenticationResult authenticationResult) {
                // Log the token for debug purposes
                String accessToken = authenticationResult.getAccessToken();
                Log.d("AUTH", String.format("Access token: %s", accessToken));
                hideProgressBar();

                setSignedInState(true);
                openHomeFragment(mUserName);
            }

            @Override
            public void onError(MsalException exception) {
                // Check the type of exception and handle appropriately
                if (exception instanceof MsalUiRequiredException) {
                    Log.d("AUTH", "Interactive login required");
                    doInteractiveSignIn();

                } else if (exception instanceof MsalClientException) {
                    // Exception inside MSAL, more info inside MsalError.java
                    Log.e("AUTH", "Client error authenticating", exception);
                } else if (exception instanceof MsalServiceException) {
                    // Exception when communicating with the auth server, likely config issue
                    Log.e("AUTH", "Service error authenticating", exception);
                }
                hideProgressBar();
            }

            @Override
            public void onCancel() {
                // User canceled the authentication
                Log.d("AUTH", "Authentication canceled");
                hideProgressBar();
            }
        };
    }
    ```

1. Replace the existing `signIn()` and `signOut()` functions with the following implementations:

    ```java
    private void signIn() {
        showProgressBar();
        if (mAuthHelper.hasAccount()) {
            doSilentSignIn();
        } else {
            doInteractiveSignIn();
        }
    }

    private void signOut() {
        mAuthHelper.signOut();

        setSignedInState(false);
        openHomeFragment(mUserName);
    }
    ```

    Notice that the `signIn` method first checks if there is a user account already in the MSAL cache. If there is, it attempts to refresh its tokens silently, avoiding having to prompt the user every time they launch the app.

1. Save your changes.

### Test the application

1. Run the app by selecting **Run > Run 'app'**.
1. When you tap the **Sign in** menu item, a browser opens to the Azure AD login page.

    ![Screenshot of logging into Azure AD from the Android app](./images/signin-01.png)

    Sign in with your account.

    ![Screenshot of logging into Azure AD from the Android app](./images/signin-02.png)

    Once the app resumes, you should see an access token printed in the debug log in Android Studio:

    ![A screenshot of the Logcat window in Android Studio](./images/debugger-access-token.png)

### Add Microsoft Graph Java SDK dependencies to the project

1. Within Android Studio, expand **Gradle Scripts**, then open the **build.gradle (Module: app)** file.
1. Add the following lines inside the `dependencies` value:

    ```Gradle
    implementation 'com.microsoft.graph:microsoft-graph:1.4.0'
    ```

1. Save your change and within Android Studio, select **File > Sync Project with Gradle Files**.

### Get user details

Use the Microsoft Graph to get the current user's details.

1. Start by creating a helper class to hold all of the calls to Microsoft Graph.
    1. Right-click the **app/java/com.microsoft.graphtutorial** folder and select **New**, then **Java Class**.
    1. Name the class `GraphHelper`.
    1. Select **OK**.
    1. Open the new file and replace its contents with the following:

        ```java
        package com.microsoft.graphtutorial;

        import com.microsoft.graph.authentication.IAuthenticationProvider;
        import com.microsoft.graph.concurrency.ICallback;
        import com.microsoft.graph.http.IHttpRequest;
        import com.microsoft.graph.models.extensions.IGraphServiceClient;
        import com.microsoft.graph.models.extensions.User;
        import com.microsoft.graph.requests.extensions.GraphServiceClient;

        // Singleton class - the app only needs a single instance
        // of the Graph client
        public class GraphHelper implements IAuthenticationProvider {
            private static GraphHelper INSTANCE = null;
            private IGraphServiceClient mClient = null;
            private String mAccessToken = null;

            private GraphHelper() {
                mClient = GraphServiceClient.builder()
                        .authenticationProvider(this).buildClient();
            }

            public static synchronized GraphHelper getInstance() {
                if (INSTANCE == null) {
                    INSTANCE = new GraphHelper();
                }

                return INSTANCE;
            }

            // Part of the Graph IAuthenticationProvider interface
            // This method is called before sending the HTTP request
            @Override
            public void authenticateRequest(IHttpRequest request) {
                // Add the access token in the Authorization header
                request.addHeader("Authorization", "Bearer " + mAccessToken);
            }

            public void getUser(String accessToken, ICallback<User> callback) {
                mAccessToken = accessToken;

                // GET /me (logged in user)
                mClient.me().buildRequest().get(callback);
            }
        }
        ```

        Note what this code does:

        - It implements the `IAuthenticationProvider` interface to insert the access token in the `Authorization` header on outgoing HTTP requests.
        - It exposes a `getUser` function to get the logged-in user's information from the `/me` Graph endpoint.

1. Now update the `MainActivity` class to use this new class to get the logged-in user.
    1. Open **MainActivity** from the **app/java/com.microsoft.graphtutorial** folder.
    1. Add the following `import` statements to the top of the **MainActivity** file, after the existing `import` statements:

        ```java
        import com.microsoft.graph.concurrency.ICallback;
        import com.microsoft.graph.core.ClientException;
        import com.microsoft.graph.models.extensions.IGraphServiceClient;
        import com.microsoft.graph.models.extensions.User;
        ```

    1. Add the following function to the `MainActivity` class to generate an `ICallback` for the Graph call:

        ```java
        private ICallback<User> getUserCallback() {
          return new ICallback<User>() {
            @Override
            public void success(User user) {
              Log.d("AUTH", "User: " + user.displayName);

              mUserName = user.displayName;
              mUserEmail = user.mail == null ? user.userPrincipalName : user.mail;

              runOnUiThread(new Runnable() {
                @Override
                public void run() {
                  hideProgressBar();

                  setSignedInState(true);
                  openHomeFragment(mUserName);
                }
              });

            }

            @Override
            public void failure(ClientException ex) {
              Log.e("AUTH", "Error getting /me", ex);
              mUserName = "ERROR";
              mUserEmail = "ERROR";

              runOnUiThread(new Runnable() {
                @Override
                public void run() {
                  hideProgressBar();

                  setSignedInState(true);
                  openHomeFragment(mUserName);
                }
              });
            }
          };
        }
        ```

    1. Remove the following lines that set the user name and email. These are found in the `setSignedInState()` function in the `MainActivity` class:

        ```java
        // For testing
        mUserName = "Megan Bowen";
        mUserEmail = "meganb@contoso.com";
        ```

    1. Finally, replace the `onSuccess()` function override in the `getAuthCallback()` function with the following:

        ```java
        @Override
        public void onSuccess(AuthenticationResult authenticationResult) {
          // Log the token for debug purposes
          String accessToken = authenticationResult.getAccessToken();
          Log.d("AUTH", String.format("Access token: %s", accessToken));

          // Get Graph client and get user
          GraphHelper graphHelper = GraphHelper.getInstance();
          graphHelper.getUser(accessToken, getUserCallback());
        }
        ```

    1. Save all changed files.

### Test the application

1. Run the app by selecting **Run > Run 'app'**.
1. Notice that when you signin this time, the fragment displays your name.
