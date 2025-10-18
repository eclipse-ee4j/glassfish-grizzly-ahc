/*
 * Copyright (c) 2025 Contributors to the Eclipse Foundation.
 * Copyright (c) 2017, 2018 Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2012-2015 Sonatype, Inc. All rights reserved.
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

package com.ning.http.client.async.grizzly;

import com.ning.http.client.AsyncCompletionHandler;
import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.AsyncHttpClientConfig;
import com.ning.http.client.Response;
import com.ning.http.client.async.AbstractBasicTest;
import com.ning.http.client.providers.grizzly.GrizzlyAsyncHttpProvider;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.io.Content;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.util.Callback;
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

public class GrizzlyExpectingTimeoutTest extends AbstractBasicTest {

    private static final String MSG = "Unauthorized without WWW-Authenticate header";

    protected String getExpectedTimeoutMessage() {
        return "Timeout exceeded";
    }

    @Override
    public AsyncHttpClient getAsyncHttpClient(AsyncHttpClientConfig config) {
        if (config == null) {
            config = new AsyncHttpClientConfig.Builder().build();
        }
        return new AsyncHttpClient(new GrizzlyAsyncHttpProvider(config), config);
    }

    @Override
    public Handler.Abstract configureHandler() throws Exception {
        return new ExpectExceptionHandler();
    }

    private class ExpectExceptionHandler extends Handler.Abstract {
        @Override
        public boolean handle(Request request, org.eclipse.jetty.server.Response response, Callback callback)
                throws Exception {
            response.setStatus(HttpStatus.UNAUTHORIZED_401);
            new Thread(new Runnable() {
                public void run() {
                    try {
                        Content.Sink.asOutputStream(response).write(MSG.getBytes());
                        Content.Sink.asOutputStream(response).flush();
                    } catch (IOException e) {
                        log.error(e.getMessage(), e);
                    }
                }
            }).start();
            return true;
        }
    }

    @Test(groups = {"standalone", "default_provider"})
    public void expectedTimeoutTest() throws IOException {
        final AtomicInteger counts = new AtomicInteger();
        final int timeout = 100;

        try (AsyncHttpClient client = getAsyncHttpClient(new AsyncHttpClientConfig.Builder().setRequestTimeout(timeout).build())) {
            Future<Response> responseFuture =
                    client.prepareGet(getTargetUrl()).execute(new AsyncCompletionHandler<Response>() {
                        @Override
                        public Response onCompleted(Response response) throws Exception {
                            counts.incrementAndGet();
                            return response;
                        }
    
                        @Override
                        public void onThrowable(Throwable t) {
                            counts.incrementAndGet();
                            super.onThrowable(t);
                        }
                    });
            // currently, an exception is expected
            // because the grizzly provider would throw IllegalStateException if WWW-Authenticate header doesn't exist with 401 response status.
            try {
                Response response = responseFuture.get();
                assertNull(response);
            } catch (InterruptedException e) {
                fail("Interrupted.", e);
            } catch (ExecutionException e) {
                assertTrue(e.getCause() instanceof TimeoutException);
                assertEquals(e.getCause().getMessage(), getExpectedTimeoutMessage());
            }
            // wait for timeout again.
            try {
                Thread.sleep(timeout*2);
            } catch (InterruptedException e) {
                fail("Interrupted.", e);
            }
            // the result should be either onCompleted or onThrowable.
            assertEquals(1, counts.get(), "result should be one");
        }
    }
}
