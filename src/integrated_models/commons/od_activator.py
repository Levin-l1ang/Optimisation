import logging
import random
from typing import List

import preprocessing
from core.exceptions.exceptions import LinTimException
from ean_data import Ean
from od_data import OD, ODPair
from parameters import ODActivator
from parameters import ODActivatorParameters

logger = logging.getLogger(__name__)


def activate_od_pairs(od: OD, parameters: ODActivatorParameters, ean: Ean = None, random_seed: int = 0) -> None:
    if parameters.number_of_routed_od_pairs == -1:
        activate_all_od_pairs(od, ean)
        return
    if parameters.number_of_routed_od_pairs < 0:
        raise LinTimException("Invalid choice for the number of OD pairs: {parameters.number_of_routed_od_pairs}")

    use_ean = parameters.od_activator == ODActivator.POTENTIAL or \
              parameters.od_activator == ODActivator.LARGEST_WEIGHT_WITH_TRANSFER or \
              parameters.od_activator == ODActivator.DIFF
    if use_ean:
        find_passenger_paths(od, ean, parameters)
    number_of_active_od_pairs = 0
    sorted_od_pairs = get_sorted_od_pairs(parameters.od_activator, od, random_seed)
    if parameters.number_of_routed_od_pairs > len(sorted_od_pairs):
        logger.warning(f"Try to activate {parameters.number_of_routed_od_pairs} of {len(sorted_od_pairs)} "
                       f"OD pairs with positive demand...")
    for od_pair in sorted_od_pairs:
        if number_of_active_od_pairs < parameters.number_of_routed_od_pairs:
            od_pair.active = True
            number_of_active_od_pairs += 1
    if use_ean:
        ean.reset_events_and_activities()


def activate_all_od_pairs(od: OD, ean: Ean) -> None:
    for od_pair in od.get_all_od_pairs():
        if od_pair.get_total_passengers() > 0:
            od_pair.active = True
    if ean:
        ean.reset_events_and_activities()


def get_sorted_od_pairs(activator: ODActivator, od: OD, random_seed: int) -> List[ODPair]:
    all_od_pairs = [pair for pair in od.get_all_od_pairs() if pair.get_total_passengers() > 0]
    if activator == ODActivator.LARGEST_WEIGHT:
        return sorted(all_od_pairs, key=lambda x: x.get_total_passengers(), reverse=True)
    if activator == ODActivator.SMALLEST_WEIGHT:
        return sorted(all_od_pairs, key=lambda x: x.get_total_passengers(), reverse=False)
    if activator == ODActivator.RANDOM:
        random.seed(random_seed)
        random.shuffle(all_od_pairs)
        return all_od_pairs
    if activator == ODActivator.LARGEST_DISTANCE:
        return sorted(all_od_pairs, key=lambda x: (x.origin.x_coordinate - x.destination.x_coordinate) ** 2
                                           + (x.origin.y_coordinate - x.destination.y_coordinate) ** 2, reverse=True)
    if activator == ODActivator.LARGEST_WEIGHT_WITH_TRANSFER:
        all_od_pairs = [pair for pair in all_od_pairs if pair.transfer_in_shortest_paths]
        return sorted(all_od_pairs, key=lambda x: x.get_total_passengers(), reverse=True)
    if activator == ODActivator.DIFF:
        return sorted(all_od_pairs, key=lambda x: x.diff_bounds_sp, reverse=True)
    if activator == ODActivator.POTENTIAL:
        return sorted(all_od_pairs, key=lambda x: x.get_weighted_diff_bounds_sp(), reverse=True)


def find_passenger_paths(od: OD, ean: Ean, parameters: ODActivatorParameters) -> None:
    processor = preprocessing.EanPreprocessor(ean, parameters)
    processor.compute_fixed_passenger_paths(ean, od)