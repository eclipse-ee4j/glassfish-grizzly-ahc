/*
 * Copyright (c) 2025 Contributors to the Eclipse Foundation.
 * Copyright (c) 2017, 2020 Oracle and/or its affiliates. All rights reserved.
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

import static java.nio.charset.StandardCharsets.*;

import com.ning.http.client.AsyncHandler;
import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.AsyncHttpClientConfig;
import com.ning.http.client.HttpResponseBodyPart;
import com.ning.http.client.HttpResponseHeaders;
import com.ning.http.client.HttpResponseStatus;
import com.ning.http.client.Realm;
import com.ning.http.client.Realm.AuthScheme;
import com.ning.http.client.Response;

import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.io.Content;
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
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Paths;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

public abstract class BasicAuthTest extends AbstractBasicTest {

    protected final static String MY_MESSAGE = "my message";
    protected final static String user = "user";
    protected final static String admin = "admin";

    private Server server2;
    private Server serverNoAuth;
    private int portNoAuth;
    
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
        _securityHandler.setAuthenticator(new BasicAuthenticator());
        _securityHandler.setLoginService(loginService);
        _securityHandler.setHandler(configureHandler());

        server.setHandler(_securityHandler);
        server.start();
        log.info("Local HTTP server started successfully");
    }

    private String getFileContent(final File file) {
        FileInputStream in = null;
        try {
            if (file.exists() && file.canRead()) {
                final StringBuilder sb = new StringBuilder(128);
                final byte[] b = new byte[512];
                int read;
                in = new FileInputStream(file);
                while ((read = in.read(b)) != -1) {
                    sb.append(new String(b, 0, read, "UTF-8"));
                }
                return sb.toString();
            }
            throw new IllegalArgumentException("File does not exist or cannot be read: " + file.getCanonicalPath());
        } catch (IOException ioe) {
            throw new IllegalStateException(ioe);
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    private void setUpSecondServer() throws Exception {
        server2 = new Server();
        port2 = findFreePort();

        ServerConnector connector = new ServerConnector(server2);
        connector.setHost("127.0.0.1");
        connector.setPort(port2);

        server2.addConnector(connector);

        final LoginService loginService;
        try (final ResourceFactory.Closeable rf = ResourceFactory.closeable()) {
            loginService =
                    new HashLoginService("MyRealm", rf.newResource(Paths.get("src/test/resources/realm.properties")));
        }
        server2.addBean(loginService);

        final SecurityHandler.PathMapped _securityHandler = new SecurityHandler.PathMapped() {

            @Override
            public boolean handle(Request request, org.eclipse.jetty.server.Response response, Callback callback)
                    throws Exception {
                final HttpFields requestHeaders = request.getHeaders();
                System.err.println("request in security handler");
                System.err.println("Authorization: " + requestHeaders.get(HttpHeader.AUTHORIZATION));
                System.err.println("RequestUri: " + request.getHttpURI().getPath());
                return super.handle(request, response, callback);
            }
        };
        _securityHandler.put("/*", Constraint.from("DIGEST", Constraint.Authorization.SPECIFIC_ROLE, user, admin));
        _securityHandler.setAuthenticator(new DigestAuthenticator());
        _securityHandler.setLoginService(loginService);
        _securityHandler.setHandler(new RedirectHandler());

        server2.setHandler(_securityHandler);
        server2.start();
    }

    private void stopSecondServer() throws Exception {
        server2.stop();
    }

    private void setUpServerNoAuth() throws Exception {
        serverNoAuth = new Server();
        portNoAuth = findFreePort();

        ServerConnector listener = new ServerConnector(serverNoAuth);
        listener.setHost("127.0.0.1");
        listener.setPort(portNoAuth);

        serverNoAuth.addConnector(listener);

        serverNoAuth.setHandler(new SimpleHandler());
        serverNoAuth.start();
    }
    
    private void stopServerNoAuth() throws Exception {
        serverNoAuth.stop();
    }

    private class RedirectHandler extends Handler.Abstract {

        @Override
        public boolean handle(Request request, org.eclipse.jetty.server.Response response, Callback callback)
                throws Exception {

            System.err.println("redirecthandler");
            System.err.println("request: " + request.getHttpURI().getPath());

            final HttpFields requestHeaders = request.getHeaders();
            final HttpFields.Mutable responseHeaders = response.getHeaders();
            if ("/uff".equals(request.getHttpURI().getPath())) {
                System.err.println("redirect to /bla");
                response.setStatus(HttpStatus.FOUND_302);
                responseHeaders.put(HttpHeader.LOCATION, "/bla");
                Content.Sink.asOutputStream(response).flush();
                Content.Sink.asOutputStream(response).close();

            } else {
                System.err.println("got redirected" + request.getHttpURI().getPath());
                response.setStatus(HttpStatus.OK_200);
                responseHeaders.put("X-Auth", requestHeaders.get(HttpHeader.AUTHORIZATION));
                responseHeaders.put("X-Content-Length", String.valueOf(request.getLength()));
                byte[] b = "content".getBytes(UTF_8);
                responseHeaders.put(HttpHeader.CONTENT_LENGTH, b.length);
                Content.Sink.asOutputStream(response).write(b);
                Content.Sink.asOutputStream(response).flush();
                Content.Sink.asOutputStream(response).close();
            }
            callback.succeeded();
            return true;
        }
    }

    private class SimpleHandler extends Handler.Abstract {

        @Override
        public boolean handle(Request request, org.eclipse.jetty.server.Response response, Callback callback)
                throws Exception {
            final HttpFields requestHeaders = request.getHeaders();
            final HttpFields.Mutable responseHeaders = response.getHeaders();
            if (requestHeaders.get("X-401") != null) {
                response.setStatus(HttpStatus.UNAUTHORIZED_401);
                responseHeaders.put(HttpHeader.CONTENT_LENGTH, 0L);

            } else {
                responseHeaders.put("X-Auth", requestHeaders.get(HttpHeader.AUTHORIZATION));
                responseHeaders.put("X-Content-Length", String.valueOf(request.getLength()));
                response.setStatus(HttpStatus.OK_200);
    
                int size = 10 * 1024;
                if (request.getLength() > 0) {
                    size = (int) request.getLength();
                }
                byte[] bytes = new byte[size];
                int contentLength = 0;
                if (bytes.length > 0) {
                    try (final InputStream in = Content.Source.asInputStream(request)) {
                        int read = in.read(bytes);
                        if (read > 0) {
                            contentLength = read;
                            Content.Sink.asOutputStream(response).write(bytes, 0, read);
                        }
                    }
                }
                responseHeaders.put(HttpHeader.CONTENT_LENGTH, contentLength);
            }
            Content.Sink.asOutputStream(response).flush();
            Content.Sink.asOutputStream(response).close();
            callback.succeeded();
            return true;
        }
    }

    @Test(groups = { "standalone", "default_provider" })
    public void basicAuthTest() throws IOException, ExecutionException, TimeoutException, InterruptedException {
        try (AsyncHttpClient client = getAsyncHttpClient(null)) {
            AsyncHttpClient.BoundRequestBuilder r = client.prepareGet(getTargetUrl()).setRealm((new Realm.RealmBuilder()).setPrincipal(user).setPassword(admin).build());

            Future<Response> f = r.execute();
            Response resp = f.get(3, TimeUnit.SECONDS);
            assertNotNull(resp);
            assertNotNull(resp.getHeader("X-Auth"));
            assertEquals(resp.getStatusCode(), 200);
        }
    }

    @Test(groups = { "standalone", "default_provider" })
    public void redirectAndBasicAuthTest() throws Exception, ExecutionException, TimeoutException, InterruptedException {
        try (AsyncHttpClient client = getAsyncHttpClient(new AsyncHttpClientConfig.Builder().setFollowRedirect(true).setMaxRedirects(10).build())) {
            setUpSecondServer();
            AsyncHttpClient.BoundRequestBuilder r = client.prepareGet(getTargetUrl2())
                    .setRealm((new Realm.RealmBuilder()).setPrincipal(user).setPassword(admin).build());

            Future<Response> f = r.execute();
            Response resp = f.get(3, TimeUnit.SECONDS);
            assertNotNull(resp, "Response shouldn't be null");
            assertEquals(resp.getStatusCode(), 200, "Status code should be 200-OK");
            assertNotNull(resp.getHeader("X-Auth"), "X-Auth shouldn't be null");

        } finally {
            stopSecondServer();
        }
    }

    @Override
    protected String getTargetUrl() {
        return "http://127.0.0.1:" + port1 + "/";
    }

    protected String getTargetUrl2() {
        return "http://127.0.0.1:" + port2 + "/uff";
    }

    protected String getTargetUrlNoAuth() {
        return "http://127.0.0.1:" + portNoAuth + "/";
    }

    @Override
    public Handler.Abstract configureHandler() throws Exception {
        return new SimpleHandler();
    }

    @Test(groups = { "standalone", "default_provider" })
    public void basic401Test() throws IOException, ExecutionException, TimeoutException, InterruptedException {
        try (AsyncHttpClient client = getAsyncHttpClient(null)) {
            AsyncHttpClient.BoundRequestBuilder r = client.prepareGet(getTargetUrl()).setHeader("X-401", "401").setRealm((new Realm.RealmBuilder()).setPrincipal(user).setPassword(admin).build());

            Future<Integer> f = r.execute(new AsyncHandler<Integer>() {

                private HttpResponseStatus status;

                public void onThrowable(Throwable t) {

                }

                public STATE onBodyPartReceived(HttpResponseBodyPart bodyPart) throws Exception {
                    return STATE.CONTINUE;
                }

                public STATE onStatusReceived(HttpResponseStatus responseStatus) throws Exception {
                    this.status = responseStatus;

                    if (status.getStatusCode() != 200) {
                        return STATE.ABORT;
                    }
                    return STATE.CONTINUE;
                }

                public STATE onHeadersReceived(HttpResponseHeaders headers) throws Exception {
                    return STATE.CONTINUE;
                }

                public Integer onCompleted() throws Exception {
                    return status.getStatusCode();
                }
            });
            Integer statusCode = f.get(10, TimeUnit.SECONDS);
            assertNotNull(statusCode);
            assertEquals(statusCode.intValue(), 401);
        }
    }

    @Test(groups = { "standalone", "default_provider" })
    public void basicAuthTestPreemtiveTest() throws Exception, ExecutionException, TimeoutException, InterruptedException {
        try (AsyncHttpClient client = getAsyncHttpClient(null)) {
            setUpServerNoAuth();

            AsyncHttpClient.BoundRequestBuilder r = client.prepareGet(getTargetUrlNoAuth())
            		.setRealm((new Realm.RealmBuilder()).setScheme(AuthScheme.BASIC).setPrincipal(user).setPassword(admin).setUsePreemptiveAuth(true).build());

            Future<Response> f = r.execute();
            Response resp = f.get(3, TimeUnit.SECONDS);
            assertNotNull(resp);
            assertNotNull(resp.getHeader("X-Auth"));
            assertEquals(resp.getStatusCode(), 200);
        } finally {
            stopServerNoAuth();
        }
    }

    @Test(groups = { "standalone", "default_provider" })
    public void basicAuthNegativeTest() throws IOException, ExecutionException, TimeoutException, InterruptedException {
        try (AsyncHttpClient client = getAsyncHttpClient(null)) {
            AsyncHttpClient.BoundRequestBuilder r = client.prepareGet(getTargetUrl()).setRealm((new Realm.RealmBuilder()).setPrincipal("fake").setPassword(admin).build());

            Future<Response> f = r.execute();
            Response resp = f.get(3, TimeUnit.SECONDS);
            assertNotNull(resp);
            assertEquals(resp.getStatusCode(), 401);
        }
    }

    @Test(groups = { "standalone", "default_provider" })
    public void basicAuthInputStreamTest() throws IOException, ExecutionException, TimeoutException, InterruptedException {
        try (AsyncHttpClient client = getAsyncHttpClient(null)) {
            ByteArrayInputStream is = new ByteArrayInputStream("test".getBytes());
            AsyncHttpClient.BoundRequestBuilder r = client.preparePost(getTargetUrl()).setBody(is).setRealm((new Realm.RealmBuilder()).setPrincipal(user).setPassword(admin).build());

            Future<Response> f = r.execute();
            Response resp = f.get(30, TimeUnit.SECONDS);
            assertNotNull(resp);
            assertNotNull(resp.getHeader("X-Auth"));
            assertEquals(resp.getStatusCode(), 200);
            assertEquals(resp.getResponseBody(), "test");
        }
    }

    @Test(groups = { "standalone", "default_provider" })
    public void basicAuthFileTest() throws Throwable {
        try (AsyncHttpClient client = getAsyncHttpClient(null)) {
            ClassLoader cl = getClass().getClassLoader();
            // override system properties
            URL url = cl.getResource("SimpleTextFile.txt");
            File file = new File(url.toURI());
            final String fileContent = getFileContent(file);

            AsyncHttpClient.BoundRequestBuilder r = client.preparePost(getTargetUrl()).setBody(file).setRealm((new Realm.RealmBuilder()).setPrincipal(user).setPassword(admin).build());

            Future<Response> f = r.execute();
            Response resp = f.get(3, TimeUnit.SECONDS);
            assertNotNull(resp);
            assertNotNull(resp.getHeader("X-Auth"));
            assertEquals(resp.getStatusCode(), 200);
            assertEquals(resp.getResponseBody(), fileContent);
        }
    }

    @Test(groups = { "standalone", "default_provider" })
    public void basicAuthAsyncConfigTest() throws Throwable {
        try (AsyncHttpClient client = getAsyncHttpClient(new AsyncHttpClientConfig.Builder().setRealm((new Realm.RealmBuilder()).setPrincipal(user).setPassword(admin).build()).build())) {
            ClassLoader cl = getClass().getClassLoader();
            // override system properties
            URL url = cl.getResource("SimpleTextFile.txt");
            File file = new File(url.toURI());
            final String fileContent = getFileContent(file);

            AsyncHttpClient.BoundRequestBuilder r = client.preparePost(getTargetUrl()).setBody(file);

            Future<Response> f = r.execute();
            Response resp = f.get(3, TimeUnit.SECONDS);
            assertNotNull(resp);
            assertNotNull(resp.getHeader("X-Auth"));
            assertEquals(resp.getStatusCode(), 200);
            assertEquals(resp.getResponseBody(), fileContent);
        }
    }

    @Test(groups = { "standalone", "default_provider" })
    public void basicAuthFileNoKeepAliveTest() throws Throwable {
        try (AsyncHttpClient client = getAsyncHttpClient(new AsyncHttpClientConfig.Builder().setAllowPoolingConnections(false).build())) {
            ClassLoader cl = getClass().getClassLoader();
            // override system properties
            URL url = cl.getResource("SimpleTextFile.txt");
            File file = new File(url.toURI());
            final String fileContent = getFileContent(file);

            AsyncHttpClient.BoundRequestBuilder r = client.preparePost(getTargetUrl()).setBody(file).setRealm((new Realm.RealmBuilder()).setPrincipal(user).setPassword(admin).build());

            Future<Response> f = r.execute();
            Response resp = f.get(3, TimeUnit.SECONDS);
            assertNotNull(resp);
            assertNotNull(resp.getHeader("X-Auth"));
            assertEquals(resp.getStatusCode(), 200);
            assertEquals(resp.getResponseBody(), fileContent);
        }
    }

    @Test(groups = { "standalone", "default_provider" })
    public void noneAuthTest() throws IOException, ExecutionException, TimeoutException, InterruptedException {
        try (AsyncHttpClient client = getAsyncHttpClient(null)) {
            AsyncHttpClient.BoundRequestBuilder r = client.prepareGet(getTargetUrl()).setRealm((new Realm.RealmBuilder()).setPrincipal(user).setPassword(admin).build());

            Future<Response> f = r.execute();
            Response resp = f.get(3, TimeUnit.SECONDS);
            assertNotNull(resp);
            assertNotNull(resp.getHeader("X-Auth"));
            assertEquals(resp.getStatusCode(), 200);
        }
    }
}
