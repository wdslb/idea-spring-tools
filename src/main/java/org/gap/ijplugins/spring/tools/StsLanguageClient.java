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

package org.gap.ijplugins.spring.tools;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMember;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.util.ClassUtil;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.MarkupContent;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.jsonrpc.messages.Tuple;
import org.gap.ijplugins.spring.tools.highlight.HighlightProcessor;
import org.gap.ijplugins.spring.tools.highlight.InlayHighlightProcessor;
import org.gap.ijplugins.spring.tools.highlight.RangeHighlightProcessor;
import org.gap.ijplugins.spring.tools.java.ClasspathListener;
import org.gap.ijplugins.spring.tools.java.JavaUtils;
import org.gap.ijplugins.spring.tools.java.PsiResolver;
import org.gap.ijplugins.spring.tools.java.TypeDescriptorProvider;
import org.gap.ijplugins.spring.tools.java.TypeProvider;
import org.springframework.ide.vscode.commons.protocol.CursorMovement;
import org.springframework.ide.vscode.commons.protocol.HighlightParams;
import org.springframework.ide.vscode.commons.protocol.STS4LanguageClient;
import org.springframework.ide.vscode.commons.protocol.java.ClasspathListenerParams;
import org.springframework.ide.vscode.commons.protocol.java.JavaCodeCompleteData;
import org.springframework.ide.vscode.commons.protocol.java.JavaCodeCompleteParams;
import org.springframework.ide.vscode.commons.protocol.java.JavaDataParams;
import org.springframework.ide.vscode.commons.protocol.java.JavaSearchParams;
import org.springframework.ide.vscode.commons.protocol.java.JavaTypeHierarchyParams;
import org.springframework.ide.vscode.commons.protocol.java.TypeData;
import org.springframework.ide.vscode.commons.protocol.java.TypeDescriptorData;
import org.wso2.lsp4intellij.client.ClientContext;
import org.wso2.lsp4intellij.client.DefaultLanguageClient;
import org.wso2.lsp4intellij.editor.EditorEventManager;
import org.wso2.lsp4intellij.utils.FileUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.Collectors;

import static org.gap.ijplugins.spring.tools.ApplicationUtils.runReadAction;

class StsLanguageClient extends DefaultLanguageClient implements STS4LanguageClient {

    private static final Logger LOGGER = Logger.getInstance(StsLanguageClient.class);

    private final List<HighlightProcessor> processors;

    private final Map<String, ClasspathListener> classpathListenerMap = new HashMap<>();

    private final TypeProvider typeProvider;

    private final TypeDescriptorProvider typeDescriptorProvider;

    private final PsiResolver psiResolver;

    public StsLanguageClient(ClientContext clientContext) {
        super(clientContext);
        processors = ImmutableList.of(new RangeHighlightProcessor(), new InlayHighlightProcessor());
        typeProvider = new TypeProvider(clientContext.getProject());
        typeDescriptorProvider = new TypeDescriptorProvider();
        psiResolver = new PsiResolver(clientContext.getProject());
    }
    private void processHighlights(HighlightParams params, String documentUri, Editor editor,
                                   Document document) {
        processors.forEach(p -> p.preProcess(documentUri, editor));
        params.getCodeLenses()
                .forEach(l -> processors.forEach(p -> p.process(documentUri, l, editor)));
    }

    @Override
    public void highlight(HighlightParams params) {
        LOGGER.debug("Processing highligh notification for document uri :",
                params.getDoc().getUri());

        final String documentUri = FileUtils.sanitizeURI(params.getDoc().getUri());
        final EditorEventManager editorEventManager = getContext()
                .getEditorEventManagerFor(documentUri);

        if (editorEventManager == null || editorEventManager.editor == null
                || editorEventManager.editor.getDocument() == null) {
            LOGGER.debug("Editor is not initialized for processing highlights");
            return;
        }

        final Document document = editorEventManager.editor.getDocument();
        ApplicationManager.getApplication()
                .invokeLater(
                        () -> processHighlights(params, documentUri, editorEventManager.editor, document));
    }

    @Override
    public void progress(ProgressParams progressEvent) {

    }

    @Override
    public CompletableFuture<Object> moveCursor(CursorMovement cursorMovement) {
        return null;
    }

    @Override
    public CompletableFuture<Object> addClasspathListener(ClasspathListenerParams params) {
        ClasspathListener classpathListener = ClasspathListener.from(params, getContext().getProject());
        classpathListenerMap.put(params.getCallbackCommandId(), classpathListener);
        ForkJoinPool.commonPool().execute(() -> classpathListener.register(getContext().getRequestManager()));
        return CompletableFuture.completedFuture(new Object());
    }

    @Override
    public CompletableFuture<Object> removeClasspathListener(ClasspathListenerParams params) {
        ClasspathListener classpathListener = classpathListenerMap.remove(params.getCallbackCommandId());
        if (classpathListener != null) {
            classpathListener.unregister();
        } else {
            LOGGER.warn("removeClasspathListener was called for unregistered listener [callbackId:"
                    + params.getCallbackCommandId() + "]");
        }
        return CompletableFuture.completedFuture(new Object());
    }

    @Override
    public CompletableFuture<MarkupContent> javadoc(JavaDataParams params) {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<TypeData> javaType(JavaDataParams params) {
        return runReadAction(() -> {
            return CompletableFuture.completedFuture(typeProvider.typeDataFor(params.getBindingKey()));
        });
    }

    @Override
    public CompletableFuture<String> javadocHoverLink(JavaDataParams params) {
        return runReadAction(() -> {
            PsiClass psiClass = ClassUtil.findPsiClass(PsiManager.getInstance(getContext().getProject()),
                    JavaUtils.typeBindingKeyToFqName(params.getBindingKey()));

            if (psiClass != null) {
                String url = VfsUtilCore.fixIDEAUrl(psiClass.getContainingFile().getVirtualFile().getUrl());
                return CompletableFuture.completedFuture(url);
            } else {
                LOGGER.warn("Failed to find source file url for : " + params.getBindingKey());
                return CompletableFuture.completedFuture("");
            }
        });
    }

    @Override
    public CompletableFuture<Location> javaLocation(JavaDataParams params) {
        return CompletableFuture.supplyAsync(() -> runReadAction(() -> {
            final Tuple.Two<PsiClass, PsiMember> elements = psiResolver.resolvePsiElements(params.getBindingKey());
            if (elements.getFirst() == null) {
                LOGGER.warn(String.format("Failed to resolve location for binding %s", params.getBindingKey()));
                return null;
            }

            String url = VfsUtilCore.fixIDEAUrl(elements.getFirst().getContainingFile().getVirtualFile().getUrl());
            Range range = Optional.ofNullable(elements.getSecond())
                    .map(this::mapToRange)
                    .orElseGet(() -> mapToRange(elements.getFirst()));
            return new Location(url, range);
        }));
    }

    private Range mapToRange(PsiElement element) {
        Document document = PsiDocumentManager.getInstance(getContext().getProject()).getDocument(element.getContainingFile());
        final int line = document.getLineNumber(element.getTextOffset());
        final int column = 0; //todo: need to calculate the column
        return new Range(new Position(line, column), new Position(line, column));
    }

    @Override
    public CompletableFuture<List<TypeDescriptorData>> javaSearchTypes(JavaSearchParams params) {
        return runReadAction(() -> {
            return CompletableFuture.completedFuture(typeDescriptorProvider.descriptors(PsiShortNamesCache.getInstance(getContext().getProject())
                    .getClassesByName(params.getTerm(), GlobalSearchScope.allScope(getContext().getProject()))));
        });
    }

    @Override
    public CompletableFuture<List<String>> javaSearchPackages(JavaSearchParams params) {
        return CompletableFuture.completedFuture(Collections.emptyList());
    }

    @Override
    public CompletableFuture<List<Either<TypeDescriptorData, TypeData>>> javaSubTypes(JavaTypeHierarchyParams params) {
        return runReadAction(() -> {
            return CompletableFuture.completedFuture(findClass(params).map(clazz -> {
                List<PsiClass> subtypes = Lists.newCopyOnWriteArrayList(ClassInheritorsSearch.search(clazz, true).findAll());
                if(params.isIncludeFocusType()) {
                    subtypes.add(clazz);
                }
                return typeDescriptorProvider.descriptors(subtypes.toArray(new PsiClass[0]))
                        .stream().map(Either::<TypeDescriptorData, TypeData>forLeft).collect(Collectors.toList());
            }).orElse(Collections.emptyList()));
        });
    }

    @Override
    public CompletableFuture<List<Either<TypeDescriptorData, TypeData>>> javaSuperTypes(JavaTypeHierarchyParams params) {
        return runReadAction(() -> {
            return CompletableFuture.completedFuture(findClass(params).map(clazz -> {
                List<TypeDescriptorData> descriptors = typeDescriptorProvider.descriptors(clazz.getSupers());
                if (params.isIncludeFocusType()) {
                    List<TypeDescriptorData> supers = new ArrayList<>(descriptors.size() + 1);
                    supers.addAll(typeDescriptorProvider.descriptors(new PsiClass[] { clazz }));
                    supers.addAll(descriptors);
                    return supers.stream().map(Either::<TypeDescriptorData, TypeData>forLeft).collect(Collectors.toList());
                } else {
                    return descriptors.stream().map(Either::<TypeDescriptorData, TypeData>forLeft).collect(Collectors.toList());
                }
            }).orElse(Collections.emptyList()));
        });
    }

    @Override
    public CompletableFuture<List<JavaCodeCompleteData>> javaCodeComplete(JavaCodeCompleteParams params) {
        return CompletableFuture.completedFuture(Collections.emptyList());
    }

    private Optional<PsiClass> findClass(JavaTypeHierarchyParams params) {
        return Optional.ofNullable(JavaPsiFacade.getInstance(getContext().getProject()).findClass(params.getFqName(),
                GlobalSearchScope.allScope(getContext().getProject())));
    }
}
