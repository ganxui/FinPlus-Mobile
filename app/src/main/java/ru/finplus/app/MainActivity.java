package ru.finplus.app;

import android.Manifest;
import android.app.Activity;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.SafeBrowsingResponse;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import java.util.UUID;

public class MainActivity extends Activity {
    private static final String APP_URL = "https://finplus.su";
    private static final String CHANNEL_ID = "finplus_updates";
    private FinPlusWebView webView;
    private View offlineView;
    private ProgressBar progress;
    private ValueCallback<Uri[]> fileCallback;
    private ConnectivityManager connectivity;
    private boolean pageFailed;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        setTheme(R.style.Theme_FinPlus);
        getWindow().setStatusBarColor(Color.rgb(245,243,239));
        getWindow().setNavigationBarColor(Color.rgb(245,243,239));
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        createNotificationChannel();
        buildInterface();
        registerNetworkMonitor();
        requestNotificationPermission();
        webView.loadUrl(resolveLaunchUrl(getIntent()));
    }

    private void buildInterface() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(245,243,239));
        webView = new FinPlusWebView(this);
        configureWebView();
        root.addView(webView, new FrameLayout.LayoutParams(-1,-1));
        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setIndeterminate(false); progress.setMax(100); progress.setVisibility(View.GONE);
        FrameLayout.LayoutParams pp = new FrameLayout.LayoutParams(-1,dp(3),Gravity.TOP);
        root.addView(progress,pp);
        offlineView = createOfflineView();
        offlineView.setVisibility(View.GONE);
        root.addView(offlineView,new FrameLayout.LayoutParams(-1,-1));
        setContentView(root);
    }

    private void configureWebView() {
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true); s.setDomStorageEnabled(true); s.setDatabaseEnabled(true);
        s.setAllowFileAccess(false); s.setAllowContentAccess(true); s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        s.setMediaPlaybackRequiresUserGesture(true); s.setBuiltInZoomControls(false); s.setDisplayZoomControls(false);
        s.setUserAgentString(s.getUserAgentString()+" FinPlusAndroid/1.0");
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView,false);
        webView.addJavascriptInterface(new NativeBridge(),"FinPlusNative");
        webView.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri=request.getUrl(); String host=uri.getHost();
                if ("https".equals(uri.getScheme()) && host!=null && (host.equals("finplus.su")||host.endsWith(".finplus.su"))) return false;
                try { startActivity(new Intent(Intent.ACTION_VIEW,uri)); } catch(ActivityNotFoundException ignored) {}
                return true;
            }
            @Override public void onPageStarted(WebView view,String url,android.graphics.Bitmap icon){pageFailed=false;progress.setVisibility(View.VISIBLE);}
            @Override public void onPageFinished(WebView view,String url){progress.setVisibility(View.GONE);if(!pageFailed)showOnline();}
            @Override public void onReceivedError(WebView view,WebResourceRequest request,WebResourceError error){if(request.isForMainFrame()){pageFailed=true;showOffline();}}
            @Override public void onSafeBrowsingHit(WebView view,WebResourceRequest request,int threatType,SafeBrowsingResponse callback){callback.backToSafety(true);}
        });
        webView.setWebChromeClient(new WebChromeClient(){
            @Override public void onProgressChanged(WebView view,int value){progress.setProgress(value);}
            @Override public boolean onShowFileChooser(WebView view,ValueCallback<Uri[]> callback,FileChooserParams params){
                if(fileCallback!=null)fileCallback.onReceiveValue(null);fileCallback=callback;
                try{startActivityForResult(params.createIntent(),42);return true;}catch(ActivityNotFoundException e){fileCallback=null;return false;}
            }
        });
    }

    private View createOfflineView(){
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setGravity(Gravity.CENTER);box.setPadding(dp(32),dp(32),dp(32),dp(32));box.setBackgroundColor(Color.rgb(245,243,239));
        TextView mark=text("F+",32,Color.WHITE,true);mark.setGravity(Gravity.CENTER);mark.setBackgroundResource(R.drawable.offline_mark);box.addView(mark,new LinearLayout.LayoutParams(dp(84),dp(84)));
        TextView title=text(getString(R.string.offline_title),24,Color.rgb(37,35,31),true);LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(-2,-2);tp.topMargin=dp(28);box.addView(title,tp);
        TextView copy=text(getString(R.string.offline_text),15,Color.rgb(119,113,106),false);copy.setGravity(Gravity.CENTER);copy.setMaxWidth(dp(440));copy.setLineSpacing(0,1.25f);LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-2,-2);cp.topMargin=dp(10);box.addView(copy,cp);
        Button retry=new Button(this);retry.setText(R.string.retry);retry.setTextColor(Color.WHITE);retry.setTypeface(Typeface.DEFAULT,Typeface.BOLD);retry.setAllCaps(false);retry.setBackgroundResource(R.drawable.primary_button);retry.setOnClickListener(v->retry());LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(dp(210),dp(52));bp.topMargin=dp(26);box.addView(retry,bp);return box;
    }

    private TextView text(String value,int size,int color,boolean bold){TextView v=new TextView(this);v.setText(value);v.setTextSize(size);v.setTextColor(color);if(bold)v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return v;}
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
    private boolean online(){Network n=connectivity==null?null:connectivity.getActiveNetwork();NetworkCapabilities c=n==null?null:connectivity.getNetworkCapabilities(n);return c!=null&&c.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);}
    private void retry(){if(online()){showOnline();webView.reload();}else showOffline();}
    private void showOffline(){offlineView.setVisibility(View.VISIBLE);progress.setVisibility(View.GONE);}
    private void showOnline(){offlineView.setVisibility(View.GONE);}

    private void registerNetworkMonitor(){connectivity=(ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);connectivity.registerDefaultNetworkCallback(new ConnectivityManager.NetworkCallback(){@Override public void onAvailable(Network n){runOnUiThread(()->{if(pageFailed||offlineView.getVisibility()==View.VISIBLE){showOnline();webView.reload();}});}@Override public void onLost(Network n){runOnUiThread(()->{if(!online())showOffline();});}});}
    private String resolveLaunchUrl(Intent intent){Uri data=intent==null?null:intent.getData();if(data!=null&&"finplus".equals(data.getScheme())){String path=data.getQueryParameter("path");if(path!=null&&path.startsWith("/")&&!path.startsWith("//"))return APP_URL+path;}return APP_URL;}
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);webView.loadUrl(resolveLaunchUrl(intent));}
    @Override public void onBackPressed(){if(webView.canGoBack())webView.goBack();else super.onBackPressed();}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==42&&fileCallback!=null){fileCallback.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(result,data));fileCallback=null;}}
    private void createNotificationChannel(){NotificationManager n=getSystemService(NotificationManager.class);n.createNotificationChannel(new NotificationChannel(CHANNEL_ID,"Обновления ФинПлюс",NotificationManager.IMPORTANCE_DEFAULT));}
    private void requestNotificationPermission(){if(android.os.Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},81);}

    public final class NativeBridge {
        @JavascriptInterface public String platform(){return "android";}
        @JavascriptInterface public String installationId(){String value=getSharedPreferences("finplus_native",MODE_PRIVATE).getString("installation_id",null);return value==null?createId():value;}
        @JavascriptInterface public void setSystemTheme(String theme){runOnUiThread(()->{boolean dark="DARK".equalsIgnoreCase(theme)||("SYSTEM".equalsIgnoreCase(theme)&&((getResources().getConfiguration().uiMode&48)==32));int color=dark?Color.rgb(29,26,23):Color.rgb(245,243,239);getWindow().setStatusBarColor(color);getWindow().setNavigationBarColor(color);getWindow().getDecorView().setSystemUiVisibility(dark?0:View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);});}
        private String createId(){String id=UUID.randomUUID().toString();getSharedPreferences("finplus_native",MODE_PRIVATE).edit().putString("installation_id",id).apply();return id;}
        @JavascriptInterface public void openNotificationSettings(){Intent i=new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,getPackageName());startActivity(i);}
    }

    private final class FinPlusWebView extends WebView {
        private float startY; private boolean pulling;
        FinPlusWebView(Context context){super(context);}
        @Override public boolean onTouchEvent(MotionEvent e){if(e.getAction()==MotionEvent.ACTION_DOWN){startY=e.getY();pulling=!canScrollVertically(-1);}else if(e.getAction()==MotionEvent.ACTION_MOVE&&pulling){float d=e.getY()-startY;if(d>0){progress.setVisibility(View.VISIBLE);progress.setProgress(Math.min(95,(int)(d/2)));}}else if(e.getAction()==MotionEvent.ACTION_UP&&pulling){float d=e.getY()-startY;pulling=false;if(d>dp(120))reload();else progress.setVisibility(View.GONE);}return super.onTouchEvent(e);}
    }
}
