package org.radarcns.gateway;

import java.net.URI

class Config {
    var baseUri: URI = URI.create("http://0.0.0.0:8080/radar-gateway/")
    var restProxyUrl: String = "http://rest-proxy-1:8082"
    var managementPortalUrl: String = "http://managementportal-app:8080/managementportal/"
}
