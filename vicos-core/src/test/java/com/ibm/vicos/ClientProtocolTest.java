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

import com.google.common.collect.Maps;

import com.ibm.vicos.client.ClientProtocol;
import com.ibm.vicos.client.ClientProtocolImpl;
import com.ibm.vicos.common.ClientIdentifier;
import com.ibm.vicos.common.ClientLookupMap;
import com.ibm.vicos.common.DummyClientLookupMap;
import com.ibm.vicos.common.Operations;
import com.ibm.vicos.common.crypto.CryptoUtils;
import com.ibm.vicos.common.crypto.DSACryptoUtilsImpl;
import com.ibm.vicos.common.util.Utils;
import com.ibm.vicos.kvs.KVSCompatibleOperationProcessor;
import com.ibm.vicos.kvs.KVSOperationProcessor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.Test;

import java.security.KeyPair;
import java.util.NavigableMap;
import java.util.concurrent.atomic.AtomicLong;

import static com.ibm.vicos.common.util.ClientIdentityGenerator.generate;
import static com.ibm.vicos.common.util.ClientIdentityGenerator.generateDSA;
import static com.ibm.vicos.common.util.ClientIdentityGenerator.generateRSA;

/**
 * Created by bur on 23/09/15.
 */
public class ClientProtocolTest {

    private static final Logger LOG = LoggerFactory.getLogger(ClientProtocolTest.class);
    private final CryptoUtils cryptoUtils = new DSACryptoUtilsImpl();
    private ClientIdentifier clientIdentifier;
    private ClientLookupMap clientLookupMap;
    private KVSOperationProcessor operationProcessor;
    private NavigableMap<Long, String> hashChain;
    private NavigableMap<Long, Operations.Status> status;
    private AtomicLong lastSequenceNumber;
    private ClientProtocol clientProtocol;

    @Test
    public void testCreateClient() throws Exception {

        operationProcessor = new KVSCompatibleOperationProcessor(cryptoUtils);

        KeyPair keyPair = cryptoUtils.generateKeyPair();

        LOG.debug("Pub: {}", Utils.base64encoding(keyPair.getPublic().getEncoded()));
        LOG.debug("Prv: {}", Utils.base64encoding(keyPair.getPrivate().getEncoded()));

        clientIdentifier = generateDSA("TestClient");
        clientIdentifier = generateRSA("TestClient");
        clientIdentifier = generate("TestClient", keyPair.getPublic(), keyPair.getPrivate());
        clientLookupMap = new DummyClientLookupMap(clientIdentifier);

        hashChain = Maps.newTreeMap();
        status = Maps.newTreeMap();

        lastSequenceNumber = new AtomicLong(0);

        clientProtocol = new ClientProtocolImpl(hashChain, status, lastSequenceNumber, clientLookupMap, clientIdentifier, cryptoUtils, operationProcessor);
    }

    @Test
    public void testInvokeOperation() throws Exception {

    }

    @Test(dependsOnMethods = {"testInvokeOperation"})
    public void testHandleReply() throws Exception {

    }

    @Test
    public void testReturnResponse() throws Exception {

    }

    @Test
    public void testHandleUpdateAuth() throws Exception {

    }
}
