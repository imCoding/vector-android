/*
 * Copyright 2016 OpenMarket Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package im.vector.receiver;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

import java.net.URLDecoder;
import java.util.HashMap;
import java.util.Set;

import im.vector.activity.LoginActivity;

@SuppressLint("LongLogTag")
public class VectorRegistrationReceiver extends BroadcastReceiver {
    private static final String LOG_TAG = "VectorRegistrationReceiver";

    public static final String BROADCAST_ACTION_REGISTRATION = "im.vector.receiver.BROADCAST_ACTION_REGISTRATION";

    // Broadcast Extras
    public static final String EXTRA_EMAIL_VALIDATION_PARAMS = "EXTRA_EMAIL_VALIDATION_PARAMS";

    // Supported path
    public static final String SUPPORTED_PATH_ACCOUNT_EMAIL_VALIDATION = "/_matrix/identity/api/v1/validate/email/submitToken";
    public static final String SUPPORTED_HOST = "vector.im";

    // mail validation url query parameters
    // Examples:
    // mail validation url = https://vector.im/_matrix/identity/api/v1/validate/email/submitToken?token=815159&client_secret=8033cc24-0312-4c65-a9cd-bb70cea44828&sid=3643&nextLink=...
    // nextLink = https://vector.im/develop/#/register?client_secret=8033cc24-ĸ-4c65-a9cd-bb70cea44828&hs_url=https://matrix.org&is_url=https://vector.im&session_id=gRVxdjiMTAfHIRUMtiDvaNMa&sid=3643
    public static final String KEY_MAIL_VALIDATION_TOKEN = "token";
    public static final String KEY_MAIL_VALIDATION_CLIENT_SECRET = "client_secret";
    public static final String KEY_MAIL_VALIDATION_IDENTITY_SERVER_SESSION_ID = "sid";
    public static final String KEY_MAIL_VALIDATION_NEXT_LINK = "nextLink";
    public static final String KEY_MAIL_VALIDATION_HOME_SERVER_URL = "hs_url";
    public static final String KEY_MAIL_VALIDATION_IDENTITY_SERVER_URL = "is_url";
    public static final String KEY_MAIL_VALIDATION_SESSION_ID = "session_id";

    public VectorRegistrationReceiver() {
    }

    @Override
    public void onReceive(final Context aContext, final Intent aIntent) {
        String action;
        Uri intentUri;

        Log.d(LOG_TAG, "## onReceive() IN");

        // sanity check
        if (null != aIntent) {
            action = aIntent.getAction();

            // test action received
            if (!TextUtils.equals(action, BROADCAST_ACTION_REGISTRATION)) {
                Log.e(LOG_TAG, "## onReceive() Error - not supported action =" + action);
            } else if (null == (intentUri = aIntent.getData())) {
                Log.e(LOG_TAG, "## onReceive() Error - Uri is null");
            } else {

                // test if URI path is allowed
                if (SUPPORTED_PATH_ACCOUNT_EMAIL_VALIDATION.equals(intentUri.getPath())) {
                    // account registration URL set in a mail:
                    HashMap<String, String> mailRegParams = parseMailRegistrationLink(intentUri);

                    // build Login intent
                    Intent intent = new Intent(aContext, LoginActivity.class);
                    intent.putExtra(EXTRA_EMAIL_VALIDATION_PARAMS, mailRegParams);
                    intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                    aContext.startActivity(intent);
                } else {
                    Log.e(LOG_TAG, "## onReceive() Error - received path not supported: " + intentUri.getPath());
                }
            }
        }
    }

    /**
     * Parse the URL sent in the email validation.
     * This flow flow is part of the registration process {@see <a href="http://matrix.org/speculator/spec/HEAD/identity_service.html">Indenty spec server</a>}:
     * https://vector.im/_matrix/identity/api/v1/validate/email/submitToken?token=172230&client_secret=3a164877-1f6a-4aa3-a056-0dc20ebe6392&sid=3672&nextLink=https%3A//vector.im/develop/%23/register%3Fclient_secret%3D3a164877-1f6a-4aa3-a056-0dc20ebe6392%26hs_url%3Dhttps%3A//matrix.org%26is_url%3Dhttps%3A//vector.im%26session_id%3DipLKXEvRArNFZkDVpIZvqJMa%26sid%3D3672
     *
     * @param uri
     * @return
     */
    public static HashMap<String, String> parseMailRegistrationLink(Uri uri) {
        HashMap<String, String> mapParams = new HashMap<>();

        try {
            // sanity check
            if ((null == uri) || TextUtils.isEmpty(uri.getPath())) {
                Log.e(LOG_TAG, "## parseMailRegistrationLink : null");
            } else if (!SUPPORTED_PATH_ACCOUNT_EMAIL_VALIDATION.equals(uri.getPath())) {
                Log.e(LOG_TAG, "## parseUniversalLink : not supported");
            } else {
                String uriFragment, host=uri.getHost();
                Log.i(LOG_TAG,"## parseMailRegistrationLink(): host="+host);

                if (!TextUtils.equals(host, SUPPORTED_HOST)) {
                    Log.e(LOG_TAG, "## parseUniversalLink : unsupported host ="+host);
                    return null;
                }

                // remove the server part
                uriFragment = uri.getFragment();
                String lastFrag = uri.getLastPathSegment();
                String specPart = uri.getSchemeSpecificPart();
                Log.i(LOG_TAG,"## parseMailRegistrationLink(): uriFragment="+uriFragment);
                Log.i(LOG_TAG, "## parseMailRegistrationLink(): getLastPathSegment()=" + lastFrag);
                Log.i(LOG_TAG, "## parseMailRegistrationLink(): getSchemeSpecificPart()=" + specPart );

                Uri nextLinkUri=null;
                Set<String> names = uri.getQueryParameterNames();
                for (String name : names) {
                    String value = uri.getQueryParameter(name);

                    if(KEY_MAIL_VALIDATION_NEXT_LINK.equals(name)){
                        // remove "#" to allow query params parsing
                        nextLinkUri = Uri.parse(value.replace("#/",""));
                    }
                    
                    try {
                        value = URLDecoder.decode(value, "UTF-8");
                    } catch (Exception e) {
                        Log.e(LOG_TAG, "## parseUniversalLink : Exception - parse query params Msg="+e.getLocalizedMessage());
                    }
                    mapParams.put(name, value);
                }

                // parse next link URI
                if(null !=  nextLinkUri) {

                    String nextLinkHomeServer = nextLinkUri.getQueryParameter(KEY_MAIL_VALIDATION_HOME_SERVER_URL);
                    mapParams.put(KEY_MAIL_VALIDATION_HOME_SERVER_URL, nextLinkHomeServer);

                    String nextLinkIdentityServer = nextLinkUri.getQueryParameter(KEY_MAIL_VALIDATION_IDENTITY_SERVER_URL);
                    mapParams.put(KEY_MAIL_VALIDATION_IDENTITY_SERVER_URL, nextLinkIdentityServer);

                    String nextLinkSessionId = nextLinkUri.getQueryParameter(KEY_MAIL_VALIDATION_SESSION_ID);
                    mapParams.put(KEY_MAIL_VALIDATION_SESSION_ID, nextLinkSessionId);
                }

                Log.i(LOG_TAG, "## parseMailRegistrationLink(): map query=" + mapParams.toString());
            }
        } catch (Exception e) {
            mapParams = null;
            Log.e(LOG_TAG, "## parseUniversalLink : Exception - Msg=" + e.getLocalizedMessage());
        }

        return mapParams;
    }
}

