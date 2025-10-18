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
import com.ning.http.client.FluentCaseInsensitiveStringsMap;
import com.ning.http.client.Response;
import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.io.Content;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.util.Callback;
import org.testng.annotations.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

public abstract class InputStreamTest extends AbstractBasicTest {

    private class InputStreamHandler extends Handler.Abstract {
        @Override
        public boolean handle(Request request, org.eclipse.jetty.server.Response response, Callback callback)
                throws Exception {
            if ("POST".equalsIgnoreCase(request.getMethod())) {
                byte[] b = new byte[3];
                try (final InputStream in = Content.Source.asInputStream(request)) {
                    in.read(b, 0, 3);
                }
                response.setStatus(HttpStatus.OK_200);
                final HttpFields.Mutable responseHeaders = response.getHeaders();
                responseHeaders.put("X-Param", new String(b));
            } else { // this handler is to handle POST request
                org.eclipse.jetty.server.Response.writeError(request, response, callback, HttpStatus.FORBIDDEN_403);
            }
            Content.Sink.asOutputStream(response).flush();
            Content.Sink.asOutputStream(response).close();
            callback.succeeded();
            return true;
        }
    }

    @Test(groups = { "standalone", "default_provider" })
    public void testInvalidInputStream() throws IOException, ExecutionException, TimeoutException, InterruptedException {

        try (AsyncHttpClient client = getAsyncHttpClient(null)) {
            FluentCaseInsensitiveStringsMap h = new FluentCaseInsensitiveStringsMap();
            h.add("Content-Type", "application/x-www-form-urlencoded");

            InputStream is = new InputStream() {

                public int readAllowed;

                @Override
                public int available() {
                    return 1; // Fake
                }

                @Override
                public int read() throws IOException {
                    int fakeCount = readAllowed++;
                    if (fakeCount == 0) {
                        return (int) 'a';
                    } else if (fakeCount == 1) {
                        return (int) 'b';
                    } else if (fakeCount == 2) {
                        return (int) 'c';
                    } else {
                        return -1;
                    }

                }
            };

            Response resp = client.preparePost(getTargetUrl()).setHeaders(h).setBody(is).execute().get();
            assertNotNull(resp);
            assertEquals(resp.getStatusCode(), 200);
            assertEquals(resp.getHeader("X-Param"), "abc");
        }
    }

    @Override
    public Handler.Abstract configureHandler() throws Exception {
        return new InputStreamHandler();
    }
}
