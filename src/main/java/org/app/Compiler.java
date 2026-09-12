package org.app;

public class Compiler {
    private String text;
    private ILCode code;

    public Compiler(String text) {
        this.text = text;
    }

    public String compile() {
        try {
            return "";
        } catch (Exception e) {
            return "Error";
        }
    }
}
