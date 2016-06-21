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

import com.google.inject.Singleton;
import com.google.inject.name.Named;

import com.ibm.vicos.common.ClientIdentifier;
import com.ibm.vicos.common.util.Utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.Key;
import java.util.Arrays;

import javax.crypto.Mac;
import javax.inject.Inject;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.ibm.vicos.common.util.Utils.base64encoding;

@Singleton
public class HMacCryptoUtilsImpl extends RSACryptoUtilsImpl {

    private static final Logger LOG = LoggerFactory.getLogger(HMacCryptoUtilsImpl.class);
    private static final String DEFAULT_HMAC_ALGORITHM = "HmacSHA1";
    private final Key sharedKey;

    @Inject
    public HMacCryptoUtilsImpl(@Named("sharedKey") Key sharedKey) {
        this.sharedKey = checkNotNull(sharedKey, "sharedKey");
    }

    public String sign(final Iterable<String> items, final ClientIdentifier clientIdentifier) {
        checkNotNull(items, "items");
        checkNotNull(clientIdentifier, "clientIdentifier");
        try {
            Mac mac = Mac.getInstance(DEFAULT_HMAC_ALGORITHM);
            mac.init(sharedKey);
            for (String item : items) {
                mac.update(Utils.utf8decoding(item));
            }
            return base64encoding(mac.doFinal());
        } catch (Exception e) {
            LOG.error("Error while sign \"{}\": {}", items, e);
            return null;
        }
    }

    public boolean verify(final String signature, final Iterable<String> items,
                          final ClientIdentifier clientIdentifier) {
        checkNotNull(signature, "signature");
        checkNotNull(items, "items");
        try {
            Mac mac = Mac.getInstance(DEFAULT_HMAC_ALGORITHM);
            mac.init(sharedKey);
            for (String item : items) {
                mac.update(Utils.utf8decoding(item));
            }
            return Arrays.equals(mac.doFinal(), Utils.base64decoding(signature));
        } catch (Exception e) {
            LOG.error("Error while verify signature \"{}\": {}", signature.toString(), e);
            return false;
        }
    }
}
