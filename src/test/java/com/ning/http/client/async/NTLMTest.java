/*
 * Copyright (c) 2025 Contributors to the Eclipse Foundation.
 * Copyright (c) 2017, 2018 Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2014 AsyncHttpClient Project. All rights reserved.
 *
 * This program is licensed to you under the Apache License Version 2.0,
 * and you may not use this file except in compliance with the Apache License Version 2.0.
 * You may obtain a copy of the Apache License Version 2.0 at
 *     http://www.apache.org/licenses/LICENSE-2.0.
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the Apache License Version 2.0 is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Apache License Version 2.0 for the specific language governing permissions and limitations there under.
 */

package com.ning.http.client.async;

import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.io.Content;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.util.Callback;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.AsyncHttpClientConfig;
import com.ning.http.client.Realm;
import com.ning.http.client.Realm.AuthScheme;
import com.ning.http.client.Realm.RealmBuilder;
import com.ning.http.client.Request;
import com.ning.http.client.RequestBuilder;
import com.ning.http.client.Response;

import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public abstract class NTLMTest extends AbstractBasicTest {

    public static class NTLMHandler extends Handler.Abstract {

        @Override
        public boolean handle(org.eclipse.jetty.server.Request request, org.eclipse.jetty.server.Response response,
                              Callback callback) throws Exception {
            final HttpFields requestHeaders = request.getHeaders();
            final HttpFields.Mutable responseHeaders = response.getHeaders();

            final String authorization = requestHeaders.get(HttpHeader.AUTHORIZATION);
            if (authorization == null) {
                response.setStatus(HttpStatus.UNAUTHORIZED_401);
                responseHeaders.put(HttpHeader.WWW_AUTHENTICATE, "NTLM");

            } else if (authorization.equals("NTLM TlRMTVNTUAABAAAAAYIIogAAAAAoAAAAAAAAACgAAAAFASgKAAAADw==")) {
                response.setStatus(HttpStatus.UNAUTHORIZED_401);
                responseHeaders.put(HttpHeader.WWW_AUTHENTICATE, "NTLM TlRMTVNTUAACAAAAAAAAACgAAAABggAAU3J2Tm9uY2UAAAAAAAAAAA==");

            } else if (authorization
                    .equals("NTLM TlRMTVNTUAADAAAAGAAYAEgAAAAYABgAYAAAABQAFAB4AAAADAAMAIwAAAASABIAmAAAAAAAAACqAAAAAYIAAgUBKAoAAAAPrYfKbe/jRoW5xDxHeoxC1gBmfWiS5+iX4OAN4xBKG/IFPwfH3agtPEia6YnhsADTVQBSAFMAQQAtAE0ASQBOAE8AUgBaAGEAcABoAG8AZABMAGkAZwBoAHQAQwBpAHQAeQA=")) {
                response.setStatus(HttpStatus.OK_200);
            } else {
                response.setStatus(HttpStatus.UNAUTHORIZED_401);
            }
            responseHeaders.put(HttpHeader.CONTENT_LENGTH, 0L);
            Content.Sink.asOutputStream(response).flush();
            Content.Sink.asOutputStream(response).close();
            callback.succeeded();
            return true;
        }
    }

    @Override
    public Handler.Abstract configureHandler() throws Exception {
        return new NTLMHandler();
    }

    private RealmBuilder realmBuilderBase() {
        return new Realm.RealmBuilder()//
                .setScheme(AuthScheme.NTLM)//
                .setNtlmDomain("Ursa-Minor")//
                .setNtlmHost("LightCity")//
                .setPrincipal("Zaphod")//
                .setPassword("Beeblebrox");
    }

    private void ntlmAuthTest(RealmBuilder realmBuilder) throws IOException, InterruptedException, ExecutionException {

        AsyncHttpClientConfig config = new AsyncHttpClientConfig.Builder().setRealm(realmBuilder.build()).build();

        try (AsyncHttpClient client = getAsyncHttpClient(config)) {
            Request request = new RequestBuilder("GET").setUrl(getTargetUrl()).build();
            Future<Response> responseFuture = client.executeRequest(request);
            int status = responseFuture.get().getStatusCode();
            Assert.assertEquals(status, 200);
        }
    }

    @Test
    public void lazyNTLMAuthTest() throws IOException, InterruptedException, ExecutionException {
        ntlmAuthTest(realmBuilderBase());
    }

    @Test
    public void preemptiveNTLMAuthTest() throws IOException, InterruptedException, ExecutionException {
        ntlmAuthTest(realmBuilderBase().setUsePreemptiveAuth(true));
    }
}
