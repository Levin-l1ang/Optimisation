import logging
import sys

import pyomo.environ as pyo
from pyomo.core import ConcreteModel
from pyomo.opt import SolverStatus, TerminationCondition

from core.io.config import ConfigReader
from core.io.lines import LineReader, LineWriter
from core.io.ptn import PTNReader
from core.model.graph import Graph
from core.model.lines import LinePool
from core.model.ptn import Link, Stop
from core.util.config import Config
from helper import add_time_limit, add_mip_gap, get_solver

logger = logging.getLogger(__name__)


class CostModel:
    def __init__(self, ptn: Graph[Stop, Link], line_pool: LinePool):
        self.ptn = ptn
        self.line_pool = line_pool
        self.model = ConcreteModel()

    def solve(self, config: Config):
        solver_type = config.getSolverType("lc_solver")
        #solver = SolverFactory(solver_type.name.lower())
        solver = get_solver(solver_type)
        # TODO: Determine options
        add_time_limit(solver, solver_type, config.getIntegerValue("lc_timelimit"))
        add_mip_gap(solver, solver_type, config.getDoubleValue("lc_mip_gap"))
        display_output = config.getLogLevel("console_log_level") == logging.DEBUG
        results = solver.solve(self.model, tee=display_output)
        self.read_solution(results)

    def read_solution(self, results):
        if results.solver.status != SolverStatus.ok:
            logger.warning("Solver did not finish ok!")
        if results.solver.termination_condition == TerminationCondition.optimal or \
                results.solver.termination_condition == TerminationCondition.feasible:
            logger.debug(f"Solution status: {results.solver.termination_condition}")
            logger.debug(f"Objective value: {self.model.OBJ()}")
            logger.debug("Read back solution")
            for line in self.line_pool.getLines():
                line.setFrequency(int(round(pyo.value(self.model.f[line.getId()]))))

    def create_model(self):
        self.model.f = pyo.Var([line.getId() for line in self.line_pool.getLines()], domain=pyo.NonNegativeIntegers)

        self.model.OBJ = pyo.Objective(expr=sum(self.model.f[line.getId()] * line.getCost() for line in self.line_pool.getLines()), sense=pyo.minimize)

        self.model.LowerBound = pyo.ConstraintList()
        self.model.UpperBound = pyo.ConstraintList()
        for edge in self.ptn.getEdges():
            print(edge)
            line_ids_containing_edge = [line.getId() for line in self.line_pool.getLines()
                                        if edge in line.getLinePath().getEdges()]

            self.model.LowerBound.add(sum(self.model.f[line_id] for line_id in line_ids_containing_edge)
                                      >= edge.getLowerFrequencyBound())
            self.model.UpperBound.add(sum(self.model.f[line_id] for line_id in line_ids_containing_edge)
                                      <= edge.getUpperFrequencyBound())
        print("finished")

    def create_set_based_model(self):
        self.model.line_index_set = pyo.Set(initialize=[line.getId() for line in self.line_pool.getLines()])
        self.model.edge_index_set = pyo.Set(initialize=[edge.getId() for edge in self.ptn.getEdges()])
        self.model.f = pyo.Var(self.model.line_index_set, domain=pyo.NonNegativeIntegers)

        def obj_rule(model):
            return sum(model.f[line.getId()] * line.getCost() for line in self.line_pool.getLines())
        self.model.OBJ = pyo.Objective(rule=obj_rule, sense=pyo.minimize)

        def lower_bound_rule(model, edge_id: int):
            print("test")
            edge = self.ptn.getEdge(edge_id)
            line_ids_containing_edge = [line.getId() for line in self.line_pool.getLines()
                                        if edge in line.getLinePath().getEdges()]
            return sum(model.f[line_id] for line_id in line_ids_containing_edge) >= edge.getLowerFrequencyBound()
        self.model.LowerBound = pyo.Constraint(self.model.edge_index_set, rule=lower_bound_rule)
        def upper_bound_rule(model, edge_id: int):
            edge = self.ptn.getEdge(edge_id)
            line_ids_containing_edge = [line.getId() for line in self.line_pool.getLines()
                                        if edge in line.getLinePath().getEdges()]
            return sum(model.f[line_id] for line_id in line_ids_containing_edge) <= edge.getUpperFrequencyBound()
        self.model.UpperBound = pyo.Constraint(self.model.edge_index_set, rule=upper_bound_rule)


if __name__ == '__main__':
    ConfigReader.read(sys.argv[1])
    ptn = PTNReader.read(read_loads=True)
    line_pool = LineReader.read(ptn, read_frequencies=False)
    model = CostModel(ptn, line_pool)
    model.create_model()
    model.solve(Config.getDefaultConfig())
    LineWriter.write(line_pool, write_pool=False)