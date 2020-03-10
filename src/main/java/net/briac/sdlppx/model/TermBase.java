package net.briac.sdlppx.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TermBase {

    public Map<Integer, Concept> concepts;
    public Map<String, Integer> languages;
    public List<String> metadata;

    public TermBase() {
        concepts = new HashMap<>();
        languages = new HashMap<>();
        metadata = new ArrayList<>();
    }

    public void addConcept(int key) {
        Concept c = new Concept();
        concepts.put(key, c);
    }

    public void addLanguage(String name) {
        languages.put(name, 1);
    }

    public int getMaxNumber(String name) {
        return languages.get(name);
    }

    public int inLanguageList(String name) {
        if (!languages.containsKey(name)) {
            return 0;
        } else {
            return languages.get(name);
        }
    }

    public void inMeta(String s) {
        if (!metadata.contains(s)) {
            metadata.add(s);
        }
    }

    public void setMaxNumber(String name, int number) {
        languages.put(name, number);
    }

}
