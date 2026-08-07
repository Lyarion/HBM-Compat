package io.github.hbmcompat.client;

import java.util.Collections;
import java.util.List;

import codechicken.nei.PositionedStack;

final class ExtractionResult {

    final List<PositionedStack> inputs;
    final List<PositionedStack> outputs;
    final String error;

    private ExtractionResult(List<PositionedStack> inputs, List<PositionedStack> outputs, String error) {
        this.inputs = inputs;
        this.outputs = outputs;
        this.error = error;
    }

    static ExtractionResult success(List<PositionedStack> inputs, List<PositionedStack> outputs) {
        return new ExtractionResult(inputs, outputs, null);
    }

    static ExtractionResult failure(String error) {
        return new ExtractionResult(Collections.<PositionedStack>emptyList(), Collections.<PositionedStack>emptyList(), error);
    }

    boolean isSuccess() {
        return error == null;
    }
}
