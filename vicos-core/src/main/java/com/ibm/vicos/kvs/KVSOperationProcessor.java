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

package com.ibm.vicos.kvs;

import com.google.common.collect.Maps;
import com.google.inject.Inject;

import com.ibm.vicos.common.ADS.Authenticator;
import com.ibm.vicos.common.ADS.AuxiliaryData;
import com.ibm.vicos.common.KeyNotFoundException;
import com.ibm.vicos.common.OperationProcessor;
import com.ibm.vicos.common.Operations;
import com.ibm.vicos.common.Operations.Operation;
import com.ibm.vicos.common.Operations.Result;
import com.ibm.vicos.common.crypto.CryptoUtils;
import com.ibm.vicos.exceptions.IntegrityException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.TreeMap;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.ibm.vicos.common.Operations.OpCode.DELETE;
import static com.ibm.vicos.common.Operations.OpCode.GET;
import static com.ibm.vicos.common.Operations.OpCode.LIST;
import static com.ibm.vicos.common.Operations.OpCode.PUT;

public abstract class KVSOperationProcessor extends OperationProcessor<KVSState> {

    private static final Logger LOG = LoggerFactory.getLogger(KVSOperationProcessor.class);
    private final CryptoUtils cryptoUtils;

    @Inject
    public KVSOperationProcessor(CryptoUtils cryptoUtils) {
        this.cryptoUtils = checkNotNull(cryptoUtils, "cryptoUtils");
    }

    public QueryResult query(KVSState state, List<Operation> operations) {
        final KVSState tempState = state.clone();
        return QueryResult.builder()
                .setResult(execute(tempState, operations))
                .setAuxiliaryData(state.toAuxiliaryData())
                .build();
    }

    private Result execute(KVSState state, List<Operation> operations) {
        checkArgument(!operations.isEmpty(), "No operations to execute");
        return operations.stream().map(o -> {
            Result.Builder resultBuilder = Result.newBuilder();
            switch (o.getOpCode()) {
                case PUT: {
                    state.put(o.getKey(), o.getValue());
                    resultBuilder.setOpCode(PUT);
                    break;
                }
                case GET: {
                    resultBuilder.setOpCode(GET);
                    try {
                        String result = state.get(o.getKey());
                        resultBuilder.addValues(result);
                    } catch (KeyNotFoundException e) {
                        resultBuilder.setResultType(Operations.ResultType.KEY_NOT_FOUND);
                    }
                    break;
                }
                case DELETE: {
                    resultBuilder.setOpCode(DELETE);
                    try {
                        String result = state.remove(o.getKey());
                        resultBuilder.addValues(result);
                    } catch (KeyNotFoundException e) {
                        resultBuilder.setResultType(Operations.ResultType.KEY_NOT_FOUND);
                    }
                    break;
                }
                case LIST: {
                    Iterable<String> result = state.list();
                    resultBuilder.setOpCode(LIST).addAllValues(result);
                    break;
                }
                default:
                    throw new RuntimeException("Unknown operation");
            }
            return resultBuilder;
        }).reduce((prev, next) -> next).get().build();
    }

    public AuthExecResult authexec(List<Operation> operations, Authenticator authenticator, Result result, AuxiliaryData auxiliaryData) {
        LOG.trace("Authexec {}, {}, {}", operations, authenticator, result);
        TreeMap<String, String> data = Maps.newTreeMap();
        data.putAll(auxiliaryData.getData());
        KVSState state = new KVSState(data);

        // verify current state
        String a = cryptoUtils.hash(state.items());
        if (!authenticator.getValue().equals(a)) {
            throw new IntegrityException("Integrity violation! Expected: " + authenticator.getValue() + " but found: " + a);
        }

        Result ourResult = execute(state, operations);
        boolean isValid = result.equals(ourResult);
        if (!isValid) {
            LOG.debug("Result expected: {}", result);
            LOG.debug("Result actual: {}", ourResult);
        }

        long sequenceNumber = operations.stream().reduce((prev, next) -> next).get().getSequenceNumber();

        Authenticator newAuthenticator = Authenticator.newBuilder()
                .setValue(cryptoUtils.hash(state.items()))
                .setSequenceNumber(sequenceNumber)
                .build();

        return AuthExecResult.builder()
                .setAuthenticator(newAuthenticator)
                .setAuxiliaryData(state.toAuxiliaryData())
                .setValid(isValid)
                .build();
    }

    public KVSState refresh(KVSState state, Operation operation, AuxiliaryData auxiliaryData) {
        switch (operation.getOpCode()) {
            case GET: {
                return state;
            }
            case PUT: {
                KVSState newState = state.clone();
                newState.put(operation.getKey(), operation.getValue());
                return newState;
            }
            case DELETE: {
                try {
                    KVSState newState = state.clone();
                    newState.remove(operation.getKey());
                    return newState;
                } catch (KeyNotFoundException e) {
                    LOG.error("Key not found", e);
                    return state;
                }

            }
            case LIST: {
                return state;
            }
            default: {
                throw new RuntimeException("Unknown operation");
            }
        }
    }

    public boolean isCompatible(List<Operation> listOthers, Operation currentOperation) {
        LOG.trace("Compatibility check: mu: {} u: {}", listOthers, currentOperation);
        return listOthers.isEmpty() || listOthers.stream().allMatch(o -> isCompatible(o, currentOperation));
    }

    public boolean isUpdateOperation(Operation operation) {
        return operation.getOpCode() == PUT || operation.getOpCode() == DELETE;
    }
}
