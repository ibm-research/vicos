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
import com.google.inject.TypeLiteral;
import com.google.inject.assistedinject.FactoryModuleBuilder;
import com.google.inject.name.Names;

import com.ibm.vicos.client.VICOSClient;
import com.ibm.vicos.common.ClientIdentifier;
import com.ibm.vicos.common.OperationProcessor;
import com.ibm.vicos.common.Operations.Status;
import com.ibm.vicos.common.StorageAPI;
import com.ibm.vicos.common.crypto.CryptoUtils;
import com.ibm.vicos.common.crypto.RSACryptoUtilsImpl;
import com.ibm.vicos.kvs.KVSCompatibleOperationProcessor;

import java.util.Map;
import java.util.NavigableMap;
import java.util.concurrent.atomic.AtomicLong;

public class DefaultClientModule implements Module {

    @Override
    public void configure(Binder binder) {
        binder.bind(new TypeLiteral<NavigableMap<Long, String>>() {
        })
                .annotatedWith(Names.named("hashChainMap"))
                .toInstance(Maps.<Long, String>newTreeMap());

        binder.bind(new TypeLiteral<NavigableMap<Long, Status>>() {
        })
                .annotatedWith(Names.named("statusMap"))
                .toInstance(Maps.<Long, Status>newTreeMap());

        binder.bind(new TypeLiteral<Map<String, ClientIdentifier>>() {
        })
                .annotatedWith(Names.named("clientLookupMap"))
                .toInstance(Maps.<String, ClientIdentifier>newTreeMap());

        binder.bind(AtomicLong.class)
                .annotatedWith(Names.named("lastSequenceNumber"))
                .toInstance(new AtomicLong(0));

        binder.bind(CryptoUtils.class).to(RSACryptoUtilsImpl.class);

        binder.bind(OperationProcessor.class).to(KVSCompatibleOperationProcessor.class);

        binder.install(new FactoryModuleBuilder()
                .implement(StorageAPI.class, VICOSClient.class)
                .build(VICOSClient.VICOSClientKVSAPIFactory.class));
    }
}
