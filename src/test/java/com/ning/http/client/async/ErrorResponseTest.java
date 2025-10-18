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

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

import java.io.OutputStream;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.io.Content;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.util.Callback;
import org.testng.annotations.Test;

import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.Response;

/**
 * Tests to reproduce issues with handling of error responses
 * 
 * @author Tatu Saloranta
 */
public abstract class ErrorResponseTest extends AbstractBasicTest {
    final static String BAD_REQUEST_STR = "Very Bad Request! No cookies.";

    private static class ErrorHandler extends Handler.Abstract {
        @Override
        public boolean handle(Request request, org.eclipse.jetty.server.Response response, Callback callback)
                throws Exception {
            try {
                Thread.sleep(210L);
            } catch (InterruptedException e) {
            }
            final HttpFields.Mutable responseHeaders = response.getHeaders();
            responseHeaders.put(HttpHeader.CONTENT_TYPE, "text/plain");
            response.setStatus(HttpStatus.BAD_REQUEST_400);
            try (final OutputStream out = Content.Sink.asOutputStream(response)) {
                out.write(BAD_REQUEST_STR.getBytes("UTF-8"));
                out.flush();
            }
            callback.succeeded();
            return true;
        }
    }

    @Override
    public Handler.Abstract configureHandler() throws Exception {
        return new ErrorHandler();
    }

    @Test(groups = { "standalone", "default_provider" })
    public void testQueryParameters() throws Exception {
        try (AsyncHttpClient client = getAsyncHttpClient(null)) {
            Future<Response> f = client.prepareGet("http://127.0.0.1:" + port1 + "/foo").addHeader("Accepts", "*/*").execute();
            Response resp = f.get(3, TimeUnit.SECONDS);
            assertNotNull(resp);
            assertEquals(resp.getStatusCode(), 400);
            String respStr = resp.getResponseBody();
            assertEquals(BAD_REQUEST_STR, respStr);
        }
    }
}
