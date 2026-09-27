import logging

import sys
import subprocess
import time

from core.io.config import ConfigReader

from core.io.periodic_ean import PeriodicEANReader, PeriodicEANWriter

from instanceModifier import InstanceModifier
from helper import Helper, Parameters

logger = logging.getLogger(__name__)


if __name__ == '__main__':
    logger.info("Begin reading configuration")
    if len(sys.argv) < 2:
        logger.fatal("No config file name given to read!")
        sys.exit(1)
    # Config
    config = ConfigReader.read(sys.argv[1])
    parameters = Parameters(config=config)
    logger.info("Finished reading configuration")

    ''' preprocessing '''
    logger.info("Phase I - Preprocessing: Start")
    logger.info("Begin reading input data.")
    original_ean, _ = PeriodicEANReader.read(read_events=True,
                                             read_activities=True,
                                             read_timetable=False)
    logger.info("Finished reading input data.")

    # save original ean files
    Helper.save_original_ean_files(parameters=parameters)

    # creating extended ean
    extended_ean = InstanceModifier.create_extended_ean(original_ean, parameters)
    Helper.check_timetable_feasibility(ean=extended_ean, parameters=parameters)

    logger.info("Begin writing output.")
    PeriodicEANWriter.write(ean=extended_ean,
                            write_events=True,
                            write_activities=True,
                            write_timetable=True)
    logger.info("Finished writing output.")

    # add chosen tim_model at EOF of Private-Config, such that make tim-timetable can run with this
    # algorithm for phase I
    Helper.add_phase_one_model_to_private_config(parameters=parameters)
    logger.info("Phase I - Preprocessing: End")

    ''' phase I'''
    logger.info("Phase I: Start")
    start_ptt1 = InstanceModifier.calculate_ppt1(extended_ean, parameters)
    logger.info("Start ptt1 value: {}".format(start_ptt1))

    if start_ptt1 > 0:
        start_time = time.time()
        try:
            subprocess.run(['make', 'tim-timetable'])
            # TODO: Maybe do some piping to figure to skip the process if not ptt1 not zero
            end_time = time.time()
            logger.info("Time for Phase I (in sec): {}".format(round(end_time - start_time, 4)))
        except subprocess.SubprocessError:
            end_time = time.time()
            logger.info("Time for Phase I (in sec): {}".format(round(end_time - start_time, 4)))
            logger.error("SubprocessError during Phase I.")
            Helper.remove_last_line_from_private_config(parameters=parameters)
            Helper.restore_original_ean_files(parameters=parameters)
            sys.exit(1)
        except KeyboardInterrupt:
            end_time = time.time()
            logger.info("Time for Phase I (in sec): {}".format(round(end_time - start_time, 4)))
            logger.error("KeyboardInterrupt during Phase I.")
            Helper.remove_last_line_from_private_config(parameters=parameters)
            Helper.restore_original_ean_files(parameters=parameters)
            sys.exit(1)

    else:
        logger.info("Ptt1 of extended EAN already 0. Skip Phase I Optimization.")
    logger.info("Phase I: End")

    ''' postprocessing '''
    logger.info("Phase I - Postprocessing: Start")
    # Remove last line, i.e. the tim_model for phase I
    Helper.remove_last_line_from_private_config(parameters=parameters)

    # save extended ean if true
    if parameters.tim_phase_one_save_extended_ean:
        logger.info("Save extended EAN.")
        Helper.save_extended_ean(parameters=parameters)

    logger.info("Begin reading timetable.")
    PeriodicEANReader.read(read_events=False, read_activities=False, read_timetable=True,
                           periodic_ean=extended_ean)
    logger.info("Finished reading timetable.")

    # check ptt1 value
    ptt1 = InstanceModifier.calculate_ppt1(ean=extended_ean, parameters=parameters)
    logger.info("Ptt1 value of extended instance: {}".format(ptt1))

    if ptt1 != 0:
        logger.error("Ptt1 of extended ean > 0. No feasible timetable for the original instance exists.")
        Helper.remove_timetable_file(parameters=parameters)
        Helper.restore_original_ean_files(parameters=parameters)
        sys.exit(1)

    logger.info("Ptt1 of extended ean = 0. A feasible timetable for the original instance exists.")

    # calculate original timetable
    original_timetable = InstanceModifier.calculate_original_timetable(extended_ean=extended_ean)

    logger.info("Begin writing output.")
    PeriodicEANWriter.write(ean=original_timetable,
                            write_events=False,
                            write_activities=False,
                            write_timetable=True)
    logger.info("Finished writing output.")

    # restore original ean files
    Helper.restore_original_ean_files(parameters=parameters)
    logger.info("Phase I - Postprocessing: End")
