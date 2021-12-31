package io.openex.rest.file.form;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class DocumentUpdateInput {

    @JsonProperty("document_description")
    private String description;

    @JsonProperty("document_tags")
    private List<String> tagIds;

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<String> getTagIds() {
        return tagIds;
    }

    public void setTagIds(List<String> tagIds) {
        this.tagIds = tagIds;
    }
}
