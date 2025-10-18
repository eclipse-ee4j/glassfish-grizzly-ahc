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

import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.util.concurrent.Future;

import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.io.Content;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.util.Callback;
import org.testng.annotations.Test;

import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.Response;

/**
 * Test the Expect: 100-Continue.
 */
public abstract class Expect100ContinueTest extends AbstractBasicTest {

    private class ZeroCopyHandler extends Handler.Abstract {
        @Override
        public boolean handle(Request request, org.eclipse.jetty.server.Response response, Callback callback)
                throws Exception {

            int size = 10 * 1024;
            if (request.getLength() > 0) {
                size = (int) request.getLength();
            }
            byte[] bytes = new byte[size];
            if (bytes.length > 0) {
                try (final InputStream in = Content.Source.asInputStream(request)) {
                    final int read = in.read(bytes);
                    Content.Sink.asOutputStream(response).write(bytes, 0, read);
                }
            }

            response.setStatus(HttpStatus.OK_200);
            Content.Sink.asOutputStream(response).flush();
            callback.succeeded();
            return true;
        }
    }

    @Test(groups = { "standalone", "default_provider" })
    public void Expect100Continue() throws Throwable {
        try (AsyncHttpClient client = getAsyncHttpClient(null)) {
            ClassLoader cl = getClass().getClassLoader();
            URL url = cl.getResource("SimpleTextFile.txt");
            File file = new File(url.toURI());

            Future<Response> f = client.preparePut("http://127.0.0.1:" + port1 + "/").setHeader("Expect", "100-continue").setBody(file).execute();
            Response resp = f.get();
            assertNotNull(resp);
            assertEquals(resp.getStatusCode(), 200);
            assertEquals(resp.getResponseBody(), "This is a simple test file");
        }
    }

    @Override
    public Handler.Abstract configureHandler() throws Exception {
        return new ZeroCopyHandler();
    }
}
