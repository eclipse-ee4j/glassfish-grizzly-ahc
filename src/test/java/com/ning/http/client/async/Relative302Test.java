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
import com.ning.http.client.uri.Uri;

import org.eclipse.jetty.http.HttpField;
import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.io.Content;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.util.Callback;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.net.ConnectException;
import java.net.URI;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

public abstract class Relative302Test extends AbstractBasicTest {
    private final AtomicBoolean isSet = new AtomicBoolean(false);

    private class Relative302Handler extends Handler.Abstract {

        @Override
        public boolean handle(Request request, org.eclipse.jetty.server.Response response, Callback callback)
                throws Exception {
            final HttpFields requestHeaders = request.getHeaders();
            final HttpFields.Mutable responseHeaders = response.getHeaders();
            String param;
            response.setStatus(HttpStatus.OK_200);
            responseHeaders.put(HttpHeader.CONTENT_TYPE, "text/html; charset=utf-8");
            for (final HttpField field : requestHeaders) {
                param = field.getName();
                if (param.startsWith("X-redirect") && !isSet.getAndSet(true)) {
                    responseHeaders.put(HttpHeader.LOCATION, field.getValue());
                    response.setStatus(HttpStatus.FOUND_302);
                    break;
                }
            }
            responseHeaders.put(HttpHeader.CONTENT_LENGTH, 0L);
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

        server.setHandler(new Relative302Handler());
        server.start();
        log.info("Local HTTP server started successfully");
    }

    @Test(groups = { "online", "default_provider" })
    public void redirected302Test() throws Throwable {
        isSet.getAndSet(false);
        AsyncHttpClientConfig cg = new AsyncHttpClientConfig.Builder().setFollowRedirect(true).build();
        try (AsyncHttpClient client = getAsyncHttpClient(cg)) {
            // once
            Response response = client.prepareGet(getTargetUrl()).setHeader("X-redirect", "http://www.google.com/").execute().get();

            assertNotNull(response);
            assertEquals(response.getStatusCode(), 200);

            String baseUrl = getBaseUrl(response.getUri());

            assertTrue(baseUrl.startsWith("http://www.google."), "response does not show redirection to a google subdomain, got " + baseUrl);
        }
    }

    private String getBaseUrl(Uri uri) {
        String url = uri.toString();
        int port = uri.getPort();
        if (port == -1) {
            port = getPort(uri);
            url = url.substring(0, url.length() - 1) + ":" + port;
        }
        return url.substring(0, url.lastIndexOf(":") + String.valueOf(port).length() + 1);
    }

    private static int getPort(Uri uri) {
        int port = uri.getPort();
        if (port == -1)
            port = uri.getScheme().equals("http") ? 80 : 443;
        return port;
    }

    @Test(groups = { "standalone", "default_provider" })
    public void redirected302InvalidTest() throws Throwable {
        isSet.getAndSet(false);
        AsyncHttpClientConfig cg = new AsyncHttpClientConfig.Builder().setFollowRedirect(true).build();

        // If the test hit a proxy, no ConnectException will be thrown and instead of 404 will be returned.
        try (AsyncHttpClient client = getAsyncHttpClient(cg)) {
            Response response = client.prepareGet(getTargetUrl()).setHeader("X-redirect", String.format("http://127.0.0.1:%d/", port2)).execute().get();

            assertNotNull(response);
            assertEquals(response.getStatusCode(), 404);
        } catch (ExecutionException ex) {
            assertEquals(ex.getCause().getClass(), ConnectException.class);
        }
    }

    @Test(groups = { "standalone", "default_provider" })
    public void absolutePathRedirectTest() throws Throwable {
        isSet.getAndSet(false);

        AsyncHttpClientConfig cg = new AsyncHttpClientConfig.Builder().setFollowRedirect(true).build();
        try (AsyncHttpClient client = getAsyncHttpClient(cg)) {
            String redirectTarget = "/bar/test";
            String destinationUrl = new URI(getTargetUrl()).resolve(redirectTarget).toString();

            Response response = client.prepareGet(getTargetUrl()).setHeader("X-redirect", redirectTarget).execute().get();
            assertNotNull(response);
            assertEquals(response.getStatusCode(), 200);
            assertEquals(response.getUri().toString(), destinationUrl);

            log.debug("{} was redirected to {}", redirectTarget, destinationUrl);
        }
    }

    @Test(groups = { "standalone", "default_provider" })
    public void relativePathRedirectTest() throws Throwable {
        isSet.getAndSet(false);

        AsyncHttpClientConfig cg = new AsyncHttpClientConfig.Builder().setFollowRedirect(true).build();
        try (AsyncHttpClient client = getAsyncHttpClient(cg)) {
            String redirectTarget = "bar/test1";
            String destinationUrl = new URI(getTargetUrl()).resolve(redirectTarget).toString();

            Response response = client.prepareGet(getTargetUrl()).setHeader("X-redirect", redirectTarget).execute().get();
            assertNotNull(response);
            assertEquals(response.getStatusCode(), 200);
            assertEquals(response.getUri().toString(), destinationUrl);

            log.debug("{} was redirected to {}", redirectTarget, destinationUrl);
        }
    }
}
