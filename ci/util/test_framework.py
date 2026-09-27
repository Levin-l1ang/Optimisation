import os
import shutil
import subprocess
import sys
from datetime import datetime
from shutil import copytree
from time import sleep
from typing import Tuple, List

from compare_files import compare_files
from evaluate_statistics import compare_statistic

script_location = os.path.abspath(os.path.dirname(__file__))


class TestParameters:
    def __init__(self, target_dataset: str, commands_to_run: str, files_to_compare: List[str]):
        self.target_dataset = target_dataset
        self.commands_to_run = commands_to_run
        self.files_to_compare = files_to_compare


def read_input_values(filename: str) -> TestParameters:
    target_dataset = ""
    commands_to_run = ""
    files_to_compare = []
    with open(filename) as input_file:
        for line in input_file:
            line = line.split("#")[0]
            line = line.strip()
            key = line.split(";")[0].strip()
            value = ";".join(line.split(";")[1:]).strip()
            if key == "TARGET_DATASET":
                if target_dataset:
                    raise RuntimeError(f"Invalid test parameter file {filename}, found multiple target datasets")
                target_dataset = value
            elif key == "COMMANDS_TO_RUN":
                if commands_to_run:
                    raise RuntimeError(f"Invalid test parameter file {filename}, found multiple commands to run")
                commands_to_run = value
            elif key == "ADDITIONAL_FILES_TO_COMPARE":
                if files_to_compare:
                    raise RuntimeError(f"Invalid test parameter file {filename}, found multiple additional files")
                for file in value.split(","):
                    files_to_compare.append(file.strip())
    if not target_dataset or not commands_to_run:
        raise RuntimeError(f"Invalid test parameter file {filename}, found target dataset {target_dataset} "
                           f"and commands to run {commands_to_run}")
    return TestParameters(target_dataset, commands_to_run, files_to_compare)


def test(test_folder: str, parameters: TestParameters) -> bool:
    test_name = os.path.basename(test_folder)
    timestamp = datetime.now().strftime("%Y-%m-%d_%H-%M-%S")
    original_dataset = os.path.join(script_location, "..", "..", "datasets", parameters.target_dataset)
    target_directory = os.path.join(script_location, "..", "..", "datasets", f"{parameters.target_dataset}_{test_name}_{timestamp}")
    # Copy all original files
    copytree(original_dataset, target_directory)
    # Copy all additional files
    for item in os.listdir(test_folder):
        source = os.path.join(test_folder, item)
        destination = os.path.join(target_directory, item)
        if os.path.isdir(source):
            shutil.copytree(source, destination, dirs_exist_ok=True)
        else:
            shutil.copy(source, destination)
    # Change into the target directory to run LinTim commands
    last_working_directory = os.getcwd()
    os.chdir(target_directory)
    log_file = open(os.path.join(target_directory, "test.log"), "w")
    return_code = subprocess.run([parameters.commands_to_run],
                                 stdout=log_file, stderr=log_file, shell=True).returncode
    log_file.flush()
    log_file.close()
    # Compare output files
    try:
        # Test, if expected statistic file is present. For tests in multimodal dataset we might not have a expected-statistic file but several prefixed
        # statistic files to be compared one by one as listed in ADDITIONAL_FILES_TO_COMPARE.
        if os.path.exists(os.path.join(script_location, "..", test_folder, "expected-statistic.sta")):
            success = return_code == 0 and compare_statistic(os.path.join(script_location, "..", test_folder, "expected-statistic.sta"),
                                            os.path.join("statistic", "statistic.sta"))
        else:
            success = return_code == 0
    except FileNotFoundError as e:
        print(f"One statistic file does not exist: {e}")
        print(f"Test {test_folder} failed, returned with code {return_code} . Output was:")
        print_test_output(target_directory)
        os.chdir(last_working_directory)
        return False
    for file in parameters.files_to_compare:
        if file.strip():
            success = success and compare_files(os.path.join(script_location, "..", test_folder, file), file)
    if not success:
        print(f"Test {test_folder} failed, returned with code {return_code} . Output was:")
        print_test_output(target_directory)
        os.chdir(last_working_directory)
        return False
    # Change working directory back so we can delete the folder
    os.chdir(last_working_directory)
    #subprocess.run(f"rm -rf {target_directory}", shell=True)
    shutil.rmtree(target_directory)
    # Wait for one second. Otherwise, creating the directory for the next test (when its the same dataset) might fail
    # if the test was too fast and the directory names match
    sleep(1)
    return True


def print_test_output(directory: str):
    with open(os.path.join(directory, "test.log")) as output_file:
        for line in output_file:
            print(line.strip())


if __name__ == '__main__':
    if len(sys.argv) < 2:
        raise RuntimeError("Need at least one argument, the folder to find the tests in")
    working_directory = os.path.abspath(sys.argv[1])
    run_failed = len(sys.argv) > 2 and sys.argv[2].upper() == "TRUE"
    skipped_tests = 0
    completed_tests = 0
    failed_tests = 0
    if run_failed:
        tests_to_do = []
        try:
            with open("failed_tests") as input_file:
                for line in input_file:
                    tests_to_do.append(line.strip())
        except FileNotFoundError:
            print("No failed tests found, exit")
            exit(0)
    elif os.path.exists(os.path.join(working_directory, "parameters.csv")):
        tests_to_do = [working_directory]
    else:
        tests_to_do = [folder for folder in os.listdir(working_directory)
                       if os.path.isdir(folder) and folder != "util" and folder != "template"]
    failed_test_list = []
    for index, test_folder in enumerate(tests_to_do):
        print(f"Running test {index+1} of {len(tests_to_do)}, {test_folder}. "
              f"Currently {completed_tests} completed, {skipped_tests} skipped and {failed_tests} failed")
        if os.path.exists(os.path.join(test_folder, "DISABLED")):
            skipped_tests += 1
            continue
        try:
            parameters = read_input_values(os.path.join(test_folder, "parameters.csv"))
        except IOError as err:
            print(f"Cannot read parameters file for {test_folder}, skip. Error was {err}")
            skipped_tests += 1
            failed_test_list.append(test_folder)
            continue
        success = test(test_folder, parameters)
        if success:
            completed_tests += 1
        else:
            failed_tests += 1
            failed_test_list.append(test_folder)
    print(f"Executed {len(tests_to_do)} tests: {completed_tests} successful, {skipped_tests} skipped and {failed_tests} failed")
    if failed_tests > 0:
        if len(tests_to_do) > 1:
            # Dump the list of failed tests
            # When only running a single test, dont write a new failed_tests file
            with open("failed_tests", "w") as output_file:
                for test in failed_test_list:
                    output_file.write(test + "\n")
        exit(1)