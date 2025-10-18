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

import com.ning.http.client.AsyncCompletionHandler;
import com.ning.http.client.AsyncHandler;
import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.AsyncHttpClientConfig;
import com.ning.http.client.HttpResponseBodyPart;
import com.ning.http.client.HttpResponseHeaders;
import com.ning.http.client.HttpResponseStatus;
import com.ning.http.client.Response;

import org.eclipse.jetty.http.HttpCookie;
import org.eclipse.jetty.http.HttpField;
import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.io.Content;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.util.Callback;
import org.eclipse.jetty.util.Fields;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;

import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.util.List;

public abstract class AbstractBasicTest {
    
    public final static String TEXT_HTML_CONTENT_TYPE_WITH_UTF_8_CHARSET = "text/html; charset=utf-8";
    
    protected final Logger log = LoggerFactory.getLogger(AbstractBasicTest.class);
    protected Server server;
    protected int port1;
    protected int port2;

    public final static int TIMEOUT = 30;

    public static class EchoHandler extends Handler.Abstract {

        @Override
        public boolean handle(org.eclipse.jetty.server.Request request, org.eclipse.jetty.server.Response response,
                              Callback callback) throws Exception {

            final HttpFields requestHeaders = request.getHeaders();
            final HttpFields.Mutable responseHeaders = response.getHeaders();

            if (requestHeaders.get("X-HEAD") != null) {
                responseHeaders.put(HttpHeader.CONTENT_LENGTH, 1L);
            }

            if (requestHeaders.get("X-ISO") != null) {
                responseHeaders.put(HttpHeader.CONTENT_TYPE, "text/html; charset=ISO-8859-1");
            } else {
                responseHeaders.put(HttpHeader.CONTENT_TYPE, TEXT_HTML_CONTENT_TYPE_WITH_UTF_8_CHARSET);
            }

            if (request.getMethod().equalsIgnoreCase("OPTIONS")) {
                responseHeaders.put(HttpHeader.ALLOW, "GET,HEAD,POST,OPTIONS,TRACE");
            }

            String param;
            for (final HttpField httpField: requestHeaders) {
                param = httpField.getName();
                if (param.startsWith("LockThread")) {
                    try {
                        Thread.sleep(40 * 1000);
                    } catch (InterruptedException ex) {
                    }
                }

                if (param.startsWith("X-redirect")) {
                    org.eclipse.jetty.server.Response.sendRedirect(request, response, callback, httpField.getValue());
                    callback.succeeded();
                    return true;
                }
                if (param.startsWith("Y-")) {
                    responseHeaders.put(param.substring(2), httpField.getValue());
                } else {
                    responseHeaders.put("X-" + param, httpField.getValue());
                }
            }

            final StringBuilder requestBody = new StringBuilder();
            final Fields allParameters = Request.getParameters(request);
            for (final Fields.Field field: allParameters) {
                param = field.getName();
                responseHeaders.put("X-" + param, field.getValue());
                requestBody.append(param);
                requestBody.append("_");
            }

            final String pathInfo = Request.getPathInContext(request);
            if (pathInfo != null)
                responseHeaders.put("X-pathInfo", pathInfo);

            final String queryString = request.getHttpURI().getQuery();
            if (queryString != null)
                responseHeaders.put("X-queryString", queryString);

            responseHeaders.put("X-KEEP-ALIVE", Request.getRemoteAddr(request) + ":" + Request.getRemotePort(request));

            final List<HttpCookie> cookies = Request.getCookies(request);
            for (final HttpCookie cookie: cookies) {
                org.eclipse.jetty.server.Response.addCookie(response, cookie);
            }

            if (requestBody.length() > 0) {
                Content.Sink.asOutputStream(response).write(requestBody.toString().getBytes());
            }

            int size = 16384;
            if (request.getLength() > 0) {
                size = (int) request.getLength();
            }
            byte[] bytes = new byte[size];
            if (bytes.length > 0) {
                int read = 0;
                try (final InputStream in = Content.Source.asInputStream(request)) {
                    while (read > -1) {
                        read = in.read(bytes);
                        if (read > 0) {
                            Content.Sink.asOutputStream(response).write(bytes, 0, read);
                        }
                    }
                }
            }

            response.setStatus(HttpStatus.OK_200);
            Content.Sink.asOutputStream(response).flush();
            Content.Sink.asOutputStream(response).close();
            callback.succeeded();
            return true;
        }
    }

    @AfterClass(alwaysRun = true)
    public void tearDownGlobal() throws Exception {
        server.stop();
    }

    protected int findFreePort() throws IOException {

        try (ServerSocket socket = new ServerSocket(0)){
            return socket.getLocalPort();
        }
    }

    protected String getTargetUrl() {
        return String.format("http://127.0.0.1:%d/foo/test", port1);
    }

    protected String getTargetUrl2() {
        return String.format("https://127.0.0.1:%d/foo/test", port2);
    }

    public Handler.Abstract configureHandler() throws Exception {
        return new EchoHandler();
    }

    @BeforeClass(alwaysRun = true)
    public void setUpGlobal() throws Exception {
        server = new Server();

        port1 = findFreePort();
        port2 = findFreePort();
        ServerConnector listener = new ServerConnector(server);

        listener.setHost("127.0.0.1");
        listener.setPort(port1);

        server.addConnector(listener);

        listener = new ServerConnector(server);
        listener.setHost("127.0.0.1");
        listener.setPort(port2);

        server.addConnector(listener);

        server.setHandler(configureHandler());
        server.start();
        log.info("Local HTTP server started successfully");
    }

    public static class AsyncCompletionHandlerAdapter extends AsyncCompletionHandler<Response> {

        @Override
        public Response onCompleted(Response response) throws Exception {
            return response;
        }

        @Override
        public void onThrowable(Throwable t) {
            t.printStackTrace();
            Assert.fail("Unexpected exception: " + t.getMessage(), t);
        }

    }

    public static class AsyncHandlerAdapter implements AsyncHandler<String> {


        @Override
        public void onThrowable(Throwable t) {
            t.printStackTrace();
            Assert.fail("Unexpected exception", t);
        }

        @Override
        public STATE onBodyPartReceived(final HttpResponseBodyPart content) throws Exception {
            return STATE.CONTINUE;
        }

        @Override
        public STATE onStatusReceived(final HttpResponseStatus responseStatus) throws Exception {
            return STATE.CONTINUE;
        }

        @Override
        public STATE onHeadersReceived(final HttpResponseHeaders headers) throws Exception {
            return STATE.CONTINUE;
        }

        @Override
        public String onCompleted() throws Exception {
            return "";
        }

    }

    public abstract AsyncHttpClient getAsyncHttpClient(AsyncHttpClientConfig config);

}
