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

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.fail;

import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.io.Content;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.util.Callback;
import org.testng.annotations.Test;

import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.AsyncHttpClientConfig;
import com.ning.http.util.AsyncHttpProviderUtils;

import java.io.IOException;
import java.io.OutputStream;

public abstract class RetryRequestTest extends AbstractBasicTest {
    public static class SlowAndBigHandler extends Handler.Abstract {

        @Override
        public boolean handle(Request request, org.eclipse.jetty.server.Response response, Callback callback)
                throws Exception {

            final HttpFields.Mutable responseHeaders = response.getHeaders();
            int load = 100;
            response.setStatus(HttpStatus.OK_200);
            responseHeaders.put(HttpHeader.CONTENT_LENGTH, load);
            responseHeaders.put(HttpHeader.CONTENT_TYPE, "application/octet-stream");

            Content.Sink.asOutputStream(response).flush();

            OutputStream os = Content.Sink.asOutputStream(response);
            for (int i = 0; i < load; i++) {
                os.write(i % 255);

                try {
                    Thread.sleep(300);
                } catch (InterruptedException ex) {
                    // nuku
                }

                if (i > load / 10) {
                    Response.writeError(request, response, null, HttpStatus.INTERNAL_SERVER_ERROR_500);
                }
            }

            Content.Sink.asOutputStream(response).flush();
            Content.Sink.asOutputStream(response).close();
            callback.succeeded();
            return true;
        }
    }

    protected String getTargetUrl() {
        return String.format("http://127.0.0.1:%d/", port1);
    }

    @Override
    public Handler.Abstract configureHandler() throws Exception {
        return new SlowAndBigHandler();
    }

    @Test(groups = { "standalone", "default_provider" })
    public void testMaxRetry() throws Throwable {
        try (AsyncHttpClient client = getAsyncHttpClient(new AsyncHttpClientConfig.Builder().setMaxRequestRetry(0).build())) {
            client.executeRequest(client.prepareGet(getTargetUrl()).build()).get();
            fail();
        } catch (Exception t) {
            assertNotNull(t.getCause());
            assertEquals(t.getCause().getClass(), IOException.class);
            if (t.getCause() != AsyncHttpProviderUtils.REMOTELY_CLOSED_EXCEPTION) {
                fail();
            }
        }
    }
}
