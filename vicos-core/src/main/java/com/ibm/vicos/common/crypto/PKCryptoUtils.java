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

import com.ibm.vicos.common.ClientIdentifier;
import com.ibm.vicos.common.util.Utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.Signature;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.ibm.vicos.common.util.Utils.base64encoding;

public abstract class PKCryptoUtils extends CryptoUtils {

    private static final Logger LOG = LoggerFactory.getLogger(PKCryptoUtils.class);

    public abstract String getSignatureAlgorithm();

    public abstract String getPkiAlgorithm();

    public abstract int getKeySize();

    @Override
    public String sign(final Iterable<String> items, final ClientIdentifier clientIdentifier) {
        checkNotNull(items, "items");
        checkNotNull(clientIdentifier, "clientIdentifier");
        try {
            Signature signature = Signature.getInstance(getSignatureAlgorithm());
            signature.initSign(clientIdentifier.getPrivateKey());

            for (String item : items) {
                signature.update(Utils.utf8decoding(item));
            }
            return base64encoding(signature.sign());
        } catch (Exception e) {
            LOG.error("Error while sign \"{}\"", items, e);
            return null;
        }
    }

    @Override
    public boolean verify(final String signature, final Iterable<String> items,
                          final ClientIdentifier clientIdentifier) {
        checkNotNull(signature, "signature");
        checkNotNull(items, "items");
        checkNotNull(clientIdentifier, "clientIdentifier");

        final PublicKey publicKey = clientIdentifier.getPublicKey();
        try {
            Signature sig = Signature.getInstance(getSignatureAlgorithm());
            sig.initVerify(publicKey);
            for (String item : items) {
                sig.update(Utils.utf8decoding(item));
            }
            return sig.verify(Utils.base64decoding(signature));
        } catch (Exception e) {
            LOG.error("Error while verify signature \"{}\"", signature, e);
            return false;
        }
    }

    @Override
    public KeyPairGenerator getKeyPairGenerator() throws NoSuchAlgorithmException {
        return KeyPairGenerator.getInstance(getPkiAlgorithm());
    }

    @Override
    public KeyFactory getKeyFactory() throws NoSuchAlgorithmException {
        return KeyFactory.getInstance(getPkiAlgorithm());
    }

    @Override
    public KeyPair generateKeyPair() throws NoSuchAlgorithmException {
        KeyPairGenerator keyPairGenerator = getKeyPairGenerator();
        keyPairGenerator.initialize(getKeySize(), getSecureRandom());
        return keyPairGenerator.generateKeyPair();
    }
}
