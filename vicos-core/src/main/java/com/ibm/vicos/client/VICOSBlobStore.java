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

import com.ibm.vicos.common.KeyNotFoundException;
import com.ibm.vicos.exceptions.IntegrityException;
import com.typesafe.config.Config;

import java.io.InputStream;

public interface VICOSBlobStore {

    void init(Config config);

    InputStream getObject(String container, String name)
            throws KeyNotFoundException, IntegrityException;

    void createContainer(String container);

    void createObject(String container, String name, InputStream data, long length)
            throws IntegrityException;

    void deleteContainer(String container);

    void deleteBlob(String container, String name)
            throws KeyNotFoundException, IntegrityException;

    void dispose();
}
