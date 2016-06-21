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

package com.ibm.vicos.config;

import com.google.common.collect.Maps;
import com.google.inject.Binder;
import com.google.inject.Module;
import com.google.inject.name.Names;

import com.ibm.vicos.common.OperationProcessor;
import com.ibm.vicos.common.State;
import com.ibm.vicos.common.crypto.CryptoUtils;
import com.ibm.vicos.common.crypto.RSACryptoUtilsImpl;
import com.ibm.vicos.kvs.KVSCompatibleOperationProcessor;
import com.ibm.vicos.kvs.KVSState;
import com.ibm.vicos.server.ServerProtocol;
import com.ibm.vicos.server.ServerProtocolImpl;

import java.util.concurrent.atomic.AtomicLong;

public class DefaultServerModule implements Module {

    @Override
    public void configure(Binder binder) {
        binder.bind(AtomicLong.class)
                .annotatedWith(Names.named("lastInvokedSeqNo"))
                .toInstance(new AtomicLong(0));

        binder.bind(AtomicLong.class)
                .annotatedWith(Names.named("lastAppliedSeqNo"))
                .toInstance(new AtomicLong(0));

        binder.bind(State.class).toInstance(new KVSState(Maps.newTreeMap()));

        binder.bind(OperationProcessor.class).to(KVSCompatibleOperationProcessor.class);

        binder.bind(CryptoUtils.class).to(RSACryptoUtilsImpl.class);

        binder.bind(ServerProtocol.class).to(ServerProtocolImpl.class);
    }
}
