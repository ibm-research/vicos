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

package com.ibm.vicos.common;

/**
 * Created by bur on 25/09/15.
 */
public interface StorageAPI<K, V> {

    void init();

    /**
     * Stores the key value pair
     */
    void put(K key, V value);

    /**
     * Retrieves the value for key
     */
    V get(K key) throws KeyNotFoundException;

    /**
     * Removes a object with key
     */
    V remove(K key) throws KeyNotFoundException;

    /**
     * List all keys in KVS
     *
     * @return iterable of strings
     */
    Iterable<V> list();
}
