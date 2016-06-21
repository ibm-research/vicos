/*
 * Copyright IBM Corp. 2016 All Rights Reserved.
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
 */

package com.ibm.vicos.server;

import com.google.inject.Inject;
import com.google.inject.name.Named;

import com.ibm.vicos.common.Messages;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.actor.ActorSelection;
import akka.actor.UntypedActor;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Created by bur on 25/02/16.
 */
public class RouterActor extends UntypedActor {

    private static final Logger LOG = LoggerFactory.getLogger(RouterActor.class);

    private ActorSelection server;
    private ActorSelection throttler;

    @Inject
    public RouterActor(@Named("serverActorSelection") ActorSelection server,
                       @Named("throttlerActorSelection") ActorSelection throttler) {
        this.server = checkNotNull(server, "server");
        this.throttler = checkNotNull(throttler, "throttler");
    }

    @Override
    public void preStart() throws Exception {
        super.preStart();
        LOG.info("Boot RouterActor: {}", getSelf().toString());
    }

    private void route(final Messages.Message message) {
        switch (message.getType()) {
            case INVOKE: {
                throttler.tell(message, getSender());
                break;
            }
            default: {
                server.tell(message, getSender());
            }
        }
    }

    @Override
    public void onReceive(final Object message) throws Exception {
        if (message instanceof Messages.Message) {
            route((Messages.Message) message);
        } else {
            unhandled(message);
        }
    }

    @Override
    public void unhandled(final Object message) {
        LOG.warn("Received unknown message; Message dropped");
        LOG.trace("Message: ", message);
        super.unhandled(message);
    }
}
