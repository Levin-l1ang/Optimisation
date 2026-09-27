import errno
import filecmp
import sys
import os
import shutil

if __name__ == '__main__':
    lintim_folder = os.path.dirname(os.path.realpath(__file__)) + "/../../.."

    # instance from stdin
    if len(sys.argv) < 2:
        print("No instance name given.")
        sys.exit(1)

    # setting variables
    instance = sys.argv[1]
    file_name = instance + ".txt"
    dataset_folder = "{}/datasets/{}".format(lintim_folder, instance)
    activities_file = "{}/timetabling/Activities-periodic.giv".format(dataset_folder)
    events_file = "{}/timetabling/Events-periodic.giv".format(dataset_folder)

    events = []
    activities = []

    # check if it already exists
    if os.path.isdir(dataset_folder):
        print(instance + " already exists in LinTim.")
        sys.exit(0)
    else:
        print("Creating LinTim dataset for " + instance)

    # create lintim dataset folder
    template_dir_name = os.path.join(lintim_folder, "datasets", "template")
    try:
        shutil.copytree(src=template_dir_name, dst=dataset_folder)
    except OSError as e:
        # Workaround for WSL error, see https://bugs.python.org/issue38633
        # Check whether the files where copied
        cmp = filecmp.dircmp(template_dir_name, dataset_folder)
        if len(cmp.left_only) > 0 or len(cmp.right_only) > 0 or len(cmp.funny_files) > 0 or len(cmp.diff_files) > 0:
            # Raise the original error, there were problems during the copy
            raise


    # read instance file
    with open(file_name, "r") as csv_file:
        for line in csv_file.readlines():
            # skip empty lines or comments
            if line == "" or line == "\n" or line[0] == '#':
                continue

            split_line = line.rstrip().split(";")
            cur_id = int(split_line[0])
            cur_from = int(split_line[1])
            cur_to = int(split_line[2])
            cur_lb = int(split_line[3])
            cur_ub = int(split_line[4])
            cur_weight = int(split_line[5])

            if cur_from not in events:
                events.append(cur_from)
            if cur_to not in events:
                events.append(cur_to)

            activities.append([cur_id, cur_from, cur_to, cur_lb, cur_ub, cur_weight])

    events.sort()

    # write event file
    with open(events_file, "w") as file:
        file.write("# event_id; type; stop-id; line-id; passengers; line-direction; line-freq-repetition\n")

        for e_id in events:
            line = "{}; \"departure\"; 0; 0; 0; >; 1\n".format(e_id)
            file.write(line)

    with open(activities_file, "w") as file:
        file.write("# activity_index; type; from_event; to_event; lower_bound; upper_bound; passengers\n")

        for a in activities:
            line = "{}; \"drive\"; {}; {}; {}; {}; {}\n".format(a[0], a[1], a[2], a[3], a[4], a[5])
            file.write(line)

    print("Done.")

