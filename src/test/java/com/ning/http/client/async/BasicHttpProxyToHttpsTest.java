/*
 * Copyright (c) 2025 Contributors to the Eclipse Foundation.
 * Copyright (c) 2017, 2018 Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2016 AsyncHttpClient Project. All rights reserved.
 *
 * This program is licensed to you under the Apache License Version 2.0,
 * and you may not use this file except in compliance with the Apache License Version 2.0.
 * You may obtain a copy of the Apache License Version 2.0 at
 *     http://www.apache.org/licenses/LICENSE-2.0.
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the Apache License Version 2.0 is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Apache License Version 2.0 for the specific language governing permissions and limitations there under.
 */

package com.ning.http.client.async;

import static com.ning.http.client.async.BasicHttpsTest.createSSLContext;

import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.AsyncHttpClientConfig;
import com.ning.http.client.ProxyServer;
import com.ning.http.client.Realm;
import com.ning.http.client.Realm.AuthScheme;
import com.ning.http.client.Request;
import com.ning.http.client.RequestBuilder;
import com.ning.http.client.Response;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.UnknownHostException;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.http.HttpVersion;
import org.eclipse.jetty.io.Content;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.SecureRequestCustomizer;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.SslConnectionFactory;
import org.eclipse.jetty.util.Callback;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * Test that validates that when having an HTTP proxy and trying to access an HTTPS through the proxy the
 * proxy credentials should be passed during the CONNECT request.
 */
public abstract class BasicHttpProxyToHttpsTest extends AbstractBasicTest {

    private Server server2;

    @AfterClass(alwaysRun = true)
    public void tearDownGlobal() throws Exception {
        try {
            server.stop();
        } catch (Exception e) {
            // Nothing to do
        }
        try
        {
            server2.stop();
        } catch (Exception e) {
            // Nothing to do
        }
    }

    @AfterMethod(alwaysRun = true)
    public void tearDownProps() throws Exception {
        System.clearProperty("javax.net.ssl.keyStore");
    }

    @BeforeClass(alwaysRun = true)
    public void setUpGlobal() throws Exception {
        // HTTP Proxy Server
        server = new Server();
        // HTTPS Server
        server2 = new Server();

        port1 = findFreePort();
        port2 = findFreePort();

        // Proxy Server configuration
        ServerConnector listener = new ServerConnector(server);
        listener.setHost("127.0.0.1");
        listener.setPort(port1);
        server.addConnector(listener);
        server.setHandler(configureHandler());
        server.start();

        // HTTPS Server
        HttpConfiguration https_config = new HttpConfiguration();
        https_config.setSecureScheme("https");
        https_config.setSecurePort(port2);
        https_config.setOutputBufferSize(32768);
        SecureRequestCustomizer src = new SecureRequestCustomizer();
        src.setStsMaxAge(2000);
        src.setStsIncludeSubDomains(true);
        https_config.addCustomizer(src);

        SslContextFactory.Server sslContextFactory = new SslContextFactory.Server();
        ClassLoader cl = getClass().getClassLoader();
        URL cacertsUrl = cl.getResource("ssltest-cacerts.jks");
        String trustStoreFile = new File(cacertsUrl.toURI()).getAbsolutePath();
        sslContextFactory.setTrustStorePath(trustStoreFile);
        sslContextFactory.setTrustStorePassword("changeit");
        sslContextFactory.setTrustStoreType("JKS");

        log.info("SSL certs path: {}", trustStoreFile);

        URL keystoreUrl = cl.getResource("ssltest-keystore.jks");
        String keyStoreFile = new File(keystoreUrl.toURI()).getAbsolutePath();
        sslContextFactory.setKeyStorePath(keyStoreFile);
        sslContextFactory.setKeyStorePassword("changeit");
        sslContextFactory.setKeyStoreType("JKS");

        log.info("SSL keystore path: {}", keyStoreFile);

        ServerConnector connector = new ServerConnector(server2,
                new SslConnectionFactory(sslContextFactory, HttpVersion.HTTP_1_1.asString()),
                new HttpConnectionFactory(https_config));
        connector.setHost("127.0.0.1");
        connector.setPort(port2);
        server2.addConnector(connector);
        server2.setHandler(new AuthenticateHandler(new EchoHandler()));
        server2.start();
        log.info("Local Proxy Server (" + port1 + "), HTTPS Server (" + port2 + ") started successfully");
    }

    @Override
    public Handler.Abstract configureHandler() throws Exception {
        return new ProxyConnectHTTPHandler(new EchoHandler());
    }

    @Test
    public void httpProxyToHttpsUsePreemptiveTargetTest() throws IOException, InterruptedException, ExecutionException, NoSuchAlgorithmException {
        doTest(true);
    }

    @Test
    public void httpProxyToHttpsTargetTest() throws IOException, InterruptedException, ExecutionException, NoSuchAlgorithmException {
        doTest(false);
    }

    private void doTest(boolean usePreemptiveAuth) throws UnknownHostException, InterruptedException, ExecutionException
    {
        try (AsyncHttpClient client = getAsyncHttpClient(new AsyncHttpClientConfig.Builder().setSSLContext(createSSLContext(new AtomicBoolean(true))).build())) {
            Request request = new RequestBuilder("GET")
                .setProxyServer(basicProxy())
                .setUrl(getTargetUrl2())
                .setRealm(new Realm.RealmBuilder()
                              .setPrincipal("user")
                              .setPassword("passwd")
                              .setScheme(AuthScheme.BASIC)
                              .setUsePreemptiveAuth(usePreemptiveAuth)
                              .build())
                .build();
            Future<Response> responseFuture = client.executeRequest(request);
            Response response = responseFuture.get();
            Assert.assertEquals(response.getStatusCode(), 200);
            Assert.assertEquals("127.0.0.1:" + port2, response.getHeader("x-host"));
        }
    }

    private ProxyServer basicProxy() throws UnknownHostException {
        ProxyServer proxyServer = new ProxyServer("127.0.0.1", port1, "johndoe", "pass");
        proxyServer.setScheme(AuthScheme.BASIC);
        return proxyServer;
    }

    private static class ProxyConnectHTTPHandler extends org.eclipse.jetty.server.handler.ConnectHandler {

        public ProxyConnectHTTPHandler(Handler handler) {
            super(handler);
        }

        @Override
        protected boolean handleAuthentication(org.eclipse.jetty.server.Request request,
                                               org.eclipse.jetty.server.Response response, String address) {
            return true;
        }

        @Override
        protected void handleConnect(final org.eclipse.jetty.server.Request request,
                                     final org.eclipse.jetty.server.Response response, final Callback callback,
                                     String serverAddress) {
            try {
                if (!this.doHandleAuthentication(request, response)) {
                    callback.succeeded();
                    return;
                }
            } catch (IOException e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
            // Just call super class method to establish the tunnel and avoid copy/paste.
            super.handleConnect(request, response, callback, serverAddress);
        }

        public boolean doHandleAuthentication(org.eclipse.jetty.server.Request request,
                                              org.eclipse.jetty.server.Response response) throws IOException {
            final HttpFields requestHeaders = request.getHeaders();
            final HttpFields.Mutable responseHeaders = response.getHeaders();

            boolean result = false;
            if ("CONNECT".equals(request.getMethod())) {
                final String authorization = requestHeaders.get(HttpHeader.PROXY_AUTHORIZATION);
                if (authorization == null) {
                    response.setStatus(HttpStatus.PROXY_AUTHENTICATION_REQUIRED_407);
                    responseHeaders.put(HttpHeader.PROXY_AUTHENTICATE, "Basic realm=\"Fake Realm\"");
                    result = false;
                    Content.Sink.asOutputStream(response).flush();
                    Content.Sink.asOutputStream(response).close();
                } else if (authorization
                    .equals("Basic am9obmRvZTpwYXNz")) {
                    response.setStatus(HttpStatus.OK_200);
                    result = true;
                } else {
                    response.setStatus(HttpStatus.UNAUTHORIZED_401);
                    Content.Sink.asOutputStream(response).flush();
                    Content.Sink.asOutputStream(response).close();
                    result = false;
                }
            }
            return result;
        }
    }

    private static class AuthenticateHandler extends Handler.Abstract {

        private Handler target;

        public AuthenticateHandler(Handler target) {
            this.target = target;
        }

        @Override
        public boolean handle(org.eclipse.jetty.server.Request request, org.eclipse.jetty.server.Response response,
                              Callback callback) throws Exception {
            final HttpFields requestHeaders = request.getHeaders();
            final HttpFields.Mutable responseHeaders = response.getHeaders();

            final String authorization = requestHeaders.get(HttpHeader.AUTHORIZATION);
            if (authorization != null && authorization.equals("Basic dXNlcjpwYXNzd2Q=")) {
                responseHeaders.put("target", request.getHttpURI().getPath());
                target.handle(request, response, callback);
            } else {
                response.setStatus(HttpStatus.UNAUTHORIZED_401);
                responseHeaders.put(HttpHeader.WWW_AUTHENTICATE, "Basic realm=\"Fake Realm\"");

                Content.Sink.asOutputStream(response).flush();
                Content.Sink.asOutputStream(response).close();
                callback.succeeded();
            }
            return true;
        }
    }

}
