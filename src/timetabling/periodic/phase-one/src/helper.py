import sys
import logging

import os
import shutil
from datetime import datetime

from core.util.config import Config
from core.model.graph import Graph
from core.model.periodic_ean import PeriodicEvent, PeriodicActivity

logger = logging.getLogger(__name__)

# TODO: Problem remove last line delets \n? After running again, a error appears because write(_,"a") does not append
# the tim model in a new line
# workaround: add \n before writing tim_model. So there is a line between orginal and appended line


class Parameters:
    def __init__(self, config: Config):
        self.config = config
        self.ptn_name = config.getStringValue("ptn_name")
        self.event_file_name = config.getStringValue("default_events_periodic_file")
        self.activity_file_name = config.getStringValue("default_activities_periodic_file")
        self.timetable_file_name = config.getStringValue("default_timetable_periodic_file")
        self.private_config_file_name = config.getStringValue("filename_private_config")
        self.period_length = config.getIntegerValueStatic("period_length")
        # phase I parameter
        self.tim_phase_one_model = config.getStringValue("tim_phase_one_model")
        self.tim_phase_one_extend_ean_model = config.getStringValue("tim_phase_one_extend_ean_model")
        self.tim_phase_one_save_extended_ean = config.getBooleanValue("tim_phase_one_save_extended_ean")
        self.tim_phase_one_save_extended_ean_dir = config.getStringValue("tim_phase_one_save_extended_ean_dir")


class Helper:
    @staticmethod
    def add_phase_one_model_to_private_config(parameters: Parameters):
        logging.debug("Add tim_phase_one_model as tim_model to Private-Config.cnf")

        # TODO question: what happens if there is no Private-Config file???
        with open(parameters.private_config_file_name, 'a') as file:
            file.write('\ntim_model; {}\n'.format(parameters.tim_phase_one_model))

    @staticmethod
    def remove_last_line_from_private_config(parameters: Parameters):
        logging.debug("Remove last line from Private-Config.cnf. "
                      "The removed line should be the tim_model for phase one.")

        # function found here: https://stackoverflow.com/a/10289740
        with open(parameters.private_config_file_name, "r+") as file:
            # Move the pointer (similar to a cursor in a text editor) to the end of the file
            file.seek(0, os.SEEK_END)
            # This code means the following code skips the very last character in the file -
            # i.e. in the case the last line is null we delete the last line
            # and the penultimate one
            pos = file.tell() - 1
            # Read each character in the file one at a time from the penultimate
            # character going backwards, searching for a newline character
            # If we find a new line, exit the search
            while pos > 0 and file.read(1) != "\n":
                pos -= 1
                file.seek(pos, os.SEEK_SET)
            # So long as we're not at the start of the file, delete all the characters ahead
            # of this position
            if pos > 0:
                file.seek(pos, os.SEEK_SET)
                file.truncate()

    @staticmethod
    def save_original_ean_files(parameters: Parameters):
        logging.debug("Run save_original_ean_files(...)")
        shutil.copy(parameters.event_file_name, "timetabling/Original-events-periodic.giv")
        shutil.copy(parameters.activity_file_name, "timetabling/Original-activities-periodic.giv")

    @staticmethod
    def remove_timetable_file(parameters: Parameters):
        logging.debug("Remove timetable file.")
        if os.path.exists(parameters.timetable_file_name):
            os.remove(parameters.timetable_file_name)

    @staticmethod
    def restore_original_ean_files(parameters: Parameters):
        logging.debug("run restore_original_ean_files(...)")
        shutil.move("timetabling/Original-events-periodic.giv", parameters.event_file_name)
        shutil.move("timetabling/Original-activities-periodic.giv", parameters.activity_file_name)

    @staticmethod
    def save_extended_ean(parameters: Parameters):
        logging.debug("Run save_extended_ean(...)")
        extended_ean_dir = parameters.tim_phase_one_save_extended_ean_dir

        if extended_ean_dir == "default":
            dt_string = datetime.now().strftime("%Y-%m-%d_%H-%M-%S")
            extended_ean_dir = "timetabling/{}_phase-one_extended-ean".format(dt_string)

        os.makedirs(parameters.tim_phase_one_save_extended_ean_dir, exist_ok=True)
        shutil.copy(parameters.event_file_name, extended_ean_dir + "/Events-periodic.giv")
        shutil.copy(parameters.activity_file_name, extended_ean_dir + "/Activities-periodic.giv")
        shutil.copy(parameters.timetable_file_name, extended_ean_dir + "/Timetable-periodic.giv")

    @staticmethod
    def check_timetable_feasibility(ean: Graph[PeriodicEvent, PeriodicActivity], parameters: Parameters):
        count = 0
        feasible = True

        for e in ean.getEdges():
            u = e.getLeftNode()
            v = e.getRightNode()

            tmp = (ean.getNode(v.getId()).getTime() - ean.getNode(u.getId()).getTime() - e.getLowerBound()) % \
                parameters.period_length

            if tmp > e.getUpperBound() - e.getLowerBound():
                count += 1
                feasible = False
                print("{}: ({},{}), {}".format(e.getId(), u.getId(), v.getId(), e.getType()))
                print("->", (v.getTime() - u.getTime()) % parameters.period_length, e.getLowerBound(),
                      e.getUpperBound())

        if feasible:
            logging.info("Timetable is feasible.")
        else:
            logging.error("Timetable is not feasible ({} edges are problematic)".format(count))
            sys.exit(1)
