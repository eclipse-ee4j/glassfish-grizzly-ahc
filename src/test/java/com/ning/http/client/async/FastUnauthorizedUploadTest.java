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

import static java.nio.charset.StandardCharsets.*;

import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.io.Content;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.util.Callback;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.AsyncHttpClient.BoundRequestBuilder;
import com.ning.http.client.Response;
import com.ning.http.client.multipart.FilePart;

import java.io.File;

public abstract class FastUnauthorizedUploadTest extends AbstractBasicTest {

    @Override
    public Handler.Abstract configureHandler() throws Exception {
        return new Handler.Abstract() {

            @Override
            public boolean handle(Request request, org.eclipse.jetty.server.Response response, Callback callback)
                    throws Exception {

                response.setStatus(HttpStatus.UNAUTHORIZED_401);
                Content.Sink.asOutputStream(response).flush();
                Content.Sink.asOutputStream(response).close();

                callback.succeeded();
                return true;
            }
        };
    }

    @Test(groups = { "standalone", "default_provider" }, enabled = true)
    public void testUnauthorizedWhileUploading() throws Exception {
        byte[] bytes = "RatherLargeFileRatherLargeFileRatherLargeFileRatherLargeFile".getBytes(UTF_16);
        long repeats = (1024 * 1024 / bytes.length) + 1;
        File largeFile = FilePartLargeFileTest.createTempFile(bytes, (int) repeats);

        try (AsyncHttpClient client = getAsyncHttpClient(null)) {
            BoundRequestBuilder rb = client.preparePut(getTargetUrl());

            rb.addBodyPart(new FilePart("test", largeFile, "application/octet-stream", UTF_8));

            Response response = rb.execute().get();
            Assert.assertEquals(401, response.getStatusCode());
        }
    }
}
