package io.antigen.ai.orchestrator;

import io.antigen.ai.llm.ClaudeGenerator;
import io.antigen.ai.model.GenerationResult;
import io.antigen.ai.phases.BuildPhase;
import io.antigen.ai.phases.GenerationPhase;
import io.antigen.ai.phases.AntigenPhase;
import io.antigen.ai.phases.TestPhase;
import io.antigen.ai.runners.GradleRunner;
import java.nio.file.Path;
import java.util.List;

public class Orchestrator {

    private final ClaudeGenerator claudeGenerator;
    private final GradleRunner gradleRunner;
    private final AntigenConfig config;

    public Orchestrator(AntigenConfig config) {
        this.config = config;
        this.claudeGenerator = new ClaudeGenerator(config);
        this.gradleRunner = new GradleRunner(config);
    }

    public GenerationResult generate(Path specPath, Path projectPath, List<String> requirements) {
        return generate(specPath, projectPath, requirements, null);
    }

    public GenerationResult generate(Path specPath, Path projectPath, List<String> requirements, Path promptTemplatePath) {
        if (promptTemplatePath != null) {
            System.out.println("Custom Prompt Template: " + promptTemplatePath);
        }

        if (!claudeGenerator.isClaudeAvailable()) {
            System.out.println("ERROR: Claude CLI is not available. Please install Claude Code and ensure 'claude' command is in PATH.");
            return GenerationResult.failure(0, "Claude CLI not found. Install Claude Code first.");
        }

        GenerationContext context = GenerationContext.builder()
                .specPath(specPath)
                .projectPath(projectPath)
                .promptTemplatePath(promptTemplatePath)
                .requirements(requirements)
                .build();

        for (int attempt = 1; attempt <= config.getMaxRetries(); attempt++) {
            System.out.println();
            System.out.printf("=== Attempt %d/%d ===%n", attempt, config.getMaxRetries());

            System.out.println("State 1: Generating tests with Claude...");
            GenerationPhase genPhase = claudeGenerator.generate(context);
            System.out.println("Result: " + (genPhase.isSuccess() ? "SUCCESS" : "FAILED"));

            if (genPhase.failed()) {
                System.out.println("Generation failed: " + genPhase.getFeedback());
                context = context.addFeedback(genPhase);
                continue;
            }

            System.out.println("State 2: Building project...");
            BuildPhase buildPhase = gradleRunner.build(context);
            System.out.println("Result: " + (buildPhase.isSuccess() ? "SUCCESS" : "FAILED"));

            if (buildPhase.failed()) {
                System.out.printf("Build failed with %d errors%n", buildPhase.getCompilationErrors().size());
                context = context.addFeedback(buildPhase);
                continue;
            }

            System.out.println("State 3: Running tests (without Antigen)...");
            TestPhase testPhase = gradleRunner.runTests(context);
            System.out.println("Result: " + (testPhase.isSuccess() ? "SUCCESS" : "FAILED"));

            if (testPhase.failed()) {
                System.out.printf("Tests failed: %d failures%n", testPhase.getTestFailures().size());
                context = context.addFeedback(testPhase);
                continue;
            }

            System.out.println("State 4: Running tests with Antigen fault injection...");
            AntigenPhase antigenPhase = gradleRunner.runAntigen(context);
            System.out.println("Result: " + (antigenPhase.isSuccess() ? "SUCCESS" : "FAILED"));
            System.out.printf("Fault Detection Rate: %.1f%%%n", antigenPhase.getFaultDetectionRate() * 100);

            if (antigenPhase.hasEscapedFaults()) {
                System.out.printf("Antigen failed: %d faults escaped%n", antigenPhase.getEscapedFaults().size());

                if (shouldRetry(context, attempt)) {
                    context = context.addFeedback(antigenPhase);
                    continue;
                } else {
                    System.out.println("Same Antigen failures repeating, stopping retries");
                    return GenerationResult.failure(attempt,
                            "Antigen failures are repeating. Generated tests may be at maximum quality.");
                }
            }

            System.out.println("=== SUCCESS ===");
            System.out.printf("Tests generated and validated in %d attempts%n", attempt);
            return GenerationResult.success(attempt, genPhase.getGeneratedFiles());
        }

        System.out.println("=== FAILURE ===");
        System.out.printf("Failed to generate valid tests after %d attempts%n", config.getMaxRetries());
        return GenerationResult.failure(config.getMaxRetries(),
                "Maximum retries exceeded. Last error: " + context.getLatestFeedback().getFeedback());
    }

    private boolean shouldRetry(GenerationContext context, int currentAttempt) {
        if (currentAttempt >= config.getMaxRetries()) {
            return false;
        }

        if (context.getFeedbackHistory().size() >= 2) {
            List<AntigenPhase> recentAntigenPhases = context.getFeedbackHistory().stream()
                    .filter(phase -> phase instanceof AntigenPhase)
                    .map(phase -> (AntigenPhase) phase)
                    .toList();

            if (recentAntigenPhases.size() >= 2) {
                AntigenPhase last = recentAntigenPhases.get(recentAntigenPhases.size() - 1);
                AntigenPhase secondLast = recentAntigenPhases.get(recentAntigenPhases.size() - 2);

                if (last.getEscapedFaults().equals(secondLast.getEscapedFaults())) {
                    return false;
                }
            }
        }

        return true;
    }
}
