package com.github.leoliang;

import sun.jvm.hotspot.memory.SystemDictionary;
import sun.jvm.hotspot.oops.Klass;
import sun.jvm.hotspot.runtime.VM;
import sun.jvm.hotspot.tools.Tool;

/**
 * Print the string literal pool of a running JVM.
 * Usage: java -cp .:${JAVA_HOME}/lib/sa-jdi.jar com.github.leoliang.PrintClassesJDK8 &lt;Running JVM's PID&gt; <br />
 * You need to add sa-jdi.jar to your class path. This is generally available in your JDK's lib directory. Also, you might need to run this class with super user privileges in order to access the other JVM.
 * <p>
 */
public class PrintClassesJDK8 extends Tool {

    public static void main(String[] args) {
        if (args.length == 0 || args.length > 1) {
            System.err.println("Usage: java com.github.leoliang.PrintClassesJDK8 <PID of the JVM whose string table you want to print>");
            System.exit(1);
        }
        PrintClassesJDK8 test = new PrintClassesJDK8();
        test.execute(args);
        test.stop();
    }

    @Override
    public void run() {
        VM.getVM().getSystemDictionary().allClassesDo(new SystemDictionary.ClassVisitor() {
            @Override
            public void visit(Klass klass) {
                System.out.println(klass.getName().asString());
            }
        });

    }
}