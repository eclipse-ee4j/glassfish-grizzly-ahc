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

import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.Response;
import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.io.Content;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.util.Callback;
import org.eclipse.jetty.util.Fields;
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

public abstract class ParamEncodingTest extends AbstractBasicTest {

    private class ParamEncoding extends Handler.Abstract {
        @Override
        public boolean handle(Request request, org.eclipse.jetty.server.Response response, Callback callback)
                throws Exception {
            final HttpFields requestHeaders = request.getHeaders();
            final HttpFields.Mutable responseHeaders = response.getHeaders();
            if ("POST".equalsIgnoreCase(request.getMethod())) {
                final Fields allParameters = Request.getParameters(request);
                final Fields.Field f = allParameters.get("test");
                if (f != null) {
                    String p = f.getValue();
                    if (isNonEmpty(p)) {
                        response.setStatus(HttpStatus.OK_200);
                        responseHeaders.put("X-Param", p);
                    } else {
                        org.eclipse.jetty.server.Response.writeError(request, response, callback,
                                                                     HttpStatus.NOT_ACCEPTABLE_406);
                    }
                } else {
                    org.eclipse.jetty.server.Response.writeError(request, response, callback, HttpStatus.NOT_ACCEPTABLE_406);
                }
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
    public void testParameters() throws IOException, ExecutionException, TimeoutException, InterruptedException {

        String value = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKQLMNOPQRSTUVWXYZ1234567809`~!@#$%^&*()_+-=,.<>/?;:'\"[]{}\\| ";
        try (AsyncHttpClient client = getAsyncHttpClient(null)) {
            Future<Response> f = client.preparePost("http://127.0.0.1:" + port1).addFormParam("test", value).execute();
            Response resp = f.get(10, TimeUnit.SECONDS);
            assertNotNull(resp);
            assertEquals(resp.getStatusCode(), 200);
            assertEquals(resp.getHeader("X-Param"), value.trim());
        }
    }

    @Override
    public Handler.Abstract configureHandler() throws Exception {
        return new ParamEncoding();
    }
}
