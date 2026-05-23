package com.cocode.vcode.ide.data.model;

import com.cocode.vcode.ide.core.language.Language;

/**
 * Data object container mapping user-defined boilerplate code pieces.
 * Maps code snippet strings to localized categorization labels and language configurations.
 */
public class SnippetItem {
    private String id;
    private String title;
    private String content;
    private Language language;

    /**
     * Three-argument constructor initializing newly authored snippet templates before disk persistence allocation.
     */
    public SnippetItem(String title, String content, Language language) {
        this.title = title;
        this.content = content;
        this.language = language;
    }

    /**
     * Four-argument constructor handling direct indexing from localized repository storage channels.
     */
    public SnippetItem(String id, String title, String content, Language language) {
        this.id = id;
        this.title = title;
        this.content = content;
        this.language = language;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Language getLanguage() {
        return language;
    }

    public void setLanguage(Language language) {
        this.language = language;
    }
}