package com.avioconsulting.mule.connector.akv.provider.internal.connection;

import org.mule.runtime.api.connection.ConnectionException;
import org.mule.runtime.api.exception.MuleException;
import org.mule.runtime.api.lifecycle.Initialisable;
import org.mule.runtime.api.lifecycle.Startable;
import org.mule.runtime.api.lifecycle.Stoppable;
import org.mule.runtime.api.tls.TlsContextFactory;
import org.mule.runtime.extension.api.annotation.param.Parameter;
import org.mule.runtime.extension.api.annotation.param.Optional;
import org.mule.runtime.api.connection.ConnectionValidationResult;
import org.mule.runtime.api.connection.PoolingConnectionProvider;
import org.mule.runtime.api.connection.ConnectionProvider;
import org.mule.runtime.api.connection.CachedConnectionProvider;
import org.mule.runtime.extension.api.annotation.param.RefName;
import org.mule.runtime.extension.api.annotation.param.display.DisplayName;

import org.mule.runtime.extension.api.annotation.param.display.Password;
import org.mule.runtime.http.api.HttpService;
import org.mule.runtime.http.api.client.HttpClient;
import org.mule.runtime.http.api.client.HttpClientConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;


/**
 * This class (as it's name implies) provides connection instances and the funcionality to disconnect and validate those
 * connections.
 * <p>
 * All connection related parameters (values required in order to create a connection) must be
 * declared in the connection providers.
 * <p>
 * This particular example is a {@link PoolingConnectionProvider} which declares that connections resolved by this provider
 * will be pooled and reused. There are other implementations like {@link CachedConnectionProvider} which lazily creates and
 * caches connections or simply {@link ConnectionProvider} if you want a new connection each time something requires one.
 */
public class AkvConnectionProvider implements CachedConnectionProvider<AkvConnection>, Startable, Stoppable {

    private static final Logger LOGGER = LoggerFactory.getLogger(AkvConnectionProvider.class);

    @Inject
    private HttpService httpService;
    private HttpClient httpClient;

    /**
     * A parameter that is always required to be configured.
     */
    @Parameter
    @DisplayName("Azure OAuth Base URI")
    @Optional(defaultValue = "https://login.microsoftonline.com/")
    private String baseUri;

    @Parameter
    @DisplayName("Azure Tenant Id")
    private String tenantId;

    @Parameter
    @DisplayName("Service account client id")
    private String clientId;

    @Parameter
    @DisplayName("Service account client secret")
    @Password
    private String clientSecret;

    @Parameter
    @DisplayName("Optional timeout in milliseconds for all Azure API calls")
    @Optional
    private Integer timeout;

    @Parameter
    @Optional
    private TlsContextFactory tlsContextFactory;

    @RefName
    private String configName;


    @Override
    public AkvConnection connect() throws ConnectionException {
        return new AkvConnection(httpClient, baseUri, tenantId, clientId, clientSecret, timeout);
    }

    @Override
    public void disconnect(AkvConnection connection) {
        try {
            connection.disconnect();
        } catch (Exception e) {
            LOGGER.error("Error while disconnecting [" + connection.getId() + "]: " + e.getMessage(), e);
        }
    }

    @Override
    public ConnectionValidationResult validate(AkvConnection connection) {
        if (connection.isValid()) {
            return ConnectionValidationResult.success();
        } else {
            return ConnectionValidationResult.failure("Connection Invalid", null);
        }
    }

    @Override
    public void start() throws MuleException {
        if (tlsContextFactory instanceof Initialisable) {
            ((Initialisable) tlsContextFactory).initialise();
        }
        HttpClientConfiguration.Builder builder = new HttpClientConfiguration.Builder();
        if (tlsContextFactory != null) {
            builder.setTlsContextFactory(tlsContextFactory);
        }
        httpClient = httpService.getClientFactory().create(builder.setName(configName).build());
        httpClient.start();
    }

    @Override
    public void stop() throws MuleException {
        httpClient.stop();
    }
}