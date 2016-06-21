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

package com.ibm.vicos.common;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ComparisonChain;

import java.security.PrivateKey;
import java.security.PublicKey;

/**
 * Created by bur on 22/09/15.
 */
@AutoValue
public abstract class ClientIdentifier implements Comparable<ClientIdentifier> {

    public static Builder builder() {
        return new AutoValue_ClientIdentifier.Builder();
    }

    public abstract String getClientId();

    @Nullable
    public abstract PublicKey getPublicKey();

    @Nullable
    public abstract PrivateKey getPrivateKey();

    public int compareTo(ClientIdentifier that) {
        return ComparisonChain.start()
                .compare(this.getClientId(), that.getClientId())
                .result();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder setClientId(String s);

        public abstract Builder setPublicKey(PublicKey n);

        public abstract Builder setPrivateKey(PrivateKey n);

        public abstract ClientIdentifier build();
    }

}