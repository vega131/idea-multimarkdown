/*
 * Copyright (c) 2015-2015 Vladimir Schneider <vladimir.schneider@gmail.com>
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
 * This file is based on the IntelliJ SimplePlugin tutorial
 *
 */
package com.vladsch.idea.multimarkdown.editor;

//import com.intellij.ide.scratch.ScratchFileService;

import com.intellij.lang.Language;
import com.intellij.lang.PerFileMappings;
import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.PossiblyDumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.vladsch.idea.multimarkdown.MultiMarkdownFileType;
import com.vladsch.idea.multimarkdown.MultiMarkdownFileTypeFactory;
import com.vladsch.idea.multimarkdown.MultiMarkdownLanguage;
import com.vladsch.idea.multimarkdown.settings.MultiMarkdownGlobalSettings;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

public class MultiMarkdownFxPreviewEditorProvider implements FileEditorProvider, PossiblyDumbAware {
    private static final org.apache.log4j.Logger logger = org.apache.log4j.Logger.getLogger(MultiMarkdownFxPreviewEditorProvider.class);

    private static final int FX_PREVIEW_UNKNOWN = 0;
    private static final int FX_PREVIEW_HAVE_JAR = 1;
    private static final int FX_PREVIEW_CAN_LOAD = 2;
    private static final int FX_PREVIEW_CANNOT_LOAD = 3;

    public static final String EDITOR_TYPE_ID = MultiMarkdownLanguage.NAME + "FxPreviewEditor";
    private static int canLoadFxEditor = FX_PREVIEW_UNKNOWN;
    private static Class<?> MultiMarkdownFxPreviewEditor;
    private static Constructor<?> classConstructor;

    public static boolean accept(@NotNull VirtualFile file) {
        String fileExt = file.getExtension();
        FileType fileType = file.getFileType();
        boolean doAccept = fileType instanceof MultiMarkdownFileType;

        if (!doAccept) {
            try {
                // Issue: #14 scratch files have to be matched differently
                //ScratchFileService fileService = ScratchFileService.classConstructor();
                //PerFileMappings<Language> scratchesMapping = fileService.getScratchesMapping();
                //Language language = scratchesMapping.getMapping(file);
                //doAccept = language instanceof MultiMarkdownLanguage;

                // Issue: #15 class not found ScratchFileService, so we take care of it through reflection
                Class<?> ScratchFileService = Class.forName("com.intellij.ide.scratch.ScratchFileService");
                Method getInstance = ScratchFileService.getMethod("getInstance");
                Method getScratchesMapping = ScratchFileService.getMethod("getScratchesMapping");
                Object fileService = getInstance.invoke(ScratchFileService);
                PerFileMappings<Language> mappings = (PerFileMappings<Language>) getScratchesMapping.invoke(fileService);
                Language language = mappings.getMapping(file);
                doAccept = language instanceof MultiMarkdownLanguage;
            } catch (Exception ex) {
                // no such beast
            }
        }

        if (!doAccept && fileExt != null) {
            for (String ext : MultiMarkdownFileTypeFactory.getExtensions()) {
                if (ext.equals(fileExt)) {
                    doAccept = true;
                    break;
                }
            }
        }
        return doAccept;
    }

    public boolean accept(@NotNull Project project, @NotNull VirtualFile file) {
        return accept(file);
    }

    @NotNull
    @Override
    public FileEditor createEditor(@NotNull Project project, @NotNull VirtualFile file) {
        return createEditor(project, file, false);
    }

    @NotNull
    public static FileEditor createEditor(@NotNull Project project, @NotNull VirtualFile file, boolean forRawHtml) {
        if (!MultiMarkdownGlobalSettings.getInstance().useOldPreview.getValue()) {
            if (canLoadFxEditor == FX_PREVIEW_UNKNOWN) {
                try {
                    MultiMarkdownGlobalSettings.getInstance().setIsFxHtmlPreview(true);
                    canLoadFxEditor = FX_PREVIEW_CAN_LOAD;
                    FileEditor fileEditor = new MultiMarkdownFxPreviewEditor(project, FileDocumentManager.getInstance().getDocument(file), forRawHtml);
                    return fileEditor;
                } catch (Exception e) {
                    canLoadFxEditor = FX_PREVIEW_CANNOT_LOAD;
                    MultiMarkdownGlobalSettings.getInstance().setIsFxHtmlPreview(false);
                    MultiMarkdownGlobalSettings.getInstance().useOldPreview.setValue(true);
                    e.printStackTrace();
                }
            }
        }

        if (canLoadFxEditor == FX_PREVIEW_CAN_LOAD) {
            return new MultiMarkdownFxPreviewEditor(project, FileDocumentManager.getInstance().getDocument(file), forRawHtml);
        }

        // TODO: show notification of the problem and solutions
        if (canLoadFxEditor != FX_PREVIEW_CANNOT_LOAD) {
            canLoadFxEditor = FX_PREVIEW_CANNOT_LOAD;
            MultiMarkdownGlobalSettings.getInstance().useOldPreview.setValue(true);
            MultiMarkdownGlobalSettings.getInstance().setIsFxHtmlPreview(false);
        }
        return new MultiMarkdownPreviewEditor(project, FileDocumentManager.getInstance().getDocument(file), forRawHtml);
    }

    public void disposeEditor(@NotNull FileEditor editor) {
        editor.dispose();
    }

    @NotNull
    @Override
    public FileEditorState readState(@NotNull Element sourceElement, @NotNull Project project, @NotNull VirtualFile file) {
        return FileEditorState.INSTANCE;
    }

    @Override
    public void writeState(@NotNull FileEditorState state, @NotNull Project project, @NotNull Element targetElement) {
    }

    @NotNull
    @Override
    public String getEditorTypeId() {
        return EDITOR_TYPE_ID;
    }

    @NotNull
    @Override
    public FileEditorPolicy getPolicy() {
        return FileEditorPolicy.PLACE_AFTER_DEFAULT_EDITOR;
    }

    @Override
    public boolean isDumbAware() {
        return true;
    }
}
