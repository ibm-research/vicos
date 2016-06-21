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

package com.ibm.vicos.common.util;

import com.google.common.base.CharMatcher;
import com.google.common.collect.Sets;
import com.google.inject.Inject;

import com.ibm.vicos.common.ClientIdentifier;
import com.ibm.vicos.common.crypto.CryptoUtils;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigValue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static com.google.common.base.Preconditions.checkNotNull;

public class ClientIdentityLoader {

    private static final Logger LOG = LoggerFactory.getLogger(ClientIdentityLoader.class);
    private final CryptoUtils cryptoUtils;

    @Inject
    public ClientIdentityLoader(CryptoUtils cryptoUtils) {
        this.cryptoUtils = cryptoUtils;
    }

    public ClientIdentifier loadClientIdentifier(final String clientID, final Config config) {
        checkNotNull(clientID, "clientID");
        checkNotNull(config, "config");
        String pubKeyData = config.getString(clientID + ".public");
        String privateKeyData = config.getString(clientID + ".private");

        try {
            PublicKey publicKey = cryptoUtils.convert2PublicKey(pubKeyData);
            PrivateKey privateKey = cryptoUtils.convert2PrivateKey(privateKeyData);
            return ClientIdentifier.builder().setClientId(clientID).setPublicKey(publicKey).setPrivateKey(privateKey).build();
        } catch (Exception e) {
            LOG.error("Can not create user identity {}", clientID, e);
            return null;
        }
    }

    public ConcurrentMap<ClientIdentifier, PublicKey> loadPublicKeyDirectory(final Config config) {
        ConcurrentMap<ClientIdentifier, PublicKey> publicKeyDirectory = new ConcurrentHashMap<>();

        // filter public keys
        Set<Map.Entry<String, ConfigValue>> publics = Sets.filter(config.entrySet(),
                input -> input != null && input.getKey().endsWith(".public")
        );

        // create a SimpleClientIdentifier for each public key
        for (Map.Entry<String, ConfigValue> p : publics) {
            try {
                String aClientID = CharMatcher.anyOf(".public").trimTrailingFrom(p.getKey());
                String somePubKeyData = String.valueOf(p.getValue().unwrapped());

                LOG.debug("read public key for {}", aClientID);

                PublicKey publicKey = cryptoUtils.convert2PublicKey(somePubKeyData);
                publicKeyDirectory.put(ClientIdentifier.builder().setClientId(aClientID).setPublicKey(publicKey).build(), publicKey);
            } catch (Exception e) {
                LOG.error("Can not create public", e);
                return null;
            }
        }
        return publicKeyDirectory;
    }
}
