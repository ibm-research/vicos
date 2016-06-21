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

import com.ibm.vicos.common.Messages;
import com.ibm.vicos.common.Messages.Commit;
import com.ibm.vicos.common.Messages.CommitAuth;
import com.ibm.vicos.common.Messages.Init;
import com.ibm.vicos.common.Messages.Invoke;
import com.ibm.vicos.common.Messages.Reply;

/**
 * Created by bur on 28/09/15.
 */
public interface ServerProtocol {

    Reply handleInvoke(final Invoke message);

    void handleCommit(final Commit message);

    Messages.UpdateAuth uponNextCommittedOperationAvailable();

    void handleCommitAuth(final CommitAuth message);

    void handleInit(final Init message);

    boolean readyForNewInvocation();
}
