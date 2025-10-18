/*
 * Copyright (c) 2025 Contributors to the Eclipse Foundation.
 * Copyright (c) 2017, 2018 Oracle and/or its affiliates. All rights reserved.
 * Copyright 2010 Ning, Inc.
 *
 * Ning licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.ning.http.client.async;

import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.AsyncHttpClientConfig.Builder;
import com.ning.http.client.Response;

import org.eclipse.jetty.http.HttpCookie;
import org.eclipse.jetty.http.HttpField;
import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.http.HttpVersion;
import org.eclipse.jetty.io.Content;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.SecureRequestCustomizer;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.SslConnectionFactory;
import org.eclipse.jetty.util.Callback;
import org.eclipse.jetty.util.Fields;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.URL;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.testng.Assert.*;

public abstract class BasicHttpsTest extends AbstractBasicTest {

    protected final Logger log = LoggerFactory.getLogger(BasicHttpsTest.class);

    public static class EchoHandler extends Handler.Abstract {

        @Override
        public boolean handle(Request request, org.eclipse.jetty.server.Response response, Callback callback)
                throws Exception {

            final HttpFields requestHeaders = request.getHeaders();
            final HttpFields.Mutable responseHeaders = response.getHeaders();

            responseHeaders.put(HttpHeader.CONTENT_TYPE, "text/html; charset=utf-8");
            String param;
            for (final HttpField field : requestHeaders) {
                param = field.getName();
                if (param.startsWith("LockThread")) {
                    try {
                        Thread.sleep(40 * 1000);
                    } catch (InterruptedException ex) { // nothing to do here
                    }
                }
                responseHeaders.put("X-" + param, field.getValue());
            }

            final Fields allParameters = Request.getParameters(request);
            final StringBuilder requestBody = new StringBuilder();
            for (final Fields.Field field : allParameters) {
                param = field.getName();
                responseHeaders.put("X-" + param, field.getValue());
                requestBody.append(param);
                requestBody.append("_");
            }

            final String pathInfo = Request.getPathInContext(request);
            if (pathInfo != null)
                responseHeaders.put("X-pathInfo", pathInfo);

            String queryString = request.getHttpURI().getQuery();
            if (queryString != null)
                responseHeaders.put("X-queryString", queryString);

            responseHeaders.put("X-KEEP-ALIVE", Request.getRemoteAddr(request) + ":" + Request.getRemotePort(request));

            final List<HttpCookie> cookies = Request.getCookies(request);
            for (final HttpCookie cookie : cookies) {
                responseHeaders.put(HttpHeader.SET_COOKIE, cookie.toString());
            }

            if (requestBody.length() > 0) {
                Content.Sink.asOutputStream(response).write(requestBody.toString().getBytes());
            }

            int size = 10 * 1024;
            if (request.getLength() > 0) {
                size = (int) request.getLength();
            }
            byte[] bytes = new byte[size];
            int pos = 0;
            if (bytes.length > 0) {
                // noinspection ResultOfMethodCallIgnored
                int read = 0;
                try (final InputStream in = Content.Source.asInputStream(request)) {
                    while (read != -1) {
                        read = in.read(bytes, pos, bytes.length - pos);
                        pos += read;
                    }
                }

                Content.Sink.asOutputStream(response).write(bytes, 0, pos + 1); // (pos + 1) because last read added -1
            }

            response.setStatus(HttpStatus.OK_200);
            Content.Sink.asOutputStream(response).flush();
            Content.Sink.asOutputStream(response).close();
            callback.succeeded();
            return true;

        }
    }

    @AfterClass(alwaysRun = true)
    public void tearDownGlobal() throws Exception {
        server.stop();
    }

    @AfterMethod(alwaysRun = true)
    public void tearDownProps() throws Exception {
        System.clearProperty("javax.net.ssl.keyStore");
        System.clearProperty("javax.net.ssl.trustStore");
    }

    protected String getTargetUrl() {
        return String.format("https://127.0.0.1:%d/foo/test", port1);
    }

    public Handler.Abstract configureHandler() throws Exception {
        return new EchoHandler();
    }

    protected int findFreePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    @BeforeClass(alwaysRun = true)
    public void setUpGlobal() throws Exception {
        server = new Server();
        port1 = findFreePort();

        HttpConfiguration https_config = new HttpConfiguration();
        https_config.setSecureScheme("https");
        https_config.setSecurePort(port1);
        https_config.setOutputBufferSize(32768);
        SecureRequestCustomizer src = new SecureRequestCustomizer();
        src.setStsMaxAge(2000);
        src.setStsIncludeSubDomains(true);
        https_config.addCustomizer(src);

        final SslContextFactory.Server sslContextFactory = new SslContextFactory.Server();

        ClassLoader cl = getClass().getClassLoader();
        // override system properties
        URL cacertsUrl = cl.getResource("ssltest-cacerts.jks");
        String trustStoreFile = new File(cacertsUrl.toURI()).getAbsolutePath();
        sslContextFactory.setTrustStorePath(trustStoreFile);
        sslContextFactory.setTrustStorePassword("changeit");
        sslContextFactory.setTrustStoreType("JKS");

        log.info("SSL certs path: {}", trustStoreFile);

        // override system properties
        URL keystoreUrl = cl.getResource("ssltest-keystore.jks");
        String keyStoreFile = new File(keystoreUrl.toURI()).getAbsolutePath();
        sslContextFactory.setKeyStorePath(keyStoreFile);
        sslContextFactory.setKeyStorePassword("changeit");
        sslContextFactory.setKeyStoreType("JKS");

        log.info("SSL keystore path: {}", keyStoreFile);

        ServerConnector connector = new ServerConnector(server,
                new SslConnectionFactory(sslContextFactory, HttpVersion.HTTP_1_1.asString()),
                new HttpConnectionFactory(https_config));
        connector.setHost("127.0.0.1");
        connector.setPort(port1);
        server.addConnector(connector);

        server.setHandler(configureHandler());
        server.start();
        log.info("Local HTTP server started successfully");
    }

    @Test(groups = { "standalone", "default_provider" })
    public void zeroCopyPostTest() throws Throwable {
        try (AsyncHttpClient client = getAsyncHttpClient(new Builder().setSSLContext(createSSLContext(new AtomicBoolean(true))).build())) {
            ClassLoader cl = getClass().getClassLoader();
            // override system properties
            URL url = cl.getResource("SimpleTextFile.txt");
            File file = new File(url.toURI());

            Future<Response> f = client.preparePost(getTargetUrl()).setBody(file).setHeader("Content-Type", "text/html").execute();
            Response resp = f.get();
            assertNotNull(resp);
            assertEquals(resp.getStatusCode(), 200);
            assertEquals(resp.getResponseBody(), "This is a simple test file");
        }
    }

    @Test(groups = { "standalone", "default_provider" })
    public void multipleSSLRequestsTest() throws Throwable {
        try (AsyncHttpClient c = getAsyncHttpClient(new Builder().setSSLContext(createSSLContext(new AtomicBoolean(true))).build())) {
            String body = "hello there";

            // once
            Response response = c.preparePost(getTargetUrl()).setBody(body).setHeader("Content-Type", "text/html").execute().get(TIMEOUT, TimeUnit.SECONDS);

            assertEquals(response.getResponseBody(), body);

            // twice
            response = c.preparePost(getTargetUrl()).setBody(body).setHeader("Content-Type", "text/html").execute().get(TIMEOUT, TimeUnit.SECONDS);

            assertEquals(response.getResponseBody(), body);
        }
    }

    @Test(groups = { "standalone", "default_provider" })
    public void multipleSSLWithoutCacheTest() throws Throwable {
        try (AsyncHttpClient client = getAsyncHttpClient(new Builder().setSSLContext(createSSLContext(new AtomicBoolean(true))).setAllowPoolingSslConnections(false).build())) {
            String body = "hello there";
            // once
            Response response = client.preparePost(getTargetUrl()).setBody(body).setHeader("Content-Type", "text/html")
                                      .setHeader("Connection", "keep-alive").execute().get();
            assertEquals(response.getResponseBody(), body);
            // twice
            response = client.preparePost(getTargetUrl()).setBody(body).setHeader("Content-Type", "text/html")
                             .setHeader("Connection", "keep-alive").execute().get();
            assertEquals(response.getResponseBody(), body);
        }
    }

    @Test(groups = { "standalone", "default_provider" })
    public void reconnectsAfterFailedCertificationPath() throws Exception {
        
        AtomicBoolean trust = new AtomicBoolean(false);
        try (AsyncHttpClient client = getAsyncHttpClient(new Builder().setSSLContext(createSSLContext(trust)).build())) {
            String body = "hello there";

            // first request fails because server certificate is rejected
            Throwable cause = null;
            try {
                client.preparePost(getTargetUrl()).setBody(body).setHeader("Content-Type", "text/html").execute().get(TIMEOUT, TimeUnit.SECONDS);
            } catch (final ExecutionException e) {
                cause = e.getCause();
            }
            assertNotNull(cause);

            // second request should succeed
            trust.set(true);
            Response response = client.preparePost(getTargetUrl()).setBody(body).setHeader("Content-Type", "text/html").execute().get(TIMEOUT, TimeUnit.SECONDS);

            assertEquals(response.getResponseBody(), body);
        }
    }

    @Test(timeOut = 2000, expectedExceptions = { Exception.class } )
    public void failInstantlyIfNotAllowedSelfSignedCertificate() throws Throwable {

        try (AsyncHttpClient client = getAsyncHttpClient(new Builder().setRequestTimeout(2000).build())) {
            try {
                client.prepareGet(getTargetUrl()).execute().get(TIMEOUT, TimeUnit.SECONDS);
            } catch (ExecutionException e) {
                throw e.getCause() != null ? e.getCause() : e;
            }
        }
    }
 
    private static KeyManager[] createKeyManagers() throws GeneralSecurityException, IOException {
        try (InputStream keyStoreStream = Thread.currentThread().getContextClassLoader().getResourceAsStream("ssltest-cacerts.jks")) {
            char[] keyStorePassword = "changeit".toCharArray();
            KeyStore ks = KeyStore.getInstance("JKS");
            ks.load(keyStoreStream, keyStorePassword);
            assert(ks.size() > 0);
    
            // Set up key manager factory to use our key store
            char[] certificatePassword = "changeit".toCharArray();
            KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
            kmf.init(ks, certificatePassword);
    
            // Initialize the SSLContext to work with our key managers.
            return kmf.getKeyManagers();
        }
    }

    private static TrustManager[] createTrustManagers() throws GeneralSecurityException, IOException {
        try (InputStream keyStoreStream = Thread.currentThread().getContextClassLoader().getResourceAsStream("ssltest-keystore.jks")) {
            char[] keyStorePassword = "changeit".toCharArray();
            KeyStore ks = KeyStore.getInstance("JKS");
            ks.load(keyStoreStream, keyStorePassword);
            assert(ks.size() > 0);
    
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(ks);
            return tmf.getTrustManagers();
        }
    }

    public static SSLContext createSSLContext(AtomicBoolean trust) {
        try {
            KeyManager[] keyManagers = createKeyManagers();
            TrustManager[] trustManagers = new TrustManager[] { dummyTrustManager(trust, (X509TrustManager) createTrustManagers()[0]) };
            SecureRandom secureRandom = new SecureRandom();

            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(keyManagers, trustManagers, secureRandom);

            return sslContext;
        } catch (Exception e) {
            throw new Error("Failed to initialize the server-side SSLContext", e);
        }
    }

    public static class DummyTrustManager implements X509TrustManager {

        private final X509TrustManager tm;
        private final AtomicBoolean trust;

        public DummyTrustManager(final AtomicBoolean trust, final X509TrustManager tm) {
            this.trust = trust;
            this.tm = tm;
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType)
                throws CertificateException {
            tm.checkClientTrusted(chain, authType);
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType)
                throws CertificateException {
            if (!trust.get()) {
                throw new CertificateException("Server certificate not trusted.");
            }
            tm.checkServerTrusted(chain, authType);
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return tm.getAcceptedIssuers();
        }
    }

    private static TrustManager dummyTrustManager(final AtomicBoolean trust, final X509TrustManager tm) {
        return new DummyTrustManager(trust, tm);
    }
}
