package io.antigen.core.report;

import org.junit.jupiter.api.Test;
import java.io.File;

public class HtmlReportGeneratorTest {

    @Test
    public void testGenerateReport() {
        System.out.println("Testing HTML report generation...");

        // Use the JSON files in the lib directory
        HtmlReportGenerator.generateReport("antigen_report.html");

        File reportFile = new File("antigen_report.html");
        if (reportFile.exists()) {
            System.out.println("HTML report generated successfully: " + reportFile.getAbsolutePath());
            System.out.println("File size: " + reportFile.length() + " bytes");
        } else {
            System.err.println("HTML report was not created!");
        }
    }
}
