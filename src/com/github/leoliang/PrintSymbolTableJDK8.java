package com.github.leoliang;

import sun.jvm.hotspot.memory.StringTable;
import sun.jvm.hotspot.memory.SymbolTable;
import sun.jvm.hotspot.memory.SystemDictionary;
import sun.jvm.hotspot.oops.Instance;
import sun.jvm.hotspot.oops.InstanceKlass;
import sun.jvm.hotspot.oops.OopField;
import sun.jvm.hotspot.oops.Symbol;
import sun.jvm.hotspot.oops.TypeArray;
import sun.jvm.hotspot.runtime.VM;
import sun.jvm.hotspot.tools.Tool;

/**
 * Print the symbol table of a running JVM.
 * Usage: java -cp .:${JAVA_HOME}/lib/sa-jdi.jar com.github.leoliang.PrintSymbolTableJDK8 &lt;Running JVM's PID&gt; <br />
 * You need to add sa-jdi.jar to your class path. This is generally available in your JDK's lib directory. Also, you might need to run this class with super user privileges in order to access the other JVM.
 * <p>
 */
public class PrintSymbolTableJDK8 extends Tool {
    public PrintSymbolTableJDK8() {

    }

    public static void main(String args[]) throws Exception {
        if (args.length == 0 || args.length > 1) {
            System.err.println("Usage: java com.github.leoliang.PrintSymbolTableJDK8 <PID of the JVM whose string table you want to print>");
            System.exit(1);
        }
        PrintSymbolTableJDK8 pst = new PrintSymbolTableJDK8();
        pst.execute(args);
        pst.stop();
    }

    @Override
    public void run() {
        SymbolTable table = VM.getVM().getSymbolTable();
        table.symbolsDo(new SymbolTablePrinter());
    }

    class SymbolTablePrinter implements SymbolTable.SymbolVisitor {
        @Override
        public void visit(Symbol symbol) {
            System.out.println(symbol.asString());
        }
    }
}
