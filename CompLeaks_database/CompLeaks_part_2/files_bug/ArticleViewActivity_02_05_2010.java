package aarddict.android;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import aarddict.Dictionary;
import aarddict.Dictionary.Article;
import aarddict.Dictionary.RedirectNotFound;
import aarddict.Dictionary.RedirectTooManyLevels;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.DialogInterface.OnClickListener;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.Window;
import android.webkit.JsResult;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;


public class ArticleViewActivity_02_05_2010 extends Activity {

    private final static String TAG = "aarddict.ArticleViewActivity";
    private WebView articleView;
    private String sharedCSS;
    private String mediawikiSharedCSS;
    private String mediawikiMonobookCSS;
    private String js;
        
    private List<Dictionary.Article> backItems; 
    private List<Dictionary.Article> forwardItems;
    
    DictionaryService 	dictionaryService;
    ServiceConnection 	connection;
        
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        loadAssets();

        backItems = new LinkedList<Dictionary.Article>();
        forwardItems = new LinkedList<Dictionary.Article>();
        
        getWindow().requestFeature(Window.FEATURE_PROGRESS);        
        getWindow().requestFeature(Window.FEATURE_LEFT_ICON);
        
        articleView = new WebView(this);        
        articleView.getSettings().setBuiltInZoomControls(true);
        articleView.getSettings().setJavaScriptEnabled(true);
        
        articleView.addJavascriptInterface(new SectionMatcher(), "matcher");
        
        articleView.setWebChromeClient(new WebChromeClient(){
//            @Override
            public void onConsoleMessage(String message, int lineNumber, String sourceID) {
                Log.d(TAG + ".js", String.format("%d [%s]: %s", lineNumber, sourceID, message));
            }
            
            @Override
            public boolean onJsAlert(WebView view, String url, String message,
            		JsResult result) {            	
            	Log.d(TAG + ".js", String.format("[%s]: %s", url, message));
            	result.cancel();
            	return true;
            }
            
            public void onProgressChanged(WebView view, int newProgress) {
                Log.d(TAG, "Progress: " + newProgress);
                setProgress(5000 + newProgress * 50);
            }
        });
                       
        articleView.setWebViewClient(new WebViewClient() {
            
            @Override
            public void onPageFinished(WebView view, String url) {
                Log.d(TAG, "Page finished: " + url);
                String section = null;
                
                if (url.contains("#")) {
                    String[] parts = url.split("#", 2);
                    section = parts[1];
                    if (backItems.size() > 0) {
                        Dictionary.Article current = backItems.get(backItems.size() - 1);
                        Dictionary.Article a = new Dictionary.Article(current);
                        a.section = section;
                        backItems.add(a);
                    }
                }
                else if (backItems.size() > 0) {
                    Dictionary.Article current = backItems.get(backItems.size() - 1);
                    section = current.section;
                }
                
                if (section != null && !section.trim().equals("")) {
                    goToSection(section);
                }     
                
            }
            
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, final String url) {
                Log.d(TAG, "URL clicked: " + url);
                String urlLower = url.toLowerCase(); 
                if (urlLower.startsWith("http://") ||
                    urlLower.startsWith("https://") ||
                    urlLower.startsWith("ftp://") ||
                    urlLower.startsWith("sftp://") ||
                    urlLower.startsWith("mailto:")) {
                    Intent browserIntent = new Intent(Intent.ACTION_VIEW, 
                                                Uri.parse(url)); 
                    startActivity(browserIntent);                                         
                }
                else {
                    Thread t = new Thread(new Runnable() {
    					public void run() {
    						final Iterator<Dictionary.Entry> a = dictionaryService.lookup(url);
    						runOnUiThread(new Runnable() {
								public void run() {
				                    if (a.hasNext()) {
				                        Dictionary.Entry entry = a.next();
				                        showArticle(entry);
				                    }                
				                    else {
				                        showMessage(String.format("Article \"%s\" not found", url));
				                    }                										
								}
							});
    					}
    				});
                    t.setPriority(Thread.MIN_PRIORITY);
					t.start();
                }
                return true;
            }
        });        
                        
        setContentView(articleView);
        setProgressBarVisibility(true);
        getWindow().setFeatureDrawableResource(Window.FEATURE_LEFT_ICON, R.drawable.aarddict);
        
        connection = new ServiceConnection() {
            public void onServiceConnected(ComponentName className, IBinder service) {
            	dictionaryService = ((DictionaryService.LocalBinder)service).getService();
                Intent intent = getIntent();
                String word = intent.getStringExtra("word");                
                String section = intent.getStringExtra("section");
                String volumeId = intent.getStringExtra("volumeId");
                long articlePointer = intent.getLongExtra("articlePointer", -1);            	
            	showArticle(volumeId, articlePointer, word, section);
            }

            public void onServiceDisconnected(ComponentName className) {
            	dictionaryService = null;
                Toast.makeText(ArticleViewActivity.this, "Dictionary service disconnected, quitting...",
                        Toast.LENGTH_LONG).show();
                ArticleViewActivity.this.finish();
            }
        };                
        
        Intent dictServiceIntent = new Intent(this, DictionaryService.class);
        bindService(dictServiceIntent, connection, 0);                                
    }

    private void goToSection(String section) {
        articleView.loadUrl(String.format("javascript:scrollToMatch(\"%s\")", section));
    }    
    
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if ((keyCode == KeyEvent.KEYCODE_BACK)) {
            if (goBack()) {
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    private boolean goBack() {
        if (backItems.size() > 1) {
            Dictionary.Article current = backItems.remove(backItems.size() - 1); 
            forwardItems.add(0, current);
            Dictionary.Article prev = backItems.remove(backItems.size() - 1);
            
            if (prev.eqalsIgnoreSection(current)) {
                backItems.add(prev);
                goToSection(prev.section);
            }   
            else {
                showArticle(prev);
            }
            return true;            
        }
        return false;
    }
    
    private boolean goForward() {
        if (forwardItems.size() > 0){              
            Dictionary.Article next = forwardItems.remove(0);
            Dictionary.Article current = backItems.get(backItems.size() - 1);
            if (next.eqalsIgnoreSection(current)) {
                backItems.add(next);
                goToSection(next.section);                
            } else {
                showArticle(next);
            }
        }
        return false;
    }

    @Override
    public boolean onSearchRequested() {
        finish();
        return true;
    }
    
    final static int MENU_BACK = 1;
    final static int MENU_FORWARD = 2;
    final static int MENU_VIEW_ONLINE = 3;
    final static int MENU_NEW_LOOKUP = 4;
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, MENU_BACK, 0, "Back").setIcon(android.R.drawable.ic_media_previous);        
        menu.add(0, MENU_FORWARD, 0, "Forward").setIcon(android.R.drawable.ic_media_next);
        menu.add(0, MENU_VIEW_ONLINE, 0, "View Online").setIcon(android.R.drawable.ic_menu_view);
        menu.add(0, MENU_NEW_LOOKUP, 0, "New Lookup").setIcon(android.R.drawable.ic_menu_search);
        return true;
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case MENU_BACK:
            if (!goBack()) {
                finish();
            };
            break;
        case MENU_FORWARD:
            goForward();
            break;
        case MENU_VIEW_ONLINE:
            viewOnline();
            break;            
        case MENU_NEW_LOOKUP:
            onSearchRequested();
            break;                        
        }
        return true;
    }
    
    private void viewOnline() {
        if (this.backItems.size() > 0) {            
            Dictionary.Article current = this.backItems.get(this.backItems.size() - 1);
            Dictionary d = dictionaryService.getDictionary(current.volumeId);
            String url = d == null ? null : d.getArticleURL(current.title);
            if (url != null) {
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, 
                        Uri.parse(url)); 
                startActivity(browserIntent);                                         
            }
        }
    }
    
    private void showArticle(String volumeId, long articlePointer, String word, String section) {
        Log.d(TAG, "word: " + word);
        Log.d(TAG, "dictionaryId: " + volumeId);
        Log.d(TAG, "articlePointer: " + articlePointer);
        Log.d(TAG, "section: " + section);
                
        Dictionary d = dictionaryService.getDictionary(volumeId);
        if (d == null) {
            showError(String.format("Dictionary %s not found", volumeId));
            return;
        }
        
        Dictionary.Entry entry = new Dictionary.Entry(d, word, articlePointer);
        entry.section = section;
        showArticle(entry);
    }    
    
    private void showArticle(final Dictionary.Entry entry) {
    	forwardItems.clear();    	
    	setTitle(entry);
    	setProgress(500);
    	Thread t = new Thread(
    			new Runnable() {
					public void run() {						
				        try {
					        final Article a = entry.getArticle();
							runOnUiThread( new Runnable() {							
								public void run() {
									showArticle(a);							
								}
							});
				        }
				        catch (Exception e) {
							runOnUiThread( new Runnable() {							
								public void run() {
									showError(String.format("There was an error loading article \"%s\"", entry.title));
								}
							});				        					            
				        }    							
					}
				});
    	t.setPriority(Thread.MIN_PRIORITY);
		t.start();     	
    }
    
    private void showArticle(Dictionary.Article a) {
        try {
            a = dictionaryService.redirect(a);
        }            
        catch (RedirectNotFound e) {        	
        	setProgress(10000);     
        	if (!backItems.isEmpty()) {
        		setTitle(backItems.get(0));
        	}
            showMessage(String.format("Redirect \"%s\" not found", a.getRedirect()));
            return;
        }
        catch (RedirectTooManyLevels e) {
        	setProgress(10000);
        	if (!backItems.isEmpty()) {
        		setTitle(backItems.get(0));
        	}        	
            showMessage(String.format("Too many redirects for \"%s\"", a.getRedirect()));
            return;
        }
        catch (Exception e) {
        	setProgress(10000);
        	if (!backItems.isEmpty()) {
        		setTitle(backItems.get(0));
        	}        	
            showError(String.format("There was an error loading article \"%s\"", a.title));
            return;
        }
        backItems.add(a);
        setProgress(5000);
        setTitle(a);
        Log.d(TAG, "Show article: " + a.text);        
        articleView.loadDataWithBaseURL("", wrap(a.text), "text/html", "utf-8", null);
    }
    
    private void showMessage(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        if (backItems.size() == 0) {
            finish();
        }        
    }

    private void showError(String message) {
        AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(this);
        dialogBuilder.setTitle("Error").setMessage(message).setNeutralButton("Dismiss", new OnClickListener() {            
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
                if (backItems.size() == 0) {
                    finish();
                }
            }
        });
        dialogBuilder.show();
    }
    
    private void setTitle(Article a) {
    	Dictionary d = dictionaryService.getDictionary(a.volumeId);
    	CharSequence dictTitle = "Dictionary not found";
    	if (d != null) {
    		dictTitle = d.getDisplayTitle();
    	}
    	setTitle(a.title, dictTitle);
    }
    
    private void setTitle(Dictionary.Entry e) {
    	setTitle(e.title, e.dictionary.getDisplayTitle());
    }    
    
    private void setTitle(CharSequence articleTitle, CharSequence dictTitle) {
    	setTitle(String.format("%s - %s", articleTitle, dictTitle));
    }        
    
    private String wrap(String articleText) {
        return new StringBuilder("<html>")
        .append("<head>")
        .append(this.sharedCSS)
        .append(this.mediawikiSharedCSS)
        .append(this.mediawikiMonobookCSS)
        .append(this.js)
        .append("</head>")
        .append("<body>")
        .append("<div id=\"globalWrapper\">")        
        .append(articleText)
        .append("</div>")
        .append("</body>")
        .append("</html>")
        .toString();
    }
    
    private String wrapCSS(String css) {
        return String.format("<style type=\"text/css\">%s</style>", css);
    }

    private String wrapJS(String js) {
        return String.format("<script type=\"text/javascript\">%s</script>", js);
    }
    
    private void loadAssets() {
        try {
            this.sharedCSS = wrapCSS(readFile("shared.css"));
            this.mediawikiSharedCSS = wrapCSS(readFile("mediawiki_shared.css"));
            this.mediawikiMonobookCSS = wrapCSS(readFile("mediawiki_monobook.css"));
            this.js = wrapJS(readFile("aar.js"));
        }
        catch (IOException e) {
            Log.e(TAG, "Failed to load assets", e);
        }        
    }
    
    private String readFile(String name) throws IOException {
        final char[] buffer = new char[0x1000];
        StringBuilder out = new StringBuilder();
        InputStream is = getResources().getAssets().open(name);
        Reader in = new InputStreamReader(is, "UTF-8");
        int read;
        do {
          read = in.read(buffer, 0, buffer.length);
          if (read>0) {
            out.append(buffer, 0, read);
          }
        } while (read>=0);
        return out.toString();
    }
    
    @Override
    protected void onDestroy() {
    	super.onDestroy();
    	unbindService(connection);  
    }
}