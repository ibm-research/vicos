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

package com.ibm.vicos.common.util;

import com.google.common.io.BaseEncoding;
import com.google.inject.Binder;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.name.Names;
import com.google.inject.util.Modules;

import com.ibm.vicos.client.ClientActor;
import com.ibm.vicos.client.ClientProtocol;
import com.ibm.vicos.client.ClientProtocolImpl;
import com.ibm.vicos.client.ClientThrottleActor;
import com.ibm.vicos.client.VICOSClient;
import com.ibm.vicos.common.ClientIdentifier;
import com.ibm.vicos.common.ClientLookupMap;
import com.ibm.vicos.common.DummyClientLookupMap;
import com.ibm.vicos.common.OperationProcessor;
import com.ibm.vicos.common.crypto.CryptoUtils;
import com.ibm.vicos.config.DefaultClientModule;
import com.ibm.vicos.config.GuiceInjector;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.Key;

import javax.crypto.spec.SecretKeySpec;

import akka.actor.ActorRef;
import akka.actor.ActorSelection;
import akka.actor.ActorSystem;
import akka.actor.Props;

import static com.google.common.base.Preconditions.checkState;
import static com.ibm.vicos.config.ConfigHelper.returnCryptoImplClazz;
import static com.ibm.vicos.config.ConfigHelper.returnOperationProcessorImpl;

/**
 * Created by bur on 24/03/16.
 */
public class ClientBuilder {

    private static final Logger LOG = LoggerFactory.getLogger(ClientBuilder.class);
    private static ClientBuilder ourInstance = new ClientBuilder();

    public static ClientBuilder getInstance() {
        return ourInstance;
    }


    public static VICOSClient buildFromConfig(Config userConfig) {
        final Config config = userConfig.withFallback(ConfigFactory.load()).resolve();

        String systemName = config.getString("vicos.system.name");
        ActorSystem system = ActorSystem.create(systemName);

        String serverHostname = config.getString("vicos.server.hostname");
        String serverPort = config.getString("vicos.server.port");
        String serverActorName = config.getString("vicos.server.name");
        String actorPath = "akka.tcp://" + systemName + "@" + serverHostname + ":" + serverPort + "/user/" + serverActorName;
        LOG.info("actorPath: {}", actorPath);
        ActorSelection remoteServerSelection = system.actorSelection(actorPath);
        LOG.info("Remote server: {}", remoteServerSelection);

        String opMode = config.getString("vicos.system.operation.mode");
        Class<? extends OperationProcessor> operationProcessorClass = returnOperationProcessorImpl(opMode);
        LOG.info("Operation processor: {}", operationProcessorClass.getSimpleName());

        boolean opRetryOnAbort = config.getBoolean("vicos.system.operation.retry-on-abort");
        LOG.info("Operation retry on abort: {}", opRetryOnAbort);

        String signatures = config.getString("vicos.system.signatures.type").toUpperCase();
        checkState(signatures.equals("HMAC"), "Only HMAC supported right now!");
        Class<? extends CryptoUtils> cryptoUtilClass = returnCryptoImplClazz(signatures);
        LOG.info("Crypto: {}", cryptoUtilClass.getSimpleName());


        // load shared key for hmac signatures
        byte[] raw = BaseEncoding.base64().decode(config.getString("vicos.system.signatures.hmac.key"));
        final Key sharedKey = new SecretKeySpec(raw, "AES");

        // TODO create client

        String clientId = config.getString("vicos.client.identifier");
        ClientIdentifier clientIdentifier = ClientIdentifier.builder().setClientId(clientId).build();

        final DummyClientLookupMap clientLookupMap = new DummyClientLookupMap(clientIdentifier);
        LOG.info("ClientId: {}", clientIdentifier.getClientId());

        final int maxOperationInProgress = config.getInt("vicos.system.flowcontrol.max-operation-in-progress");

        if (maxOperationInProgress > 0) {
            final ActorRef throttler = system.actorOf(Props.create(GuiceInjector.class,
                    Guice.createInjector(binder -> {
                        binder.bind(ActorSelection.class)
                                .annotatedWith(Names.named("outActorSelection"))
                                .toInstance(remoteServerSelection);
                        binder.bind(ActorSelection.class)
                                .annotatedWith(Names.named("inActorSelection"))
                                .toInstance(system.actorSelection("/user/" + clientIdentifier.getClientId()));
                        binder.bind(Integer.class)
                                .annotatedWith(Names.named("maxOperationInProgress"))
                                .toInstance(maxOperationInProgress);
                    }),
                    ClientThrottleActor.class), "throttler-" + clientIdentifier.getClientId());
        }
        final ActorSelection throttlerPath = system.actorSelection("/user/throttler-" + clientIdentifier.getClientId());

        final Module overrides = Modules.override(new DefaultClientModule()).with(new Module() {
            @Override
            public void configure(Binder binder) {

                if (maxOperationInProgress > 0) {
                    // todo
                    binder.bind(ActorSelection.class)
                            .annotatedWith(Names.named("remoteServerSelection"))
                            .toInstance(throttlerPath);
                } else {
                    binder.bind(ActorSelection.class)
                            .annotatedWith(Names.named("remoteServerSelection"))
                            .toInstance(remoteServerSelection);
                }

                // enable commutative operations
                binder.bind(OperationProcessor.class).to(operationProcessorClass);

                binder.bind(ClientIdentifier.class)
                        .annotatedWith(Names.named("clientIdentifier"))
                        .toInstance(clientIdentifier);

                binder.bind(Boolean.class)
                        .annotatedWith(Names.named("retryOnAbort"))
                        .toInstance(opRetryOnAbort);

                if (signatures.equals("HMAC")) {
                    binder.bind(Key.class)
                            .annotatedWith(Names.named("sharedKey"))
                            .toInstance(sharedKey);
                }
                binder.bind(CryptoUtils.class).to(cryptoUtilClass);

                binder.bind(ClientLookupMap.class)
                        .annotatedWith(Names.named("clientLookupMap"))
                        .toInstance(clientLookupMap);

                binder.bind(Integer.class)
                        .annotatedWith(Names.named("maxOperationInProgress"))
                        .toInstance(maxOperationInProgress);

                binder.bind(ClientProtocol.class).to(ClientProtocolImpl.class);
            }
        });

        Injector injector = Guice.createInjector(overrides);

        final ActorRef clientRef = system.actorOf(
                Props.create(GuiceInjector.class, injector, ClientActor.class),
                clientIdentifier.getClientId()
        );

        return injector.getInstance(VICOSClient.VICOSClientKVSAPIFactory.class)
                .create(clientRef, clientIdentifier);

    }
}
