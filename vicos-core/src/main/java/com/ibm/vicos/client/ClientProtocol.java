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

import com.ibm.vicos.common.Messages.CommitAuth;
import com.ibm.vicos.common.Messages.CommitResult;
import com.ibm.vicos.common.Messages.Init;
import com.ibm.vicos.common.Messages.Invoke;
import com.ibm.vicos.common.Messages.Reply;
import com.ibm.vicos.common.Messages.UpdateAuth;
import com.ibm.vicos.common.Operations.Operation;
import com.ibm.vicos.exceptions.IntegrityException;

/**
 * Created by bur on 25/09/15.
 */
public interface ClientProtocol {

    Invoke invokeOperation(Operation operation);

    CommitResult handleReply(final Reply message) throws IntegrityException;

    CommitAuth handleUpdateAuth(final UpdateAuth message) throws IntegrityException;

    Init invokeInit();

    String getClientId();
}
