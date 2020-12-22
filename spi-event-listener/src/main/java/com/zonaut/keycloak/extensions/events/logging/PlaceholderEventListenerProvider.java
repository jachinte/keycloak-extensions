package com.zonaut.keycloak.extensions.events.logging;

import org.jboss.logging.Logger;
import org.keycloak.events.Event;
import org.keycloak.events.EventListenerProvider;
import org.keycloak.events.EventType;
import org.keycloak.events.admin.AdminEvent;
import org.keycloak.events.admin.OperationType;
import org.keycloak.events.admin.ResourceType;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.RealmProvider;
import org.keycloak.models.UserModel;

import java.io.File;
import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.FileBasedConfiguration;
import org.apache.commons.configuration2.PropertiesConfiguration;
import org.apache.commons.configuration2.builder.FileBasedConfigurationBuilder;
import org.apache.commons.configuration2.builder.fluent.Parameters;
import org.apache.commons.configuration2.ex.ConfigurationException;

public class PlaceholderEventListenerProvider implements EventListenerProvider {

    private static final Logger log = Logger.getLogger(PlaceholderEventListenerProvider.class);

    private static final Configuration CONFIG = PlaceholderEventListenerProvider.config();

    private final KeycloakSession session;
    private final RealmProvider model;

    public PlaceholderEventListenerProvider(KeycloakSession session) {
        this.session = session;
        this.model = session.realms();
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
    public void onEvent(Event event) {
        log.infof("## NEW %s EVENT", event.getType());
        log.info("-----------------------------------------------------------");
        event.getDetails().forEach((key, value) -> log.info(key + ": " + value));

        log.info("MyProperty: " + CONFIG.getString("myproperty"));

        // USE CASE SCENARIO, I'm sure there are better use case scenario's :p
        //
        // Let's assume for whatever reason you only want the user
        // to be able to verify his account if a transaction we make succeeds.
        // Let's say an external call to a service needs to return a 200 response code or we throw an exception.

        // When the user tries to login after a failed attempt,
        // the user remains unverified and when trying to login will receive another verify account email.

        if (EventType.VERIFY_EMAIL.equals(event.getType())) {
            RealmModel realm = this.model.getRealm(event.getRealmId());
            UserModel user = this.session.users().getUserById(event.getUserId(), realm);
            if (user != null && user.getEmail() != null && user.isEmailVerified()) {
                log.info("USER HAS VERIFIED EMAIL : " + event.getUserId());

                // Example of adding an attribute when this event happens
                user.setSingleAttribute("attribute-key", "attribute-value");

                UserUuidDto userUuidDto = new UserUuidDto(event.getType().name(), event.getUserId(), user.getEmail());
                UserVerifiedTransaction userVerifiedTransaction = new UserVerifiedTransaction(userUuidDto);

                // enlistPrepare -> if our transaction fails than the user is NOT verified
                // enlist -> if our transaction fails than the user is still verified
                // enlistAfterCompletion -> if our transaction fails our user is still verified

                session.getTransactionManager().enlistPrepare(userVerifiedTransaction);
            }
        }
        log.info("-----------------------------------------------------------");
    }

    @Override
    public void onEvent(AdminEvent adminEvent, boolean b) {
        log.info("## NEW ADMIN EVENT");
        log.info("-----------------------------------------------------------");
        log.info("Resource path" + ": " + adminEvent.getResourcePath());
        log.info("Resource type" + ": " + adminEvent.getResourceType());
        log.info("Operation type" + ": " + adminEvent.getOperationType());

        if (ResourceType.USER.equals(adminEvent.getResourceType())
                && OperationType.CREATE.equals(adminEvent.getOperationType())) {
            log.info("A new user has been created");
        }

        log.info("-----------------------------------------------------------");
    }

    @Override
    public void close() {
        // Nothing to close
    }

}
