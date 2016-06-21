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

import com.google.auto.value.AutoValue;

import com.ibm.vicos.common.ADS.Authenticator;
import com.ibm.vicos.common.ADS.AuxiliaryData;

import java.util.List;

import static com.ibm.vicos.common.Operations.Operation;

/**
 * Created by bur on 22/09/15.
 */
public abstract class OperationProcessor<S extends State> {

    public abstract QueryResult query(S state, List<Operation> operation);

    public abstract AuthExecResult authexec(List<Operation> operation, Authenticator authenticator, Operations.Result result, AuxiliaryData auxiliaryData);

    public abstract S refresh(S state, Operation operation, AuxiliaryData auxiliaryData);

    public abstract boolean isCompatible(List<Operation> listOthers, Operation currentOperation);

    public abstract boolean isCompatible(Operation otherOperation, Operation currentOperation);

    public abstract boolean isUpdateOperation(Operation operation);

    @AutoValue
    public static abstract class QueryResult {

        public static Builder builder() {
            return new AutoValue_OperationProcessor_QueryResult.Builder();
        }

        public abstract Operations.Result getResult();

        public abstract AuxiliaryData getAuxiliaryData();

        @AutoValue.Builder
        public abstract static class Builder {

            public abstract Builder setResult(Operations.Result result);

            public abstract Builder setAuxiliaryData(AuxiliaryData getAuxiliaryData);

            public abstract QueryResult build();
        }
    }

    @AutoValue
    public static abstract class AuthExecResult {

        public static Builder builder() {
            return new AutoValue_OperationProcessor_AuthExecResult.Builder();
        }

        public abstract Authenticator getAuthenticator();

        public abstract AuxiliaryData getAuxiliaryData();

        public abstract boolean isValid();

        @AutoValue.Builder
        public abstract static class Builder {

            public abstract Builder setAuthenticator(Authenticator authenticator);

            public abstract Builder setAuxiliaryData(AuxiliaryData getAuxiliaryData);

            public abstract Builder setValid(boolean valid);

            public abstract AuthExecResult build();
        }
    }
}
