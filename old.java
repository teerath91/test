import android.os.RemoteException;
import android.provider.BaseColumns;
import android.text.Editable;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.util.Linkify;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

public class AccountActivity extends Activity {

    public static final String TAG = "AccountActivity";
    private static final String ACCOUNT_URI_KEY = "accountUri";
    private long mProviderId = 0;
    private long mAccountId = 0;
    static final int REQUEST_SIGN_IN = RESULT_FIRST_USER + 1;
    private static final String[] ACCOUNT_PROJECTION = { Imps.Account._ID, Imps.Account.PROVIDER,
                                                        Imps.Account.USERNAME,
                                                        Imps.Account.PASSWORD,
                                                        Imps.Account.KEEP_SIGNED_IN,
                                                        Imps.Account.LAST_LOGIN_STATE };
    private static final int ACCOUNT_PROVIDER_COLUMN = 1;
    private static final int ACCOUNT_USERNAME_COLUMN = 2;
    private static final int ACCOUNT_PASSWORD_COLUMN = 3;
    
    public final static String DEFAULT_SERVER_GOOGLE = "talk.l.google.com";
    public final static String DEFAULT_SERVER_FACEBOOK = "chat.facebook.com";
    public final static String DEFAULT_SERVER_JABBERORG = "hermes2.jabber.org";
    public final static String DEFAULT_SERVER_DUKGO = "dukgo.com";
    public final static String ONION_JABBERCCC = "okj7xc6j2szr2y75.onion";
    public final static String ONION_CALYX = "ijeeynrc6x2uy5ob.onion";
    
    //    private static final int ACCOUNT_KEEP_SIGNED_IN_COLUMN = 4;
    //    private static final int ACCOUNT_LAST_LOGIN_STATE = 5;

    Uri mAccountUri;
    EditText mEditUserAccount;
    EditText mEditPass;
    EditText mEditPassConfirm;
    CheckBox mRememberPass;
    CheckBox mUseTor;
    Button mBtnSignIn;
    Button mBtnDelete;
    Spinner mSpinnerDomains;
    
    Button mBtnAdvanced;
    TextView mTxtFingerprint;

    //Imps.ProviderSettings.QueryMap settings;
    
    boolean isEdit = false;
    boolean isSignedIn = false;

    String mUserName = "";
    String mDomain = "";
    int mPort = 0;
    private String mOriginalUserAccount = "";

    private final static int DEFAULT_PORT = 5222;

    IOtrChatSession mOtrChatSession;
    private SignInHelper mSignInHelper;

    private boolean mIsNewAccount = false;

    private AsyncTask<String, Void, String> mCreateAccountTask = null;
    
    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        Intent i = getIntent();
        
        mApp = (ImApp)getApplication();

        String action = i.getAction();

        if (i.hasExtra("isSignedIn"))
            isSignedIn = i.getBooleanExtra("isSignedIn", false);
        

        final ProviderDef provider;
        
        mSignInHelper = new SignInHelper(this);
        SignInHelper.SignInListener signInListener = new SignInHelper.SignInListener() {
            public void connectedToService() {
            }
            public void stateChanged(int state, long accountId) {
                if (state == ImConnection.LOGGING_IN || state == ImConnection.LOGGED_IN)
                {
                    mSignInHelper.goToAccount(accountId);
                    finish();
                }
            }
        };
        
        mSignInHelper.setSignInListener(signInListener);
        

        ContentResolver cr = getContentResolver();

        Uri uri = i.getData();
        // check if there is account information and direct accordingly
        if (Intent.ACTION_INSERT_OR_EDIT.equals(action)) {
            if ((uri == null) || !Imps.Account.CONTENT_ITEM_TYPE.equals(cr.getType(uri))) {
                action = Intent.ACTION_INSERT;
            } else {
                action = Intent.ACTION_EDIT;
            }
        }

        if (Intent.ACTION_INSERT.equals(action) && uri.getScheme().equals("ima")) {
            ImPluginHelper helper = ImPluginHelper.getInstance(this);
            String authority = uri.getAuthority();
            String[] userpass_host = authority.split("@");
            String[] user_pass = userpass_host[0].split(":");
            mUserName = user_pass[0];
            String pass = user_pass[1];
            mDomain = userpass_host[1];
            mPort = 0;
            Cursor cursor = openAccountByUsernameAndDomain(cr);
            boolean exists = cursor.moveToFirst();
            long accountId;
            if (exists) {
                accountId = cursor.getLong(0);
                mAccountUri = ContentUris.withAppendedId(Imps.Account.CONTENT_URI, accountId);
                pass = cursor.getString(ACCOUNT_PASSWORD_COLUMN);
                
                setAccountKeepSignedIn(true);
                mSignInHelper.activateAccount(mProviderId, accountId);
                mSignInHelper.signIn(pass, mProviderId, accountId, true);
                setResult(RESULT_OK);
                cursor.close();
                finish();
                return;
                
            } else {
                mProviderId = helper.createAdditionalProvider(helper.getProviderNames().get(0)); //xmpp FIXME
                accountId = ImApp.insertOrUpdateAccount(cr, mProviderId, mUserName, pass);
                mAccountUri = ContentUris.withAppendedId(Imps.Account.CONTENT_URI, accountId);
                mSignInHelper.activateAccount(mProviderId, accountId);
                createNewAccount(mUserName, pass, accountId);
                cursor.close();
                return;
            }
           
           
        
            
        } else if (Intent.ACTION_INSERT.equals(action)) {
            

            setupUIPre();
            
            mOriginalUserAccount = "";
            // TODO once we implement multiple IM protocols
            mProviderId = ContentUris.parseId(uri);
            provider = mApp.getProvider(mProviderId);

            if (provider != null)
            {
                setTitle(getResources().getString(R.string.add_account, provider.mFullName));
    
            }
            else
            {
                finish();
            }

            
        } else if (Intent.ACTION_EDIT.equals(action)) {
            

            setupUIPre();
            
            if ((uri == null) || !Imps.Account.CONTENT_ITEM_TYPE.equals(cr.getType(uri))) {
                LogCleaner.warn(ImApp.LOG_TAG, "<AccountActivity>Bad data");
                return;
            }

            isEdit = true;

            Cursor cursor = cr.query(uri, ACCOUNT_PROJECTION, null, null, null);

            if (cursor == null) {
                finish();
                return;
            }

            if (!cursor.moveToFirst()) {
                cursor.close();
                finish();
                return;
            }

            setTitle(R.string.sign_in);

            mAccountId = cursor.getLong(cursor.getColumnIndexOrThrow(BaseColumns._ID));

            mProviderId = cursor.getLong(ACCOUNT_PROVIDER_COLUMN);
            provider = mApp.getProvider(mProviderId);

            Cursor pCursor = cr.query(Imps.ProviderSettings.CONTENT_URI,new String[] {Imps.ProviderSettings.NAME, Imps.ProviderSettings.VALUE},Imps.ProviderSettings.PROVIDER + "=?",new String[] { Long.toString(mProviderId)},null);

            Imps.ProviderSettings.QueryMap settings = new Imps.ProviderSettings.QueryMap(
                    pCursor, cr, mProviderId, false /* don't keep updated */, null /* no handler */);

            try {
                mOriginalUserAccount = cursor.getString(ACCOUNT_USERNAME_COLUMN) + "@"
                                       + settings.getDomain();
                mEditUserAccount.setText(mOriginalUserAccount);
                mEditPass.setText(cursor.getString(ACCOUNT_PASSWORD_COLUMN));
                mRememberPass.setChecked(!cursor.isNull(ACCOUNT_PASSWORD_COLUMN));
                mUseTor.setChecked(settings.getUseTor());
                mBtnDelete.setVisibility(View.VISIBLE);
            } finally {
                settings.close();
                cursor.close();
            }


        } else {
            LogCleaner.warn(ImApp.LOG_TAG, "<AccountActivity> unknown intent action " + action);
            finish();
            return;
        }

       setupUIPost();

    }
    
    private void setupUIPre ()
    {
        setContentView(R.layout.account_activity);
        
        mIsNewAccount = getIntent().getBooleanExtra("register", false);
        
        
        mEditUserAccount = (EditText) findViewById(R.id.edtName);
        mEditUserAccount.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                checkUserChanged();
            }
        });

        mEditPass = (EditText) findViewById(R.id.edtPass);
        
        mEditPassConfirm = (EditText) findViewById(R.id.edtPassConfirm);
        mSpinnerDomains = (Spinner) findViewById(R.id.spinnerDomains);
        
        if (mIsNewAccount)
        {
            mEditPassConfirm.setVisibility(View.VISIBLE);
            mSpinnerDomains.setVisibility(View.VISIBLE);
            mEditUserAccount.setHint(R.string.account_setup_new_username);
        }
        
        mRememberPass = (CheckBox) findViewById(R.id.rememberPassword);
        mUseTor = (CheckBox) findViewById(R.id.useTor);
       

        mBtnSignIn = (Button) findViewById(R.id.btnSignIn);
        
        if (mIsNewAccount)
            mBtnSignIn.setText(R.string.btn_create_account);
        
        mBtnAdvanced = (Button) findViewById(R.id.btnAdvanced);
        mBtnDelete = (Button) findViewById(R.id.btnDelete);
        
        mRememberPass.setOnCheckedChangeListener(new OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                updateWidgetState();
            }
        });

    }
    
    private void setupUIPost ()
    {
        Intent i = getIntent();
        
        if (isSignedIn) {
            mBtnSignIn.setText(getString(R.string.menu_sign_out));
            mBtnSignIn.setBackgroundResource(R.drawable.btn_red);
        }

        final BrandingResources brandingRes = mApp.getBrandingResource(mProviderId);

        mEditUserAccount.addTextChangedListener(mTextWatcher);
        mEditPass.addTextChangedListener(mTextWatcher);

        mBtnAdvanced.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                showAdvanced();
            }
        });
        
        mBtnDelete.setOnClickListener(new OnClickListener()
        {

            @Override
            public void onClick(View v) {
               
                deleteAccount();
                finish();
                
            }
            
        });

        mBtnSignIn.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {

                checkUserChanged();
                
                if (mUseTor.isChecked())
                {
                    OrbotHelper oh = new OrbotHelper(AccountActivity.this);
                    if (!oh.isOrbotRunning())
                    {
                        oh.requestOrbotStart(AccountActivity.this);
                        return;
                    }
                }
                

                final String pass = mEditPass.getText().toString();
                final String passConf = mEditPassConfirm.getText().toString();
                final boolean rememberPass = mRememberPass.isChecked();
                final boolean isActive = false; // TODO(miron) does this ever need to be true?
                ContentResolver cr = getContentResolver();

                if (mIsNewAccount)
                {
                    mDomain = (String)mSpinnerDomains.getSelectedItem();
                    String fullUser = mEditUserAccount.getText().toString();
                    
                    if (fullUser.indexOf("@")==-1)
                        fullUser += '@' + mDomain;
                    
                    if (!parseAccount(fullUser)) {
                        mEditUserAccount.selectAll();
                        mEditUserAccount.requestFocus();
                        return;
                    }
                    
                    ImPluginHelper helper = ImPluginHelper.getInstance(AccountActivity.this);
                    mProviderId = helper.createAdditionalProvider(helper.getProviderNames().get(0)); //xmpp FIXME

                }
                else
                {
                    if (!parseAccount(mEditUserAccount.getText().toString())) {
                        mEditUserAccount.selectAll();
                        mEditUserAccount.requestFocus();
                        return;
                    }
                    else
                    {
                        settingsForDomain(mDomain,mPort);//apply final settings
                    }
                }

     
                mAccountId = ImApp.insertOrUpdateAccount(cr, mProviderId, mUserName,
                        rememberPass ? pass : null);
                
                mAccountUri = ContentUris.withAppendedId(Imps.Account.CONTENT_URI, mAccountId);
                
                //if remember pass is true, set the "keep signed in" property to true
                if (mIsNewAccount)
                {
                    if (pass.equals(passConf))
                    {
                        createNewAccount(mUserName, pass, mAccountId);
                        setAccountKeepSignedIn(rememberPass);
                        mSignInHelper.activateAccount(mProviderId, mAccountId);
                       // setResult(RESULT_OK);
                        //mSignInHelper.signIn(pass, mProviderId, accountId, isActive);
                        //isSignedIn = true;
                        //updateWidgetState();
                       // finish();
                    }
                    else
                    {
                       Toast.makeText(AccountActivity.this, "Your passwords do not match", Toast.LENGTH_SHORT).show();
                    }
                }
                else
                {
                    if (isSignedIn) {
                        signOut();
                        isSignedIn = false;
                    } else {
                        setAccountKeepSignedIn(rememberPass);
                        
                        if (!mOriginalUserAccount.equals(mUserName + '@' + mDomain)
                            && shouldShowTermOfUse(brandingRes)) {
                            confirmTermsOfUse(brandingRes, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    mSignInHelper.signIn(pass, mProviderId, mAccountId, isActive);
                                }
                            });
                        } else {
                            mSignInHelper.signIn(pass, mProviderId, mAccountId, isActive);
                        }
                      
                        isSignedIn = true;
                    }
                    updateWidgetState();
                    setResult(RESULT_OK);
                    finish();
                }
                
            }
        });
        
        mUseTor.setOnCheckedChangeListener(new OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                updateUseTor(isChecked);
            }
        });

        updateWidgetState();
        
        if (i.hasExtra("title"))
        {
            String title = i.getExtras().getString("title");
            setTitle(title);
        }
        
        if (i.hasExtra("newuser"))
        {
            String newuser = i.getExtras().getString("newuser");
            mEditUserAccount.setText(newuser);
            
            parseAccount(newuser);
            settingsForDomain(mDomain,mPort);
            
        }
        
        if (i.hasExtra("newpass"))
        {
            mEditPass.setText(i.getExtras().getString("newpass"));
            mEditPass.setVisibility(View.GONE);
            mRememberPass.setChecked(true);
            mRememberPass.setVisibility(View.GONE);
        }

        if (i.getBooleanExtra("hideTor", false))
        {
            mUseTor.setVisibility(View.GONE);
        }
    }

    private Cursor openAccountByUsernameAndDomain(ContentResolver cr) {
        String clauses = Imps.Account.USERNAME + " = ? AND " + Imps.ProviderSettings.VALUE + " = ?";
        String args[] = new String[2];
        args[0] = mUserName;
        args[1] = mDomain;

        String[] projection = { Imps.Account._ID };
        Cursor cursor = cr.query(Imps.Account.BY_DOMAIN_URI, projection, clauses, args, null);
        return cursor;
    }
    
    @Override
    protected void onDestroy() {
       
        if (mCreateAccountTask != null && (!mCreateAccountTask.isCancelled()))
        {
            mCreateAccountTask.cancel(true);           
        }
        
        if (mSignInHelper != null)
            mSignInHelper.stop();
                
        super.onDestroy();
    }
    
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if ((keyCode == KeyEvent.KEYCODE_BACK)) {
            checkUserChanged();
        }
        return super.onKeyDown(keyCode, event);
    }
    
    private void updateUseTor(boolean useTor) {
        checkUserChanged();
    
        OrbotHelper orbotHelper = new OrbotHelper(this);

        ContentResolver cr = getContentResolver();
        Cursor pCursor = cr.query(Imps.ProviderSettings.CONTENT_URI,new String[] {Imps.ProviderSettings.NAME, Imps.ProviderSettings.VALUE},Imps.ProviderSettings.PROVIDER + "=?",new String[] { Long.toString(mProviderId)},null);

        Imps.ProviderSettings.QueryMap settings = new Imps.ProviderSettings.QueryMap(
               pCursor, cr, mProviderId, false /* don't keep updated */, null /* no handler */);

        if (useTor && (!orbotHelper.isOrbotInstalled()))
        {
            //Toast.makeText(this, "Orbot app is not installed. Please install from Google Play or from https://guardianproject.info/releases", Toast.LENGTH_LONG).show();
            
            orbotHelper.promptToInstall(this);
            
            mUseTor.setChecked(false);
            settings.setUseTor(false);
        }
        else
        {
            settings.setUseTor(useTor);
        }
        
        settingsForDomain(settings.getDomain(),settings.getPort(),settings);
        settings.close();
    }
/*
    private void getOTRKeyInfo() {

        if (mApp != null && FFF != null) {
            try {
                otrKeyManager = mApp.getRemoteImService().getOtrKeyManager(mOriginalUserAccount);

                if (otrKeyManager == null) {
                    mTxtFingerprint = ((TextView) findViewById(R.id.txtFingerprint));

                    String localFingerprint = otrKeyManager.getLocalFingerprint();
                    if (localFingerprint != null) {
                        ((TextView) findViewById(R.id.lblFingerprint)).setVisibility(View.VISIBLE);
                        mTxtFingerprint.setText(processFingerprint(localFingerprint));
                    } else {
                        ((TextView) findViewById(R.id.lblFingerprint)).setVisibility(View.GONE);
                        mTxtFingerprint.setText("");
                    }
                } else {
                    //don't need to notify people if there is nothing to show here
//                    Toast.makeText(this, "OTR is not initialized yet", Toast.LENGTH_SHORT).show();
                }

            } catch (Exception e) {
                Log.e(ImApp.LOG_TAG, "error on create", e);

            }
        }

    }*/

    private void checkUserChanged() {
        String username = mEditUserAccount.getText().toString().trim();

        if ((!username.equals(mOriginalUserAccount)) && parseAccount(username)) {
            //Log.i(TAG, "Username changed: " + mOriginalUserAccount + " != " + username);
            settingsForDomain(mDomain, mPort);
            mOriginalUserAccount = username;
            
        }
        
        
        
    }
    
    boolean parseAccount(String userField) {
        boolean isGood = true;
        String[] splitAt = userField.trim().split("@");
        mUserName = splitAt[0];
        mDomain = "";
        mPort = 0;

        if (splitAt.length > 1) {
            mDomain = splitAt[1].toLowerCase();
            String[] splitColon = mDomain.split(":");
            mDomain = splitColon[0];
            if (splitColon.length > 1) {
                try {
                    mPort = Integer.parseInt(splitColon[1]);
                } catch (NumberFormatException e) {
                    // TODO move these strings to strings.xml
                    isGood = false;
                    Toast.makeText(
                            AccountActivity.this,
                            "The port value '" + splitColon[1]
                                    + "' after the : could not be parsed as a number!",
                            Toast.LENGTH_LONG).show();
                }
            }
        }

        //its okay if domain is null;
        
//        if (mDomain == null) {
  //          isGood = false;
            //Toast.makeText(AccountActivity.this, 
            //	R.string.account_wizard_no_domain_warning,
            //	Toast.LENGTH_LONG).show();
    //    } 
        /*//removing requirement of a . in the domain
        else if (mDomain.indexOf(".") == -1) { 
            isGood = false;
            //	Toast.makeText(AccountActivity.this, 
            //		R.string.account_wizard_no_root_domain_warning,
            //	Toast.LENGTH_LONG).show();
        }*/

        return isGood;
    }

    /*
     * If we know the direct XMPP server for a domain, we should turn off DNS lookup
     * because it is slow, error prone, and a way to leak information from third parties
     */
    void settingsForDomain(String domain,int port) {

        ContentResolver cr = getContentResolver();
        Cursor pCursor = cr.query(Imps.ProviderSettings.CONTENT_URI,new String[] {Imps.ProviderSettings.NAME, Imps.ProviderSettings.VALUE},Imps.ProviderSettings.PROVIDER + "=?",new String[] { Long.toString(mProviderId)},null);

        Imps.ProviderSettings.QueryMap settings = new Imps.ProviderSettings.QueryMap(
                pCursor, cr, mProviderId, false /* don't keep updated */, null /* no handler */);
    
        settingsForDomain(domain, port, settings);
   
        settings.close();
   
    }

    private void settingsForDomain(String domain, int port, Imps.ProviderSettings.QueryMap settings) {
        if (domain.equals("gmail.com")) {
            // Google only supports a certain configuration for XMPP:
            // http://code.google.com/apis/talk/open_communications.html
            
            settings.setDoDnsSrv(false);
            settings.setServer(DEFAULT_SERVER_GOOGLE);            
            settings.setDomain(domain);
            settings.setPort(DEFAULT_PORT);
            settings.setRequireTls(true);
            settings.setTlsCertVerify(true);
            settings.setAllowPlainAuth(false);
        } 
        //mEditPass can be NULL if this activity is used in "headless" mode for auto account setup
        else if (mEditPass != null && mEditPass.getText().toString().startsWith(GTalkOAuth2.NAME))
        {
            //this is not @gmail but IS a google account
            settings.setDoDnsSrv(false);
            settings.setServer(DEFAULT_SERVER_GOOGLE); //set the google connect server
            settings.setDomain(domain);
            settings.setPort(DEFAULT_PORT);
            settings.setRequireTls(true);
            settings.setTlsCertVerify(true);
            settings.setAllowPlainAuth(false);
        }
        else if (domain.equals("jabber.org")) {
            settings.setDoDnsSrv(false);
            settings.setServer(DEFAULT_SERVER_JABBERORG);            
            settings.setDomain(domain);
            settings.setPort(DEFAULT_PORT);            
            settings.setRequireTls(true);
            settings.setTlsCertVerify(true);
            settings.setAllowPlainAuth(false);
        } else if (domain.equals("facebook.com")) {
            settings.setDoDnsSrv(false);
            settings.setDomain(DEFAULT_SERVER_FACEBOOK);
            settings.setPort(DEFAULT_PORT);
            settings.setServer(DEFAULT_SERVER_FACEBOOK);
            settings.setRequireTls(true); //facebook TLS now seems to be on
            settings.setTlsCertVerify(true); //but cert verify can still be funky - off by default
            settings.setAllowPlainAuth(false);
        } 
        else if (domain.equals("jabber.calyxinstitute.org")) {
            
            if (settings.getUseTor())
            {                
                settings.setDoDnsSrv(false);
                settings.setServer(ONION_CALYX);
            }
            else
            {
                settings.setDoDnsSrv(false);
                settings.setServer("");
            }
            
            settings.setDomain(domain);
            settings.setPort(DEFAULT_PORT);            
            settings.setRequireTls(true);
            settings.setTlsCertVerify(true);
            settings.setAllowPlainAuth(false);
        } 
        else if (domain.equals("jabber.ccc.de")) {
            
            if (settings.getUseTor())
            {                
                settings.setDoDnsSrv(false);
                settings.setServer(ONION_JABBERCCC);
            }
            else
            {
                settings.setDoDnsSrv(true);
                settings.setServer("");
            }
            
            settings.setDomain(domain);
            settings.setPort(DEFAULT_PORT);            
            settings.setRequireTls(true);
            settings.setTlsCertVerify(true);
            settings.setAllowPlainAuth(false);
        }        
        else {
          
            settings.setDomain(domain);
            settings.setPort(port);
            
            //if use Tor, turn off DNS resolution, and set Server manually from Domain
            if (settings.getUseTor())
            {
                settings.setDoDnsSrv(false);
                
                //if Tor is off, and the user has not provided any values here, set to the @domain
                if (settings.getServer() == null || settings.getServer().length() == 0)
                    settings.setServer(domain);
            }
            else if (settings.getServer() == null || settings.getServer().length() == 0)
            {
                //if Tor is off, and the user has not provided any values here, then reset to nothing
                settings.setDoDnsSrv(true);
                settings.setServer("");
            }
            
            settings.setRequireTls(true);
            settings.setTlsCertVerify(true);
            settings.setAllowPlainAuth(false);
        }
        
        settings.requery();
    }

    void confirmTermsOfUse(BrandingResources res, DialogInterface.OnClickListener accept) {
        SpannableString message = new SpannableString(
                res.getString(BrandingResourceIDs.STRING_TOU_MESSAGE));
        Linkify.addLinks(message, Linkify.ALL);

        new AlertDialog.Builder(this).setIcon(android.R.drawable.ic_dialog_alert)
                .setTitle(res.getString(BrandingResourceIDs.STRING_TOU_TITLE)).setMessage(message)
                .setPositiveButton(res.getString(BrandingResourceIDs.STRING_TOU_DECLINE), null)
                .setNegativeButton(res.getString(BrandingResourceIDs.STRING_TOU_ACCEPT), accept)
                .show();
    }

    boolean shouldShowTermOfUse(BrandingResources res) {
        return !TextUtils.isEmpty(res.getString(BrandingResourceIDs.STRING_TOU_MESSAGE));
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        mAccountUri = savedInstanceState.getParcelable(ACCOUNT_URI_KEY);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelable(ACCOUNT_URI_KEY, mAccountUri);
    }

   
