package net.lintim.io;

import net.lintim.exception.OutputFileException;
import net.lintim.solver.Constraint;
import net.lintim.solver.LinearExpression;
import net.lintim.solver.Model;
import net.lintim.solver.Variable;
import net.lintim.solver.impl.NativeConstraint;
import net.lintim.solver.impl.NativeLinearExpression;
import net.lintim.solver.impl.NativeVariable;
import net.lintim.util.Logger;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;

/**
 * Class to write lp files that can be read be integer programming solvers.
 *
 * Use {@link Builder#build()} on a {@link Builder} object to create the writer and use {@link #write()} afterwards.
 */
public class LPWriter {

    private static final Logger logger = new Logger(LPWriter.class);

    private final Collection<? extends Variable> variables;
    private final Collection<NativeConstraint<? extends Variable>> constraints;
    private final NativeLinearExpression<? extends Variable> objective;
    private final Model.OptimizationSense sense;
    private final String filename;

    private static final int MAX_LINE_LENGTH = 255;

    private LPWriter(Collection<? extends Variable> variables,
                     Collection<NativeConstraint<? extends Variable>> constraints,
                     NativeLinearExpression<? extends Variable> objective, Model.OptimizationSense sense, String filename) {
        this.variables = variables;
        this.constraints = constraints;
        this.objective = objective;
        this.sense = sense;
        this.filename = filename;
    }

    /**
     * Start the writing process. The behavior is controlled by the {@link Builder} object this object was created with.
     * @return the list of variables. This may be needed to read a solution back later, e.g. by
     * {@link net.lintim.solver.impl.GLPKSolver}
     */
    public List<Variable> write() {
        try {
            logger.debug("Writing to file " + filename);
            Path writeFolderPathParent = Paths.get(filename).getParent();
            if (writeFolderPathParent != null) {
                Files.createDirectories(writeFolderPathParent);
            }
            BufferedWriter writer = Files.newBufferedWriter(Paths.get(filename), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            writer.write("\\LP File create by LinTim Java Core\n");
            // First, write the objective
            if (sense == Model.OptimizationSense.MAXIMIZE) {
                writer.write("Maximize\n");
            }
            else {
                writer.write("Minimize\n");
            }
            writer.write("obj:");
            // Track the current line length, since the maximal length of a line is given by the file format
            int currentLineLength = 5;
            int nextEntryLength;
            String nextEntry;
            // We track the index of every variable. A variable gets assigned an index if it is first used. This
            // information may be needed to read back solution files (e.g. of GLPK)
            ArrayList<Variable> listOfVariables = new ArrayList<>();
            HashSet<Variable> alreadyFoundVariables = new HashSet<>();
            for (Map.Entry<? extends Variable, Double> objEntry: objective.getValues().entrySet()) {
                if (!alreadyFoundVariables.contains(objEntry.getKey())) {
                    listOfVariables.add(objEntry.getKey());
                    alreadyFoundVariables.add(objEntry.getKey());
                }
                // Determine the new length of the line
                nextEntry = transformExpressionEntry(objEntry);
                if (nextEntry.length() == 0) {
                    continue;
                }
                nextEntryLength = nextEntry.length();
                if (currentLineLength + nextEntryLength >= MAX_LINE_LENGTH) {
                    writer.write("\n");
                    currentLineLength = 0;
                }
                writer.write(nextEntry);
                currentLineLength += nextEntryLength;
            }
            // Next step, write constraints
            writer.write("\n\nSubject To\n");
            for (NativeConstraint<? extends Variable> constraint: constraints) {
                // First, check if we need to write the constraint, i.e., if there are any variables in it
                if (constraint.getLhs().getValues().size() == 0) {
                    continue;
                }
                writer.write(" " + constraint.getName() + ":");
                currentLineLength = constraint.getName().length() + 2;
                // First, write the lhs
                for (Map.Entry<? extends Variable, Double> constrEntry: constraint.getLhs().getValues().entrySet()) {
                    if (!alreadyFoundVariables.contains(constrEntry.getKey())) {
                        listOfVariables.add(constrEntry.getKey());
                        alreadyFoundVariables.add(constrEntry.getKey());
                    }
                    nextEntry = transformExpressionEntry(constrEntry);
                    if (nextEntry.length() == 0) {
                        continue;
                    }
                    nextEntryLength = nextEntry.length();
                    if (currentLineLength + nextEntryLength >= MAX_LINE_LENGTH) {
                        writer.write("\n");
                        currentLineLength = 0;
                    }
                    writer.write(nextEntry);
                    currentLineLength += nextEntryLength;
                }
                // Now, the constraint sense
                if (constraint.getSense() == Constraint.ConstraintSense.LESS_EQUAL) {
                    nextEntry = " <= ";
                }
                else if (constraint.getSense() == Constraint.ConstraintSense.GREATER_EQUAL) {
                    nextEntry = " >= ";
                }
                else {
                    nextEntry = " = ";
                }
                nextEntryLength = nextEntry.length();
                if (currentLineLength + nextEntryLength >= MAX_LINE_LENGTH) {
                    writer.write("\n");
                    currentLineLength = 0;
                }
                writer.write(nextEntry);
                currentLineLength += nextEntryLength;
                // And lastly the rhs
                if (constraint.getRhs() >= 0) {
                    nextEntry = String.valueOf(constraint.getRhs());
                }
                else {
                    nextEntry = "- " + Math.abs(constraint.getRhs());
                }
                nextEntryLength = nextEntry.length();
                if (currentLineLength + nextEntryLength >= MAX_LINE_LENGTH) {
                    writer.write("\n");
                }
                writer.write(nextEntry + "\n");
            }
            // Next, write variable bounds
            // Store binary and integer variables for next step
            ArrayList<Variable> integerVariables = new ArrayList<>();
            ArrayList<Variable> binaryVariables = new ArrayList<>();
            writer.write("\n\nBounds\n");
            for (Variable variable: variables) {
                if (!alreadyFoundVariables.contains(variable)) {
                    listOfVariables.add(variable);
                    alreadyFoundVariables.add(variable);
                }
                if (variable.getType() == Variable.VariableType.INTEGER) {
                    integerVariables.add(variable);
                }
                else if (variable.getType() == Variable.VariableType.BINARY) {
                    binaryVariables.add(variable);
                }
                if (variable.getLowerBound() == variable.getUpperBound()) {
                    writer.write(variable.getName() + " = " + variable.getLowerBound());
                }
                else if (variable.getLowerBound() == Double.NEGATIVE_INFINITY) {
                    if (variable.getUpperBound() == Double.POSITIVE_INFINITY) {
                        writer.write(variable.getName() + " FREE\n");
                    }
                    writer.write("-INFINITY <= " + variable.getName() + " <= " + variable.getUpperBound());
                }
                else if (variable.getLowerBound() == 0) {
                    if (variable.getUpperBound() == Double.POSITIVE_INFINITY) {
                        continue;
                    }
                    writer.write(variable.getName() + " <= " + variable.getUpperBound());
                }
                else {
                    if (variable.getUpperBound() == Double.POSITIVE_INFINITY) {
                        writer.write(variable.getLowerBound() + " <= " + variable.getName());
                    }
                    else {
                        writer.write(variable.getLowerBound() + " <= " + variable.getName() + " <= " +
                            variable.getUpperBound());
                    }
                }
            }
            // Next, write integer restrictions
            writer.write("\n\nGenerals\n");
            currentLineLength = 0;
            for (Variable var: integerVariables) {
                nextEntry = " " + var.getName();
                nextEntryLength = nextEntry.length();
                if (currentLineLength + nextEntryLength >= MAX_LINE_LENGTH) {
                    writer.write("\n");
                    currentLineLength = 0;
                }
                writer.write(nextEntry);
                currentLineLength += nextEntryLength;
            }
            writer.write("\n\nBinaries\n");
            currentLineLength = 0;
            for (Variable var: binaryVariables) {
                nextEntry = " " + var.getName();
                nextEntryLength = nextEntry.length();
                if (currentLineLength + nextEntryLength >= MAX_LINE_LENGTH) {
                    writer.write("\n");
                    currentLineLength = 0;
                }
                writer.write(nextEntry);
                currentLineLength += nextEntryLength;
            }
            writer.write("\nEnd\n");
            writer.close();
            return listOfVariables;
        } catch (IOException e) {
            logger.error(e.toString());
            throw new OutputFileException(filename);
        }
    }

    private static String transformExpressionEntry(Map.Entry<? extends Variable, Double> entry) {
        String result;
        if (entry.getValue() == 0) {
            return "";
        }
        else if (entry.getValue() < 0) {
            result = " - ";
        }
        else {
            result = " + ";
        }
        result += Math.abs(entry.getValue());
        result += " " + entry.getKey().getName();
        return result;
    }

    /**
     * Builder object for a LP writer.
     *
     * Use {@link #Builder(Collection, Collection, String)} to create a builder with default options, afterwards use
     * the setters to adapt it. The setters return this object, therefore they can be chained.
     *
     * For the possible parameters and their default values, see {@link #Builder(Collection, Collection, String)}.
     * To create a writer object, use {@link #build()} after setting all parameters.
     */
    public static class Builder {
        private final Collection<? extends Variable> variables;
        private final Collection<NativeConstraint<? extends Variable>> constraints;
        private NativeLinearExpression<? extends Variable> objective = new NativeLinearExpression<>();
        private Model.OptimizationSense sense = Model.OptimizationSense.MINIMIZE;
        private final String filename;

        /**
         * Create a default builder object. Possible parameters for this calss are (with the default in parentheses):
         * <ul>
         *     <li>
         *         variables (given in constructor) - the variables of the problem
         *     </li>
         *     <li>
         *         constraints (given in constructor) - the constraints of the problem
         *     </li>
         *     <li>
         *         filename (given in constructor) - the name of the file to write to
         *     </li>
         *     <li>
         *         sense ({@link Model.OptimizationSense#MINIMIZE}) - the sense of the optimization problem
         *     </li>
         *     <li>
         *         objective (no objective) - the objective of the optimization problem
         *     </li>
         * </ul>
         * @param variables the variables of the problem
         * @param constraints the constraints of the problem
         * @param filename the name of the file to write to
         */
        public Builder(Collection<? extends Variable> variables,
                       Collection<NativeConstraint<NativeVariable>> constraints,
                       String filename) {
            this.variables = new ArrayList<>(variables);
            this.constraints = new ArrayList<>(constraints);
            this.filename = filename;
        }

        /**
         * Set the optimization sense of the problem
         * @param sense the new sense
         * @return this object
         */
        public Builder setSense(Model.OptimizationSense sense) {
            this.sense = sense;
            return this;
        }

        /**
         * Set a new objective for this problem. Any old objective will be replaced. Will copy the given expression,
         * so any changes afterwards will not be reflected here.
         * @param objective the new objective
         * @return this object
         */
        public Builder setObjective(NativeLinearExpression<? extends Variable> objective) {
            this.objective = new NativeLinearExpression<>();
            this.objective.add(objective);
            return this;
        }

        /**
         * Create a new aperiodic ean writer with the current builder settings
         * @return the new writer. Use {@link #write()} for the writing process.
         */
        public LPWriter build() {
            return new LPWriter(variables, constraints, objective, sense, filename);
        }
    }
}
