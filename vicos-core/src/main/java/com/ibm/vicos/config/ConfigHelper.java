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

import com.ibm.vicos.common.OperationProcessor;
import com.ibm.vicos.common.crypto.CryptoUtils;
import com.ibm.vicos.common.crypto.DSACryptoUtilsImpl;
import com.ibm.vicos.common.crypto.DummyCryptoUtilsImpl;
import com.ibm.vicos.common.crypto.HMacCryptoUtilsImpl;
import com.ibm.vicos.common.crypto.RSACryptoUtilsImpl;
import com.ibm.vicos.kvs.KVSCommuteOperationProcessor;
import com.ibm.vicos.kvs.KVSCompatibleOperationProcessor;

/**
 * Created by bur on 02/10/15.
 */
public class ConfigHelper {

    public static Class<? extends OperationProcessor> returnOperationProcessorImpl(String mode) {
        switch (mode) {
            case "compatible":
                return KVSCompatibleOperationProcessor.class;
            case "commutative":
                return KVSCommuteOperationProcessor.class;
            default:
                return KVSCompatibleOperationProcessor.class;
        }
    }

    public static Class<? extends CryptoUtils> returnCryptoImplClazz(String signatures) {
        signatures = signatures.toUpperCase();
        switch (signatures) {
            case "RSA":
                return RSACryptoUtilsImpl.class;
            case "DSA":
                return DSACryptoUtilsImpl.class;
            case "HMAC":
                return HMacCryptoUtilsImpl.class;
            case "NONE":
                return DummyCryptoUtilsImpl.class;
            default:
                return RSACryptoUtilsImpl.class;
        }
    }

}
