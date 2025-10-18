/*
 * Copyright (c) 2025 Contributors to the Eclipse Foundation.
 * Copyright (c) 2017, 2020 Oracle and/or its affiliates. All rights reserved.
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
import com.ning.http.client.Realm;
import com.ning.http.client.Response;
import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.io.Content;
import org.eclipse.jetty.security.Constraint;
import org.eclipse.jetty.security.HashLoginService;
import org.eclipse.jetty.security.LoginService;
import org.eclipse.jetty.security.SecurityHandler;
import org.eclipse.jetty.security.authentication.DigestAuthenticator;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.util.Callback;
import org.eclipse.jetty.util.resource.ResourceFactory;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

public abstract class DigestAuthTest extends AbstractBasicTest {

    private final static String user = "user";
    private final static String admin = "admin";

    @BeforeClass(alwaysRun = true)
    @Override
    public void setUpGlobal() throws Exception {
        server = new Server();

        port1 = findFreePort();
        ServerConnector listener = new ServerConnector(server);

        listener.setHost("127.0.0.1");
        listener.setPort(port1);

        server.addConnector(listener);

        final LoginService loginService;
        try (final ResourceFactory.Closeable rf = ResourceFactory.closeable()) {
            loginService =
                    new HashLoginService("MyRealm", rf.newResource(Paths.get("src/test/resources/realm.properties")));
        }
        server.addBean(loginService);

        final SecurityHandler.PathMapped _securityHandler = new SecurityHandler.PathMapped();
        _securityHandler.put("/*", Constraint.from("BASIC", Constraint.Authorization.SPECIFIC_ROLE, user, admin));
        _securityHandler.setAuthenticator(new DigestAuthenticator());
        _securityHandler.setLoginService(loginService);
        _securityHandler.setHandler(configureHandler());
        server.setHandler(_securityHandler);
        server.start();
        log.info("Local HTTP server started successfully");
    }

    private class SimpleHandler extends Handler.Abstract {
        @Override
        public boolean handle(Request request, org.eclipse.jetty.server.Response response, Callback callback)
                throws Exception {
            final HttpFields requestHeaders = request.getHeaders();
            final HttpFields.Mutable responseHeaders = response.getHeaders();
            responseHeaders.put("X-Auth", requestHeaders.get(HttpHeader.AUTHORIZATION));
            response.setStatus(HttpStatus.OK_200);
            Content.Sink.asOutputStream(response).flush();
            Content.Sink.asOutputStream(response).close();
            callback.succeeded();
            return true;
        }
    }

    @Test(groups = { "standalone", "default_provider" })
    public void digestAuthTest() throws IOException, ExecutionException, TimeoutException, InterruptedException {
        try (AsyncHttpClient client = getAsyncHttpClient(null)) {
            AsyncHttpClient.BoundRequestBuilder r = client.prepareGet("http://127.0.0.1:" + port1 + "/").setRealm((new Realm.RealmBuilder()).setPrincipal(user).setPassword(admin).setRealmName("MyRealm").setScheme(Realm.AuthScheme.DIGEST).build());

            Future<Response> f = r.execute();
            Response resp = f.get(60, TimeUnit.SECONDS);
            assertNotNull(resp);
            assertEquals(resp.getStatusCode(), 200);
            assertNotNull(resp.getHeader("X-Auth"));
        }
    }

    @Test(groups = { "standalone", "default_provider" })
    public void digestAuthTestWithoutScheme() throws IOException, ExecutionException, TimeoutException, InterruptedException {
        try (AsyncHttpClient client = getAsyncHttpClient(null)) {
            AsyncHttpClient.BoundRequestBuilder r = client.prepareGet("http://127.0.0.1:" + port1 + "/").setRealm((new Realm.RealmBuilder()).setPrincipal(user).setPassword(admin).setRealmName("MyRealm").build());

            Future<Response> f = r.execute();
            Response resp = f.get(60, TimeUnit.SECONDS);
            assertNotNull(resp);
            assertEquals(resp.getStatusCode(), 200);
            assertNotNull(resp.getHeader("X-Auth"));
        }
    }

    @Test(groups = { "standalone", "default_provider" })
    public void digestAuthNegativeTest() throws IOException, ExecutionException, TimeoutException, InterruptedException {
        try (AsyncHttpClient client = getAsyncHttpClient(null)) {
            AsyncHttpClient.BoundRequestBuilder r = client.prepareGet("http://127.0.0.1:" + port1 + "/").setRealm((new Realm.RealmBuilder()).setPrincipal("fake").setPassword(admin).setScheme(Realm.AuthScheme.DIGEST).build());

            Future<Response> f = r.execute();
            Response resp = f.get(20, TimeUnit.SECONDS);
            assertNotNull(resp);
            assertEquals(resp.getStatusCode(), 401);
        }
    }

    @Override
    public Handler.Abstract configureHandler() throws Exception {
        return new SimpleHandler();
    }
}
