package com.ctux.ae2craftingtime.core;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class JobTransitionDetectorTest {
    @Test
    void firstStatusAndProgressOfTheSameJobAreNotTransitions() {
        var detector = new JobTransitionDetector();
        assertFalse(detector.observe(100, 0));
        assertFalse(detector.observe(100, 40));
        assertFalse(detector.observe(100, 40));
    }

    @Test
    void differentStartItemCountIsANewJob() {
        var detector = new JobTransitionDetector();
        detector.observe(100, 500);
        assertTrue(detector.observe(64, 600));
    }

    @Test
    void elapsedTimeGoingBackwardsIsANewJobWithTheSameSize() {
        var detector = new JobTransitionDetector();
        detector.observe(100, 500);
        assertTrue(detector.observe(100, 20));
        assertFalse(detector.observe(100, 60));
    }

    @Test
    void clearForgetsThePreviousJob() {
        var detector = new JobTransitionDetector();
        detector.observe(100, 500);
        detector.clear();
        assertFalse(detector.observe(7, 0));
    }
}
