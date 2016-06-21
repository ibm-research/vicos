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

package com.ibm.vicos.server;

import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import com.google.inject.name.Named;

import com.ibm.vicos.common.ADS.Authenticator;
import com.ibm.vicos.common.Messages.Commit;
import com.ibm.vicos.common.Messages.CommitAuth;
import com.ibm.vicos.common.Messages.Init;
import com.ibm.vicos.common.Messages.Invoke;
import com.ibm.vicos.common.Messages.Reply;
import com.ibm.vicos.common.Messages.UpdateAuth;
import com.ibm.vicos.common.OperationProcessor;
import com.ibm.vicos.common.State;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.List;
import java.util.NavigableMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.ibm.vicos.common.OperationProcessor.QueryResult;
import static com.ibm.vicos.common.Operations.Operation;
import static com.ibm.vicos.common.Operations.Status.ABORT;
import static com.ibm.vicos.common.Operations.Status.SUCCESS;
import static com.ibm.vicos.common.Operations.Status.UNKNOWN;

/**
 * Created by bur on 22/09/15.
 */
public class ServerProtocolImpl implements ServerProtocol {

    private static final Logger LOG = LoggerFactory.getLogger(ServerProtocolImpl.class);
    private final AtomicLong lastInvokedSeqNo;
    private final AtomicLong lastAppliedSeqNo;
    private final NavigableMap<Long, Authenticator> authenticators; // A
    private final NavigableMap<Long, Operation> operations; // I
    private final OperationProcessor operationProcessor;
    private final AtomicBoolean waitingForCommitAuth = new AtomicBoolean(false);
    private final Integer pendingListMaxLength;
    private State state;

    @Inject
    public ServerProtocolImpl(@Named("lastInvokedSeqNo") AtomicLong t,
                              @Named("lastAppliedSeqNo") AtomicLong b,
                              State dataStructure,
                              OperationProcessor operationProcessor,
                              NavigableMap<Long, Operation> operations,
                              NavigableMap<Long, Authenticator> authenticators,
                              @Named("pendingListMaxLength") Integer pendingListMaxLength) {
        this.lastInvokedSeqNo = checkNotNull(t, "lastInvokedSeqNo");
        this.lastAppliedSeqNo = checkNotNull(b, "lastAppliedSeqNo");
        this.state = checkNotNull(dataStructure, "state");
        this.operationProcessor = checkNotNull(operationProcessor, "operationProcessor");
        this.operations = checkNotNull(operations, "appliedOperations");
        this.authenticators = checkNotNull(authenticators, "authenticators");
        this.pendingListMaxLength = checkNotNull(pendingListMaxLength, "pendingListMaxLength");
    }

    @Override
    public boolean readyForNewInvocation() {
        return lastInvokedSeqNo.get() - lastAppliedSeqNo.get() <= pendingListMaxLength;
    }

    /**
     * @param message
     * @return
     */
    @SuppressWarnings("unchecked")
    public synchronized Reply handleInvoke(final Invoke message) {
        LOG.debug("Process Invoke message");
        LOG.trace("Message {}", message);


        Operation operation = message.getOperation();
        final String clientId = operation.getClientId();

        // clientsLastSequenceNumber could be initial value (0)
        long clientsLastSequenceNumber = message.getLastSequenceNumber();

        final long newSequenceNumber = lastInvokedSeqNo.incrementAndGet();
        final long lastAppliedSequenceNumber = this.lastAppliedSeqNo.get();
        checkState(newSequenceNumber >= lastAppliedSequenceNumber, "nextSeq %s >= lastAppliedSeq %s", newSequenceNumber, lastAppliedSequenceNumber);


        // store sequence number in operation
        operations.put(newSequenceNumber, Operation.newBuilder()
                .mergeFrom(operation)
                .setSequenceNumber(newSequenceNumber)
                .build());

        Collection<Operation> deltaOperations = operations.subMap(clientsLastSequenceNumber, true, lastAppliedSequenceNumber, true).values();
        LOG.debug("deltaOps size: {}", deltaOperations.size());
        LOG.trace("newSeq:{} lastSeq:{} clientsLastSeq: {}", newSequenceNumber, lastAppliedSequenceNumber, clientsLastSequenceNumber);
        checkState((deltaOperations.size() >= 1)
                        && (deltaOperations.size() <= lastAppliedSequenceNumber - clientsLastSequenceNumber + 1),
                "delta operation can not be empty, it always contains last applied operation");

        Collection<Operation> pendingOperations = operations.subMap(lastAppliedSequenceNumber + 1, true, newSequenceNumber, false).values();
        LOG.debug("pendingOps size: {}", pendingOperations.size());

        // filter operations by user
        // and omit aborted operations (this is a protocol extension)
        List<Operation> successByOthers = pendingOperations.stream()
                .filter(o -> !o.getClientId().equals(clientId))
                .filter(o -> o.getStatus() != SUCCESS)
                .collect(Collectors.toList());

        // filter operations by user
        // and omit aborted operations (this is a protocol extension)
        List<Operation> pendingByClient = pendingOperations.stream()
                .filter(o -> o.getClientId().equals(clientId))
                .filter(o -> o.getStatus() != ABORT)
                .collect(Collectors.toList());

        successByOthers.addAll(pendingByClient);
        successByOthers.add(operation);

        // extract new partial state according to all pending operation by the user
        QueryResult queryResult = operationProcessor.query(state, successByOthers);

        return Reply.newBuilder()
                .addAllDeltaOperations(deltaOperations)
                .setLastAuthenticator(authenticators.get(lastAppliedSequenceNumber))
                .addAllPendingOperations(pendingOperations)
                .setAssignedSequenceNumber(newSequenceNumber)
                .setResult(queryResult.getResult())
                .setAuxiliaryData(queryResult.getAuxiliaryData())
                .build();
    }

    /**
     * @param message
     */
    public synchronized void handleCommit(final Commit message) {
        LOG.debug("Process Commit message");
        LOG.trace("Message {}", message);

        final long seqNo = message.getSequenceNumber();
        final Operation operation = operations.get(seqNo);

        checkNotNull(operation, "Unknown operation with seqNo %s", seqNo);
        checkArgument(message.getStatus() != UNKNOWN, "Wrong message status %s", message);

        final Operation newOperation = Operation.newBuilder()
                .mergeFrom(operation)
                .setSignature(message.getSignature())
                .setStatus(message.getStatus())
                .build();

        operations.put(seqNo, newOperation);
    }

    /**
     * @return
     */
    @SuppressWarnings("unchecked")
    public synchronized UpdateAuth uponNextCommittedOperationAvailable() {
        if (waitingForCommitAuth.get()) {
            LOG.debug("Waiting for authenticator commitment");
            return null;
        }

        final long lastAppliedSeqNo = this.lastAppliedSeqNo.get();
        final long nextSeqNum = lastAppliedSeqNo + 1;

        Operation operation = operations.get(nextSeqNum);
        if (operation == null) {
            LOG.trace("Next operation not available {}", nextSeqNum);
            return null;
        }

        if (operation.getStatus() == UNKNOWN) {
            LOG.trace("Operation still needs to be committed", nextSeqNum);
            return null;
        }

        if (authenticators.containsKey(nextSeqNum)) {
            LOG.trace("Authenticator already available {}", nextSeqNum);
            return null;
        }
        LOG.debug("Process next committed operation [seq: {}]", nextSeqNum);

        final Authenticator lastAuthenticator = checkNotNull(authenticators.get(lastAppliedSeqNo), "lastAuthenticator");
        final Operation lastAppliedOperation = checkNotNull(operations.get(lastAppliedSeqNo), "lastAppliedOperation");

        UpdateAuth.Builder updateAuth = UpdateAuth.newBuilder()
                .setOperation(operation)
                .setLastOperation(lastAppliedOperation)
                .setLastAuthenticator(lastAuthenticator);

        // extract new partial state according to that operation
        if (operation.getStatus().equals(SUCCESS)) {
            QueryResult queryResult = operationProcessor.query(state, ImmutableList.of(operation));
            updateAuth.setResult(queryResult.getResult()).setAuxiliaryData(queryResult.getAuxiliaryData());
        }

        waitingForCommitAuth.set(true);
        return updateAuth.build();
    }

    /**
     * @param message
     */
    @SuppressWarnings("unchecked")
    public synchronized void handleCommitAuth(final CommitAuth message) {
        LOG.debug("Process CommitAuth message", message);
        LOG.trace("Message {}", message);
        Authenticator authenticator = message.getAuthenticator();

        long sequenceNumber = authenticator.getSequenceNumber();
        lastAppliedSeqNo.incrementAndGet();

        // save the new authenticator
        authenticators.put(sequenceNumber, authenticator);

        // executes only if operation is compatible
        final Operation operation = operations.get(sequenceNumber);
        if (operation.getStatus() == SUCCESS) {
            state = operationProcessor.refresh(state, operation, message.getAuxiliaryData());
        }

        waitingForCommitAuth.set(false);

        LOG.debug("Server state has been updated! t: {} b: {}", lastInvokedSeqNo.get(), lastAppliedSeqNo.get());
        LOG.trace("lastInvoked: {}", operations.get(lastInvokedSeqNo.get()));
        LOG.trace("lastApplied: {}", operations.get(lastAppliedSeqNo.get()));
        LOG.trace("lastAuthenticator {}", authenticators.get(lastAppliedSeqNo.get()));
    }

    /**
     * @param message
     */
    public synchronized void handleInit(final Init message) {
        LOG.debug("Process Init message");
        LOG.trace("Message {}", message);

        final Operation operation = message.getOperation();
        final Authenticator authenticator = message.getAuthenticator();
        final long sequenceNumber = operation.getSequenceNumber();

        waitingForCommitAuth.set(false);
        lastInvokedSeqNo.set(sequenceNumber);
        lastAppliedSeqNo.set(sequenceNumber);

        operations.clear();
        authenticators.clear();

        operations.put(sequenceNumber, operation);
        authenticators.put(sequenceNumber, authenticator);

        state.init();

        LOG.info("Server has been initialized! t: {} b: {}", lastInvokedSeqNo.get(), lastAppliedSeqNo.get());
        LOG.trace("lastInvoked: {}", operations.get(lastInvokedSeqNo.get()));
        LOG.trace("lastApplied: {}", operations.get(lastAppliedSeqNo.get()));
        LOG.trace("lastAuthenticator {}", authenticators.get(lastAppliedSeqNo.get()));
    }
}
