package com.cocode.vcode.ide.core.diagnostic.linters;

import com.cocode.vcode.ide.core.parser.BracketMatcher;
import com.cocode.vcode.ide.data.model.Problem;

import java.io.File;
import java.util.List;

public class BracketLinter {
    public static List<Problem> analyze(File file, String text) {
        return BracketMatcher.findMismatches(file, text);
    }
}
