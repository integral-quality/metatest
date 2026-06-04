package io.antigen.gradle;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.tasks.JavaExec;

import java.util.ArrayList;
import java.util.List;

public class AntigenPlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        AntigenExtension extension = project.getExtensions().create("antigen", AntigenExtension.class);

        project.getTasks().register("generateTests", JavaExec.class, task -> {
            task.setGroup("antigen");
            task.setDescription("Generate API tests using Antigen AI");
            task.getMainClass().set("io.antigen.ai.Antigen");

            task.doFirst(t -> {
                var runtimeClasspath = project.getConfigurations().findByName("runtimeClasspath");
                if (runtimeClasspath == null) {
                    throw new IllegalStateException("generateTests requires the java plugin to be applied");
                }
                task.setClasspath(runtimeClasspath);
                task.setArgs(buildArgs(project, extension));
            });
        });
    }

    private List<String> buildArgs(Project project, AntigenExtension extension) {
        List<String> args = new ArrayList<>();
        args.add("generate");

        String spec = (String) project.findProperty("spec");
        if (spec == null) spec = extension.getSpec();
        if (spec == null || spec.isBlank()) {
            throw new IllegalArgumentException(
                "No spec provided. Use -Pspec=path/to/openapi.yaml or set antigen { spec = '...' } in build.gradle"
            );
        }
        args.add("--spec");
        args.add(spec);

        args.add("--project");
        args.add(project.getProjectDir().getAbsolutePath());

        for (String req : extension.getRequirements()) {
            args.add("--requirements");
            args.add(req);
        }

        args.add("--max-retries");
        args.add(String.valueOf(extension.getMaxRetries()));

        if (extension.isVerbose()) {
            args.add("--verbose");
        }

        return args;
    }
}
