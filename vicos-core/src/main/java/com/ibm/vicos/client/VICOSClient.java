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

import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.name.Named;

import com.ibm.vicos.common.ClientIdentifier;
import com.ibm.vicos.common.KeyNotFoundException;
import com.ibm.vicos.common.Operations;
import com.ibm.vicos.common.Operations.Operation;
import com.ibm.vicos.common.Operations.Result;
import com.ibm.vicos.common.StorageAPI;
import com.ibm.vicos.exceptions.IntegrityException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

import akka.actor.ActorRef;
import akka.pattern.Patterns;
import akka.util.Timeout;
import scala.concurrent.Await;
import scala.concurrent.Future;
import scala.concurrent.duration.Duration;
import scala.concurrent.duration.FiniteDuration;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.ibm.vicos.common.Operations.OpCode.DELETE;
import static com.ibm.vicos.common.Operations.OpCode.GET;
import static com.ibm.vicos.common.Operations.OpCode.INIT;
import static com.ibm.vicos.common.Operations.OpCode.LIST;
import static com.ibm.vicos.common.Operations.OpCode.PUT;
import static com.ibm.vicos.common.util.Utils.backoffSleep;

public class VICOSClient implements StorageAPI<String, String> {

    private static final Logger LOG = LoggerFactory.getLogger(VICOSClient.class);
    private static final FiniteDuration RESPONSE_TIMEOUT = Duration.create(500, TimeUnit.SECONDS);
    private final ActorRef clientActorRef;
    private final ClientIdentifier clientIdentifier;
    private final boolean retryOnAbort;


    @Inject
    public VICOSClient(@Assisted ActorRef clientActorRef,
                       @Assisted ClientIdentifier clientIdentifier,
                       @Named("retryOnAbort") boolean retryOnAbort) {
        this.clientActorRef = checkNotNull(clientActorRef, "clientActorRef");
        this.clientIdentifier = checkNotNull(clientIdentifier, "ClientIdentifier");
        this.retryOnAbort = checkNotNull(retryOnAbort, "retryOnAbort");
    }

    @Override
    public void init() {
        executeOperation(Operation.newBuilder().setOpCode(INIT).build());
    }

    @Override
    public void put(final String key, final String value) throws IntegrityException {
        Result result = executeOperation(Operation.newBuilder().setOpCode(PUT).setKey(key).setValue(value).build());
        if (result.getResultType() == Operations.ResultType.INTEGRITY_VIOLATION) {
            throw new IntegrityException("Integrity violation detected");
        }
    }

    @Override
    public String get(final String key) throws KeyNotFoundException, IntegrityException {
        Result result = executeOperation(Operation.newBuilder().setOpCode(GET).setKey(key).build());

        switch (result.getResultType()) {
            case KEY_NOT_FOUND:
                throw new KeyNotFoundException("key: " + key + " does not exist");
            case INTEGRITY_VIOLATION:
                throw new IntegrityException("Integrity violation detected");
            default:
                return result.getValues(0);
        }
    }

    @Override
    public String remove(final String key) throws KeyNotFoundException, IntegrityException {
        Result result = executeOperation(Operation.newBuilder().setOpCode(DELETE).setKey(key).build());
        switch (result.getResultType()) {
            case KEY_NOT_FOUND:
                throw new KeyNotFoundException("key: " + key + " does not exist");
            case INTEGRITY_VIOLATION:
                throw new IntegrityException("Integrity violation detected");
            default:
                return result.getValues(0);
        }
    }

    @Override
    public Iterable<String> list() throws IntegrityException {
        Result result = executeOperation(Operation.newBuilder().setOpCode(LIST).build());
        switch (result.getResultType()) {
            case INTEGRITY_VIOLATION:
                throw new IntegrityException("Integrity violation detected");
            default:
                return result.getValuesList();
        }
    }

    private Result executeOperation(final Operation operation) {

        // start with max sleep time of 1.5^4 = ~25ms
        int retryAttempts = 4;
        boolean retry;

        Result result;
        do {
            retry = false;
            try {
                Future<Object> response = Patterns.ask(clientActorRef, operation,
                        Timeout.durationToTimeout(RESPONSE_TIMEOUT));
                result = (Result) Await.result(response, RESPONSE_TIMEOUT);
            } catch (Exception e) {
                LOG.warn("Operation execution interruption", e);
                throw new RuntimeException(e);
            }

            if (result.getStatus().equals(Operations.Status.ABORT)) {
                if (retryOnAbort) {
                    LOG.info("Operation aborted! Try again: Attempt: {} [{}]", retryAttempts, operation);
                    // max sleep time 1.5^15 = ~437ms
                    // max sleep time 1.5^17 = ~985ms
                    backoffSleep(retryAttempts++ > 17 ? 17 : retryAttempts);
                    retry = true;
                } else {
                    throw new RuntimeException("Operation aborted");
                }
            }

        } while (retry);

        checkNotNull(result, "OperationResult is null");
        return result;
    }

    public String getClientId() {
        return clientIdentifier.getClientId();
    }

    public interface VICOSClientKVSAPIFactory {
        VICOSClient create(ActorRef clientProtocolRef, ClientIdentifier clientIdentifier);
    }
}
