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

import com.google.inject.Inject;

import com.ibm.vicos.common.Operations.Operation;
import com.ibm.vicos.common.crypto.CryptoUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.ibm.vicos.common.Operations.OpCode.GET;
import static com.ibm.vicos.common.Operations.OpCode.LIST;

public class KVSCompatibleOperationProcessor extends KVSOperationProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(KVSCompatibleOperationProcessor.class);

    @Inject
    public KVSCompatibleOperationProcessor(CryptoUtils cryptoUtils) {
        super(cryptoUtils);
    }

    /**
     * Implements compatible operations
     */
    public boolean isCompatible(Operation o, Operation u) {
        final String oKey = o.getKey();
        final String uKey = u.getKey();

        switch (o.getOpCode()) {
            case PUT: {
                if (((u.getOpCode() == GET) && uKey.equals(oKey)) || (u.getOpCode() == LIST)) {
                    return false;
                }
                break;
            }
            case DELETE: {
                if (((u.getOpCode() == GET) && uKey.equals(oKey)) || (u.getOpCode() == LIST)) {
                    return false;
                }
                break;
            }
        }
        return true;
    }
}
