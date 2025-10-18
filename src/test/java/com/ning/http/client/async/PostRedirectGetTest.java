/*
 * Copyright (c) 2025 Contributors to the Eclipse Foundation.
 * Copyright (c) 2017, 2018 Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2012 Sonatype, Inc. All rights reserved.
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

import com.ning.http.client.AsyncCompletionHandler;
import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.AsyncHttpClientConfig;
import com.ning.http.client.Request;
import com.ning.http.client.RequestBuilder;
import com.ning.http.client.Response;
import com.ning.http.client.filter.FilterContext;
import com.ning.http.client.filter.FilterException;
import com.ning.http.client.filter.ResponseFilter;
import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.io.Content;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.util.Callback;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

public abstract class PostRedirectGetTest extends AbstractBasicTest {

    // ------------------------------------------------------ Test Configuration

    @Override
    public Handler.Abstract configureHandler() throws Exception {
        return new PostRedirectGetHandler();
    }

    // ------------------------------------------------------------ Test Methods

    // FIXME reimplement test since only some headers are propagated on redirect
    @Test(groups = { "standalone", "post_redirect_get" }, enabled = false)
    public void postRedirectGet302Test() throws Exception {
        doTestPositive(302);
    }

    // FIXME reimplement test since only some headers are propagated on redirect
    @Test(groups = { "standalone", "post_redirect_get" }, enabled = false)
    public void postRedirectGet302StrictTest() throws Exception {
        doTestNegative(302, true);
    }

    // FIXME reimplement test since only some headers are propagated on redirect
    @Test(groups = { "standalone", "post_redirect_get" }, enabled = false)
    public void postRedirectGet303Test() throws Exception {
        doTestPositive(303);
    }

    // FIXME reimplement test since only some headers are propagated on redirect
    @Test(groups = { "standalone", "post_redirect_get" }, enabled = false)
    public void postRedirectGet301Test() throws Exception {
        doTestNegative(301, false);
    }

    // FIXME reimplement test since only some headers are propagated on redirect
    @Test(groups = { "standalone", "post_redirect_get" }, enabled = false)
    public void postRedirectGet307Test() throws Exception {
        doTestNegative(307, false);
    }

    // --------------------------------------------------------- Private Methods

    private void doTestNegative(final int status, boolean strict) throws Exception {

        AsyncHttpClientConfig config = new AsyncHttpClientConfig.Builder().setFollowRedirect(true).setStrict302Handling(strict).addResponseFilter(new ResponseFilter() {
            public FilterContext filter(FilterContext ctx) throws FilterException {
                // pass on the x-expect-get and remove the x-redirect
                // headers if found in the response
                ctx.getResponseHeaders().getHeaders().get("x-expect-post");
                ctx.getRequest().getHeaders().add("x-expect-post", "true");
                ctx.getRequest().getHeaders().remove("x-redirect");
                return ctx;
            }
        }).build();

        try (AsyncHttpClient client = getAsyncHttpClient(config)) {
            Request request = new RequestBuilder("POST").setUrl(getTargetUrl()).addFormParam("q", "a b").addHeader("x-redirect", +status + "@" + "http://localhost:" + port1 + "/foo/bar/baz").addHeader("x-negative", "true").build();
            Future<Integer> responseFuture = client.executeRequest(request, new AsyncCompletionHandler<Integer>() {

                @Override
                public Integer onCompleted(Response response) throws Exception {
                    return response.getStatusCode();
                }

                @Override
                public void onThrowable(Throwable t) {
                    t.printStackTrace();
                    Assert.fail("Unexpected exception: " + t.getMessage(), t);
                }

            });
            int statusCode = responseFuture.get();
            Assert.assertEquals(statusCode, 200);
        }
    }

    private void doTestPositive(final int status) throws Exception {
        
        AsyncHttpClientConfig config = new AsyncHttpClientConfig.Builder().setFollowRedirect(true).addResponseFilter(new ResponseFilter() {
            public FilterContext filter(FilterContext ctx) throws FilterException {
                // pass on the x-expect-get and remove the x-redirect
                // headers if found in the response
                ctx.getResponseHeaders().getHeaders().get("x-expect-get");
                ctx.getRequest().getHeaders().add("x-expect-get", "true");
                ctx.getRequest().getHeaders().remove("x-redirect");
                return ctx;
            }
        }).build();
        
        try (AsyncHttpClient client = getAsyncHttpClient(config)) {
            Request request = new RequestBuilder("POST").setUrl(getTargetUrl()).addFormParam("q", "a b").addHeader("x-redirect", +status + "@" + "http://localhost:" + port1 + "/foo/bar/baz").build();
            Future<Integer> responseFuture = client.executeRequest(request, new AsyncCompletionHandler<Integer>() {

                @Override
                public Integer onCompleted(Response response) throws Exception {
                    return response.getStatusCode();
                }

                @Override
                public void onThrowable(Throwable t) {
                    t.printStackTrace();
                    Assert.fail("Unexpected exception: " + t.getMessage(), t);
                }

            });
            int statusCode = responseFuture.get();
            Assert.assertEquals(statusCode, 200);
        }
    }

    // ---------------------------------------------------------- Nested Classes

    public static class PostRedirectGetHandler extends Handler.Abstract {

        final AtomicInteger counter = new AtomicInteger();

        @Override
        public boolean handle(org.eclipse.jetty.server.Request request, org.eclipse.jetty.server.Response response,
                              Callback callback) throws Exception {
            final HttpFields requestHeaders = request.getHeaders();
            final HttpFields.Mutable responseHeaders = response.getHeaders();

            final boolean expectGet = (requestHeaders.get("x-expect-get") != null);
            final boolean expectPost = (requestHeaders.get("x-expect-post") != null);
            if (expectGet) {
                final String method = request.getMethod();
                if (!"GET".equals(method)) {
                    org.eclipse.jetty.server.Response.writeError(request, response, callback,
                                                                 HttpStatus.INTERNAL_SERVER_ERROR_500,
                                                                 "Incorrect method.  Expected GET, received " + method);
                    callback.succeeded();
                    return true;
                }
                response.setStatus(HttpStatus.OK_200);
                Content.Sink.asOutputStream(response).write("OK".getBytes());
                Content.Sink.asOutputStream(response).flush();
                callback.succeeded();
                return true;
            } else if (expectPost) {
                final String method = request.getMethod();
                if (!"POST".equals(method)) {
                    org.eclipse.jetty.server.Response.writeError(request, response, callback,
                                                                 HttpStatus.INTERNAL_SERVER_ERROR_500,
                                                                 "Incorrect method.  Expected POST, received " +
                                                                 method);
                    callback.succeeded();
                    return true;
                }
                response.setStatus(HttpStatus.OK_200);
                Content.Sink.asOutputStream(response).write("OK".getBytes());
                Content.Sink.asOutputStream(response).flush();
                callback.succeeded();
                return true;
            }

            String header = requestHeaders.get("x-redirect");
            if (header != null) {
                // format for header is <status code>|<location url>
                String[] parts = header.split("@");
                int redirectCode;
                try {
                    redirectCode = Integer.parseInt(parts[0]);
                } catch (Exception ex) {
                    ex.printStackTrace();
                    org.eclipse.jetty.server.Response.writeError(request, response, callback,
                                                                 HttpStatus.INTERNAL_SERVER_ERROR_500, "Unable to parse redirect code");
                    callback.succeeded();
                    return true;
                }
                response.setStatus(redirectCode);
                if (requestHeaders.get("x-negative") == null) {
                    responseHeaders.put("x-expect-get", "true");
                } else {
                    responseHeaders.put("x-expect-post", "true");
                }
                responseHeaders.put(HttpHeader.CONTENT_LENGTH, 0L);
                responseHeaders.put(HttpHeader.LOCATION, parts[1] + counter.getAndIncrement());
                Content.Sink.asOutputStream(response).flush();
                callback.succeeded();
                return true;
            }

            org.eclipse.jetty.server.Response.writeError(request, response, callback,
                                                         HttpStatus.INTERNAL_SERVER_ERROR_500);
            Content.Sink.asOutputStream(response).flush();
            Content.Sink.asOutputStream(response).close();
            callback.succeeded();
            return true;
        }
    }
}
