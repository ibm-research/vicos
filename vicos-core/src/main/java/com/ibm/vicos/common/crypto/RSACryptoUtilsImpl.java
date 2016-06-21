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

package com.ibm.vicos.common.crypto;

import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import com.google.inject.Singleton;


@Singleton
public class RSACryptoUtilsImpl extends PKCryptoUtils {

    private static final String SIGNATURE_ALGORITHM = "SHA1withRSA";
    private static final String PKI_ALGORITHM = "RSA";
    private static final int PKI_KEY_SIZE = 2048;
    private static final HashFunction HASH_FUNCTION = Hashing.sha1();

    public String getSignatureAlgorithm() {
        return SIGNATURE_ALGORITHM;
    }

    @Override
    public String getPkiAlgorithm() {
        return PKI_ALGORITHM;
    }

    @Override
    public int getKeySize() {
        return PKI_KEY_SIZE;
    }

    @Override
    public HashFunction getHashFunction() {
        return HASH_FUNCTION;
    }
}
