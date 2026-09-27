import os
import sys
from typing import List

from util import get_lintim, log_file, error_log_file, \
    install_system_dependencies, get_apache_commons_math, get_boost, \
    get_cute, get_g4p, get_goblin, get_googletest, get_hamcrest, get_jgrapht, \
    get_jline, get_junit, get_k_shortest_paths, \
    get_lemon, get_log4j, get_processing, get_super_csv, get_unfolding, \
    python_dependencies, get_python_dependencies, install_gurobi, \
    get_gurobi_license, get_gurobipy, python_dependencies_install_cmd

def parse_bool_input(question: str) -> bool:
    answer = input(question + " [Y/n]")
    if not answer:
        answer = "y"
    return answer.upper() == "Y" or answer.upper() == "YES"


def handle_failure(program_name: str, failures: List[str]) -> None:
    print(
        f"Unable to install {program_name}, check script output and logs ({log_file.name} and {error_log_file.name}) for details.")
    print("Try to continue...")
    failures.append(program_name)


def handle_failure_python(failures: List[str]):
    print(
        f"Unable to install python dependencies. Possibly a virtual environment is needed. "
        f"Check script output and logs ({log_file.name} and {error_log_file.name}) for details. "
        f"To manually install the packages, run \n"
        f"{python_dependencies_install_cmd}")
    failures.append("python dependencies")


if __name__ == '__main__':
    failures = []
    print("LinTim installation script")
    print("This script will try to install LinTim and all corresponding dependencies.")
    print("Start with installing system dependencies")
    print("The following apt-get packages will be installed: git build-essential openjdk-11-jdk ant maven graphviz python3-pip")
    print("Note that this will require sudo access, i.e., you will be prompted for your password")
    install_dependencies = parse_bool_input("Do you want to continue?")
    if install_dependencies:
        return_code = install_system_dependencies()
        if return_code != 0:
           handle_failure("System dependencies", failures)
    print("Start with downloading LinTim")
    download_lintim = parse_bool_input("Do you want to download LinTim?")
    if download_lintim:
        install_dir = input("Where do you want to install LinTim? [./LinTim]")
        if not install_dir:
            install_dir = "./LinTim"
    else:
        install_dir = input("Where is the LinTim base folder? [../..]")
        if not install_dir:
            install_dir = "../.."
    install_dir = os.path.join(os.path.abspath(os.getcwd()), install_dir)
    if download_lintim:
        return_code = get_lintim(install_dir)
        if return_code != 0:
            print(f"Unable to get LinTim, check script output and logs ({log_file.name} and {error_log_file.name}) for details.")
    print(f"Now installing local LinTim dependencies into {install_dir}/libs")
    install_dependencies = parse_bool_input("Do you want to continue?")
    if install_dependencies:
        print("Local LinTim dependencies contains Apache Common Math 2.1, Boost 1.67.0, Cute 2.2.1, G4P 3.5.4, "
              "Goblin 2.8b28, Googletest 1.7.0, Hamcrest 1.3, JGraphT 1.5.0, JLine 1.0, JUnit 4.13.2, K-shortest-path d028fd8, "
              "Lemon 1.3.1, log4j 1.2.17, Processing 2.2.1, super-csv 2.4.0 and Unfolding 0.9.6.")
        install_all = parse_bool_input("Do you want to to install all local LinTim dependencies?")
        if install_all:
            install_apache_commons = True
            print("Installing Apache Commons Math 2.1 ...")
        else:
            install_apache_commons = parse_bool_input("Do you want to install Apache Commons Math 2.1?")
        if install_apache_commons:
            result = get_apache_commons_math(install_dir)
            if result != 0:
                handle_failure("Apache Commons Math 2.1", failures)
        if install_all:
            install_boost = True
            print("Installing Boost 1.67.0 ...")
        else:
            install_boost = parse_bool_input("Do you want to install Boost 1.67.0?")
        if install_boost:
            result = get_boost(install_dir)
            if result != 0:
                handle_failure("Boost 1.67.0", failures)
        if install_all:
            install_cute = True
            print("Installing Cute 2.2.1 ...")
        else:
            install_cute = parse_bool_input("Do you want to install Cute 2.2.1?")
        if install_cute:
            result = get_cute(install_dir)
            if result != 0:
                handle_failure("Cute 2.2.1", failures)
        if install_all:
            install_g4p = True
            print("Installing G4P 3.5.4 ...")
        else:
            install_g4p = parse_bool_input("Do you want to install G4P 3.5.4?")
        if install_g4p:
            result = get_g4p(install_dir)
            if result != 0:
                handle_failure("G4P 3.5.4", failures)
        if install_all:
            install_goblin = True
            print("Installing Goblin 2.8b28 ...")
        else:
            install_goblin = parse_bool_input("Do you want to install Goblin 2.8b28?")
        if install_goblin:
            result = get_goblin(install_dir)
            if result != 0:
                handle_failure("Goblin 2.8b28", failures)
        if install_all:
            install_googletest = True
            print("Installing Googletest 1.7.0 ...")
        else:
            install_googletest = parse_bool_input("Do you want to install Googletest 1.7.0?")
        if install_googletest:
            result = get_googletest(install_dir)
            if result != 0:
                handle_failure("Googletest 1.7.0", failures)
        if install_all:
            install_hamcrest = True
            print("Installing Hamcrest 1.3 ...")
        else:
            install_hamcrest = parse_bool_input("Do you want to install Hamcrest 1.3?")
        if install_hamcrest:
            result = get_hamcrest(install_dir)
            if result != 0:
                handle_failure("Hamcrest 1.3", failures)
        if install_all:
            install_jgrapht = True
            print("Installing JGraphT 1.5.0 ...")
        else:
            install_jgrapht = parse_bool_input("Do you want to install JGraphT 1.5.0?")
        if install_jgrapht:
            result = get_jgrapht(install_dir)
            if result != 0:
                handle_failure("JGraphT 1.5.0", failures)
        if install_all:
            install_jline = True
            print("Installing JLine 1.0 ...")
        else:
            install_jline = parse_bool_input("Do you want to install JLine 1.0?")
        if install_jline:
            result = get_jline(install_dir)
            if result != 0:
                handle_failure("JLine 1.0", failures)
        if install_all:
            install_junit = True
            print("Installing JUnit 4.13.2 ...")
        else:
            install_junit = parse_bool_input("Do you want to install JUnit 4.13.2?")
        if install_junit:
            result = get_junit(install_dir)
            if result != 0:
                handle_failure("JUnit 4.13.2", failures)
        if install_all:
            install_k_shortest_paths = True
            print("Installing K-shortest-path d028fd8 ...")
        else:
            install_k_shortest_paths = parse_bool_input("Do you want to install K-shortest-path d028fd8?")
        if install_k_shortest_paths:
            result = get_k_shortest_paths(install_dir)
            if result != 0:
                handle_failure("K-shortest-paths d028fd8", failures)
        if install_all:
            install_lemon = True
            print("Installing Lemon 1.3.1 ...")
        else:
            install_lemon = parse_bool_input("Do you want to install Lemon 1.3.1?")
        if install_lemon:
            result = get_lemon(install_dir)
            if result != 0:
                handle_failure("Lemon 1.3.1", failures)
        if install_all:
            install_log4j = True
            print("Installing log4j 1.2.17 ...")
        else:
            install_log4j = parse_bool_input("Do you want to install log4j 1.2.17?")
        if install_log4j:
            result = get_log4j(install_dir)
            if result != 0:
                handle_failure("log4j 1.2.17", failures)
        if install_all:
            install_processing = True
            print("Installing Processing 2.2.1 ...")
        else:
            install_processing = parse_bool_input("Do you want to install Processing 2.2.1?")
        if install_processing:
            result = get_processing(install_dir)
            if result != 0:
                handle_failure("Processing 2.2.1", failures)
        if install_all:
            install_super_csv = True
            print("Installing super-csv 2.4.0 ...")
        else:
            install_super_csv = parse_bool_input("Do you want to install super-csv 2.4.0?")
        if install_super_csv:
            result = get_super_csv(install_dir)
            if result != 0:
                handle_failure("super_csv 2.4.0", failures)
        if install_all:
            install_unfolding = True
            print("Installing Unfolding 0.9.6 ...")
        else:
            install_unfolding = parse_bool_input("Do you want to install Unfolding 0.9.6")
        if install_unfolding:
            result = get_unfolding(install_dir)
            if result != 0:
                handle_failure("Unfolding 0.9.6", failures)
    with open("lintim_lib_error.log", "w") as lib_error:
        lib_error.write("Following libs had error with installing: \n")
        lib_error.write("\n".join(failures))
    print("Now installing gurobi.")
    handle_gurobi = parse_bool_input("Do you want continue?")
    if handle_gurobi:
        gurobi_account = parse_bool_input("Do you have a gurobi account?")
        if not gurobi_account:
            print("Please register now on \"https://pages.gurobi.com/registration\"")
        print("Please go to \"Download&Licenses -> Gurobi Optimizer-Download Software\" and accept the End User License Agreement.")
        print("Then download the latest Linux 64 Version. Please note that even if you have Windows or MacOs, you need the Linux Version.")
        user_name = os.environ.get('USER')
        gurobi_file = input(f"Please enter here the path of the downloaded Gurobi file [/home/{user_name}/Downloads/gurobi12.0.0_linux64.tar.gz]: ")
        if not gurobi_file:
            gurobi_file = f"/home/{user_name}/Downloads/gurobi12.0.0_linux64.tar.gz"
        print("We will now extract and install Gurobi into your environment. Note that this will require sudo access, i.e., you will be prompted for your password.")
        target_dir = input(f"Please insert the directory where you want to extract Gurobi to [/home/{user_name}]: ")
        if not target_dir:
            target_dir = f"/home/{user_name}"
        result = install_gurobi(gurobi_file, target_dir)
        if result != 0:
            handle_failure("gurobi", failures)
        else:
            print("Now get a license. If you have an academic account, we advise to get an academic license.")
            gurobi_key = input("Please copy here the license key (with grbgetkey): ")
            result = get_gurobi_license(gurobi_key, gurobi_file)
            if result != 0:
                handle_failure("gurobi license", failures)
    print(f"Now installing python dependencies. It contains {', '.join(python_dependencies)}.")
    install_python_deps = parse_bool_input("Do you want continue?")
    if install_python_deps:
        result = get_python_dependencies()
        if result != 0:
            handle_failure_python(failures)
        if handle_gurobi:
            result = get_gurobipy()
            if result != 0:
                handle_failure("pip3 install gurobipy", failures)
    print("Done.")

    if failures:
        sys.exit(1)


