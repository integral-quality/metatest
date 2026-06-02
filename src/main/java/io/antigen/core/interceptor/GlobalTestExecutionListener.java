package io.antigen.core.interceptor;

import io.antigen.core.simulation.FaultSimulationReport;
import io.antigen.core.coverage.Collector;
import io.antigen.core.analytics.GapAnalyzer;
import io.antigen.core.report.HtmlReportGenerator;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestPlan;

public class GlobalTestExecutionListener implements TestExecutionListener {

    private static boolean executed = false;
    private final boolean runWithAntigen = Boolean.parseBoolean(System.getProperty("runWithAntigen"));

    public GlobalTestExecutionListener() {
        System.out.println("[Antigen] GlobalTestExecutionListener initialized. runWithAntigen=" + runWithAntigen);
    }

    @Override
    public void testPlanExecutionFinished(TestPlan testPlan) {
        System.out.println("[Antigen] testPlanExecutionFinished called. executed=" + executed + ", runWithAntigen=" + runWithAntigen);
        if (!executed && runWithAntigen) {
            executed = true;
            System.out.println("[Antigen] All tests completed - Generating reports...");
//            FaultSimulationReport.getInstance().sendResultsToAPI();

            // Print console summary before writing file reports
            FaultSimulationReport.getInstance().printConsoleSummary();

            // Generate JSON reports first
            FaultSimulationReport.getInstance().createJSONReport();
            Collector.saveCoverageReport();
            GapAnalyzer.generateGapReport();

            // Generate HTML report after all JSON reports are created
            try {
                System.out.println("[Antigen] Generating HTML report...");
                HtmlReportGenerator.generateReport("antigen_report.html");
                System.out.println("[Antigen] HTML report generated successfully: antigen_report.html");
            } catch (Exception e) {
                System.err.println("[Antigen] Failed to generate HTML report: " + e.getMessage());
                e.printStackTrace();
            }

            System.out.println("[Antigen] All reports generated successfully!");
        } else {
            System.out.println("[Antigen] Skipping report generation (executed=" + executed + ", runWithAntigen=" + runWithAntigen + ")");
        }
    }
}