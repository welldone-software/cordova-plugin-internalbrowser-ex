package welldonesoftware.cordova.plugins;

import org.json.JSONException;
import org.json.JSONObject;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.net.http.SslError;
import android.text.InputType;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.CookieSyncManager;
import android.webkit.HttpAuthHandler;
import android.webkit.SslErrorHandler;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebSettings.PluginState;
import android.webkit.WebSettings.ZoomDensity;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;

@SuppressLint("SetJavaScriptEnabled")
public class InternalBrowserCommand extends BaseCommand {
	
	private MainDialogRunnable internalBrowserThreadInstance = null;
	
	public void onActionDismissInternalBrowser(final String callbackId, JSONObject args) throws Exception{
		if(internalBrowserThreadInstance != null){
			internalBrowserThreadInstance.closeDialog(null);
		}
	}
	
	public void onActionRefresh(final String callbackId, JSONObject args) throws Exception{
		if(internalBrowserThreadInstance != null){
			internalBrowserThreadInstance.refresh();
		}
	}
	
	public Object onActionShowPage(final String callbackId, JSONObject args) throws Exception
	{
		InternalBrowserOptions options = new InternalBrowserOptions(args);
		//ProxyUtils.setStoredProxy(options.getProxy());

		internalBrowserThreadInstance = new MainDialogRunnable(options, callbackId);
		cordova.getActivity().runOnUiThread(internalBrowserThreadInstance);

		return createDelayedResult();
	}
	
	class MainDialogRunnable implements Runnable {
		private final InternalBrowserOptions options;
		private final String callbackId;
		private JSONObject response;
		private Dialog dialog;
		private WebView webview;
		private ProgressBar progressBar;
		private String loadingUrl;

		public MainDialogRunnable(final InternalBrowserOptions options, final String callbackId){
			this.response = new JSONObject();
			this.options = options;
			this.callbackId = callbackId;
		}
		
		public void closeDialog(String url){
			try{
				if(url == null){
					getLog().debug("closing InternalBrowser on cancel button.");
					response.put("isDone", true);
				}
				else{
					getLog().debug("closing InternalBrowser on url:" + url);
					response.put("closedByURL", url);
				}
				dialog.dismiss();
			}
			catch (JSONException e){
				e.printStackTrace();
				getLog().error("Couldn't edit the response");
			}
		}
		
		public void refresh(){
			webview.loadUrl("javascript:window.location.reload(true)");
		}
		
		@Override
		public void run()
		{
			dialog = createDialog();

			webview = (WebView)dialog.findViewById(R.id.webView1);
			
			progressBar = (ProgressBar)dialog.findViewById(R.id.progressBar1);
			
			dialog.findViewById(R.id.imageButton1).setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v){
					closeDialog(null);
				}
			});
			
			if(options.isShowOpenInExternal()){
				ImageButton openInExternl = (ImageButton)dialog.findViewById(R.id.imageButton2);
				openInExternl.setVisibility(ImageButton.VISIBLE);
				openInExternl.setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View v){
						if(loadingUrl!=null){
						    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(loadingUrl));
						    cordova.getActivity().startActivity(Intent.createChooser(intent, "Choose Browser"));
						}
					}
				});
			}
			
			webview.setWebChromeClient(new WebChromeClient());
			webview.setWebViewClient(new ChildBrowserClient());
						
			dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
				@Override
				public void onDismiss(DialogInterface dialog)
				{
					webview.destroy();
					success(response, callbackId);
				}
			});
			
//			try{
//				ProxyUtils.setWebViewProxy(webview);
//			}
//			catch (Exception e){
//				e.printStackTrace();
//				getLog().warn("couldn't set proxy on the internal browser", e);
//			}

			WebSettings settings = webview.getSettings();

			settings.setJavaScriptEnabled(true);
			settings.setJavaScriptCanOpenWindowsAutomatically(true);
			
			settings.setSupportZoom(true);
			settings.setBuiltInZoomControls(true);
			settings.setUseWideViewPort(true);
			settings.setDefaultZoom(ZoomDensity.MEDIUM);

			settings.setPluginState(PluginState.ON);

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
			dialog.setContentView(R.layout.internal_browser_dialog);
			dialog.setCancelable(true);

			return dialog;
		}
		
		class ChildBrowserClient extends WebViewClient {
			private CookieSyncManager cookieSyncManager;
			private String scriptToInject;

			public ChildBrowserClient()
			{
				//httpAuthTryCount = 0;
				scriptToInject = options.getScript();
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
						closeDialog(url);
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
						closeDialog(url);
						return;
					}
				}
				if(scriptToInject != null){
					view.loadUrl("javascript:"+scriptToInject);
					view.setVisibility(WebView.VISIBLE);
					scriptToInject = null;
				}
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
				
				getLog().warn("closeCompareType of internal browser is unrecognized. using \"equals\" instead.");
				return url.equals(closeUrl);
				
			}
		}
	};

	class InternalBrowserOptions {

		private String url;
		private String password;
		private String user;
		private String script;
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
			useAsync = args.optBoolean("useAsync", false);
			proxy = args.optJSONObject("proxy");
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

	}
}
