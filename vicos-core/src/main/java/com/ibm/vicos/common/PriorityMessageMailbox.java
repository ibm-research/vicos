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

import com.typesafe.config.Config;

import java.util.Comparator;

import akka.actor.ActorSystem;
import akka.dispatch.Envelope;
import akka.dispatch.PriorityGenerator;
import akka.dispatch.UnboundedStablePriorityMailbox;

public class PriorityMessageMailbox extends UnboundedStablePriorityMailbox {

    final static int INIT_CAPACITY = 32;

    final static Comparator<Envelope> CMP = new PriorityGenerator() {
        @Override
        public int gen(Object msg) {
            if (msg instanceof Messages.Message) {
                switch (((Messages.Message) msg).getType()) {
                    case INVOKE:
                        return 2;
                    case REPLY:
                        return 2;
                    case UPDATE_AUTH:
                        return 1;
                    case COMMIT:
                        return 1;
                    case COMMIT_AUTH:
                        return 0;
                    case INIT:
                        return 0;
                    default:
                        return 4;
                }
            } else {
                return 4;
            }
        }
    };

    public PriorityMessageMailbox(ActorSystem.Settings settings, Config config) {
        super(CMP, INIT_CAPACITY);
    }

    @Override
    public Comparator<Envelope> cmp() {
        return CMP;
    }

    @Override
    public int initialCapacity() {
        return INIT_CAPACITY;
    }
}
