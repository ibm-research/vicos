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
import com.google.protobuf.TextFormat;

import com.ibm.vicos.client.ClientProtocolImpl;
import com.ibm.vicos.common.ClientIdentifier;
import com.ibm.vicos.common.ClientLookupMap;
import com.ibm.vicos.common.Messages;
import com.ibm.vicos.common.Operations;
import com.ibm.vicos.common.Operations.Operation;
import com.ibm.vicos.common.crypto.CryptoUtils;
import com.ibm.vicos.common.crypto.HMacCryptoUtilsImpl;
import com.ibm.vicos.kvs.KVSCompatibleOperationProcessor;
import com.ibm.vicos.kvs.KVSOperationProcessor;
import com.ibm.vicos.kvs.KVSState;
import com.ibm.vicos.server.ServerProtocolImpl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.security.KeyPair;
import java.util.NavigableMap;
import java.util.concurrent.atomic.AtomicLong;

import javax.crypto.spec.SecretKeySpec;

import static com.ibm.vicos.common.ADS.*;
import static com.ibm.vicos.common.Operations.OpCode.DELETE;
import static com.ibm.vicos.common.Operations.OpCode.GET;
import static com.ibm.vicos.common.Operations.OpCode.LIST;
import static com.ibm.vicos.common.Operations.OpCode.PUT;
import static com.ibm.vicos.common.util.Utils.base64decoding;
import static org.testng.Assert.assertTrue;

/**
 * Created by bur on 24/09/15.
 */
public class IntegrationTest {

    private static final Logger LOG = LoggerFactory.getLogger(IntegrationTest.class);
    private static final Integer MAX_PENDING_LIST = 128;
    private final CryptoUtils cryptoUtils = new HMacCryptoUtilsImpl(new SecretKeySpec(base64decoding("GF2sVH7yEpPasdfDgenM/hDhux3dxuZb7sgl6NAYVTM="), "AES"));
    private ClientIdentifier clientIdentifier_AAA;
    private ClientIdentifier clientIdentifier_BBB;
    private ClientIdentifier clientIdentifier_CCC;
    private KVSState state = new KVSState(Maps.newTreeMap());
    private ServerProtocolImpl serverProtocolImpl;
    private NavigableMap<Long, String> hashChain = Maps.newTreeMap();
    private NavigableMap<Long, Operation> operations = Maps.newTreeMap();
    private NavigableMap<Long, Authenticator> authenticators = Maps.newTreeMap();
    private ClientLookupMap clientLookupMap;
    private KVSOperationProcessor operationProcessor;
    private NavigableMap<Long, Operations.Status> status = Maps.newTreeMap();
    private AtomicLong lastSequenceNumber = new AtomicLong(0);
    private ClientProtocolImpl clientProtocolImpl_AAA;
    private ClientProtocolImpl clientProtocolImpl_BBB;
    private ClientProtocolImpl clientProtocolImpl_CCC;

    @DataProvider(name = "OperationProvider")
    public static Operation[][] OperationProvider() {
        return new Operation[][]{
                {Operation.newBuilder().setOpCode(PUT).setKey("Hello").setValue("World").build()},
                {Operation.newBuilder().setOpCode(GET).setKey("Hello").build()},
                {Operation.newBuilder().setOpCode(DELETE).setKey("Hello").build()},
                {Operation.newBuilder().setOpCode(LIST).build()}
        };
    }

    @BeforeClass
    public void setUp() throws Exception {

        operationProcessor = new KVSCompatibleOperationProcessor(cryptoUtils);

        KeyPair keyPair = cryptoUtils.generateKeyPair();
        clientIdentifier_AAA = ClientIdentifier.builder()
                .setClientId("TestClientAAA")
                .setPrivateKey(keyPair.getPrivate())
                .setPublicKey(keyPair.getPublic())
                .build();

        keyPair = cryptoUtils.generateKeyPair();
        clientIdentifier_BBB = ClientIdentifier.builder()
                .setClientId("TestClientBBB")
                .setPrivateKey(keyPair.getPrivate())
                .setPublicKey(keyPair.getPublic())
                .build();

        keyPair = cryptoUtils.generateKeyPair();
        clientIdentifier_CCC = ClientIdentifier.builder()
                .setClientId("TestClientCCC")
                .setPrivateKey(keyPair.getPrivate())
                .setPublicKey(keyPair.getPublic())
                .build();

        clientLookupMap = new ClientLookupMap();
        clientLookupMap.put(clientIdentifier_AAA.getClientId(), clientIdentifier_AAA);
        clientLookupMap.put(clientIdentifier_BBB.getClientId(), clientIdentifier_BBB);
        clientLookupMap.put(clientIdentifier_CCC.getClientId(), clientIdentifier_CCC);

        serverProtocolImpl = new ServerProtocolImpl(new AtomicLong(0), new AtomicLong(0), state, operationProcessor, operations, authenticators, MAX_PENDING_LIST);

        clientProtocolImpl_AAA = new ClientProtocolImpl(hashChain, status, lastSequenceNumber, clientLookupMap, clientIdentifier_AAA, cryptoUtils, operationProcessor);
        clientProtocolImpl_BBB = new ClientProtocolImpl(Maps.newTreeMap(), Maps.newTreeMap(), new AtomicLong(0), clientLookupMap, clientIdentifier_BBB, cryptoUtils, operationProcessor);
        clientProtocolImpl_CCC = new ClientProtocolImpl(Maps.newTreeMap(), Maps.newTreeMap(), new AtomicLong(0), clientLookupMap, clientIdentifier_BBB, cryptoUtils, operationProcessor);

    }

    @Test
    public void testInitServer() throws Exception {

        LOG.debug(" --- SETUP INTEGRATION TEST --- ");

        Operation op = Operation.newBuilder().setOpCode(GET)
                .setKey("Init")
                .setSequenceNumber(1)
                .setClientId(clientIdentifier_AAA.getClientId())
                .setStatus(Operations.Status.SUCCESS)
                .build();

        assertTrue(cryptoUtils.extendHashChain(op, hashChain), "ExtendHashChain");

        String signature = cryptoUtils.sign(ImmutableList.of("COMMIT",
                        cryptoUtils.generateOperationUUID(op, op.getClientId()),
                        clientIdentifier_AAA.getClientId(),
                        String.valueOf(op.getSequenceNumber()),
                        op.getStatus().toString(),
                        hashChain.get(op.getSequenceNumber())),
                clientIdentifier_AAA
        );
        op = Operation.newBuilder().mergeFrom(op).setSignature(signature).build();

        Authenticator authenticator = Authenticator.newBuilder()
                .setValue(cryptoUtils.hash(state.items()))
                .setClientId(clientIdentifier_AAA.getClientId())
                .setSequenceNumber(1)
                .build();

        final String authenticatorSignature = cryptoUtils.sign(
                ImmutableList.of("AUTH",
                        cryptoUtils.generateOperationUUID(op, op.getClientId()),
                        op.getClientId(),
                        String.valueOf(1),
                        authenticator.getValue()),
                clientIdentifier_AAA
        );

        authenticator = Authenticator.newBuilder()
                .mergeFrom(authenticator)
                .setSignature(authenticatorSignature).build();

        Messages.Init initMsg = Messages.Init.newBuilder()
                .setOperation(op)
                .setAuthenticator(authenticator)
                .build();


        initMsg = clientProtocolImpl_AAA.invokeInit();


        LOG.debug("init msg : {}", initMsg);

        serverProtocolImpl.handleInit(initMsg);
    }


    @Test(dependsOnMethods = {"testInitServer"}, invocationCount = 2, timeOut = 1000)
    public void testPendingOps() throws Exception {
        LOG.debug(" --- START PENDING_OPS TEST --- ");

        Operation op1 = Operation.newBuilder().setOpCode(PUT).setKey("Hello").setValue("World").build();
        Operation op2 = Operation.newBuilder().setOpCode(GET).setKey("Hello").build();


        // op1

        Messages.Invoke invokeMsg_op1 = clientProtocolImpl_AAA.invokeOperation(op1);
        Messages.Reply replyMsg_op1 = serverProtocolImpl.handleInvoke(invokeMsg_op1);
        LOG.debug("--- op1: Reply msg: {}", TextFormat.shortDebugString(replyMsg_op1));
        LOG.debug("--- op1: Size: {} byte", replyMsg_op1.toByteArray().length);

        Messages.CommitResult commitResultMsg_op1 = clientProtocolImpl_AAA.handleReply(replyMsg_op1);
        LOG.debug("--- op1: Result: {}", commitResultMsg_op1.getResult());
        LOG.debug("--- op1: Commit msg: {}", TextFormat.shortDebugString(commitResultMsg_op1.getCommit()));
        LOG.debug("--- op1: Size: {} byte", commitResultMsg_op1.getCommit().toByteArray().length);

        serverProtocolImpl.handleCommit(commitResultMsg_op1.getCommit());




        Messages.Invoke invokeMsg_op2 = clientProtocolImpl_BBB.invokeOperation(op2);
        Messages.Reply replyMsg_op2 = serverProtocolImpl.handleInvoke(invokeMsg_op2);
        LOG.debug("--- op2: Reply msg: {}", TextFormat.shortDebugString(replyMsg_op2));
        LOG.debug("--- op2: Size: {} byte", replyMsg_op2.toByteArray().length);

        Messages.UpdateAuth updateAuth_op1 = serverProtocolImpl.uponNextCommittedOperationAvailable();
        LOG.debug("--- op1: UpdateAuth msg: {}", TextFormat.shortDebugString(updateAuth_op1));
        LOG.debug("--- op1: Size: {} byte", updateAuth_op1.toByteArray().length);

        Messages.CommitAuth commitAuth_op1 = clientProtocolImpl_AAA.handleUpdateAuth(updateAuth_op1);
        LOG.debug("--- op1: CommitAuth msg: {}", TextFormat.shortDebugString(commitAuth_op1));
        LOG.debug("--- op1: Size: {} byte", commitAuth_op1.toByteArray().length);

        serverProtocolImpl.handleCommitAuth(commitAuth_op1);



        Messages.CommitResult commitResultMsg_op2 = clientProtocolImpl_BBB.handleReply(replyMsg_op2);
        LOG.debug("--- op2: Result: {}", commitResultMsg_op2.getResult());
        LOG.debug("--- op2: Commit msg: {}", TextFormat.shortDebugString(commitResultMsg_op2.getCommit()));
        LOG.debug("--- op2: Size: {} byte", commitResultMsg_op2.getCommit().toByteArray().length);

        serverProtocolImpl.handleCommit(commitResultMsg_op2.getCommit());


        Messages.UpdateAuth updateAuth_op2 = serverProtocolImpl.uponNextCommittedOperationAvailable();
        LOG.debug("--- op2: UpdateAuth msg: {}", TextFormat.shortDebugString(updateAuth_op2));
        LOG.debug("--- op2: Size: {} byte", updateAuth_op2.toByteArray().length);

        Messages.CommitAuth commitAuth_op2 = clientProtocolImpl_BBB.handleUpdateAuth(updateAuth_op2);
        LOG.debug("--- op2: CommitAuth msg: {}", TextFormat.shortDebugString(commitAuth_op2));
        LOG.debug("--- op2: Size: {} byte", commitAuth_op2.toByteArray().length);

        serverProtocolImpl.handleCommitAuth(commitAuth_op2);



        LOG.debug("--- DONE INTEGRATION TEST --- ");
    }
}
