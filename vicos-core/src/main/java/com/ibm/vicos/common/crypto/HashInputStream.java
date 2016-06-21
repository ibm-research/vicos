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

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.ibm.vicos.common.util.Utils.base64encoding;

public class HashInputStream extends FilterInputStream {
    private final byte[] expectedHash;
    private final Hasher hasher;
    private byte[] hash;
    private boolean validationMode = false;
    private boolean isDone = false;
    private long total = 0;

    public HashInputStream(final InputStream in, final String hasherAlgorithm) throws InvalidKeyException, NoSuchAlgorithmException {
        this(in, null, hasherAlgorithm);
    }

    public HashInputStream(final InputStream in, final byte[] expectedHash, final String hasherAlgorithm) throws NoSuchAlgorithmException, InvalidKeyException {
        super(checkNotNull(in, "inputstream"));
        this.expectedHash = expectedHash;
        this.hasher = Hasher.getInstance(hasherAlgorithm);

        if (expectedHash != null) {
            validationMode = true;
        }
    }

    @Override
    public int read() throws IOException {
        // please do not call this
        byte[] b = new byte[1];
        return read(b);
    }

    @Override
    public int read(byte[] b) throws IOException {
        return read(b, 0, b.length);
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        if (isDone()) {
            return -1;
        }
        if (b == null) {
            throw new NullPointerException();
        } else if (off < 0 || len < 0 || len > b.length - off) {
            throw new IndexOutOfBoundsException();
        } else if (len == 0) {
            return 0;
        }

        int result = in.read(b, off, len);

        if (result > 0) {
            hasher.update(b, 0, result);
            total += result;
        } else if (result == -1) {
            hash = hasher.digest();
            isDone = true;

            if (validationMode) {
                checkState(Arrays.equals(expectedHash, hash),
                        "Hash validation failed! \n" +
                                "Expected: " + base64encoding(expectedHash) + "\n" +
                                "Actual: " + base64encoding(hash) + "\n" +
                                "total: " + total + "\n" +
                                "hasher: " + hasher + "\n" +
                                "in: " + in
                );
            }
        }

        return result;
    }

    public boolean isDone() {
        return isDone;
    }

    public byte[] getHash() {
        return isDone() ? hash : null;
    }

    private static class Hasher {
        private final MessageDigest md;

        private AtomicBoolean isDone = new AtomicBoolean(false);

        private Hasher(final String algorithm) throws NoSuchAlgorithmException, InvalidKeyException {
            md = MessageDigest.getInstance(algorithm);
        }

        public static Hasher getInstance(final String algorithm) throws InvalidKeyException, NoSuchAlgorithmException {
            return new Hasher(algorithm);
        }

        public final void update(byte[] bytes, int off, int len) throws IllegalStateException {
            synchronized (md) {
                checkState(!isDone.get(), "Already invoked digest");
                md.update(bytes, off, len);
            }
        }

        public final byte[] digest() throws IllegalStateException {
            synchronized (md) {
                isDone.compareAndSet(false, true);
                return md.digest();
            }
        }
    }
}
