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

package com.ning.http.util;

import com.ning.http.client.Param;
import com.ning.http.client.uri.Uri;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.List;

public class UTF8UrlCodecTest {

    @Test(groups = "fast")
    public void testBasics() {
        Assert.assertEquals(UTF8UrlEncoder.encodeQueryElement("foobar"), "foobar");
        Assert.assertEquals(UTF8UrlEncoder.encodeQueryElement("a&b"), "a%26b");
        Assert.assertEquals(UTF8UrlEncoder.encodeQueryElement("a+b"), "a%2Bb");
    }

    @Test(groups = "fast")
    public void testNonBmp() {
        // Plane 1
        Assert.assertEquals(UTF8UrlEncoder.encodeQueryElement("\uD83D\uDCA9"), "%F0%9F%92%A9");
        // Plane 2
        Assert.assertEquals(UTF8UrlEncoder.encodeQueryElement("\ud84c\uddc8 \ud84f\udfef"), "%F0%A3%87%88%20%F0%A3%BF%AF");
        // Plane 15
        Assert.assertEquals(UTF8UrlEncoder.encodeQueryElement("\udb80\udc01"), "%F3%B0%80%81");
    }

    @Test(groups = "fast")
    public void testDecodeBasics() {
        Assert.assertEquals(UTF8UrlDecoder.decode("foobar").toString(), "foobar");
        Assert.assertEquals(UTF8UrlDecoder.decode("a&b").toString(), "a&b");
        Assert.assertEquals(UTF8UrlDecoder.decode("a+b").toString(), "a b");

        Assert.assertEquals(UTF8UrlDecoder.decode("+").toString(), " ");
        Assert.assertEquals(UTF8UrlDecoder.decode("%20").toString(), " ");
        Assert.assertEquals(UTF8UrlDecoder.decode("%25").toString(), "%");

        Assert.assertEquals(UTF8UrlDecoder.decode("+%20x").toString(), "  x");
    }

    @Test(groups = "fast")
    public void testDecodeTooShort() {
        try {
            UTF8UrlDecoder.decode("%2");
            Assert.assertTrue(false, "No exception thrown on illegal encoding length");
        } catch (IllegalArgumentException ex) {
            Assert.assertEquals(ex.getMessage(), "UTF8UrlDecoder: Incomplete trailing escape (%) pattern");
        } catch (StringIndexOutOfBoundsException ex) {
            Assert.assertTrue(false, "String Index Out of Bound thrown, but should be IllegalArgument");
        }
    }

    @Test(groups = "fast")
    public void testUriEncoder() {
        // This is not affected by URI encoding. The result is the same whether disableUrlEncoding is true or false.
        final Uri uriWithoutQuery = Uri.create("http://example.com:8080/path");
        final Uri uriWithQuery = Uri.create("https://example.com/path?query1=val1&query2=val2");
        final List<Param> additionalQueryParams =
                Arrays.asList(new Param("addQuery1", "addVal1"), new Param("addQuery2", "addVal2"));

        Assert.assertEquals(UriEncoder.uriEncoder(true).encode(uriWithoutQuery, null).toString(),
                            "http://example.com:8080/path");
        Assert.assertEquals(UriEncoder.uriEncoder(true).encode(uriWithQuery, null).toString(),
                            "https://example.com/path?query1=val1&query2=val2");
        Assert.assertEquals(UriEncoder.uriEncoder(true).encode(uriWithoutQuery, additionalQueryParams).toString(),
                            "http://example.com:8080/path?addQuery1=addVal1&addQuery2=addVal2");
        Assert.assertEquals(UriEncoder.uriEncoder(true).encode(uriWithQuery, additionalQueryParams).toString(),
                            "https://example.com/path?query1=val1&query2=val2&addQuery1=addVal1&addQuery2=addVal2");

        // So we expect the same result whether it's UriEncoder.RAW or UriEncoder.FIXING.
        Assert.assertEquals(UriEncoder.uriEncoder(true).encode(uriWithoutQuery, null),
                            UriEncoder.uriEncoder(false).encode(uriWithoutQuery, null));
        Assert.assertEquals(UriEncoder.uriEncoder(true).encode(uriWithQuery, null),
                            UriEncoder.uriEncoder(false).encode(uriWithQuery, null));
        Assert.assertEquals(UriEncoder.uriEncoder(true).encode(uriWithoutQuery, additionalQueryParams),
                            UriEncoder.uriEncoder(false).encode(uriWithoutQuery, additionalQueryParams));
        Assert.assertEquals(UriEncoder.uriEncoder(true).encode(uriWithQuery, additionalQueryParams),
                            UriEncoder.uriEncoder(false).encode(uriWithQuery, additionalQueryParams));
    }
}
