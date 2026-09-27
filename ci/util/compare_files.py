import sys


def compare_files(expected: str, given: str) -> bool:
    with open(expected, encoding="utf-8") as expected_file:
        with open(given, encoding="utf-8") as given_file:
            difference = set(expected_file).difference(given_file)
    if difference:
        print("Found different lines in files {} and {}".format(expected, given))
        for line in difference:
            print(line)
        return False
    return True


if __name__ == '__main__':
    if len(sys.argv) < 3:
        raise RuntimeError("Program needs two parameters, the two files to compare.")
    success = compare_files(sys.argv[1], sys.argv[2])
    if not success:
        exit(1)