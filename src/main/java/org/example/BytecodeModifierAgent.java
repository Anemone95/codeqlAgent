package org.example;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;
import java.util.Stack;

import org.objectweb.asm.*;

public class BytecodeModifierAgent {
    public static void premain(String agentArgs, Instrumentation inst) {
        System.out.println("[Agent] Agent is loaded with args: " + agentArgs);
        inst.addTransformer(new ClassFileTransformer() {
            @Override
            public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
                                    ProtectionDomain protectionDomain, byte[] classfileBuffer) {
                // 找到目标类 "com/semmle/js/extractor/AutoBuild"
                if (className.equals("com/semmle/js/extractor/AutoBuild")) {
                    return modifyClass(classfileBuffer);
                }
                return classfileBuffer;
            }
        });
    }

    private static byte[] modifyClass(byte[] classfileBuffer) {
        ClassReader classReader = new ClassReader(classfileBuffer);
        ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_FRAMES);

        classReader.accept(new ClassVisitor(Opcodes.ASM9, classWriter) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);

                // 仅在 setupFilters 方法中修改逻辑
                if ("setupFilters".equals(name)) {
                    return new MethodVisitor(Opcodes.ASM9, mv) {
                        private Stack<String> constantStack = new Stack<>();

                        @Override
                        public void visitLdcInsn(Object value) {
                            // 将 LDC 加载的常量推入栈
                            if (value instanceof String) {
                                constantStack.push((String) value);
                                System.out.println("[Agent] Found LDC constant: " + value);
                            }
                            super.visitLdcInsn(value);
                        }

                        @Override
                        public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
                            // 检查是否是调用 patterns.add(String) 方法
                            if ("java/util/Set".equals(owner) && "add".equals(name) && "(Ljava/lang/Object;)Z".equals(descriptor)) {
                                // 从栈中取出最近的常量值，检查是否匹配
                                if (!constantStack.isEmpty()) {
                                    String argument = constantStack.pop();
                                    if ("-**/node_modules".equals(argument) || "-**/*.min.js".equals(argument) ||
                                            "-**/*-min.js".equals(argument) || "-**/bower_components".equals(argument)) {
                                        System.out.println("[Agent] Skipping patterns.add(...) call with argument: " + argument);
                                        return; // 跳过这次方法调用
                                    }
                                }
                            }
                            super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
                        }
                    };
                }
                return mv;
            }

        }, 0);

        return classWriter.toByteArray();
    }
}