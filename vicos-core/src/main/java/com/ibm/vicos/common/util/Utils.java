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

import com.google.common.base.Charsets;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import com.google.common.io.BaseEncoding;
import com.google.protobuf.ByteString;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

import static com.google.common.base.Preconditions.checkNotNull;

public class Utils {

    private static final Logger LOG = LoggerFactory.getLogger(Utils.class);
    private static final int NONCE_INITIAL_VALUE = 0;
    private static final String NAME_EXTENSION_SEPARATOR = "-";
    private static final String FLAT_KEY_SEPARATOR = "/";
    private static AtomicLong nonce = new AtomicLong(NONCE_INITIAL_VALUE);
    private static Random random;

    public static void backoffSleep(int attempt) {
        if (random == null) {
            random = new Random(System.currentTimeMillis());
        }

        double backoff = 1.5;
        int sleepy = 1;
        int min = 0;
        int max = (int) Math.pow(backoff, attempt) - 1;
        int sleepTime = (random.nextInt((max - min) + 1) + min) * sleepy;
        LOG.debug("Sleep {} ms [{},{}]", sleepTime, min, max);

        sleep(sleepTime);
    }

    public static void sleep(long delayMillis) {
        try {
            Thread.sleep(delayMillis);
        } catch (InterruptedException e) {
            LOG.error("There is an InterruptedException while sleeping", e);
        }
    }

    public static String base64encoding(byte[] bytes) {
        return BaseEncoding.base64().encode(bytes);
    }

    public static byte[] base64decoding(String string) {
        return BaseEncoding.base64().decode(string);
    }

    public static String utf8encoding(byte[] bytes) {
        return ByteString.copyFrom(bytes).toStringUtf8();
    }

    public static byte[] utf8decoding(String string) {
        return ByteString.copyFromUtf8(string).toByteArray();
    }

    public static String hashMurmur3_32(String input) {
        Hasher hasher = Hashing.murmur3_32().newHasher();
        hasher.putString(input, Charsets.UTF_8);
        return hasher.hash().toString();
    }

    @Deprecated
    public static synchronized String pickNonce() {
        if (nonce == null) {
            nonce = new AtomicLong(NONCE_INITIAL_VALUE);
        }
        // using UUID.randomUUID() is super slow! Do not use it if possible
        return String.valueOf(nonce.incrementAndGet() + "-" + UUID.randomUUID());
    }

    public static String transformToFlatKey(String container, String name) {
        return container + FLAT_KEY_SEPARATOR + name;
    }

    public static String transformBlobName(String name, String prefix, String postfix) {
        return prefix + NAME_EXTENSION_SEPARATOR + name + NAME_EXTENSION_SEPARATOR + postfix;
    }

    protected static Pattern generateKeyPattern(String container) {
        return Pattern.compile("^(" + container + "/)(.+)");
    }

    public static String map2String(Map<? extends Serializable, ? extends Serializable> map) {
        checkNotNull(map, "map");
        final StringBuilder builder = new StringBuilder();
        for (Map.Entry item : map.entrySet()) {
            builder.append(item.getKey().toString())
                    .append(" : ")
                    .append(item.getValue().toString())
                    .append("\n");
        }
        return builder.toString();
    }
}
