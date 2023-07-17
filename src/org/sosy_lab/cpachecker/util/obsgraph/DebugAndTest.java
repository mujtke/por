package org.sosy_lab.cpachecker.util.obsgraph;

import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DebugAndTest {

    private final static String dotFile = "output/ogs.dot";

    /**
     * Print all relations in graph g.
     */
    public static void getDot(ObsGraph g) {
        Map<OGNode, String> visited = new HashMap<>();
        try {
            FileWriter fout = new FileWriter(dotFile);
            fout.write("digraph {\n");
            for (OGNode n : g.getNodes()) {
                // Node.
                if (!visited.containsKey(n)) {
                    addNewNode(fout, n, visited);
                }
                String nStmt = visited.get(n);
                // Trace order.
                OGNode tb = n.getTrBefore();
                if (tb != null && tb.isInGraph()) {
                    if (!visited.containsKey(tb)) addNewNode(fout, tb, visited);
                    String tbStmt = visited.get(tb);
                    fout.write("\t" + nStmt + " -> " + tbStmt
                            + " [style=bold, color=red];\n");
                }
                // Successors.
                List<OGNode> sucs = n.getSuccessors();
                for (OGNode suc : sucs) {
                    if (!visited.containsKey(suc)) addNewNode(fout, suc, visited);
                    String sucStmt = visited.get(suc);
                    fout.write("\t" + nStmt + " -> " + sucStmt + ";\n");
                }
                // ReadBy.
                List<OGNode> rbns = n.getReadBy();
                for (OGNode rbn : rbns) {
                    if (!visited.containsKey(rbn)) addNewNode(fout, rbn, visited);
                    String rbnStmt = visited.get(rbn);
                    fout.write("\t" + nStmt + " -> " + rbnStmt
                            + " [style=dotted, color=green, penwidth=2.0];\n");
                }
                // Wb.
                List<OGNode> wbns = n.getWBefore();
                for (OGNode wbn : wbns) {
                    if (!visited.containsKey(wbn)) addNewNode(fout, wbn, visited);
                    String wbnStmt = visited.get(wbn);
                    fout.write("\t" + nStmt + " -> " + wbnStmt
                            + " [style=dotted, color=brown, penwidth=2.0];\n");
                }
                // Mo before.
                List<OGNode> mbns = n.getMoBefore();
                for (OGNode mbn : mbns) {
                    if (!visited.containsKey(mbn)) addNewNode(fout, mbn, visited);
                    String mbnStmt = visited.get(mbn);
                    fout.write("\t" + nStmt + " -> " + mbnStmt
                            + " [style=dotted, color=orange, penwidth=2.0];\n");
                }
                // From read.
                List<OGNode> frns = n.getFromRead();
                for (OGNode frn : frns) {
                    if (!visited.containsKey(frn)) addNewNode(fout, frn, visited);
                    String frnStmt = visited.get(frn);
                    fout.write("\t" + nStmt + " -> " + frnStmt
                            + " [style=dotted, color=pink, penwidth=2.0];\n");
                }
            }
            fout.write("}");
            fout.close();

            // Process .dot file in shell.
            Process p = Runtime.getRuntime().exec(new String[] {
                    "/bin/bash",
                    "-c",
                    "/usr/bin/dot -Tpdf " + dotFile + " -o output/ogs.pdf"
            });
            //System.out.println(p.info());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void addNewNode(FileWriter fout, OGNode n,
                                   Map<OGNode, String> visited) throws IOException {
        String nodeStmt = String.valueOf(n.hashCode()),
                nodeLabel = "[label=\""
                        + n.toString().replace(",", "\n")
                        + "\", fontsize=10.0]",
                nodeDeclaration = nodeStmt + " " + nodeLabel;
        if (n.isInGraph()) {
            nodeDeclaration = nodeDeclaration + "\n"
                    + nodeStmt + " [style=filled, color=\"lightgreen\"]";
        }
        fout.write("\t" + nodeDeclaration + ";\n");
        visited.put(n, nodeStmt);
    }

    public static void getAllDot(ObsGraph g) {
        Map<OGNode, String> visited = new HashMap<>();
        try {
            FileWriter fout = new FileWriter(dotFile);
            fout.write("digraph {\n");
            for (OGNode n : g.getNodes()) {
                if (!visited.containsKey(n)) {
                    addNewNode(fout, n, visited);
                }
                String nStmt = visited.get(n);
                // Trace order.
                OGNode tb = n.getTrBefore();
                if (tb != null) {
                    if (!visited.containsKey(tb)) addNewNode(fout, tb, visited);
                    String tbStmt = visited.get(tb);
                    fout.write("\t" + nStmt + " -> " + tbStmt
                            + " [style=bold, color=red];\n");
                }
                // Successors.
                List<OGNode> sucs = n.getSuccessors();
                for (OGNode suc : sucs) {
                    if (!visited.containsKey(suc)) addNewNode(fout, suc, visited);
                    String sucStmt = visited.get(suc);
                    fout.write("\t" + nStmt + " -> " + sucStmt + ";\n");
                }
                // Read By.
                List<OGNode> rbns = n.getReadBy();
                for (OGNode rbn : rbns) {
                    if (!visited.containsKey(rbn)) addNewNode(fout, rbn, visited);
                    String rbnStmt = visited.get(rbn);
                    fout.write("\t" + nStmt + " -> " + rbnStmt
                            + " [style=dotted, color=green, penwidth=2.0];\n");
                }
                // Wb.
                List<OGNode> wbns = n.getWBefore();
                for (OGNode wbn : wbns) {
                    if (!visited.containsKey(wbn)) addNewNode(fout, wbn, visited);
                    String wbnStmt = visited.get(wbn);
                    fout.write("\t" + nStmt + " -> " + wbnStmt
                            + " [style=dotted, color=brown, penwidth=2.0];\n");
                }
                // Mo Before.
                List<OGNode> mbns = n.getMoBefore();
                for (OGNode mbn : mbns) {
                    if (!visited.containsKey(mbn)) addNewNode(fout, mbn, visited);
                    String mbnStmt = visited.get(mbn);
                    fout.write("\t" + nStmt + " -> " + mbnStmt
                            + " [style=dotted, color=orange, penwidth=2.0];\n");
                }
                // From read.
                List<OGNode> frns = n.getFromRead();
                for (OGNode frn : frns) {
                    if (!visited.containsKey(frn)) addNewNode(fout, frn, visited);
                    String frnStmt = visited.get(frn);
                    fout.write("\t" + nStmt + " -> " + frnStmt
                            + " [style=dotted, color=pink, penwidth=2.0];\n");
                }
            }
            fout.write("}");
            fout.close();

            // Process .dot file in shell.
            Process p = Runtime.getRuntime().exec(new String[] {
                    "/bin/bash",
                    "-c",
                    "/usr/bin/dot -Tpdf " + dotFile + " -o output/ogs-all.pdf"
            });
//            System.out.println(p.info());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static boolean isCyclic() {

        return false;
    }
}
