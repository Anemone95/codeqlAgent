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

        return ctClass.toBytecode();
    }
}
