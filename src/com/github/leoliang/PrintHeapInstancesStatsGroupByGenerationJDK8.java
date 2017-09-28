package com.github.leoliang;

import sun.jvm.hotspot.gc_implementation.g1.G1CollectedHeap;
import sun.jvm.hotspot.gc_implementation.parallelScavenge.PSOldGen;
import sun.jvm.hotspot.gc_implementation.parallelScavenge.PSYoungGen;
import sun.jvm.hotspot.gc_implementation.parallelScavenge.ParallelScavengeHeap;
import sun.jvm.hotspot.gc_interface.CollectedHeap;
import sun.jvm.hotspot.memory.DefNewGeneration;
import sun.jvm.hotspot.memory.GenCollectedHeap;
import sun.jvm.hotspot.memory.Generation;
import sun.jvm.hotspot.oops.HeapVisitor;
import sun.jvm.hotspot.oops.ObjectHeap;
import sun.jvm.hotspot.oops.Oop;
import sun.jvm.hotspot.runtime.VM;
import sun.jvm.hotspot.tools.Tool;
import sun.jvm.hotspot.utilities.Assert;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Print instance stats group by class and generation.
 * Usage: java -cp .:${JAVA_HOME}/lib/sa-jdi.jar com.github.leoliang.PrintHeapInstancesStatsGroupByGenerationJDK8 &lt;Running JVM's PID&gt; <br />
 * You need to add sa-jdi.jar to your class path. This is generally available in your JDK's lib directory. Also, you might need to run this class with super user privileges in order to access the other JVM.
 * <p>
 * <p>
 * Base on https://gist.github.com/rednaxelafx/1551013#file-histo-patch
 */
public class PrintHeapInstancesStatsGroupByGenerationJDK8 extends Tool {

    public static void main(String args[]) throws Exception {
        if (args.length == 0 || args.length > 1) {
            System.err.println("Usage: java com.github.leoliang.PrintHeapInstancesStatsGroupByGenerationJDK8 <PID of the JVM whose string table you want to print>");
            System.exit(1);
        }
        PrintHeapInstancesStatsGroupByGenerationJDK8 phisgbg = new PrintHeapInstancesStatsGroupByGenerationJDK8();
        phisgbg.execute(args);
        phisgbg.stop();
    }

    private static boolean inEden(Oop obj) {
        if (obj == null) return false;

        CollectedHeap heap = VM.getVM().getUniverse().heap();
        if (heap instanceof GenCollectedHeap) {
            DefNewGeneration gen0 = (DefNewGeneration) ((GenCollectedHeap) heap).getGen(0);
            return gen0.eden().contains(obj.getHandle());
        } else if (heap instanceof ParallelScavengeHeap) {
            PSYoungGen youngGen = ((ParallelScavengeHeap) heap).youngGen();
            return youngGen.edenSpace().contains(obj.getHandle());
        } else if (heap instanceof G1CollectedHeap) {
//            ((G1CollectedHeap)heap).g1mm()
        } else {
            if (Assert.ASSERTS_ENABLED) {
                Assert.that(false, "Expecting GenCollectedHeap/ParallelScavengeHeap/G1CollectedHeap, but got " +
                        heap.getClass().getName());
            }

        }
        return false;
    }

    private static boolean inSurvivor(Oop obj) {
        if (obj == null) return false;

        CollectedHeap heap = VM.getVM().getUniverse().heap();
        if (heap instanceof GenCollectedHeap) {
            DefNewGeneration gen0 = (DefNewGeneration) ((GenCollectedHeap) heap).getGen(0);
            return gen0.from().contains(obj.getHandle());
        } else if (heap instanceof ParallelScavengeHeap) {
            PSYoungGen youngGen = ((ParallelScavengeHeap) heap).youngGen();
            return youngGen.fromSpace().contains(obj.getHandle());
        } else if (heap instanceof G1CollectedHeap) {
//            ((G1CollectedHeap)heap).g1mm()
        } else {
            if (Assert.ASSERTS_ENABLED) {
                Assert.that(false, "Expecting GenCollectedHeap/ParallelScavengeHeap/G1CollectedHeap, but got " +
                        heap.getClass().getName());
            }
        }
        return false;
    }


    private static boolean inOld(Oop obj) {
        if (obj == null) return false;

        CollectedHeap heap = VM.getVM().getUniverse().heap();
        if (heap instanceof GenCollectedHeap) {
            Generation gen1 = ((GenCollectedHeap) heap).getGen(1);
            return gen1.isIn(obj.getHandle());
        } else if (heap instanceof ParallelScavengeHeap) {
            PSOldGen oldGen = ((ParallelScavengeHeap) heap).oldGen();
            return oldGen.isIn(obj.getHandle());
        } else if (heap instanceof G1CollectedHeap) {
//            ((G1CollectedHeap)heap).g1mm()
        } else {
            if (Assert.ASSERTS_ENABLED) {
                Assert.that(false, "Expecting GenCollectedHeap/ParallelScavengeHeap/G1CollectedHeap, but got " +
                        heap.getClass().getName());
            }
        }
        return false;
    }

    @Override
    public void run() {
        ObjectHeap heap = VM.getVM().getObjectHeap();
        HeapStatsVisitor heapVisitor = new HeapStatsVisitor();

        heap.iterate(heapVisitor);

        System.out.println("---------------------------------------------------------------------------------- Eden --------------------------------------------------------------------------");
        printStats(heapVisitor.getEdenStats());
        System.out.println("-------------------------------------------------------------------------------- Survivor ------------------------------------------------------------------------");
        printStats(heapVisitor.getSurvivorStats());
        System.out.println("----------------------------------------------------------------------------------- Old ---------------------------------------------------------------------------");
        printStats(heapVisitor.getOldStats());
        System.out.println("-------------------------------------------------------------------------------------------------------------------------------------------------------------------");
    }

    private void printStats(Map<String, KlassInstanceStats> stats) {
        List<Map.Entry<String, KlassInstanceStats>> instanceStats = new ArrayList<>(stats.entrySet());
        instanceStats.sort(new Comparator<Map.Entry<String, KlassInstanceStats>>() {
            @Override
            public int compare(Map.Entry<String, KlassInstanceStats> o1, Map.Entry<String, KlassInstanceStats> o2) {
                return -(o1.getValue().compareTo(o2.getValue()));
            }
        });

        System.out.printf("%100s%10s%10s", "class", "\tcount", "\tRetained Size");
        System.out.println();
        for (Map.Entry<String, KlassInstanceStats> entry : instanceStats) {
            System.out.println(String.format("%100s%10s%10s", entry.getKey(), entry.getValue().count, entry.getValue().retainedSize));
        }
    }

    private class HeapStatsVisitor implements HeapVisitor {
        private Map<String, KlassInstanceStats> edenStats = new HashMap<>();
        private Map<String, KlassInstanceStats> oldStats = new HashMap<>();
        private Map<String, KlassInstanceStats> survivorStats = new HashMap<>();
        private long totalSize;

        @Override
        public void prologue(long l) {
            totalSize = l;
        }

        @Override
        public boolean doObj(Oop oop) {

            Map<String, KlassInstanceStats> stats;

            if (inEden(oop)) {
                stats = edenStats;
            } else if (inOld(oop)) {
                stats = oldStats;
            } else if (inSurvivor(oop)) {
                stats = survivorStats;
            } else {
                System.out.println("Unknown generation: " + oop);
                return false;
            }

            String klass = oop.getKlass().getName().asString();

            if (!stats.containsKey(klass)) {
                stats.put(klass, new KlassInstanceStats());
            }


            stats.get(klass).increase(1, oop.getObjectSize());

            return false;
        }

        @Override
        public void epilogue() {

        }

        Map<String, KlassInstanceStats> getEdenStats() {
            return edenStats;
        }

        Map<String, KlassInstanceStats> getOldStats() {
            return oldStats;
        }

        Map<String, KlassInstanceStats> getSurvivorStats() {
            return survivorStats;
        }

        public long getTotalSize() {
            return totalSize;
        }
    }

    private class KlassInstanceStats implements Comparable<KlassInstanceStats> {
        private int count;
        private long retainedSize;


        void increase(int count, long retainedSize) {
            this.count += count;
            this.retainedSize += retainedSize;
        }

        @Override
        public int compareTo(KlassInstanceStats o) {
            int countCompare = Integer.compare(this.count, o.count);
            int rsCompare = Long.compare(this.retainedSize, o.retainedSize);
            return rsCompare == 0 ? countCompare : rsCompare;
        }
    }
}
