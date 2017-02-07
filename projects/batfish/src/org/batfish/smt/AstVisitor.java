package org.batfish.smt;


import org.batfish.datamodel.Configuration;
import org.batfish.datamodel.routing_policy.RoutingPolicy;
import org.batfish.datamodel.routing_policy.expr.*;
import org.batfish.datamodel.routing_policy.statement.If;
import org.batfish.datamodel.routing_policy.statement.Statement;

import java.util.List;
import java.util.function.Consumer;

public class AstVisitor {

    public AstVisitor() {
    }

    public void visit(Configuration conf, BooleanExpr e, Consumer<Statement> fs, Consumer<BooleanExpr> fe) {
        fe.accept(e);
        if (e instanceof Conjunction) {
            Conjunction c = (Conjunction) e;
            for (BooleanExpr be : c.getConjuncts()) {
                visit(conf, be, fs, fe);
            }
        } else if (e instanceof Disjunction) {
            Disjunction d = (Disjunction) e;
            for (BooleanExpr be : d.getDisjuncts()) {
                visit(conf, be, fs, fe);
            }
        } else if (e instanceof Not) {
            Not n = (Not) e;
            visit(conf, n.getExpr(), fs, fe);
        } else if (e instanceof CallExpr) {
            CallExpr c = (CallExpr) e;
            RoutingPolicy rp = conf.getRoutingPolicies().get(c.getCalledPolicyName());
            visit(conf, rp.getStatements(), fs, fe);
        }
    }

    public void visit(Configuration conf, Statement s, Consumer<Statement> fs, Consumer<BooleanExpr> fe) {
        fs.accept(s);
        if (s instanceof If) {
            If i = (If) s;
            visit(conf, i.getGuard(), fs, fe);
            visit(conf, i.getTrueStatements(), fs, fe);
            visit(conf, i.getFalseStatements(), fs, fe);
        }
    }

    public void visit(Configuration conf, List<Statement> ss, Consumer<Statement> fs, Consumer<BooleanExpr> fe) {
        for (Statement s : ss) {
            visit(conf, s, fs, fe);
        }
    }

}
