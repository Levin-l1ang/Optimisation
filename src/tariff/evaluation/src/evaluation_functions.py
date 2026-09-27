import logging
from typing import Tuple

from core.model.tariff import PriceMatrix
from core.model.ptn import Link, Stop
from core.model.graph import Graph
from core.model.impl.mapOD import MapOD

logger = logging.getLogger(__name__)


def compute_revenue(old_price_matrix: PriceMatrix, new_price_matrix: PriceMatrix, ptn: Graph[Link, Stop], od: MapOD) :
    rev_old = rev_new = 0
    for origin in ptn.getNodes():
        origin_id = origin.getId()
        for destination in ptn.getNodes():
            destination_id = destination.getId()
            price_old = old_price_matrix.getValue(origin_id,destination_id)
            price_new = new_price_matrix.getValue(origin_id,destination_id)
            if od:
                pass_od = od.getValue(origin_id,destination_id)
                rev_old += pass_od * price_old
                rev_new += pass_od * price_new
    nb_increased_prices, pass_increased_prices, nb_decreased_prices, pass_decreased_prices = compare_price_matrices(old_price_matrix, new_price_matrix, od, ptn)
    if not old_price_matrix.hasNonDiagonalZeros():
        obj_ref_inverse_sum = sum([(1/old_price_matrix.getValue(origin.getId(), destination.getId()))*abs(old_price_matrix.getValue(origin.getId(), destination.getId()) - new_price_matrix.getValue(origin.getId(), destination.getId())) for origin in ptn.getNodes() for destination in ptn.getNodes() if origin!=destination])
        obj_ref_inverse_max = max([(1/old_price_matrix.getValue(origin.getId(), destination.getId()))*abs(old_price_matrix.getValue(origin.getId(), destination.getId()) - new_price_matrix.getValue(origin.getId(), destination.getId())) for origin in ptn.getNodes() for destination in ptn.getNodes() if origin!=destination])
    else:
        obj_ref_inverse_sum = "None"
        obj_ref_inverse_max = "None"
    obj_unit_sum = sum([abs(old_price_matrix.getValue(origin.getId(), destination.getId()) - new_price_matrix.getValue(origin.getId(), destination.getId())) for origin in ptn.getNodes() for destination in ptn.getNodes() if origin!=destination])
    obj_unit_max = max([abs(old_price_matrix.getValue(origin.getId(), destination.getId()) - new_price_matrix.getValue(origin.getId(), destination.getId())) for origin in ptn.getNodes() for destination in ptn.getNodes() if origin!=destination])
    obj_od_sum = sum([od.getValue(origin.getId(), destination.getId())*abs(old_price_matrix.getValue(origin.getId(), destination.getId()) - new_price_matrix.getValue(origin.getId(), destination.getId())) for origin in ptn.getNodes() for destination in ptn.getNodes() if origin!=destination])
    obj_od_max = max([od.getValue(origin.getId(), destination.getId())*abs(old_price_matrix.getValue(origin.getId(), destination.getId()) - new_price_matrix.getValue(origin.getId(), destination.getId())) for origin in ptn.getNodes() for destination in ptn.getNodes() if origin!=destination])
    statistic_dict = {
        "taf_revenue_old" : rev_old,
        "taf_revenue_new": rev_new,
        "taf_od_pairs_increased_prices": nb_increased_prices,
        "taf_od_pairs_decreased_prices": nb_decreased_prices,
        "taf_passengers_increased_prices": pass_increased_prices,
        "taf_passengers_decreased_prices": pass_decreased_prices,
        "taf_objective_sum_unit": obj_unit_sum,
        "taf_objective_sum_od": obj_od_sum,
        "taf_objective_sum_reference_inverse": obj_ref_inverse_sum,
        "taf_objective_max_unit": obj_unit_max,
        "taf_objective_max_od": obj_od_max,
        "taf_objective_max_reference_inverse": obj_ref_inverse_max,
    }
    return statistic_dict


def compare_price_matrices(old_price_matrix: PriceMatrix, new_price_matrix: PriceMatrix, od: MapOD, ptn: Graph[Link, Stop]) -> Tuple[float, float, float, float]:
    """
    Compare two given price matrices and return the number of OD-pairs and number of passengers for which prices became higher/lower.
    :param old_price_matrix: The first price matrix, that is considered as old version for the comparison
    :param new_price_matrix: The second price matrix, that is considered as new version for the comparison
    :param od: OD-Matrix
    :param ptn: The ptn to work with
    :return: number of OD-pairs for which prices became higher, number of passengers for which prices became higher, number of OD-pairs
    for which prices became lower and number of passengers for which prices became lower.
    """
    nb_increased_prices = 0
    pass_increased_prices = 0
    nb_decreased_prices = 0
    pass_decreased_prices = 0
    for origin in ptn.getNodes():
        for destination in ptn.getNodes():
            old_price = old_price_matrix.getValue(origin.getId(), destination.getId())
            new_price = new_price_matrix.getValue(origin.getId(), destination.getId())
            if old_price > new_price:
                nb_decreased_prices += 1
                pass_decreased_prices += od.getValue(origin.getId(), destination.getId())
            elif old_price < new_price:
                nb_increased_prices += 1
                pass_increased_prices += od.getValue(origin.getId(), destination.getId())
    return nb_increased_prices, pass_increased_prices, nb_decreased_prices, pass_decreased_prices

