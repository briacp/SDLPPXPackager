package net.briac.sdlppx.model;

import java.util.HashMap;
import java.util.Map;

public class Concept {
    public Map<String, TermGroup> termGroups;
    private String creator;
    private String creationTime;
    private String modifier;
    private String modificationTime;
    private Map<String, String> metadata;

    public Concept() {
        termGroups = new HashMap<>();
        creator = "";
        creationTime = "";
        modifier = "";
        modificationTime = "";
        metadata = new HashMap<>();
    }

    public void setEntryCreator(String c) {
        creator = c;
    }

    public String getCreator() {
        return creator;
    }

    public void setCreationTime(String t) {
        creationTime = t;
    }

    public String getCreationTime() {
        return creationTime;
    }

    public void setEntryModifier(String m) {
        modifier = m;
    }

    public String getModifier() {
        return modifier;
    }

    public void setModificationTime(String t) {
        modificationTime = t;
    }

    public String getModificationTime() {
        return modificationTime;
    }

    public void addTerm(Term term, String lang) {
        termGroups.get(lang).addTerm(term);
    }

    public void addTermgroup(String lang) {
        TermGroup trmgrp = new TermGroup();
        termGroups.put(lang, trmgrp);
    }

    public void addDef(String def, String lang) {
        termGroups.get(lang).addDefinition(def);
    }

    public void addMeta(String key, String value) {
        metadata.put(key, value);
    }

    public String getMeta(String key) {
        if (metadata.get(key) == null) {
            return "";
        } else {
            return metadata.get(key);
        }
    }

}
