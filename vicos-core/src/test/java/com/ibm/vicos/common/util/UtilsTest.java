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

package com.ibm.vicos.common.util;

import com.google.common.base.Charsets;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * Created by bur on 23/02/16.
 */
public class UtilsTest {

    private static final Logger LOG = LoggerFactory.getLogger(UtilsTest.class);

    @DataProvider(name = "test1")
    public static Object[][] primeNumbers() {
        return new Object[][] {
                {"myobject1"},
                {"myobject14"},
                {"myobject8"},
                {"myobject123"}
        };
    }


    @Test(dataProvider = "test1")
    public void testSha1(String input) throws Exception {
        Hasher hasher = Hashing.sha1().newHasher();
        hasher.putString(input, Charsets.UTF_8);
        String output = hasher.hash().toString();
        LOG.info("testSha1: Output: {}", output);
    }


    @Test(dataProvider = "test1")
    public void testMurmur3_32(String input) throws Exception {
        String output = Utils.hashMurmur3_32(input);
        LOG.info("testMurmur3_32: Input: {} Output: {}", input, output);
    }

}
