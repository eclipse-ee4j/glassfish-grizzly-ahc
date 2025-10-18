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
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.util.Callback;
import org.slf4j.LoggerFactory;
import org.testng.annotations.Test;

import java.io.IOException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

/**
 * Testing query parameters support.
 * 
 * @author Hubert Iwaniuk
 */
public abstract class QueryParametersTest extends AbstractBasicTest {
    private class QueryStringHandler extends Handler.Abstract {
        @Override
        public boolean handle(Request request, org.eclipse.jetty.server.Response response, Callback callback)
                throws Exception {
            final HttpFields.Mutable responseHeaders = response.getHeaders();
            if ("GET".equalsIgnoreCase(request.getMethod())) {
                String qs = request.getHttpURI().getQuery();
                if (isNonEmpty(qs)) {
                    for (String qnv : qs.split("&")) {
                        String nv[] = qnv.split("=");
                        responseHeaders.put(nv[0], nv[1]);
                    }
                    response.setStatus(HttpStatus.OK_200);
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

    @Override
    public Handler.Abstract configureHandler() throws Exception {
        return new QueryStringHandler();
    }

    @Test(groups = { "standalone", "default_provider" })
    public void testQueryParameters() throws IOException, ExecutionException, TimeoutException, InterruptedException {
        try (AsyncHttpClient client = getAsyncHttpClient(null)) {
            Future<Response> f = client.prepareGet("http://127.0.0.1:" + port1).addQueryParam("a", "1").addQueryParam("b", "2").execute();
            Response resp = f.get(3, TimeUnit.SECONDS);
            assertNotNull(resp);
            assertEquals(resp.getStatusCode(), 200);
            assertEquals(resp.getHeader("a"), "1");
            assertEquals(resp.getHeader("b"), "2");
        }
    }

    @Test(groups = { "standalone", "default_provider" })
    public void testUrlRequestParametersEncoding() throws IOException, ExecutionException, InterruptedException {
        String URL = getTargetUrl() + "?q=";
        String REQUEST_PARAM = "github github \ngithub";

        try (AsyncHttpClient client = getAsyncHttpClient(null)) {
            String requestUrl2 = URL + URLEncoder.encode(REQUEST_PARAM, "UTF-8");
            LoggerFactory.getLogger(QueryParametersTest.class).info("Executing request [{}] ...", requestUrl2);
            Response response = client.prepareGet(requestUrl2).execute().get();
            String s = URLDecoder.decode(response.getHeader("q"), "UTF-8");
            assertEquals(s, REQUEST_PARAM);
        }
    }

    @Test(groups = { "standalone", "default_provider" })
    public void urlWithColonTest() throws Throwable {
        try (AsyncHttpClient client = getAsyncHttpClient(null)) {
            String query = "test:colon:";
            Response response = client.prepareGet(String.format("http://127.0.0.1:%d/foo/test/colon?q=%s", port1, query)).setHeader("Content-Type", "text/html").execute().get(TIMEOUT, TimeUnit.SECONDS);
            assertEquals(response.getHeader("q"), query);
        }
    }
}
