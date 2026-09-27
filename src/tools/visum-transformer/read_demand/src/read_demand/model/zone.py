class Zone:
    def __init__(self, zone_id: int, x_coordinate: float, y_coordinate: float):
        self.zone_id = zone_id
        self.x_coordinate = x_coordinate
        self.y_coordinate = y_coordinate


class ZonePath:
    def __init__(self, link_id: int, from_zone_id: int, to_stop_id: int, length: float, duration: int):
        self.link_id = link_id
        self.from_zone_id = from_zone_id
        self.to_stop_id = to_stop_id
        self.length = length
        self.duration = duration