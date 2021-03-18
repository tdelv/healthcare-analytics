package solver.ip;

import ilog.cplex.*;
import ilog.concert.*;

import java.util.Arrays;
import java.util.Optional;

public class IPInstance {
    // IBM Ilog Cplex Solver
    IloCplex cplex;

    int numTests;            // number of tests
    int numDiseases;        // number of diseases
    double[] costOfTest;  // [numTests] the cost of each test
    int[][] A;            // [numTests][numDiseases] 0/1 matrix if test is positive for disease

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

        Optional<Double> solution = solveRecursive();
        if (solution.isPresent()) {
            return Optional.of((int) Math.ceil(solution.get()));
        } else {
            return Optional.empty();
        }
    }

    private Optional<Double> solveRecursive() throws IloException {
        return solveLinear();
    }

    private Optional<Double> solveLinear() throws IloException {
        IloCplex cplex = new IloCplex();
        IloNumVar[] useTest = cplex.numVarArray(numTests, 0, 1, varType);

        /*
            int numTests;            // number of tests
            int numDiseases;        // number of diseases
            double[] costOfTest;  // [numTests] the cost of each test
            int[][] A;            // [numTests][numDiseases] 0/1 matrix if test is positive for disease
         */

        /*
            For each pair of disease d1, d2:
                Check that there is some test t for which A[t][d1] != A[t][d2] (and useTest[t] = 1)
            Forall d1, d2, Exists t | (A[t][d1] != A[t][d2] and useTest[t] = 1)
         */

        for (int d1 = 0; d1 < numDiseases; d1 ++) {
            for (int d2 = 0; d2 < numDiseases; d2 ++) {
                if (d1 != d2) {

                    IloNumExpr differs[] = new IloNumExpr[numTests];
                    for (int t = 0; t < numTests; t++) {
                        // If there is a test that differs for the two diseases
                        IloNumVar testDiffers = cplex.numVar(0, 1, varType);

                        IloNumVar testCompare = cplex.numVar(0, 1, varType);
                        int isDifferent = A[t][d1] - A[t][d2];
                        cplex.add(cplex.eq(testCompare, isDifferent));

                        // If the test is different, then testDiffers > 0
                        cplex.addGe(testDiffers, testCompare); // slack
                        cplex.addGe(testDiffers, cplex.prod(testCompare, -1)); // slack

                        // Makes sures that test is used
                        cplex.addGe(useTest[t], testDiffers); // slack


                        differs[t] = testDiffers;
                    }
                    // Checks that there is at least one test that differs for the 2 diseases
                    cplex.addGe(cplex.sum(differs), 1);
                }
            }
        }

        IloNumExpr totalCost = cplex.scalProd(useTest, costOfTest);
        cplex.addMinimize(totalCost);


        if (cplex.solve()) {
            return Optional.of(cplex.getObjValue());
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