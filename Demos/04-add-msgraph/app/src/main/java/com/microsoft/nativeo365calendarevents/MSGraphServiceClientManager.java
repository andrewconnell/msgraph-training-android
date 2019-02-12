package com.microsoft.nativeo365calendarevents;

import android.content.Context;
import android.util.Log;

import com.microsoft.graph.authentication.IAuthenticationProvider;
import com.microsoft.graph.core.DefaultClientConfig;
import com.microsoft.graph.core.IClientConfig;
import com.microsoft.graph.requests.extensions.GraphServiceClient;
import com.microsoft.graph.models.extensions.IGraphServiceClient;
import com.microsoft.graph.http.IHttpRequest;

public class MSGraphServiceClientManager implements IAuthenticationProvider {

  private final static String TAG = MSGraphServiceClientManager.class.getSimpleName();
  private IGraphServiceClient graphClient;
  private static MSGraphServiceClientManager INSTANCE;
  private static Context context;

  @Override
  public void authenticateRequest(IHttpRequest request) {
    try {
      request.addHeader("Authorization", "Bearer "
              + AuthenticationController.getInstance(context)
              .getAccessToken());
    } catch (NullPointerException e) {
      e.printStackTrace();
    }
  }

  public static synchronized MSGraphServiceClientManager getInstance(Context ctx) {
    context = ctx;
    if (INSTANCE == null) {
      INSTANCE = new MSGraphServiceClientManager();
    }
    return INSTANCE;
  }

  public synchronized IGraphServiceClient getGraphServiceClient() {
    return getGraphServiceClient(this);
  }

  public synchronized IGraphServiceClient getGraphServiceClient(IAuthenticationProvider authenticationProvider) {
    if (graphClient == null){
      graphClient = GraphServiceClient.builder().authenticationProvider(authenticationProvider).buildClient();
    }
    return graphClient;
  }

}
