package com.semmle.js.extractor;

import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.LoaderClassPath;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;

public class BytecodeModifierAgent {
    public static void premain(String agentArgs, Instrumentation inst) {
        System.out.println("[Agent] Agent loaded!");
        inst.addTransformer(new ClassFileTransformer() {
            @Override
            public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
                                    ProtectionDomain protectionDomain, byte[] classfileBuffer) {
                if ("com/semmle/js/extractor/AutoBuild".equals(className)) {
                    try {
                        System.out.println("Found target class: " + className);
                        return modifyClass(loader, classfileBuffer);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                return classfileBuffer;
            }
        });
    }

    private static byte[] modifyClass(ClassLoader loader, byte[] classfileBuffer) throws Exception {
        ClassPool classPool = ClassPool.getDefault();
        classPool.insertClassPath(new LoaderClassPath(loader));

        CtClass ctClass = classPool.makeClass(new java.io.ByteArrayInputStream(classfileBuffer));

        CtMethod setupFiltersMethod = ctClass.getDeclaredMethod("setupFilters");
        System.out.println("[Agent] Modifying method: " + setupFiltersMethod.getName());

        setupFiltersMethod.setBody("""
                {java.util.List patterns = new java.util.ArrayList();
                patterns.add("-**/*.*");
                System.out.println("[Agent] Modified method: " + this.getClass().getName());
                java.util.Set defaultExtract = new java.util.LinkedHashSet();
                defaultExtract.add(com.semmle.js.extractor.FileExtractor.FileType.HTML);
                defaultExtract.add(com.semmle.js.extractor.FileExtractor.FileType.JS);
                defaultExtract.add(com.semmle.js.extractor.FileExtractor.FileType.YAML);
                defaultExtract.add(com.semmle.js.extractor.FileExtractor.FileType.TYPESCRIPT);
                java.util.Iterator fileTypeIterator = defaultExtract.iterator();
                while (fileTypeIterator.hasNext()) {
                    com.semmle.js.extractor.FileExtractor.FileType filetype = \
                        (com.semmle.js.extractor.FileExtractor.FileType) fileTypeIterator.next();
                    java.util.Iterator extensionIterator = filetype.getExtensions().iterator();
                    while (extensionIterator.hasNext()) {
                        String extension = (String) extensionIterator.next();
                        patterns.add("**/*" + extension);
                    }
                }
                patterns.add("**/.eslintrc*");
                patterns.add("**/.xsaccess");
                patterns.add("**/package.json");
                patterns.add("**/tsconfig*.json");
                patterns.add("**/codeql-javascript-*.json");
                java.util.Iterator fileTypeKeyIterator = fileTypes.keySet().iterator();
                while (fileTypeKeyIterator.hasNext()) {
                    String extension = (String) fileTypeKeyIterator.next();
                    patterns.add("**/*" + extension);
                }
                String base = this.LGTM_SRC.toString().replace('\\\\', '/');
                String[] splitPatterns = com.semmle.js.extractor.Main.NEWLINE.split(getEnvVar("LGTM_INDEX_FILTERS", ""));
                for (int i = 0; i < splitPatterns.length; i++) {
                    String pattern = splitPatterns[i].trim();
                    if (pattern.isEmpty()) {
                        continue;
                    }
                    String[] fields = pattern.split(":");
                    if (fields.length != 2) {
                        continue;
                    }
                    pattern = fields[1].trim();
                    pattern = base + "/" + pattern;
                    if ("exclude".equals(fields[0].trim())) {
                        pattern = "-" + pattern;
                    }
                    patterns.add(pattern);
                }
                filters = new com.semmle.util.projectstructure.ProjectLayout((String[])patterns.toArray(new String[0]));
                }""");
        CtMethod setupIncludesAndExcludesMethod = ctClass.getDeclaredMethod("setupIncludesAndExcludes");
        System.out.println("[Agent] Modifying method: " + setupIncludesAndExcludesMethod.getName());
        setupIncludesAndExcludesMethod.setBody("""
               {
                    System.out.println("[ModifiedCodeQL] Modified method: " + this.getClass().getName());
                    String[] includesOri =  com.semmle.js.extractor.Main.NEWLINE.split(getEnvVar("LGTM_INDEX_INCLUDE", ""));
                    String[] includeDirs = com.semmle.js.extractor.Main.NEWLINE.split(getEnvVar("LGTM_INCLUDE_DIRS", ""));
                    java.util.Set includes2 = new java.util.HashSet();

                    for (int i = 0; i < includesOri.length; i++) {
                        String pattern = includesOri[i];
                        includes2.add(pattern);
                    }
                    for (int i = 0; i < includeDirs.length; i++) {
                        java.nio.file.Path startPath = java.nio.file.Paths.get(includeDirs[i], new String[0]);
                        java.util.Deque queue = new java.util.ArrayDeque();
                        queue.add(startPath);
                        java.util.Set visitedDirs = new java.util.HashSet();

                        while (!queue.isEmpty()) {
                            java.nio.file.Path currentDir = queue.poll();
                            if (!visitedDirs.add(currentDir)) {
                                continue;
                            }

                            java.io.File[] entries = currentDir.toFile().listFiles();
                            if (entries == null) {
                                continue;
                            }

                            for (int j = 0; j < entries.length; j++) {
                                java.io.File entry = entries[j];
                                String name = entry.getName();
                                if (entry.isDirectory()) {
                                    queue.add(entry.toPath());
                                } else if (name.endsWith(".js") || name.endsWith(".jsx") || name.endsWith(".ts") || name.endsWith(".tsx")) {
                                    System.out.println("[ModifiedCodeQL] Adding " + entry.toPath() + " to includes");
                                    includes2.add(entry.toPath().toString());
                                }
                            }
                        }
                    }
                    boolean seenInclude = false;
                    String[] includes = new String[includes2.size()];
                    java.util.Iterator iterator = includes2.iterator();
                    int i = 0;
                    while (iterator.hasNext()) {
                        String element = iterator.next();
                        includes[i++] = element;
                    }

                    for (int index = 0; index < includes.length; index++) {
                        String pattern = includes[index];
                        seenInclude = seenInclude | this.addPathPattern(this.includes, this.LGTM_SRC, pattern);
                    }

                    if (!seenInclude) {
                        this.includes.add(this.LGTM_SRC);
                    }

                    String[] excludes = com.semmle.js.extractor.Main.NEWLINE.split(getEnvVar("LGTM_INDEX_EXCLUDE", ""));

                    for (int index = 0; index < excludes.length; index++) {
                        String pattern = excludes[index];
                        this.addPathPattern(this.excludes, this.LGTM_SRC, pattern);
                    }

                    String lgtmRepositoryFoldersCsv = this.getEnvVar("LGTM_REPOSITORY_FOLDERS_CSV");
                    if (lgtmRepositoryFoldersCsv != null) {
                        java.nio.file.Path path = java.nio.file.Paths.get(lgtmRepositoryFoldersCsv, new String[0]);
                        try {
                            java.io.Reader reader = java.nio.file.Files.newBufferedReader(path, java.nio.charset.StandardCharsets.UTF_8);
                            com.semmle.util.io.csv.CSVReader csv = new com.semmle.util.io.csv.CSVReader(reader);
                            try {
                                csv.readNext();
                                while (true) {
                                    String[] fields;
                                    do {
                                        do {
                                            fields = csv.readNext();
                                            if (fields == null) {
                                                break;
                                            }
                                        } while (fields.length != 2);
                                    } while (!"external".equals(fields[0]) && !"metadata".equals(fields[0]));

                                    String folder = fields[1];

                                    try {
                                        java.nio.file.Path folderPath = folder.startsWith("file://") ? java.nio.file.Paths.get(new java.net.URI(folder)) : java.nio.file.Paths.get(folder, new String[0]);
                                        this.excludes.add(this.toRealPath(folderPath));
                                    } catch (java.net.URISyntaxException e) {
                                        com.semmle.util.exception.Exceptions.ignore(e, "Ignore path and print warning message instead");
                                        this.warn("Ignoring '" + fields[0] + "' classification for " + folder + ", which is not a valid path.");
                                    } catch (com.semmle.util.exception.ResourceError e) {
                                        com.semmle.util.exception.Exceptions.ignore(e, "Ignore path and print warning message instead");
                                        this.warn("Ignoring '" + fields[0] + "' classification for " + folder + ", which is not a valid path.");
                                    } catch (java.nio.file.InvalidPathException e) {
                                        com.semmle.util.exception.Exceptions.ignore(e, "Ignore path and print warning message instead");
                                        this.warn("Ignoring '" + fields[0] + "' classification for " + folder + ", which is not a valid path.");
                                    }
                                }
                            } finally {
                                try {
                                    csv.close();
                                } finally {
                                    reader.close();
                                }
                            }
                        } catch (java.io.IOException e) {
                            ;
                        }
                    }
               }
               """);

        return ctClass.toBytecode();
    }

}
