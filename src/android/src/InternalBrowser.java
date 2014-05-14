package welldonesoftware.cordova.plugins.internalBrowser;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.net.Uri;
import android.net.http.SslError;
import android.text.InputType;
import android.util.Log;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.CookieSyncManager;
import android.webkit.HttpAuthHandler;
import android.webkit.JsResult;
import android.webkit.SslErrorHandler;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;

@SuppressLint("SetJavaScriptEnabled")
public class InternalBrowser extends CordovaPlugin {
	
	private static final String ALERT_TO_RETURN = "___ALERT_TO_RETURN_";

	@Override
    public boolean execute(String action, JSONArray argsArr, CallbackContext callbackContext) {		
		try {
			
			JSONObject args = argsArr.length() > 0 ?
					argsArr.getJSONObject(0) : new JSONObject();
					
			if (action.equals("show")){
	            this.show(callbackContext, args);
	        }
			else if (action.equals("dismiss")){
	            this.dismiss(callbackContext, args);
	        }
			else if (action.equals("refresh")) {
				this.refresh(callbackContext, args);
	        }
	        else {
	            return false;
	        }
			
        } catch (Exception e) {
			e.printStackTrace();
			callbackContext.error(e.getMessage());
		}
      
        return true;
    }
	
	private static String TAG = "InternalBrowser";
	
	private MainDialogRunnable internalBrowserThreadInstance = null;
	
	public void dismiss(CallbackContext callbackContext, JSONObject args) throws Exception{
		if(internalBrowserThreadInstance == null){
			return;
		}
		
		internalBrowserThreadInstance.closeDialog();
	}
	
	public void refresh(CallbackContext callbackContext, JSONObject args) throws Exception{
		if(internalBrowserThreadInstance == null){
			return;
		}
		
		//TODO change url;
		internalBrowserThreadInstance.refresh();
	}
	
	public void show(CallbackContext callbackContext, JSONObject args) throws Exception
	{
		InternalBrowserOptions options = new InternalBrowserOptions(args);
		
		internalBrowserThreadInstance = new MainDialogRunnable(options, callbackContext);
		
		cordova.getActivity().runOnUiThread(internalBrowserThreadInstance);
	}
	
	class MainDialogRunnable implements Runnable {
		private final InternalBrowserOptions options;
		private final CallbackContext callbackContext;
		
		private JSONObject response;
		private String loadingUrl;

		private Resources resources;
		private String packageName;
		
		private WebView webview;
		private Dialog dialog;
		private ProgressBar progressBar;
		
		private int getIdentifier(String name, String defType){
			return resources.getIdentifier(name, defType, packageName);
		}
		
		public MainDialogRunnable(final InternalBrowserOptions options, final CallbackContext callbackContext){
			this.response = new JSONObject();
			this.options = options;
			this.callbackContext = callbackContext;			
			this.resources = cordova.getActivity().getApplicationContext().getResources();                       
            this.packageName = cordova.getActivity().getApplicationContext().getPackageName();
		}
		
		public void closeDialog(){
			try{
				Log.d(TAG, "closing InternalBrowser on cancel button.");
				response.put("isDone", true);
				dialog.dismiss();
			}
			catch (JSONException e){
				e.printStackTrace();
				Log.e(TAG, "Couldn't edit the response");
			}
		}
		
		public void closeDialogByUrl(String url){
			try{
				Log.d(TAG, "closing InternalBrowser with result url:" + url);
				response.put("closedByURL", url);
				dialog.dismiss();
			}
			catch (JSONException e){
				e.printStackTrace();
				Log.e(TAG, "Couldn't edit the response");
			}
		}
		
		public void closeDialogByEvaluatedScript(String evaluatedScript){
			try{
				Log.d(TAG, "closing InternalBrowser with evaluated script:" + evaluatedScript);
				response.put("evaluatedScript", evaluatedScript);
				dialog.dismiss();
			}
			catch (JSONException e){
				e.printStackTrace();
				Log.e(TAG, "Couldn't edit the response");
			}
		}
		
		public void refresh(){
			webview.loadUrl("javascript:window.location.reload(true)");
		}
		
		@Override
		public void run()
		{
			dialog = createDialog();

			webview = (WebView)dialog.findViewById(getIdentifier("webView1", "id"));
			
			progressBar = (ProgressBar)dialog.findViewById(getIdentifier("progressBar1", "id"));
			
			dialog.findViewById(getIdentifier("imageButton1", "id")).setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v){
					closeDialog();
				}
			});
			
			if(options.isShowOpenInExternal()){
				View openExternal = dialog.findViewById(getIdentifier("imageButton2", "id"));
				openExternal.setVisibility(ImageButton.VISIBLE);
				openExternal.setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View v){
						if(loadingUrl!=null){
						    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(loadingUrl));
						    cordova.getActivity().startActivity(Intent.createChooser(intent, "Choose Browser"));
						}
					}
				});
			}
			
			webview.setWebChromeClient(new WebChromeClient(){
				@Override
				public boolean onJsAlert(WebView view, String url, String message, JsResult result) {
					if(!message.startsWith(ALERT_TO_RETURN)){
						return super.onJsAlert(view, url, message, result);
					};
					
					message = message.substring(ALERT_TO_RETURN.length());
					closeDialogByEvaluatedScript(message);
					
					return true;
				}
			});
			webview.setWebViewClient(new ChildBrowserClient());
						
			dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
				@Override
				public void onDismiss(DialogInterface dialog)
				{
					webview.destroy();
					callbackContext.success(response);
				}
			});

			WebSettings settings = webview.getSettings();

			settings.setJavaScriptEnabled(true);
			settings.setJavaScriptCanOpenWindowsAutomatically(true);
			
			settings.setSupportZoom(true);
			settings.setBuiltInZoomControls(true);
			settings.setUseWideViewPort(true);

			settings.setDomStorageEnabled(true);
			
			settings.setUserAgentString("Mozilla/5.0 (Windows; U; Windows NT 6.1; tr-TR) AppleWebKit/533.20.25 (KHTML, like Gecko) Version/5.0.4 Safari/533.20.27");			
			webview.loadUrl(options.getUrl());
			
			webview.requestFocusFromTouch();

			webview.requestFocus(View.FOCUS_UP);

			dialog.show();
		}

		private Dialog createDialog()
		{
			Dialog dialog = new Dialog(cordova.getActivity(), android.R.style.Theme_NoTitleBar);
			dialog.setContentView(getIdentifier("internal_browser_dialog", "layout"));
			dialog.setCancelable(true);

			return dialog;
		}
		
		class ChildBrowserClient extends WebViewClient {
			private CookieSyncManager cookieSyncManager;
			private String scriptToInject;
			private String scriptToEvaluate;
			
			public ChildBrowserClient()
			{
				scriptToInject = options.getScript();
				scriptToEvaluate = options.getScriptToEvaluate();
				cookieSyncManager = CookieSyncManager.createInstance(cordova.getActivity());
				cookieSyncManager.sync();
				CookieManager.getInstance().setAcceptCookie(true);
				CookieManager.getInstance().removeExpiredCookie();
			}
			
			
			@Override
			public void onReceivedHttpAuthRequest(WebView view, HttpAuthHandler handler, String host, String realm)
			{
				Activity activity = cordova.getActivity();
				final WebView finalView = view;
		        final HttpAuthHandler finalHandler = handler;

		        final EditText usernameInput = new EditText(activity);
		        usernameInput.setHint("Username");

		        final EditText passwordInput = new EditText(activity);
		        passwordInput.setHint("Password");
		        passwordInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);

		        LinearLayout ll = new LinearLayout(activity);
		        ll.setOrientation(LinearLayout.VERTICAL);
		        ll.addView(usernameInput);
		        ll.addView(passwordInput);

		        Builder authDialog = new AlertDialog.Builder(activity)
	                .setTitle("Authentication")
	                .setView(ll)
	                .setCancelable(false)
	                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
	                    public void onClick(DialogInterface authDialog, int whichButton) {
	                    	finalHandler.proceed(usernameInput.getText().toString(), passwordInput.getText().toString());
	                    	authDialog.dismiss();
	                    }
	                })
	                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
	                    public void onClick(DialogInterface authDialog, int whichButton) {
	                    	finalView.stopLoading();
	                    	authDialog.dismiss();
	                    }
	                });

		        if(view!=null){
		            authDialog.show();
		        }
			}

			@Override
			public boolean shouldOverrideUrlLoading(WebView view, String url)
			{
				String[] requestUrls = options.getCloseOnRequestURLs();
				for (String requestUrl : requestUrls)
				{
					if (shouldCloseOnUrl(url, requestUrl))
					{
						closeDialogByUrl(url);
						return true;
					}
				}

				return super.shouldOverrideUrlLoading(view, url);
			}

			@Override
			public void onPageStarted(WebView view, String url, Bitmap favicon)
			{
				loadingUrl = url;
				
				progressBar.setVisibility(ProgressBar.VISIBLE);
				
				if(scriptToInject != null){
					view.setVisibility(WebView.INVISIBLE);
				}
				super.onPageStarted(view, url, favicon);
			}

			@Override
			public void onPageFinished(WebView view, String url)
			{				
				String[] loadingUrls = options.getCloseOnReturnURLs();
				for (String loadingUrl : loadingUrls)
				{
					if (shouldCloseOnUrl(url, loadingUrl))
					{
						closeDialogByUrl(url);
						return;
					}
				}
				if(scriptToInject != null){
					view.loadUrl("javascript:"+scriptToInject);
					scriptToInject = null;
				}
				if(scriptToEvaluate != null){
					view.loadUrl("javascript:"+scriptToEvaluate);
					scriptToEvaluate = null;
				}
				view.setVisibility(WebView.VISIBLE);
				progressBar.setVisibility(ProgressBar.INVISIBLE);
				cookieSyncManager.sync();
				super.onPageFinished(view, url);
			}

			@Override
			public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error)
			{
				super.onReceivedSslError(view, handler, error);
				handler.proceed();
			}
			
			private boolean shouldCloseOnUrl(String url, String closeUrl){
				String closeCompareType = options.getCloseCompareType();
				if(closeUrl == null){
					return false;
				}				
				if(closeCompareType.equals("startsWith")){
					return url.startsWith(closeUrl);
				}
				if(closeCompareType.equals("endsWith")){
					return url.endsWith(closeUrl);
				}
				if(closeCompareType.equals("contains")){
					return url.contains(closeUrl);
				}
				if(closeCompareType.equals("equals")){
					return url.equals(closeUrl);
				}
				
				Log.w(TAG, "closeCompareType of internal browser is unrecognized. using \"equals\" instead.");
				return url.equals(closeUrl);
				
			}
		}
	};

	class InternalBrowserOptions {

		private String url;
		private String password;
		private String user;
		private String script;
		private String scriptToEvaluate;
		private String closeOnInjectAnswer;
		private String[] closeOnReturnURLs;
		private String[] closeOnRequestURLs;
		private String closeCompareType;
		private boolean useAsync;
		private boolean fancyCloseButton;
		private boolean shouldStopOnPost;
		private boolean scalePageToFit;
		private boolean showToolbar;
		private boolean showToolbarWhileLoading;
		private boolean showOpenInExternal;
		private JSONObject proxy;

		public InternalBrowserOptions(JSONObject args) throws Exception
		{
			url = args.getString("url");
			password = args.optString("password", null);
			user = args.optString("user", null);
			script = args.optString("script", null);
			scriptToEvaluate = args.optString("scriptToEvaluate", null);
			useAsync = args.optBoolean("useAsync", false);
			proxy = args.optJSONObject("proxy");
			closeOnInjectAnswer = args.optString("closeOnInjectAnswer", null);
			closeOnReturnURLs = getOptDelimitedStringArray(args, "closeOnReturnURL");
			closeOnRequestURLs = getOptDelimitedStringArray(args, "closeOnRequestURL");
			closeCompareType = args.optString("closeCompareType", "contains");
			fancyCloseButton = args.optBoolean("fancyCloseButton", false);
			shouldStopOnPost = args.optBoolean("shouldStopOnPost", false);
			scalePageToFit = args.optBoolean("scalePageToFit", false);
			showToolbar = args.optBoolean("showToolbar", false);
			showToolbarWhileLoading = args.optBoolean("showToolbarWhileLoading", false);
			showOpenInExternal= args.optBoolean("showOpenInExternal", false);
		}

		private String[] getOptDelimitedStringArray(JSONObject args, String key)
		{
			String value = args.optString(key, null);

			if (value == null)
			{
				return new String[0];
			}

			return value.split(";");
		}
		
		public String getCloseOnInjectAnswer() {
			return closeOnInjectAnswer;
		}

		public void setCloseOnInjectAnswer(String closeOnInjectAnswer) {
			this.closeOnInjectAnswer = closeOnInjectAnswer;
		}

		public String getCloseCompareType()
		{
			return closeCompareType;
		}
		
		public String getUrl()
		{
			return url;
		}

		public String getPassword()
		{
			return password;
		}

		public String getUser()
		{
			return user;
		}

		public String getScript()
		{
			return script;
		}

		public String[] getCloseOnReturnURLs()
		{
			return closeOnReturnURLs;
		}

		public boolean isUseAsync()
		{
			return useAsync;
		}

		public boolean isFancyCloseButton()
		{
			return fancyCloseButton;
		}

		public String[] getCloseOnRequestURLs()
		{
			return closeOnRequestURLs;
		}

		public boolean isShouldStopOnPost()
		{
			return shouldStopOnPost;
		}

		public boolean isScalePageToFit()
		{
			return scalePageToFit;
		}

		public boolean isShowToolbar()
		{
			return showToolbar;
		}

		public boolean isShowToolbarWhileLoading()
		{
			return showToolbarWhileLoading;
		}

		public boolean isShowOpenInExternal()
		{
			return showOpenInExternal;
		}
		
		public JSONObject getProxy()
		{
			return proxy;
		}

		public String getScriptToEvaluate() {
			return scriptToEvaluate == null ? null : String.format("alert('%s' + %s)", ALERT_TO_RETURN, scriptToEvaluate);
		}

	}
}