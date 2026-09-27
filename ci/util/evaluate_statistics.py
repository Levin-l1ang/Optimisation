import os
import sys


def isclose(a, b, rel_tol=1e-09, abs_tol=0.0):
    return abs(a-b) <= max(rel_tol * max(abs(a), abs(b)), abs_tol)


def compare_statistic(expected_file: str, current_file: str) -> bool:
    if os.path.getsize(expected_file) == 0:
        return True
    with open(expected_file) as expected_statistic_file:
        with open(current_file) as current_statistic_file:
            expected_statistic_lines = expected_statistic_file.readlines()
            expected_statistic = {}
            for expected_statistic_line in expected_statistic_lines:
                if not expected_statistic_line.strip() or expected_statistic_line[0] == "#":
                    continue
                values = expected_statistic_line.split(";")
                if len(values) != 2:
                    raise RuntimeError("Expected statistic has a line that has not two entries, namely {}"
                                       .format(expected_statistic_line))
                expected_statistic[values[0]] = values[1].strip().lower()
            current_statistic_lines = current_statistic_file.readlines()
            for current_statistic_line in current_statistic_lines:
                if not current_statistic_line.strip() or current_statistic_line[0] == "#":
                    continue
                values = current_statistic_line.split(";")
                if len(values) != 2:
                    raise RuntimeError("Expected statistic has a line that has not two entries, namely {}"
                                       .format(current_statistic_line))
                if values[0] in expected_statistic:
                    found_value = values[1].strip().lower()
                    expected_value = expected_statistic[values[0]]
                    # Lets see if the values coincide
                    if expected_value != found_value:
                        # The strings are not the same, lets try to parse as float
                        try:
                            found_value_float = float(found_value)
                            expected_value_float = float(expected_value)
                            if not isclose(found_value_float, expected_value_float):
                                # We could cast both values as float and they were not the same. Therefore we found a
                                # difference!
                                print("Values for {} do not coincide (expected: {}, found: {})"
                                      .format(values[0], expected_value, found_value))
                                return False
                            # We could cast as float and the values are close enough. Therefore we can check the next
                            # statistic line
                            expected_statistic.pop(values[0])
                            continue
                        except ValueError:
                            # String were unequal and we could not cast both as float. Therefore, we found a
                            # difference!
                            print("Values for {} do not coincide (expected: {}, found: {})"
                                  .format(values[0], expected_value, found_value))
                            return False
                    else:
                        expected_statistic.pop(values[0])
            # Check if there are still values in the expected statistic. If that is the case, they were not in the
            # found statistic and therefore we found a difference
            if len(expected_statistic) > 0:
                print("Expected statistic entries that are not in the found statistic:")
                for key, value in expected_statistic.items():
                    print("Key: {}, Value: {}".format(key, value))
                return False
            print("Statistic comparison successful")
            return True


if __name__ == '__main__':
    if len(sys.argv) < 3:
        raise RuntimeError("Program needs two parameters, the two statistic files to compare. It will be checked "
                           "whether the first file is contained in the second.")
    success = compare_statistic(sys.argv[0], sys.argv[1])
    if not success:
        exit(1)


