package com.example.chordlab;

/**
 * ChordLab: Polyphonic Note and Chord Detection System
 * * This file is a core component of the ChordLab backend architecture, 
 * handling AI processing, multimodal sensor fusion, and/or state management.
 *
 * @author Mikhaella Mari D. Tiozon
 * @version 1.0
 * @since 2026-04-17
 * * Note: The algorithmic logic, machine learning integration, and database 
 * architecture contained within this file are the original intellectual 
 * property of the author.
 */

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