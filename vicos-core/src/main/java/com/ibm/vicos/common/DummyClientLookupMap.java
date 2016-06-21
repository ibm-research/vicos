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

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Created by bur on 28/09/15.
 */
public class DummyClientLookupMap extends ClientLookupMap {

    private final ClientIdentifier clientIdentifier;

    public DummyClientLookupMap(ClientIdentifier clientIdentifier) {
        this.clientIdentifier = checkNotNull(clientIdentifier, "clientIdentifier");
    }

    @Override
    public ClientIdentifier get(Object key) {
        return clientIdentifier;
    }

}
