/*
 *  Copyright (c) 2020 Gayan Perera
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *
 * Contributors:
 *     Gayan Perera <gayanper@gmail.com> - initial API and implementation
 */

package org.gap.ijplugins.spring.tools.lang;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.gap.ijplugins.spring.tools.util.Throwables;
import org.jetbrains.annotations.NotNull;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

public class StsLanguageValidator {
    private static final String NS = "http://www.springframework.org/schema/beans";

    private static final Logger LOGGER = Logger.getInstance(StsLanguageValidator.class);

    public boolean isXmlSpringBeanFile(@NotNull VirtualFile virtualFile, @NotNull Project project) {
        if ("xml".equalsIgnoreCase(virtualFile.getExtension())) {
            XMLInputFactory inputFactory = XMLInputFactory.newInstance();
            inputFactory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, Boolean.FALSE);
            XMLStreamReader xmlStreamReader = null;
            try (InputStream inputStream = virtualFile.getInputStream()) {
                xmlStreamReader = inputFactory.createXMLStreamReader(inputStream);
                while (xmlStreamReader.hasNext()) {
                    int elementType = xmlStreamReader.next();
                    if (elementType == XMLStreamConstants.START_ELEMENT) {
                        if (NS.equals(xmlStreamReader.getNamespaceURI())) {
                            return true;
                        }
                        break;
                    }
                }
            } catch (IOException | XMLStreamException e) {
                LOGGER.warn("Failed to process xml file", e);
            } finally {
                Optional.ofNullable(xmlStreamReader)
                        .ifPresent(Throwables.fromThrowable(XMLStreamReader::close, LOGGER::warn));
            }
        }
        return false;
    }
}
