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

package com.ibm.vicos.kvs;

import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;

import com.ibm.vicos.common.ADS.AuxiliaryData;
import com.ibm.vicos.common.KeyNotFoundException;
import com.ibm.vicos.common.State;
import com.ibm.vicos.common.StorageAPI;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.TreeMap;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkNotNull;

public class KVSState implements StorageAPI<String, String>, State, Cloneable {

    private final TreeMap<String, String> internalMap;

    @Inject
    public KVSState(TreeMap<String, String> internalMap) {
        this.internalMap = checkNotNull(internalMap, "internalMap");
    }

    @Override
    public void init() {
        this.internalMap.clear();
    }

    @Override
    public void put(String key, String value) {
        internalMap.put(key, value);
    }

    @Override
    public String get(String key) throws KeyNotFoundException {
        String value = internalMap.get(key);
        if (value != null) {
            return value;
        }
        else {
            throw new KeyNotFoundException("key: " + key + " does not exist");
        }
    }

    @Override
    public String remove(String key) throws KeyNotFoundException {
        String value = internalMap.remove(key);
        if (value != null) {
            return value;
        }
        else {
            throw new KeyNotFoundException("key: " + key + " does not exist");
        }
    }

    @Override
    public Iterable<String> list() {
        return ImmutableList.copyOf(internalMap.keySet());
    }

    @SuppressWarnings("unchecked")
    @Override
    public KVSState clone() {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(baos);
            oos.writeObject(internalMap);
            ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
            ObjectInputStream ois = new ObjectInputStream(bais);
            return new KVSState((TreeMap<String, String>) ois.readObject());
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public AuxiliaryData toAuxiliaryData() {
        AuxiliaryData.Builder builder = AuxiliaryData.newBuilder();
        builder.getMutableData().putAll(internalMap);
        return builder.build();
    }

    public Iterable<String> items() {
        return internalMap.entrySet().stream().map(entry -> entry.getKey() + ITEM_SEPARATOR + entry.getValue()).collect(Collectors.toList());
    }
}


