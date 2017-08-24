package com.github.leoliang;

import sun.jvm.hotspot.memory.StringTable;
import sun.jvm.hotspot.memory.SystemDictionary;
import sun.jvm.hotspot.oops.Instance;
import sun.jvm.hotspot.oops.InstanceKlass;
import sun.jvm.hotspot.oops.OopField;
import sun.jvm.hotspot.oops.TypeArray;
import sun.jvm.hotspot.runtime.VM;
import sun.jvm.hotspot.tools.Tool;

/**
 * Print the string literal pool of a running JVM.
 * Usage: java -cp .:${JAVA_HOME}/lib/sa-jdi.jar com.github.leoliang.PrintStringTableJDK8 &lt;Running JVM's PID&gt; <br />
 * You need to add sa-jdi.jar to your class path. This is generally available in your JDK's lib directory. Also, you might need to run this class with super user privileges in order to access the other JVM.
 * <p>
 * Base on https://github.com/puneetlakhina/javautils/blob/master/src/com/blogspot/sahyog/PrintStringTable.java
 */
public class PrintStringTableJDK8 extends Tool {
    public PrintStringTableJDK8() {

    }

    public static void main(String args[]) throws Exception {
        if (args.length == 0 || args.length > 1) {
            System.err.println("Usage: java com.github.leoliang.PrintStringTableJDK8 <PID of the JVM whose string table you want to print>");
            System.exit(1);
        }
        PrintStringTableJDK8 pst = new PrintStringTableJDK8();
        pst.execute(args);
        pst.stop();
    }

    @Override
    public void run() {
        StringTable table = VM.getVM().getStringTable();
        StringPrinter stringPrinter = new StringPrinter();
        table.stringsDo(stringPrinter);
        System.out.println("Total: " + stringPrinter.getTotal());
    }

    class StringPrinter implements StringTable.StringVisitor {
        private final OopField stringValueField;
        private volatile long total = 0;

        public StringPrinter() {
            InstanceKlass strKlass = SystemDictionary.getStringKlass();
            stringValueField = (OopField) strKlass.findField("value", "[C");
        }

        @Override
        public void visit(Instance instance) {
            TypeArray charArray = ((TypeArray) stringValueField.getValue(instance));
            StringBuilder sb = new StringBuilder();
            for (long i = 0; i < charArray.getLength(); i++) {
                sb.append(charArray.getCharAt(i));
            }
            total++;
            System.out.println("Address: " + instance.getHandle() + " Content: " + sb.toString());
        }

        public long getTotal() {
            return total;
        }
    }
}
