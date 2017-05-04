package org.radarcns.gateway.model;

import org.radarcns.security.commons.model.RadarToken;

public class RadarUserToken extends RadarToken {
    private String user;

    public String getUser() {
        return user;
    }

    public RadarUserToken setUser(String user) {
        if (user == null) {
            this.user = null;
        } else {
            if (user.contains("@")) {
                this.user = user.split("@")[0];
            } else {
                this.user = user;
            }
        }
        return this;
    }

    @Override
    public String toString() {
        return "RadarUserToken{" +
                "token='" + getToken() + "'," +
                "user='" + user + "'," +
                "scopes=" + getScopes() +
                '}';
    }
}
