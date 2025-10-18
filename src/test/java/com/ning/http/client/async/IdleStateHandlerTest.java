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
import com.ning.http.client.AsyncHttpClientConfig;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.io.Content;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.util.Callback;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.concurrent.atomic.AtomicBoolean;

public abstract class IdleStateHandlerTest extends AbstractBasicTest {
    private final AtomicBoolean isSet = new AtomicBoolean(false);

    private class IdleStateHandler extends Handler.Abstract {

        @Override
        public boolean handle(Request request, org.eclipse.jetty.server.Response response, Callback callback)
                throws Exception {

            try {
                Thread.sleep(20 * 1000);
            }
            catch (InterruptedException e) {
                e.printStackTrace();
            }
            response.setStatus(HttpStatus.OK_200);
            Content.Sink.asOutputStream(response).flush();
            Content.Sink.asOutputStream(response).close();
            callback.succeeded();
            return true;
        }
    }

    @BeforeClass(alwaysRun = true)
    public void setUpGlobal() throws Exception {
        server = new Server();

        port1 = findFreePort();
        ServerConnector listener = new ServerConnector(server);

        listener.setHost("127.0.0.1");
        listener.setPort(port1);
        server.addConnector(listener);

        server.setHandler(new IdleStateHandler());
        server.start();
        log.info("Local HTTP server started successfully");
    }

    @Test(groups = {"online", "default_provider"})
    public void idleStateTest() throws Throwable {
        isSet.getAndSet(false);
        AsyncHttpClientConfig cg = new AsyncHttpClientConfig.Builder().setPooledConnectionIdleTimeout(10 * 1000).build();

        try (AsyncHttpClient client = getAsyncHttpClient(cg)) {
            client.prepareGet(getTargetUrl()).execute().get();
        }
    }
}
