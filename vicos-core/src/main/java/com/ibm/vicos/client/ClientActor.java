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

import com.google.common.collect.Queues;
import com.google.inject.Inject;
import com.google.inject.name.Named;

import com.ibm.vicos.common.Messages;
import com.ibm.vicos.common.Messages.Message;
import com.ibm.vicos.common.Operations;
import com.ibm.vicos.common.Operations.Operation;
import com.ibm.vicos.common.Operations.Result;
import com.ibm.vicos.exceptions.FlowControlException;
import com.ibm.vicos.exceptions.IntegrityException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Queue;

import akka.actor.ActorRef;
import akka.actor.ActorSelection;
import akka.actor.Status.Failure;
import akka.actor.UntypedActor;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.ibm.vicos.common.Messages.Message.Type.COMMIT;
import static com.ibm.vicos.common.Messages.Message.Type.COMMIT_AUTH;
import static com.ibm.vicos.common.Messages.Message.Type.INIT;
import static com.ibm.vicos.common.Messages.Message.Type.INVOKE;
import static com.ibm.vicos.common.Operations.Status.SUCCESS;

/**
 * Created by bur on 22/09/15.
 */
public class ClientActor extends UntypedActor {

    private static final Logger LOG = LoggerFactory.getLogger(ClientActor.class);
    private final ClientProtocol clientProtocol;
    private State state = State.IDLE;
    private ActorRef invocationCallback;
    private ActorSelection remoteServer;
    private Queue<Operation> uncompletedOperations = Queues.newLinkedBlockingQueue();

    @Inject
    public ClientActor(ClientProtocol clientProtocol,
                       @Named("remoteServerSelection") ActorSelection remoteServer) {
        this.clientProtocol = checkNotNull(clientProtocol, "clientProtocol");
        this.remoteServer = checkNotNull(remoteServer, "remoteServer");
    }

    @Override
    public void preStart() throws Exception {
        super.preStart();
        LOG.info("Boot actor: {}", getSelf().toString());
    }

    @Override
    public void postStop() throws Exception {
        super.postStop();
        LOG.info("Stop actor: {}", getSelf().toString());
    }

    public State getState() {
        return state;
    }

    public void setState(State state) {
        this.state = state;
    }

    private void processMessage(final Message message) {
        switch (message.getType()) {
            case REPLY: {
                processReplyMessage(message.getReply());
                break;
            }
            case UPDATE_AUTH: {
                processUpdateAuthMessage(message.getUpdateAuth());
                break;
            }
            default: {
                unhandled(message);
            }
        }
    }

    private void processReplyMessage(Messages.Reply message) {
        Result result;
        try {
            Messages.CommitResult output = clientProtocol.handleReply(message);

            Message msg = Message.newBuilder()
                    .setType(COMMIT)
                    .setCommit(output.getCommit())
                    .setSource(clientProtocol.getClientId())
                    .build();
            sendToServer(msg);
            result = output.getResult();
        } catch (IntegrityException e) {
            result = Result.newBuilder().setResultType(Operations.ResultType.INTEGRITY_VIOLATION).build();
        }

        returnResult(result);
    }

    private void processUpdateAuthMessage(Messages.UpdateAuth message) {
        Message msg = Message.newBuilder()
                .setType(COMMIT_AUTH)
                .setCommitAuth(clientProtocol.handleUpdateAuth(message))
                .setSource(clientProtocol.getClientId())
                .build();
        sendToServer(msg);
        uncompletedOperations.poll();
    }

    private void processOperation(final Operation operation) {
        registerInvocationCallback();
        final Message.Builder msg = Message.newBuilder().setSource(clientProtocol.getClientId());
        switch (operation.getOpCode()) {
            case INIT: {
                sendToServer(msg.setType(INIT)
                        .setInit(clientProtocol.invokeInit())
                        .build());
                returnResult(Result.newBuilder().setStatus(SUCCESS).build());
                unregisterInvocationCallback();
                break;
            }
            default: {
                uncompletedOperations.add(operation);
                sendToServer(msg.setType(INVOKE)
                        .setInvoke(clientProtocol.invokeOperation(operation))
                        .build());
                break;
            }
        }

    }

    private ActorRef registerInvocationCallback() {
        setState(State.ACTIVE);
        invocationCallback = sender();
        return invocationCallback;
    }

    private ActorRef unregisterInvocationCallback() {
        setState(State.IDLE);
        ActorRef ref = invocationCallback;
        invocationCallback = null;
        return ref;
    }

    @Override
    public void onReceive(final Object message) throws Exception {
        if (message instanceof Message) {
            processMessage((Message) message);
        } else if (message instanceof Operation) {
            if (getState() == State.IDLE) {
                processOperation((Operation) message);
            } else {
                LOG.warn("There is currently another operation being processed.");
                getSender().tell(new Failure(new FlowControlException("There is currently another operation being processed.")), getSelf());
            }
        } else {
            unhandled(message);
        }
    }

    @Override
    public void unhandled(final Object message) {
        LOG.warn("Received unknown message from {} Message dropped", getSender());
        LOG.debug("Message: ", message);
        super.unhandled(message);
    }

    /**
     * Sends message to server
     */
    private void sendToServer(Message message) {
        remoteServer.tell(message, getSelf());
    }

    /**
     * Returns operation result to application
     */
    private void returnResult(Result result) {
        checkNotNull(result, "result must not be null");
        ActorRef ref = checkNotNull(unregisterInvocationCallback(), "callback is undefined");
        ref.tell(result, getSelf());
    }

    protected enum State {
        IDLE,
        ACTIVE
    }
}
