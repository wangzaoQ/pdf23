package com.pdf.editor.baselineprofile;

import androidx.benchmark.macro.ExperimentalBaselineProfilesApi;
import androidx.benchmark.macro.junit4.BaselineProfileRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
@LargeTest
public class BaselineProfileGenerator {

    @Rule
    public BaselineProfileRule baselineProfileRule = new BaselineProfileRule();

    @OptIn(markerClass = ExperimentalBaselineProfilesApi.class)
    @Test
    public void generate() {
        baselineProfileRule.collect(
                "com.test.pdf",
                true,
                scope -> {
                    scope.pressHome();
                    scope.startActivityAndWait();
                    scope.getDevice().waitForIdle();
                    return null;
                });
    }
}
