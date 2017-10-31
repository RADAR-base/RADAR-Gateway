# RADAR-Gateway

Gateway to the Confluent Kafka REST Proxy. It does the authentication and authorization, content 
validation and decompression if needed.

## Configuration

The [RADAR-Auth] library is used for authentication and authorization of users. Refer to the
documentation there for a full description of the configuration options.

## Usage

Start the REST Proxy with

```shell
docker-compose up -d
```

Then run the local servlet with

```shell
./gradlew jettyRun
```

Now the gateway is accessible through <http://localhost:8080/radar-gateway/>.

Now you can access the REST proxy via the gateway:
```shell
curl -H 'Authorization: Bearer myAccessToken' http://localhost:8080/radar-gateway/topics
```

The gateway does content validation for posted data. It requires to use the Avro format with JSON
serialization, using the `application/vnd.kafka.avro.v1+json` or 
`application/vnd.kafka.avro.v2+json` media types, as described in the [REST Proxy documentation].
It also requires messages to have both a key and a value, and if the key contains a user ID, it 
needs to match the user ID that is used in the for authentication.

Data compressed with GZIP is decompressed if the `Content-Encoding: gzip` header is provided. With
`curl`, use the `-H "Content-Encoding: gzip" --data-binary @data.json.gz` flags.


[REST Proxy documentation]: http://docs.confluent.io/3.0.0/kafka-rest/docs/intro.html#produce-and-consume-avro-messages
[RADAR-Auth]: https://github.com/RADAR-CNS/ManagementPortal/tree/dev/radar-auth