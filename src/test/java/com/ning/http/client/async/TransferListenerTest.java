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
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;

import org.eclipse.jetty.http.HttpField;
import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.io.Content;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.util.Callback;
import org.testng.annotations.Test;

import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.FluentCaseInsensitiveStringsMap;
import com.ning.http.client.Response;
import com.ning.http.client.generators.FileBodyGenerator;
import com.ning.http.client.listener.TransferCompletionHandler;
import com.ning.http.client.listener.TransferListener;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public abstract class TransferListenerTest extends AbstractBasicTest {
    private static final File TMP = new File(System.getProperty("java.io.tmpdir"), "ahc-tests-" + UUID.randomUUID().toString().substring(0, 8));

    private class BasicHandler extends Handler.Abstract {

        @Override
        public boolean handle(Request request, org.eclipse.jetty.server.Response response, Callback callback)
                throws Exception {
            final HttpFields requestHeaders = request.getHeaders();
            final HttpFields.Mutable responseHeaders = response.getHeaders();
            for(final HttpField field : requestHeaders) {
                responseHeaders.put("X-" + field.getName(), field.getValue());
            }

            int size = 10 * 1024;
            if (request.getLength() > 0) {
                size = (int) request.getLength();
            }
            byte[] bytes = new byte[8092];
            int count;
            int total = 0;
            try (final InputStream in = Content.Source.asInputStream(request)) {
                while ((count = in.read(bytes)) != -1 && total < size) {
                    total += count;
                    Content.Sink.asOutputStream(response).write(bytes, 0, count);
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
    public void basicGetTest() throws Throwable {
        final AtomicReference<Throwable> throwable = new AtomicReference<>();
        final AtomicReference<FluentCaseInsensitiveStringsMap> hSent = new AtomicReference<>();
        final AtomicReference<FluentCaseInsensitiveStringsMap> hRead = new AtomicReference<>();
        final AtomicReference<byte[]> br = new AtomicReference<>();
        final AtomicBoolean bs = new AtomicBoolean(false);
        final AtomicBoolean completed = new AtomicBoolean(false);

        TransferCompletionHandler tl = new TransferCompletionHandler();
        tl.addTransferListener(new TransferListener() {

            public void onRequestHeadersSent(FluentCaseInsensitiveStringsMap headers) {
                hSent.set(headers);
            }

            public void onResponseHeadersReceived(FluentCaseInsensitiveStringsMap headers) {
                hRead.set(headers);
            }

            public void onBytesReceived(byte[] b) {
                br.set(b);
            }

            public void onBytesSent(long amount, long current, long total) {
                bs.compareAndSet(false, true);
            }

            public void onRequestResponseCompleted() {
                completed.set(true);
            }

            public void onThrowable(Throwable t) {
                throwable.set(t);
            }
        });

        try (AsyncHttpClient client = getAsyncHttpClient(null)) {
            Response response = client.prepareGet(getTargetUrl()).execute(tl).get();

            assertNotNull(response);
            assertEquals(response.getStatusCode(), 200);
            assertNotNull(hRead.get());
            assertNotNull(hSent.get());
            assertNull(br.get());
            assertFalse(bs.get());
            assertNull(throwable.get());
        }
    }

    @Test(groups = { "standalone", "default_provider" })
    public void basicPutTest() throws Throwable {

        final AtomicReference<Throwable> throwable = new AtomicReference<>();
        final AtomicReference<FluentCaseInsensitiveStringsMap> hSent = new AtomicReference<>();
        final AtomicReference<FluentCaseInsensitiveStringsMap> hRead = new AtomicReference<>();
        final AtomicLong bbReceivedLenght = new AtomicLong(0);
        final AtomicLong bbSentLenght = new AtomicLong(0);

        final AtomicBoolean completed = new AtomicBoolean(false);

        byte[] bytes = "RatherLargeFileRatherLargeFileRatherLargeFileRatherLargeFile".getBytes("UTF-16");
        long repeats = (1024 * 100 * 10 / bytes.length) + 1;
        File largeFile = createTempFile(bytes, (int) repeats);

        TransferCompletionHandler tl = new TransferCompletionHandler();
        tl.addTransferListener(new TransferListener() {

            public void onRequestHeadersSent(FluentCaseInsensitiveStringsMap headers) {
                hSent.set(headers);
            }

            public void onResponseHeadersReceived(FluentCaseInsensitiveStringsMap headers) {
                hRead.set(headers);
            }

            public void onBytesReceived(byte[] b) {
                bbReceivedLenght.addAndGet(b.length);
            }

            public void onBytesSent(long amount, long current, long total) {
                bbSentLenght.addAndGet(amount);
            }

            public void onRequestResponseCompleted() {
                completed.set(true);
            }

            public void onThrowable(Throwable t) {
                throwable.set(t);
            }
        });

        try (AsyncHttpClient client = getAsyncHttpClient(null)) {
            Response response = client.preparePut(getTargetUrl()).setBody(largeFile).execute(tl).get();

            assertNotNull(response);
            assertEquals(response.getStatusCode(), 200);
            assertNotNull(hRead.get());
            assertNotNull(hSent.get());
            assertEquals(bbReceivedLenght.get(), largeFile.length());
            assertEquals(bbSentLenght.get(), largeFile.length());
        }
    }

    @Test(groups = { "standalone", "default_provider" })
    public void basicPutBodyTest() throws Throwable {

        final AtomicReference<Throwable> throwable = new AtomicReference<>();
        final AtomicReference<FluentCaseInsensitiveStringsMap> hSent = new AtomicReference<>();
        final AtomicReference<FluentCaseInsensitiveStringsMap> hRead = new AtomicReference<>();
        final AtomicLong bbReceivedLenght = new AtomicLong(0);
        final AtomicLong bbSentLenght = new AtomicLong(0);

        final AtomicBoolean completed = new AtomicBoolean(false);

        byte[] bytes = "RatherLargeFileRatherLargeFileRatherLargeFileRatherLargeFile".getBytes("UTF-16");
        long repeats = (1024 * 100 * 10 / bytes.length) + 1;
        File largeFile = createTempFile(bytes, (int) repeats);

        TransferCompletionHandler tl = new TransferCompletionHandler();
        tl.addTransferListener(new TransferListener() {

            public void onRequestHeadersSent(FluentCaseInsensitiveStringsMap headers) {
                hSent.set(headers);
            }

            public void onResponseHeadersReceived(FluentCaseInsensitiveStringsMap headers) {
                hRead.set(headers);
            }

            public void onBytesReceived(byte[] b) {
                bbReceivedLenght.addAndGet(b.length);
            }

            public void onBytesSent(long amount, long current, long total) {
                bbSentLenght.addAndGet(amount);
            }

            public void onRequestResponseCompleted() {
                completed.set(true);
            }

            public void onThrowable(Throwable t) {
                throwable.set(t);
            }
        });

        try (AsyncHttpClient client = getAsyncHttpClient(null)) {
            Response response = client.preparePut(getTargetUrl()).setBody(new FileBodyGenerator(largeFile)).execute(tl).get();

            assertNotNull(response);
            assertEquals(response.getStatusCode(), 200);
            assertNotNull(hRead.get());
            assertNotNull(hSent.get());
            assertEquals(bbReceivedLenght.get(), largeFile.length());
            assertEquals(bbSentLenght.get(), largeFile.length());
        }
    }

    public String getTargetUrl() {
        return String.format("http://127.0.0.1:%d/foo/test", port1);
    }

    public static File createTempFile(byte[] pattern, int repeat) throws IOException {
        TMP.mkdirs();
        TMP.deleteOnExit();
        File tmpFile = File.createTempFile("tmpfile-", ".data", TMP);
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
