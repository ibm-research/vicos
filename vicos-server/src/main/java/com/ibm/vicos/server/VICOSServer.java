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
import com.google.inject.Binder;
import com.google.inject.Guice;
import com.google.inject.Module;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Names;
import com.google.inject.util.Modules;

import com.ibm.vicos.common.ADS.Authenticator;
import com.ibm.vicos.common.ActorSystemInstance;
import com.ibm.vicos.common.Operations.Operation;
import com.ibm.vicos.config.DefaultServerModule;
import com.ibm.vicos.config.GuiceInjector;
import com.ibm.vicos.kvs.KVSState;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.NavigableMap;
import java.util.concurrent.atomic.AtomicLong;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;

@Service
public class VICOSServer {

    private static final long ACTOR_SHUTDOWN_TIMEOUT = 30000;
    private KVSState state;
    private AtomicLong invokedSequenceNumber;
    private AtomicLong executedSequenceNumber;
    private NavigableMap<Long, Operation> operations;
    private NavigableMap<Long, Authenticator> authenticators;
    private ActorRef serverActorRef;
    private ActorSystem system;

    protected void startUp() {

        invokedSequenceNumber = new AtomicLong(-1);
        executedSequenceNumber = new AtomicLong(-1);
        operations = Maps.newTreeMap();
        authenticators = Maps.newTreeMap();
        state = new KVSState(Maps.newTreeMap());

        final Config config = ConfigFactory.load();

        int pendingListMaxLength = config.getInt("vicos.system.flowcontrol.pending-list-max-length");

        final Module overrides = Modules.override(new DefaultServerModule()).with(new Module() {
            @Override
            public void configure(Binder binder) {
                binder.bind(AtomicLong.class)
                        .annotatedWith(Names.named("lastInvokedSeqNo"))
                        .toInstance(invokedSequenceNumber);

                binder.bind(AtomicLong.class)
                        .annotatedWith(Names.named("lastAppliedSeqNo"))
                        .toInstance(executedSequenceNumber);

                binder.bind(com.ibm.vicos.common.State.class)
                        .annotatedWith(Names.named(""))
                        .toInstance(state);

                binder.bind(new TypeLiteral<NavigableMap<Long, Authenticator>>() {
                })
                        .toInstance(authenticators);

                binder.bind(new TypeLiteral<NavigableMap<Long, Operation>>() {
                })
                        .toInstance(operations);
                binder.bind(Integer.class)
                        .annotatedWith(Names.named("pendingListMaxLength"))
                        .toInstance(pendingListMaxLength);
            }
        });

        system = ActorSystemInstance.getInstance(config);

        serverActorRef = system.actorOf(
                Props.create(GuiceInjector.class, Guice.createInjector(overrides), ServerActor.class),
                config.getString("vicos.server.name")
        );
    }

    protected void shutDown() {
        system.stop(serverActorRef);
        long startTime = System.currentTimeMillis();
        long timeoutInMillis = ACTOR_SHUTDOWN_TIMEOUT;
        while ((System.currentTimeMillis() < startTime + timeoutInMillis) && !serverActorRef.isTerminated()) {
        }
        serverActorRef = null;
        system.shutdown();
    }

    public String getStatus() {
        return "b: " + executedSequenceNumber.get() + " t: " + invokedSequenceNumber.get();
    }

    public Map<Long, Operation> getOperations() {
        return operations;
    }

    public Map<Long, Authenticator> getAuthenticator() {
        return authenticators;
    }

    public KVSState getState() {
        return state;
    }
}
