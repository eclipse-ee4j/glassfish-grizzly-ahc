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
import com.ning.http.client.AsyncHttpClientConfig;
import com.ning.http.client.Response;
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
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.File;
import java.net.URL;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

public abstract class HttpToHttpsRedirectTest extends AbstractBasicTest {

    // FIXME super NOT threadsafe!!!
    private final AtomicBoolean isSet = new AtomicBoolean(false);

    private class Relative302Handler extends Handler.Abstract {

        @Override
        public boolean handle(Request request, org.eclipse.jetty.server.Response response, Callback callback)
                throws Exception {
            final HttpFields requestHeaders = request.getHeaders();
            final HttpFields.Mutable responseHeaders = response.getHeaders();
            String param;
            responseHeaders.put(HttpHeader.CONTENT_TYPE, "text/html; charset=utf-8");
            for (final HttpField field : requestHeaders) {
                param = field.getName();

                if (param.startsWith("X-redirect") && !isSet.getAndSet(true)) {
                    responseHeaders.put(HttpHeader.LOCATION, field.getValue());
                    response.setStatus(HttpStatus.FOUND_302);
                    Content.Sink.asOutputStream(response).flush();
                    Content.Sink.asOutputStream(response).close();
                    callback.succeeded();
                    return true;
                }
            }

            if (request.getHttpURI().getScheme().equalsIgnoreCase("https")) {
                responseHeaders.put("X-httpToHttps", "PASS");
                isSet.getAndSet(false);
            }

            response.setStatus(HttpStatus.OK_200);
            Content.Sink.asOutputStream(response).flush();
            Content.Sink.asOutputStream(response).close();
            callback.succeeded();
            return true;
        }
    }

    @BeforeClass(alwaysRun = true)
    public void setUpGlobal() throws Exception {
        server = new Server();

        port1 = findFreePort();
        port2 = findFreePort();

        ServerConnector listener = new ServerConnector(server);

        listener.setHost("127.0.0.1");
        listener.setPort(port1);
        server.addConnector(listener);

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

        ServerConnector connector = new ServerConnector(server,
                new SslConnectionFactory(sslContextFactory, HttpVersion.HTTP_1_1.asString()),
                new HttpConnectionFactory(https_config));
        connector.setHost("127.0.0.1");
        connector.setPort(port2);

        server.addConnector(connector);

        server.setHandler(new Relative302Handler());
        server.start();
        log.info("Local HTTP server started successfully");
    }

    @Test(groups = { "standalone", "default_provider" })
    public void httpToHttpsRedirect() throws Throwable {
        isSet.getAndSet(false);

        AsyncHttpClientConfig cg = new AsyncHttpClientConfig.Builder()//
                .setMaxRedirects(5)//
                .setFollowRedirect(true)//
                .setAcceptAnyCertificate(true)//
                .build();

        try (AsyncHttpClient client = getAsyncHttpClient(cg)) {
            Response response = client.prepareGet(getTargetUrl()).setHeader("X-redirect", getTargetUrl2()).execute().get();
            assertNotNull(response);
            assertEquals(response.getStatusCode(), 200);
            assertEquals(response.getHeader("X-httpToHttps"), "PASS");
        }
    }

    public String getTargetUrl2() {
        return String.format("https://127.0.0.1:%d/foo/test", port2);
    }

    @Test(groups = { "standalone", "default_provider" })
    public void httpToHttpsProperConfig() throws Throwable {
        isSet.getAndSet(false);

        AsyncHttpClientConfig cg = new AsyncHttpClientConfig.Builder()//
                .setMaxRedirects(5)//
                .setFollowRedirect(true)//
                .setAcceptAnyCertificate(true)//
                .build();
        try (AsyncHttpClient client = getAsyncHttpClient(cg)) {
            Response response = client.prepareGet(getTargetUrl()).setHeader("X-redirect", getTargetUrl2() + "/test2").execute().get();
            assertNotNull(response);
            assertEquals(response.getStatusCode(), 200);
            assertEquals(response.getHeader("X-httpToHttps"), "PASS");

            // Test if the internal channel is downgraded to clean http.
            response = client.prepareGet(getTargetUrl()).setHeader("X-redirect", getTargetUrl2() + "/foo2").execute().get();
            assertNotNull(response);
            assertEquals(response.getStatusCode(), 200);
            assertEquals(response.getHeader("X-httpToHttps"), "PASS");
        }
    }

    @Test(groups = { "standalone", "default_provider" })
    public void relativeLocationUrl() throws Throwable {
        isSet.getAndSet(false);

        AsyncHttpClientConfig cg = new AsyncHttpClientConfig.Builder()//
                .setMaxRedirects(5)//
                .setFollowRedirect(true)//
                .setAcceptAnyCertificate(true)//
                .build();
        try (AsyncHttpClient client = getAsyncHttpClient(cg)) {
            Response response = client.prepareGet(getTargetUrl()).setHeader("X-redirect", "/foo/test").execute().get();
            assertNotNull(response);
            assertEquals(response.getStatusCode(), 200);
            assertEquals(response.getUri().toString(), getTargetUrl());
        }
    }
}
