/*
 * Copyright 2025 Contributors to the Eclipse Foundation.
 * Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ning.http.client.ws;

import org.eclipse.jetty.websocket.api.Callback;
import org.eclipse.jetty.websocket.api.Session;

import java.nio.ByteBuffer;
import java.time.Duration;

public class EchoByteWebSocket implements Session.Listener {

    private Session session;

    @Override
    public void onWebSocketOpen(Session session) {
        this.session = session;
        this.session.setIdleTimeout(Duration.ofSeconds(10));
        this.session.demand();
    }

    @Override
    public void onWebSocketBinary(ByteBuffer payload, Callback callback) {
        if (isNotConnected()) {
            callback.succeed();
            session.demand();
            return;
        }

        session.sendBinary(payload, Callback.from(() -> {
            callback.succeed();
            session.demand();
        }, Throwable::printStackTrace));
    }

    private boolean isNotConnected() {
        return session == null || !session.isOpen();
    }
}
