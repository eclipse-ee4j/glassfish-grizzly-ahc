/*
 * Copyright (c) 2025 Contributors to the Eclipse Foundation.
 * Copyright (c) 2017, 2018 Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2010-2012 Sonatype, Inc. All rights reserved.
 *
 * This program is licensed to you under the Apache License Version 2.0,
 * and you may not use this file except in compliance with the Apache License Version 2.0.
 * You may obtain a copy of the Apache License Version 2.0 at http://www.apache.org/licenses/LICENSE-2.0.
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the Apache License Version 2.0 is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Apache License Version 2.0 for the specific language governing permissions and limitations there under.
 */

package com.ning.http.client.async;

import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.AsyncHttpClientConfig;
import com.ning.http.client.Request;
import com.ning.http.client.RequestBuilder;
import com.ning.http.client.Response;
import com.ning.http.client.extra.ThrottleRequestFilter;
import com.ning.http.client.filter.FilterContext;
import com.ning.http.client.filter.FilterException;
import com.ning.http.client.filter.ResponseFilter;

import org.eclipse.jetty.http.HttpField;
import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.io.Content;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.util.Callback;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.testng.Assert.*;

public abstract class FilterTest extends AbstractBasicTest {

    private class BasicHandler extends Handler.Abstract {

        @Override
        public boolean handle(org.eclipse.jetty.server.Request request, org.eclipse.jetty.server.Response response,
                              Callback callback) throws Exception {

            final HttpFields requestHeaders = request.getHeaders();
            final HttpFields.Mutable responseHeaders = response.getHeaders();
            for (final HttpField field : requestHeaders) {
                responseHeaders.put(field.getName(), field.getValue());
            }

            response.setStatus(HttpStatus.OK_200);
            Content.Sink.asOutputStream(response).flush();
            Content.Sink.asOutputStream(response).close();
            callback.succeeded();
            return true;
        }
    }

    @Override
    public Handler.Abstract configureHandler() throws Exception {
        return new BasicHandler();
    }

    @Test(groups = { "standalone", "default_provider" })
    public void basicTest() throws Throwable {
        AsyncHttpClientConfig.Builder b = new AsyncHttpClientConfig.Builder();
        b.addRequestFilter(new ThrottleRequestFilter(100));

        try (AsyncHttpClient client = getAsyncHttpClient(b.build())) {
            Response response = client.preparePost(getTargetUrl()).execute().get();
            assertNotNull(response);
            assertEquals(response.getStatusCode(), 200);
        }
    }

    @Test(groups = { "standalone", "default_provider" })
    public void loadThrottleTest() throws Throwable {
        AsyncHttpClientConfig.Builder b = new AsyncHttpClientConfig.Builder();
        b.addRequestFilter(new ThrottleRequestFilter(10));

        try (AsyncHttpClient client = getAsyncHttpClient(b.build())) {
            List<Future<Response>> futures = new ArrayList<>();
            for (int i = 0; i < 200; i++) {
                futures.add(client.preparePost(getTargetUrl()).execute());
            }

            for (Future<Response> f : futures) {
                Response r = f.get();
                assertNotNull(f.get());
                assertEquals(r.getStatusCode(), 200);
            }
        }
    }

    @Test(groups = { "standalone", "default_provider" })
    public void maxConnectionsText() throws Throwable {
        AsyncHttpClientConfig.Builder b = new AsyncHttpClientConfig.Builder();
        b.addRequestFilter(new ThrottleRequestFilter(0, 1000));

        try (AsyncHttpClient client = getAsyncHttpClient(b.build())) {
            client.preparePost(getTargetUrl()).execute().get();
            fail("Should have timed out");
        } catch (ExecutionException ex) {
            assertTrue(ex.getCause() instanceof FilterException);
        }
    }

    public String getTargetUrl() {
        return String.format("http://127.0.0.1:%d/foo/test", port1);
    }

    @Test(groups = { "standalone", "default_provider" })
    public void basicResponseFilterTest() throws Throwable {
        AsyncHttpClientConfig.Builder b = new AsyncHttpClientConfig.Builder();
        b.addResponseFilter(new ResponseFilter() {

            public <T> FilterContext<T> filter(FilterContext<T> ctx) throws FilterException {
                return ctx;
            }

        });

        try (AsyncHttpClient client = getAsyncHttpClient(b.build())) {
            Response response = client.preparePost(getTargetUrl()).execute().get();

            assertNotNull(response);
            assertEquals(response.getStatusCode(), 200);
        }
    }

    @Test(groups = { "standalone", "default_provider" })
    public void replayResponseFilterTest() throws Throwable {
        AsyncHttpClientConfig.Builder b = new AsyncHttpClientConfig.Builder();
        final AtomicBoolean replay = new AtomicBoolean(true);

        b.addResponseFilter(new ResponseFilter() {

            public <T> FilterContext<T> filter(FilterContext<T> ctx) throws FilterException {

                if (replay.getAndSet(false)) {
                    Request request = new RequestBuilder(ctx.getRequest()).addHeader("X-Replay", "true").build();
                    return new FilterContext.FilterContextBuilder<T>().asyncHandler(ctx.getAsyncHandler()).request(request).replayRequest(true).build();
                }
                return ctx;
            }

        });

        try (AsyncHttpClient c = getAsyncHttpClient(b.build())) {
            Response response = c.preparePost(getTargetUrl()).execute().get();

            assertNotNull(response);
            assertEquals(response.getStatusCode(), 200);
            assertEquals(response.getHeader("X-Replay"), "true");
        }
    }

    @Test(groups = { "standalone", "default_provider" })
    public void replayStatusCodeResponseFilterTest() throws Throwable {
        AsyncHttpClientConfig.Builder b = new AsyncHttpClientConfig.Builder();
        final AtomicBoolean replay = new AtomicBoolean(true);

        b.addResponseFilter(new ResponseFilter() {

            public <T> FilterContext<T> filter(FilterContext<T> ctx) throws FilterException {

                if (ctx.getResponseStatus() != null && ctx.getResponseStatus().getStatusCode() == 200 && replay.getAndSet(false)) {
                    Request request = new RequestBuilder(ctx.getRequest()).addHeader("X-Replay", "true").build();
                    return new FilterContext.FilterContextBuilder<T>().asyncHandler(ctx.getAsyncHandler()).request(request).replayRequest(true).build();
                }
                return ctx;
            }

        });

        try (AsyncHttpClient c = getAsyncHttpClient(b.build())) {
            Response response = c.preparePost(getTargetUrl()).execute().get();

            assertNotNull(response);
            assertEquals(response.getStatusCode(), 200);
            assertEquals(response.getHeader("X-Replay"), "true");
        }
    }

    @Test(groups = { "standalone", "default_provider" })
    public void replayHeaderResponseFilterTest() throws Throwable {
        AsyncHttpClientConfig.Builder b = new AsyncHttpClientConfig.Builder();
        final AtomicBoolean replay = new AtomicBoolean(true);

        b.addResponseFilter(new ResponseFilter() {

            public <T> FilterContext<T> filter(FilterContext<T> ctx) throws FilterException {

                if (ctx.getResponseHeaders() != null && ctx.getResponseHeaders().getHeaders().getFirstValue("Ping").equals("Pong") && replay.getAndSet(false)) {

                    Request request = new RequestBuilder(ctx.getRequest()).addHeader("Ping", "Pong").build();
                    return new FilterContext.FilterContextBuilder<T>().asyncHandler(ctx.getAsyncHandler()).request(request).replayRequest(true).build();
                }
                return ctx;
            }

        });

        try (AsyncHttpClient c = getAsyncHttpClient(b.build())) {
            Response response = c.preparePost(getTargetUrl()).addHeader("Ping", "Pong").execute().get();

            assertNotNull(response);
            assertEquals(response.getStatusCode(), 200);
            assertEquals(response.getHeader("Ping"), "Pong");
        }
    }
}
