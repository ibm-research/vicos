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

import com.google.common.io.ByteStreams;

import com.typesafe.config.ConfigFactory;

import org.springframework.shell.core.CommandMarker;
import org.springframework.shell.core.annotation.CliCommand;
import org.springframework.shell.core.annotation.CliOption;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.FileSystems;
import java.nio.file.Path;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import static com.google.common.base.Preconditions.checkState;

/**
 * Created by bur on 18/09/15.
 */
@Component
public class ClientCommand implements CommandMarker {

    private VICOSBlobStore storage;

    @PostConstruct
    public void init() {
        try {
            storage = new VICOSBlobStoreImpl();
            storage.init(ConfigFactory.load());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @PreDestroy
    public void dispose() {
        storage.dispose();
    }

    @CliCommand(value = "init", help = "Initializes the remote VICOS server (for testing only)")
    public String initStorage() {
        try {
            final String init_container = "_init_1_init_";
            storage.createContainer(init_container);
        } catch (Exception e) {
            e.printStackTrace();
            return "Error while init server";
        }
        return "Server successfully initialized";
    }

    @CliCommand(value = "getblob", help = "Downloads a blob to destination path")
    public String getBlob(
            @CliOption(key = {"container"}, mandatory = true, help = "container name") final String container,
            @CliOption(key = {"blob"}, mandatory = true, help = "blob name") final String blob,
            @CliOption(key = {"dest_path"}, mandatory = true, help = "destination path") final String path) {
        Path fullPath = FileSystems.getDefault().getPath(path, blob);
        fullPath.getParent().toFile().mkdirs();

        try {
            InputStream inputStream = storage.getObject(container, blob);
            OutputStream outputStream = new FileOutputStream(fullPath.toFile());

            long total = ByteStreams.copy(inputStream, outputStream);

            checkState(fullPath.toFile().exists(), "Could not create file");
            return "Successfully downloaded " + fullPath.toString() + " [" + total + " bytes]";
        } catch (Exception e) {
            e.printStackTrace();
            return "Error while getting the " + container + "/" + blob;
        }
    }

    @CliCommand(value = "createcontainer", help = "Creates a container")
    public String createContainer(
            @CliOption(key = {"container"}, mandatory = true, help = "container name") final String container) {
        try {
            storage.createContainer(container);
        } catch (Exception e) {
            e.printStackTrace();
            return "Error while creating container";
        }
        return "Container successfully created";
    }

    @CliCommand(value = "putblob", help = "Uploads a blob from source path")
    public String putBlob(
            @CliOption(key = {"container"}, mandatory = true, help = "container name") final String container,
            @CliOption(key = {"src"}, mandatory = true, help = "source file") final String src) {
        Path fullPath = FileSystems.getDefault().getPath(src);
        final File file = fullPath.toFile();
        try {
            InputStream data = new FileInputStream(file);
            storage.createObject(container, file.getName(), data, file.length());
        } catch (FileNotFoundException e) {
            return src + " does not exists";
        } catch (Exception e) {
            e.printStackTrace();
            return "Error while creating Object";
        }
        return "Successfully uploaded " + container + "/" + file.getName();
    }

    @CliCommand(value = "deletecontainer", help = "Deletes a container")
    public String deleteContainer(
            @CliOption(key = {"container"}, mandatory = true, help = "container name") final String container) {
        try {
            storage.deleteContainer(container);
        } catch (Exception e) {
            e.printStackTrace();
            return "Error while deleting container";
        }
        return "Container successfully deleted";
    }

    @CliCommand(value = "deleteblob", help = "Deletes a blob")
    public String deleteObject(
            @CliOption(key = {"container"}, mandatory = true, help = "container name") final String container,
            @CliOption(key = {"blob"}, mandatory = true, help = "blob name") final String blob) {
        try {
            storage.deleteBlob(container, blob);
        } catch (Exception e) {
            e.printStackTrace();
            return "Error while deleting object";
        }
        return "Object successfully deleted";
    }
}
