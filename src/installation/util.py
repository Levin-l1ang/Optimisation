import os
import shutil
import subprocess
import tarfile
from io import BytesIO
from urllib.request import urlopen, urlretrieve
from zipfile import ZipFile

log_file = open("lintim_install.log", "w")
error_log_file = open("lintim_error.log", "w")

LINTIM_GIT_URL = "https://gitlab.com/lintim/OpenLinTim.git"
APACHE_COMMONS_MATH_URL = "http://archive.apache.org/dist/commons/math/binaries/commons-math-2.1.zip"
BOOST_URL = "https://archives.boost.io/release/1.67.0/source/boost_1_67_0.tar.bz2"
CUTE_URL = "https://github.com/PeterSommerlad/CUTE/archive/v2.2.1.zip"
G4P_URL = "https://downloads.sourceforge.net/project/g4p/G4P%20V3.5.4.zip?ts=gAAAAABhdrjPZ0JYcUEQVMxAoqWIunAAvziKLGi7khf3cN8EHA0UPrdU_Gj2E1JP9NUmfOuvgECMEE6F8yVQ7hT-67RutZCfFA%3D%3D&r=https%3A%2F%2Fsourceforge.net%2Fprojects%2Fg4p%2Ffiles%2FG4P%2520V3.5.4.zip%2Fdownload"
GOBLIN_URL = "https://downloads.sourceforge.net/project/goblin2/goblin2/goblin.2.8b28/goblin.2.8b28.tgz?ts=gAAAAABhcCBwVQvNLoPutBMz4-MPpSp_nQGVXsZnfRC7RNnV87O6_kgBTleSSVT1ecxnGF4dJ603Faj4_m198cLONfD0WjekoQ%3D%3D&r=https%3A%2F%2Fsourceforge.net%2Fprojects%2Fgoblin2%2Ffiles%2Fgoblin2%2Fgoblin.2.8b28%2Fgoblin.2.8b28.tgz%2Fdownload"
GOOGLETEST_URL = "https://github.com/google/googletest/archive/refs/tags/release-1.7.0.zip"
HAMCREST_URL = "https://search.maven.org/remotecontent?filepath=org/hamcrest/hamcrest-core/1.3/hamcrest-core-1.3.jar"
JGRAPHT_URL = "https://search.maven.org/remotecontent?filepath=org/jgrapht/jgrapht-core/1.5.0/jgrapht-core-1.5.0.jar"
JHEAPS_URL = "https://search.maven.org/remotecontent?filepath=org/jheaps/jheaps/0.13/jheaps-0.13.jar"
JLINE_URL = "https://search.maven.org/remotecontent?filepath=jline/jline/1.0/jline-1.0.jar"
JUNIT_URL = "https://repo1.maven.org/maven2/junit/junit/4.13.2/junit-4.13.2.jar"
K_SHORTEST_PATHS_URL = "https://github.com/yan-qi/k-shortest-paths-java-version/archive/refs/heads/master.zip"
LEMON_URL = "http://lemon.cs.elte.hu/pub/sources/lemon-1.3.1.zip"
LOG4J_URL = "https://archive.apache.org/dist/logging/log4j/1.2.17/log4j-1.2.17.zip"
PROCESSING_URL = "https://github.com/processing/processing/releases/download/processing-0227-2.2.1/processing-2.2.1-linux64.tgz"
SUPERCSV_URL = "https://github.com/super-csv/super-csv/releases/download/v2.4.0/super-csv-distribution-2.4.0-bin.zip"
UNFOLDING_URL = "https://github.com/tillnagel/unfolding/releases/download/v0.9.6/Unfolding_for_processing_0.9.6.zip"

# TODO: Add checksum check for every download

python_dependencies = ["numpy", "networkx", "pulp", "holoviews", "weightedstats", "pandas", "pyproj", "matplotlib", "seaborn", "scikit-learn", "sphinx", "sphinx-rtd-theme"]
python_install_command = "pip3 install "
python_dependencies_install_cmd = python_install_command + " ".join(python_dependencies)

def git_available() -> bool:
    return shutil.which("git") is not None

def run(command: str) -> int:
    print(f"Running {command}")
    return subprocess.run(command, stdout=log_file, stderr=error_log_file, shell=True).returncode


def get_lintim(install_path: str):
    print(f"Installing to {install_path}")
    if git_available():
        print("Using git for LinTim installation")
        return_code = run(f"git clone {LINTIM_GIT_URL} {install_path}")
        return return_code
    else:
        print("Unable to locate git!")

def install_system_dependencies() -> int:
    return_code = run("sudo apt-get update")
    if return_code != 0:
        print("Unable to update repositories")
        return return_code
    return_code = run("sudo DEBIAN_FRONTEND=noninteractive DEBCONF_NONINTERACTIVE_SEEN=true apt-get -y install git build-essential openjdk-11-jdk ant maven graphviz python3-pip imagemagick")
    if return_code != 0:
        print("Unable to install system dependencies")
        return return_code
    return return_code

def get_apache_commons_math(lintim_base_dir: str) -> int:
    print("Downloading...")
    try:
        with urlopen(APACHE_COMMONS_MATH_URL) as zip_url:
            try:
                with ZipFile(BytesIO(zip_url.read())) as zip_file:
                    print("Extracting...")
                    zip_file.extract("commons-math-2.1/commons-math-2.1.jar", os.path.join(lintim_base_dir, "libs", "apache-commons"))
            except Exception as e:
                print(f"Unable to extract files: {e}")
                return 1
    except Exception as e:
        print(f"Unable to read URL: {e}")
        return 1
    print("Copying files")
    try:
        shutil.move(os.path.join(lintim_base_dir, "libs", "apache-commons", "commons-math-2.1", "commons-math-2.1.jar"),
                    os.path.join(lintim_base_dir, "libs", "apache-commons", "commons-math-2.1.jar"))
        print("Removing temp files")
    except Exception as e:
        print(f"Unable to move files: {e}")
        return 1
    shutil.rmtree(os.path.join(lintim_base_dir, "libs", "apache-commons", "commons-math-2.1"))
    return 0

def get_boost(lintim_base_dir: str) -> int:
    print("Downloading...")
    try:
        file_name, headers = urlretrieve(BOOST_URL)
        with tarfile.open(file_name) as tar:
            try:
                print("Extracting...")
                tar.extractall(os.path.join(lintim_base_dir, "libs", "boost"))
            except Exception as e:
                print(f"Unable to extract files: {e}")
                return 1
        os.remove(file_name)
    except Exception as e:
        print(f"Unable to read URL: {e}")
        return 1
    print("Compiling...")
    try:
        current_dir = os.getcwd()
        os.chdir(os.path.join(lintim_base_dir, "libs", "boost", "boost_1_67_0"))
        run(f"./bootstrap.sh --prefix={os.path.join(lintim_base_dir, 'libs', 'boost')} --with-libraries=filesystem,mpi,serialization,system")
        run("./b2 install")
        os.chdir(current_dir)
        print("Removing temp files")
        shutil.rmtree(os.path.join(lintim_base_dir, "libs", "boost", "boost_1_67_0"))
    except Exception as e:
        print(f"Unable to move files: {e}")
        return 1
    return 0

def get_cute(lintim_base_dir: str) -> int:
    print("Downloading...")
    try:
        with urlopen(CUTE_URL) as zip_url:
            try:
                with ZipFile(BytesIO(zip_url.read())) as zip_file:
                    print("Extracting...")
                    zip_file.extractall(os.path.join(lintim_base_dir, "libs", "cute"))
            except Exception as e:
                print(f"Unable to extract files: {e}")
                return 1
    except Exception as e:
        print(f"Unable to read URL: {e}")
        return 1
    print("Copying files")
    try:
        shutil.move(os.path.join(lintim_base_dir, "libs", "cute", "CUTE-2.2.1", "cute"),
                    os.path.join(lintim_base_dir, "libs", "cute", "cute_lib"))
        print("Removing temp files")
        shutil.rmtree(os.path.join(lintim_base_dir, "libs", "cute", "CUTE-2.2.1"))
    except Exception as e:
        print(f"Unable to move files: {e}")
        return 1
    return 0

def get_g4p(lintim_base_dir: str) -> int:
    print("Downloading...")
    try:
        with urlopen(G4P_URL) as zip_url:
            try:
                with ZipFile(BytesIO(zip_url.read())) as zip_file:
                    print("Extracting...")
                    zip_file.extract("G4P/library/G4P.jar", os.path.join(lintim_base_dir, "libs", "G4P"))
            except Exception as e:
                print(f"Unable to extract files: {e}")
                return 1
    except Exception as e:
        print(f"Unable to read URL: {e}")
        return 1
    print("Copying files")
    try:
        shutil.move(os.path.join(lintim_base_dir, "libs", "G4P", "G4P", "library", "G4P.jar"),
                    os.path.join(lintim_base_dir, "libs", "G4P", "G4P.jar"))
        print("Removing temp files")
        shutil.rmtree(os.path.join(lintim_base_dir, "libs", "G4P", "G4P"))
    except Exception as e:
        print(f"Unable to move files: {e}")
        return 1
    return 0

def get_goblin(lintim_base_dir: str) -> int:
    print("Downloading...")
    try:
        file_name, headers = urlretrieve(GOBLIN_URL)
        try:
            with tarfile.open(file_name) as tar:
                print(f"Extracting...")
                tar.extractall(path=os.path.join(lintim_base_dir, "libs", "goblin"))
        except Exception as e:
            print(f"Unable to extract files: {e}")
            return 1
        os.remove(file_name)
    except Exception as e:
        print(f"Unable to read URL: {e}")
        return 1
    print("Copying files")
    try:
        shutil.move(os.path.join(lintim_base_dir, "libs", "goblin", "goblin.2.8b28", "include"),
                    os.path.join(lintim_base_dir, "libs", "goblin"))
        print("Removing temp files")
        shutil.rmtree(os.path.join(lintim_base_dir, "libs", "goblin", "goblin.2.8b28"))
    except Exception as e:
        print(f"Unable to move files: {e}")
        return 1
    return 0

def get_googletest(lintim_base_dir: str) -> int:
    print("Downloading...")
    try:
        with urlopen(GOOGLETEST_URL) as zip_url:
            try:
                with ZipFile(BytesIO(zip_url.read())) as zip_file:
                    print("Extracting...")
                    zip_file.extractall(os.path.join(lintim_base_dir, "libs", "googletest"))
            except Exception as e:
                print(f"Unable to extract files: {e}")
                return 1
    except Exception as e:
        print(f"Unable to read URL: {e}")
        return 1
    print("Copying files")
    try:
        print("Removing temp files")
        os.rename(os.path.join(lintim_base_dir, "libs", "googletest", "googletest-release-1.7.0"),
                    os.path.join(lintim_base_dir, "libs", "googletest", "googletest"))
    except Exception as e:
        print(f"Unable to move files: {e}")
        return 1
    return 0

def get_hamcrest(lintim_base_dir: str) -> int:
    print("Downloading...")
    try:
        with urlopen(HAMCREST_URL) as file_url:
            open(os.path.join(lintim_base_dir, "libs", "hamcrest", "hamcrest-core-1.3.jar"), "wb").write(file_url.read())
    except Exception as e:
        print(f"Unable to read URL: {e}")
        return 1
    return 0

def get_jgrapht(lintim_base_dir: str) -> int:
    print("Downloading...")
    try:
        with urlopen(JGRAPHT_URL) as file_url:
            open(os.path.join(lintim_base_dir, "libs", "jgrapht", "jgrapht-core-1.5.0.jar"), "wb").write(file_url.read())
    except Exception as e:
        print(f"Unable to read URL: {e}")
        return 1
    try:
        with urlopen(JHEAPS_URL) as file_url:
            open(os.path.join(lintim_base_dir, "libs", "jgrapht", "jheaps-0.13.jar"), "wb").write(file_url.read())
    except Exception as e:
        print(f"Unable to read URL: {e}")
        return 1
    return 0

def get_jline(lintim_base_dir: str) -> int:
    print("Downloading...")
    try:
        with urlopen(JLINE_URL) as file_url:
            open(os.path.join(lintim_base_dir, "libs", "jline", "jline-1.0.jar"), "wb").write(file_url.read())
    except Exception as e:
        print(f"Unable to read URL: {e}")
        return 1
    return 0

def get_junit(lintim_base_dir: str) -> int:
    print("Downloading...")
    try:
        with urlopen(JUNIT_URL) as file_url:
            open(os.path.join(lintim_base_dir, "libs", "junit", "junit-4.13.2.jar"), "wb").write(file_url.read())
    except Exception as e:
        print(f"Unable to read URL: {e}")
        return 1
    return 0

def get_k_shortest_paths(lintim_base_dir: str) -> int:
    print("Downloading...")
    try:
        with urlopen(K_SHORTEST_PATHS_URL) as zip_url:
            try:
                with ZipFile(BytesIO(zip_url.read())) as zip_file:
                    print("Extracting...")
                    zip_file.extractall(os.path.join(lintim_base_dir, "libs", "k-shortest-paths"))
            except Exception as e:
                print(f"Unable to extract files: {e}")
                return 1
    except Exception as e:
        print(f"Unable to read URL: {e}")
        return 1
    print("Copying files")
    try:
        shutil.move(os.path.join(lintim_base_dir, "libs", "k-shortest-paths", "k-shortest-paths-java-version-master", "src", "main", "java"),
                    os.path.join(lintim_base_dir, "libs", "k-shortest-paths", "src", "main", "java"))
        print("Removing temp files")
        shutil.rmtree(os.path.join(lintim_base_dir, "libs", "k-shortest-paths", "k-shortest-paths-java-version-master"))
    except Exception as e:
        print(f"Unable to move files: {e}")
        return 1
    return 0

def get_lemon(lintim_base_dir: str) -> int:
    print("Downloading...")
    try:
        with urlopen(LEMON_URL) as zip_url:
            try:
                with ZipFile(BytesIO(zip_url.read())) as zip_file:
                    print("Extracting...")
                    zip_file.extractall(os.path.join(lintim_base_dir, "libs", "lemon"))
            except Exception as e:
                print(f"Unable to extract files: {e}")
                return 1
    except Exception as e:
        print(f"Unable to read URL: {e}")
        return 1
    print("Copying files")
    try:
        shutil.move(os.path.join(lintim_base_dir, "libs", "lemon", "lemon-1.3.1", "lemon"),
                    os.path.join(lintim_base_dir, "libs", "lemon", "lemon"))
        print("Removing temp files")
        shutil.rmtree(os.path.join(lintim_base_dir, "libs", "lemon", "lemon-1.3.1"))
    except Exception as e:
        print(f"Unable to move files: {e}")
        return 1
    return 0

def get_log4j(lintim_base_dir: str) -> int:
    print("Downloading...")
    try:
        with urlopen(LOG4J_URL) as zip_url:
            try:
                with ZipFile(BytesIO(zip_url.read())) as zip_file:
                    print("Extracting...")
                    zip_file.extract("apache-log4j-1.2.17/log4j-1.2.17.jar", os.path.join(lintim_base_dir, "libs", "log4j"))
            except Exception as e:
                print(f"Unable to extract files: {e}")
                return 1
    except Exception as e:
        print(f"Unable to read URL: {e}")
        return 1
    print("Copying files")
    try:
        shutil.move(os.path.join(lintim_base_dir, "libs", "log4j", "apache-log4j-1.2.17", "log4j-1.2.17.jar"),
                    os.path.join(lintim_base_dir, "libs", "log4j", "log4j-1.2.17.jar"))
        print("Removing temp files")
        shutil.rmtree(os.path.join(lintim_base_dir, "libs", "log4j", "apache-log4j-1.2.17"))
    except Exception as e:
        print(f"Unable to move files: {e}")
        return 1
    return 0

def get_processing(lintim_base_dir: str) -> int:
    print("Downloading...")
    try:
        file_name, headers = urlretrieve(PROCESSING_URL)
        try:
            with tarfile.open(file_name) as tar_file:
                print("Extracting...")
                tar_file.extract("processing-2.2.1/core/library/core.jar", os.path.join(lintim_base_dir, "libs", "processing"))
                tar_file.extract("processing-2.2.1/core/library/gluegen-rt.jar", os.path.join(lintim_base_dir, "libs", "processing"))
                tar_file.extract("processing-2.2.1/core/library/gluegen-rt-natives-linux-amd64.jar", os.path.join(lintim_base_dir, "libs", "processing"))
                tar_file.extract("processing-2.2.1/core/library/jogl-all.jar", os.path.join(lintim_base_dir, "libs", "processing"))
                tar_file.extract("processing-2.2.1/core/library/jogl-all-natives-linux-amd64.jar", os.path.join(lintim_base_dir, "libs", "processing"))
        except Exception as e:
            print(f"Unable to extract files: {e}")
            return 1
        os.remove(file_name)
    except Exception as e:
        print(f"Unable to read URL: {e}")
        return 1
    print("Copying files")
    try:
        shutil.move(os.path.join(lintim_base_dir, "libs", "processing", "processing-2.2.1", "core", "library", "core.jar"),
                    os.path.join(lintim_base_dir, "libs", "processing", "core.jar"))
        shutil.move(os.path.join(lintim_base_dir, "libs", "processing", "processing-2.2.1", "core", "library", "gluegen-rt.jar"),
                    os.path.join(lintim_base_dir, "libs", "processing", "gluegen-rt.jar"))
        shutil.move(os.path.join(lintim_base_dir, "libs", "processing", "processing-2.2.1", "core", "library", "gluegen-rt-natives-linux-amd64.jar"),
                    os.path.join(lintim_base_dir, "libs", "processing", "gluegen-rt-natives-linux-amd64.jar"))
        shutil.move(os.path.join(lintim_base_dir, "libs", "processing", "processing-2.2.1", "core", "library", "jogl-all.jar"),
                    os.path.join(lintim_base_dir, "libs", "processing", "jogl-all.jar"))
        shutil.move(os.path.join(lintim_base_dir, "libs", "processing", "processing-2.2.1", "core", "library", "jogl-all-natives-linux-amd64.jar"),
                    os.path.join(lintim_base_dir, "libs", "processing", "jogl-all-natives-linux-amd64.jar"))
        print("Removing temp files")
        shutil.rmtree(os.path.join(lintim_base_dir, "libs", "processing", "processing-2.2.1"))
    except Exception as e:
        print(f"Unable to move files: {e}")
        return 1
    return 0

def get_rhpc(lintim_base_dir: str) -> int:
    print("Downloading...")
    print("Copying files")
    print("Removing temp files")
    return 0

def get_super_csv(lintim_base_dir: str) -> int:
    print("Downloading...")
    try:
        with urlopen(SUPERCSV_URL) as zip_url:
            try:
                with ZipFile(BytesIO(zip_url.read())) as zip_file:
                    print("Extracting...")
                    zip_file.extract("super-csv/super-csv-2.4.0.jar", os.path.join(lintim_base_dir, "libs", "super-csv"))
            except Exception as e:
                print(f"Unable to extract files: {e}")
                return 1
    except Exception as e:
        print(f"Unable to read URL: {e}")
        return 1
    print("Copying files")
    try:
        shutil.move(os.path.join(lintim_base_dir, "libs", "super-csv", "super-csv", "super-csv-2.4.0.jar"),
                    os.path.join(lintim_base_dir, "libs", "super-csv", "super-csv-2.4.0.jar"))
        print("Removing temp files")
        shutil.rmtree(os.path.join(lintim_base_dir, "libs", "super-csv", "super-csv"))
    except Exception as e:
        print(f"Unable to move files: {e}")
        return 1
    return 0

def get_unfolding(lintim_base_dir: str) -> int:
    print("Downloading...")
    try:
        with urlopen(UNFOLDING_URL) as zip_url:
            try:
                with ZipFile(BytesIO(zip_url.read())) as zip_file:
                    print("Extracting...")
                    zip_file.extract("Unfolding.zip", os.path.join(lintim_base_dir, "libs", "Unfolding"))
                    with ZipFile(os.path.join(lintim_base_dir, "libs", "Unfolding", "Unfolding.zip")) as zip_file_2:
                        zip_file_2.extract("library/Unfolding.jar", os.path.join(lintim_base_dir, "libs", "Unfolding"))
                        zip_file_2.extract("library/json4processing.jar", os.path.join(lintim_base_dir, "libs", "Unfolding"))
            except Exception as e:
                print(f"Unable to extract files: {e}")
                return 1
    except Exception as e:
        print(f"Unable to read URL: {e}")
        return 1
    print("Copying files")
    try:
        shutil.move(os.path.join(lintim_base_dir, "libs", "Unfolding", "library", "Unfolding.jar"),
                    os.path.join(lintim_base_dir, "libs", "Unfolding", "Unfolding.jar"))
        shutil.move(os.path.join(lintim_base_dir, "libs", "Unfolding", "library", "json4processing.jar"),
                    os.path.join(lintim_base_dir, "libs", "Unfolding", "json4processing.jar"))
        shutil.move(os.path.join(lintim_base_dir, "libs", "Unfolding", "Unfolding.zip"),
                    os.path.join(lintim_base_dir, "libs", "Unfolding", "library", "Unfolding.zip"))
        print("Removing temp files")
        shutil.rmtree(os.path.join(lintim_base_dir, "libs", "Unfolding", "library"))
    except Exception as e:
        print(f"Unable to move files: {e}")
        return 1
    return 0

def install_gurobi(file: str, target: str) -> int:
    user_name = os.environ.get('USER')
    if "~" in file:
        file = file.replace("~", f"/home/{user_name}")
    if "~" in target:
        target = target.replace("~", f"/home/{user_name}")
    (path, filename) = os.path.split(file)
    new_name = filename.replace("_linux64.tar.gz", "")
    new_name = new_name.replace(".", "")
    print(f"Try extracting {file} to {target}")
    try:
        with tarfile.open(file) as tar_file:
            print("Extracting...")
            tar_file.extractall(target)
    except Exception as e:
        print(f"Unable to extract files: {e}")
        return 1
    print("Installing files")
    returncode_1 = run('export GUROBI_HOME=/home/$USER/'+new_name+'/linux64')
    returncode_2 = run('export LD_LIBRARY_PATH=$GUROBI_HOME/lib:$LD_LIBRARY_PATH')
    returncode_3 = run('export CLASSPATH=$GUROBI_HOME/lib/gurobi.jar:$CLASSPATH')
    returncode_4 = run('export PATH=$GUROBI_HOME/bin:\"${PATH}\"')
    bashfile = open(f"/home/{user_name}/.bashrc","a")
    bashfile.write("\n export GUROBI_HOME=/home/$USER/"+new_name+"/linux64")
    bashfile.write("\n export LD_LIBRARY_PATH=$GUROBI_HOME/lib:$LD_LIBRARY_PATH")
    bashfile.write("\n export CLASSPATH=$GUROBI_HOME/lib/gurobi.jar:$CLASSPATH")
    bashfile.write('\n export PATH=$GUROBI_HOME/bin:\"${PATH}\" \n')
    bashfile.close()
    return returncode_1 + returncode_2 + returncode_3 + returncode_4

def get_python_dependencies() -> int:
    return_code = run(python_dependencies_install_cmd)
    return return_code

def get_gurobi_license(gurobi_key, file):
    (path, filename) = os.path.split(file)
    new_name = filename.replace("_linux64.tar.gz","")
    new_name = new_name.replace(".","")
    result = run("/home/$USER/"+new_name+"/linux64/bin/"+gurobi_key)
    return result

def get_gurobipy():
    return run(python_install_command + "gurobipy")