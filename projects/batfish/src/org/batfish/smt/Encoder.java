package org.batfish.smt;

import com.microsoft.z3.*;
import org.batfish.common.BatfishException;
import org.batfish.common.plugin.IBatfish;
import org.batfish.datamodel.*;
import org.batfish.datamodel.routing_policy.RoutingPolicy;
import org.batfish.datamodel.routing_policy.expr.*;
import org.batfish.datamodel.routing_policy.expr.IntExpr;
import org.batfish.datamodel.routing_policy.statement.*;

import java.util.*;

import static org.batfish.datamodel.routing_policy.statement.Statements.*;

// Features:
// ---------
//   - BGP community values (ignore regex for now)
//   - Avoid loops in BGP when non-standard (or non-common) local-pref internally
//   - iBGP by comparing local-pref internally
//     * Requires reachability, and no ACLs for loopbacks
//   - maximum path length by protocol
//   - RIP, EIGRP routing protocols
//
// Environment stuff:
// ------------------
//   - Ensure Local preference/MED transitive depending on local/remote/send community AS?
//
// Small items:
// ------------
//   - Add upper bounds for variables (what to do with overflow?)
//   - Ensure distance is transfered over with redistribution
//   - Compute multipath correctly (how do we handle some multipath)


/**
 * An object that is responsible for creating a symbolic representation
 * of both the network control and data planes.
 *
 */
public class Encoder {

    static final boolean ENABLE_DEBUGGING = false;

    static final int BITS = 0;

    static final int DEFAULT_CISCO_VLAN_OSPF_COST = 1;

    static final String BGP_NETWORK_FILTER_LIST_NAME = "BGP_NETWORK_NETWORKS_FILTER";

    static final String BGP_COMMON_FILTER_LIST_NAME = "BGP_COMMON_EXPORT_POLICY";

    // static final String BGP_AGGREGATE_FILTER_LIST_NAME = "BGP_AGGREGATE_NETWORKS_FILTER";
    // Globally unique identifier for distinguishing instances of variables
    private static int encodingId = 0;

    private List<Prefix> _destinations;

    private Optimizations _optimizations;

    private LogicalGraph _logicalGraph;

    private SymbolicDecisions _symbolicDecisions;

    private SymbolicFailures _symbolicFailures;

    private SymbolicPacket _symbolicPacket;

    private Map<Interface, BoolExpr> _inboundAcls;

    private Map<Interface, BoolExpr> _outboundAcls;

    private List<Expr> _allVariables;

    private List<SymbolicRecord> _allSymbolicRecords;

    private Context _ctx;

    private Solver _solver;

    private UnsatCore _unsatCore;

    private Set<String> _communities;


    public Encoder(IBatfish batfish, List<Prefix> destinations) {
        this(destinations, new Graph(batfish));
    }

    public Encoder(List<Prefix> destinations, Graph graph) {
        this(destinations, graph, null, null, null);
    }

    private Encoder(
            List<Prefix> destinations, Graph graph, Context ctx, Solver solver, List<Expr> vars) {
        HashMap<String, String> cfg = new HashMap<>();

        // allows for unsat core when debugging
        if (ENABLE_DEBUGGING) {
            cfg.put("proof", "true");
            cfg.put("auto-config", "false");
        }

        _ctx = (ctx == null ? new Context(cfg) : ctx);

        if (solver == null) {
            if (ENABLE_DEBUGGING) {
                _solver = _ctx.mkSolver();
            } else {
                Tactic t1 = _ctx.mkTactic("simplify");
                Tactic t2 = _ctx.mkTactic("solve-eqs");
                Tactic t3 = _ctx.mkTactic("smt");
                Tactic t = _ctx.then(t1, t2, t3);
                _solver = _ctx.mkSolver(t);
            }
        } else {
            _solver = solver;
            encodingId = encodingId + 1;
        }

        _destinations = destinations;
        _optimizations = new Optimizations(this);
        _logicalGraph = new LogicalGraph(graph);
        _symbolicDecisions = new SymbolicDecisions();
        _symbolicFailures = new SymbolicFailures();
        _symbolicPacket = new SymbolicPacket(_ctx, encodingId);
        _communities = new HashSet<>();
        _allSymbolicRecords = new ArrayList<>();

        if (vars == null) {
            _allVariables = new ArrayList<>();
        } else {
            _allVariables = vars;
        }

        _allVariables.add(_symbolicPacket.getDstIp());
        _allVariables.add(_symbolicPacket.getSrcIp());
        _allVariables.add(_symbolicPacket.getDstPort());
        _allVariables.add(_symbolicPacket.getSrcPort());
        _allVariables.add(_symbolicPacket.getIcmpCode());
        _allVariables.add(_symbolicPacket.getIcmpType());
        _allVariables.add(_symbolicPacket.getTcpAck());
        _allVariables.add(_symbolicPacket.getTcpCwr());
        _allVariables.add(_symbolicPacket.getTcpEce());
        _allVariables.add(_symbolicPacket.getTcpFin());
        _allVariables.add(_symbolicPacket.getTcpPsh());
        _allVariables.add(_symbolicPacket.getTcpRst());
        _allVariables.add(_symbolicPacket.getTcpSyn());
        _allVariables.add(_symbolicPacket.getTcpUrg());
        _allVariables.add(_symbolicPacket.getIpProtocol());

        _inboundAcls = new HashMap<>();
        _outboundAcls = new HashMap<>();

        _unsatCore = new UnsatCore(ENABLE_DEBUGGING);
    }

    public Encoder(Encoder e, Graph graph) {
        this(e.getDestinations(), graph, e.getCtx(), e.getSolver(), e.getAllVariables());
    }

    public List<Prefix> getDestinations() {
        return _destinations;
    }

    public Context getCtx() {
        return _ctx;
    }

    public Solver getSolver() {
        return _solver;
    }

    public List<Expr> getAllVariables() {
        return _allVariables;
    }

    public LogicalGraph getLogicalGraph() {
        return _logicalGraph;
    }

    public SymbolicDecisions getSymbolicDecisions() {
        return _symbolicDecisions;
    }

    public SymbolicPacket getSymbolicPacket() {
        return _symbolicPacket;
    }

    public Map<Interface, BoolExpr> getIncomingAcls() {
        return _inboundAcls;
    }

    public Map<Interface, BoolExpr> getOutgoingAcls() {
        return _outboundAcls;
    }

    private void add(BoolExpr e) {
        _unsatCore.track(_solver, _ctx, e);
    }

    private BoolExpr If(BoolExpr cond, BoolExpr case1, BoolExpr case2) {
        return (BoolExpr) _ctx.mkITE(cond, case1, case2);
    }

    private BoolExpr True() {
        return _ctx.mkBool(true);
    }

    private BoolExpr False() {
        return _ctx.mkBool(false);
    }

    private BoolExpr Bool(boolean val) {
        return _ctx.mkBool(val);
    }

    private BoolExpr Not(BoolExpr e) {
        return _ctx.mkNot(e);
    }

    private BoolExpr And(BoolExpr... vals) {
        return _ctx.mkAnd(vals);
    }

    private BoolExpr Or(BoolExpr... vals) {
        return _ctx.mkOr(vals);
    }

    private BoolExpr Implies(BoolExpr e1, BoolExpr e2) {
        return _ctx.mkImplies(e1, e2);
    }

    private BoolExpr Eq(Expr e1, Expr e2) {
        return _ctx.mkEq(e1, e2);
    }

    private BoolExpr Ge(ArithExpr e1, ArithExpr e2) {
        return _ctx.mkGe(e1, e2);
    }

    private BoolExpr Le(ArithExpr e1, ArithExpr e2) {
        return _ctx.mkLe(e1, e2);
    }

    private BoolExpr Gt(ArithExpr e1, ArithExpr e2) {
        return _ctx.mkGt(e1, e2);
    }

    private BoolExpr Lt(ArithExpr e1, ArithExpr e2) {
        return _ctx.mkLt(e1, e2);
    }

    private ArithExpr Int(long l) {
        return _ctx.mkInt(l);
    }

    private ArithExpr Sum(ArithExpr e1, ArithExpr e2) {
        return _ctx.mkAdd(e1, e2);
    }

    private ArithExpr Sub(ArithExpr e1, ArithExpr e2) {
        return _ctx.mkSub(e1, e2);
    }

    public boolean overlaps(Prefix p1, Prefix p2) {
        long l1 = p1.getNetworkPrefix().getAddress().asLong();
        long l2 = p2.getNetworkPrefix().getAddress().asLong();
        long u1 = p1.getNetworkPrefix().getEndAddress().asLong();
        long u2 = p2.getNetworkPrefix().getEndAddress().asLong();
        return (l1 >= l2 && l1 <= u2) || (u1 <= u2 && u1 >= l2) || (u2 >= l1 && u2 <= u1) || (l2
                >= l1 && l2 <= u1);
    }

    private void addExprs(SymbolicRecord e) {
        _allVariables.add(e.getPermitted());
        if (e.getAdminDist() != null) {
            _allVariables.add(e.getAdminDist());
        }
        if (e.getMed() != null) {
            _allVariables.add(e.getMed());
        }
        if (e.getLocalPref() != null) {
            _allVariables.add(e.getLocalPref());
        }
        if (e.getMetric() != null) {
            _allVariables.add(e.getMetric());
        }
        if (e.getPrefixLength() != null) {
            _allVariables.add(e.getPrefixLength());
        }
        if (e.getRouterId() != null) {
            _allVariables.add(e.getRouterId());
        }
    }

    private void addChoiceVariables() {
        getGraph().getEdgeMap().forEach((router, edges) -> {
            Configuration conf = getGraph().getConfigurations().get(router);

            Map<RoutingProtocol, Map<LogicalEdge, BoolExpr>> map = new HashMap<>();
            _symbolicDecisions.getChoiceVariables().put(router, map);

            for (RoutingProtocol proto : getGraph().getProtocols().get(router)) {

                Map<LogicalEdge, BoolExpr> edgeMap = new HashMap<>();
                map.put(proto, edgeMap);

                for (LogicalEdge e : collectAllImportLogicalEdges(router, conf, proto)) {
                    String chName = e.getSymbolicRecord().getName() + "_choice";
                    BoolExpr choiceVar = _ctx.mkBoolConst(chName);
                    _allVariables.add(choiceVar);
                    edgeMap.put(e, choiceVar);
                }
            }
        });
    }

    private void buildEdgeMap() {
        getGraph().getEdgeMap().forEach((router, edges) -> {
            for (RoutingProtocol p : getGraph().getProtocols().get(router)) {
                _logicalGraph.getLogicalGraphEdges().put(router, p, new ArrayList<>());
            }
        });
    }

    private void addForwardingVariables() {
        getGraph().getEdgeMap().forEach((router, edges) -> {
            for (GraphEdge edge : edges) {
                String iface = edge.getStart().getName();

                String cName = "control-forwarding_" + router + "_" + iface;
                BoolExpr cForward = _ctx.mkBoolConst(cName);
                _allVariables.add(cForward);
                _symbolicDecisions.getControlForwarding().put(router, edge, cForward);

                String dName = "data-forwarding_" + router + "_" + iface;
                BoolExpr dForward = _ctx.mkBoolConst(dName);
                _allVariables.add(dForward);
                _symbolicDecisions.getDataForwarding().put(router, edge, dForward);
            }
        });
    }

    private void addBestVariables() {
        getGraph().getEdgeMap().forEach((router, edges) -> {
            for (int len = 0; len <= BITS; len++) {
                SymbolicRecord evBest = new SymbolicRecord(router, RoutingProtocol.AGGREGATE,
                        "OVERALL", _optimizations, "none", _ctx, len, "BEST", true);
                addExprs(evBest);
                _allSymbolicRecords.add(evBest);
                _symbolicDecisions.getBestNeighbor().put(router, evBest);
            }
        });

        getGraph().getEdgeMap().forEach((router, edges) -> {
            if (!_optimizations.getSliceHasSingleProtocol().contains(router)) {
                for (RoutingProtocol proto : getGraph().getProtocols().get(router)) {
                    for (int len = 0; len <= BITS; len++) {
                        SymbolicRecord evBest = new SymbolicRecord(router, proto, proto
                                .protocolName(), _optimizations, "none", _ctx, len, "BEST", true);
                        addExprs(evBest);
                        _allSymbolicRecords.add(evBest);
                        _symbolicDecisions.getBestNeighborPerProtocol().put(router, proto, evBest);
                    }
                }
            }
        });
    }

    private void addSymbolicRecords() {
        Map<String, EnumMap<RoutingProtocol, Map<GraphEdge, ArrayList<LogicalGraphEdge>>>>
                importInverseMap = new HashMap<>();
        Map<String, EnumMap<RoutingProtocol, Map<GraphEdge, ArrayList<LogicalGraphEdge>>>>
                exportInverseMap = new HashMap<>();
        Map<String, EnumMap<RoutingProtocol, SymbolicRecord>> singleExportMap = new HashMap<>();

        // add edge EXPORT and IMPORT state variables
        getGraph().getEdgeMap().forEach((router, edges) -> {

            EnumMap<RoutingProtocol, SymbolicRecord> singleProtoMap;
            singleProtoMap = new EnumMap<>(RoutingProtocol.class);
            EnumMap<RoutingProtocol, Map<GraphEdge, ArrayList<LogicalGraphEdge>>> importEnumMap;
            importEnumMap = new EnumMap<>(RoutingProtocol.class);
            EnumMap<RoutingProtocol, Map<GraphEdge, ArrayList<LogicalGraphEdge>>> exportEnumMap;
            exportEnumMap = new EnumMap<>(RoutingProtocol.class);

            singleExportMap.put(router, singleProtoMap);
            importInverseMap.put(router, importEnumMap);
            exportInverseMap.put(router, exportEnumMap);

            for (RoutingProtocol proto : getGraph().getProtocols().get(router)) {

                boolean useSingleExport = _optimizations.getSliceCanKeepSingleExportVar().get
                        (router).get(proto);

                Map<GraphEdge, ArrayList<LogicalGraphEdge>> importGraphEdgeMap = new HashMap<>();
                Map<GraphEdge, ArrayList<LogicalGraphEdge>> exportGraphEdgeMap = new HashMap<>();

                importEnumMap.put(proto, importGraphEdgeMap);
                exportEnumMap.put(proto, exportGraphEdgeMap);

                for (GraphEdge e : edges) {

                    Interface iface = e.getStart();
                    Configuration conf = getGraph().getConfigurations().get(router);

                    if (getGraph().isInterfaceUsed(conf, proto, iface)) {

                        ArrayList<LogicalGraphEdge> importEdgeList = new ArrayList<>();
                        ArrayList<LogicalGraphEdge> exportEdgeList = new ArrayList<>();
                        importGraphEdgeMap.put(e, importEdgeList);
                        exportGraphEdgeMap.put(e, exportEdgeList);

                        for (int len = 0; len <= BITS; len++) {

                            String ifaceName = e.getStart().getName();

                            // If we use a single set of export variables, then make sure
                            // to reuse the existing variables instead of creating new ones
                            if (useSingleExport) {
                                SymbolicRecord singleVars = singleExportMap.get(router).get(proto);
                                SymbolicRecord ev1;
                                if (singleVars == null) {
                                    String name = proto.protocolName();
                                    ev1 = new SymbolicRecord(router, proto, name, _optimizations,
                                            "", _ctx, len, "SINGLE-EXPORT", false);
                                    singleProtoMap.put(proto, ev1);
                                    addExprs(ev1);
                                    _allSymbolicRecords.add(ev1);
                                } else {
                                    ev1 = singleVars;
                                }
                                LogicalGraphEdge eExport = new LogicalGraphEdge(e, EdgeType
                                        .EXPORT, len, ev1);
                                exportEdgeList.add(eExport);

                            } else {
                                String name = proto.protocolName();
                                SymbolicRecord ev1 = new SymbolicRecord(router, proto, name,
                                        _optimizations, ifaceName, _ctx, len, "EXPORT", false);
                                LogicalGraphEdge eExport = new LogicalGraphEdge(e, EdgeType
                                        .EXPORT, len, ev1);
                                exportEdgeList.add(eExport);
                                addExprs(ev1);
                                _allSymbolicRecords.add(ev1);
                            }

                            boolean notNeeded = _optimizations.getSliceCanCombineImportExportVars
                                    ().get(router).get(proto).contains(e);

                            if (notNeeded) {
                                SymbolicRecord ev2 = new SymbolicRecord(router, proto
                                        .protocolName(), ifaceName, len, "IMPORT");
                                LogicalGraphEdge eImport = new LogicalGraphEdge(e, EdgeType
                                        .IMPORT, len, ev2);
                                importEdgeList.add(eImport);
                            } else {
                                String name = proto.protocolName();
                                SymbolicRecord ev2 = new SymbolicRecord(router, proto, name,
                                        _optimizations, ifaceName, _ctx, len, "IMPORT", false);
                                LogicalGraphEdge eImport = new LogicalGraphEdge(e, EdgeType
                                        .IMPORT, len, ev2);
                                importEdgeList.add(eImport);
                                addExprs(ev2);
                                _allSymbolicRecords.add(ev2);
                            }
                        }

                        List<ArrayList<LogicalGraphEdge>> es = _logicalGraph.getLogicalGraphEdges
                                ().get(router, proto);
                        ArrayList<LogicalGraphEdge> allEdges = new ArrayList<>();
                        allEdges.addAll(importEdgeList);
                        allEdges.addAll(exportEdgeList);
                        es.add(allEdges);
                    }
                }

            }
        });

        // Build a map to find the opposite of a given edge
        _logicalGraph.getLogicalGraphEdges().forEach((router, edgeLists) -> {
            for (RoutingProtocol proto : getGraph().getProtocols().get(router)) {

                for (ArrayList<LogicalGraphEdge> edgeList : edgeLists.get(proto)) {

                    for (int i = 0; i < edgeList.size(); i++) {

                        LogicalGraphEdge e = edgeList.get(i);

                        GraphEdge edge = e.getEdge();
                        Map<GraphEdge, ArrayList<LogicalGraphEdge>> m;

                        if (edge.getPeer() != null) {

                            if (e.getEdgeType() == EdgeType.IMPORT) {
                                m = exportInverseMap.get(edge.getPeer()).get(proto);

                            } else {
                                m = importInverseMap.get(edge.getPeer()).get(proto);
                            }

                            if (m != null) {
                                GraphEdge otherEdge = getGraph().getOtherEnd().get(edge);
                                LogicalGraphEdge other = m.get(otherEdge).get(i / 2);
                                _logicalGraph.getOtherEnd().put(e, other);
                            }

                        }

                    }
                }
            }
        });
    }

    private void addRedistributionSymbolicRecords() {
        getGraph().getConfigurations().forEach((router, conf) -> {
            Map<RoutingProtocol, Map<RoutingProtocol, LogicalRedistributionEdge>> map1 = new
                    HashMap<>();
            _logicalGraph.getRedistributionEdges().put(router, map1);
            for (RoutingProtocol proto : getGraph().getProtocols().get(router)) {
                EnumMap<RoutingProtocol, LogicalRedistributionEdge> map2 = new EnumMap<>
                        (RoutingProtocol.class);
                map1.put(proto, map2);
                RoutingPolicy pol = getGraph().findCommonRoutingPolicy(router, proto);
                if (pol != null) {
                    _logicalGraph.getRedistributionEdges().get(router).get(proto).forEach((
                            p, edge) -> {
                        // List<RoutingProtocol> ps = getGraph().findRedistributedProtocols(pol,
                        // proto);
                        // for (RoutingProtocol p : ps) {
                        String name = "REDIST_FROM_" + p.protocolName().toUpperCase();
                        String ifaceName = "none";
                        int len = 0;
                        SymbolicRecord e = new SymbolicRecord(router, proto, proto.protocolName()
                                , _optimizations, ifaceName, _ctx, len, name, false);
                        _allSymbolicRecords.add(e);
                        addExprs(e);
                        map2.put(p, new LogicalRedistributionEdge(p, EdgeType.IMPORT, 0, e));
                    });
                }
            }
        });
    }

    private void computeCommunities() {
        getGraph().getConfigurations().forEach((router, conf) -> {
            conf.getCommunityLists().forEach((clName, cl) -> {
                for (CommunityListLine line : cl.getLines()) {
                    conf.getRoutingPolicies().forEach((name, pol) -> {
                        AstVisitor v = new AstVisitor(pol.getStatements());
                        v.visit(stmt -> {
                            if (stmt instanceof SetCommunity) {
                                SetCommunity sc = (SetCommunity) stmt;
                                CommunitySetExpr ce = sc.getExpr();
                                if (ce instanceof InlineCommunitySet) {
                                    InlineCommunitySet c = (InlineCommunitySet) ce;
                                    // TODO:
                                }
                                if (ce instanceof NamedCommunitySet) {
                                    NamedCommunitySet c = (NamedCommunitySet) ce;
                                    // TODO:
                                }
                            }
                        }, expr -> {

                        });
                    });
                }
            });
        });

    }

    private void computeOptimizations() {
        _optimizations.computeOptimizations();
    }

    private void addEnvironmentVariables() {
        getGraph().getConfigurations().forEach((router, conf) -> {
            for (RoutingProtocol proto : getGraph().getProtocols().get(router)) {
                if (proto == RoutingProtocol.BGP) {
                    _logicalGraph.getLogicalGraphEdges().get(router, proto).forEach(eList -> {
                        eList.forEach(e -> {
                            if (e.getEdgeType() == EdgeType.IMPORT) {
                                BgpNeighbor n = getGraph().getBgpNeighbors().get(e.getEdge());
                                if (n != null && e.getEdge().getEnd() == null) {
                                    String address;
                                    if (n.getAddress() == null) {
                                        address = "null";
                                    } else {
                                        address = n.getAddress().toString();
                                    }

                                    SymbolicRecord vars = new SymbolicRecord(router, proto,
                                            "ENV", _optimizations, address, _ctx, 0, "EXPORT",
                                            false);
                                    addExprs(vars);
                                    _allSymbolicRecords.add(vars);
                                    _logicalGraph.getEnvironmentVars().put(e, vars);
                                }
                            }
                        });
                    });
                }
            }
        });
    }

    private void addFailedLinkVariables() {
        getGraph().getEdgeMap().forEach((router, edges) -> {
            for (GraphEdge ge : edges) {
                if (ge.getPeer() == null) {
                    Interface i = ge.getStart();
                    String name = "failed-edge_" + ge.getRouter() + "_" + i.getName();
                    ArithExpr var = _ctx.mkIntConst(name);
                    _symbolicFailures.getFailedEdgeLinks().put(ge, var);
                }
            }
        });
        getGraph().getNeighbors().forEach((router, peers) -> {
            for (String peer : peers) {
                // sort names for unique
                String pair = (router.compareTo(peer) < 0 ? router + "_" + peer : peer + "_" +
                        router);
                String name = "failed-internal_" + pair;
                ArithExpr var = _ctx.mkIntConst(name);
                _symbolicFailures.getFailedInternalLinks().put(router, peer, var);
            }
        });
    }

    private void addVariables() {
        buildEdgeMap();
        addForwardingVariables();
        addBestVariables();
        addSymbolicRecords();
        addRedistributionSymbolicRecords();
        addChoiceVariables();
        addEnvironmentVariables();
        addFailedLinkVariables();
    }

    private void addBoundConstraints() {

        ArithExpr upperBound4 = Int((long) Math.pow(2, 4));
        ArithExpr upperBound8 = Int((long) Math.pow(2, 8));
        ArithExpr upperBound16 = Int((long) Math.pow(2, 16));
        ArithExpr upperBound32 = Int((long) Math.pow(2, 32));
        ArithExpr zero = Int(0);

        // Valid 32 bit integers
        add(Ge(_symbolicPacket.getDstIp(), zero));
        add(Ge(_symbolicPacket.getSrcIp(), zero));
        add(Lt(_symbolicPacket.getDstIp(), upperBound32));
        add(Lt(_symbolicPacket.getSrcIp(), upperBound32));

        // Valid 16 bit integer
        add(Ge(_symbolicPacket.getDstPort(), zero));
        add(Ge(_symbolicPacket.getSrcPort(), zero));
        add(Lt(_symbolicPacket.getDstPort(), upperBound16));
        add(Lt(_symbolicPacket.getSrcPort(), upperBound16));

        // Valid 8 bit integer
        add(Ge(_symbolicPacket.getIcmpType(), zero));
        add(Ge(_symbolicPacket.getIpProtocol(), zero));
        add(Lt(_symbolicPacket.getIcmpType(), upperBound8));
        add(Lt(_symbolicPacket.getIpProtocol(), upperBound8));

        // Valid 4 bit integer
        add(Ge(_symbolicPacket.getIcmpCode(), zero));
        add(Lt(_symbolicPacket.getIcmpCode(), upperBound4));

        for (SymbolicRecord e : _allSymbolicRecords) {
            if (e.getAdminDist() != null) {
                add(Ge(e.getAdminDist(), zero));
                //_solver.add(_ctx.mkLe(e.getAdminDist(), _ctx.mkInt(200)));
            }
            if (e.getMed() != null) {
                add(Ge(e.getMed(), zero));
                //_solver.add(_ctx.mkLe(e.getMed(), _ctx.mkInt(100)));
            }
            if (e.getLocalPref() != null) {
                add(Ge(e.getLocalPref(), zero));
                //_solver.add(_ctx.mkLe(e.getLocalPref(), _ctx.mkInt(100)));
            }
            if (e.getMetric() != null) {
                add(Ge(e.getMetric(), zero));
                //_solver.add(_ctx.mkLe(e.getMetric(), _ctx.mkInt(200)));
            }
            if (e.getPrefixLength() != null) {
                add(Ge(e.getPrefixLength(), zero));
                add(Le(e.getPrefixLength(), Int(32)));
            }
        }
    }

    private void addFailedConstraints(int k) {
        List<ArithExpr> vars = new ArrayList<>();
        _symbolicFailures.getFailedInternalLinks().forEach((router, peer, var) -> {
            vars.add(var);
        });
        _symbolicFailures.getFailedEdgeLinks().forEach((ge, var) -> {
            vars.add(var);
        });

        ArithExpr sum = Int(0);
        for (ArithExpr var : vars) {
            sum = Sum(sum, var);
            add(Ge(var, Int(0)));
            add(Le(var, Int(1)));
        }
        if (k == 0) {
            for (ArithExpr var : vars) {
                add(Eq(var, Int(0)));
            }
        } else {
            add(Le(sum, Int(k)));
        }
    }

    private BoolExpr firstBitsEqual(ArithExpr x, long y, int n) {
        assert (n >= 0 && n <= 32);
        if (n == 0) {
            return True();
        }
        long bound = (long) Math.pow(2, 32 - n);
        ArithExpr upperBound = Int(y + bound);
        return And(Ge(x, Int(y)), Lt(x, upperBound));
    }

    private BoolExpr isRelevantFor(SymbolicRecord vars, PrefixRange range) {
        Prefix p = range.getPrefix();
        SubRange r = range.getLengthRange();
        long pfx = p.getNetworkAddress().asLong();
        int len = p.getPrefixLength();
        int lower = r.getStart();
        int upper = r.getEnd();

        // well formed prefix
        assert (p.getPrefixLength() < lower && lower <= upper);

        BoolExpr lowerBitsMatch = firstBitsEqual(_symbolicPacket.getDstIp(), pfx, len);
        if (lower == upper) {
            BoolExpr equalLen = Eq(vars.getPrefixLength(), Int(lower));
            return And(equalLen, lowerBitsMatch);
        } else {
            BoolExpr lengthLowerBound = Ge(vars.getPrefixLength(), Int(lower));
            BoolExpr lengthUpperBound = Le(vars.getPrefixLength(), Int(upper));
            return And(lengthLowerBound, lengthUpperBound, lowerBitsMatch);
        }
    }

    private BoolExpr matchFilterList(SymbolicRecord other, RouteFilterList x) {
        BoolExpr acc = False();

        List<RouteFilterLine> lines = new ArrayList<>(x.getLines());
        Collections.reverse(lines);

        for (RouteFilterLine line : lines) {
            Prefix p = line.getPrefix();
            SubRange r = line.getLengthRange();
            PrefixRange range = new PrefixRange(p, r);
            BoolExpr matches = isRelevantFor(other, range);

            switch (line.getAction()) {
                case ACCEPT:
                    acc = If(matches, True(), acc);
                    break;

                case REJECT:
                    acc = If(matches, False(), acc);
                    break;
            }
        }
        return acc;
    }

    private BoolExpr matchPrefixSet(Configuration conf, SymbolicRecord other, PrefixSetExpr e) {
        if (e instanceof ExplicitPrefixSet) {
            ExplicitPrefixSet x = (ExplicitPrefixSet) e;

            Set<PrefixRange> ranges = x.getPrefixSpace().getPrefixRanges();
            if (ranges.isEmpty()) {
                return True();
            }

            BoolExpr acc = False();
            for (PrefixRange range : ranges) {
                acc = Or(acc, isRelevantFor(other, range));
            }
            return acc;

        } else if (e instanceof NamedPrefixSet) {
            NamedPrefixSet x = (NamedPrefixSet) e;
            String name = x.getName();
            RouteFilterList fl = conf.getRouteFilterLists().get(name);
            return matchFilterList(other, fl);

        } else {
            throw new BatfishException("TODO: match prefix set: " + e);
        }
    }

    private BoolExpr computeTransferFunction(
            SymbolicRecord other, SymbolicRecord current, Configuration conf, RoutingProtocol to,
            RoutingProtocol from, Modifications mods, BooleanExpr expr, Integer addedCost,
            boolean inCall) {

        if (expr instanceof Conjunction) {
            Conjunction c = (Conjunction) expr;
            if (c.getConjuncts().size() == 0) {
                return False();
            }
            BoolExpr v = True();
            for (BooleanExpr x : c.getConjuncts()) {
                v = And(v, computeTransferFunction(other, current, conf, to, from, mods, x,
                        addedCost, inCall));
            }
            return v;
        }
        if (expr instanceof Disjunction) {
            Disjunction d = (Disjunction) expr;
            if (d.getDisjuncts().size() == 0) {
                return True();
            }
            BoolExpr v = False();
            for (BooleanExpr x : d.getDisjuncts()) {
                v = Or(v, computeTransferFunction(other, current, conf, to, from, mods, x,
                        addedCost, inCall));
            }
            return v;
        }
        if (expr instanceof Not) {
            Not n = (Not) expr;
            BoolExpr v = computeTransferFunction(other, current, conf, to, from, mods, n.getExpr
                    (), addedCost, inCall);
            return Not(v);
        }
        if (expr instanceof MatchProtocol) {
            // TODO: is this right?
            MatchProtocol mp = (MatchProtocol) expr;
            return Bool(mp.getProtocol() == from);
        }
        if (expr instanceof MatchPrefixSet) {
            MatchPrefixSet m = (MatchPrefixSet) expr;
            return matchPrefixSet(conf, other, m.getPrefixSet());
        }
        if (expr instanceof CallExpr) {
            CallExpr c = (CallExpr) expr;
            String name = c.getCalledPolicyName();
            RoutingPolicy pol = conf.getRoutingPolicies().get(name);

            // TODO: we really need some sort of SSA form
            // TODO: modifications will not be kept because it depends on the branch choosen
            // Do not copy modifications to keep
            return computeTransferFunction(other, current, conf, to, from, mods, pol
                    .getStatements(), addedCost, true);
        }
        if (expr instanceof WithEnvironmentExpr) {
            // TODO: this is not correct
            WithEnvironmentExpr we = (WithEnvironmentExpr) expr;
            return computeTransferFunction(other, current, conf, to, from, mods, we.getExpr(),
                    addedCost, inCall);
        }

        throw new BatfishException("TODO: compute expr transfer function: " + expr);
    }

    private ArithExpr getOrDefault(ArithExpr x, ArithExpr d) {
        if (x != null) {
            return x;
        }
        return d;
    }

    private ArithExpr applyIntExprModification(ArithExpr x, IntExpr e) {
        if (e instanceof LiteralInt) {
            LiteralInt z = (LiteralInt) e;
            return Int(z.getValue());
        }
        if (e instanceof DecrementMetric) {
            DecrementMetric z = (DecrementMetric) e;
            return Sub(x, Int(z.getSubtrahend()));
        }
        if (e instanceof IncrementMetric) {
            IncrementMetric z = (IncrementMetric) e;
            return Sum(x, Int(z.getAddend()));
        }
        if (e instanceof IncrementLocalPreference) {
            IncrementLocalPreference z = (IncrementLocalPreference) e;
            return Sum(x, Int(z.getAddend()));
        }
        if (e instanceof DecrementLocalPreference) {
            DecrementLocalPreference z = (DecrementLocalPreference) e;
            return Sub(x, Int(z.getSubtrahend()));
        }
        throw new BatfishException("TODO: int expr transfer function: " + e);
    }

    private BoolExpr applyModifications(
            Configuration conf, RoutingProtocol to, RoutingProtocol from, Modifications mods,
            SymbolicRecord current, SymbolicRecord other, Integer addedCost) {
        ArithExpr defaultLen = Int(defaultLength());
        ArithExpr defaultAd = Int(defaultAdminDistance(conf, from));
        ArithExpr defaultMed = Int(defaultMed(from));
        ArithExpr defaultLp = Int(defaultLocalPref());
        ArithExpr defaultId = Int(defaultId());
        ArithExpr defaultMet = Int(defaultMetric(from));

        BoolExpr met;
        ArithExpr otherMet = getOrDefault(other.getMetric(), defaultMet);
        if (mods.getSetMetric() == null) {
            met = safeEqAdd(current.getMetric(), otherMet, addedCost);
        } else {
            IntExpr ie = mods.getSetMetric().getMetric();
            ArithExpr val = applyIntExprModification(otherMet, ie);
            met = safeEqAdd(current.getMetric(), val, addedCost);
        }

        BoolExpr lp;
        ArithExpr otherLp = getOrDefault(other.getLocalPref(), defaultLp);
        if (mods.getSetLp() == null) {
            lp = safeEq(current.getLocalPref(), otherLp);
        } else {
            IntExpr ie = mods.getSetLp().getLocalPreference();
            lp = safeEq(current.getLocalPref(), applyIntExprModification(otherLp, ie));
        }

        BoolExpr per = safeEq(current.getPermitted(), other.getPermitted());
        BoolExpr len = safeEq(current.getPrefixLength(), getOrDefault(other.getPrefixLength(),
                defaultLen));
        BoolExpr id = safeEq(current.getRouterId(), getOrDefault(other.getRouterId(), defaultId));

        // TODO: handle AD correctly
        // TODO: handle MED correctly
        // TODO: what about transitivity?
        // TODO: communities are transmitted to neighbors?
        ArithExpr otherAd = (other.getAdminDist() == null ? defaultAd : other.getAdminDist());
        ArithExpr otherMed = (other.getMed() == null ? defaultMed : other.getMed());

        BoolExpr ad = safeEq(current.getAdminDist(), otherAd);
        BoolExpr med = safeEq(current.getMed(), otherMed);

        return And(per, len, ad, med, lp, met, id);

    }

    private BoolExpr computeTransferFunction(
            SymbolicRecord other, SymbolicRecord current, Configuration conf, RoutingProtocol to,
            RoutingProtocol from, Modifications mods, List<Statement> statements, Integer
            addedCost, boolean inCall) {

        ListIterator<Statement> it = statements.listIterator();
        while (it.hasNext()) {
            Statement s = it.next();

            if (s instanceof Statements.StaticStatement) {
                Statements.StaticStatement ss = (Statements.StaticStatement) s;
                if (ss.getType() == ExitAccept) {
                    return applyModifications(conf, to, from, mods, current, other, addedCost);
                } else if (ss.getType() == ExitReject) {
                    return Not(current.getPermitted());
                } else if (ss.getType() == ReturnTrue) {
                    if (inCall) {
                        return True();
                    } else {
                        return applyModifications(conf, to, from, mods, current, other, addedCost);
                    }
                } else if (ss.getType() == ReturnFalse) {
                    if (inCall) {
                        return False();
                    } else {
                        return Not(current.getPermitted());
                    }
                } else if (ss.getType() == SetDefaultActionAccept) {
                    mods.addModification(s);
                } else if (ss.getType() == SetDefaultActionReject) {
                    mods.addModification(s);
                }
                // TODO: need to set local default action in an environment
                else if (ss.getType() == ReturnLocalDefaultAction) {
                    return False();
                } else {
                    throw new BatfishException("TODO: computeTransferFunction: " + ss.getType());
                }
            } else if (s instanceof If) {
                If i = (If) s;
                List<Statement> remainingx = new ArrayList<>(i.getTrueStatements());
                List<Statement> remainingy = new ArrayList<>(i.getFalseStatements());

                // Copy the remaining statements along both branches
                if (it.hasNext()) {
                    ListIterator<Statement> ix = statements.listIterator(it.nextIndex());
                    ListIterator<Statement> iy = statements.listIterator(it.nextIndex());
                    while (ix.hasNext()) {
                        remainingx.add(ix.next());
                        remainingy.add(iy.next());
                    }
                }

                Modifications modsTrue = new Modifications(mods);
                Modifications modsFalse = new Modifications(mods);
                BoolExpr guard = computeTransferFunction(other, current, conf, to, from, mods, i
                        .getGuard(), addedCost, inCall);
                BoolExpr trueBranch = computeTransferFunction(other, current, conf, to, from,
                        modsTrue, remainingx, addedCost, inCall);
                BoolExpr falseBranch = computeTransferFunction(other, current, conf, to, from,
                        modsFalse, remainingy, addedCost, inCall);
                return If(guard, trueBranch, falseBranch);

            } else if (s instanceof SetOspfMetricType || s instanceof SetMetric) {
                mods.addModification(s);

            } else {
                throw new BatfishException("TODO: statement transfer function: " + s);
            }
        }

        if (mods.getDefaultAccept()) {
            return applyModifications(conf, to, from, mods, current, other, addedCost);
        } else {
            return Not(current.getPermitted());
        }
    }

    private BoolExpr computeTransferFunction(
            SymbolicRecord other, SymbolicRecord current, Configuration conf, RoutingProtocol to,
            RoutingProtocol from, List<Statement> statements, Integer addedCost) {
        return computeTransferFunction(other, current, conf, to, from, new Modifications(),
                statements, addedCost, false);
    }

    private BoolExpr transferFunction(
            SymbolicRecord other, SymbolicRecord current, RoutingPolicy pol, Configuration conf,
            RoutingProtocol to, RoutingProtocol from) {
        if (ENABLE_DEBUGGING) {
            System.out.println("------ REDISTRIBUTION ------");
            System.out.println("From: " + to.protocolName());
            System.out.println("To: " + from.protocolName());
        }
        return computeTransferFunction(other, current, conf, to, from, pol.getStatements(), null);
    }

    private void addRedistributionConstraints() {
        getGraph().getConfigurations().forEach((router, conf) -> {
            for (RoutingProtocol proto : getGraph().getProtocols().get(router)) {
                RoutingPolicy pol = getGraph().findCommonRoutingPolicy(router, proto);
                if (pol != null) {
                    Map<RoutingProtocol, LogicalRedistributionEdge> map = _logicalGraph
                            .getRedistributionEdges().get(router).get(proto);
                    map.forEach((fromProto, vars) -> {
                        SymbolicRecord current = vars.getSymbolicRecord();
                        SymbolicRecord other = _symbolicDecisions.getBestNeighborPerProtocol()
                                                                 .get(router, fromProto);
                        BoolExpr be = transferFunction(other, current, pol, conf, proto, fromProto);
                        add(be);
                    });
                }
            }
        });
    }

    /**
     * TODO:
     * This was copied from BdpDataPlanePlugin.java
     * to make things easier for now.
     */
    private void initOspfInterfaceCosts(Configuration conf) {
        if (conf.getDefaultVrf().getOspfProcess() != null) {
            conf.getInterfaces().forEach((interfaceName, i) -> {
                if (i.getActive()) {
                    Integer ospfCost = i.getOspfCost();
                    if (ospfCost == null) {
                        if (interfaceName.startsWith("Vlan")) {
                            // TODO: fix for non-cisco
                            ospfCost = DEFAULT_CISCO_VLAN_OSPF_COST;
                        } else {
                            if (i.getBandwidth() != null) {
                                ospfCost = Math.max((int) (conf.getDefaultVrf().getOspfProcess()
                                                               .getReferenceBandwidth() / i
                                        .getBandwidth()), 1);
                            } else {
                                throw new BatfishException("Expected non-null interface " +
                                        "bandwidth" + " for \"" + conf.getHostname() + "\":\"" +
                                        interfaceName + "\"");
                            }
                        }
                    }
                    i.setOspfCost(ospfCost);
                }
            });
        }
    }

    private BoolExpr isRelevantFor(Prefix p, ArithExpr ae) {
        long pfx = p.getNetworkAddress().asLong();
        return firstBitsEqual(ae, pfx, p.getPrefixLength());
    }

    public List<Prefix> getOriginatedNetworks(Configuration conf, RoutingProtocol proto) {
        List<Prefix> acc = new ArrayList<>();

        if (proto == RoutingProtocol.OSPF) {
            conf.getDefaultVrf().getOspfProcess().getAreas().forEach((areaID, area) -> {
                // if (areaID == 0) {
                    for (Interface iface : area.getInterfaces()) {
                        if (iface.getActive() && iface.getOspfEnabled()) {
                            acc.add(iface.getPrefix());
                        }
                    }
                // } else {
                //     throw new BatfishException("Error: only support area 0 at the moment");
                // }
            });
            return acc;
        }

        if (proto == RoutingProtocol.BGP) {
            conf.getRouteFilterLists().forEach((name, list) -> {
                for (RouteFilterLine line : list.getLines()) {
                    if (name.contains(BGP_NETWORK_FILTER_LIST_NAME)) {
                        acc.add(line.getPrefix());
                    }
                }
            });
            return acc;
        }

        if (proto == RoutingProtocol.CONNECTED) {
            conf.getInterfaces().forEach((name, iface) -> {
                Prefix p = iface.getPrefix();
                if (p != null) {
                    acc.add(p);
                }
            });
            return acc;
        }

        if (proto == RoutingProtocol.STATIC) {
            for (StaticRoute sr : conf.getDefaultVrf().getStaticRoutes()) {
                if (sr.getNetwork() != null) {
                    acc.add(sr.getNetwork());
                }
            }
            return acc;
        }

        throw new BatfishException("ERROR: getOriginatedNetworks: " + proto.protocolName());
    }

    private BoolExpr safeEq(Expr x, Expr value) {
        if (x == null) {
            return True();
        }
        return Eq(x, value);
    }

    private BoolExpr safeEqAdd(ArithExpr x, ArithExpr value, Integer cost) {
        if (x == null) {
            return True();
        }
        if (cost == null) {
            return Eq(x, value);
        }
        return Eq(x, Sum(value, Int(cost)));
    }

    private int defaultId() {
        return 0;
    }

    private int defaultMetric(RoutingProtocol proto) {
        if (proto == RoutingProtocol.CONNECTED) {
            return 0;
        }
        if (proto == RoutingProtocol.STATIC) {
            return 0;
        }
        return 0;
    }

    private int defaultMed(RoutingProtocol proto) {
        if (proto == RoutingProtocol.BGP) {
            return 100;
        }
        return 0;
    }

    private int defaultLocalPref() {
        return 0;
    }

    private int defaultLength() {
        return 0;
    }

    // TODO: depends on configuration
    public boolean isMultipath(Configuration conf, RoutingProtocol proto) {
        if (proto == RoutingProtocol.CONNECTED) {
            return true;
        } else if (proto == RoutingProtocol.STATIC) {
            return true;
        } else if (proto == RoutingProtocol.OSPF) {
            return true;
        } else {
            return true;
        }
    }

    private SymbolicRecord correctVars(LogicalEdge e) {
        if (e instanceof LogicalGraphEdge) {
            SymbolicRecord vars = e.getSymbolicRecord();
            if (!vars.getIsUsed()) {
                return _logicalGraph.getOtherEnd().get(e).getSymbolicRecord();
            }
            return vars;
        } else {
            return e.getSymbolicRecord();
        }
    }

    private BoolExpr equalHelper(ArithExpr best, ArithExpr vars, ArithExpr defaultVal) {
        BoolExpr tru = True();
        if (vars == null) {
            if (best != null) {
                return Eq(best, defaultVal);
            } else {
                return tru;
            }
        } else {
            return Eq(best, vars);
        }
    }

    public BoolExpr equal(
            Configuration conf, RoutingProtocol proto, SymbolicRecord best, SymbolicRecord vars,
            LogicalEdge e) {

        ArithExpr defaultLocal = Int(defaultLocalPref());
        ArithExpr defaultAdmin = Int(defaultAdminDistance(conf, proto));
        ArithExpr defaultMet = Int(defaultMetric(proto));
        ArithExpr defaultMed = Int(defaultMed(proto));
        ArithExpr defaultLen = Int(defaultLength());

        BoolExpr equalLen;
        BoolExpr equalAd;
        BoolExpr equalLp;
        BoolExpr equalMet;
        BoolExpr equalMed;
        BoolExpr equalId;

        equalLen = equalHelper(best.getPrefixLength(), vars.getPrefixLength(), defaultLen);
        equalAd = equalHelper(best.getAdminDist(), vars.getAdminDist(), defaultAdmin);
        equalLp = equalHelper(best.getLocalPref(), vars.getLocalPref(), defaultLocal);
        equalMet = equalHelper(best.getMetric(), vars.getMetric(), defaultMet);
        equalMed = equalHelper(best.getMed(), vars.getMed(), defaultMed);

        if (vars.getRouterId() == null) {
            if (best.getRouterId() == null) {
                equalId = True();
            } else {
                Long peerId = _logicalGraph.findRouterId(e, proto);
                if (isMultipath(conf, proto) || peerId == null) {
                    equalId = True();
                } else {
                    equalId = Eq(best.getRouterId(), Int(peerId));
                }
            }
        } else {
            equalId = Eq(best.getRouterId(), vars.getRouterId());
        }

        return And(equalLen, equalAd, equalLp, equalMet, equalMed, equalId);
    }

    private BoolExpr geBetterHelper(
            ArithExpr best, ArithExpr vars, ArithExpr defaultVal, boolean less, boolean bestCond) {
        BoolExpr fal = False();
        if (vars == null) {
            if (best != null && bestCond) {
                if (less) {
                    return Lt(best, defaultVal);
                } else {
                    return Gt(best, defaultVal);
                }
            } else {
                return fal;
            }
        } else {
            if (less) {
                return Lt(best, vars);
            } else {
                return Gt(best, vars);
            }
        }
    }

    private BoolExpr geEqualHelper(
            ArithExpr best, ArithExpr vars, ArithExpr defaultVal, boolean bestCond) {
        BoolExpr tru = True();
        if (vars == null) {
            if (best != null && bestCond) {
                return Eq(best, defaultVal);
            } else {
                return tru;
            }
        } else {
            return Eq(best, vars);
        }
    }

    private BoolExpr greaterOrEqual(
            Configuration conf, RoutingProtocol proto, SymbolicRecord best, SymbolicRecord vars,
            LogicalEdge e) {

        ArithExpr defaultLocal = Int(defaultLocalPref());
        ArithExpr defaultAdmin = Int(defaultAdminDistance(conf, proto));
        ArithExpr defaultMet = Int(defaultMetric(proto));
        ArithExpr defaultMed = Int(defaultMed(proto));
        ArithExpr defaultLen = Int(defaultLength());

        BoolExpr betterLen = geBetterHelper(best.getPrefixLength(), vars.getPrefixLength(),
                defaultLen, false, true);
        BoolExpr equalLen = geEqualHelper(best.getPrefixLength(), vars.getPrefixLength(),
                defaultLen, true);

        boolean keepAd = _optimizations.getKeepAdminDist();
        BoolExpr betterAd = geBetterHelper(best.getAdminDist(), vars.getAdminDist(),
                defaultAdmin, true, keepAd);
        BoolExpr equalAd = geEqualHelper(best.getAdminDist(), vars.getAdminDist(), defaultAdmin,
                keepAd);

        boolean keepLp = _optimizations.getKeepLocalPref();
        BoolExpr betterLp = geBetterHelper(best.getLocalPref(), vars.getLocalPref(),
                defaultLocal, false, keepLp);
        BoolExpr equalLp = geEqualHelper(best.getLocalPref(), vars.getLocalPref(), defaultLocal,
                keepLp);

        BoolExpr betterMet = geBetterHelper(best.getMetric(), vars.getMetric(), defaultMet, true,
                true);
        BoolExpr equalMet = geEqualHelper(best.getMetric(), vars.getMetric(), defaultMet, true);

        BoolExpr betterMed = geBetterHelper(best.getMed(), vars.getMed(), defaultMed, true, true);
        BoolExpr equalMed = geEqualHelper(best.getMed(), vars.getMed(), defaultMed, true);

        BoolExpr tiebreak;
        if (vars.getRouterId() == null) {
            if (best.getRouterId() == null) {
                tiebreak = True();
            } else {
                Long peerId = _logicalGraph.findRouterId(e, proto);
                if (isMultipath(conf, proto) || peerId == null) {
                    tiebreak = True();
                } else {
                    tiebreak = Le(best.getRouterId(), Int(peerId));
                }
            }
        } else {
            tiebreak = Le(best.getRouterId(), vars.getRouterId());
        }

        BoolExpr b = And(equalMed, tiebreak);
        BoolExpr b0 = Or(betterMed, b);
        BoolExpr b1 = And(equalMet, b0);
        BoolExpr b2 = Or(betterMet, b1);
        BoolExpr b3 = And(equalLp, b2);
        BoolExpr b4 = Or(betterLp, b3);
        BoolExpr b5 = And(equalAd, b4);
        BoolExpr b6 = Or(betterAd, b5);
        BoolExpr b7 = And(equalLen, b6);
        return Or(betterLen, b7);
    }

    private void addBestOverallConstraints() {
        getGraph().getConfigurations().forEach((router, conf) -> {

            // These constraints will be added at the protocol-level when a single protocol
            if (!_optimizations.getSliceHasSingleProtocol().contains(router)) {

                boolean someProto = false;

                BoolExpr acc = null;
                BoolExpr somePermitted = null;
                SymbolicRecord best = _symbolicDecisions.getBestNeighbor().get(router);

                for (RoutingProtocol proto : getGraph().getProtocols().get(router)) {
                    someProto = true;

                    SymbolicRecord bestVars = _symbolicDecisions.getBestVars(_optimizations,
                            router, proto);

                    if (somePermitted == null) {
                        somePermitted = bestVars.getPermitted();
                    } else {
                        somePermitted = Or(somePermitted, bestVars.getPermitted());
                    }

                    BoolExpr val = And(bestVars.getPermitted(), equal(conf, proto, best,
                            bestVars, null));
                    if (acc == null) {
                        acc = val;
                    } else {
                        acc = Or(acc, val);
                    }
                    add(Implies(bestVars.getPermitted(), greaterOrEqual(conf, proto, best,
                            bestVars, null)));
                }

                if (someProto) {
                    if (acc != null) {
                        add(Eq(somePermitted, best.getPermitted()));
                        add(Implies(somePermitted, acc));
                    }
                } else {
                    add(Not(best.getPermitted()));
                }
            }
        });
    }

    private Set<LogicalEdge> collectAllImportLogicalEdges(String router, Configuration conf,
            RoutingProtocol proto) {
        Set<LogicalEdge> eList = new HashSet<>();
        for (ArrayList<LogicalGraphEdge> es : _logicalGraph.getLogicalGraphEdges().get(router,
                proto)) {
            for (LogicalGraphEdge lge : es) {
                if (_logicalGraph.isEdgeUsed(conf, proto, lge) && lge.getEdgeType() == EdgeType
                        .IMPORT) {
                    eList.add(lge);
                }
            }
        }
        _logicalGraph.getRedistributionEdges().get(router).get(proto).forEach((fromProto, edge)
                -> eList.add(edge));
        return eList;
    }

    private void addBestPerProtocolConstraints() {
        getGraph().getConfigurations().forEach((router, conf) -> {

            for (RoutingProtocol proto : getGraph().getProtocols().get(router)) {

                // TODO: must do this for each of 32 lens
                SymbolicRecord bestVars = _symbolicDecisions.getBestVars(_optimizations, router,
                        proto);
                BoolExpr acc = null;
                BoolExpr somePermitted = null;

                for (LogicalEdge e : collectAllImportLogicalEdges(router, conf, proto)) {

                    SymbolicRecord vars = correctVars(e);

                    if (somePermitted == null) {
                        somePermitted = vars.getPermitted();
                    } else {
                        somePermitted = Or(somePermitted, vars.getPermitted());
                    }

                    BoolExpr v = And(vars.getPermitted(), equal(conf, proto, bestVars, vars, e));
                    if (acc == null) {
                        acc = v;
                    } else {
                        acc = Or(acc, v);
                    }
                    add(Implies(vars.getPermitted(), greaterOrEqual(conf, proto, bestVars, vars,
                            e)));

                }

                if (acc != null) {
                    add(Eq(somePermitted, bestVars.getPermitted()));
                    // best is one of the allowed ones
                    add(Implies(somePermitted, acc));
                }
            }

        });
    }

    private void addChoicePerProtocolConstraints() {
        getGraph().getConfigurations().forEach((router, conf) -> {
            for (RoutingProtocol proto : getGraph().getProtocols().get(router)) {
                SymbolicRecord bestVars = _symbolicDecisions.getBestVars(_optimizations, router,
                        proto);
                for (LogicalEdge e : collectAllImportLogicalEdges(router, conf, proto)) {
                    SymbolicRecord vars = correctVars(e);
                    BoolExpr choice = _symbolicDecisions.getChoiceVariables().get(router, proto, e);
                    BoolExpr isBest = equal(conf, proto, bestVars, vars, e);
                    add(Eq(choice, And(vars.getPermitted(), isBest)));
                }
            }
        });
    }

    private void addControlForwardingConstraints() {
        getGraph().getConfigurations().forEach((router, conf) -> {

            boolean someEdge = false;

            SymbolicRecord best = _symbolicDecisions.getBestNeighbor().get(router);
            Map<GraphEdge, BoolExpr> cfExprs = new HashMap<>();

            for (RoutingProtocol proto : getGraph().getProtocols().get(router)) {

                for (LogicalEdge e : collectAllImportLogicalEdges(router, conf, proto)) {

                    someEdge = true;

                    SymbolicRecord vars = correctVars(e);
                    BoolExpr choice = _symbolicDecisions.getChoiceVariables().get(router, proto, e);
                    BoolExpr isBest = And(choice, equal(conf, proto, best, vars, e));

                    if (e instanceof LogicalGraphEdge) {
                        LogicalGraphEdge lge = (LogicalGraphEdge) e;
                        GraphEdge ge = lge.getEdge();
                        BoolExpr cForward = _symbolicDecisions.getControlForwarding().get(router,
                                ge);
                        add(Implies(isBest, cForward));

                        // record the negation as well
                        BoolExpr existing = cfExprs.get(ge);
                        if (existing == null) {
                            cfExprs.put(ge, isBest);
                        } else {
                            cfExprs.put(ge, Or(existing, isBest));
                        }

                    } else {
                        LogicalRedistributionEdge lre = (LogicalRedistributionEdge) e;
                        RoutingProtocol protoFrom = lre.getFrom();

                        _logicalGraph.getLogicalGraphEdges().get(router, protoFrom).forEach(eList
                                -> {
                            for (LogicalGraphEdge lge : eList) {
                                if (lge.getEdgeType() == EdgeType.IMPORT) {

                                    GraphEdge ge = lge.getEdge();
                                    BoolExpr cForward = _symbolicDecisions.getControlForwarding()
                                                                          .get(router, ge);
                                    BoolExpr otherProtoChoice = _symbolicDecisions
                                            .getChoiceVariables().get(router, protoFrom, lge);
                                    add(Implies(And(isBest, otherProtoChoice), cForward));

                                    // record the negation as well
                                    BoolExpr existing = cfExprs.get(ge);
                                    BoolExpr both = And(isBest, otherProtoChoice);
                                    if (existing == null) {
                                        cfExprs.put(ge, both);
                                    } else {
                                        cfExprs.put(ge, Or(existing, both));
                                    }
                                }
                            }
                        });
                    }
                }
            }

            // Handle the case that the router has no protocol running
            if (!someEdge) {
                for (GraphEdge ge : getGraph().getEdgeMap().get(router)) {
                    BoolExpr cForward = _symbolicDecisions.getControlForwarding().get(router, ge);
                    add(Not(cForward));
                }
            } else {
                _logicalGraph.getLogicalGraphEdges().get(router).forEach((proto, eList) -> {
                    eList.forEach(edges -> {
                        edges.forEach(le -> {
                            GraphEdge ge = le.getEdge();
                            BoolExpr expr = cfExprs.get(ge);
                            BoolExpr cForward = _symbolicDecisions.getControlForwarding().get
                                    (router, ge);
                            if (expr != null) {
                                add(Implies(Not(expr), Not(cForward)));
                            } else {
                                add(Not(cForward));
                            }
                        });
                    });
                });
            }
        });

    }

    private BoolExpr computeWildcardMatch(Set<IpWildcard> wcs, ArithExpr field) {
        BoolExpr acc = False();
        for (IpWildcard wc : wcs) {
            if (!wc.isPrefix()) {
                throw new BatfishException("ERROR: computeDstWildcards, non sequential mask " +
                        "detected");
            }
            acc = Or(acc, isRelevantFor(wc.toPrefix(), field));
        }
        return (BoolExpr) acc.simplify();
    }

    private BoolExpr computeValidRange(Set<SubRange> ranges, ArithExpr field) {
        BoolExpr acc = False();
        for (SubRange range : ranges) {
            int start = range.getStart();
            int end = range.getEnd();
            if (start == end) {
                BoolExpr val = Eq(field, Int(start));
                acc = Or(acc, val);
            } else {
                BoolExpr val1 = Ge(field, Int(start));
                BoolExpr val2 = Le(field, Int(end));
                acc = Or(acc, And(val1, val2));
            }
        }
        return (BoolExpr) acc.simplify();
    }

    private BoolExpr computeTcpFlags(TcpFlags flags) {
        BoolExpr acc = True();
        if (flags.getUseAck()) {
            acc = And(acc, Eq(_symbolicPacket.getTcpAck(), Bool(flags.getAck())));
        }
        if (flags.getUseCwr()) {
            acc = And(acc, Eq(_symbolicPacket.getTcpCwr(), Bool(flags.getCwr())));
        }
        if (flags.getUseEce()) {
            acc = And(acc, Eq(_symbolicPacket.getTcpEce(), Bool(flags.getEce())));
        }
        if (flags.getUseFin()) {
            acc = And(acc, Eq(_symbolicPacket.getTcpFin(), Bool(flags.getFin())));
        }
        if (flags.getUsePsh()) {
            acc = And(acc, Eq(_symbolicPacket.getTcpPsh(), Bool(flags.getPsh())));
        }
        if (flags.getUseRst()) {
            acc = And(acc, Eq(_symbolicPacket.getTcpRst(), Bool(flags.getRst())));
        }
        if (flags.getUseSyn()) {
            acc = And(acc, Eq(_symbolicPacket.getTcpSyn(), Bool(flags.getSyn())));
        }
        if (flags.getUseUrg()) {
            acc = And(acc, Eq(_symbolicPacket.getTcpUrg(), Bool(flags.getUrg())));
        }
        return (BoolExpr) acc.simplify();
    }

    private BoolExpr computeTcpFlags(List<TcpFlags> flags) {
        BoolExpr acc = False();
        for (TcpFlags fs : flags) {
            acc = Or(acc, computeTcpFlags(fs));
        }
        return (BoolExpr) acc.simplify();
    }

    private BoolExpr computeIpProtocols(Set<IpProtocol> ipProtos) {
        BoolExpr acc = False();
        for (IpProtocol proto : ipProtos) {
            ArithExpr protoNum = Int(proto.number());
            acc = Or(acc, Eq(protoNum, _symbolicPacket.getIpProtocol()));
        }
        return (BoolExpr) acc.simplify();
    }

    private BoolExpr computeACL(IpAccessList acl) {
        // Check if there is an ACL first
        if (acl == null) {
            return True();
        }

        BoolExpr acc = False();

        List<IpAccessListLine> lines = new ArrayList<>(acl.getLines());
        Collections.reverse(lines);

        for (IpAccessListLine l : lines) {
            BoolExpr local = null;

            if (l.getDstIps() != null) {
                local = computeWildcardMatch(l.getDstIps(), _symbolicPacket.getDstIp());
            }

            if (l.getSrcIps() != null) {
                BoolExpr val = computeWildcardMatch(l.getSrcIps(), _symbolicPacket.getSrcIp());
                local = (local == null ? val : And(local, val));
            }

            if (l.getDscps() != null && !l.getDscps().isEmpty()) {
                throw new BatfishException("detected dscps");
            }

            if (l.getDstPorts() != null && !l.getDstPorts().isEmpty()) {
                BoolExpr val = computeValidRange(l.getDstPorts(), _symbolicPacket.getDstPort());
                local = (local == null ? val : And(local, val));
            }

            if (l.getSrcPorts() != null && !l.getSrcPorts().isEmpty()) {
                BoolExpr val = computeValidRange(l.getSrcPorts(), _symbolicPacket.getSrcPort());
                local = (local == null ? val : And(local, val));
            }

            if (l.getEcns() != null && !l.getEcns().isEmpty()) {
                throw new BatfishException("detected ecns");
            }

            if (l.getTcpFlags() != null && !l.getTcpFlags().isEmpty()) {
                BoolExpr val = computeTcpFlags(l.getTcpFlags());
                local = (local == null ? val : And(local, val));
            }

            if (l.getFragmentOffsets() != null && !l.getFragmentOffsets().isEmpty()) {
                throw new BatfishException("detected fragment offsets");
            }

            if (l.getIcmpCodes() != null && !l.getIcmpCodes().isEmpty()) {
                BoolExpr val = computeValidRange(l.getIcmpCodes(), _symbolicPacket.getIcmpCode());
                local = (local == null ? val : And(local, val));
            }

            if (l.getIcmpTypes() != null && !l.getIcmpTypes().isEmpty()) {
                BoolExpr val = computeValidRange(l.getIcmpTypes(), _symbolicPacket.getIcmpType());
                local = (local == null ? val : And(local, val));
            }

            if (l.getStates() != null && !l.getStates().isEmpty()) {
                throw new BatfishException("detected states");
            }

            if (l.getIpProtocols() != null && !l.getIpProtocols().isEmpty()) {
                BoolExpr val = computeIpProtocols(l.getIpProtocols());
                local = (local == null ? val : And(local, val));
            }

            if (l.getNotDscps() != null && !l.getNotDscps().isEmpty()) {
                throw new BatfishException("detected NOT dscps");
            }

            if (l.getNotDstIps() != null && !l.getNotDstIps().isEmpty()) {
                throw new BatfishException("detected NOT dst ip");
            }

            if (l.getNotSrcIps() != null && !l.getNotSrcIps().isEmpty()) {
                throw new BatfishException("detected NOT src ip");
            }

            if (l.getNotDstPorts() != null && !l.getNotDstPorts().isEmpty()) {
                throw new BatfishException("detected NOT dst port");
            }

            if (l.getNotSrcPorts() != null && !l.getNotSrcPorts().isEmpty()) {
                throw new BatfishException("detected NOT src port");
            }

            if (l.getNotEcns() != null && !l.getNotEcns().isEmpty()) {
                throw new BatfishException("detected NOT ecns");
            }

            if (l.getNotIcmpCodes() != null && !l.getNotIcmpCodes().isEmpty()) {
                throw new BatfishException("detected NOT icmp codes");
            }

            if (l.getNotIcmpTypes() != null && !l.getNotIcmpTypes().isEmpty()) {
                throw new BatfishException("detected NOT icmp types");
            }

            if (l.getNotFragmentOffsets() != null && !l.getNotFragmentOffsets().isEmpty()) {
                throw new BatfishException("detected NOT fragment offset");
            }

            if (l.getNotIpProtocols() != null && !l.getNotIpProtocols().isEmpty()) {
                throw new BatfishException("detected NOT ip protocols");
            }

            if (local != null) {
                BoolExpr ret;
                if (l.getAction() == LineAction.ACCEPT) {
                    ret = True();
                } else {
                    ret = False();
                }

                if (l.getNegate()) {
                    local = Not(local);
                }

                acc = If(local, ret, acc);
            }
        }

        return acc;
    }

    private void addAclFunctions() {
        getGraph().getEdgeMap().forEach((router, edges) -> {
            for (GraphEdge ge : edges) {
                Interface i = ge.getStart();

                IpAccessList outbound = i.getOutgoingFilter();
                if (outbound != null) {
                    String outName = router + "_" + i.getName() + "_OUTBOUND_" + outbound.getName();
                    BoolExpr outAcl = _ctx.mkBoolConst(outName);
                    BoolExpr outAclFunc = computeACL(outbound);
                    add(Eq(outAcl, outAclFunc));
                    _outboundAcls.put(i, outAcl);
                }

                IpAccessList inbound = i.getIncomingFilter();
                if (inbound != null) {
                    String inName = router + "_" + i.getName() + "_INBOUND_" + inbound.getName();
                    BoolExpr inAcl = _ctx.mkBoolConst(inName);
                    BoolExpr inAclFunc = computeACL(inbound);
                    add(Eq(inAcl, inAclFunc));
                    _inboundAcls.put(i, inAcl);
                }
            }
        });
    }

    private void addDataForwardingConstraints() {
        getGraph().getEdgeMap().forEach((router, edges) -> {
            for (GraphEdge ge : edges) {
                BoolExpr acl = _inboundAcls.get(ge.getStart());
                if (acl == null) {
                    acl = True();
                }
                BoolExpr cForward = _symbolicDecisions.getControlForwarding().get(router, ge);
                BoolExpr dForward = _symbolicDecisions.getDataForwarding().get(router, ge);
                BoolExpr notBlocked = And(cForward, acl);
                add(Eq(notBlocked, dForward));
            }
        });
    }

    public BoolExpr relevantOrigination(List<Prefix> prefixes) {
        BoolExpr acc = False();
        for (Prefix p : prefixes) {
            acc = Or(acc, isRelevantFor(p, _symbolicPacket.getDstIp()));
        }
        return acc;
    }

    private Integer addedCost(RoutingProtocol proto, Interface iface) {
        switch (proto) {
            case OSPF:
                return iface.getOspfCost();
            case ISIS:
                return iface.getIsisCost();
        }
        return 1;
    }

    private int defaultAdminDistance(Configuration conf, RoutingProtocol proto) {
        return proto.getDefaultAdministrativeCost(conf.getConfigurationFormat());
    }

    private void addImportConstraint(
            LogicalGraphEdge e, SymbolicRecord varsOther, Configuration conf, RoutingProtocol
            proto, Interface iface, String router, List<Prefix> originations) {

        SymbolicRecord vars = e.getSymbolicRecord();

        ArithExpr failed = _symbolicFailures.getFailedVariable(e.getEdge());
        BoolExpr notFailed = Eq(failed, Int(0));

        if (vars.getIsUsed()) {

            if (proto == RoutingProtocol.CONNECTED) {
                Prefix p = iface.getPrefix();
                BoolExpr relevant = And(Bool(iface.getActive()), isRelevantFor(p, _symbolicPacket
                        .getDstIp()), notFailed);
                BoolExpr values = And(vars.getPermitted(), safeEq(vars.getPrefixLength(), Int(p
                        .getPrefixLength())), safeEq(vars.getAdminDist(), Int(1)), safeEq(vars
                        .getLocalPref(), Int(0)), safeEq(vars.getMetric(), Int(0)));
                add(If(relevant, values, Not(vars.getPermitted())));
            }

            if (proto == RoutingProtocol.STATIC) {
                List<StaticRoute> srs = getGraph().getStaticRoutes().get(router).get(iface
                        .getName()); // should exist
                assert (srs != null);
                BoolExpr acc = Not(vars.getPermitted());
                for (StaticRoute sr : srs) {
                    Prefix p = sr.getNetwork();
                    BoolExpr relevant = And(Bool(iface.getActive()), isRelevantFor(p,
                            _symbolicPacket.getDstIp()), notFailed);
                    BoolExpr values = And(vars.getPermitted(), safeEq(vars.getPrefixLength(), Int
                            (p.getPrefixLength())), safeEq(vars.getAdminDist(), Int(sr
                            .getAdministrativeCost())), safeEq(vars.getLocalPref(), Int(0)),
                            safeEq(vars.getMetric(), Int(0)));
                    acc = If(relevant, values, acc);
                }
                add(acc);
            }

            if (proto == RoutingProtocol.OSPF || proto == RoutingProtocol.BGP) {
                BoolExpr val = Not(vars.getPermitted());
                if (varsOther != null) {
                    // Get the import policy for a given network, can return null if none,
                    // in which case we just will copy over the old variables.

                    BoolExpr isRoot = relevantOrigination(originations);
                    BoolExpr usable = And(Not(isRoot), Bool(iface.getActive()), varsOther
                            .getPermitted(), notFailed);
                    BoolExpr importFunction;
                    RoutingPolicy pol = getGraph().findImportRoutingPolicy(router, proto, e);
                    if (pol != null) {
                        importFunction = computeTransferFunction(varsOther, vars, conf, proto,
                                proto, pol.getStatements(), null);
                    } else {
                        // just copy the export policy in ospf/bgp
                        BoolExpr per = Eq(vars.getPermitted(), varsOther.getPermitted());
                        BoolExpr lp = safeEq(vars.getLocalPref(), varsOther.getLocalPref());
                        BoolExpr ad = safeEq(vars.getAdminDist(), varsOther.getAdminDist());
                        BoolExpr met = safeEq(vars.getMetric(), varsOther.getMetric());
                        BoolExpr med = safeEq(vars.getMed(), varsOther.getMed());
                        BoolExpr len = safeEq(vars.getPrefixLength(), varsOther.getPrefixLength());
                        importFunction = And(per, lp, ad, met, med, len);
                    }

                    add(If(usable, importFunction, val));
                } else {
                    add(val);
                }

            }
        }
    }

    private boolean addExportConstraint(
            LogicalGraphEdge e, SymbolicRecord varsOther, Configuration conf, RoutingProtocol
            proto, Interface iface, String router, boolean usedExport, List<Prefix> originations) {

        SymbolicRecord vars = e.getSymbolicRecord();

        ArithExpr failed = _symbolicFailures.getFailedVariable(e.getEdge());
        BoolExpr notFailed = Eq(failed, Int(0));

        // only add constraints once when using a single copy of export variables
        if (!_optimizations.getSliceCanKeepSingleExportVar().get(router).get(proto) ||
                !usedExport) {

            if (proto == RoutingProtocol.CONNECTED) {
                BoolExpr val = Not(vars.getPermitted());
                add(val);
            }

            if (proto == RoutingProtocol.STATIC) {
                BoolExpr val = Not(vars.getPermitted());
                add(val);
            }

            if (proto == RoutingProtocol.OSPF || proto == RoutingProtocol.BGP) {
                // originated routes
                Integer cost = addedCost(proto, iface);
                BoolExpr val = Not(vars.getPermitted());

                // default is to propagate the "best" route
                SymbolicRecord best = _symbolicDecisions.getBestVars(_optimizations, router, proto);

                // If the export is usable, then we propagate the best route after incrementing
                // the metric
                BoolExpr usable = And(Bool(iface.getActive()), best.getPermitted(), notFailed);

                BoolExpr acc;
                RoutingPolicy pol = getGraph().findExportRoutingPolicy(router, proto, e);
                if (pol != null) {
                    acc = computeTransferFunction(varsOther, vars, conf, proto, proto, pol
                            .getStatements(), cost);
                } else {
                    acc = And(Eq(vars.getPermitted(), True()), safeEq(vars.getPrefixLength(),
                            best.getPrefixLength()), safeEq(vars.getAdminDist(), best
                            .getAdminDist()), safeEq(vars.getMed(), best.getMed()), safeEq(vars
                            .getLocalPref(), best.getLocalPref()), safeEqAdd(vars.getMetric(),
                            best.getMetric(), cost));
                }

                acc = If(usable, acc, val);

                for (Prefix p : originations) {
                    BoolExpr relevant = And(Bool(iface.getActive()), isRelevantFor(p,
                            _symbolicPacket.getDstIp()));
                    int adminDistance = defaultAdminDistance(conf, proto);
                    int prefixLength = p.getPrefixLength();
                    BoolExpr values = And(vars.getPermitted(), safeEq(vars.getLocalPref(), Int(0)
                    ), safeEq(vars.getAdminDist(), Int(adminDistance)), safeEq(vars.getMetric(),
                            Int(cost)), safeEq(vars.getMed(), Int(100)), safeEq(vars
                            .getPrefixLength(), Int(prefixLength)));

                    acc = If(relevant, values, acc);
                }

                add(acc);
            }
            return true;
        }
        return false;
    }

    private void addTransferFunction() {
        getGraph().getConfigurations().forEach((router, conf) -> {
            for (RoutingProtocol proto : getGraph().getProtocols().get(router)) {
                Boolean usedExport = false;
                List<Prefix> originations = getOriginatedNetworks(conf, proto);
                for (ArrayList<LogicalGraphEdge> eList : _logicalGraph.getLogicalGraphEdges().get
                        (router, proto)) {
                    for (LogicalGraphEdge e : eList) {
                        GraphEdge ge = e.getEdge();
                        Interface iface = ge.getStart();
                        if (getGraph().isInterfaceUsed(conf, proto, iface)) {
                            SymbolicRecord varsOther;
                            switch (e.getEdgeType()) {
                                case IMPORT:
                                    varsOther = _logicalGraph.findOtherVars(e);
                                    addImportConstraint(e, varsOther, conf, proto, iface, router,
                                            originations);
                                    break;

                                case EXPORT:
                                    varsOther = _symbolicDecisions.getBestVars(_optimizations,
                                            router, proto);
                                    usedExport = addExportConstraint(e, varsOther, conf, proto,
                                            iface, router, usedExport, originations);
                                    break;
                            }
                        }
                    }
                }
            }
        });
    }

    private void addUnusedDefaultValueConstraints() {
        for (SymbolicRecord vars : _allSymbolicRecords) {

            BoolExpr notPermitted = Not(vars.getPermitted());
            ArithExpr zero = Int(0);

            if (vars.getAdminDist() != null) {
                add(Implies(notPermitted, Eq(vars.getAdminDist(), zero)));
            }
            if (vars.getMed() != null) {
                add(Implies(notPermitted, Eq(vars.getMed(), zero)));
            }
            if (vars.getLocalPref() != null) {
                add(Implies(notPermitted, Eq(vars.getLocalPref(), zero)));
            }
            if (vars.getPrefixLength() != null) {
                add(Implies(notPermitted, Eq(vars.getPrefixLength(), zero)));
            }
            if (vars.getMetric() != null) {
                add(Implies(notPermitted, Eq(vars.getMetric(), zero)));
            }
        }
    }

    private void addDestinationConstraint() {
        BoolExpr validDestRange = False();
        for (Prefix p : _destinations) {
            long lower = p.getAddress().asLong();
            long upper = p.getEndAddress().asLong();
            BoolExpr constraint;
            if (lower == upper) {
                constraint = Eq(_symbolicPacket.getDstIp(), Int(lower));
            } else {
                BoolExpr x = Ge(_symbolicPacket.getDstIp(), Int(lower));
                BoolExpr y = Le(_symbolicPacket.getDstIp(), Int(upper));
                constraint = And(x, y);
            }
            validDestRange = Or(validDestRange, constraint);
        }

        add(validDestRange);
    }

    private void initConfigurations() {
        getGraph().getConfigurations().forEach((router, conf) -> {
            initOspfInterfaceCosts(conf);
        });
    }

    public VerificationResult verify() {
        int numVariables = _allVariables.size();
        int numConstraints = _solver.getAssertions().length;
        int numNodes = getGraph().getConfigurations().size();
        int numEdges = 0;
        for (Map.Entry<String, Set<String>> e : getGraph().getNeighbors().entrySet()) {
            numEdges += e.getValue().size();
        }
        long start = System.currentTimeMillis();
        Status status = _solver.check();
        long time = System.currentTimeMillis() - start;

        VerificationStats stats = new VerificationStats(numNodes, numEdges, numVariables,
                numConstraints, time);

        if (status == Status.UNSATISFIABLE) {
            return new VerificationResult(true, null);
        } else if (status == Status.UNKNOWN) {
            return new VerificationResult(false, null);
        } else {
            Model m = _solver.getModel();
            SortedMap<String, String> model = new TreeMap<>();
            for (Expr e : _allVariables) {
                String name = e.toString();
                Expr val = m.evaluate(e, false);
                if (!val.equals(e)) {
                    model.put(name, val.toString());
                }
            }
            return new VerificationResult(false, model);
        }
    }

    public Graph getGraph() {
        return _logicalGraph.getGraph();
    }

    public void computeEncoding() {
        if (ENABLE_DEBUGGING) {
            System.out.println(getGraph().toString());
        }
        initConfigurations();
        computeCommunities();
        computeOptimizations();
        addVariables();
        addBoundConstraints();
        addFailedConstraints(0);
        addRedistributionConstraints();
        addTransferFunction();
        addBestPerProtocolConstraints();
        addChoicePerProtocolConstraints();
        addBestOverallConstraints();
        addControlForwardingConstraints();
        addAclFunctions();
        addDataForwardingConstraints();
        addUnusedDefaultValueConstraints();
        addDestinationConstraint();
    }
}