package net.lintim.generator;

import ilog.concert.IloConstraint;
import ilog.concert.IloException;
import ilog.concert.IloIntVar;
import ilog.concert.IloLinearIntExpr;
import ilog.concert.IloLinearNumExpr;
import ilog.cplex.IloCplex;

import java.io.FileNotFoundException;
import java.util.LinkedHashMap;

import net.lintim.callback.DefaultCallbackCplex;
import net.lintim.evaluator.LineCollectionEvaluator;
import net.lintim.exception.DataInconsistentException;
import net.lintim.main.LineConcept;
import net.lintim.model.Line;
import net.lintim.model.Link;
import net.lintim.util.BiLinkedHashMap;

/**
 * Solves the lineplanning cost model with help of Cplex. The wrapper, i.e.
 * {@link LineConcept} uses a class loader, since cplex.jar may be unavailble on
 * the system. If there are compile errors in eclipse, right click on
 * PeriodicTimetableGeneratorOdpespCplex.java in the package explorer and select
 * "Build Path -> Exclude".
 */
public class LineConceptGeneratorCplex extends LineConceptGenerator {
    /**
     * Wrapper to solve the actual problem with Cplex.
     */
    @Override
    protected void solveInternal() throws DataInconsistentException{
        Boolean failed = true;

        if(solver == Solver.CPLEX){
            try {
                solveCplex();
                failed = false;
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IloException e) {
                e.printStackTrace();
            }
        }
        else{
            throw new UnsupportedOperationException("no support for " +
                    "solver " + solver.name());
        }

        if(failed){
            throw new DataInconsistentException("line concept computation failed");
        }
    }

    protected void solveCplex() throws DataInconsistentException, IloException,
    FileNotFoundException{
        IloCplex model = new IloCplex();

        model.setName("LineConcept");
        // model.setOut(new FileOutputStream("cplex.log"));
        model.setOut(System.err);

        model.use(new DefaultCallbackCplex());

        IloLinearNumExpr objective = model.linearNumExpr(0.0);

        // frequencies and existance
        LinkedHashMap<Line, IloIntVar> f = new LinkedHashMap<Line, IloIntVar>();
        LinkedHashMap<Line, IloIntVar> x = new LinkedHashMap<Line, IloIntVar>();
        LinkedHashMap<Link, IloConstraint> linkc1 =
            new LinkedHashMap<Link, IloConstraint>();
        LinkedHashMap<Link, IloConstraint> linkc2 =
            new LinkedHashMap<Link, IloConstraint>();

        // one line per edge
        LinkedHashMap<Line, IloConstraint> fxc1 =
            new LinkedHashMap<Line, IloConstraint>();
        LinkedHashMap<Line, IloConstraint> fxc2 =
            new LinkedHashMap<Line, IloConstraint>();
        LinkedHashMap<Link, IloConstraint> linkolpe =
            new LinkedHashMap<Link, IloConstraint>();

        // line pair intersections connected
        BiLinkedHashMap<Line, Line, IloConstraint> lpic =
            new BiLinkedHashMap<Line, Line, IloConstraint>();

        // Formulation
        for(Line line : lines){
            Integer index = line.getIndex();

            // basic model
            Integer maxLinkFreq = 0;
            for(Link prelink : line.getLinks()){
                Link link = ptnIsUndirected ?
                        prelink.getUndirectedRepresentative() : prelink;
                maxLinkFreq = Math.max(maxLinkFreq, link.getUpperFrequency());
            }

            IloIntVar fl = model.intVar(0, maxLinkFreq, "frequency_" + index);
            f.put(line, fl);

            if(linearModel == LinearModel.ONE_LINE_PER_EDGE ||
                    forceLinePairIntersectionsConnected){

                IloIntVar xl = model.boolVar("line_exists_" + index);
                x.put(line, xl);

                IloLinearIntExpr xtimesmaxfreq =
                    model.linearIntExpr();
                xtimesmaxfreq.addTerm(maxLinkFreq, xl);

                fxc1.put(line, model.addLe(xl, fl, "fxc1_" + index));
                fxc2.put(line, model.addLe(fl, xtimesmaxfreq, "fxc2_" + index));
            }

            objective.addTerm(line.getCost(), fl);

        }

        if(forceLinePairIntersectionsConnected){
            for(Line line1 : lines){
                for(Line line2 : lines){
                    if(!LineCollectionEvaluator.
                            linePairIntersectionConnected(line1, line2)){
                        IloLinearIntExpr expr = model.linearIntExpr();
                        expr.addTerm(1, x.get(line1));
                        expr.addTerm(1, x.get(line2));
                        lpic.put(line1, line2, model.addLe(expr, 1.0, "lpic_" +
                                line1.getIndex() + "_" + line2.getIndex()));
                    }
                }
            }
        }

        for (Link prelink : links) {
            Link link = ptnIsUndirected ?
                    prelink.getUndirectedRepresentative() : prelink;

            // basic model
            Integer index = link.getIndex();

            IloLinearIntExpr linkfreqexpr = model.linearIntExpr();

            for(Line line : ptnIsUndirected ?
                    lpool.getUndirectedLinesByUndirectedLink(link) :
                        lpool.getDirectedLinesByLink(link)){
                linkfreqexpr.addTerm(1, f.get(line));
            }

            linkc1.put(link, model.addLe((double)link.getLowerFrequency(),
                    linkfreqexpr, "constraint_link_1_" + index));
            linkc2.put(link, model.addLe(linkfreqexpr,
                    (double)link.getUpperFrequency(), "constraint_link_2_"
                    + index));

            if(linearModel == LinearModel.ONE_LINE_PER_EDGE){

                IloLinearIntExpr linklineexpr = model.linearIntExpr();

                for(Line line : lpool.getUndirectedLinesByUndirectedLink(link)){
                    linklineexpr.addTerm(1, x.get(line));
                }

                linkolpe.put(link, model.addLe(linklineexpr, 1,
                        "constraint_link_olpe_" + index));

            }
        }

        model.addMinimize(objective);

        if(model.solve()){
            for (Line line : lines) {
                line.setFrequency((int)Math.round(model.getValue(f.get(line))));
            }

            objectiveFunction = model.getObjValue();
        }
        else{
            throw new DataInconsistentException("no feasible line concept available");
        }

    }

}
