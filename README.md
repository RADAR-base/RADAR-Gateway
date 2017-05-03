# RADAR-Gateway

Gateway to the Confluent Kafka REST Proxy. It does the authorization, content validation and decompression if needed.

## Usage

Start the REST Proxy with

```shell
docker-compose up -d
```

Then run the local servlet with

```shell
./gradlew jettyRun
```

Now the gateway is accessible through <http://localhost:8080/radar-gateway/>. To use it, first set up a user in WSO2 by going to <http://localhost:9443> and logging in with admin/admin. Now add a user and add a service provider. In the latter, specify Inbound Authentication Configuration -> OAuth/OpenID Connect Configuration and copy the OAuth Client Key and OAuth Client Secret. First get a OAuth token by doing

```shell
curl -u oauthClientKey:oauthClientSecret -k -d "grant_type=password&username=myUser&password=myPassword&scope=scope1" -H "Content-Type:application/x-www-form-urlencoded" https://localhost:9443/oauth2/token
```
This will return
```json
{"access_token":"myAccessToken","refresh_token":"myRefreshToken","scope":"scope1","token_type":"Bearer","expires_in":3600}
```

Now you can access the REST proxy via the gateway:
```shell
curl -H 'Authorization: Bearer myAccessToken' http://localhost:8080/radar-gateway/topics
```
