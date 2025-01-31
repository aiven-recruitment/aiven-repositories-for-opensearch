/*
 * Copyright 2020 Aiven Oy
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.aiven.elasticsearch.repositories.gcs;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.nio.file.Files;

import org.opensearch.common.settings.Settings;

import io.aiven.elasticsearch.repositories.CommonSettings;
import io.aiven.elasticsearch.repositories.DummySecureSettings;
import io.aiven.elasticsearch.repositories.RsaKeyAwareTest;

import com.google.api.client.http.javanet.DefaultConnectionFactory;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.UserCredentials;
import com.google.cloud.http.HttpTransportOptions;
import org.junit.jupiter.api.Test;
import org.junit.platform.commons.support.HierarchyTraversalMode;
import org.junit.platform.commons.support.ReflectionSupport;

import static io.aiven.elasticsearch.repositories.CommonSettings.RepositorySettings.MAX_RETRIES;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GcsClientProviderTest extends RsaKeyAwareTest {

    @Test
    void providerInitialization() throws Exception {
        final var gcsClientProvider = new GcsClientProvider();
        final var settings = Settings.builder()
                .put(CommonSettings.RepositorySettings.BASE_PATH.getKey(), "base_path/")
                .put(GcsClientSettings.CONNECTION_TIMEOUT.getKey(), 1)
                .put(GcsClientSettings.READ_TIMEOUT.getKey(), 2)
                .put(GcsClientSettings.PROJECT_ID.getKey(), "some_project")
                .setSecureSettings(createFullSecureSettings()).build();

        final var repoSettings =
                Settings.builder()
                        .put("some_settings_1", 20)
                        .put("some_settings_2", 210)
                        .build();
        final var client = gcsClientProvider.buildClientIfNeeded(GcsClientSettings.create(settings), repoSettings);

        assertTrue(client.getOptions().getTransportOptions() instanceof HttpTransportOptions);
        final var httpTransportOptions = (HttpTransportOptions) client.getOptions().getTransportOptions();
        assertEquals(1, httpTransportOptions.getConnectTimeout());
        assertEquals(2, httpTransportOptions.getReadTimeout());
        assertEquals(GcsClientProvider.HTTP_USER_AGENT, client.getOptions().getUserAgent());
        assertEquals("some_project", client.getOptions().getProjectId());

        assertEquals(loadCredentials(), client.getOptions().getCredentials());
        assertEquals(3, client.getOptions().getRetrySettings().getMaxAttempts());
    }

    @Test
    void provideInitializationWithProxyConfigurationWithUsernameAndPassword() throws Exception {
        final var gcsClientProvider = new GcsClientProvider();
        final var proxySettingsWithUsernameAndPassword = Settings.builder()
                .put(CommonSettings.RepositorySettings.BASE_PATH.getKey(), "base_path/")
                .put(GcsClientSettings.CONNECTION_TIMEOUT.getKey(), 1)
                .put(GcsClientSettings.READ_TIMEOUT.getKey(), 2)
                .put(GcsClientSettings.PROXY_HOST.getKey(), "socks.test.io")
                .put(GcsClientSettings.PROXY_PORT.getKey(), 1234)
                .put(GcsClientSettings.PROJECT_ID.getKey(), "some_project")
                .setSecureSettings(createFullSecureSettingsWithProxyUsernameAndPassword()).build();
        final var repoSettings =
                Settings.builder()
                        .put("some_settings_1", 20)
                        .put("some_settings_2", 210)
                        .build();

        final var client = gcsClientProvider
                .buildClientIfNeeded(GcsClientSettings.create(proxySettingsWithUsernameAndPassword), repoSettings);

        assertTrue(client.getOptions().getTransportOptions() instanceof HttpTransportOptions);

        final var httpTransportOptions = (HttpTransportOptions) client.getOptions().getTransportOptions();
        final var netHttpTransport = (NetHttpTransport) httpTransportOptions.getHttpTransportFactory().create();

        final var proxy = extractProxy(netHttpTransport);
        final var inetSocketAddress = (InetSocketAddress) proxy.address();

        assertEquals(1, httpTransportOptions.getConnectTimeout());
        assertEquals(2, httpTransportOptions.getReadTimeout());
        assertEquals(GcsClientProvider.HTTP_USER_AGENT, client.getOptions().getUserAgent());
        assertEquals("some_project", client.getOptions().getProjectId());

        assertEquals("socks.test.io", inetSocketAddress.getHostName());
        assertEquals(1234, inetSocketAddress.getPort());
        assertEquals(loadCredentials(), client.getOptions().getCredentials());
        assertEquals(3, client.getOptions().getRetrySettings().getMaxAttempts());
    }

    @Test
    void provideInitializationWithProxyConfigurationWithoutUsernameAndPassword() throws Exception {
        final var gcsClientProvider = new GcsClientProvider();
        final var proxySettingsWithoutUsernameAndPassword = Settings.builder()
                .put(CommonSettings.RepositorySettings.BASE_PATH.getKey(), "base_path/")
                .put(GcsClientSettings.CONNECTION_TIMEOUT.getKey(), 1)
                .put(GcsClientSettings.READ_TIMEOUT.getKey(), 2)
                .put(GcsClientSettings.PROXY_HOST.getKey(), "socks5.test.io")
                .put(GcsClientSettings.PROXY_PORT.getKey(), 12345)
                .put(GcsClientSettings.PROJECT_ID.getKey(), "some_project")
                .setSecureSettings(createFullSecureSettings()).build();
        final var repoSettings =
                Settings.builder()
                        .put("some_settings_1", 20)
                        .put("some_settings_2", 210)
                        .build();
        final var client = gcsClientProvider
                .buildClientIfNeeded(GcsClientSettings.create(proxySettingsWithoutUsernameAndPassword), repoSettings);

        assertTrue(client.getOptions().getTransportOptions() instanceof HttpTransportOptions);

        final var httpTransportOptions = (HttpTransportOptions) client.getOptions().getTransportOptions();
        final var netHttpTransport = (NetHttpTransport) httpTransportOptions.getHttpTransportFactory().create();

        final var proxy = extractProxy(netHttpTransport);
        final var inetSocketAddress = (InetSocketAddress) proxy.address();

        assertEquals(1, httpTransportOptions.getConnectTimeout());
        assertEquals(2, httpTransportOptions.getReadTimeout());
        assertEquals(GcsClientProvider.HTTP_USER_AGENT, client.getOptions().getUserAgent());
        assertEquals("some_project", client.getOptions().getProjectId());

        assertEquals("socks5.test.io", inetSocketAddress.getHostName());
        assertEquals(12345, inetSocketAddress.getPort());
        assertEquals(loadCredentials(), client.getOptions().getCredentials());
        assertEquals(3, client.getOptions().getRetrySettings().getMaxAttempts());
    }

    @Test
    void providerInitializationWithDefaultValues() throws Exception {
        final var gcsClientProvider = new GcsClientProvider();
        final var settings = Settings.builder()
                .setSecureSettings(createFullSecureSettings()).build();
        final var repoSettings =
                Settings.builder()
                        .put("some_settings_1", 20)
                        .put("some_settings_2", 210)
                        .build();

        final var client = gcsClientProvider
                .buildClientIfNeeded(GcsClientSettings.create(settings), repoSettings);
        assertNotNull(client);
        assertTrue(client.getOptions().getTransportOptions() instanceof HttpTransportOptions);

        final var httpTransportOptions = (HttpTransportOptions) client.getOptions().getTransportOptions();
        assertEquals(-1, httpTransportOptions.getConnectTimeout());
        assertEquals(-1, httpTransportOptions.getReadTimeout());
        assertEquals(GcsClientProvider.HTTP_USER_AGENT, client.getOptions().getUserAgent());
        assertEquals(3, client.getOptions().getRetrySettings().getMaxAttempts());
        //skip project id since GCS client returns default one

        assertEquals(loadCredentials(), client.getOptions().getCredentials());
    }

    @Test
    void testMaxRetriesOverridesClientSettings() throws IOException {
        final var gcsClientProvider = new GcsClientProvider();
        final var settings = Settings.builder()
                .setSecureSettings(createFullSecureSettings()).build();
        final var repoSettings =
                Settings.builder()
                        .put(MAX_RETRIES.getKey(), 20)
                        .put("some_settings_2", 210)
                        .build();

        final var client = gcsClientProvider
                .buildClientIfNeeded(GcsClientSettings.create(settings), repoSettings);
        assertNotNull(client);
        assertTrue(client.getOptions().getTransportOptions() instanceof HttpTransportOptions);

        final var httpTransportOptions = (HttpTransportOptions) client.getOptions().getTransportOptions();
        assertEquals(-1, httpTransportOptions.getConnectTimeout());
        assertEquals(-1, httpTransportOptions.getReadTimeout());
        assertEquals(GcsClientProvider.HTTP_USER_AGENT, client.getOptions().getUserAgent());
        assertEquals(20, client.getOptions().getRetrySettings().getMaxAttempts());
        //skip project id since GCS client returns default one

        assertEquals(loadCredentials(), client.getOptions().getCredentials());
    }

    private Proxy extractProxy(final NetHttpTransport netHttpTransport) throws Exception {
        final var connectionFactoryField = ReflectionSupport.findFields(NetHttpTransport.class, f -> f
                        .getName().equals("connectionFactory"),
                HierarchyTraversalMode.TOP_DOWN).get(0);
        connectionFactoryField.setAccessible(true);
        final var connectionFactoryObj = connectionFactoryField.get(netHttpTransport);
        final var proxyField = ReflectionSupport.findFields(DefaultConnectionFactory.class, f -> f
                        .getName().equals("proxy"),
                HierarchyTraversalMode.TOP_DOWN).get(0);
        proxyField.setAccessible(true);
        return (Proxy) proxyField.get(connectionFactoryObj);
    }

    private DummySecureSettings createFullSecureSettings() throws IOException {
        return new DummySecureSettings()
                .setFile(
                        GcsClientSettings.CREDENTIALS_FILE_SETTING.getKey(),
                        getClass().getClassLoader().getResourceAsStream("test_gcs_creds.json"))
                .setFile(GcsClientSettings.PRIVATE_KEY_FILE.getKey(), Files.newInputStream(privateKeyPem))
                .setFile(GcsClientSettings.PUBLIC_KEY_FILE.getKey(), Files.newInputStream(publicKeyPem));
    }

    private DummySecureSettings createFullSecureSettingsWithProxyUsernameAndPassword() throws IOException {
        return new DummySecureSettings()
                .setFile(
                        GcsClientSettings.CREDENTIALS_FILE_SETTING.getKey(),
                        getClass().getClassLoader().getResourceAsStream("test_gcs_creds.json"))
                .setFile(GcsClientSettings.PRIVATE_KEY_FILE.getKey(), Files.newInputStream(privateKeyPem))
                .setFile(GcsClientSettings.PUBLIC_KEY_FILE.getKey(), Files.newInputStream(publicKeyPem))
                .setString(GcsClientSettings.PROXY_USER_NAME.getKey(), "some_user_name")
                .setString(GcsClientSettings.PROXY_USER_PASSWORD.getKey(), "some_user_password");
    }

    private DummySecureSettings createNoGcsCredentialFileSettings() throws IOException {
        return new DummySecureSettings()
                .setFile(GcsClientSettings.PUBLIC_KEY_FILE.getKey(), Files.newInputStream(publicKeyPem))
                .setFile(GcsClientSettings.PRIVATE_KEY_FILE.getKey(), Files.newInputStream(privateKeyPem));
    }

    private DummySecureSettings createPublicRsaKeyOnlySecureSettings() throws IOException {
        return new DummySecureSettings()
                .setFile(
                        GcsClientSettings.CREDENTIALS_FILE_SETTING.getKey(),
                        getClass().getClassLoader().getResourceAsStream("test_gcs_creds.json"))
                .setFile(GcsClientSettings.PUBLIC_KEY_FILE.getKey(), Files.newInputStream(publicKeyPem));
    }

    private DummySecureSettings createPrivateRsaKeyOnlySecureSettings() throws IOException {
        return new DummySecureSettings()
                .setFile(
                        GcsClientSettings.CREDENTIALS_FILE_SETTING.getKey(),
                        getClass().getClassLoader().getResourceAsStream("test_gcs_creds.json"))
                .setFile(GcsClientSettings.PRIVATE_KEY_FILE.getKey(), Files.newInputStream(privateKeyPem));
    }

    GoogleCredentials loadCredentials() throws IOException {
        try (final var in = getClass().getClassLoader().getResourceAsStream("test_gcs_creds.json")) {
            return UserCredentials.fromStream(in);
        }
    }

}
