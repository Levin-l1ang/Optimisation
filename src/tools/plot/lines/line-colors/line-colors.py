import logging
import sys
import numpy as np
import matplotlib.colors as mcolors
from matplotlib.pyplot import cm

from core.exceptions.config_exceptions import ConfigNoFileNameGivenException
from core.io.config import ConfigReader
from core.io.csv import CsvWriter
from core.io.lines import LineReader
from core.io.ptn import PTNReader

logger = logging.getLogger(__name__)

if __name__ == '__main__':
    logger.info("Begin reading configuration")
    if len(sys.argv) < 2:
        raise ConfigNoFileNameGivenException()
    config = ConfigReader.read(sys.argv[1])

    number_of_colors = config.getIntegerValue("lpool_opt_visualisation_number_colors_in_color_file")
    use_line_pool = config.getBooleanValue("lpool_opt_visualisation_create_color_file_for_line_pool")
    colormap= config.getStringValue("lpool_opt_visualisation_colormap_in_color_file")
    color_file_name = config.getStringValue("filename_line_colors")
    color_file_header = config.getStringValue("line_colors_header")

    logger.info("Finished reading configuration")

    logger.info("Begin reading input data")
    iteration_list = range(1, number_of_colors + 1)
    if use_line_pool:
        ptn = PTNReader.read()
        pool = LineReader.read(ptn, read_costs=False, read_frequencies=False)
        print(len(pool.getLines()))
        number_of_colors = len(pool.getLines())
        id_list = [x.getId() for x in pool.getLines()]
        iteration_list = id_list
    logger.info("Finish reading input data")


    logger.info("Begin computing colors")
    # Run your actual program here. You can also use functions defined in this file or in another file that you import
    color_dict = {}
    if colormap.lower() == "rainbow":
        color = iter(cm.rainbow(np.linspace(0, 1, number_of_colors)))
    elif colormap.lower() == "greys":
        color = iter(cm.Greys(np.linspace(0.1, 1, number_of_colors)))
    elif colormap.lower() == "gist_ncar":
        color = iter(cm.gist_ncar(np.linspace(0, 0.95, number_of_colors)))
    else:
        logger.warning("Colormap {} not supported, revert to rainbow.".format(colormap))
        color = iter(cm.rainbow(np.linspace(0, 1, number_of_colors)))

    for n in iteration_list:
        c = next(color)
        r, g, b = mcolors.to_rgb(c)
        color_dict[n] = f"({r:.2f},{g:.2f},{b:.2f})"
    logger.info("Finished computing colors")


    logger.info("Begin writing output data")
    color_file = open("Line-Colors.giv", 'a')
    color_file.write(f"#line-id; color\n")

    CsvWriter.writeListStatic(color_file_name, list(color_dict.items()),
                                  lambda item: [str(item[0]), item[1]],
                                  lambda item: item[0] ,color_file_header)

    logger.info("Finished writing output data")
