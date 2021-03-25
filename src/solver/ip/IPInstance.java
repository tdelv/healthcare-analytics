package solver.ip;

import ilog.cplex.*;
import ilog.concert.*;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class IPInstance {
    // IBM Ilog Cplex Solver
    IloCplex cplex;

    int numTests;            // number of tests
    int numDiseases;        // number of diseases
    double[] costOfTest;  // [numTests] the cost of each test
    int[][] A;            // [numTests][numDiseases] 0/1 matrix if test is positive for disease

    Optional<Double> minCost;

    SolveType solveType = SolveType.solveFloat;
    IloNumVarType varType = IloNumVarType.Float;

    enum SolveType {
        solveFloat,
        solveInt
    }

    public IPInstance() {
        super();
    }

    void init(int numTests, int numDiseases, double[] costOfTest, int[][] A) {
        assert (numTests >= 0) : "Init error: numtests should be non-negative " + numTests;
        assert (numDiseases >= 0) : "Init error: numtests should be non-negative " + numTests;
        assert (costOfTest != null) : "Init error: costOfTest cannot be null";
        assert (costOfTest.length == numTests) : "Init error: costOfTest length differ from numTests" + costOfTest.length + " vs. " + numTests;
        assert (A != null) : "Init error: A cannot be null";
        assert (A.length == numTests) : "Init error: Number of rows in A differ from numTests" + A.length + " vs. " + numTests;
        assert (A[0].length == numDiseases) : "Init error: Number of columns in A differ from numDiseases" + A[0].length + " vs. " + numDiseases;

        this.numTests = numTests;
        this.numDiseases = numDiseases;
        this.costOfTest = new double[numTests];
        for (int i = 0; i < numTests; i++)
            this.costOfTest[i] = costOfTest[i];
        this.A = new int[numTests][numDiseases];
        for (int i = 0; i < numTests; i++)
            for (int j = 0; j < numDiseases; j++)
                this.A[i][j] = A[i][j];
    }

    public Optional<Integer> solve() throws IloException {
        switch (solveType) {
            case solveFloat:
                varType = IloNumVarType.Float;
                break;
            case solveInt:
                varType = IloNumVarType.Int;
                break;
            default:
                System.err.println("Invalid solveType: " + solveType);
                System.exit(1);
        }

        /*
            Setup recursion here!
         */

        Map<Integer, Boolean> setTests = new HashMap<Integer, Boolean>();


        Optional<Double> solution = solveRecursive(setTests);
        if (solution.isPresent()) {
            return Optional.of((int) Math.ceil(solution.get()));
        } else {
            return Optional.empty();
        }
    }

    private void solveRecursive(Map<Integer, Boolean> setTests) throws IloException {

        // solve with setTests constraints

        // can get:
        // linear solution - check if we need to prune
        // integer solution - check if we need to better or worse
        // infeasible

        Optional<SolveLinearReturn> linearResult = this.solveLinear(setTests);

        // No best solution cost yet
        if (!minCost.isPresent()) {
            // Found an integer solution
            if (linearResult.isPresent() && linearResult.get().isInteger) {
                // Update best solution cost
                minCost = Optional.of(linearResult.get().totalCost);
            }
        }
        // Found another miscellaneous solution
        if (linearResult.isPresent()) {
            // Integer or float solution
            boolean isInteger = linearResult.get().isInteger;
            if (isInteger) {
                if (linearResult.get().totalCost < minCost.get()) {
                    // Update best solution cost
                    minCost = Optional.of(linearResult.get().totalCost);
                }

            }
            else {
                if (linearResult.get().totalCost < minCost.get()) {
                    // keep going
                }
            }
        }

        return;
    }

    private class SolveLinearReturn {
        public double totalCost;
        public boolean isInteger;

        public SolveLinearReturn(double totalCost, boolean isInteger) {
            this.totalCost = totalCost;
            this.isInteger = isInteger;
        }
    }

    private Optional<SolveLinearReturn> solveLinear(Map<Integer, Boolean> setTests) throws IloException {
        IloCplex cplex = new IloCplex();
        IloNumVar[] useTest = cplex.numVarArray(numTests, 0, 1, varType);

        /*
            For each pair of disease d1, d2:
                Check that there is some test t for which A[t][d1] != A[t][d2] (and useTest[t] = 1)
            Forall d1, d2, Exists t | (A[t][d1] != A[t][d2] and useTest[t] = 1)
         */

        for (int d1 = 0; d1 < numDiseases - 1; d1 ++) {
            for (int d2 = d1 + 1; d2 < numDiseases; d2 ++) {
                IloNumExpr canDifferentiate = cplex.numExpr();
                for (int t = 0; t < numTests; t++) {
                    /*
                        If the test differentiates the two diseases,
                        add it to canDifferentiate.
                     */

                    if (A[t][d1] != A[t][d2]) {
                        canDifferentiate = cplex.sum(canDifferentiate, useTest[t]);
                    }
                }
                // Checks that there is at least one test that differs for the 2 diseases
                cplex.addGe(canDifferentiate, 1); // slack
            }
        }

        // Get cost of used tests
        IloNumExpr totalCost = cplex.scalProd(useTest, costOfTest);
        cplex.addMinimize(totalCost);


        if (cplex.solve()) {
//            System.out.print("Used tests:");
//            for (int i = 0; i < numTests; i ++) {
//                if (cplex.getValue(useTest[i]) == 1) {
//                    System.out.print(" " + i);
//                }
//            }
//            System.out.println();
//            System.out.print("Cost of tests:");
//            for (int i = 0; i < numTests; i ++) {
//                if (cplex.getValue(useTest[i]) == 1) {
//                    System.out.print(" " + costOfTest[i]);
//                }
//            }
//            System.out.println();
//            for (int d1 = 0; d1 < numDiseases - 1; d1 ++) {
//                for (int d2 = d1 + 1; d2 < numDiseases; d2 ++) {
//                    boolean differed = false;
//                    for (int t = 0; t < numTests; t ++) {
//                        if ((cplex.getValue(useTest[t]) == 1) && (A[t][d1] != A[t][d2])) {
//                            differed = true;
//                            System.out.println("Diseases " + d1 + " and " + d2 + " diff by test " + t);
//                        }
//                    }
//                    if (!differed) {
//                        System.out.println("Diseases " + d1 + " and " + d2 + " not differed :(");
//                    }
//                  }
//            }
            boolean isInteger = true;
            for (int test = 0; test <= numTests; test ++) {
                double testUsed = cplex.getValue(useTest[test]);
                isInteger = isInteger && (testUsed == 0 || testUsed == 1);
            }
            return Optional.of(new SolveLinearReturn(cplex.getObjValue(), isInteger));
        } else {
            return Optional.empty();
        }
    }

    private Optional<Double> solveDual() throws IloException {
        IloCplex cplex = new IloCplex();
        IloNumVar[] useTest = cplex.numVarArray(numTests, 0, 1, varType);

        if (cplex.solve()) {
            return Optional.of(cplex.getObjValue());
        } else {
            return Optional.empty();
        }
    }

    public String toString() {
        StringBuffer buf = new StringBuffer();
        buf.append("Number of tests: " + numTests + "\n");
        buf.append("Number of diseases: " + numDiseases + "\n");
        buf.append("Cost of tests: " + Arrays.toString(costOfTest) + "\n");
        buf.append("A:\n");
        for (int i = 0; i < numTests; i++)
            buf.append(Arrays.toString(A[i]) + "\n");
        return buf.toString();
    }
}