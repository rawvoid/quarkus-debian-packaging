/*
 * Copyright 2026 Rawvoid(https://github.com/rawvoid)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.github.rawvoid.quarkus.debian.packaging.deployment.builder;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.jboss.logging.Logger;

import io.github.rawvoid.quarkus.debian.packaging.deployment.model.DebianPackageModel;
import io.github.rawvoid.quarkus.debian.packaging.deployment.model.PackagePayload;

/**
 * Assembles Debian package entries from the resolved model and writes the {@code .deb} file.
 *
 * @author rawvoid
 */
public final class DebPackager {

    private static final Logger LOG = Logger.getLogger(DebPackager.class);

    private DebPackager() {
    }

    public static Path packageDeb(DebianPackageModel model) {
        try {
            // Data templates do not use installedSize; build the tree once.
            List<DebEntry> dataEntries = buildDataEntries(model);
            long installedSizeKiB = DebBuilder.installedSizeKiB(dataEntries);

            Map<String, String> vars = model.templateVariables();
            vars.put("installedSize", Long.toString(installedSizeKiB));
            // JVM-only cleanup; native packages leave a no-op placeholder.
            vars.put(
                    "hsPerfCleanup",
                    model.payload().isNative()
                            ? ":"
                            : "rm -rf \"/tmp/hsperfdata_" + model.serviceUser() + "\"");

            String control = TemplateRenderer.render("control", vars);
            List<DebEntry> controlEntries = List.of(
                    DebEntry.bytes("postinst", TemplateRenderer.renderBytes("postinst", vars), DebEntry.MODE_EXEC, false),
                    DebEntry.bytes("prerm", TemplateRenderer.renderBytes("prerm", vars), DebEntry.MODE_EXEC, false),
                    DebEntry.bytes("postrm", TemplateRenderer.renderBytes("postrm", vars), DebEntry.MODE_EXEC, false));

            Path output = model.outputFile();
            DebBuilder.build(output, control, controlEntries, dataEntries);
            LOG.infof("Built Debian package %s", output.toAbsolutePath());
            return output;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to build Debian package for " + model.packageName(), e);
        }
    }

    private static List<DebEntry> buildDataEntries(DebianPackageModel model) throws IOException {
        Map<String, String> vars = model.templateVariables();
        List<DebEntry> entries = new ArrayList<>();

        // Application payload
        addPayload(entries, model);

        // Launcher (public entrypoint)
        entries.add(DebEntry.bytes(
                stripLeadingSlash(model.executableFile()),
                TemplateRenderer.renderBytes("launcher.sh", vars),
                DebEntry.MODE_EXEC,
                false));

        // Startup runner script
        String startupTemplate = model.payload().isNative() ? "startup-native.sh" : "startup-jvm.sh";
        entries.add(DebEntry.bytes(
                stripLeadingSlash(model.installDir() + "/startup"),
                TemplateRenderer.renderBytes(startupTemplate, vars),
                DebEntry.MODE_EXEC,
                false));

        // Reload helper script
        entries.add(DebEntry.bytes(
                stripLeadingSlash(model.installDir() + "/reload"),
                TemplateRenderer.renderBytes("reload.py", vars),
                DebEntry.MODE_EXEC,
                false));

        // systemd unit
        entries.add(DebEntry.bytes(
                stripLeadingSlash(model.systemdUnitFile()),
                TemplateRenderer.renderBytes("unit.service", vars),
                DebEntry.MODE_FILE,
                false));

        // defaults EnvironmentFile (conffile)
        entries.add(DebEntry.bytes(
                stripLeadingSlash(model.defaultsFile()),
                TemplateRenderer.renderBytes("defaults", vars),
                DebEntry.MODE_FILE,
                true));

        // application.properties (conffile)
        entries.add(DebEntry.bytes(
                stripLeadingSlash(model.configFile()),
                TemplateRenderer.renderBytes("application.properties", vars),
                DebEntry.MODE_FILE,
                true));

        // jvm.options (JVM only, conffile)
        if (!model.payload().isNative()) {
            entries.add(DebEntry.bytes(
                    stripLeadingSlash(model.jvmOptionsFile()),
                    TemplateRenderer.renderBytes("jvm.options", vars),
                    DebEntry.MODE_FILE,
                    true));
        }

        return entries;
    }

    private static void addPayload(List<DebEntry> entries, DebianPackageModel model) throws IOException {
        PackagePayload payload = model.payload();
        String installPrefix = stripLeadingSlash(model.installDir());

        switch (payload.kind()) {
            case FAST_JAR_TREE -> addTree(entries, payload.primaryPath(), installPrefix, DebEntry.MODE_FILE);
            case UBER_JAR -> entries.add(DebEntry.file(
                    installPrefix + "/" + payload.primaryPath().getFileName(),
                    payload.primaryPath(),
                    DebEntry.MODE_FILE,
                    false));
            case LEGACY_JAR -> {
                entries.add(DebEntry.file(
                        installPrefix + "/" + payload.primaryPath().getFileName(),
                        payload.primaryPath(),
                        DebEntry.MODE_FILE,
                        false));
                if (payload.libraryDir() != null && Files.isDirectory(payload.libraryDir())) {
                    addTree(entries, payload.libraryDir(), installPrefix + "/lib", DebEntry.MODE_FILE);
                }
            }
            case NATIVE -> entries.add(DebEntry.file(
                    installPrefix + "/" + payload.primaryPath().getFileName(),
                    payload.primaryPath(),
                    DebEntry.MODE_EXEC,
                    false));
        }
    }

    private static void addTree(List<DebEntry> entries, Path root, String packagePrefix, int fileMode) throws IOException {
        if (!Files.exists(root)) {
            throw new IOException("Payload path does not exist: " + root);
        }
        try (Stream<Path> walk = Files.walk(root)) {
            walk.sorted().forEach(path -> {
                if (path.equals(root)) {
                    return;
                }
                Path relative = root.relativize(path);
                String packagePath = packagePrefix + "/" + relative.toString().replace('\\', '/');
                if (Files.isDirectory(path)) {
                    entries.add(DebEntry.directory(packagePath));
                } else if (Files.isRegularFile(path)) {
                    int mode = Files.isExecutable(path) ? DebEntry.MODE_EXEC : fileMode;
                    entries.add(DebEntry.file(packagePath, path, mode, false));
                }
            });
        }
    }

    private static String stripLeadingSlash(String absolutePath) {
        if (absolutePath.startsWith("/")) {
            return absolutePath.substring(1);
        }
        return absolutePath;
    }
}
