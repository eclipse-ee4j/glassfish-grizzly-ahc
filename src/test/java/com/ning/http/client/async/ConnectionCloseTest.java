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
import com.ning.http.client.AsyncHttpClientConfig.Builder;
import com.ning.http.client.Request;
import com.ning.http.client.RequestBuilder;
import com.ning.http.client.Response;

import java.io.File;
import java.io.IOException;
import java.net.URL;
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
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public abstract class ConnectionCloseTest extends AbstractBasicTest {

    @BeforeClass(alwaysRun = true)
    public void setUpGlobal() throws Exception {
        server = new Server();
        port1 = findFreePort();

        SslContextFactory.Server sslContextFactory = new SslContextFactory.Server();
        ClassLoader cl = getClass().getClassLoader();

        URL cacertsUrl = cl.getResource("ssltest-cacerts.jks");
        String trustStoreFile = new File(cacertsUrl.toURI()).getAbsolutePath();
        sslContextFactory.setTrustStorePath(trustStoreFile);
        sslContextFactory.setTrustStorePassword("changeit");
        sslContextFactory.setTrustStoreType("JKS");

        URL keystoreUrl = cl.getResource("ssltest-keystore.jks");
        String keyStoreFile = new File(keystoreUrl.toURI()).getAbsolutePath();
        sslContextFactory.setKeyStorePath(keyStoreFile);
        sslContextFactory.setKeyStorePassword("changeit");
        sslContextFactory.setKeyStoreType("JKS");

        HttpConfiguration https_config = new HttpConfiguration();
        https_config.setSecureScheme("https");
        https_config.setSecurePort(port1);
        https_config.setOutputBufferSize(32768);
        SecureRequestCustomizer src = new SecureRequestCustomizer();
        src.setStsMaxAge(2000);
        src.setStsIncludeSubDomains(true);
        https_config.addCustomizer(src);
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

    @AfterClass(alwaysRun = true)
    public void tearDownGlobal() throws Exception {
        server.stop();
    }

    public static class CloseConnectionHandler extends Handler.Abstract {

        @Override
        public boolean handle(org.eclipse.jetty.server.Request request, org.eclipse.jetty.server.Response response,
                              Callback callback)
                throws Exception {
            final HttpFields.Mutable responseHeaders = response.getHeaders();
            responseHeaders.put(HttpHeader.CONNECTION, "close");
            response.setStatus(HttpStatus.OK_200);
            Content.Sink.asOutputStream(response).flush();
            Content.Sink.asOutputStream(response).close();
            //callback.succeeded();
            return true;
        }
    }

    @Override
    public Handler.Abstract configureHandler() throws Exception {
        return new CloseConnectionHandler();
    }

    @Test
    public void handlesCloseResponse() throws IOException, InterruptedException, ExecutionException {

        AsyncHttpClientConfig config = new Builder().setSSLContext(createSSLContext(new AtomicBoolean(true))).build();

        try (AsyncHttpClient client = getAsyncHttpClient(config)) {
            Request request = new RequestBuilder("GET").setHeader("Connection", "keep-alive").setUrl(getTargetUrl()).build();
            Future<Response> responseFuture = client.executeRequest(request);
            int status = responseFuture.get().getStatusCode();
            Assert.assertEquals(status, 200);
        }
    }

    protected String getTargetUrl() {
        return String.format("https://127.0.0.1:%d/foo/test", port1);
    }

}

