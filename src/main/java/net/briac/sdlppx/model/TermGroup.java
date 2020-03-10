package net.briac.sdlppx.model;

import java.util.ArrayList;
import java.util.List;

public class TermGroup {

    public List<Term> terms;
    private String definition;

    public TermGroup() {
        terms = new ArrayList<>();
        definition = "";
    }

    public void addDefinition(String def) {
        definition = def;
    }

    public void addTerm(Term term) {
        terms.add(term);
    }

    public String getDefinition() {
        return definition;
    }

}
