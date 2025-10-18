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
import com.ning.http.client.AsyncHttpClientConfig;
import com.ning.http.client.Realm;
import com.ning.http.client.Response;
import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.io.Content;
import org.eclipse.jetty.security.Authenticator;
import org.eclipse.jetty.security.Constraint;
import org.eclipse.jetty.security.HashLoginService;
import org.eclipse.jetty.security.LoginService;
import org.eclipse.jetty.security.SecurityHandler;
import org.eclipse.jetty.security.authentication.BasicAuthenticator;
import org.eclipse.jetty.security.authentication.DigestAuthenticator;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.util.Callback;
import org.eclipse.jetty.util.resource.ResourceFactory;
import org.testng.annotations.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Paths;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.fail;

public abstract class AuthTimeoutTest extends AbstractBasicTest {

    private final static String user = "user";

    private final static String admin = "admin";

    /**
      * A conversion pattern equivalent to the TTCCLayout. Current value is <b>%r [%t] %p %c %notEmpty{%x }- %m%n</b>.
      * Taken from Log4J 1.17
      */
    public static final String TTCC_CONVERSION_PATTERN = "%r [%t] %p %c %notEmpty{%x }- %m%n";

    public void setUpServer(String auth) throws Exception {
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
        _securityHandler.put("/*", Constraint.from(auth, Constraint.Authorization.SPECIFIC_ROLE, user, admin));
        final Authenticator authenticator;
        if (auth != null && auth.equals("BASIC")) {
            authenticator = new BasicAuthenticator();
        } else if (auth != null && auth.equals("DIGEST")) {
            authenticator = new DigestAuthenticator();
        } else {
            authenticator = new BasicAuthenticator();
        }
        _securityHandler.setAuthenticator(authenticator);
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

            // NOTE: handler sends less bytes than are given in Content-Length, which should lead to timeout

            final HttpFields requestHeaders = request.getHeaders();
            final HttpFields.Mutable responseHeaders = response.getHeaders();
            final OutputStream out = Content.Sink.asOutputStream(response);
            if (requestHeaders.get("X-Content") != null) {
                String content = requestHeaders.get("X-Content");
                responseHeaders.put(HttpHeader.CONTENT_LENGTH, String.valueOf(content.getBytes("UTF-8").length));
                out.write(content.substring(1).getBytes("UTF-8"));
                out.flush();
                out.close();
                callback.succeeded();
                return true;
            }

            response.setStatus(HttpStatus.OK_200);
            out.flush();
            out.close();
            callback.succeeded();
            return true;
        }
    }

    @Test(groups = { "standalone", "default_provider" }, enabled = false)
    public void basicAuthTimeoutTest() throws Exception {
        setUpServer("BASIC");
        AsyncHttpClientConfig config = new AsyncHttpClientConfig.Builder().setPooledConnectionIdleTimeout(2000).setConnectTimeout(20000).setRequestTimeout(2000).build();

        try (AsyncHttpClient client = getAsyncHttpClient(config)) {
            Future<Response> f = execute(client, false);
            try {
                f.get();
                fail("expected timeout");
            } catch (Exception e) {
                inspectException(e);
            }
        }
    }

    @Test(groups = { "standalone", "default_provider" }, enabled = false)
    public void basicPreemptiveAuthTimeoutTest() throws Exception {
        setUpServer("BASIC");
        AsyncHttpClientConfig config = new AsyncHttpClientConfig.Builder().setPooledConnectionIdleTimeout(2000).setConnectTimeout(20000).setRequestTimeout(2000).build();

        try (AsyncHttpClient client = getAsyncHttpClient(config)) {
            Future<Response> f = execute(client, true);
            try {
                f.get();
                fail("expected timeout");
            } catch (Exception e) {
                inspectException(e);
            }
        }
    }

    @Test(groups = { "standalone", "default_provider" }, enabled = false)
    public void digestAuthTimeoutTest() throws Exception {
        setUpServer("DIGEST");
        AsyncHttpClientConfig config = new AsyncHttpClientConfig.Builder().setPooledConnectionIdleTimeout(2000).setConnectTimeout(20000).setRequestTimeout(2000).build();

        try (AsyncHttpClient client = getAsyncHttpClient(config)) {
            Future<Response> f = execute(client, false);
            try {
                f.get();
                fail("expected timeout");
            } catch (Exception e) {
                inspectException(e);
            }
        }
    }

    @Test(groups = { "standalone", "default_provider" }, enabled = false)
    public void digestPreemptiveAuthTimeoutTest() throws Exception {
        setUpServer("DIGEST");
        AsyncHttpClientConfig config = new AsyncHttpClientConfig.Builder().setPooledConnectionIdleTimeout(2000).setConnectTimeout(20000).setRequestTimeout(2000).build();

        try (AsyncHttpClient client = getAsyncHttpClient(config)) {
            Future<Response> f = execute(client, true);
            f.get();
            fail("expected timeout");
        } catch (Exception e) {
            inspectException(e);
        }
    }

    @Test(groups = { "standalone", "default_provider" }, enabled = false)
    public void basicFutureAuthTimeoutTest() throws Exception {
        setUpServer("BASIC");
        AsyncHttpClientConfig config = new AsyncHttpClientConfig.Builder().setPooledConnectionIdleTimeout(2000).setConnectTimeout(20000).setRequestTimeout(2000).build();

        try (AsyncHttpClient client = getAsyncHttpClient(config)) {
            Future<Response> f = execute(client, false);
            f.get(1, TimeUnit.SECONDS);
            fail("expected timeout");
        } catch (Exception e) {
            inspectException(e);
        }
    }

    @Test(groups = { "standalone", "default_provider" }, enabled = false)
    public void basicFuturePreemptiveAuthTimeoutTest() throws Exception {
        setUpServer("BASIC");
        AsyncHttpClientConfig config = new AsyncHttpClientConfig.Builder().setPooledConnectionIdleTimeout(2000).setConnectTimeout(20000).setRequestTimeout(2000).build();

        try (AsyncHttpClient client = getAsyncHttpClient(config)) {
            Future<Response> f = execute(client, true);
            f.get(1, TimeUnit.SECONDS);
            fail("expected timeout");
        } catch (Exception e) {
            inspectException(e);
        }
    }

    @Test(groups = { "standalone", "default_provider" }, enabled = false)
    public void digestFutureAuthTimeoutTest() throws Exception {
        setUpServer("DIGEST");
        AsyncHttpClientConfig config = new AsyncHttpClientConfig.Builder().setPooledConnectionIdleTimeout(2000).setConnectTimeout(20000).setRequestTimeout(2000).build();

        try (AsyncHttpClient client = getAsyncHttpClient(config)) {
            Future<Response> f = execute(client, false);
            f.get(1, TimeUnit.SECONDS);
            fail("expected timeout");
        } catch (Exception e) {
            inspectException(e);
        }
    }

    @Test(groups = { "standalone", "default_provider" }, enabled = false)
    public void digestFuturePreemptiveAuthTimeoutTest() throws Exception {
        setUpServer("DIGEST");
        AsyncHttpClientConfig config = new AsyncHttpClientConfig.Builder().setPooledConnectionIdleTimeout(2000).setConnectTimeout(20000).setRequestTimeout(2000).build();

        try (AsyncHttpClient client = getAsyncHttpClient(config)) {
            Future<Response> f = execute(client, true);
            f.get(1, TimeUnit.SECONDS);
            fail("expected timeout");
        } catch (Exception e) {
            inspectException(e);
        }
    }

    protected void inspectException(Throwable t) {
        assertNotNull(t.getCause());
        assertEquals(t.getCause().getClass(), IOException.class);
        if (!t.getCause().getMessage().startsWith("Remotely Closed")) {
            fail();
        }
    }

    protected Future<Response> execute(AsyncHttpClient client, boolean preemptive) throws IOException {
        AsyncHttpClient.BoundRequestBuilder r = client.prepareGet(getTargetUrl()).setRealm(realm(preemptive)).setHeader("X-Content", "Test");
        Future<Response> f = r.execute();
        return f;
    }

    private Realm realm(boolean preemptive) {
        return (new Realm.RealmBuilder()).setPrincipal(user).setPassword(admin).setUsePreemptiveAuth(preemptive).build();
    }

    @Override
    protected String getTargetUrl() {
        return "http://127.0.0.1:" + port1 + "/";
    }

    @Override
    public Handler.Abstract configureHandler() throws Exception {
        return new SimpleHandler();
    }
}
