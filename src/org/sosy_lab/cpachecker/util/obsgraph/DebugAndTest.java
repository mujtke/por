package org.sosy_lab.cpachecker.util.obsgraph;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.cfa.model.CFATerminationNode;
import org.sosy_lab.cpachecker.cfa.model.FunctionExitNode;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.reachedset.ReachedSet;
import org.sosy_lab.cpachecker.cpa.arg.ARGState;
import org.sosy_lab.cpachecker.cpa.bdd.BDDState;
import org.sosy_lab.cpachecker.util.Pair;
import org.sosy_lab.cpachecker.util.globalinfo.GlobalInfo;
import org.sosy_lab.cpachecker.util.predicates.regions.NamedRegionManager;
import org.sosy_lab.cpachecker.util.predicates.regions.Region;

import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class DebugAndTest {

  private final static String dotFile = "output/ogs.dot";
  private final static String fullDotFile = "output/fullDot.json";
  private final static String fullDotFile2 = "output/fullDot2.json";
  private final static String revisitDotFile = "output/revisitDot.json";
  private final static String argFile = "output/arg.json";
  private final static String argFile2 = "output/arg2.json";
  private final static String instantOG = "output/instantOG.dot";
  private final static String modelDumpDir = "ogs-visual/model/";

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
              "[[ -e output/instantOG.pdf ]] " +
                      "&& $(which mv) output/instantOG.pdf " +
                      "output/instantOG.prev.pdf; " +
                      "$(which dot) -Tpdf " + instantOG + " -o output/instantOG.pdf"
      });

      p.waitFor();
      return p.exitValue();
    } catch (IOException | InterruptedException e) {
      //
    }

    return 1;
  }

  public static int print(NamedRegionManager nrmgr, Region region) {
    assert region != null && nrmgr != null;
    return print(nrmgr.regionToDot(region));
  }

  public static int print(BDDState bddState) {
    NamedRegionManager nrmgr = bddState.getManager();
    Region region = bddState.getRegion();
    assert region != null && nrmgr != null;
    return print(nrmgr.regionToDot(region));
  }
  static int print(String bddDot) {
    try {
      String bddDotFile = "output/instantBDD.dot";
      FileWriter fout = new FileWriter(bddDotFile);
      fout.write(bddDot);
      fout.close();

      Process p = Runtime.getRuntime().exec(new String[] {
              "/bin/bash",
              "-c",
              "[[ -e output/instantBDD.pdf ]] " +
                      "&& $(which mv) output/instantBDD.pdf " +
                      "output/instantBDD.prev.pdf; " +
                      "$(which dot) -Tpdf " + bddDotFile + " -o output/instantBDD.pdf"
      });
      p.waitFor();
      return p.exitValue();
    } catch (IOException | InterruptedException | NullPointerException e) {
      System.out.println("Exception " + e.getMessage());
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

  // Detecting whether there is a cycle from node A to B.
  public static boolean isCyclic(ObsGraph G, OGNode A, OGNode B) {

    Set<OGNode> descendantsOfA = new HashSet<>(),
            descendantsOfB = new HashSet<>(),
            visitedNodes = new HashSet<>();
    getAllDescendants(G, A, descendantsOfA, visitedNodes);
    visitedNodes.clear();
    getAllDescendants(G, B, descendantsOfB, visitedNodes);
    return descendantsOfA.contains(B) && descendantsOfB.contains(A);
  }

  // Get all direct successors of node N. Considering po, rf and fr.
  private static void getAllDescendants(ObsGraph G, OGNode N,
                                        Set<OGNode> descendants, Set<OGNode> visitedNodes) {
    if (visitedNodes.contains(N))
      return;
    N.getFromRead().forEach(frn -> {
      if (!visitedNodes.contains(frn)) {
        visitedNodes.add(frn);
        descendants.add(frn);
        getAllDescendants(G, frn, descendants, visitedNodes);
      }
    });
    N.getReadBy().forEach(rbn -> {
      if (!visitedNodes.contains(rbn)) {
        visitedNodes.add(rbn);
        descendants.add(rbn);
        getAllDescendants(G, rbn, descendants, visitedNodes);
      }
    });
    N.getSuccessors().forEach(suc -> {
      if (!visitedNodes.contains(suc)) {
        visitedNodes.add(suc);
        descendants.add(suc);
        getAllDescendants(G, suc, descendants, visitedNodes);
      }
    });
  }

  // Test for detecting empty rf relation.
  public static List<OGNode> findEmtpyRf(ObsGraph g) {
    return g.getNodes().stream().filter(n ->
        n.getRs().stream().anyMatch(r -> r.getReadFrom() == null)
            && ((n.getLhrIndex() == n.getEvents().size() - 1)
            || (n.getLhwIndex() == n.getEvents().size() - 1))
            && !n.isInGraph()
            && n.getWs().stream().anyMatch(w -> !w.getWAfter().isEmpty())
    ).collect(Collectors.toList());
  }

  // Test for LHEIndex.
  public static List<OGNode> hasInvalidLheFor(ObsGraph g) {
    return g.getNodes().stream().filter(n ->
        ((n.getLhrIndex() >=0 || n.getLhwIndex() >= 0)
            && (n.getLheIndex() != n.getLhrIndex() && n.getLheIndex() != n.getLhwIndex()))
            || (n.getLhwIndex() >= 0 && n.getLhwIndex() < n.getEvents().size() && !n.getEvents().get(n.getLhwIndex()).isWrite())
            || (n.getLhrIndex() >= 0 && n.getLhrIndex() < n.getEvents().size() && !n.getEvents().get(n.getLhrIndex()).isRead()))
        .collect(Collectors.toList());
  }

  // Test po relation.
  public static boolean testPO(ObsGraph g) {
    boolean hasPredecessor, hasSuccessor;
    for (OGNode n : g.getNodes()) {
      hasPredecessor = n.getPredecessor() != null;
      if (hasPredecessor && !n.getPredecessor().getSuccessors().contains(n))
        return false;

      hasSuccessor = !n.getSuccessors().isEmpty();
      if (hasSuccessor) {
        for (OGNode suc : n.getSuccessors()) {
          if (!Objects.equals(suc.getPredecessor(), n))
            return false;
        }
      } else if (!hasPredecessor)
        return g.getNodes().size() == 1;
    }

    return true;
  }

  // Is acyclic for mo in g?
  public static boolean acyclicMo(ObsGraph g) {
    for (OGNode n : g.getNodes()) {
      if (n.getAllMoPredecessors().contains(n))
        return true;
    }

    return false;
  }

  // Detecting whether there are some duplicated graphs in the given ARG state.
  public static boolean testRedundancy(ARGState state,
                                       Map<Integer, Map<Integer, String>> fullOGMap) {
    int stateNum = state.getStateId();
    Map<Integer, String> graphs = fullOGMap.get(stateNum);
    if (graphs == null || graphs.size() < 2) {
      return false;
    } else {
      List<String> stringsOfGraphs = new ArrayList<>(graphs.values());
//            List<ObsGraph> redundantGraphs = new ArrayList<>();
      for (int i = 0; i < stringsOfGraphs.size(); i++) {
        for (int j = i + 1; j < stringsOfGraphs.size(); j++) {
          if (Objects.equals(stringsOfGraphs.get(i), stringsOfGraphs.get(j)))
            return true;
        }
      }
    }

    return false;
  }

  // Given a graph, detect whether it contains any node without any events.
  public static boolean checkInvalidNodeFor(ObsGraph graph) {
    List<OGNode> nodes = graph.getNodes();
    return nodes.stream().anyMatch(n -> (n.getRs().isEmpty() && n.getWs().isEmpty())
            && (n.getTrAfter() != null || n.getTrBefore() != null));
  }

  // Detecting whether the transfer of some graphs gets blocked somewhere.
  public static boolean testBlocking(ARGState state) {
    Map<Integer, List<Pair<Integer, String>>> fullOGMap =
            GlobalInfo.getInstance().getOgInfo().getFullOGMap();
    if (fullOGMap.get(state.getStateId()) != null
            && !fullOGMap.get(state.getStateId()).isEmpty()) {
      assert state.getParents().size() == 1;
      ARGState parent = state.getParents().iterator().next();
      CFAEdge edge = parent.getEdgeToChild(state);
      assert edge != null;
      assert GlobalInfo.getInstance().getCFAInfo().isPresent();
      CFANode suc = edge.getSuccessor(),
              mainExitNode = GlobalInfo.getInstance().getCFAInfo().get().getCFA()
                      .getMainFunction().getExitNode();
      if (!(suc instanceof CFATerminationNode)
              && (suc != mainExitNode)
              && !(suc instanceof FunctionExitNode)
              && !edge.toString().contains("abort();")) {
        return true;
      }
    }

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

  public static int dumpToJson(ReachedSet reachedSet) {
    Map<Integer, List<Pair<Integer, String>>> fullOGMap =
            GlobalInfo.getInstance().getOgInfo().getFullOGMap();
    Map<Integer, List<String>> fullOGMap1 = new HashMap<>();
    fullOGMap.forEach((k, v) -> fullOGMap1.put(k,
            new ArrayList<>(v.stream().map(Pair::getSecondNotNull).collect(Collectors.toList()))));
//        JSONObject json = new JSONObject(fullOGMap1);
    ObjectMapper objMapper = new ObjectMapper();
    try {
      // Export ogs in json.
      FileWriter fout = new FileWriter(fullDotFile);

      String fullOGMap1JsonString =
              objMapper.writerWithDefaultPrettyPrinter().writeValueAsString(fullOGMap1);
      fout.write(fullOGMap1JsonString);
//            json.write(fout, 4, 0);
      fout.close();

      // Export arg.
      ARG arg = getARG(reachedSet, fullOGMap1, null);
      String argJsonString =
              objMapper.writerWithDefaultPrettyPrinter().writeValueAsString(arg);
      fout = new FileWriter(argFile);
      fout.write(argJsonString);
//            json = new JSONObject(arg);
//            json.write(fout, 4, 0);
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
      p.waitFor();
    } catch (IOException | InterruptedException e) {
      throw new RuntimeException(e);
    }

    return 0;
  }

  public static int dumpToJson2(ReachedSet reachedSet) {
    Map<Integer, List<Pair<Integer, String>>> fullOGMap =
            GlobalInfo.getInstance().getOgInfo().getFullOGMap();
    Map<Integer, Map<Integer, List<String>>> revisitOGMap =
            GlobalInfo.getInstance().getOgInfo().getRevisitOGMap();
    ObjectMapper objMapper = new ObjectMapper();
    try {
      // Export { stateNum -> [ (og_id, og_str), ... ] }.
      Map<Integer, List<OGItem>> fullOGMap0 = new HashMap<>();

      fullOGMap.forEach((k, v) -> fullOGMap0.put(k,
              v.stream().map(p -> new OGItem(p.getFirstNotNull(),
                      p.getSecondNotNull())).collect(Collectors.toList())));
//            JSONObject fullOGMap0Json = new JSONObject(fullOGMap0);
      String fullOGMap0JsonString =
              objMapper.writerWithDefaultPrettyPrinter().writeValueAsString(fullOGMap0);
      FileWriter fout =  new FileWriter(fullDotFile2);
      fout.write(fullOGMap0JsonString);
//            fullOGMap0Json.write(fout, 4, 0);
      fout.close();
      // Export { stateNum -> { og_id -> [ revisit_og_str, ... ] }.
      fout = new FileWriter(revisitDotFile);

      String revisitOGMapJsonString =
              objMapper.writerWithDefaultPrettyPrinter().writeValueAsString(revisitOGMap);
      fout.write(revisitOGMapJsonString);
//            revisitOGMapJson.write(fout, 4, 0);
      fout.close();

      // Export arg.
      Map<Integer, List<String>> fullOGMap1 = new HashMap<>();
      fullOGMap.forEach((k, v) -> fullOGMap1.put(k,
              new ArrayList<>(v.stream().map(Pair::getSecondNotNull).collect(Collectors.toList()))));
      ARG arg = getARG(reachedSet, fullOGMap1, revisitOGMap);
      fout = new FileWriter(argFile2);
      String argJsonString = objMapper.writerWithDefaultPrettyPrinter().writeValueAsString(arg);
      fout.write(argJsonString);
//            JSONObject argJson = new JSONObject(arg);
//            argJson.write(fout, 4, 0);
      fout.close();

      Process p = Runtime.getRuntime().exec(new String[] {
              "/bin/bash",
              "-c",
              "$(which cp) " + fullDotFile2 + " " + modelDumpDir + "; "
                      + "$(which cp) " + revisitDotFile + " " + modelDumpDir + "; "
                      + "$(which cp) " + argFile2 + " " + modelDumpDir
      });
      p.waitFor();
    } catch (IOException | InterruptedException e) {
      throw new RuntimeException(e);
    }
    return 0;
  }

  private static String getARGStateFillColor(Map<Integer, Map<Integer, List<String>>> revisitOGMap,
                                             int curStateId) {
    if (revisitOGMap != null) {
      Map<Integer, List<String>> revisitOgs = revisitOGMap.get(curStateId);
      if (revisitOgs != null && !revisitOgs.isEmpty())
        return "orange";
    }
    return "white";
  }
  private static ARG getARG(ReachedSet reachedSet,
                            Map<Integer, List<String>> fullOGMap,
                            Map<Integer, Map<Integer, List<String>>> revisitOGMap) {
    ARG arg = new ARG();
    ARGState s = (ARGState) reachedSet.getFirstState();
    assert s != null;
    Stack<ARGState> stack = new Stack<>();
    stack.push(s);
    while (!stack.isEmpty()) {
      ARGState cur = stack.pop(), par;
      if (cur.getChildren().isEmpty()
              && (!fullOGMap.containsKey(cur.getStateId())
              || fullOGMap.get(cur.getStateId()) == null
              || fullOGMap.get(cur.getStateId()).isEmpty())) {
//                    continue; // Has neither children nor graph.
      }
      cur.getChildren().forEach(stack::push);
      int curStateId = cur.getStateId();
      if (cur.getParents().isEmpty()) {
        ARG.State state = new ARG.State(String.valueOf(curStateId),
                "s" + curStateId,
                getARGStateFillColor(revisitOGMap, curStateId));
        arg.getReached().add(state);
        continue;
      }
      // One parent at most (Assume).
      par = cur.getParents().iterator().next();
      int parStateId = par.getStateId();
      ARG.State state = new ARG.State(String.valueOf(curStateId),
              "s" + curStateId,
              getARGStateFillColor(revisitOGMap, curStateId));
      ARG.Edge edge = new ARG.Edge(String.valueOf(parStateId),
              String.valueOf(curStateId),
              Objects.requireNonNull(par.getEdgeToChild(cur)).toString());
      arg.getReached().add(state);
      arg.getEdges().add(edge);
    }
    return arg;
  }

  public static class ARG {
    public static class State {
      public State(String pKey, String pStateNum, String pFillColor) {
        this.key = pKey;
        this.stateNum = pStateNum;
        this.fillColor = pFillColor;
      }
      private final String key;
      private final String stateNum;
      private final String fillColor;

      public String getKey() { return key; }

      public String getStateNum() { return stateNum; }
      public String getFillColor() { return fillColor; }
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

  private static class OGItem {
    private final Integer id;
    private final String dotStr;

    public OGItem(Integer pId, String pDotStr) {
      this.id = pId;
      this.dotStr = pDotStr;
    }

    public Integer getId() { return this.id; }
    public String getDotStr() { return this.dotStr; }
  }
}