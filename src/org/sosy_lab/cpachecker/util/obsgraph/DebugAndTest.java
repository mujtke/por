package org.sosy_lab.cpachecker.util.obsgraph;

import org.json.JSONObject;
import org.sosy_lab.cpachecker.core.reachedset.ReachedSet;
import org.sosy_lab.cpachecker.cpa.arg.ARGState;
import org.sosy_lab.cpachecker.util.globalinfo.GlobalInfo;

import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

public class DebugAndTest {

    private final static String dotFile = "output/ogs.dot";
    private final static String fullDotFile = "output/fullDot.json";
    private final static String argFile = "output/arg.json";
    private final static String instantOG = "output/instantOG.dot";

    public static int print(ObsGraph g) {
        String dotStr = getDotStr(g);
        try {
            // Write dotStr to the file 'output/instantOG.dot'.
            FileWriter fout = new FileWriter(instantOG);
            fout.write(dotStr);
            fout.close();

            Process p = Runtime.getRuntime().exec(new String[] {
                    "/bin/bash",
                    "-c",
                    "/usr/bin/dot -Tpdf " + instantOG + " -o output/instantOG.pdf"
            });

            p.waitFor();
            return p.exitValue();
        } catch (IOException | InterruptedException e) {
            //
        }

        return 1;
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

    private static void strBuilderAddNewNode(StringBuilder strBuilder, OGNode n,
                                   Map<OGNode, String> visited) {
        String nodeStmt = String.valueOf(n.hashCode()),
                nodeLabel = "[label=\""
                        + n.toString().replace(",", "\n")
                        + "\", fontsize=10.0]",
                nodeDeclaration = nodeStmt + " " + nodeLabel;
        if (n.isInGraph()) {
            nodeDeclaration = nodeDeclaration + "\n"
                    + nodeStmt + " [style=filled, color=\"lightgreen\"]";
        }
        strBuilder.append("\t")
                .append(nodeDeclaration).
                append(";\n");
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
    public static String getDotStr(ObsGraph g) {
        Map<OGNode, String> visited = new HashMap<>();
        StringBuilder strBuilder = new StringBuilder();
        strBuilder.append("digraph {\n");
        for (OGNode n : g.getNodes()) {
            // Node.
            if (!visited.containsKey(n)) {
                strBuilderAddNewNode(strBuilder, n, visited);
            }
            String nStmt = visited.get(n);
            // Trace order.
            OGNode tb = n.getTrBefore();
            if (tb != null && tb.isInGraph()) {
                if (!visited.containsKey(tb)) strBuilderAddNewNode(strBuilder, tb,
                        visited);
                String tbStmt = visited.get(tb);
                strBuilder.append("\t")
                        .append(nStmt)
                        .append(" -> ")
                        .append(tbStmt)
                        .append(" [style=bold, color=red];\n");
            }
            // Successors.
            List<OGNode> sucs = n.getSuccessors();
            for (OGNode suc : sucs) {
                if (!visited.containsKey(suc)) strBuilderAddNewNode(strBuilder, suc,
                        visited);
                String sucStmt = visited.get(suc);
                strBuilder.append("\t")
                        .append(nStmt)
                        .append(" -> ")
                        .append(sucStmt)
                        .append(";\n");
            }
            // ReadBy.
            List<OGNode> rbns = n.getReadBy();
            for (OGNode rbn : rbns) {
                if (!visited.containsKey(rbn)) strBuilderAddNewNode(strBuilder, rbn,
                        visited);
                String rbnStmt = visited.get(rbn);
                strBuilder.append("\t")
                        .append(nStmt)
                        .append(" -> ")
                        .append(rbnStmt)
                        .append(" [style=dotted, color=green, penwidth=2.0];\n");
            }
            // Wb.
            List<OGNode> wbns = n.getWBefore();
            for (OGNode wbn : wbns) {
                if (!visited.containsKey(wbn)) strBuilderAddNewNode(strBuilder, wbn,
                        visited);
                String wbnStmt = visited.get(wbn);
                strBuilder.append("\t")
                        .append(nStmt)
                        .append(" -> ")
                        .append(wbnStmt)
                        .append(" [style=dotted, color=brown, penwidth=2.0];\n");
            }
            // Mo before.
            List<OGNode> mbns = n.getMoBefore();
            for (OGNode mbn : mbns) {
                if (!visited.containsKey(mbn)) strBuilderAddNewNode(strBuilder, mbn,
                        visited);
                String mbnStmt = visited.get(mbn);
                strBuilder.append("\t")
                        .append(nStmt)
                        .append(" -> ")
                        .append(mbnStmt)
                        .append(" [style=dotted, color=orange, penwidth=2.0];\n");
            }
            // From read.
            List<OGNode> frns = n.getFromRead();
            for (OGNode frn : frns) {
                if (!visited.containsKey(frn)) strBuilderAddNewNode(strBuilder, frn,
                        visited);
                String frnStmt = visited.get(frn);
                strBuilder.append("\t")
                        .append(nStmt)
                        .append(" -> ")
                        .append(frnStmt)
                        .append(" [style=dotted, color=pink, penwidth=2.0];\n");
            }
        }
        strBuilder.append("}");

        return strBuilder.toString();
    }

    public static void dumpToJson(ReachedSet reachedSet) {
        Map<Integer, List<String>> fullOGMap =
                GlobalInfo.getInstance().getOgInfo().getFullOGMap();
        JSONObject json = new JSONObject(fullOGMap);
        try {
            // Export ogs in json.
            FileWriter fout = new FileWriter(fullDotFile);
            json.write(fout, 4, 0);
            fout.close();

            // Export arg.
            ARG arg = new ARG();
            ARGState s = (ARGState) reachedSet.getFirstState();
            assert s != null;
            Stack<ARGState> stack = new Stack<>();
            stack.push(s);
            while (!stack.isEmpty()) {
                ARGState cur = stack.pop(), par;
                cur.getChildren().forEach(stack::push);
                int curStateId = cur.getStateId();
                if (cur.getParents().isEmpty()) {
                    ARG.State state = new ARG.State(String.valueOf(curStateId),
                            "s" + curStateId);
                    arg.getReached().add(state);
                    continue;
                }
                // One parent at most (Assume).
                par = cur.getParents().iterator().next();
                int parStateId = par.getStateId();
                ARG.State state = new ARG.State(String.valueOf(curStateId),
                        "s" + curStateId);
                ARG.Edge edge = new ARG.Edge(String.valueOf(parStateId),
                        String.valueOf(curStateId),
                        Objects.requireNonNull(par.getEdgeToChild(cur)).toString());
                arg.getReached().add(state);
                arg.getEdges().add(edge);
            }
            fout = new FileWriter(argFile);
            json = new JSONObject(arg);
            json.write(fout, 4, 0);
            fout.close();

            // Debug.
            // Copy fullDotFile and argFile to the destination dir.
            Process p = Runtime.getRuntime().exec(new String[] {
                    "/bin/bash",
                    "-c",
                    "$(which cp) " + fullDotFile + " $HOME/mmm/js/ogs-visual" +
                            "/model/; $(which cp) " + argFile + " $HOME/mmm/js" +
                            "/ogs-visual/model/"
            });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static class ARG {
        public static class State {
            public State(String pKey, String pStateNum) {
                this.key = pKey;
                this.stateNum = pStateNum;
            }
            private final String key;
            private final String stateNum;

            public String getKey() {
                return key;
            }

            public String getStateNum() {
                return stateNum;
            }
        }

        public static class Edge {
            public Edge(String from, String to, String stmt) {
                this.from = from;
                this.to  = to;
                this.stmt = stmt;
            }
            private final String from;
            private final String to;
            private final String stmt;

            public String getFrom() {
                return from;
            }

            public String getTo() {
                return to;
            }

            public String getStmt() {
                return stmt;
            }
        }
        private final List<State> reached = new ArrayList<>();
        private final List<Edge> edges = new ArrayList<>();
        public List<Edge> getEdges() {
            return edges;
        }
        public List<State> getReached() {
            return reached;
        }
    }
}
