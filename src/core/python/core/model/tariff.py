from typing import List, Set, Dict, Tuple

from core.model.ptn import Stop
import logging


class Zone:

    def __init__(self, zone_id: int, stops: Set[Stop]):
        self._zone_id = zone_id
        self._stops = stops

    def getStops(self) -> Set[Stop]:
        return self._stops

    def getZoneId(self) -> int:
        return self._zone_id

    def addStop(self, stop: Stop) -> None:
        self._stops.add(stop)

    def containsStop(self, stop: Stop) -> bool:
        return stop in self._stops

    def removeStop(self, stop: Stop):
        self._stops.remove(stop)

    def __str__(self) -> str:
        return "Zone " + str(self._zone_id) + ": " + ",".join([str(s.getId()) for s in self._stops])

    def __eq__(self, o: object) -> bool:
        if not isinstance(o, Zone):
            return False
        return o.getZoneId() == self.getZoneId() and o.getStops() == self.getStops()

    def __hash__(self):
        return self._zone_id

    def __ne__(self, o: object) -> bool:
        return not self.__eq__(o)


class PriceMatrix:

    def __init__(self):
        self.matrix: Dict[int, Dict[int, float]] = {}

    def setValue(self, origin: int, destination: int, new_value: float) -> None:
        self.matrix.setdefault(origin, {})[destination] = new_value

    def getValue(self, origin: int, destination: int) -> float:
        if origin in self.matrix and destination in self.matrix[origin]:
            return self.matrix[origin][destination]
        raise KeyError

    def getAllEntries(self) -> List[Tuple[int, int, float]]:
        all_entries = []
        for origin_id in self.matrix.keys():
            for destination_id, price in self.matrix[origin_id].items():
                all_entries.append((origin_id, destination_id, price))
        return all_entries

    def hasNonDiagonalZeros(self) -> bool:
        """
        Returns False, if all non-diagonal elements have values not equal to zero, True otherwise
        """
        for origin_id in self.matrix.keys():
            for destination_id, price in self.matrix[origin_id].items():
                if origin_id != destination_id and price == 0:
                    return True
        return False


class ZonePrices:

    logger = logging.getLogger(__name__)

    def __init__(self, prices: List[float] = []):

        # # trim price list backwards
        # if len(prices)>1:
        #     last_price = prices[-1]
        #     idx_to_check = len(prices) - 2
        #     while prices[idx_to_check] == last_price:
        #         prices = prices[:idx_to_check+1]
        #         idx_to_check -= 1
        #         if idx_to_check < 0:
        #             break

        self.prices = {}
        i=1
        for price in prices:
            self.prices[i] = price
            i += 1
        self.max_zones = i-1
        self.flag_for_warning = True

    def getPrice(self, nb_traversed_zones: int) -> float:
        """
        get the price for travelling the specified number of zones. Note that the counting method has to be specified 
        separately.
        """
        if nb_traversed_zones > self.max_zones:
            if self.flag_for_warning:
                self.logger.warning("Access price for more zones than specified in Zone Price file. Assume last price constantly")
                self.flag_for_warning = False
            return self.prices[self.max_zones]
        elif nb_traversed_zones > 0:
            return self.prices[nb_traversed_zones]
        else:
            raise KeyError

    def setPrice(self, nb_traversed_zones: int, price: float) -> None:
        """
        set the price for travelling the specified number of zones.
        """
        if nb_traversed_zones in self.prices:
            self.prices[nb_traversed_zones] = price
        elif nb_traversed_zones > 0:
            if self.max_zones == 0:
                self.max_zones = 1
                self.prices[1] = price
            else:
                for i in range(self.max_zones+1, nb_traversed_zones+1):
                    self.prices[i] = price
                self.max_zones = nb_traversed_zones
        else:
            raise KeyError

    def getMaxZones(self) -> int:
        """
        returns the maximal number of zones for which a price is explicitly specified.
        """
        return self.max_zones

    def getPriceList(self) -> List[float]:
        """
        returns the zone prices as list. Note that index i stores the price for i+1 zones
        """
        return [self.prices[key] for key in self.prices]