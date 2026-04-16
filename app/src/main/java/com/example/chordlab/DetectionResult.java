package com.example.chordlab;

public class DetectionResult {
    public String chordName;
    public String hint;
    public boolean isMatch;

    public DetectionResult(String chordName, String hint, boolean isMatch) {
        this.chordName = chordName;
        this.hint = hint;
        this.isMatch = isMatch;
    }
}