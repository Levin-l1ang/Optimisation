from core.io.csv import CsvWriter
from core.io.periodic_ean import PeriodicEANWriter
from core.model.graph import Graph
from core.model.periodic_ean import PeriodicEvent
from robust_activities.model.buffered_activity import BufferedPeriodicActivity


def write_buffered_activities(ean: Graph[PeriodicEvent, BufferedPeriodicActivity], activity_file_name: str,
                              weight_file_name: str) -> None:
    """
    Write the buffered activities to the given files
    :param ean: the ean to write the activities from
    :param activity_file_name: the file name to write the activities
    :param weight_file_name: the weight file name
    """
    PeriodicEANWriter.write(ean, write_events=False, activities_file_name=activity_file_name)
    CsvWriter.writeListStatic(weight_file_name, ean.getEdges(), BufferedPeriodicActivity.to_buffered_weights_csv,
                              BufferedPeriodicActivity.getId)
