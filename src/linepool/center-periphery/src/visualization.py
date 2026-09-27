import logging
import matplotlib.pyplot as plt 
from InstanceClass import Instance
from typing import List
from core.model.ptn import Stop

logger = logging.getLogger(__name__)

def create_fig_ax(instance: Instance):

    logger.debug("Entered function create_fig_ax")
    logger.debug("create_fig_ax: Got edges and nodes")
    logger.debug("create_fig_ax: Prepare plot of ptn")
    logger.debug("create_fig_ax: Calculating dimensions of x- and y-axis")

    min_edge_length = min([edge.getLength() for edge in instance.Edges])
    stop_radius = min_edge_length/4

    max_name_length = max([len(node.getShortName()) for node in instance.Nodes])
    fontsize = stop_radius / max_name_length * 120

    max_x_coord = max([node.getXCoordinate() for node in instance.Nodes])
    max_y_coord = max([node.getYCoordinate() for node in instance.Nodes])
    min_x_coord = min([node.getXCoordinate() for node in instance.Nodes])
    min_y_coord = min([node.getYCoordinate() for node in instance.Nodes])
    x_diff = max_x_coord - min_x_coord
    y_diff = max_y_coord - min_y_coord

    fig, ax = plt.subplots(figsize = (1.2 * x_diff, 1.2 * y_diff), dpi = 600)

    fig.suptitle(f"center_radius: {instance.center_radius} (red), weighted_mean_dist: {instance.weighted_mean_dist} (blue)")

    ax.axis("equal")

    ax.set_xlim(min_x_coord - x_diff * 0.1, max_x_coord + x_diff * 0.1)
    ax.set_ylim(min_y_coord - y_diff * 0.1, max_y_coord + y_diff * 0.1)

    ax.get_xaxis().set_visible(False)
    ax.get_yaxis().set_visible(False)
    ax.get_xaxis().set_ticks([])
    ax.get_yaxis().set_ticks([])

    logger.debug("Exiting create_fig_ax")

    return fig, ax, stop_radius, fontsize

def draw_ptn(instance: Instance, path: str = "") -> None:

    logger.debug("Entered function draw_ptn")

    logger.debug("draw_ptn: Calling create_fig_ax")
    fig, ax, stop_radius, fontsize = create_fig_ax(instance)
    fontsize = max(1,fontsize)

    logger.debug("draw_ptn: Drawing edges")
    for edge in instance.Edges:
        plt.plot(
            [edge.getLeftNode().getXCoordinate(), edge.getRightNode().getXCoordinate()],
            [edge.getLeftNode().getYCoordinate(), edge.getRightNode().getYCoordinate()],
            "black",
            zorder = 0
        )
        # angle = np.arcsin(
        #     (edge.getLeftNode().getXCoordniate() - edge.getRightNode().getXCoordniate())/np.sqrt(
        #         (edge.getLeftNode().getXCoordniate() - edge.getRightNode().getXCoordniate())**2 + (edge.getLeftNode().getYCoordinate() - edge.getRightNode().getYCoordinate())**2
        #     )
        # ) * 360 / (2 * np.pi) - 90
        # if abs(angle) > 90:
        #     angle = angle + 180
        # ax.text(
        #     edge.getLeftNode().getXCoordniate() + abs((edge.getLeftNode().getXCoordniate() - edge.getRightNode().getXCoordniate())/2),
        #     edge.getLeftNode().getYCoordinate() + abs((edge.getLeftNode().getYCoordinate() - edge.getRightNode().getYCoordinate())/2),
        #     f"{edge.length}",
        #     fontsize = fontsize,
        #     horizontalalignment = "center",
        #     rotation = angle,
        #     rotation_mode = "anchor",
        #     zorder = 1
        # )

    logger.debug("draw_ptn: Finished drawing edges")

    logger.debug("draw_ptn: Drawing nodes")
    stop_radius_circle = stop_radius
    for node in instance.Nodes:
        if node in instance.Centers:
            stop_colour = "red"
            stop_radius_circle = 2 * stop_radius
            # if instance.center_radius > 0:
            #     circle = plt.Circle(
            #         (node.getXCoordniate(), node.getYCoordinate()),
            #         instance.center_radius, 
            #         edgecolor = stop_colour,
            #         facecolor = "none",
            #         zorder = 1,
            #     )
            #     ax.add_patch(circle)
            # if instance.weighted_mean_dist > 0:
            #     circle = plt.Circle(
            #         (node.getXCoordniate(), node.getYCoordinate()),
            #         instance.weighted_mean_dist, 
            #         edgecolor = "blue",
            #         facecolor = "none",
            #         zorder = 1,
            #     )
            #     ax.add_patch(circle)
        elif node in instance.Periphery:
            stop_colour = "blue"
        else:
            stop_colour = "black"
        circle = plt.Circle(
            (node.getXCoordinate(), node.getYCoordinate()),
            stop_radius_circle, 
            edgecolor = stop_colour, 
            facecolor = stop_colour, 
            zorder = 2,
        )
        ax.add_patch(circle)
        # ax.text(
        #     node.getXCoordniate(), node.getYCoordinate(),
        #     f"{node.getShortName()}\n{int(instance.instance.w[node.getId() - 1])}",
        #     fontsize = fontsize,
        #     color = "white",
        #     horizontalalignment = "center",
        #     verticalalignment = "center"
        # )
        stop_radius_circle = stop_radius

    logger.debug("draw_ptn: Finished drawing nodes")

    logging.basicConfig(level = logging.debug)

    logger.debug("draw_ptn: Showing plot")
    plt.draw()

    if path:
        logger.debug(f"draw_ptn: Saving plot to {path}")
        plt.savefig(path, bbox_inches = 0)

    logger.debug("Exiting function ptn_draw")

def draw_circle(instance: Instance, center: List[Stop] = [], path: str = "") -> None:

    logger.debug("Entered draw_circle")

    logger.debug("draw_circle: Calling create_fig_ax")
    fig, ax, stop_radius, fontsize = create_fig_ax(instance)

    logger.debug("draw_circle: Drawing edges")

    for edge in instance.Edges:
        plt.plot(
            [edge.getLeftNode().getXCoordinate(), edge.getRightNode().getXCoordinate()],
            [edge.getLeftNode().getYCoordinate(), edge.getRightNode().getYCoordinate()],
            "black",
            zorder = 0
        )

    logger.debug("draw_circle: Finished drawing edges")

    centerId = center.getId() - 1

    logger.debug("draw_circle: Drawing nodes")
    stop_radius_circle = stop_radius
    for node in instance.Nodes:
        if node == center:
            stop_edge_colour = "red"
            stop_face_colour = "red"
            stop_radius_circle = 2 * stop_radius
            if instance.center_radius > 0:
                circle = plt.Circle(
                    (node.getXCoordinate(), node.getYCoordinate()),
                    instance.center_radius, 
                    edgecolor = "red",
                    facecolor = "none",
                    zorder = 1,
                )
                ax.add_patch(circle)
            if instance.weighted_mean_dist > 0:
                circle = plt.Circle(
                    (node.getXCoordinate(), node.getYCoordinate()),
                    instance.weighted_mean_dist, 
                    edgecolor = "blue",
                    facecolor = "none",
                    zorder = 1,
                )
                ax.add_patch(circle)
        elif instance.distances[centerId, node.getId() - 1] <= instance.center_radius:
            stop_edge_colour = "red"
            stop_face_colour = "red"
        elif instance.distances[centerId, node.getId() - 1] > instance.weighted_mean_dist:
            stop_edge_colour = "blue"
            stop_face_colour = "blue"
        else:
            stop_edge_colour = "black"
            stop_face_colour = "black"
        circle = plt.Circle(
            (node.getXCoordinate(), node.getYCoordinate()),
            stop_radius_circle, 
            edgecolor = stop_edge_colour, 
            facecolor = stop_face_colour, 
            zorder = 2,
        )
        ax.add_patch(circle)
        ax.text(
            node.getXCoordinate(), node.getYCoordinate(),
            f"{node.getShortName()}\n{int(instance.interactions[node.getId() - 1])}",
            fontsize = fontsize,
            color = "white",
            horizontalalignment = "center",
            verticalalignment = "center"
        )
        stop_radius_circle = stop_radius

    logger.debug("draw_circle: Finished drawing nodes")

    logging.basicConfig(level = logging.debug)

    logger.debug("draw_circle: Showing plot")
    plt.draw()

    if path:
        logger.debug(f"draw_circle: Saving plot to {path}")
        plt.savefig(path, bbox_inches = 0)

    logger.debug("Exiting function draw_circle")