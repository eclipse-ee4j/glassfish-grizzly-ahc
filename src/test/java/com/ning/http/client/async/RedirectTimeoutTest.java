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

import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.AsyncHttpClientConfig;
import com.ning.http.client.ListenableFuture;
import com.ning.http.client.Request;
import com.ning.http.client.RequestBuilder;
import com.ning.http.client.Response;

import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.io.Content;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.util.Callback;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class RedirectTimeoutTest extends AbstractBasicTest {

    private static final String REDIRECT_PATH = "/redirectPath" ;

    private static final String FINAL_PATH = "/finalPath";

    private static final String PAYLOAD = "Ok";

    private static final String GLOBAL_REQUEST_TIMEOUT = "5000";

    private static final String REQUEST_TIMEOUT = "2000";

    private static final String TIMEOUT_ERROR_MESSAGE = "Timeout exceeded";

    private static long SLEEP_TIME ;

    private static final long DELTA = 800;

    private  AsyncHttpClientConfig clientConfig ;

    @Override
    public Handler.Abstract configureHandler() throws Exception {
        return new PostRedirectGetHandler();
    }

    @Override
    public AsyncHttpClient getAsyncHttpClient(AsyncHttpClientConfig config)
    {
        return new AsyncHttpClient(config);
    }

    @BeforeMethod
    public void setUp() throws Exception
    {
        clientConfig = new AsyncHttpClientConfig.Builder().setFollowRedirect(true).setRequestTimeout(Integer.valueOf(GLOBAL_REQUEST_TIMEOUT)).build();
    }

    @DataProvider(name = "timeout")
    public Object[][] createData1() {
        return new Object[][] {
                { GLOBAL_REQUEST_TIMEOUT },
                { REQUEST_TIMEOUT}
        };
    }

    @Test(dataProvider = "timeout")
    public void testRequestTimeout(String timeout) {
        SLEEP_TIME = Long.valueOf(timeout) * 2;
        AsyncHttpClient client = getAsyncHttpClient(clientConfig);
        Request request = new RequestBuilder("GET").setRequestTimeout(Integer.valueOf(timeout)).setUrl(getTargetUrl().concat(REDIRECT_PATH)).build();
        ListenableFuture<Response> responseFuture = client.executeRequest(request);
        assertTimeout(responseFuture, timeout);
    }

    private void assertTimeout (ListenableFuture<Response> responseFuture, String timeout) {
        try {
            responseFuture.get(Long.valueOf(timeout) + DELTA, TimeUnit.MILLISECONDS);
            fail("TimeoutException must be thrown");
        }
        catch (ExecutionException e) {
            //This exception is thrown when Grizzly AHC Client aborts the future (that is the expected)
            assertTrue(e.getMessage().contains(TIMEOUT_ERROR_MESSAGE));
        }
        catch (TimeoutException e) {
            //This exception is thrown when the future wait times out.
            fail("Future timed out so Grizzly didn't honor the given request timeout. ", e);
        }
        catch (InterruptedException e) {
            fail("InterruptedException should not be thrown ", e);
        }
    }

    public static class PostRedirectGetHandler extends Handler.Abstract {
        @Override
        public boolean handle(org.eclipse.jetty.server.Request request, org.eclipse.jetty.server.Response response,
                              Callback callback)
                throws Exception {
            if(request.getHttpURI().getPath().endsWith(REDIRECT_PATH)){
                response.setStatus(HttpStatus.FOUND_302);
                final HttpFields.Mutable responseHeaders = response.getHeaders();
                responseHeaders.put(HttpHeader.LOCATION, FINAL_PATH);
            }
            else if (request.getHttpURI().getPath().endsWith(FINAL_PATH)) {
                try {
                    Thread.sleep(SLEEP_TIME);
                    response.setStatus(HttpStatus.OK_200);
                    Content.Sink.asOutputStream(response).write(PAYLOAD.getBytes());
                }
                catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            Content.Sink.asOutputStream(response).flush();
            callback.succeeded();
            return true;
        }
    }
}
