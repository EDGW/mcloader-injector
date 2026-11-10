package com.kako.chan.mcloader.injector.agent;

import com.kako.chan.mcloader.injector.bridge.InjectorLog;
import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/** Replaces only application URL network calls; JDK classes and URL objects stay intact. */
final class NetworkTransformer implements ClassFileTransformer {
    private static final String BRIDGE = "com/kako/chan/mcloader/injector/bridge/UrlBridge";

    @Override
    public byte[] transform(ClassLoader loader, String name, Class<?> redefining,
                            ProtectionDomain domain, byte[] bytes) {
        if (loader == null || name == null || name.startsWith("java/") || name.startsWith("javax/")
                || name.startsWith("jdk/") || name.startsWith("sun/") || name.startsWith("com/sun/")
                || name.startsWith("com/kako/chan/mcloader/injector/")) return null;
        try {
            return rewrite(name, bytes);
        } catch (RuntimeException | LinkageError failure) {
            InjectorLog.error("could not transform " + name + ": " + failure.getClass().getName());
            throw failure;
        }
    }

    private static byte[] rewrite(String name, byte[] bytes) {
        ClassReader reader = new ClassReader(bytes);
        ClassWriter writer = new ClassWriter(reader, 0);
        int[] changed = {0};
        reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
            @Override
            public MethodVisitor visitMethod(int access, String methodName, String descriptor,
                                             String signature, String[] exceptions) {
                return new MethodVisitor(Opcodes.ASM9,
                        super.visitMethod(access, methodName, descriptor, signature, exceptions)) {
                    @Override
                    public void visitMethodInsn(int opcode, String owner, String method,
                                                String descriptor, boolean isInterface) {
                        String replacement = null;
                        if (opcode == Opcodes.INVOKEVIRTUAL && owner.equals("java/net/URL")) {
                            if (method.equals("openConnection")) {
                                if (descriptor.equals("()Ljava/net/URLConnection;")) {
                                    replacement = "(Ljava/net/URL;)Ljava/net/URLConnection;";
                                } else if (descriptor.equals("(Ljava/net/Proxy;)Ljava/net/URLConnection;")) {
                                    replacement = "(Ljava/net/URL;Ljava/net/Proxy;)Ljava/net/URLConnection;";
                                }
                            } else if (method.equals("openStream")
                                    && descriptor.equals("()Ljava/io/InputStream;")) {
                                replacement = "(Ljava/net/URL;)Ljava/io/InputStream;";
                            }
                        }
                        if (replacement != null) {
                            changed[0]++;
                            super.visitMethodInsn(Opcodes.INVOKESTATIC, BRIDGE, method, replacement, false);
                        } else {
                            super.visitMethodInsn(opcode, owner, method, descriptor, isInterface);
                        }
                    }
                };
            }
        }, 0);
        if (changed[0] == 0) return null;
        byte[] transformed = writer.toByteArray();
        if (InjectorLog.isDebugEnabled()) {
            InjectorLog.debug("transformed " + name + ": " + changed[0] + " call sites");
        }
        return transformed;
    }
}
