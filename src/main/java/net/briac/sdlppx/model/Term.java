package net.briac.sdlppx.model;

public class Term {

    private String word;
    private String termInfo;
    private String usage;
    // other metadata
    
    public Term(String newword) {
        word = newword;
        termInfo = "";
        usage = "";
    }

    public void addTermInfo(String i) {
        termInfo += i;
    }

    public String getTermInfo() {
        return termInfo;
    }

    public void addUsage(String u) {
        usage = u;
    }

    public String getUsage() {
        return usage;
    }

    public String getWord() {
        return word;
    }

}
