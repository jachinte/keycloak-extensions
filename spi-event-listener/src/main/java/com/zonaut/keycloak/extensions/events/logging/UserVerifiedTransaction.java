package com.zonaut.keycloak.extensions.events.logging;

import static javax.ws.rs.core.HttpHeaders.CONTENT_TYPE;


import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.FileBasedConfiguration;
import org.apache.commons.configuration2.PropertiesConfiguration;
import org.apache.commons.configuration2.builder.FileBasedConfigurationBuilder;
import org.apache.commons.configuration2.builder.fluent.Parameters;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.jboss.logging.Logger;
import org.keycloak.models.AbstractKeycloakTransaction;
import org.keycloak.utils.MediaType;

public class UserVerifiedTransaction extends AbstractKeycloakTransaction {

    private static final Logger log = Logger.getLogger(UserVerifiedTransaction.class);

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
        .version(HttpClient.Version.HTTP_2)
        .connectTimeout(Duration.ofSeconds(3))
        .build();

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final Configuration CONFIG = UserVerifiedTransaction.config();

    private final UserUuidDto userUuidDto;

    public UserVerifiedTransaction(UserUuidDto userUuidDto) {
        this.userUuidDto = userUuidDto;
    }

    private static FileBasedConfiguration config() {
        final Parameters params = new Parameters();
        final File file = new File("listener.properties");
        final FileBasedConfigurationBuilder<FileBasedConfiguration> builder =
            new FileBasedConfigurationBuilder<FileBasedConfiguration>(
                PropertiesConfiguration.class
            ).configure(params.fileBased().setFile(file));
        try {
            return builder.getConfiguration();
        } catch (final ConfigurationException exception) {
            throw new RuntimeException("Error loadng config", exception);
        }
    }

    @Override
    protected void commitImpl() {
        log.info("## USER VERIFIED TRANSACTION");
        log.info("-----------------------------------------------------------");
        log.info(this.userUuidDto.toString());
        log.info("-----------------------------------------------------------");

        log.info("MyProperty: " + CONFIG.getString("myproperty"));

        // You could make a http call here and send the object.
        // When we throw an exception here, the user would not be verified when using .enlistPrepare
        //throw new RuntimeException("External call failed!");

        try {

            HttpRequest request = HttpRequest.newBuilder()
                .POST(HttpRequest.BodyPublishers.ofString(OBJECT_MAPPER.writeValueAsString(this.userUuidDto)))
                .uri(URI.create("https://httpbin.org/post"))
                .header(CONTENT_TYPE, MediaType.APPLICATION_JSON)
                .build();

            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

            log.info("#### STATUS CODE");
            log.info(response.statusCode());
            if (response.statusCode() != 200) {
                throw new RuntimeException("##### WRONG RESPONSE STATUS CODE !!! : " + response.statusCode());
            }

            log.info("### USER VERIFIED TRANSACTION SUCCESS");

            log.info("#### HEADERS");
            HttpHeaders headers = response.headers();
            headers.map().forEach((k, v) -> log.info("##### " + k + ":" + v));

            log.info("#### RESPONSE BODY");
            log.info(response.body());

        } catch (Exception e) {
            throw new RuntimeException("##### USER VERIFIED TRANSACTION FAILED !", e);
        }
    }

    @Override
    protected void rollbackImpl() {
        //
    }

}
