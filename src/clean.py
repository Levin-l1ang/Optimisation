#!/usr/bin/env python3

from pathlib import Path
import os
import shutil

FILE_TO_DELETE_FILENAME = 'FILES_TO_CLEAN'

if __name__ == '__main__':
    for dirname, dirnames, filenames in os.walk(os.path.dirname(os.path.realpath(__file__))):
        if FILE_TO_DELETE_FILENAME in filenames:
            # Found files to clean, read input file
            files_to_clean = []
            p = Path(dirname)
            with open(os.path.join(dirname, FILE_TO_DELETE_FILENAME), 'r') as input_file:
                for line in input_file:
                    line = line.split('#')[0].strip()
                    if not line:
                        continue
                    new_files = list(p.glob(line))
                    files_to_clean.extend(list(p.glob(line)))
            # Now clean everything that we read
            for file in files_to_clean:
                print("Deleting ", file)
                if file.is_dir():
                    shutil.rmtree(str(file))
                else:
                    file.unlink()
