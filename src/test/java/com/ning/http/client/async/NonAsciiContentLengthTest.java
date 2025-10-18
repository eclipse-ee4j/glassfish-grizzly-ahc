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
import com.ning.http.client.AsyncHttpClient.BoundRequestBuilder;
import com.ning.http.client.Response;
import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.io.Content;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.util.Callback;
import org.testng.annotations.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import static org.testng.Assert.assertEquals;

public abstract class NonAsciiContentLengthTest extends AbstractBasicTest {

    public void setUpServer() throws Exception {
        server = new Server();
        port1 = findFreePort();
        ServerConnector listener = new ServerConnector(server);

        listener.setHost("127.0.0.1");
        listener.setPort(port1);
        server.addConnector(listener);
        server.setHandler(new Handler.Abstract() {

            @Override
            public boolean handle(Request request, org.eclipse.jetty.server.Response response, Callback callback)
                    throws Exception {
                final HttpFields requestHeaders = request.getHeaders();
                final HttpFields.Mutable responseHeaders = response.getHeaders();
                int MAX_BODY_SIZE = 1024; // Can only handle bodies of up to 1024 bytes.
                byte[] b = new byte[MAX_BODY_SIZE];
                int offset = 0;
                int numBytesRead;
                try (final InputStream is = Content.Source.asInputStream(request)) {
                    while ((numBytesRead = is.read(b, offset, MAX_BODY_SIZE - offset)) != -1) {
                        offset += numBytesRead;
                    }
                }
                assertEquals(request.getLength(), offset);
                response.setStatus(HttpStatus.OK_200);
                responseHeaders.put(HttpHeader.CONTENT_TYPE, requestHeaders.get(HttpHeader.CONTENT_TYPE));
                responseHeaders.put(HttpHeader.CONTENT_LENGTH, request.getLength());

                try (final OutputStream os = Content.Sink.asOutputStream(response)) {
                    os.write(b, 0, offset);
                }
                callback.succeeded();
                return true;
            }
        });
        server.start();
    }

    @Test(groups = { "standalone", "default_provider" })
    public void testNonAsciiContentLength() throws Exception {
        setUpServer();
        execute("test");
        execute("\u4E00"); // Unicode CJK ideograph for one
    }

    protected void execute(String body) throws IOException, InterruptedException, ExecutionException {
        try (AsyncHttpClient client = getAsyncHttpClient(null)) {
            BoundRequestBuilder r = client.preparePost(getTargetUrl()).setBody(body).setBodyEncoding("UTF-8");
            Future<Response> f = r.execute();
            Response resp = f.get();
            assertEquals(resp.getStatusCode(), 200);
            assertEquals(body, resp.getResponseBody("UTF-8"));
        }
    }
}
