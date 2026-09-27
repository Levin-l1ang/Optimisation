import textwrap
# This file mainly contains functions that return files in form of formatted strings
# that should be included before executing any LinTim algorithms, namely the Config files

# The formatting within the functions may seem of, but is
def get_config_str(folder_name):
    # TODO: Adjust parameter based on ean / dataset files
    config_strings = ["""\
                    setting-name; setting-value
                    include; "../../Global-Config.cnf"
                    # ===========================================================================
                    # === LOCAL_ONLY from Global-Config.cnf =====================================
                    # ===========================================================================
                    ptn_name; {}
                    ptn_is_undirected; False
                    # period length in time units
                    period_length; 60
                    # conversion factor to draw the ptn
                    ptn_draw_conversion_factor; 1
                    # the number of time units per minute
                    time_units_per_minute; 1
                    # the time to wait at each stop
                    ptn_stop_waiting_time; 1
                    # conversion factor to convert length in Edge.giv to kilometers
                    gen_conversion_length; 1
                    # conversion factor to convert the distance between two stations from coordinates to meters
                    gen_conversion_coordinates; 1
                    # lower bound on wait activities in the ean
                    ean_default_minimal_waiting_time; 1
                    # upper bound on wait acitivies in the ean
                    ean_default_maximal_waiting_time; 3
                    # lower bound on change activities in the ean
                    ean_default_minimal_change_time; 3
                    # upper bound on change activities in the ean
                    ean_default_maximal_change_time; 62
                    # the penalty for each change in the ean
                    ean_change_penalty; 5
                    # maximale number of passengers per vehicle
                    gen_passengers_per_vehicle; 10
                    """.format(folder_name), 
                    """\
                    # ===========================================================================
                    # === Network Specific Settings =============================================
                    # ===========================================================================
                    # Enter your config parameter here
                    # ===========================================================================
                    # === State / Experiments / Automatization ==================================
                    # ===========================================================================
                    include_if_exists; "State-Config.cnf"
                    include_if_exists; "Private-Config.cnf"
                    include_if_exists; "After-Config.cnf"
                    """]
    # the following is necessary to get rid of the indenting of the text that occurs when aligning the
    # long string above with the code indent
    config_strings = [textwrap.dedent(config_string) for config_string in config_strings] #textwrap.fill(textwrap.dedent(config_string))
    return config_strings

def get_private_config_str():
    # TODO: Add parameter for timetabling etc.  and maybe change parameter names
    private_config_string ="""\
                            console_log_level; DEBUG
                            nature_sd_import_write_ptn; True
                            nature_sd_import_write_line_concept; False
                            nature_sd_import_write_timetable; False
                            """
    # the following is necessary to get rid of the indenting of the text that occurs when aligning the
    # long string above with the code indent
    private_config_string = textwrap.dedent(private_config_string)
    return private_config_string


def get_makefile_str():
    makefile_str = """
    # First, include the base Makefile in the datasets folder. This Makefile includes all commands that hold for all datasets.
    # DO NOT EDIT THE FOLLOWING LINE
    include ../Base-Makefile

    # Add custom commands under this line
    """
    # remove newline at the beginning
    makefile_str = makefile_str[1:]
    makefile_str = textwrap.dedent(makefile_str)
    return makefile_str

def get_folder_makefile_str(foldername):
    if foldername == "line-planning":
        makefile_str = """
        .PHONY: clean

        clean:
            rm -vf *.lin
        """
    elif foldername == "delay-management":
        makefile_str = """
        clean:
	    rm -vf *Activities-expanded.giv *Events-expanded.giv *Timetable-expanded.tim *Delays-Events.giv *Delays-Activities.giv *Timetable-disposition.tim *Trips.giv *end-events-of-trips.giv *delayedstops.txt*
        """
    elif foldername == "graphics":
        makefile_str = """
        .PHONY: clean

        clean:
            rm -vf *.dot *.png delay-graph.* *.html *.ps *.gif
            rm -rvf delay_animation
        """
    elif foldername == "tariff":
        makefile_str = """
        .PHONY: clean

        clean:
            rm -vf *.taf
        """
    elif foldername == "timetabling":
        makefile_str = """
        .PHONY: clean

        clean:
            rm -vf *Events.giv *Activities.giv *Events-periodic.giv *Activities-periodic.giv *Timetable-periodic.tim
        """
    elif foldername == "vehicle-scheduling":
        makefile_str = """
        .PHONY: clean

        clean:
            rm -vf *.vs
        """
    # remove newline at the beginning
    makefile_str = makefile_str[1:]
    makefile_str = textwrap.dedent(makefile_str)
    return makefile_str