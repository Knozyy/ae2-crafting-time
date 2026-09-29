package com.ctux.ae2craftingtime.testdriver;

import java.util.ArrayList;
import java.util.List;

public final class LayoutValidator {
    public static List<String> validate(UiSnapshot snapshot) {
        var failures = new ArrayList<String>();
        for (var text : snapshot.text()) {
            check("text " + text.key(), text.bounds(), snapshot, failures);
        }
        failures.addAll(validateBadges(snapshot));
        return List.copyOf(failures);
    }

    public static List<String> validateBadges(UiSnapshot snapshot) {
        var failures = new ArrayList<String>();
        for (var badge : snapshot.badges()) {
            if (snapshot.cpuCards().stream().map(UiSnapshot.CpuCard::badge).anyMatch(badge::equals)) {
                continue;
            }
            check("badge", badge, snapshot, failures);
        }
        return List.copyOf(failures);
    }

    public static boolean warningBadgeValid(UiSnapshot snapshot, UiSnapshot.ObservedText warning) {
        var bounds = warning.bounds();
        var badgeContainsWarning = snapshot.badges().stream().anyMatch(badge -> bounds.inside(badge));
        return bounds.inside(snapshot.gui())
                && snapshot.rows().stream().anyMatch(row -> bounds.inside(row.cell()))
                && !warning.bold() && warning.color() != null && (warning.color() & 0xffffff) == 0xff5555
                && badgeContainsWarning == com.ctux.ae2craftingtime.mc1201.ClientOptionsRuntime.current().badgeBackground()
                && validateBadges(snapshot).isEmpty();
    }

    private static void check(String name, Rect candidate, UiSnapshot snapshot, List<String> failures) {
        if (!candidate.inside(snapshot.gui())) {
            failures.add(name + " outside GUI");
        }
        if (snapshot.itemCells().stream().anyMatch(candidate::overlaps)) {
            failures.add(name + " overlaps item cell");
        }
        if (snapshot.widgets().stream().map(UiSnapshot.Widget::bounds).anyMatch(candidate::overlaps)) {
            failures.add(name + " overlaps widget");
        }
    }

    private LayoutValidator() {
    }
}
