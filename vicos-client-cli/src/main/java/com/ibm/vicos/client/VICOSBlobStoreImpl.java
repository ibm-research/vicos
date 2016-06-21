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

package com.ibm.vicos.client;

import com.google.common.base.Strings;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import com.ibm.vicos.common.KeyNotFoundException;
import com.ibm.vicos.common.Metadata.IntegrityMetadata;
import com.ibm.vicos.common.crypto.HashInputStream;
import com.ibm.vicos.common.util.ClientBuilder;
import com.ibm.vicos.common.util.Utils;
import com.ibm.vicos.exceptions.IntegrityException;
import com.typesafe.config.Config;

import org.jclouds.ContextBuilder;
import org.jclouds.blobstore.BlobStore;
import org.jclouds.blobstore.BlobStoreContext;
import org.jclouds.blobstore.domain.Blob;
import org.jclouds.providers.Providers;

import java.io.InputStream;
import java.util.Properties;

import static com.google.common.base.Preconditions.checkState;
import static com.ibm.vicos.common.util.Utils.base64encoding;
import static com.ibm.vicos.common.util.Utils.hashMurmur3_32;
import static com.ibm.vicos.common.util.Utils.transformBlobName;
import static org.jclouds.Constants.PROPERTY_RELAX_HOSTNAME;
import static org.jclouds.Constants.PROPERTY_TRUST_ALL_CERTS;

/**
 * Created by bur on 23/03/16.
 */
public class VICOSBlobStoreImpl implements VICOSBlobStore {
    private static final String HASH_ALGORITHM = "SHA-1";
    private static final String INIT_TOKEN = "_init_";
    private static final String NONCE_PREFIX = "-v";
    private static final String IDENTITY_KEY = "identity";
    private static final String CREDENTIAL_KEY = "credential";
    private static final String PROVIDER_KEY = "provider";
    private static final String ENDPOINT_KEY = "endpoint";
    private BlobStoreContext context;
    private boolean isInitialized = false;
    private VICOSClient client;
    private long objCounter = 0;

    @Override
    public void init(Config config) {

        // init storage
        final String provider = config.getString(String.join(".", "cos", PROVIDER_KEY));
        final String endpoint = config.getString(String.join(".", "cos", ENDPOINT_KEY));
        final String identity = config.getString(String.join(".", "cos", IDENTITY_KEY));
        final String credential = config.getString(String.join(".", "cos", CREDENTIAL_KEY));

        final Properties properties = new Properties();
        properties.setProperty("provider", provider);
        properties.setProperty(String.join(".", provider, IDENTITY_KEY), identity);
        properties.setProperty(String.join(".", provider, CREDENTIAL_KEY), credential);
        properties.setProperty(String.join(".", provider, ENDPOINT_KEY), endpoint);

        properties.setProperty(PROPERTY_RELAX_HOSTNAME, "true");
        properties.setProperty(PROPERTY_TRUST_ALL_CERTS, "true");

        Thread.currentThread().setContextClassLoader(Providers.class.getClassLoader());

        context = ContextBuilder.newBuilder(provider)
                .overrides(properties)
                .buildView(BlobStoreContext.class);

        // init vicos
        client = ClientBuilder.buildFromConfig(config);
        isInitialized = true;
    }

    @Override
    public InputStream getObject(String container, String name) throws KeyNotFoundException, IntegrityException {
        // ask VICOSClient
        final String result = client.get(Utils.transformToFlatKey(container, name));
        try {
            IntegrityMetadata metadata = IntegrityMetadata.parseFrom(Utils.base64decoding(result));
            final InputStream in = getBlobStore().getBlob(container,
                    transformBlobName(name, hashMurmur3_32(name), metadata.getNonce())
            ).getPayload().openStream();

            return new HashInputStream(in, metadata.getHashValue().toByteArray(), HASH_ALGORITHM);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public void createContainer(String container) {
        // special condition for restarting VICOS server
        if (container.startsWith(INIT_TOKEN) && container.endsWith(INIT_TOKEN)) {
            client.init();
            return;
        }
        getBlobStore().createContainerInLocation(null, container);
    }

    @Override
    public void createObject(String container, String name, InputStream data, long length) throws IntegrityException {
        final HashInputStream hmacInputStream;
        try {
            hmacInputStream = new HashInputStream(data, HASH_ALGORITHM);
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }
        // pick a nonce and save in blobState
        final String nonce = client.getClientId() + NONCE_PREFIX + objCounter++;

        Blob blob = getBlobStore().blobBuilder(transformBlobName(name, hashMurmur3_32(name), nonce))
                .payload(hmacInputStream)
                .contentLength(length)
                .build();
        getBlobStore().putBlob(container, blob);
        // object store putBlob object
        if (hmacInputStream.isDone()) {
            String key = Utils.transformToFlatKey(container, name);
            IntegrityMetadata metadata = IntegrityMetadata.newBuilder()
                    .setNonce(nonce)
                    .setHashValue(ByteString.copyFrom(hmacInputStream.getHash()))
                    .build();
            client.put(key, base64encoding(metadata.toByteArray()));
        } else {
            checkState(false, "Error while generating hmac for object");
        }
    }

    @Override
    public void deleteContainer(String container) {
        // object store delete container
        getBlobStore().deleteContainer(container);
    }

    @Override
    public void deleteBlob(String container, String name) throws KeyNotFoundException, IntegrityException {
        // VICOS delete integrity metadata
        String result = client.remove(Utils.transformToFlatKey(container, name));
        if (!Strings.isNullOrEmpty(result)) {
            try {
                IntegrityMetadata metadata = IntegrityMetadata.parseFrom(Utils.base64decoding(result));
                // object store delete
                getBlobStore().removeBlob(container,
                        transformBlobName(name, hashMurmur3_32(name), metadata.getNonce()));
            } catch (InvalidProtocolBufferException e) {
                e.printStackTrace();
            }
        }
    }

    private BlobStore getBlobStore() {
        if (isInitialized) {
            return context.getBlobStore();
        } else {
            throw new RuntimeException("Storage is not initialized");
        }
    }

    @Override
    public void dispose() {

    }
}
