package org.radarcns.gateway.oauth;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import okhttp3.Authenticator;
import okhttp3.Credentials;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.Route;
import org.radarcns.gateway.model.RadarUserToken;
import org.radarcns.security.commons.config.ServerConfig;
import org.radarcns.security.commons.model.RadarToken;
import org.radarcns.security.commons.model.RadarUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by joris on 04/05/2017.
 */
public class OAuth2Handler {
    private static final Logger log = LoggerFactory.getLogger(OAuth2Handler.class);
    private final ServerConfig config;

    public OAuth2Handler(ServerConfig config) {
        this.config = config;
    }

    public RadarUserToken authorizeAccessToken(String token)
            throws IOException, OAuth2AuthorizationException {
        JsonObject object = this.callIntrospectionEndpoint(token);
        log.info("JSON object {}", object);
        RadarUserToken radarToken = new RadarUserToken();
        JsonElement usernameObject = object.get("username");
        radarToken.setToken(token);
        if (usernameObject != null) {
            radarToken.setUser(usernameObject.getAsString());
        }
        JsonElement scopeObject = object.get("scope");
        if (scopeObject != null) {
            List<String> tokenScopes = Arrays.asList(scopeObject.getAsString().split(" "));
            radarToken.setScopes(tokenScopes);
        }
        return radarToken;
    }

    public RadarUser getUserForToken(RadarToken token) throws IOException, OAuth2AuthorizationException {
        JsonObject object = this.callIntrospectionEndpoint(token.getToken());
        return (new RadarUser()).setUserName(object.get("username").getAsString());
    }

    protected JsonObject callIntrospectionEndpoint(String token)
            throws IOException, OAuth2AuthorizationException {
        OkHttpClient client = new OkHttpClient.Builder()
                .authenticator(new Authenticator() {
                    public Request authenticate(Route route, Response response) throws IOException {
                        String credential = Credentials.basic(config.getUsername(), config.getPassword());
                        return response.request().newBuilder()
                                .header("Authorization", credential)
                                .build();
                    }
                }).build();
        RequestBody form = new FormBody.Builder()
                .add("token", token)
                .build();
        Request request = new Request.Builder()
                .url(this.config.getUrl())
                .post(form)
                .build();

        log.debug("Checking validity of token at URL " + this.config.getUrlString());
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Unexpected code " + response);
            } else {
                JsonElement tokenSpec = (new JsonParser()).parse(response.body().string().trim());
                if (!tokenSpec.isJsonObject()) {
                    throw new IOException("Failed to communicate with authentication server: "
                            + "received response does not appear to be a JSON object");
                } else {
                    JsonObject object = tokenSpec.getAsJsonObject();
                    this.checkJsonResponse(object);
                    return object;
                }
            }
        }
    }

    protected void checkJsonResponse(JsonObject object)
            throws OAuth2AuthorizationException, IOException {
        if (object.has("error")) {
            throw new OAuth2AuthorizationException("invalid_request",
                    "Received error from identity server: " + object.get("error").getAsString());
        } else {
            if (!object.has("active")) {
                throw new IOException("Got incomplete response from the identity server "
                        + "(missing field active)");
            }
            if (!object.get("active").getAsBoolean()) {
                throw new OAuth2AuthorizationException("invalid_token", "The access token expired");
            } else {
                String[] expectedFields = {"token_type", "client_id", "username"};

                for (String field : expectedFields) {
                    if (!object.has(field)) {
                        throw new IOException("Got incomplete response from the identity server "
                                + "(missing field " + field + ")");
                    }
                }

                if (!object.get("token_type").getAsString().equals("Bearer")) {
                    throw new OAuth2AuthorizationException("invalid_request",
                            "Supplied token is not of Bearer type");
                }
            }
        }
    }
}