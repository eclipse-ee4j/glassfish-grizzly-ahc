/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
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

package com.ning.http.client.async.grizzly;

import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.AsyncHttpClientConfig;
import com.ning.http.client.AsyncHttpProviderConfig;
import com.ning.http.client.Response;
import com.ning.http.client.async.ProviderUtil;
import com.ning.http.client.async.PutLargeFileTest;
import com.ning.http.client.providers.grizzly.GrizzlyAsyncHttpProviderConfig;
import com.ning.http.client.providers.grizzly.TransportCustomizer;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.concurrent.ExecutionException;

import static com.ning.http.client.providers.grizzly.GrizzlyAsyncHttpProviderConfig.Property.TRANSPORT_CUSTOMIZER;

public class GrizzlyPutLargeFileTest extends PutLargeFileTest {

    @Override
    public AsyncHttpClient getAsyncHttpClient(AsyncHttpClientConfig config) {
        return ProviderUtil.grizzlyProvider(config);
    }

    @Test
    public void testPutLargeFileHardLimit() throws Exception {
        final long repeats = (LARGE_BYTES_SIZE / TEST_BYTES.length) + 1;
        largeFile = createTempFile(TEST_BYTES, (int) repeats);
        final int timeout = (int) (largeFile.length() / 1000);
        final AsyncHttpClientConfig config = new AsyncHttpClientConfig.Builder().setConnectTimeout(timeout)
                                                                                .setAsyncHttpClientProviderConfig(
                                                                                        getHardLimitProviderConfig())
                                                                                .build();
        try (AsyncHttpClient client = getAsyncHttpClient(config)) {
            AsyncHttpClient.BoundRequestBuilder rb = client.preparePut(getTargetUrl());
            rb.setBody(largeFile);
            try {
                final Response response = rb.execute().get();
                Assert.assertTrue(response.getStatusCode() != 200);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            } catch (ExecutionException ignored) {
                // expected
            }
        }
    }

    private AsyncHttpProviderConfig<?, ?> getHardLimitProviderConfig() {
        final GrizzlyAsyncHttpProviderConfig config = new GrizzlyAsyncHttpProviderConfig();
        config.addProperty(TRANSPORT_CUSTOMIZER, (TransportCustomizer) (transport, builder) -> {
            transport.getAsyncQueueIO().getWriter().setMaxPendingBytesPerConnection(TEST_BYTES.length);
            transport.getAsyncQueueIO().getWriter().setPendingBytesHardLimitMultiplier(1);
        });
        return config;
    }
}
