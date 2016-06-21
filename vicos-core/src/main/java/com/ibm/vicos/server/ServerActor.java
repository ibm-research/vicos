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

import com.google.common.collect.Maps;
import com.google.inject.Inject;

import com.ibm.vicos.common.Messages;
import com.ibm.vicos.common.Messages.UpdateAuth;
import com.ibm.vicos.common.logging.Marker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

import akka.actor.ActorRef;
import akka.actor.UntypedActorWithUnboundedStash;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.ibm.vicos.common.Messages.Message;
import static com.ibm.vicos.common.Messages.Message.Type.REPLY;
import static com.ibm.vicos.common.Messages.Message.Type.UPDATE_AUTH;

/**
 * Created by bur on 22/09/15.
 */
public class  ServerActor extends UntypedActorWithUnboundedStash {

    private static final Logger LOG = LoggerFactory.getLogger(ServerActor.class);
    private final ServerProtocol serverProtocol;
    private Map<String, ActorRef> clientReferences = Maps.newHashMap();

    @Inject
    public ServerActor(ServerProtocol serverProtocol) {
        this.serverProtocol = checkNotNull(serverProtocol, "serverProtocol");
    }

    @Override
    public void preStart() throws Exception {
        super.preStart();
        LOG.info("Boot actor: {}", getSelf().toString());
    }

    private void processRequest(final Message message) {
        if(LOG.isTraceEnabled(Marker.MESSAGE_SIZE)) {
            LOG.trace(Marker.MESSAGE_SIZE, "{} {}", message.getType(), message.toByteArray().length);
        }

        String source = message.getSource();
        clientReferences.put(source, getSender());
        LOG.debug("Received request {} message from {}", message.getType(), source);

        switch (message.getType()) {
            case INIT: {
                cleanup();
                serverProtocol.handleInit(message.getInit());
                break;
            }
            case INVOKE: {
                if(serverProtocol.readyForNewInvocation()) {
                    sendReply(serverProtocol.handleInvoke(message.getInvoke()), source);
                    unstash();
                } else {
                    LOG.debug("Stash invoke message {}", source);
                    stash();
                }
                break;
            }
            case COMMIT: {
                serverProtocol.handleCommit(message.getCommit());
                sendUpdateAuth(serverProtocol.uponNextCommittedOperationAvailable());
                break;
            }
            case COMMIT_AUTH: {
                serverProtocol.handleCommitAuth(message.getCommitAuth());
                sendUpdateAuth(serverProtocol.uponNextCommittedOperationAvailable());
                if(serverProtocol.readyForNewInvocation()) {
                    unstash();
                }
                break;
            }
            default: {
                unhandled(message);
            }
        }
    }

    private void sendReply(Messages.Reply reply, String destination) {
        send(Message.newBuilder()
                .setType(REPLY)
                .setReply(reply)
                .setDestination(destination)
                .build());
    }

    private void sendUpdateAuth(UpdateAuth updateAuth) {
        if (updateAuth == null)
            return;

        String destination = updateAuth.getOperation().getClientId();

        send(Message.newBuilder()
                .setType(UPDATE_AUTH)
                .setUpdateAuth(updateAuth)
                .setDestination(destination)
                .build());
    }

    @Override
    public void onReceive(final Object message) throws Exception {
        if (message instanceof Message) {
            processRequest((Message) message);
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

    private void cleanup() {
        clientReferences.clear();
    }

    private void send(Message message) {
        if(LOG.isTraceEnabled(Marker.MESSAGE_SIZE)) {
            LOG.trace(Marker.MESSAGE_SIZE, "{} {}", message.getType(), message.toByteArray().length);
        }

        ActorRef destination = clientReferences.get(message.getDestination());
        LOG.debug("Send {} message to {}", message.getType(), destination);
        destination.tell(message, self());
    }
}
