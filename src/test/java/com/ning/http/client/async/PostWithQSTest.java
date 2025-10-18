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

import static com.ning.http.util.MiscUtils.isNonEmpty;

import com.ning.http.client.AsyncCompletionHandlerBase;
import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.HttpResponseStatus;
import com.ning.http.client.Response;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.io.Content;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.util.Callback;
import org.testng.annotations.Test;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

/**
 * Tests POST request with Query String.
 * 
 * @author Hubert Iwaniuk
 */
public abstract class PostWithQSTest extends AbstractBasicTest {

    /**
     * POST with QS server part.
     */
    private class PostWithQSHandler extends Handler.Abstract {
        @Override
        public boolean handle(Request request, org.eclipse.jetty.server.Response response, Callback callback)
                throws Exception {
            if ("POST".equalsIgnoreCase(request.getMethod())) {
                String qs = request.getHttpURI().getQuery();
                if (isNonEmpty(qs) && request.getLength() == 3) {
                    try (final InputStream is = Content.Source.asInputStream(request);
                         final InputStreamReader isr = new InputStreamReader(is);
                         final BufferedReader reader = new BufferedReader(isr);
                         final OutputStream os = Content.Sink.asOutputStream(response);
                         final OutputStreamWriter osw = new OutputStreamWriter(os);
                         final BufferedWriter writer = new BufferedWriter(osw)) {
                        final String line = reader.readLine();
                        writer.write(line + "\r\n");
                        os.flush();
                    }
                } else {
                    org.eclipse.jetty.server.Response.writeError(request, response, callback,
                                                                 HttpStatus.NOT_ACCEPTABLE_406);
                }
            } else { // this handler is to handle POST request
                org.eclipse.jetty.server.Response.writeError(request, response, callback, HttpStatus.FORBIDDEN_403);
            }
            callback.succeeded();
            return true;
        }
    }

    @Test(groups = { "standalone", "default_provider" })
    public void postWithQS() throws IOException, ExecutionException, TimeoutException, InterruptedException {
        try (AsyncHttpClient client = getAsyncHttpClient(null)) {
            Future<Response> f = client.preparePost("http://127.0.0.1:" + port1 + "/?a=b").setBody("abc".getBytes()).execute();
            Response resp = f.get(3, TimeUnit.SECONDS);
            assertNotNull(resp);
            assertEquals(resp.getStatusCode(), 200);
        }
    }

    @Test(groups = { "standalone", "default_provider" })
    public void postWithNulParamQS() throws IOException, ExecutionException, TimeoutException, InterruptedException {
        try (AsyncHttpClient client = getAsyncHttpClient(null)) {
            Future<Response> f = client.preparePost("http://127.0.0.1:" + port1 + "/?a=").setBody("abc".getBytes()).execute(new AsyncCompletionHandlerBase() {

                @Override
                public STATE onStatusReceived(final HttpResponseStatus status) throws Exception {
                    if (!status.getUri().toString().equals("http://127.0.0.1:" + port1 + "/?a=")) {
                        throw new IOException(status.getUri().toString());
                    }
                    return super.onStatusReceived(status);
                }

            });
            Response resp = f.get(3, TimeUnit.SECONDS);
            assertNotNull(resp);
            assertEquals(resp.getStatusCode(), 200);
        }
    }

    @Test(groups = { "standalone", "default_provider" })
    public void postWithNulParamsQS() throws IOException, ExecutionException, TimeoutException, InterruptedException {
        try (AsyncHttpClient client = getAsyncHttpClient(null)) {
            Future<Response> f = client.preparePost("http://127.0.0.1:" + port1 + "/?a=b&c&d=e").setBody("abc".getBytes()).execute(new AsyncCompletionHandlerBase() {

                @Override
                public STATE onStatusReceived(final HttpResponseStatus status) throws Exception {
                    if (!status.getUri().toString().equals("http://127.0.0.1:" + port1 + "/?a=b&c&d=e")) {
                        throw new IOException("failed to parse the query properly");
                    }
                    return super.onStatusReceived(status);
                }

            });
            Response resp = f.get(3, TimeUnit.SECONDS);
            assertNotNull(resp);
            assertEquals(resp.getStatusCode(), 200);
        }
    }
    
    @Test(groups = { "standalone", "default_provider" })
    public void postWithEmptyParamsQS() throws IOException, ExecutionException, TimeoutException, InterruptedException {
        try (AsyncHttpClient client = getAsyncHttpClient(null)) {
            Future<Response> f = client.preparePost("http://127.0.0.1:" + port1 + "/?a=b&c=&d=e").setBody("abc".getBytes()).execute(new AsyncCompletionHandlerBase() {

                @Override
                public STATE onStatusReceived(final HttpResponseStatus status) throws Exception {
                    if (!status.getUri().toString().equals("http://127.0.0.1:" + port1 + "/?a=b&c=&d=e")) {
                        throw new IOException("failed to parse the query properly");
                    }
                    return super.onStatusReceived(status);
                }

            });
            Response resp = f.get(3, TimeUnit.SECONDS);
            assertNotNull(resp);
            assertEquals(resp.getStatusCode(), 200);
        }
    }

    @Override
    public Handler.Abstract configureHandler() throws Exception {
        return new PostWithQSHandler();
    }
}
