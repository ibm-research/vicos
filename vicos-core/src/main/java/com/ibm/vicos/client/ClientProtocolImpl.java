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

import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.google.protobuf.TextFormat;

import com.ibm.vicos.common.ADS.Authenticator;
import com.ibm.vicos.common.ADS.AuxiliaryData;
import com.ibm.vicos.common.ClientIdentifier;
import com.ibm.vicos.common.ClientLookupMap;
import com.ibm.vicos.common.Messages.Commit;
import com.ibm.vicos.common.Messages.CommitAuth;
import com.ibm.vicos.common.Messages.CommitResult;
import com.ibm.vicos.common.Messages.Init;
import com.ibm.vicos.common.Messages.Invoke;
import com.ibm.vicos.common.Messages.Reply;
import com.ibm.vicos.common.Messages.UpdateAuth;
import com.ibm.vicos.common.OperationProcessor;
import com.ibm.vicos.common.Operations.Operation;
import com.ibm.vicos.common.Operations.Result;
import com.ibm.vicos.common.Operations.Status;
import com.ibm.vicos.common.crypto.CryptoUtils;
import com.ibm.vicos.exceptions.IntegrityException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.NavigableMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.ibm.vicos.common.Operations.OpCode.GET;
import static com.ibm.vicos.common.Operations.Status.ABORT;
import static com.ibm.vicos.common.Operations.Status.SUCCESS;
import static com.ibm.vicos.common.Operations.Status.UNKNOWN;
import static java.lang.Long.max;

/**
 * Created by bur on 22/09/15.
 */
public class ClientProtocolImpl implements ClientProtocol {

    private static final Logger LOG = LoggerFactory.getLogger(ClientProtocolImpl.class);
    private final NavigableMap<Long, String> hashChain; // H
    private final NavigableMap<Long, Status> status; // Z
    private final AtomicLong lastSequenceNumber;
    private final ClientIdentifier clientIdentifier;
    private final CryptoUtils cryptoUtils;
    private final OperationProcessor operationProcessor;
    private final ClientLookupMap clientLookupMap;
    private Operation currentOperation; // u

    @Inject
    public ClientProtocolImpl(@Named("hashChainMap") NavigableMap<Long, String> hashChain,
                              @Named("statusMap") NavigableMap<Long, Status> status,
                              @Named("lastSequenceNumber") AtomicLong lastSequenceNumber,
                              @Named("clientLookupMap") ClientLookupMap clientLookupMap,
                              @Named("clientIdentifier") ClientIdentifier clientIdentifier,
                              CryptoUtils cryptoUtils,
                              OperationProcessor operationProcessor) {
        this.hashChain = checkNotNull(hashChain, "hashChain");
        this.status = checkNotNull(status, "status");
        this.lastSequenceNumber = checkNotNull(lastSequenceNumber, "lastSequenceNumber");
        this.clientIdentifier = checkNotNull(clientIdentifier, "clientIdentifier");
        this.cryptoUtils = checkNotNull(cryptoUtils, "cryptoUtils");
        this.operationProcessor = checkNotNull(operationProcessor, "operationProcessor");
        this.clientLookupMap = checkNotNull(clientLookupMap, "clientLookupMa");
    }

    public Invoke invokeOperation(Operation operation) {
        LOG.debug("Process operation invocation");
        LOG.trace("Operation: {}", operation);
        LOG.trace("Last known sequence number: {}", lastSequenceNumber.get());

        String uuid = cryptoUtils.generateOperationUUID(operation, getClientId());

        final String signature = cryptoUtils.sign(
                ImmutableList.of("INVOKE",
                        uuid,
                        clientIdentifier.getClientId()),
                clientIdentifier
        );

        registerCurrentOperation(Operation.newBuilder()
                .mergeFrom(operation)
                .setSignature(signature)
//                .setUuid(uuid)
                .setClientId(clientIdentifier.getClientId())
                .build());

        return Invoke.newBuilder()
                .setOperation(currentOperation)
                .setLastSequenceNumber(lastSequenceNumber.get())
                .build();
    }

    public CommitResult handleReply(final Reply message) throws IntegrityException {
        LOG.debug("Process Reply message");
        LOG.trace("message {}", message);

        Operation currentOperation = Operation.newBuilder()
                .mergeFrom(this.currentOperation)
                .setSequenceNumber(message.getAssignedSequenceNumber())
                .build();

        LOG.debug("Current op: {}", currentOperation);

        Result result = message.getResult();
        AuxiliaryData auxiliaryData = message.getAuxiliaryData();
        List<Operation> pendingOperations = message.getPendingOperationsList();
        List<Operation> deltaOperations = message.getDeltaOperationsList();
        Authenticator authenticator = message.getLastAuthenticator();

        // add current operation to pending
        pendingOperations = new ImmutableList.Builder<Operation>()
                .addAll(pendingOperations).add(currentOperation).build();

        checkView(deltaOperations, authenticator);
        checkPendingOperations(pendingOperations);

        // if pending op has already abort status, we can drop that one
        List<Operation> pendingOthers = pendingOperations.stream()
                .filter(o -> !o.getClientId().equals(getClientId()))
                .filter(o -> o.getStatus() == UNKNOWN)
                .collect(Collectors.toList());

        // isCompatible check
        Status currentOperationStatus;
        if (operationProcessor.isCompatible(pendingOthers, currentOperation)) {
            LOG.debug("[SUCCESS] Operation is compatible!");

            // successful operations by others
            List<Operation> successOthers = pendingOperations.stream()
                    .filter(o -> !o.getClientId().equals(getClientId()))
                    .filter(o -> o.getStatus() != SUCCESS)
                    .collect(Collectors.toList());

            List<Operation> pendingSelfs = pendingOperations.stream()
                    .filter(o -> o.getClientId().equals(getClientId()))
                    .filter(o -> o.getStatus() != ABORT)
                    .collect(Collectors.toList());

            successOthers.addAll(pendingSelfs);

            if (!operationProcessor.authexec(successOthers, authenticator, result, auxiliaryData).isValid()) {
                LOG.error("Integrity violation detected!!! Protocol has been stopped!");
                throw new IntegrityException("Integrity violation detected");
            }
            currentOperationStatus = SUCCESS;
        } else {
            LOG.debug("[ABORT] Operation is not compatible.");
            currentOperationStatus = ABORT;
        }
        result = Result.newBuilder().mergeFrom(result).setStatus(currentOperationStatus).build();

        status.put(currentOperation.getSequenceNumber(), currentOperationStatus);

        // can we return the result already to the client and after that creating the signature?

        String signature = cryptoUtils.sign(ImmutableList.of("COMMIT",
//                        currentOperation.getUuid(),
                        cryptoUtils.generateOperationUUID(currentOperation, currentOperation.getClientId()),
                        getClientId(),
                        String.valueOf(currentOperation.getSequenceNumber()),
                        currentOperationStatus.toString(),
                        hashChain.get(currentOperation.getSequenceNumber())),
                clientIdentifier
        );

        unregisterCurrentOperation();

        return CommitResult.newBuilder()
                .setCommit(Commit.newBuilder()
                        .setSequenceNumber(currentOperation.getSequenceNumber())
                        .setSignature(signature)
                        .setStatus(currentOperationStatus))
                .setResult(result)
                .build();
    }

    private void registerCurrentOperation(Operation operation) {
        checkState(currentOperation == null, "There is still an operation invocation in process");
        this.currentOperation = checkNotNull(operation);
    }

    private void unregisterCurrentOperation() {
        this.currentOperation = null;
    }

    public CommitAuth handleUpdateAuth(final UpdateAuth message) throws IntegrityException {
        LOG.debug("Process UpdateAuth message ");
        LOG.debug("message {}", message);

        final Operation lastOperation = message.getLastOperation();
        final Authenticator lastAuthenticator = message.getLastAuthenticator();

        final Operation operation = message.getOperation();
        final Result result = message.getResult();
        final AuxiliaryData auxiliaryData = message.getAuxiliaryData();

        checkArgument(operation.getStatus() != UNKNOWN, "Operation has unknown state: %s", operation);
        checkNotNull(hashChain.get(operation.getSequenceNumber()), "Hash chain entry must not be null! %s", operation);

        // verify operation
        checkArgument(cryptoUtils.verify(operation.getSignature(),
                ImmutableList.of("COMMIT",
                        cryptoUtils.generateOperationUUID(operation, operation.getClientId()),
                        operation.getClientId(),
                        String.valueOf(operation.getSequenceNumber()),
                        operation.getStatus().toString(),
                        hashChain.get(operation.getSequenceNumber())),
                clientIdentifier
        ), "Verification failed: Operation seems to be corrupted: %s", operation);

        // verify authenticator
        checkArgument(cryptoUtils.verify(lastAuthenticator.getSignature(),
                ImmutableList.of("AUTH",
                        cryptoUtils.generateOperationUUID(lastOperation, lastOperation.getClientId()),
                        lastOperation.getClientId(),
                        String.valueOf(lastOperation.getSequenceNumber()),
                        lastAuthenticator.getValue()),
                clientLookupMap.get(lastAuthenticator.getClientId())
        ), "Verification failed: Last authenticator seems to be corrupted: %s", lastAuthenticator);

        Authenticator newAuthenticator;
        if (operation.getStatus() == ABORT) {
            // reuse previous authenticator and do not execute operation
            LOG.debug("Operation was aborted. Reuse authenticator value from previous one.");
            newAuthenticator = lastAuthenticator;
        } else {
            OperationProcessor.AuthExecResult authExecResult = operationProcessor.authexec(ImmutableList.of(operation), lastAuthenticator, result, auxiliaryData);
            if (!authExecResult.isValid())
                throw new IntegrityException("Integrity violation while updating authenticator");

            newAuthenticator = authExecResult.getAuthenticator();
        }

        final String authenticatorSignature = cryptoUtils.sign(
                ImmutableList.of("AUTH",
                        cryptoUtils.generateOperationUUID(operation, operation.getClientId()),
                        operation.getClientId(),
                        String.valueOf(operation.getSequenceNumber()),
                        newAuthenticator.getValue()),
                clientIdentifier
        );
        LOG.trace("New authenticator signature: {}", authenticatorSignature);
        updateSequenceNumber(operation.getSequenceNumber());

        return CommitAuth.newBuilder()
                .setAuthenticator(Authenticator.newBuilder()
                        .mergeFrom(newAuthenticator)
                        .setSequenceNumber(operation.getSequenceNumber())
                        .setClientId(getClientId())
                        .setSignature(authenticatorSignature))
                .build();
    }

    /**
     * @return sequence number of the last operation in the input list of operations
     */
    private long checkView(final List<Operation> deltaOperations, final Authenticator authenticator) {
        checkNotNull(deltaOperations, "deltaOperations");
        checkNotNull(authenticator, "Authenticator");

        // verify operations
        Operation operation = deltaOperations.stream().map(o -> {
            LOG.trace("CheckVIewOp: {}", TextFormat.printToString(o));
            checkState(cryptoUtils.extendHashChain(o, hashChain), "Hash chain verification failed");
            checkState(cryptoUtils.verify(o.getSignature(),
                    ImmutableList.of("COMMIT",
                            cryptoUtils.generateOperationUUID(o, o.getClientId()),
                            o.getClientId(),
                            String.valueOf(o.getSequenceNumber()),
                            o.getStatus().toString(),
                            hashChain.get(o.getSequenceNumber())),
                    clientLookupMap.get(o.getClientId())
            ), "Verification failed: Committed operation seems to be corrupted: %s", o);
            return o;
        }).reduce((prev, next) -> next).get();

        checkState(operation != null, "Empty deltaOperations");

        String clientID = authenticator.getClientId();
        LOG.debug("Search for clientID {}", clientID);

        ClientIdentifier clientIdentifier = clientLookupMap.get(authenticator.getClientId());
        checkNotNull(clientIdentifier);

        // verify authenticator
        checkArgument(cryptoUtils.verify(authenticator.getSignature(),
                ImmutableList.of("AUTH",
                        cryptoUtils.generateOperationUUID(operation, operation.getClientId()),
                        operation.getClientId(),
                        String.valueOf(operation.getSequenceNumber()),
                        authenticator.getValue()),
                clientIdentifier
        ), "Verification failed: Last authenticator seems to be corrupted: %s", authenticator);

        updateSequenceNumber(operation.getSequenceNumber());

        return operation.getSequenceNumber();
    }

    private long updateSequenceNumber(long sequenceNumber) {
        return lastSequenceNumber.updateAndGet(current -> max(current, sequenceNumber));
    }

    /**
     * @return last operation of pending list
     */
    private void checkPendingOperations(final List<Operation> pendingOperations) {
        // verify that at least the current operation is included
        checkState(pendingOperations.size() > 0, "pending List is empty");

        // verify operation
        Operation operation = pendingOperations.stream().map(o -> {
            LOG.trace("CheckPendingOp: {}", TextFormat.printToString(o));
            checkState(cryptoUtils.extendHashChain(o, hashChain), "Hash chain verification failed for operation %s", o);

            if (o.getStatus() == UNKNOWN) {
                checkState(cryptoUtils.verify(o.getSignature(),
                        ImmutableList.of("INVOKE",
                                cryptoUtils.generateOperationUUID(o, o.getClientId()),
                                o.getClientId()),
                        clientLookupMap.get(o.getClientId())
                ), "Verification failed: Invoke Operation seems to be corrupted: %s", o);
            } else {
                checkState(cryptoUtils.verify(o.getSignature(),
                        ImmutableList.of("COMMIT",
                                cryptoUtils.generateOperationUUID(o, o.getClientId()),
                                o.getClientId(),
                                String.valueOf(o.getSequenceNumber()),
                                o.getStatus().toString(),
                                hashChain.get(o.getSequenceNumber())),
                        clientLookupMap.get(o.getClientId())
                ), "Verification failed: Invoke Operation seems to be corrupted: %s", o);
            }
            return o;
        }).reduce((prev, next) -> next).get();
    }

    @Override
    public Init invokeInit() {
        LOG.debug("Process init invocation");

        Operation op = Operation.newBuilder().setOpCode(GET)
                .setKey("Init")
                .setSequenceNumber(1)
                .setClientId(getClientId())
                .setStatus(SUCCESS)
                .build();

        op = Operation.newBuilder().mergeFrom(op).build();
        checkArgument(cryptoUtils.extendHashChain(op, hashChain), "ExtendHashChain");

        String signature = cryptoUtils.sign(ImmutableList.of("COMMIT",
                        cryptoUtils.generateOperationUUID(op, op.getClientId()),
                        getClientId(),
                        String.valueOf(op.getSequenceNumber()),
                        op.getStatus().toString(),
                        hashChain.get(op.getSequenceNumber())),
                clientIdentifier
        );
        op = Operation.newBuilder().mergeFrom(op).setSignature(signature).build();

        // produce authenticator for empty state
        Authenticator authenticator = Authenticator.newBuilder()
                .setValue(cryptoUtils.hash(ImmutableList.of()))
                .setClientId(getClientId())
                .setSequenceNumber(1)
                .build();

        final String authenticatorSignature = cryptoUtils.sign(
                ImmutableList.of("AUTH",
                        cryptoUtils.generateOperationUUID(op, op.getClientId()),
                        op.getClientId(),
                        String.valueOf(1),
                        authenticator.getValue()),
                clientIdentifier
        );

        authenticator = Authenticator.newBuilder()
                .mergeFrom(authenticator)
                .setSignature(authenticatorSignature).build();

        return Init.newBuilder()
                .setOperation(op)
                .setAuthenticator(authenticator)
                .build();
    }

    public String getClientId() {
        return clientIdentifier.getClientId();
    }
}
