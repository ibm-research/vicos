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

package com.ibm.vicos.server.branding;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.shell.plugin.support.DefaultBannerProvider;
import org.springframework.shell.support.util.OsUtils;
import org.springframework.stereotype.Component;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class BannerProvider extends DefaultBannerProvider {
    public String getBanner() {

        // http://patorjk.com/software/taag/#p=display&h=1&v=1&f=Big%20Money-nw&t=VICOS

        final StringBuilder builder = new StringBuilder();
        builder.append("\n");
        builder.append("$$\\    $$\\ $$$$$$\\  $$$$$$\\   $$$$$$\\   $$$$$$\\                       ").append(OsUtils.LINE_SEPARATOR);
        builder.append("$$ |   $$ |\\_$$  _|$$  __$$\\ $$  __$$\\ $$  __$$\\                        ").append(OsUtils.LINE_SEPARATOR);
        builder.append("$$ |   $$ |  $$ |  $$ /  \\__|$$ /  $$ |$$ /  \\__|                         ").append(OsUtils.LINE_SEPARATOR);
        builder.append("\\$$\\  $$  |  $$ |  $$ |      $$ |  $$ |\\$$$$$$\\                         ").append(OsUtils.LINE_SEPARATOR);
        builder.append(" \\$$\\$$  /   $$ |  $$ |      $$ |  $$ | \\____$$\\                        ").append(OsUtils.LINE_SEPARATOR);
        builder.append("  \\$$$  /    $$ |  $$ |  $$\\ $$ |  $$ |$$\\   $$ |                        ").append(OsUtils.LINE_SEPARATOR);
        builder.append("   \\$  /   $$$$$$\\ \\$$$$$$  | $$$$$$  |\\$$$$$$  |                       ").append(OsUtils.LINE_SEPARATOR);
        builder.append("    \\_/    \\______| \\______/  \\______/  \\______/                       ").append(OsUtils.LINE_SEPARATOR);
        builder.append("\n");
        builder.append("Copyright IBM Corp. 2016 All Rights Reserved.").append(OsUtils.LINE_SEPARATOR);
        builder.append("Version: ").append(this.getVersion()).append(OsUtils.LINE_SEPARATOR);

        return builder.toString();
    }

    public String getVersion() {
        return "0.1";
    }

    public String getWelcomeMessage() {
        return "Verification of Integrity and Consistency for Cloud Object Storage - Server";
    }

}
