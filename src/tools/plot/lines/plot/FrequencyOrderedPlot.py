import matplotlib as mpl
import itertools as it
import logging
import math
import numpy as np
mpl.use('Agg')


import sys
import matplotlib.pyplot as plt
import os.path
import networkx as nx

from matplotlib.pyplot import figure
from matplotlib import transforms
import matplotlib.patches as mpatches

from core.io.config import ConfigReader, Config
from core.exceptions.config_exceptions import ConfigNoFileNameGivenException
from core.exceptions.input_exceptions import InputFormatException
from core.model.lines import Line
from core.model.ptn import Stop, Link
from core.io.csv import CsvReader
from core.io.lines import LineReader
from core.io.ptn import PTNReader
from core.util.mm_utilities import add_modality_prefix
from typing import Dict, List

logger = logging.getLogger(__name__)


def plot(order_file, lines: dict[str, list[Line]], nodes: List[Stop], config: Config, lc: bool, use_frequencies: bool, merged_lines: dict[tuple[str, int], str] = {}):
    """
    Build and optimize the MIP model from Bast et al. (2019) to determine the (visually) optimal ordering of lines on edges.
    Also writes this order to filename_optimized_line_pool_order or filename_optimized_line_concept_order, depending on the parameter lc.
    :param lines: A mapping from modalities to either line pools or line concepts
    :type lines: dict[str, list[Line]]
    :param nodes: A list of nodes in the PTN
    :type nodes: List[Stop]
    :param config: The configuration used
    :type config: Config
    :param lc: True if line concept should be used instead of line pool
    :type lc: bool
    :param use_frequencies: True if line width should depend on frequency of the line
    :type use_frequencies: bool
    :param merged_lines: If some lines should be merged, the mapping of modality and line id to the name of the merged line
    :type merged_lines: dict[tuple[str, int], str]
    """
    colors_merged = {}
    modalities = lines.keys()
    line_colors = {mod: {} for mod in modalities}
    mod_colors = {}
    positions: Dict[(Link, Stop, Stop, Line), int] = {}
    widths = {mod: {} for mod in modalities}

    line_width = config.getDoubleValue("lpool_opt_visualisation_line_width")
    background = config.getBooleanValue("lpool_opt_visualisation_background")
    highlight = config.getBooleanValue("lpool_opt_visualisation_highlight_lines")
    base_color = "lightgray"

    def set_color(set, mod, id, i):
        if highlight:
            set[mod][id] = base_color
        elif i < 20:
            set[mod][id] = mpl.color_sequences['tab20'][i%20]
        elif i < 40:
            set[mod][id] = mpl.color_sequences['tab20b'][i%20]
        elif i < 60:
            set[mod][id] = mpl.color_sequences['tab20c'][i%20]
        else:
            set[mod][id] = mpl.color_sequences['tab20'][i%20]

    i = 0
    # Multimodal, modalities should each have one color
    if len(modalities) > 1 and config.getBooleanValue("lpool_opt_visualisation_same_color_modality"):
        mod_color_file = config.getStringValue("filename_modality_colors")
        if os.path.isfile(mod_color_file):
            def process_mod_colors(args, index):
                if len(args) != 2:
                    raise InputFormatException(mod_color_file, len(args), 2)
                color_read = str(args[1])
                if "(" in color_read:
                    color_read = color_read.replace("(", "")
                    color_read = color_read.replace(")", "")
                    colors_array_read = color_read.split(",")
                    color_read = [float(color_value) for color_value in colors_array_read]
                mod_colors[str(args[0])] = color_read
            CsvReader.readCsv(mod_color_file, process_mod_colors)
        for mod in modalities:
            if not (mod in mod_colors):
                for line in lines[mod]:
                    set_color(line_colors, mod, line.getId(), i)
                i += 1
            else:
                for line in lines[mod]:
                    line_colors[mod][line.getId()] = mod_colors[mod]
    # line colors should be taken from file
    elif config.getBooleanValue("lpool_opt_visualisation_use_color_file"):
        if len(modalities) > 1:
            logger.error("Using a line color file on a multimodal dataset is currently not supported, please set lpool_opt_visualisation_use_color_file to false")
        else:
            color_file = config.getStringValue("filename_line_colors")
            if os.path.isfile(color_file):
                def process_line_colors(args, index):
                    if len(args) != 2:
                        raise InputFormatException(color_file, len(args), 2)
                    for mod in modalities:
                        pass
                    color_read = str(args[1])
                    if "(" in color_read:
                        color_read = color_read.replace("(","")
                        color_read = color_read.replace(")","")
                        colors_array_read = color_read.split(",")
                        color_read = [float(color_value) for color_value in colors_array_read]
                    line_colors[mod][int(args[0])] = color_read
                CsvReader.readCsv(color_file, process_line_colors)
                for mod in modalities:
                    for line in lines[mod]:
                        id = line.getId()
                        if not (id in line_colors[mod]):
                            set_color(line_colors, mod, id, i)
                            i += 1
            else:
                logger.warning("lpool_opt_visualisation_use_color_file is set to true but no color file was provided, using default colors")
                for mod in modalities:
                    for line in lines[mod]:
                        id = line.getId()
                        set_color(line_colors, mod, id, i)
                        i += 1
    # default behavior, assign each line a color from the default palette
    else:
        for mod in modalities:
            for line in lines[mod]:
                id = line.getId()
                set_color(line_colors, mod, id, i)
                i += 1

    # the merged lines get the color of one of the lines in the set (somewhat randomly)
    for key, linename in merged_lines.items():
        if key[0] in line_colors and key[1] in line_colors[key[0]]:
            colors_merged[linename] = line_colors[key[0]][key[1]]
        else:
            continue
    for key, linename in merged_lines.items():
        if linename not in colors_merged:
            logger.warning(f"Color not found for merged line {linename} with key {key}, using default color")
            colors_merged[linename] = base_color

    # determine line widths in visualization
    freq={}
    for mod in modalities:
        freq[mod] = {}
        for line in lines[mod]:
            freq[mod][line.getId()] = line.getFrequency()
            if use_frequencies and lc:
                widths[mod][line.getId()] = line.getFrequency() * line_width
            else:
                widths[mod][line.getId()] = line_width

    def process_line_order(args: list[str], line_number):
        if len(args) != 5:
            raise InputFormatException(order_file, len(args), 5)
        node_id = int(args[0])
        node2_id = int(args[1])
        mod = args[2]
        line_id = int(args[3])
        pos = float(args[4])

        positions[(node_id, node2_id, mod, line_id)] = pos

    # read the optimal ordering from file
    CsvReader.readCsv(order_file, process_line_order)
    pos = {}

    # create a networkX graph of the network
    node_labels = {}
    G = nx.MultiDiGraph()
    added_merged_lines = []
    for node in nodes:
        G.add_node(node.getId(), id=node.getId())
        pos[node.getId()] = (node.getXCoordinate(), node.getYCoordinate())
        node_labels[node.getId()] = (node.getId())
    for (node1, node2, mod, line) in sorted(positions, key=positions.get):
        if (mod, line) in merged_lines:
            if (node1, node2, merged_lines[(mod, line)]) in added_merged_lines or (node2, node1, merged_lines[(mod, line)]) in added_merged_lines:
                continue
            if use_frequencies:
                total_width = sum([widths[key[0]][key[1]] for key, name in merged_lines.items() if merged_lines[(mod, line)] == name and ((node1, node2, key[0], key[1]) in positions or (node2, node1, key[0], key[1]) in positions)])
            else:
                total_width = line_width
            G.add_edge(node1, node2, key=line, color=colors_merged[merged_lines[(mod, line)]], width = total_width, line = line, mod = mod)
            added_merged_lines.append((node1, node2, merged_lines[(mod, line)]))
        else:
            G.add_edge(node1, node2, key=line, color=line_colors[mod][line], width = widths[mod][line], line = line, mod = mod)

    # determine the size of the figure
    xs = [coor[0] for coor in pos.values()]
    ys = [coor[1] for coor in pos.values()]

    x_range = max(xs) - min(xs)
    y_range = max(ys) - min(ys)

    xy = x_range / y_range

    scale = config.getDoubleValue("lpool_opt_visualisation_plot_scale")
    scale = scale * min(50, G.number_of_nodes()/5)

    fig_width = scale * xy
    fig_height = scale

    margin = config.getDoubleValue("lpool_opt_visualisation_plot_margins")

    fig = figure(figsize=(fig_width, fig_height), dpi=160, frameon=False)
    ax = plt.axes(frame_on = background)
    ax.margins(margin, margin)


    seen = {}
    labelled = []
    legend_labels = {}
    merged_lines_in_legend = set()  # Track which merged lines have been added to legend
    named_lines_in_legend = set()  # Track which named lines have been added to legend
    tot_width = {}
    width_so_far = {}
    line_distance = config.getDoubleValue("lpool_opt_visualisation_line_distance")

    # get total width of edges between each node pair
    for (u,v,k,w) in G.edges(data = 'width', keys=True):
        so_far = tot_width.get((u,v), 0)
        tot_width[(u,v)] = so_far + w

    # draw the lines:
    for (u,v,k, w) in G.edges(data = 'width', keys=True):
        n_parallel = G.number_of_edges(u,v)
        total = (tot_width[(u,v)] + (n_parallel - 1) * line_distance)
        linewidth = w
        start = - 0.5* (total) /72. - 0.5*line_distance/72.

        index = seen.get((u,v),0)
        prev = width_so_far.get((u,v), start)

        pixel_offset =  (linewidth) /72. # how far from each other are the lines
        pixel_offset +=  line_distance/72.
        dx = pos[v][1] - pos[u][1]
        dy = pos[v][0] - pos[u][0]
        norm = (dx**2 + dy**2)**0.5

        if norm == 0:
            norm = 1

        offset_x = 0
        offset_y = 0

        if n_parallel > 1:
            offset_x = dx / norm  * (  prev+ 0.5*pixel_offset)
            offset_y = - dy / norm  * (  prev + 0.5*pixel_offset)

        width_so_far[(u,v)] = prev + pixel_offset

        offset_trans = transforms.ScaledTranslation(offset_x, offset_y, fig.dpi_scale_trans)
        new_line_trans = ax.transData + offset_trans

        edge_x = (pos[u][0], pos[v][0])
        edge_y = (pos[u][1], pos[v][1])

        line, = ax.plot(edge_x, edge_y, color=G[u][v][k]['color'], linewidth = linewidth, transform = new_line_trans)

        if (G[u][v][k]['line'], G[u][v][k]['mod']) not in labelled:
            labelled.append((G[u][v][k]['line'], G[u][v][k]['mod']))
            if not (highlight and G[u][v][k]['color'] == base_color):
                legend_labels[line] = (G[u][v][k]['line'], G[u][v][k]['color'], G[u][v][k]['mod'])

        seen[(u,v)] = index + 1



    # drawing nodes:
    if config.getBooleanValue("lpool_opt_visualisation_scale_nodes"):
        min_node_size = config.getIntegerValue("lpool_opt_visualisation_min_node_size")
        node_scale = config.getDoubleValue("lpool_opt_visualisation_node_scaling")
        nodesizes = []
        for (node) in G.nodes():
            max_width = 0
            for (u,v) in G.out_edges(node):
                if (u,v) in tot_width and tot_width[(u,v)] > max_width: max_width = tot_width[(u,v)]
                if (v,u) in tot_width and tot_width[(v,u)] > max_width: max_width = tot_width[(v,u)]
            for (u,v) in G.in_edges(node):
                if (u,v) in tot_width and tot_width[(u,v)] > max_width: max_width = tot_width[(u,v)]
                if (v,u) in tot_width and tot_width[(v,u)] > max_width: max_width = tot_width[(v,u)]
            nodesizes.append(max(min_node_size, min_node_size * node_scale * max_width))

        nx.draw_networkx_nodes(G, pos, ax=ax, node_color='white', node_shape='s', edgecolors="black", node_size = nodesizes)
    else:
        def_node_size = config.getIntegerValue("lpool_opt_visualisation_def_node_size")
        nx.draw_networkx_nodes(G, pos, ax=ax, node_color='white', node_shape='s', edgecolors="black", node_size = def_node_size)

    if config.getBooleanValue("lpool_opt_visualisation_node_labels"):
        node_label_size = config.getIntegerValue("lpool_opt_visualisation_node_label_size")
        nx.draw_networkx_labels(G, pos, node_labels, font_size=node_label_size)

    # draw legend:
    if config.getBooleanValue("lpool_opt_visualisation_draw_legend"):
        sorted_legend = dict(sorted(legend_labels.items(), key=lambda kv: (kv[1][2], int(kv[1][0]), kv[0])))
        legend_keys = []
        in_legend = []
        names = {}

        if config.getBooleanValue("lpool_opt_visualisation_same_color_modality") and len(modalities)>1:
            for mod in mod_colors:
                legend_keys.append(mpatches.Patch(color = mod_colors[mod], label = f"{mod.capitalize()} Lines"))

        else:
            name_file = config.getStringValue("filename_line_names")
            if os.path.isfile(name_file):
                def process_line_names(args: list[str], index):
                    if len(args) != 2:
                        raise InputFormatException(name_file, len(args), 2)
                    if index != 0:
                        names[str(args[0])] = str(args[1])
                CsvReader.readCsv(name_file, process_line_names)

            for (lineid, color, mod) in sorted_legend.values():
                if not config.getBooleanValue("lpool_opt_visualisation_use_name_file"):
                    if (lineid, mod) not in in_legend:
                        if (mod, lineid) in merged_lines:
                            if merged_lines[(mod, lineid)] not in merged_lines_in_legend:
                                if display_frequencies:
                                    merged_freq = sum([freq[key[0]][key[1]] for key, name in merged_lines.items() if name == merged_lines[(mod, lineid)] and key[0] in freq and key[1] in freq[key[0]]])
                                    legend_keys.append(mpatches.Patch(color = color, label = f"{merged_lines[(mod,lineid)]}, f={merged_freq}"))
                                    in_legend.append(merged_lines[(mod,lineid)])
                                    merged_lines_in_legend.add(merged_lines[(mod, lineid)])
                                else:
                                    legend_keys.append(mpatches.Patch(color = color, label = f"{merged_lines[(mod,lineid)]}"))
                                    in_legend.append(merged_lines[(mod,lineid)])
                        elif len(modalities)>1:
                            legend_keys.append(mpatches.Patch(color = color, label = f"{mod.capitalize()} Line {lineid}"))
                        else:
                            if display_frequencies:
                                legend_keys.append(mpatches.Patch(color = color, label = f"Line {lineid}, f={freq[mod][lineid]}"))
                            else:
                                legend_keys.append(mpatches.Patch(color = color, label = f"Line {lineid}"))
                        in_legend.append((lineid, mod))
                elif names:
                    short_id = str(lineid)
                    if (short_id, mod) not in in_legend:
                        if names.get(short_id, short_id) in named_lines_in_legend:
                            continue
                        if len(modalities)>1:
                            if display_frequencies:
                                legend_keys.append(mpatches.Patch(color = color, label = f"{mod.capitalize()} Line {names.get(short_id, short_id)}, f={freq[mod][lineid]}"))
                            else:
                                legend_keys.append(mpatches.Patch(color = color, label = f"{mod.capitalize()} Line {names.get(short_id, short_id)}"))
                        else:
                            target_name = names.get(short_id, short_id)
                            merged_name_freq = 0
                            for mod_key, line_dict in freq.items():
                                for lid, fval in line_dict.items():
                                    if names.get(str(lid), str(lid)) == target_name:
                                        merged_name_freq += fval
                            if display_frequencies:
                                legend_keys.append(mpatches.Patch(color = color, label = f"{target_name}, f={merged_name_freq}"))
                            else:
                                legend_keys.append(mpatches.Patch(color = color, label = f"{target_name}"))
                        in_legend.append((short_id, mod))
                        named_lines_in_legend.add(names.get(short_id, short_id))
                else:
                    logger.warning("Config key \"lpool_opt_visualisation_use_name_file\" is set to true but no name file was provided")
                    logger.info("Drawing plot without a legend because of missing name file")
                    break

        cols = 1
        if len(lines) > 40:
            cols = 2

        legend_loc = config.getStringValue("lpool_opt_visualisation_legend_location")
        legend_scale = config.getDoubleValue("lpool_opt_visualisation_legend_scaling")

        ax.legend(handles=legend_keys, loc = legend_loc, prop={'size': max(7, legend_scale * scale)}, ncols = cols)

    if lc:
        plt.savefig(config.getStringValue("filename_optimized_line_concept_graph"), format = "PNG")
    else:
        plt.savefig(config.getStringValue("filename_optimized_line_pool_graph"), format = "PNG")



if __name__ == '__main__':
    if len(sys.argv) < 4:
        raise ConfigNoFileNameGivenException
    logger.info("Start reading configuration")
    config = ConfigReader.read(sys.argv[1])
    lc = bool(sys.argv[2] == "true")
    use_frequencies = bool(sys.argv[3] == "true")
    logger.info("Begin reading input data")

    nodes = []
    lines = {}
    if "modalities_all" in config.data:
        all_modalities = config.getStringListValue("modalities_all")
        modalities = []
        for modality in all_modalities:
            if config.getStringValue(add_modality_prefix(modality, "modality_category")) == "line-based":
                modalities.append(modality)

        for modality in modalities:
            ptn = PTNReader.read(read_loads=False, directed=False, modality=modality)
            nodelist = ptn.getNodes()
            nodes += nodelist
            line_pool = LineReader.read(ptn, read_costs=False, read_frequencies=lc, create_directed_lines= not config.getBooleanValue("ptn_is_undirected"), modality=modality)
            if lc:
                lines_mode = line_pool.getLineConcept()
            else:
                lines_mode = line_pool.getLines()
            lines[modality] = lines_mode
    else:
        ptn = PTNReader.read(read_loads=False, directed=False)
        nodelist = ptn.getNodes()
        nodes += nodelist
        line_pool = LineReader.read(ptn, read_costs=False, read_frequencies=lc, create_directed_lines= not config.getBooleanValue("ptn_is_undirected"))
        if lc:
            lines_mode = line_pool.getLineConcept()
        else:
            lines_mode = line_pool.getLines()
        lines[""] = lines_mode

    display_frequencies = config.getBooleanValue("lpool_opt_visualisation_display_frequency")
    # Do not display frequencies for line pools
    if not lc:
        display_frequencies = False
    merged_lines = {}
    def process_merged_lines(args: list[str], line_number: int):
        if len(args) != 3:
            raise InputFormatException(merged_lines_file, len(args), 3)
        mod = args[0]
        line_id = int(args[1])
        name = args[2]
        merged_lines[(mod, line_id)] = name

    if config.getBooleanValue("lpool_opt_visualisation_merge_lines"):
        merged_lines_file = config.getStringValue("filename_merged_lines")
        CsvReader.readCsv(merged_lines_file, process_merged_lines)

    logger.info("Finished reading input data")

    logger.info("Begin drawing")
    order_filename = config.getStringValue("filename_optimized_line_concept_order") if lc else config.getStringValue("filename_optimized_line_pool_order")
    plot(order_filename, lines, nodes, config, lc, use_frequencies, merged_lines)
    logger.info("Finished drawing")


