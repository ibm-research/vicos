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

package com.ibm.vicos;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;

import com.ibm.vicos.common.ADS.Authenticator;
import com.ibm.vicos.common.ClientIdentifier;
import com.ibm.vicos.common.Messages;
import com.ibm.vicos.common.Operations;
import com.ibm.vicos.common.Operations.Operation;
import com.ibm.vicos.common.crypto.RSACryptoUtilsImpl;
import com.ibm.vicos.kvs.KVSCompatibleOperationProcessor;
import com.ibm.vicos.kvs.KVSOperationProcessor;
import com.ibm.vicos.kvs.KVSState;
import com.ibm.vicos.server.ServerProtocol;
import com.ibm.vicos.server.ServerProtocolImpl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.Test;

import java.security.KeyPair;
import java.util.NavigableMap;
import java.util.concurrent.atomic.AtomicLong;

import static com.ibm.vicos.common.Operations.OpCode.GET;
import static org.testng.Assert.assertTrue;

/**
 * Created by bur on 24/09/15.
 */
public class ServerProtocolTest {

    private static final Logger LOG = LoggerFactory.getLogger(ServerProtocolTest.class);
    private static final Integer MAX_PENDING_LIST = 128;
    private ClientIdentifier clientIdentifier;
    private RSACryptoUtilsImpl cryptoUtils;
    private KVSState state;
    private ServerProtocol serverProtocol;
    private NavigableMap<Long, String> hashChain = Maps.newTreeMap();
    private NavigableMap<Long, Operation> operations = Maps.newTreeMap();
    private NavigableMap<Long, Authenticator> authenticators = Maps.newTreeMap();

    @Test
    public void testCreateServer() throws Exception {

        state = new KVSState(Maps.newTreeMap());
        cryptoUtils = new RSACryptoUtilsImpl();

        KeyPair keyPair = cryptoUtils.generateKeyPair();
        clientIdentifier = ClientIdentifier.builder()
                .setClientId("TestClient")
                .setPrivateKey(keyPair.getPrivate())
                .setPublicKey(keyPair.getPublic())
                .build();

        KVSOperationProcessor operationProcessor = new KVSCompatibleOperationProcessor(cryptoUtils);

        serverProtocol = new ServerProtocolImpl(new AtomicLong(0), new AtomicLong(0), state, operationProcessor, operations, authenticators, MAX_PENDING_LIST);
    }

    @Test(dependsOnMethods = {"testCreateServer"})
    public void testHandleInit() throws Exception {

        final long sequenceNumber = 1;
        final Operations.Status status = Operations.Status.SUCCESS;
        final String clientId = clientIdentifier.getClientId();

        Operation op = Operation.newBuilder().setOpCode(GET)
                .setKey("Init")
                .build();

        final String uuid = cryptoUtils.generateOperationUUID(op, clientId);

        final String signature = cryptoUtils.sign(ImmutableList.of("COMMIT",
                        uuid,
                        clientId,
                        String.valueOf(sequenceNumber),
                        status.toString()),
                clientIdentifier
        );

        op = Operation.newBuilder().mergeFrom(op)
                .setSequenceNumber(sequenceNumber)
                .setClientId(clientId)
                .setStatus(status)
                .setSignature(signature)
                .build();

        assertTrue(cryptoUtils.extendHashChain(op, hashChain), "ExtendHashChain");


        Authenticator authenticator = Authenticator.newBuilder()
                .setValue(cryptoUtils.hash(state.items()))
                .setClientId(clientId)
                .setSequenceNumber(sequenceNumber)
                .build();

        final String authenticatorSignature = cryptoUtils.sign(
                ImmutableList.of("AUTH",
                        uuid,
                        String.valueOf(sequenceNumber),
                        authenticator.getValue()),
                clientIdentifier
        );

        authenticator = Authenticator.newBuilder()
                .mergeFrom(authenticator)
                .setSignature(authenticatorSignature).build();

        LOG.debug("Auth: {}", authenticator.getValue());

        Messages.Init initMsg = Messages.Init.newBuilder()
                .setOperation(op)
                .setAuthenticator(authenticator)
                .build();

        serverProtocol.handleInit(initMsg);
    }

    @Test
    public void testHandleInvoke() throws Exception {

    }

    @Test
    public void testHandleCommit() throws Exception {

    }

    @Test
    public void testUponNextCommittedOperationAvailable() throws Exception {

    }

    @Test
    public void testHandleCommitAuth() throws Exception {

    }


}
