package com.github.leoliang;

import sun.jvm.hotspot.debugger.AddressException;
import sun.jvm.hotspot.oops.DefaultHeapVisitor;
import sun.jvm.hotspot.oops.InstanceKlass;
import sun.jvm.hotspot.oops.IntField;
import sun.jvm.hotspot.oops.LongField;
import sun.jvm.hotspot.oops.ObjectHeap;
import sun.jvm.hotspot.oops.Oop;
import sun.jvm.hotspot.oops.OopField;
import sun.jvm.hotspot.runtime.VM;
import sun.jvm.hotspot.tools.Tool;
import sun.jvm.hotspot.utilities.SystemDictionaryHelper;

import java.util.Arrays;

/**
 * Print stats of NIO direct memory, as an alternative on JDK6 without JMX support for direct memory monitoring
 * Usage: java -cp .:${JAVA_HOME}/lib/sa-jdi.jar com.github.leoliang.PrintDirectMemorySizeJDK8 &lt;Running JVM's PID&gt; <br />
 * You need to add sa-jdi.jar to your class path. This is generally available in your JDK's lib directory. Also, you might need to run this class with super user privileges in order to access the other JVM.
 * <p>
 * Base on https://gist.github.com/rednaxelafx/1593521#file-directmemorysize-java
 */
public class PrintDirectMemorySizeJDK8 extends Tool {
    private boolean exactMallocMode;
    private boolean verbose;

    public PrintDirectMemorySizeJDK8(boolean exactMallocMode, boolean verbose) {
        this.exactMallocMode = exactMallocMode;
        this.verbose = verbose;
    }

    public static long getStaticLongFieldValue(String className, String fieldName) {
        InstanceKlass klass = SystemDictionaryHelper.findInstanceKlass(className);
        LongField field = (LongField) klass.findField(fieldName, "J");
        return field.getValue(klass.getJavaMirror());
    }

    public static long getStaticAtomicLongFieldValue(String className, String fieldName) {
        InstanceKlass klass = SystemDictionaryHelper.findInstanceKlass(className);
        OopField field = (OopField) klass.findField(fieldName, "Ljava/util/concurrent/atomic/AtomicLong;");
        Oop atomicLong = field.getValue(klass.getJavaMirror());
        InstanceKlass atomicLongKlass = SystemDictionaryHelper.findInstanceKlass("java.util.concurrent.atomic.AtomicLong");
        LongField atomicLongValueField = (LongField) atomicLongKlass.findField("value", "J");
        return atomicLongValueField.getValue(atomicLong);
    }

    public static int getStaticIntFieldValue(String className, String fieldName) {
        InstanceKlass klass = SystemDictionaryHelper.findInstanceKlass(className);
        IntField field = (IntField) klass.findField(fieldName, "I");
        return field.getValue(klass.getJavaMirror());
    }

    public static double toM(long value) {
        return value / (1024 * 1024.0);
    }

    public static void main(String[] args) {
        boolean exactMallocMode = false;
        boolean verbose = false;

        // argument processing logic copied from sun.jvm.hotspot.tools.JStack
        int used = 0;
        for (String arg : args) {
            if ("-e".equals(arg)) {
                exactMallocMode = true;
                used++;
            } else if ("-v".equals(arg)) {
                verbose = true;
                used++;
            }
        }

        if (used != 0) {
            args = Arrays.copyOfRange(args, used, args.length);
        }

        PrintDirectMemorySizeJDK8 tool = new PrintDirectMemorySizeJDK8(exactMallocMode, verbose);
        tool.execute(args);
        tool.stop();
    }

    public void run() {
        // Ready to go with the database...
        try {
            long reservedMemory = getStaticAtomicLongFieldValue("java.nio.Bits", "reservedMemory");
            long directMemory = getStaticLongFieldValue("sun.misc.VM", "directMemory");

            System.out.println("NIO direct memory: (in bytes)");
            System.out.printf("  reserved size = %f MB (%d bytes)\n", toM(reservedMemory), reservedMemory);
            System.out.printf("  max size      = %f MB (%d bytes)\n", toM(directMemory), directMemory);

            if (verbose) {
                System.out.println("Currently allocated direct buffers:");
            }

            if (exactMallocMode || verbose) {
                final long pageSize = getStaticIntFieldValue("java.nio.Bits", "pageSize");
                ObjectHeap heap = VM.getVM().getObjectHeap();
                InstanceKlass deallocatorKlass =
                        SystemDictionaryHelper.findInstanceKlass("java.nio.DirectByteBuffer$Deallocator");
                final LongField addressField = (LongField) deallocatorKlass.findField("address", "J");
                final IntField capacityField = (IntField) deallocatorKlass.findField("capacity", "I");
                final int[] countHolder = new int[1];

                heap.iterateObjectsOfKlass(new DefaultHeapVisitor() {
                    public boolean doObj(Oop oop) {
                        long address = addressField.getValue(oop);
                        if (address == 0) return false; // this deallocator has already been run

                        long capacity = capacityField.getValue(oop);
                        long mallocSize = capacity + pageSize;
                        countHolder[0]++;

                        if (verbose) {
                            System.out.printf("  0x%016x: capacity = %f MB (%d bytes),"
                                            + " mallocSize = %f MB (%d bytes)\n",
                                    address, toM(capacity), capacity,
                                    toM(mallocSize), mallocSize);
                        }

                        return false;
                    }
                }, deallocatorKlass, false);

                if (exactMallocMode) {
                    long totalMallocSize = reservedMemory + pageSize * countHolder[0];
                    System.out.printf("NIO direct memory malloc'd size: %f MB (%d bytes)\n",
                            toM(totalMallocSize), totalMallocSize);
                }
            }
        } catch (AddressException e) {
            System.err.println("Error accessing address 0x"
                    + Long.toHexString(e.getAddress()));
            e.printStackTrace();
        }
    }

    public String getName() {
        return "directMemorySize";
    }

    protected void printFlagsUsage() {
        System.out.println("    -e\tto print the actual size malloc'd");
        System.out.println("    -v\tto print verbose info of every live DirectByteBuffer allocated from Java");
        super.printFlagsUsage();
    }
}