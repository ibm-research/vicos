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

package com.ibm.vicos.client;

import com.google.inject.Inject;
import com.google.inject.name.Named;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicInteger;

import akka.actor.ActorSelection;
import akka.actor.UntypedActorWithStash;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.ibm.vicos.common.Messages.*;

/**
 * Created by bur on 25/02/16.
 */
public class ClientThrottleActor extends UntypedActorWithStash {

    private static final Logger LOG = LoggerFactory.getLogger(ClientThrottleActor.class);

    private final ActorSelection out;
    private final ActorSelection in;

    private final AtomicInteger operationInProgress = new AtomicInteger(0);
    private final int maxOperationInProgress;

    @Inject
    public ClientThrottleActor(@Named("outActorSelection") ActorSelection out,
                               @Named("inActorSelection") ActorSelection in,
                               @Named("maxOperationInProgress") Integer maxOperationInProgress) {
        this.out = checkNotNull(out, "out");
        this.in = checkNotNull(in, "in");

        checkArgument(maxOperationInProgress > 0, "maxOperationInProgress");
        this.maxOperationInProgress = maxOperationInProgress;

    }

    @Override
    public void preStart() throws Exception {
        super.preStart();
        LOG.info("Boot ClientThrottleActor: {}; maxOperationInProgress: {}", getSelf().toString(), maxOperationInProgress);
    }

    private void throttle(final Message message) {
        LOG.info("throttle: {}; operationInProgress: {}", message.getType(), operationInProgress.get());
        switch (message.getType()) {
            case INIT: {
                out.tell(message, getSender());
                break;
            }
            case INVOKE: {
                if (operationInProgress.get() >= maxOperationInProgress) {
                    LOG.info("Stashed!");
                    stash();
                } else {
                    operationInProgress.incrementAndGet();
                    out.tell(message, getSender());
                }
                break;
            }
            case REPLY: {
                in.tell(message, getSender());
                break;
            }
            case COMMIT: {
                out.tell(message, getSender());
                break;
            }
            case UPDATE_AUTH: {
                in.tell(message, getSender());
                break;
            }
            case COMMIT_AUTH: {
                operationInProgress.decrementAndGet();
                out.tell(message, getSender());
                if (operationInProgress.get() < maxOperationInProgress) {
                    LOG.info("Unstash operation :D");
                    unstash();
                }
                break;
            }
            default: {
                unhandled(message);
            }
        }
    }

    @Override
    public void onReceive(final Object message) throws Exception {
        if (message instanceof Message) {
            throttle((Message) message);
        } else {
            unhandled(message);
        }
    }
}
