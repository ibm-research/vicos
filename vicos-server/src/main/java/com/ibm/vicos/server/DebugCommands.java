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

import com.ibm.vicos.common.KeyNotFoundException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.shell.core.CommandMarker;
import org.springframework.shell.core.annotation.CliCommand;
import org.springframework.shell.core.annotation.CliOption;
import org.springframework.shell.support.util.OsUtils;
import org.springframework.stereotype.Component;

import static com.ibm.vicos.common.util.Utils.map2String;

@Component
public class DebugCommands implements CommandMarker {

    @Autowired
    private VICOSServer vicosServer;

    @CliCommand(value = "get", help = "Gets the value for a key")
    public String get(
            @CliOption(key = {"key"}, mandatory = true, help = "Object identifier")
            final String key) throws KeyNotFoundException {
        final StringBuilder builder = new StringBuilder();
        builder.append("Fetching value for '").append(key).append("'").append(OsUtils.LINE_SEPARATOR);
        builder.append("Result: ").append(vicosServer.getState().get(key)).append(OsUtils.LINE_SEPARATOR);

        return builder.toString();
    }

    @CliCommand(value = "put", help = "Stores a key-value pair")
    public String put(
            @CliOption(key = {"key"}, mandatory = true, help = "Object identifier") final String key,
            @CliOption(key = {"value"}, mandatory = true, help = "Object identifier") final String value) {
        vicosServer.getState().put(key, value);
        return "Store key: " + key + " value: " + value;
    }

    @CliCommand(value = "del", help = "Deletes a value-key pair")
    public String del(
            @CliOption(key = {"key"}, mandatory = true, help = "Object identifier") final String key) throws KeyNotFoundException {
        vicosServer.getState().remove(key);
        return "Del key: " + key;
    }

    @CliCommand(value = "list", help = "Lists all keys")
    public String list() {
        final StringBuilder builder = new StringBuilder();
        builder.append("List all keys:").append(OsUtils.LINE_SEPARATOR);

        for (String item : vicosServer.getState().list()) {
            builder.append(item).append(OsUtils.LINE_SEPARATOR);
        }
        return builder.toString();
    }

    @CliCommand(value = "status", help = "Shows current server status")
    public String showStatus() {
        return "Server status:" + OsUtils.LINE_SEPARATOR + vicosServer.getStatus();
    }

    @CliCommand(value = "authenticators", help = "Shows all authenticators")
    public String showAuthenticators() {
        return "Authenticators:" + OsUtils.LINE_SEPARATOR
                + map2String(vicosServer.getAuthenticator());
    }

    @CliCommand(value = "history", help = "Shows history of all operations invoked so far")
    public String showInvokedOperations() {
        return "Invoked operations:" + OsUtils.LINE_SEPARATOR
                + map2String(vicosServer.getOperations());
    }


}
