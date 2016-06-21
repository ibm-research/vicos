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

package com.ibm.vicos.common.asl;

import com.google.common.collect.Maps;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.Test;

import java.util.SortedMap;

public class AuthenticatedSkipListTest {
    private static final Logger LOG = LoggerFactory.getLogger(AuthenticatedSkipListTest.class);

    @Test
    public void testCreate() throws Exception {

        SortedMap<Integer, String> m = Maps.newTreeMap();

        m.put(1, "Hallo");
        m.put(2, "Welt");
        m.put(3, "Danke");
        m.put(5, "Moin");

        MyAuthenticatedSkipList testList = new MyAuthenticatedSkipList<>(m);

        LOG.debug("Get 1: {}", testList.get(1));
        LOG.debug("Get 2: {}", testList.get(2));
        LOG.debug("Get 3: {}", testList.get(3));
        LOG.debug("Get 4: {}", testList.get(4));
        LOG.debug("Get 5: {}", testList.get(5));

//        LOG.debug("Liste: {}", testList);
//
//        LOG.debug("4 exists now {}", testList.contains(4));
//        testList.put(4, "Haha");
//        LOG.debug("Get 4: {}", testList.get(4));
//        LOG.debug("4 exists now {}", testList.contains(4));
//        testList.put(4, "DingDOng");
//        LOG.debug("Get 4: {}", testList.get(4));
//        LOG.debug("Get 5: {}", testList.get(5));
//
//        LOG.debug("Liste: {}", testList);
//        testList.put(100, "ju");
////        testList.put(0, "zero");
//
//        LOG.debug("Liste: {}", testList);
//
//        testList.remove(3);
//        LOG.debug("3 exists now {}", testList.contains(3));
//
//
//        LOG.debug("Liste: {}", testList);
//
//        LOG.debug("pre: {}", testList.getPredecessor(100));
    }
}
