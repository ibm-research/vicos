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

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterators;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hasher;

import com.ibm.vicos.common.ClientIdentifier;
import com.ibm.vicos.common.Operations;

import java.io.Serializable;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.NavigableMap;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.ibm.vicos.common.util.Utils.base64decoding;

/**
 * Created by bur on 22/09/15.
 */
public abstract class CryptoUtils {

    public static final String DEFAULT_SECURE_RANDOM_ALGORITHM = "SHA1PRNG";
    private static final String NULL_HASH = "";

    protected static X509EncodedKeySpec generateX509EncodedKeySpec(final String input) {
        return new X509EncodedKeySpec(base64decoding(input));
    }

    public static SecureRandom getSecureRandom() throws NoSuchAlgorithmException {
        return SecureRandom.getInstance(DEFAULT_SECURE_RANDOM_ALGORITHM);
    }

    public abstract String sign(Iterable<String> items, ClientIdentifier clientIdentifier);

    public abstract boolean verify(String signature, Iterable<String> items, ClientIdentifier clientIdentifier);

    public abstract KeyPairGenerator getKeyPairGenerator() throws NoSuchAlgorithmException;

    public abstract KeyFactory getKeyFactory() throws NoSuchAlgorithmException;

    public abstract HashFunction getHashFunction();

    public String hash(final Iterable<? extends Serializable> of) {
        checkNotNull(of, "of");
        if (Iterators.size(of.iterator()) > 0) {
            Hasher hasher = getHashFunction().newHasher();
            for (Object obj : of) {
                hasher.putString(obj.toString(), Charsets.UTF_8);
            }
            return hasher.hash().toString();
        } else {
            return NULL_HASH;
        }
    }

    protected PKCS8EncodedKeySpec generatePKCS8EncodedKeySpec(final String input) {
        return new PKCS8EncodedKeySpec(base64decoding(input));
    }

    public PublicKey convert2PublicKey(final String keyData) {
        try {
            return getKeyFactory().generatePublic(generateX509EncodedKeySpec(keyData));
        } catch (InvalidKeySpecException | NoSuchAlgorithmException e) {
            return null;
        }
    }

    public PrivateKey convert2PrivateKey(final String keyData) {
        try {
            return getKeyFactory().generatePrivate(generatePKCS8EncodedKeySpec(keyData));
        } catch (InvalidKeySpecException | NoSuchAlgorithmException e) {
            return null;
        }
    }

    public abstract KeyPair generateKeyPair() throws NoSuchAlgorithmException;

    public String generateOperationUUID(Operations.Operation operation, String clientId) {
        return hash(ImmutableList.of(clientId, operation.getOpCodeValue(), operation.getKey(), operation.getValue()));
    }
    
    public boolean extendHashChain(final Operations.Operation operation, final NavigableMap<Long, String> hashChain) {
        String expectedUuid = generateOperationUUID(operation, operation.getClientId());

        final long sequenceNumber = operation.getSequenceNumber();
        final String hash = hashChain.getOrDefault(sequenceNumber, "");
        final long prevSequenceNumber = sequenceNumber - 1;
        final String prevHash = prevSequenceNumber < 1 ? "InitHash" : hashChain.getOrDefault(prevSequenceNumber, "");
        if (!hash.isEmpty() && !prevHash.isEmpty()) {
            // we already know that value
            return hash.equals(hash(ImmutableList.of(prevHash, expectedUuid, sequenceNumber, operation.getClientId())));
        } else if (hash.isEmpty() && !prevHash.isEmpty()) {
            // add to hash chain
            String value = hash(ImmutableList.of(prevHash, expectedUuid, sequenceNumber, operation.getClientId()));
            hashChain.put(sequenceNumber, value);
            return true;
        }
        return false;
    }
}
