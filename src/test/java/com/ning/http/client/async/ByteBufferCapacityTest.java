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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.jetty.http.HttpField;
import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.io.Content;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.util.Callback;
import org.testng.annotations.Test;

import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.HttpResponseBodyPart;
import com.ning.http.client.Response;

public abstract class ByteBufferCapacityTest extends AbstractBasicTest {
    private static final File TMP = new File(System.getProperty("java.io.tmpdir"), "ahc-tests-" + UUID.randomUUID().toString().substring(0, 8));

    private class BasicHandler extends Handler.Abstract {

        @Override
        public boolean handle(Request request, org.eclipse.jetty.server.Response response, Callback callback)
                throws Exception {

            final HttpFields requestHeaders = request.getHeaders();
            final HttpFields.Mutable responseHeaders = response.getHeaders();
            for (final HttpField field : requestHeaders) {
                responseHeaders.put("X-" + field.getName(), field.getValue());
            }

            int size = 10 * 1024;
            if (request.getLength() > 0) {
                size = (int) request.getLength();
            }
            byte[] bytes = new byte[size];
            if (bytes.length > 0) {
                try (final InputStream in = Content.Source.asInputStream(request)) {
                    final OutputStream out = Content.Sink.asOutputStream(response);
                    int read;
                    while ((read = in.read(bytes)) != -1) {
                        out.write(bytes, 0, read);
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

    @Override
    public Handler.Abstract configureHandler() throws Exception {
        return new BasicHandler();
    }

    @Test(groups = { "standalone", "default_provider" })
    public void basicByteBufferTest() throws Throwable {
        try (AsyncHttpClient client = getAsyncHttpClient(null)) {
            byte[] bytes = "RatherLargeFileRatherLargeFileRatherLargeFileRatherLargeFile".getBytes("UTF-16");
            long repeats = (1024 * 100 * 10 / bytes.length) + 1;
            File largeFile = createTempFile(bytes, (int) repeats);
            final AtomicInteger byteReceived = new AtomicInteger();

            try {
                Response response = client.preparePut(getTargetUrl()).setBody(largeFile).execute(new AsyncCompletionHandlerAdapter() {
                    @Override
                    public STATE onBodyPartReceived(final HttpResponseBodyPart content) throws Exception {
                        byteReceived.addAndGet(content.getBodyByteBuffer().capacity());
                        return super.onBodyPartReceived(content);
                    }

                }).get();

                assertNotNull(response);
                assertEquals(response.getStatusCode(), 200);
                assertEquals(byteReceived.get(), largeFile.length());
                assertEquals(response.getResponseBody().length(), largeFile.length());

            } catch (IOException ex) {
                fail("Should have timed out");
            }
        }
    }

    public String getTargetUrl() {
        return String.format("http://127.0.0.1:%d/foo/test", port1);
    }

    public static File createTempFile(byte[] pattern, int repeat) throws IOException {
        TMP.mkdirs();
        TMP.deleteOnExit();
        final File tmpFile = Files.createTempFile(TMP.toPath(), "tmpfile-", ".data").toFile();
        write(pattern, repeat, tmpFile);

        return tmpFile;
    }

    public static void write(byte[] pattern, int repeat, File file) throws IOException {
        file.deleteOnExit();
        file.getParentFile().mkdirs();
        try (FileOutputStream out = new FileOutputStream(file)) {
            for (int i = 0; i < repeat; i++) {
                out.write(pattern);
            }
        }
    }
}
