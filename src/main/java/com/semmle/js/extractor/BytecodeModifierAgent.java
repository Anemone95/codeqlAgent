package com.semmle.js.extractor;

import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.LoaderClassPath;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;

public class BytecodeModifierAgent {
    // premain 方法是 JavaAgent 的入口
    public static void premain(String agentArgs, Instrumentation inst) {
        System.out.println("[Agent] Agent loaded!");
        inst.addTransformer(new ClassFileTransformer() {
            @Override
            public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
                                    ProtectionDomain protectionDomain, byte[] classfileBuffer) {
                // 将类名转换为 Java 格式
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
        // 将当前 ClassLoader 添加到 ClassPool 中，以便 Javassist 能够找到依赖的类
        classPool.insertClassPath(new LoaderClassPath(loader));

        // 加载字节码为 CtClass
        CtClass ctClass = classPool.makeClass(new java.io.ByteArrayInputStream(classfileBuffer));

        // 查找并修改 setupFilters 方法
        CtMethod setupFiltersMethod = ctClass.getDeclaredMethod("setupFilters");
        System.out.println("[Agent] Modifying method: " + setupFiltersMethod.getName());
        setupFiltersMethod.setBody("{java.util.List patterns = new java.util.ArrayList();\n" +
                "patterns.add(\"-**/*.*\");\n" +
                "System.out.println(\"[Agent] Modified method: \" + this.getClass().getName());\n"+
                "java.util.Set defaultExtract = new java.util.LinkedHashSet();\n" +
                "defaultExtract.add(com.semmle.js.extractor.FileExtractor.FileType.HTML);\n" +
                "defaultExtract.add(com.semmle.js.extractor.FileExtractor.FileType.JS);\n" +
                "defaultExtract.add(com.semmle.js.extractor.FileExtractor.FileType.YAML);\n" +
                "defaultExtract.add(com.semmle.js.extractor.FileExtractor.FileType.TYPESCRIPT);\n" +
                "java.util.Iterator fileTypeIterator = defaultExtract.iterator();\n" +
                "while (fileTypeIterator.hasNext()) {\n" +
                "    com.semmle.js.extractor.FileExtractor.FileType filetype = " +
                "        (com.semmle.js.extractor.FileExtractor.FileType) fileTypeIterator.next();\n" +
                "    java.util.Iterator extensionIterator = filetype.getExtensions().iterator();\n" +
                "    while (extensionIterator.hasNext()) {\n" +
                "        String extension = (String) extensionIterator.next();\n" +
                "        patterns.add(\"**/*\" + extension);\n" +
                "    }\n" +
                "}\n" +
                "patterns.add(\"**/.eslintrc*\");\n" +
                "patterns.add(\"**/.xsaccess\");\n" +
                "patterns.add(\"**/package.json\");\n" +
                "patterns.add(\"**/tsconfig*.json\");\n" +
                "patterns.add(\"**/codeql-javascript-*.json\");\n" +
                "java.util.Iterator fileTypeKeyIterator = fileTypes.keySet().iterator();\n" +
                "while (fileTypeKeyIterator.hasNext()) {\n" +
                "    String extension = (String) fileTypeKeyIterator.next();\n" +
                "    patterns.add(\"**/*\" + extension);\n" +
                "}\n"+
                "String base = this.LGTM_SRC.toString().replace('\\\\', '/');\n" +
                "String[] splitPatterns = com.semmle.js.extractor.Main.NEWLINE.split(getEnvVar(\"LGTM_INDEX_FILTERS\", \"\"));\n" +
                "for (int i = 0; i < splitPatterns.length; i++) {\n" +
                "    String pattern = splitPatterns[i].trim();\n" +
                "    if (pattern.isEmpty()) {\n" +
                "        continue;\n" +
                "    }\n" +
                "    String[] fields = pattern.split(\":\");\n" +
                "    if (fields.length != 2) {\n" +
                "        continue;\n" +
                "    }\n" +
                "    pattern = fields[1].trim();\n" +
                "    pattern = base + \"/\" + pattern;\n" +
                "    if (\"exclude\".equals(fields[0].trim())) {\n" +
                "        pattern = \"-\" + pattern;\n" +
                "    }\n" +
                "    patterns.add(pattern);\n" +
                "}\n"+
                "filters = new com.semmle.util.projectstructure.ProjectLayout((String[])patterns.toArray(new String[0]));\n" +
                "}");

        // 返回修改后的字节码
        return ctClass.toBytecode();
    }
}
